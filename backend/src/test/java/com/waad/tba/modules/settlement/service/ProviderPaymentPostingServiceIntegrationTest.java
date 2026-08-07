package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/** End-to-end proof that posting is one atomic payment/account/ledger change. */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderPaymentPostingServiceIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired ProviderPaymentPostingService service;
    @Autowired ProviderPaymentRepository payments;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderRepository providers;
    @Autowired EmployerRepository employers;

    private Long providerId;
    private Long employerId;
    private ProviderAccount account;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        providerId = providers.save(Provider.builder().name("Posting Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("POST-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        employerId = employers.save(Employer.builder().name("Posting Employer " + suffix)
                .code("POST-" + suffix).active(true).build()).getId();
        account = accounts.save(ProviderAccount.builder().providerId(providerId)
                .runningBalance(new BigDecimal("100.00"))
                .totalApproved(new BigDecimal("100.00")).totalPaid(BigDecimal.ZERO).build());
    }

    @Test
    void postsOneLedgerDebitAllowsProviderCreditAndReplayCreatesNothing() {
        ProviderPayment draft = draft("150.00", "POST-IDEMP-" + UUID.randomUUID());
        long ledgerBefore = transactions.count();

        var first = service.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), account.getVersion(), "accountant", 77L);

        ProviderPayment posted = payments.findByIdWithAllocations(draft.getId()).orElseThrow();
        ProviderAccount updated = accounts.findById(account.getId()).orElseThrow();
        assertThat(posted.getStatus()).isEqualTo(ProviderPayment.Status.POSTED);
        assertThat(posted.getLedgerTransactionId()).isEqualTo(first.getLedgerTransactionId());
        assertThat(first.getUnallocatedAmount()).isEqualByComparingTo("150.00");
        assertThat(updated.getTotalPaid()).isEqualByComparingTo("150.00");
        assertThat(updated.getRunningBalance()).isEqualByComparingTo("-50.00");
        assertThat(transactions.count()).isEqualTo(ledgerBefore + 1);
        assertThat(transactions.findByReferenceTypeAndReferenceId(
                ReferenceType.PROVIDER_PAYMENT, draft.getId())).isPresent();

        var replay = service.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), account.getVersion(), "accountant", 77L);
        assertThat(replay.isIdempotentReplay()).isTrue();
        assertThat(replay.getLedgerTransactionId()).isEqualTo(first.getLedgerTransactionId());
        assertThat(transactions.count()).isEqualTo(ledgerBefore + 1);
        assertThat(accounts.findById(account.getId()).orElseThrow().getTotalPaid())
                .isEqualByComparingTo("150.00");
    }

    @Test
    void staleAccountVersionFailsBeforeAnyFinancialWrite() {
        ProviderPayment draft = draft("20.00", "STALE-" + UUID.randomUUID());
        long ledgerBefore = transactions.count();

        assertThatThrownBy(() -> service.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), account.getVersion() + 1, "accountant", 77L))
                .hasMessageContaining("تغيّر منذ المعاينة");

        assertThat(payments.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(ProviderPayment.Status.DRAFT);
        assertThat(accounts.findById(account.getId()).orElseThrow().getTotalPaid())
                .isEqualByComparingTo("0.00");
        assertThat(transactions.count()).isEqualTo(ledgerBefore);
    }

    @Test
    void staleAllocationSnapshotFailsAndRollsBackEverything() {
        ProviderPayment draft = ProviderPayment.builder().providerId(providerId)
                .amount(new BigDecimal("20.00")).paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .idempotencyKey("ALLOC-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build();
        draft.addAllocation(ProviderPaymentAllocation.builder().employerId(employerId)
                .targetYear(LocalDate.now().getYear()).targetMonth(LocalDate.now().getMonthValue())
                .amount(new BigDecimal("20.00")).build());
        draft = payments.saveAndFlush(draft);
        long ledgerBefore = transactions.count();

        ProviderPayment finalDraft = draft;
        assertThatThrownBy(() -> service.post(finalDraft.getId(), finalDraft.getIdempotencyKey(),
                finalDraft.getVersion(), account.getVersion(), "accountant", 77L))
                .hasMessageContaining("يتجاوز المستحق الحالي");

        assertThat(payments.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(ProviderPayment.Status.DRAFT);
        assertThat(accounts.findById(account.getId()).orElseThrow().getRunningBalance())
                .isEqualByComparingTo("100.00");
        assertThat(transactions.count()).isEqualTo(ledgerBefore);
    }

    @Test
    void concurrentPaymentsFromSamePreviewCannotBothPost() throws Exception {
        ProviderPayment first = draft("10.00", "RACE-A-" + UUID.randomUUID());
        ProviderPayment second = draft("10.00", "RACE-B-" + UUID.randomUUID());
        Long previewAccountVersion = account.getVersion();
        long ledgerBefore = transactions.count();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> postAfterGate(first, previewAccountVersion, ready, start));
            var two = pool.submit(() -> postAfterGate(second, previewAccountVersion, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean onePosted = one.get(20, TimeUnit.SECONDS);
            boolean twoPosted = two.get(20, TimeUnit.SECONDS);
            assertThat(onePosted ^ twoPosted).isTrue();
        }

        assertThat(transactions.count()).isEqualTo(ledgerBefore + 1);
        assertThat(accounts.findById(account.getId()).orElseThrow().getTotalPaid())
                .isEqualByComparingTo("10.00");
        assertThat(payments.findById(first.getId()).orElseThrow().getStatus() == ProviderPayment.Status.POSTED
                ^ payments.findById(second.getId()).orElseThrow().getStatus() == ProviderPayment.Status.POSTED)
                .isTrue();
    }

    private boolean postAfterGate(ProviderPayment payment, Long previewAccountVersion,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) return false;
            service.post(payment.getId(), payment.getIdempotencyKey(), payment.getVersion(),
                    previewAccountVersion, "accountant", 77L);
            return true;
        } catch (Exception expectedConflict) {
            return false;
        }
    }

    private ProviderPayment draft(String amount, String key) {
        return payments.saveAndFlush(ProviderPayment.builder().providerId(providerId)
                .amount(new BigDecimal(amount)).paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER).idempotencyKey(key)
                .status(ProviderPayment.Status.DRAFT).build());
    }
}
