package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Append-only, interpretive record of a single applicable limit's state at
 * the moment a claim line was approved: WAAD-FIN-1.0's
 * "claim_line_limit_snapshots" (backend/docs/design/FINANCIAL_CONSTITUTION.md,
 * data-model section). One row per (ClaimLine, applicable limit,
 * calculationVersion) -- including limits that were NOT binding, so a claim
 * remains fully explainable later ("why did this line stop at 500, and what
 * happened to the other applicable limits' balances?").
 *
 * This is deliberately NOT the operational consumption ledger --
 * {@link BenefitBucketConsumption} already serves that purpose, unchanged.
 * The two answer different questions: this table answers "what limits
 * applied and what were their balances", the ledger answers "what is
 * consumed/reserved/reversed right now".
 *
 * Written once, at the final approval gate, inside the same transaction as
 * the line's financial result, the bucket ledger entries, and the status
 * change to APPROVED. Never written at draft time. Never edited or deleted
 * afterward (DB triggers enforce this, matching every other append-only
 * financial table in this system); a corrected calculation writes new rows
 * under a new calculationVersion instead.
 */
@Entity
@Table(name = "claim_line_limit_snapshots")
@Getter @Setter @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
public class ClaimLineLimitSnapshot {

    /** Mirrors WaadFinancialEngine.Result's need to distinguish what kind of limit this is. */
    public enum LimitScopeType { SERVICE, CATEGORY, GROUP, POLICY_GENERAL, FAMILY, LIFETIME }

    /** Where the effective limit value for this row came from. */
    public enum SourceType { POLICY_DEFAULT, EMPLOYER_OVERRIDE, MEMBER_OVERRIDE, PREAUTH_RESERVATION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_line_id")
    private ClaimLine claimLine;

    @Column(name = "calculation_version", nullable = false)
    private Integer calculationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_scope_type", nullable = false, length = 20)
    private LimitScopeType limitScopeType;

    /**
     * Stable identity for this limit independent of whether it is a real
     * bucket row: {@code "BUCKET:" + bucketId} for a real
     * {@link BenefitLimitBucket}, or {@code "POLICY_GENERAL:" + policyId}
     * for the general annual ceiling, which is not always backed by a real
     * bucket row (S4/S15 of the constitution).
     */
    @Column(name = "limit_semantic_key", nullable = false, length = 200)
    private String limitSemanticKey;

    /** Null exactly when limitScopeType == POLICY_GENERAL (DB-enforced, see the CHECK constraint). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id")
    private BenefitLimitBucket bucket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id")
    private BenefitPolicy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_rule_id")
    private BenefitPolicyRule benefitRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_group_id")
    private BenefitGroup benefitGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    /** Polymorphic reference to the override/reservation row identified by sourceType; no FK by design. */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_version")
    private Long sourceVersion;

    @Column(name = "structure_revision")
    private Integer structureRevision;

    /**
     * Denormalized copy of the governing bucket's period type at snapshot
     * time -- not a live reference, so this table never needs a migration in
     * lockstep with {@link BenefitLimitBucket}'s own period-type list.
     */
    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "effective_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal effectiveLimit;

    @Column(name = "consumed_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal consumedBefore;

    @Column(name = "reserved_before", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal reservedBefore = BigDecimal.ZERO;

    @Column(name = "binding_available_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal bindingAvailableBefore;

    /** WaadFinancialEngine.Result.settlementBase for this line -- repeated on every row for this line; do not sum across rows. */
    @Column(name = "line_settlement_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineSettlementBase;

    /** WaadFinancialEngine.Result.insideLimit for this line -- repeated on every row for this line; do not sum across rows. */
    @Column(name = "line_inside_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineInsideLimit;

    @Column(name = "limit_consumption", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitConsumption;

    /** WaadFinancialEngine.Result.patientLimitExcess for this line -- repeated on every row for this line; do not sum across rows. */
    @Column(name = "patient_limit_excess", nullable = false, precision = 15, scale = 2)
    private BigDecimal patientLimitExcess;

    @Column(name = "available_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal availableAfter;

    /** True for exactly the one limit (across this line's rows) that actually constrained the result. */
    @Column(name = "is_binding", nullable = false)
    @Builder.Default
    private boolean binding = false;

    @Column(name = "consumption_order", nullable = false)
    @Builder.Default
    private Integer consumptionOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
