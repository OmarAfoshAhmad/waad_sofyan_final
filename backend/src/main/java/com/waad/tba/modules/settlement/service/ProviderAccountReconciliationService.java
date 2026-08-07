package com.waad.tba.modules.settlement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.finance.Money;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto.Finding;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository.ReconciliationRow;

import lombok.RequiredArgsConstructor;

/**
 * Compares the four figures that must agree for a provider and names every
 * difference as an amount.
 *
 * Strictly read-only. Diagnosis and correction are deliberately separate: this
 * service can be run freely without changing anything, and corrections go
 * through {@link ProviderAccountAdjustmentService}, which always leaves a ledger
 * entry with a reason and an actor.
 *
 * This is NOT what {@code ProviderAccountService.recalculateBalance} does. That
 * method only repairs orphaned CLAIM_APPROVAL credits left by deleted claims; it
 * never looks at payment documents, and cannot detect or fix the
 * document/ledger split found by the phase-0 audit.
 */
@Service
@RequiredArgsConstructor
public class ProviderAccountReconciliationService {

    private final ProviderPaymentRepository payments;

    /** Reconciles a single provider. */
    @Transactional(readOnly = true)
    public ProviderReconciliationDto reconcile(Long providerId) {
        List<ProviderReconciliationDto> results = toDtos(payments.findReconciliationRows(providerId));
        if (results.isEmpty()) {
            throw new com.waad.tba.common.exception.BusinessRuleException(
                    "لا يوجد حساب أو دفعات لمقدم الخدمة: " + providerId);
        }
        return results.get(0);
    }

    /** Reconciles every provider that has an account or at least one payment. */
    @Transactional(readOnly = true)
    public List<ProviderReconciliationDto> reconcileAll() {
        return toDtos(payments.findReconciliationRows(null));
    }

    /** Only the providers that need attention — the operational work list. */
    @Transactional(readOnly = true)
    public List<ProviderReconciliationDto> findDiscrepancies() {
        return reconcileAll().stream().filter(r -> !r.isReconciled()).toList();
    }

    private List<ProviderReconciliationDto> toDtos(List<ReconciliationRow> rows) {
        List<ProviderReconciliationDto> results = new ArrayList<>(rows.size());
        for (ReconciliationRow row : rows) {
            results.add(toDto(row));
        }
        return results;
    }

    private ProviderReconciliationDto toDto(ReconciliationRow row) {
        BigDecimal documents = Money.normalize(row.getDocumentsTotal());
        BigDecimal ledgerNet = Money.normalize(row.getLedgerNet());
        BigDecimal totalPaid = Money.normalize(row.getAccountTotalPaid());
        BigDecimal totalApproved = Money.normalize(row.getAccountTotalApproved());
        BigDecimal runningBalance = Money.normalize(row.getAccountRunningBalance());
        BigDecimal allocated = Money.normalize(row.getAllocatedTotal());
        BigDecimal draft = Money.normalize(row.getDraftTotal());

        BigDecimal documentVsLedger = documents.subtract(ledgerNet);
        BigDecimal ledgerVsAccount = ledgerNet.subtract(totalPaid);
        BigDecimal equationDrift = runningBalance.subtract(totalApproved.subtract(totalPaid));
        BigDecimal unallocated = documents.subtract(allocated);
        // A negative running balance means we paid more than we approved: the
        // provider owes it back. Reported as a positive credit rather than clamped.
        BigDecimal credit = runningBalance.signum() < 0 ? runningBalance.negate() : Money.ZERO;

        List<Finding> findings = classify(row, documents, ledgerNet, documentVsLedger,
                ledgerVsAccount, equationDrift, unallocated, credit, draft);

        return ProviderReconciliationDto.builder()
                .providerId(row.getProviderId())
                .providerName(row.getProviderName())
                .providerAccountId(row.getProviderAccountId())
                .documentsTotal(documents)
                .documentsCount(row.getDocumentsCount())
                .ledgerNet(ledgerNet)
                .ledgerEntryCount(row.getLedgerEntryCount())
                .accountTotalPaid(totalPaid)
                .accountTotalApproved(totalApproved)
                .accountRunningBalance(runningBalance)
                .allocatedTotal(allocated)
                .unallocatedTotal(unallocated)
                .draftTotal(draft)
                .draftCount(row.getDraftCount())
                .documentVsLedgerDrift(documentVsLedger)
                .ledgerVsAccountDrift(ledgerVsAccount)
                .balanceEquationDrift(equationDrift)
                .creditBalance(credit)
                .findings(findings)
                .build();
    }

    private List<Finding> classify(ReconciliationRow row, BigDecimal documents, BigDecimal ledgerNet,
            BigDecimal documentVsLedger, BigDecimal ledgerVsAccount, BigDecimal equationDrift,
            BigDecimal unallocated, BigDecimal credit, BigDecimal draft) {

        List<Finding> findings = new ArrayList<>();

        if (documentVsLedger.signum() != 0) {
            // Which side is missing changes what the operator must do, so the two
            // directions are reported as different findings rather than one drift.
            findings.add(documentVsLedger.signum() > 0
                    ? Finding.DOCUMENT_WITHOUT_LEDGER
                    : Finding.LEDGER_WITHOUT_DOCUMENT);
        }
        if (ledgerVsAccount.signum() != 0) {
            findings.add(Finding.BALANCE_DRIFT);
        }
        if (equationDrift.signum() != 0) {
            findings.add(Finding.BALANCE_EQUATION_BROKEN);
        }
        if (unallocated.signum() > 0) {
            findings.add(Finding.UNDER_ALLOCATED);
        } else if (unallocated.signum() < 0) {
            // The database forbids this per payment; seeing it in aggregate means
            // something bypassed the constraint and must be investigated, not fixed
            // by a balancing entry.
            findings.add(Finding.OVER_ALLOCATED);
        }
        if (credit.signum() > 0) {
            findings.add(Finding.PROVIDER_CREDIT_BALANCE);
        }
        if (draft.signum() > 0) {
            findings.add(Finding.UNPOSTED_PAYMENT);
        }
        if (findings.isEmpty()) {
            findings.add(Finding.MATCHED);
        }
        return findings;
    }
}
