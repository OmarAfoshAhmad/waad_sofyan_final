package com.waad.tba.modules.settlement.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An immutable record of one approved correction to {@link ProviderAccount#getTotalPaid()}.
 *
 * Deliberately not an {@link AccountTransaction}. A correction's amount is, by
 * definition, exactly the drift {@code ProviderAccountReconciliationService}
 * measured between the ledger and the account — writing it into the same ledger
 * that drift was measured against would make the next reconciliation see the
 * correction itself as new drift of the same size. This table exists so the
 * "why did totalPaid change" audit trail can never be mistaken for, or summed
 * into, money movement.
 */
@Entity
@Table(name = "provider_account_reconciliation_audits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderAccountReconciliationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_account_id", nullable = false)
    private Long providerAccountId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** Signed: positive raised totalPaid, negative lowered it. */
    @Column(name = "adjustment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal adjustmentAmount;

    @Column(name = "total_paid_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPaidBefore;

    @Column(name = "total_paid_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPaidAfter;

    @Column(name = "running_balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal runningBalanceBefore;

    @Column(name = "running_balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal runningBalanceAfter;

    /** The drift that justified this correction — what was measured, not what was chosen. */
    @Column(name = "ledger_vs_account_drift_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal ledgerVsAccountDriftBefore;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_by_user_id")
    private Long performedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
