package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Side-by-side view of the four independent figures that must agree for one
 * provider, plus every discrepancy found between them.
 *
 * The phase-0 audit proved these can disagree in production: 257.00 of payment
 * documents existed against zero ledger entries, while the account reported
 * 2,012.00 paid — three different numbers, none derivable from the others.
 * This report exists so such a state is stated as an amount rather than
 * discovered months later through a complaint.
 *
 * Read-only: computing a reconciliation never writes. Corrections go through the
 * explicit adjustment operation, which leaves its own ledger entry.
 */
@Data
@Builder
public class ProviderReconciliationDto {

    public enum Finding {
        /** Documents, ledger and account all agree. */
        MATCHED,
        /** A DRAFT payment exists — expected while preparing, reportable if stale. */
        UNPOSTED_PAYMENT,
        /** Ledger movement with no payment document behind it. */
        LEDGER_WITHOUT_DOCUMENT,
        /** Posted payment document with no ledger movement. */
        DOCUMENT_WITHOUT_LEDGER,
        /** Posted payments are not fully attributed to periods. */
        UNDER_ALLOCATED,
        /** Allocations exceed the payments that carry them (should be impossible). */
        OVER_ALLOCATED,
        /** account.totalPaid disagrees with the ledger. */
        BALANCE_DRIFT,
        /** runningBalance != totalApproved - totalPaid. */
        BALANCE_EQUATION_BROKEN,
        /** Paid more than approved: a genuine credit, surfaced not clamped. */
        PROVIDER_CREDIT_BALANCE
    }

    private Long providerId;
    private String providerName;
    private Long providerAccountId;

    /** Σ posted payment documents. */
    private BigDecimal documentsTotal;
    private Long documentsCount;

    /** Σ PROVIDER_PAYMENT debits − Σ PROVIDER_PAYMENT_REVERSAL credits. */
    private BigDecimal ledgerNet;
    private Long ledgerEntryCount;

    /** The stored cumulative figure on the account. */
    private BigDecimal accountTotalPaid;

    private BigDecimal accountTotalApproved;
    private BigDecimal accountRunningBalance;

    /** Σ allocations belonging to posted payments. */
    private BigDecimal allocatedTotal;

    /** Posted money not yet attributed to any period. */
    private BigDecimal unallocatedTotal;

    /** Value of payments still in DRAFT — no ledger effect yet. */
    private BigDecimal draftTotal;
    private Long draftCount;

    // ── Differences: the whole point of the report ────────────────────────────

    /** documentsTotal − ledgerNet. Non-zero means a document/ledger split. */
    private BigDecimal documentVsLedgerDrift;

    /** ledgerNet − accountTotalPaid. Non-zero means the stored total is wrong. */
    private BigDecimal ledgerVsAccountDrift;

    /** runningBalance − (totalApproved − totalPaid). Non-zero breaks the equation. */
    private BigDecimal balanceEquationDrift;

    /** Negative running balance = money owed back by the provider. */
    private BigDecimal creditBalance;

    private List<Finding> findings;

    /** True only when every figure agrees and nothing needs attention. */
    public boolean isReconciled() {
        return findings != null && findings.size() == 1 && findings.contains(Finding.MATCHED);
    }

    /**
     * True when a difference exists that a reversal cannot be performed against —
     * the state that currently blocks {@code ProviderPaymentReversalService}.
     */
    public boolean requiresApprovedAdjustment() {
        return findings != null && (findings.contains(Finding.BALANCE_DRIFT)
                || findings.contains(Finding.BALANCE_EQUATION_BROKEN));
    }
}
