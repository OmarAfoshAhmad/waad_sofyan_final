package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto.Finding;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Reconciliation names every disagreement between the four figures, and the
 * adjustment closes the one that blocks reversal.
 *
 * The drift reproduced here is the real one from the phase-0 audit: an account
 * reporting a paid total that neither the payment documents nor the ledger
 * explain. That state currently makes a provider's payments impossible to
 * reverse, so this pair of services is what stops the reversal guard from being
 * a dead end.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderAccountReconciliationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ProviderAccountReconciliationService reconciliation;
    @Autowired ProviderAccountAdjustmentService adjustment;
    @Autowired ProviderPaymentPostingService posting;
    @Autowired ProviderPaymentReversalService reversal;
    @Autowired ProviderPaymentRepository payments;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderRepository providers;
    @Autowired JdbcTemplate jdbc;

    private Long providerId;
    private ProviderAccount account;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        providerId = providers.save(Provider.builder().name("Recon Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("REC-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        account = accounts.save(ProviderAccount.builder().providerId(providerId)
                .runningBalance(new BigDecimal("1000.00"))
                .totalApproved(new BigDecimal("1000.00")).totalPaid(BigDecimal.ZERO).build());
    }

    private ProviderPayment postedPayment(String amount) {
        ProviderPayment draft = payments.saveAndFlush(ProviderPayment.builder()
                .providerId(providerId).amount(new BigDecimal(amount)).paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .idempotencyKey("REC-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build());
        posting.post(draft.getId(), draft.getIdempotencyKey(), draft.getVersion(),
                accounts.findById(account.getId()).orElseThrow().getVersion(), "accountant", 77L);
        return payments.findById(draft.getId()).orElseThrow();
    }

    /** Reproduces the audited state: totalPaid explained by nothing. */
    private void injectHistoricalDrift(String newTotalPaid, String newRunningBalance) {
        jdbc.update("UPDATE provider_accounts SET total_paid = ?::numeric, running_balance = ?::numeric, "
                + "version = version + 1 WHERE id = ?", newTotalPaid, newRunningBalance, account.getId());
    }

    // ── التشخيص ──────────────────────────────────────────────────────────────

    @Test
    void aProviderWithNoActivityReconcilesCleanly() {
        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        assertThat(report.getFindings()).containsExactly(Finding.MATCHED);
        assertThat(report.isReconciled()).isTrue();
        assertThat(report.getDocumentsTotal()).isEqualByComparingTo("0.00");
        assertThat(report.getLedgerNet()).isEqualByComparingTo("0.00");
    }

    @Test
    void aPostedPaymentKeepsDocumentsLedgerAndAccountInAgreement() {
        postedPayment("400.00");

        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        assertThat(report.getDocumentsTotal()).isEqualByComparingTo("400.00");
        assertThat(report.getLedgerNet()).isEqualByComparingTo("400.00");
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo("400.00");
        assertThat(report.getDocumentVsLedgerDrift()).isEqualByComparingTo("0.00");
        assertThat(report.getLedgerVsAccountDrift()).isEqualByComparingTo("0.00");
        // Nothing was allocated to a period, and that is reported rather than hidden.
        assertThat(report.getUnallocatedTotal()).isEqualByComparingTo("400.00");
        assertThat(report.getFindings()).contains(Finding.UNDER_ALLOCATED);
    }

    @Test
    void aReversedPaymentNetsOutOnBothSides() {
        ProviderPayment posted = postedPayment("400.00");
        ProviderAccount current = accounts.findById(account.getId()).orElseThrow();
        reversal.reverse(posted.getId(), "تصحيح", posted.getVersion(), current.getVersion(),
                "supervisor", 88L);

        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        // The document no longer counts and the ledger nets to zero through its
        // compensating credit, so the two sides stay comparable.
        assertThat(report.getDocumentsTotal()).isEqualByComparingTo("0.00");
        assertThat(report.getLedgerNet()).isEqualByComparingTo("0.00");
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo("0.00");
        assertThat(report.getLedgerEntryCount()).isEqualTo(2L); // debit + reversing credit
        assertThat(report.isReconciled()).isTrue();
    }

    @Test
    void aDraftIsReportedWithoutAffectingLedgerOrAccount() {
        payments.saveAndFlush(ProviderPayment.builder().providerId(providerId)
                .amount(new BigDecimal("250.00")).paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .idempotencyKey("DRAFT-" + UUID.randomUUID())
                .status(ProviderPayment.Status.DRAFT).build());

        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        assertThat(report.getDraftTotal()).isEqualByComparingTo("250.00");
        assertThat(report.getDraftCount()).isEqualTo(1L);
        assertThat(report.getLedgerNet()).isEqualByComparingTo("0.00");
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo("0.00");
        assertThat(report.getFindings()).containsExactly(Finding.UNPOSTED_PAYMENT);
    }

    @Test
    void historicalDriftIsNamedAsAnAmountNotHidden() {
        postedPayment("400.00");
        injectHistoricalDrift("900.00", "100.00"); // ledger says 400, account claims 900

        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        assertThat(report.getLedgerNet()).isEqualByComparingTo("400.00");
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo("900.00");
        assertThat(report.getLedgerVsAccountDrift()).isEqualByComparingTo("-500.00");
        assertThat(report.getFindings()).contains(Finding.BALANCE_DRIFT);
        assertThat(report.requiresApprovedAdjustment()).isTrue();
        assertThat(report.isReconciled()).isFalse();
    }

    @Test
    void payingMoreThanApprovedIsSurfacedAsACreditNotClampedToZero() {
        postedPayment("1500.00"); // approved is only 1000

        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        assertThat(report.getAccountRunningBalance()).isEqualByComparingTo("-500.00");
        assertThat(report.getCreditBalance()).isEqualByComparingTo("500.00");
        assertThat(report.getFindings()).contains(Finding.PROVIDER_CREDIT_BALANCE);
    }

    @Test
    void findDiscrepanciesReturnsOnlyProvidersNeedingAttention() {
        postedPayment("400.00");
        injectHistoricalDrift("900.00", "100.00");

        assertThat(reconciliation.findDiscrepancies())
                .anyMatch(r -> r.getProviderId().equals(providerId)
                        && r.getFindings().contains(Finding.BALANCE_DRIFT));
    }

    // ── العلاج: المخرج الذي يفتحه إجراء التسوية ──────────────────────────────

    @Test
    void driftBlocksReversalUntilAnApprovedAdjustmentClosesIt() {
        ProviderPayment posted = postedPayment("400.00");
        injectHistoricalDrift("100.00", "900.00"); // account claims less was paid than the payment itself

        // Before: the guard refuses, exactly as designed.
        ProviderAccount drifted = accounts.findById(account.getId()).orElseThrow();
        assertThatThrownBy(() -> reversal.reverse(posted.getId(), "تصحيح",
                posted.getVersion(), drifted.getVersion(), "supervisor", 88L))
                .hasMessageContaining("انحراف مالي");

        // The sanctioned correction, sized by the measured drift rather than by the caller.
        var result = adjustment.alignPaidTotalWithLedger(providerId,
                "تسوية انحراف تاريخي بعد مطابقة الحساب", drifted.getVersion(), "supervisor", 88L);

        assertThat(result.getAdjustmentAmount()).isEqualByComparingTo("300.00"); // 400 ledger - 100 account
        assertThat(result.getTotalPaidAfter()).isEqualByComparingTo("400.00");
        assertThat(result.getLedgerVsAccountDriftAfter()).isEqualByComparingTo("0.00");
        assertThat(transactions.findById(result.getLedgerTransactionId())).isPresent();

        // After: the same reversal now succeeds, so the guard was never a dead end.
        ProviderAccount corrected = accounts.findById(account.getId()).orElseThrow();
        ProviderPayment reloaded = payments.findById(posted.getId()).orElseThrow();
        assertThatCode(() -> reversal.reverse(reloaded.getId(), "تصحيح بعد التسوية",
                reloaded.getVersion(), corrected.getVersion(), "supervisor", 88L))
                .doesNotThrowAnyException();
    }

    @Test
    void adjustmentLeavesAnAuditableLedgerEntryAndKeepsTheBalanceEquation() {
        postedPayment("400.00");
        injectHistoricalDrift("900.00", "100.00");
        ProviderAccount drifted = accounts.findById(account.getId()).orElseThrow();
        long ledgerBefore = transactions.count();

        var result = adjustment.alignPaidTotalWithLedger(providerId,
                "فرق موروث من النظام السابق", drifted.getVersion(), "supervisor", 88L);

        assertThat(transactions.count()).isEqualTo(ledgerBefore + 1);
        assertThat(result.getAdjustmentAmount()).isEqualByComparingTo("-500.00");

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalPaid()).isEqualByComparingTo("400.00");
        // The equation must survive the correction, not be traded away for it.
        assertThat(after.getRunningBalance())
                .isEqualByComparingTo(after.getTotalApproved().subtract(after.getTotalPaid()));
        assertThat(reconciliation.reconcile(providerId).getLedgerVsAccountDrift())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void adjustmentIsRefusedWhenThereIsNothingToCorrect() {
        postedPayment("400.00");
        ProviderAccount clean = accounts.findById(account.getId()).orElseThrow();

        assertThatThrownBy(() -> adjustment.alignPaidTotalWithLedger(providerId,
                "بلا سبب حقيقي", clean.getVersion(), "supervisor", 88L))
                .hasMessageContaining("لا يوجد انحراف");
    }

    @Test
    void adjustmentRequiresAReasonAndAStaleVersionIsRejected() {
        postedPayment("400.00");
        injectHistoricalDrift("900.00", "100.00");
        ProviderAccount drifted = accounts.findById(account.getId()).orElseThrow();
        long ledgerBefore = transactions.count();

        assertThatThrownBy(() -> adjustment.alignPaidTotalWithLedger(providerId, "   ",
                drifted.getVersion(), "supervisor", 88L))
                .hasMessageContaining("سبب التسوية");

        assertThatThrownBy(() -> adjustment.alignPaidTotalWithLedger(providerId, "سبب صحيح",
                drifted.getVersion() + 99, "supervisor", 88L))
                .hasMessageContaining("تغيّر منذ المطابقة");

        assertThat(transactions.count()).isEqualTo(ledgerBefore);
        assertThat(accounts.findById(account.getId()).orElseThrow().getTotalPaid())
                .isEqualByComparingTo("900.00");
    }
}
