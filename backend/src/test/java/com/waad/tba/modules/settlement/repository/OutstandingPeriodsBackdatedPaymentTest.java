package com.waad.tba.modules.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository.OutstandingPeriod;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;
import com.waad.tba.modules.settlement.service.ProviderPaymentPostingService;
import com.waad.tba.modules.settlement.service.ProviderPaymentReversalService;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The outstanding query subtracts already-allocated amounts with the predicate
 * {@code p.payment_date <= :asOfDate}. That date filter is right for a
 * "what was outstanding as of date X" report, but the posting path reuses the
 * same query with asOfDate = the payment's own payment_date to VALIDATE
 * allocations.
 *
 * The consequence: a payment dated earlier than an already-posted one does not
 * see that later payment's allocations, so the same period can be allocated
 * twice. Back-dating is not a hypothetical here — the system explicitly supports
 * backdated claims, so backdated payments settling them are expected too.
 *
 * Rows are inserted with JdbcTemplate rather than through the domain services:
 * this test is about one SQL predicate, and building a full claim through the
 * coverage engine would obscure what is being measured.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class OutstandingPeriodsBackdatedPaymentTest extends PostgresIntegrationTestBase {

    @Autowired ProviderPaymentRepository payments;
    @Autowired ProviderRepository providers;
    @Autowired EmployerRepository employers;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderPaymentPostingService postingService;
    @Autowired ProviderPaymentReversalService reversalService;

    private Long providerId;
    private Long employerId;

    /** Service month under test: June 2026, with 1000.00 genuinely due. */
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 15);
    private static final LocalDate LATER_PAYMENT_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate EARLIER_PAYMENT_DATE = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        providerId = providers.save(Provider.builder().name("Backdate Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("BD-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        employerId = employers.save(Employer.builder().name("Backdate Co " + suffix)
                .code("BD-" + suffix).active(true).build()).getId();

        // active = false deliberately: chk_active_member_requires_policy demands a
        // benefit policy for active members, and the outstanding query joins members
        // only to read employer_id — it never filters on the member's status.
        Long memberId = jdbc.queryForObject("""
                INSERT INTO members (employer_id, full_name, national_number, barcode, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, false, now(), now()) RETURNING id
                """, Long.class, employerId, "Backdate Member " + suffix, "NAT-" + suffix, "BC-" + suffix);

        Long visitId = jdbc.queryForObject("""
                INSERT INTO visits (member_id, provider_id, visit_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 'REGISTERED', now(), now()) RETURNING id
                """, Long.class, memberId, providerId, SERVICE_DATE);

        // One approved claim: 1000.00 owed to this provider for 2026-06.
        jdbc.update("""
                INSERT INTO claims (member_id, visit_id, provider_id, service_date, requested_amount,
                                    approved_amount, net_provider_amount, patient_copay, refused_amount,
                                    company_discount_amount, status, submission_source, encounter_type,
                                    review_paused, pending_recalculation, coverage_version, active,
                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 1000.00, 1000.00, 1000.00, 0.00, 0.00, 0.00,
                        'APPROVED', 'INTERNAL_DIRECT', 'OUTPATIENT', false, false, 1, true, now(), now())
                """, memberId, visitId, providerId, SERVICE_DATE);
    }

    /** A POSTED payment that fully allocates June, but dated in August. */
    private void postLaterPaymentCoveringJune() {
        Long paymentId = jdbc.queryForObject("""
                INSERT INTO provider_payments (provider_id, amount, payment_date, payment_method,
                                               idempotency_key, status, posted_at,
                                               created_at, updated_at, version)
                VALUES (?, 1000.00, ?, 'BANK_TRANSFER', ?, 'DRAFT', now(), now(), now(), 0) RETURNING id
                """, Long.class, providerId, LATER_PAYMENT_DATE, "BD-POST-" + UUID.randomUUID());

        jdbc.update("""
                INSERT INTO provider_payment_allocations (payment_id, employer_id, target_year, target_month,
                                                          amount, allocation_method, created_at, version)
                VALUES (?, ?, 2026, 6, 1000.00, 'AUTO_FIFO', now(), 0)
                """, paymentId, employerId);

        // Flip to POSTED directly: this test measures the query, not the posting
        // service, and the lifecycle trigger only permits DRAFT -> POSTED.
        Long ledgerId = jdbc.queryForObject("""
                INSERT INTO account_transactions (provider_account_id, transaction_type, amount,
                                                  balance_before, balance_after, reference_type,
                                                  reference_id, transaction_date, created_at)
                VALUES ((SELECT id FROM provider_accounts WHERE provider_id = ?), 'DEBIT', 1000.00,
                        1000.00, 0.00, 'PROVIDER_PAYMENT', ?, ?, now()) RETURNING id
                """, Long.class, providerId, paymentId, LATER_PAYMENT_DATE);

        jdbc.update("UPDATE provider_payments SET status = 'POSTED', ledger_transaction_id = ?, posted_by = 'test' WHERE id = ?",
                ledgerId, paymentId);
        jdbc.update("UPDATE provider_accounts SET running_balance = 0.00, total_paid = 1000.00, "
                + "version = version + 1 WHERE provider_id = ?", providerId);
    }

    private BigDecimal juneOutstandingAsOf(LocalDate asOfDate) {
        List<OutstandingPeriod> rows = payments.findOutstandingPeriodsForSuggestion(providerId, asOfDate);
        return rows.stream()
                .filter(r -> r.getTargetYear() == 2026 && r.getTargetMonth() == 6)
                .map(OutstandingPeriod::getOutstandingAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    @Test
    void juneIsFullyOutstandingBeforeAnyPayment() {
        assertThat(juneOutstandingAsOf(LATER_PAYMENT_DATE)).isEqualByComparingTo("1000.00");
    }

    @Test
    void aLaterDatedPostedPaymentClosesJuneWhenViewedFromItsOwnDate() {
        jdbc.update("""
                INSERT INTO provider_accounts (provider_id, running_balance, total_approved, total_paid,
                                               status, created_at, updated_at, version)
                VALUES (?, 1000.00, 1000.00, 0.00, 'ACTIVE', now(), now(), 0)
                """, providerId);
        postLaterPaymentCoveringJune();

        assertThat(juneOutstandingAsOf(LATER_PAYMENT_DATE)).isEqualByComparingTo("0.00");
    }

    @Test
    void postingBackdatedPaymentSubtractsEveryPostedAllocationRegardlessOfPaymentDate() {
        jdbc.update("""
                INSERT INTO provider_accounts (provider_id, running_balance, total_approved, total_paid,
                                               status, created_at, updated_at, version)
                VALUES (?, 1000.00, 1000.00, 0.00, 'ACTIVE', now(), now(), 0)
                """, providerId);
        postLaterPaymentCoveringJune();

        // June is genuinely settled: 1000.00 due, 1000.00 already allocated by a
        // POSTED payment. A payment dated earlier must not be told it is still
        // owed, or the same month gets paid twice.
        BigDecimal postingOutstanding = payments.findOutstandingPeriodsForPosting(providerId, -1L).stream()
                .filter(r -> r.getTargetYear() == 2026 && r.getTargetMonth() == 6)
                .map(OutstandingPeriod::getOutstandingAmount).findFirst().orElse(BigDecimal.ZERO);
        assertThat(postingOutstanding)
                .as("a payment dated before an already-posted one must still see that "
                        + "payment's allocations, otherwise the same period can be allocated twice")
                .isEqualByComparingTo("0.00");

        // Historical suggestion remains historically correct: before the August
        // transfer, June was still outstanding. It must not be reused for posting.
        assertThat(juneOutstandingAsOf(EARLIER_PAYMENT_DATE)).isEqualByComparingTo("1000.00");
    }

    @Test
    void postingServiceRejectsBackdatedDraftForPeriodAlreadyAllocatedByLaterPayment() {
        jdbc.update("""
                INSERT INTO provider_accounts (provider_id, running_balance, total_approved, total_paid,
                                               status, created_at, updated_at, version)
                VALUES (?, 1000.00, 1000.00, 0.00, 'ACTIVE', now(), now(), 0)
                """, providerId);
        postLaterPaymentCoveringJune();

        ProviderPayment draft = ProviderPayment.builder().providerId(providerId)
                .amount(new BigDecimal("1000.00")).paymentDate(EARLIER_PAYMENT_DATE)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .idempotencyKey("BD-DRAFT-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build();
        draft.addAllocation(ProviderPaymentAllocation.builder().employerId(employerId)
                .targetYear(2026).targetMonth(6).amount(new BigDecimal("1000.00")).build());
        draft = payments.saveAndFlush(draft);
        long ledgerBefore = transactions.count();
        var account = accounts.findByProviderId(providerId).orElseThrow();
        ProviderPayment finalDraft = draft;

        assertThatThrownBy(() -> postingService.post(finalDraft.getId(),
                finalDraft.getIdempotencyKey(), finalDraft.getVersion(), account.getVersion(),
                "accountant", 77L)).hasMessageContaining("يتجاوز المستحق الحالي");

        assertThat(payments.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(ProviderPayment.Status.DRAFT);
        assertThat(transactions.count()).isEqualTo(ledgerBefore);
        assertThat(accounts.findById(account.getId()).orElseThrow().getRunningBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void reversingPaymentReopensItsAllocatedPeriodForPosting() {
        jdbc.update("""
                INSERT INTO provider_accounts (provider_id, running_balance, total_approved, total_paid,
                                               status, created_at, updated_at, version)
                VALUES (?, 1000.00, 1000.00, 0.00, 'ACTIVE', now(), now(), 0)
                """, providerId);
        postLaterPaymentCoveringJune();
        ProviderPayment posted = payments.findByProviderIdOrderByPaymentDateDesc(providerId).getFirst();
        var account = accounts.findByProviderId(providerId).orElseThrow();
        assertThat(payments.findOutstandingPeriodsForPosting(providerId, -1L)).isEmpty();

        reversalService.reverse(posted.getId(), "إلغاء حوالة الاختبار", posted.getVersion(),
                account.getVersion(), "supervisor", 88L);

        BigDecimal reopened = payments.findOutstandingPeriodsForPosting(providerId, -1L).stream()
                .filter(r -> r.getEmployerId().equals(employerId)
                        && r.getTargetYear() == 2026 && r.getTargetMonth() == 6)
                .map(OutstandingPeriod::getOutstandingAmount).findFirst().orElse(BigDecimal.ZERO);
        assertThat(reopened).isEqualByComparingTo("1000.00");
    }
}
