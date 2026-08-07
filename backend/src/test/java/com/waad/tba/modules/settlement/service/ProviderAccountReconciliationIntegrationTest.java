package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto.Finding;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountReconciliationAuditRepository;
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
    @Autowired ProviderAccountReconciliationAuditRepository reconciliationAudits;
    @Autowired ProviderAccountService providerAccountService;
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
        assertThat(reconciliationAudits.findById(result.getReconciliationAuditId())).isPresent();

        // After: the same reversal now succeeds, so the guard was never a dead end.
        ProviderAccount corrected = accounts.findById(account.getId()).orElseThrow();
        ProviderPayment reloaded = payments.findById(posted.getId()).orElseThrow();
        assertThatCode(() -> reversal.reverse(reloaded.getId(), "تصحيح بعد التسوية",
                reloaded.getVersion(), corrected.getVersion(), "supervisor", 88L))
                .doesNotThrowAnyException();
    }

    @Test
    void adjustmentLeavesAnAuditableTrailWithoutTouchingTheLedgerAndKeepsTheBalanceEquation() {
        postedPayment("400.00");
        injectHistoricalDrift("900.00", "100.00");
        ProviderAccount drifted = accounts.findById(account.getId()).orElseThrow();
        long ledgerBefore = transactions.count();
        BigDecimal ledgerNetBefore = reconciliation.reconcile(providerId).getLedgerNet();

        var result = adjustment.alignPaidTotalWithLedger(providerId,
                "فرق موروث من النظام السابق", drifted.getVersion(), "supervisor", 88L);

        // The correction must never write to the financial ledger — that is exactly
        // the loop that would make it recreate the drift it just closed.
        assertThat(transactions.count()).isEqualTo(ledgerBefore);
        assertThat(reconciliationAudits.findById(result.getReconciliationAuditId())).isPresent();
        assertThat(result.getAdjustmentAmount()).isEqualByComparingTo("-500.00");

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getTotalPaid()).isEqualByComparingTo("400.00");
        // The equation must survive the correction, not be traded away for it.
        assertThat(after.getRunningBalance())
                .isEqualByComparingTo(after.getTotalApproved().subtract(after.getTotalPaid()));
        ProviderReconciliationDto reconciled = reconciliation.reconcile(providerId);
        assertThat(reconciled.getLedgerVsAccountDrift()).isEqualByComparingTo("0.00");
        // ledgerNet itself never moved — only the account's derived totalPaid did.
        assertThat(reconciled.getLedgerNet()).isEqualByComparingTo(ledgerNetBefore);
    }

    /**
     * The exact scenario the correction exists to protect against: a naive
     * implementation that logs the correction as an ordinary ledger entry would
     * make the SECOND, independent reconciliation see the correction itself as
     * new drift of the same size. Each call below runs and commits in its own
     * transaction (no surrounding @Transactional on this test), so this is a
     * genuine round trip, not one connection's cached view.
     */
    @Test
    void reconciliationIsStableAcrossASecondIndependentRunAfterAdjustment() {
        postedPayment("400.00");
        injectHistoricalDrift("900.00", "100.00");
        ProviderAccount drifted = accounts.findById(account.getId()).orElseThrow();

        adjustment.alignPaidTotalWithLedger(providerId,
                "تسوية انحراف تاريخي", drifted.getVersion(), "supervisor", 88L);

        ProviderReconciliationDto second = reconciliation.reconcile(providerId);

        // postedPayment() never allocates the payment to a period, so UNDER_ALLOCATED
        // legitimately remains — it is orthogonal to the drift this test closes.
        assertThat(second.getFindings()).doesNotContain(Finding.BALANCE_DRIFT,
                Finding.DOCUMENT_WITHOUT_LEDGER, Finding.LEDGER_WITHOUT_DOCUMENT,
                Finding.BALANCE_EQUATION_BROKEN);
        assertThat(second.getAccountTotalPaid()).isEqualByComparingTo(second.getLedgerNet());

        // The account_transactions history stays an unbroken chain: every entry's
        // balanceBefore equals the previous entry's balanceAfter.
        List<AccountTransaction> ledger = transactions
                .findByProviderAccountIdOrderByCreatedAtDesc(account.getId(),
                        org.springframework.data.domain.Pageable.unpaged())
                .stream().sorted(java.util.Comparator.comparing(AccountTransaction::getId)).toList();
        BigDecimal running = null;
        for (AccountTransaction tx : ledger) {
            if (running != null) {
                assertThat(tx.getBalanceBefore()).isEqualByComparingTo(running);
            }
            running = tx.getBalanceAfter();
        }
        ProviderAccount finalAccount = accounts.findById(account.getId()).orElseThrow();
        assertThat(running).isEqualByComparingTo(finalAccount.getRunningBalance());
    }

    // ── تجميد المسار القديم ──────────────────────────────────────────────────

    @Test
    void frozenInstallmentPathRefusesToWriteAndLeavesTheAccountUntouched() {
        ProviderAccount before = accounts.findById(account.getId()).orElseThrow();

        assertThatThrownBy(() -> providerAccountService.debitOnInstallmentPayment(
                providerId, new BigDecimal("50.00"), "قسط", 88L))
                .hasMessageContaining("مسار دفعات مقدم الخدمة الجديد");

        assertThatThrownBy(() -> providerAccountService.settleRemainingBalanceByProvider(
                providerId, "تسوية يدوية", 88L))
                .hasMessageContaining("مسار دفعات مقدم الخدمة الجديد");

        ProviderAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getTotalPaid()).isEqualByComparingTo(before.getTotalPaid());
        assertThat(transactions.findByProviderAccountIdOrderByCreatedAtDesc(account.getId(),
                org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    /** Simulates data the frozen legacy path (or any other ADJUSTMENT writer) left behind. */
    @Test
    void historicalAdjustmentEntryIsNeverCountedAsAProviderPaymentByReconciliation() {
        BigDecimal balanceBefore = account.getRunningBalance();
        transactions.saveAndFlush(AccountTransaction.createAdjustment(
                account.getId(), new BigDecimal("250.00"), false, balanceBefore,
                "دفعة قسطية قديمة (بيانات محاكاة)", 88L));

        ProviderReconciliationDto report = reconciliation.reconcile(providerId);

        // ledgerNet only ever counts PROVIDER_PAYMENT/PROVIDER_PAYMENT_REVERSAL —
        // a legacy ADJUSTMENT-typed entry is invisible to it by construction.
        assertThat(report.getLedgerNet()).isEqualByComparingTo("0.00");
        assertThat(report.getDocumentsTotal()).isEqualByComparingTo("0.00");
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
