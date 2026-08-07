package com.waad.tba.modules.settlement.entity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the V138 lifecycle guards: only DRAFT -> POSTED -> REVERSED is
 * reachable, posted payments and their allocations are frozen, and a ledger link
 * must be the *correct* entry rather than merely an existing one.
 *
 * All enforced by PostgreSQL. The point is that a future entry path — a new
 * service, a bulk import, a repair script — cannot bypass these by forgetting an
 * application-level check, which is exactly how the two payment paths drifted
 * apart in the first place.
 *
 * Not @Transactional: the ledger-match trigger is DEFERRABLE INITIALLY DEFERRED
 * and only fires at commit.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderPaymentLifecycleIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ProviderPaymentRepository paymentRepository;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private EmployerRepository employerRepository;
    @Autowired
    private ProviderAccountRepository accountRepository;
    @Autowired
    private AccountTransactionRepository transactionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long providerId;
    private Long otherProviderId;
    private Long employerId;
    private ProviderAccount account;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        providerId = providerRepository.save(Provider.builder()
                .name("Lifecycle Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build()).getId();

        otherProviderId = providerRepository.save(Provider.builder()
                .name("Other Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC2-" + suffix).allowAllEmployers(true).active(true).build()).getId();

        employerId = employerRepository.save(Employer.builder()
                .name("Lifecycle Co " + suffix).code("EMP-" + suffix).active(true).build()).getId();

        account = accountRepository.save(ProviderAccount.builder()
                .providerId(providerId).runningBalance(new BigDecimal("10000.00"))
                .totalApproved(new BigDecimal("10000.00")).totalPaid(BigDecimal.ZERO).build());

        accountRepository.save(ProviderAccount.builder()
                .providerId(otherProviderId).runningBalance(new BigDecimal("5000.00"))
                .totalApproved(new BigDecimal("5000.00")).totalPaid(BigDecimal.ZERO).build());
    }

    private ProviderPayment savedDraft(String amount) {
        return paymentRepository.saveAndFlush(ProviderPayment.builder()
                .providerId(providerId).amount(new BigDecimal(amount))
                .paymentDate(LocalDate.now()).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .idempotencyKey("PAY-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build());
    }

    /** Creates a ledger entry that correctly matches the given payment. */
    private AccountTransaction matchingDebit(ProviderPayment payment) {
        return transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(account.getId())
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(payment.getAmount())
                .balanceBefore(new BigDecimal("10000.00"))
                .balanceAfter(new BigDecimal("10000.00").subtract(payment.getAmount()))
                .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT)
                .referenceId(payment.getId())
                .transactionDate(LocalDate.now())
                .build());
    }

    private ProviderPayment postedPayment(String amount) {
        ProviderPayment payment = savedDraft(amount);
        AccountTransaction debit = matchingDebit(payment);
        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(debit.getId());
        payment.setPostedAt(LocalDateTime.now());
        payment.setPostedBy("tester");
        return paymentRepository.saveAndFlush(payment);
    }

    // ── آلة انتقال الحالات ───────────────────────────────────────────────────

    @Test
    void draftToPostedIsAllowed() {
        assertThatCode(() -> postedPayment("500.00")).doesNotThrowAnyException();
    }

    @Test
    void postedCannotReturnToDraft() {
        ProviderPayment posted = postedPayment("500.00");
        posted.setStatus(ProviderPayment.Status.DRAFT);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(posted))
                .hasStackTraceContaining("انتقال حالة غير مسموح");
    }

    @Test
    void draftCannotJumpStraightToReversed() {
        // A payment that was never posted has nothing to reverse; V137 allowed
        // representing this state, which V138 closes.
        ProviderPayment draft = savedDraft("500.00");
        draft.setStatus(ProviderPayment.Status.REVERSED);
        draft.setReversedAt(LocalDateTime.now());
        draft.setReversalReason("محاولة عكس دفعة لم تُرحَّل");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(draft))
                .hasStackTraceContaining("انتقال حالة غير مسموح");
    }

    @Test
    void reversedCannotBePostedAgain() {
        ProviderPayment reversed = reversedPayment("500.00");
        reversed.setStatus(ProviderPayment.Status.POSTED);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(reversed))
                .hasStackTraceContaining("انتقال حالة غير مسموح");
    }

    private ProviderPayment reversedPayment(String amount) {
        ProviderPayment posted = postedPayment(amount);
        AccountTransaction credit = transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(account.getId())
                .transactionType(AccountTransaction.TransactionType.CREDIT)
                .amount(posted.getAmount())
                .balanceBefore(new BigDecimal("9500.00"))
                .balanceAfter(new BigDecimal("9500.00").add(posted.getAmount()))
                .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT_REVERSAL)
                .referenceId(posted.getId())
                .transactionDate(LocalDate.now())
                .build());
        posted.setStatus(ProviderPayment.Status.REVERSED);
        posted.setReversalLedgerTransactionId(credit.getId());
        posted.setReversedAt(LocalDateTime.now());
        posted.setReversedBy("tester");
        posted.setReversalReason("تصحيح مبلغ");
        return paymentRepository.saveAndFlush(posted);
    }

    @Test
    void postedToReversedIsAllowed() {
        assertThatCode(() -> reversedPayment("500.00")).doesNotThrowAnyException();
    }

    // ── عدم قابلية التاريخ للتغيير ───────────────────────────────────────────

    @Test
    void postedPaymentAmountCannotBeChanged() {
        ProviderPayment posted = postedPayment("500.00");
        posted.setAmount(new BigDecimal("750.00"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(posted))
                .hasStackTraceContaining("اعكسها وأنشئ دفعة بديلة");
    }

    @Test
    void postedPaymentProviderCannotBeChanged() {
        ProviderPayment posted = postedPayment("500.00");
        posted.setProviderId(otherProviderId);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(posted))
                .hasStackTraceContaining("اعكسها وأنشئ دفعة بديلة");
    }

    @Test
    void postedPaymentNotesAndAttachmentCannotBeChanged() {
        ProviderPayment posted = postedPayment("500.00");
        posted.setNotes("ملاحظة أضيفت بعد الترحيل");
        posted.setAttachmentPath("/replacement.pdf");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(posted))
                .hasStackTraceContaining("اعكسها وأنشئ دفعة بديلة");
    }

    @Test
    void postedPaymentLedgerLinkCannotBeRewritten() {
        ProviderPayment posted = postedPayment("500.00");
        posted.setLedgerTransactionId(null);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(posted))
                .hasStackTraceContaining("لا يمكن تغيير قيد الدفتر");
    }

    @Test
    void postedPaymentCannotBeDeleted() {
        ProviderPayment posted = postedPayment("500.00");

        assertThatThrownBy(() -> {
            paymentRepository.delete(posted);
            paymentRepository.flush();
        }).hasStackTraceContaining("لا يمكن حذف دفعة في حالة");
    }

    @Test
    void allocationsCannotBeAddedAfterPosting() {
        ProviderPayment posted = postedPayment("500.00");
        ProviderPayment reloaded = paymentRepository.findByIdWithAllocations(posted.getId()).orElseThrow();
        reloaded.addAllocation(ProviderPaymentAllocation.builder()
                .employerId(employerId).targetYear(2026).targetMonth(1)
                .amount(new BigDecimal("100.00")).build());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(reloaded))
                .hasStackTraceContaining("التخصيصات تُجمَّد عند الترحيل");
    }

    @Test
    void allocationCannotBeMovedFromPostedPaymentToDraft() {
        ProviderPayment source = savedDraft("500.00");
        source.addAllocation(ProviderPaymentAllocation.builder()
                .employerId(employerId).targetYear(2026).targetMonth(2)
                .amount(new BigDecimal("100.00")).build());
        source = paymentRepository.saveAndFlush(source);
        AccountTransaction debit = matchingDebit(source);
        source.setStatus(ProviderPayment.Status.POSTED);
        source.setLedgerTransactionId(debit.getId());
        source.setPostedAt(LocalDateTime.now());
        source.setPostedBy("tester");
        source = paymentRepository.saveAndFlush(source);

        ProviderPayment target = savedDraft("500.00");
        Long allocationId = paymentRepository.findByIdWithAllocations(source.getId())
                .orElseThrow().getAllocations().get(0).getId();

        // Direct SQL deliberately bypasses JPA/service guards. The database trigger
        // must still reject moving history out of a posted payment.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE provider_payment_allocations SET payment_id = ? WHERE id = ?",
                target.getId(), allocationId))
                .hasStackTraceContaining("لا يمكن نقل تخصيص من الدفعة");
    }

    // ── صحة قيد الدفتر لا مجرد وجوده ─────────────────────────────────────────

    @Test
    void ledgerEntryBelongingToAnotherProviderIsRejected() {
        ProviderPayment payment = savedDraft("500.00");
        ProviderAccount otherAccount = accountRepository.findByProviderId(otherProviderId).orElseThrow();
        AccountTransaction wrongAccountDebit = transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(otherAccount.getId())
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(payment.getAmount())
                .balanceBefore(new BigDecimal("5000.00")).balanceAfter(new BigDecimal("4500.00"))
                .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT)
                .referenceId(payment.getId()).transactionDate(LocalDate.now()).build());

        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(wrongAccountDebit.getId());
        payment.setPostedAt(LocalDateTime.now());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .hasStackTraceContaining("يخص حساب مزود آخر");
    }

    @Test
    void ledgerEntryWithMismatchedAmountIsRejected() {
        ProviderPayment payment = savedDraft("500.00");
        AccountTransaction wrongAmount = transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(account.getId())
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(new BigDecimal("499.00"))
                .balanceBefore(new BigDecimal("10000.00")).balanceAfter(new BigDecimal("9501.00"))
                .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT)
                .referenceId(payment.getId()).transactionDate(LocalDate.now()).build());

        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(wrongAmount.getId());
        payment.setPostedAt(LocalDateTime.now());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .hasStackTraceContaining("لا يساوي مبلغ الدفعة");
    }

    @Test
    void ledgerEntryOfTheWrongReferenceTypeIsRejected() {
        ProviderPayment payment = savedDraft("500.00");
        AccountTransaction claimEntry = transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(account.getId())
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(payment.getAmount())
                .balanceBefore(new BigDecimal("10000.00")).balanceAfter(new BigDecimal("9500.00"))
                .referenceType(AccountTransaction.ReferenceType.CLAIM_APPROVAL)
                .referenceId(payment.getId()).transactionDate(LocalDate.now()).build());

        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(claimEntry.getId());
        payment.setPostedAt(LocalDateTime.now());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .hasStackTraceContaining("بدل PROVIDER_PAYMENT");
    }

    @Test
    void ledgerEntryInTheWrongDirectionIsRejected() {
        // A payment must DEBIT the account. A CREDIT would increase what we owe.
        ProviderPayment payment = savedDraft("500.00");
        AccountTransaction credit = transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(account.getId())
                .transactionType(AccountTransaction.TransactionType.CREDIT)
                .amount(payment.getAmount())
                .balanceBefore(new BigDecimal("10000.00")).balanceAfter(new BigDecimal("10500.00"))
                .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT)
                .referenceId(payment.getId()).transactionDate(LocalDate.now()).build());

        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(credit.getId());
        payment.setPostedAt(LocalDateTime.now());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .hasStackTraceContaining("يجب أن يكون DEBIT");
    }

    @Test
    void ledgerEntryPointingAtAnotherPaymentIsRejected() {
        ProviderPayment payment = savedDraft("500.00");
        ProviderPayment decoy = savedDraft("500.00");
        AccountTransaction wrongReference = transactionRepository.saveAndFlush(AccountTransaction.builder()
                .providerAccountId(account.getId())
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(payment.getAmount())
                .balanceBefore(new BigDecimal("10000.00")).balanceAfter(new BigDecimal("9500.00"))
                .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT)
                .referenceId(decoy.getId()).transactionDate(LocalDate.now()).build());

        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(wrongReference.getId());
        payment.setPostedAt(LocalDateTime.now());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .hasStackTraceContaining("يشير إلى مرجع مختلف");
    }

    @Test
    void draftMayNotCarryAReversalLedgerLink() {
        ProviderPayment draft = savedDraft("500.00");
        draft.setReversalLedgerTransactionId(999_999L);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(draft))
                .hasStackTraceContaining("chk_provider_payments_reversal_only_when_reversed");
    }

    @Test
    void postedPaymentRequiresANonBlankIdempotencyKey() {
        ProviderPayment payment = savedDraft("500.00");
        AccountTransaction debit = matchingDebit(payment);
        payment.setIdempotencyKey("   ");
        payment.setStatus(ProviderPayment.Status.POSTED);
        payment.setLedgerTransactionId(debit.getId());
        payment.setPostedAt(LocalDateTime.now());
        payment.setPostedBy("tester");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .hasStackTraceContaining("chk_provider_payments_posted_identity");
    }
}
