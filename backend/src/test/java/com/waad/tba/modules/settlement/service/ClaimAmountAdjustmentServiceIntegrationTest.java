package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.event.ClaimAmountAdjustedEvent;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Phase 7.1 — a change to an already-approved claim's amount must move only
 * totalApproved and runningBalance. The prior implementation used
 * account.debit() on a decrease, which raised totalPaid as if a payment had
 * occurred — corrupting reconciliation and able to block a legitimate reversal
 * under the Phase 6 guard.
 *
 * Tests call the listener directly with hand-built events: the service never
 * reads the claims table, so a full Claim fixture would add nothing.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimAmountAdjustmentServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ClaimAmountAdjustmentService adjustmentService;
    @Autowired ProviderAccountReconciliationService reconciliation;
    @Autowired ProviderPaymentPostingService posting;
    @Autowired ProviderPaymentReversalService reversal;
    @Autowired ProviderPaymentRepository payments;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderRepository providers;

    private Long providerId;
    private ProviderAccount account;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        providerId = providers.save(Provider.builder().name("Adj Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("ADJ-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        account = accounts.save(ProviderAccount.builder().providerId(providerId)
                .runningBalance(new BigDecimal("100.00"))
                .totalApproved(new BigDecimal("100.00")).totalPaid(BigDecimal.ZERO).build());
    }

    private ClaimAmountAdjustedEvent event(Long claimId, String oldAmount, String newAmount, long claimVersion) {
        return new ClaimAmountAdjustedEvent(this, claimId, providerId,
                new BigDecimal(oldAmount), new BigDecimal(newAmount), 88L, claimVersion);
    }

    @Test
    void reducingAnApprovedClaimLowersApprovedAndBalanceOnlyNotTotalPaid() {
        adjustmentService.onClaimAmountAdjusted(event(501L, "100.00", "70.00", 2L));

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalApproved()).isEqualByComparingTo("70.00");
        assertThat(after.getRunningBalance()).isEqualByComparingTo("70.00");
        assertThat(after.getTotalPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void increasingAnApprovedClaimRaisesApprovedAndBalanceOnly() {
        adjustmentService.onClaimAmountAdjusted(event(502L, "70.00", "120.00", 2L));

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalApproved()).isEqualByComparingTo("150.00"); // 100 + 50
        assertThat(after.getRunningBalance()).isEqualByComparingTo("150.00");
        assertThat(after.getTotalPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void existingPaymentsAndANegativeBalanceDoNotBlockReducingApproval() {
        // Pay the full 100 the account is currently owed, driving totalPaid to 100
        // and leaving a legitimate credit balance once the claim is reduced.
        ProviderPayment draft = payments.saveAndFlush(ProviderPayment.builder()
                .providerId(providerId).amount(new BigDecimal("100.00")).paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER).idempotencyKey("ADJ-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build());
        posting.post(draft.getId(), draft.getIdempotencyKey(), draft.getVersion(),
                accounts.findById(account.getId()).orElseThrow().getVersion(), "accountant", 77L);

        assertThatCode(() -> adjustmentService.onClaimAmountAdjusted(event(503L, "100.00", "40.00", 2L)))
                .doesNotThrowAnyException();

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalApproved()).isEqualByComparingTo("40.00");
        assertThat(after.getTotalPaid()).isEqualByComparingTo("100.00"); // untouched by the adjustment
        assertThat(after.getRunningBalance()).isEqualByComparingTo("-60.00"); // 40 - 100, a real credit
    }

    @Test
    void redeliveringTheSameClaimVersionDoesNotApplyTheDeltaTwice() {
        ClaimAmountAdjustedEvent evt = event(504L, "100.00", "70.00", 2L);

        adjustmentService.onClaimAmountAdjusted(evt);
        adjustmentService.onClaimAmountAdjusted(evt); // redelivery, same claimId + claimVersion

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalApproved()).isEqualByComparingTo("70.00"); // not 40
        assertThat(transactions.countByReferenceTypeAndReferenceId(
                com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType.CLAIM_AMOUNT_ADJUSTMENT,
                504L)).isEqualTo(1L);
    }

    @Test
    void twoDistinctAdjustmentsToTheSameClaimBothApplyNeitherIsLost() {
        // Two genuine, sequential edits to the same claim — distinct claimVersions.
        adjustmentService.onClaimAmountAdjusted(event(505L, "100.00", "70.00", 2L));  // -30
        adjustmentService.onClaimAmountAdjusted(event(505L, "70.00", "90.00", 3L));   // +20

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalApproved()).isEqualByComparingTo("90.00"); // 100 - 30 + 20
        assertThat(transactions.countByReferenceTypeAndReferenceId(
                com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType.CLAIM_AMOUNT_ADJUSTMENT,
                505L)).isEqualTo(2L);
    }

    @Test
    void reconciliationStaysFreeOfDriftAfterAnApprovedAmountAdjustment() {
        ProviderReconciliationDto before = reconciliation.reconcile(providerId);

        adjustmentService.onClaimAmountAdjusted(event(506L, "100.00", "60.00", 2L));

        ProviderReconciliationDto after = reconciliation.reconcile(providerId);
        // The adjustment never touches totalPaid or the PROVIDER_PAYMENT ledger, so
        // ledgerNet and the drift findings must be exactly as before.
        assertThat(after.getLedgerNet()).isEqualByComparingTo(before.getLedgerNet());
        assertThat(after.getFindings()).doesNotContain(
                ProviderReconciliationDto.Finding.BALANCE_DRIFT,
                ProviderReconciliationDto.Finding.BALANCE_EQUATION_BROKEN);
    }

    @Test
    void reversingAPaymentLaterIsNotBlockedByAFalseDriftFromAnEarlierAdjustment() {
        ProviderPayment posted = payments.saveAndFlush(ProviderPayment.builder()
                .providerId(providerId).amount(new BigDecimal("100.00")).paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER).idempotencyKey("ADJ-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build());
        posting.post(posted.getId(), posted.getIdempotencyKey(), posted.getVersion(),
                accounts.findById(account.getId()).orElseThrow().getVersion(), "accountant", 77L);

        // Unrelated claim-amount adjustment on the same provider, after the payment.
        adjustmentService.onClaimAmountAdjusted(event(507L, "100.00", "150.00", 2L));

        ProviderPayment reloaded = payments.findById(posted.getId()).orElseThrow();
        ProviderAccount current = accounts.findById(account.getId()).orElseThrow();

        assertThatCode(() -> reversal.reverse(reloaded.getId(), "اختبار",
                reloaded.getVersion(), current.getVersion(), "supervisor", 88L))
                .doesNotThrowAnyException();
    }
}
