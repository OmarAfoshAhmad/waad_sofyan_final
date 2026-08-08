package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "benefit_limit_buckets", uniqueConstraints =
        @UniqueConstraint(name = "uq_benefit_bucket_policy_code", columnNames = {"policy_id", "code"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BenefitLimitBucket {

    /**
     * STANDARD: a real, independent limit. POLICY_GENERAL_MIRROR: this bucket
     * duplicates policy.annualLimit and must never be treated as an
     * independent limit by any resolver -- see V146 and
     * ApplicableLimitResolver.
     */
    public enum LimitRole { STANDARD, POLICY_GENERAL_MIRROR }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false) private BenefitPolicy policy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_group_id", nullable = false) private BenefitGroup benefitGroup;
    @Column(nullable = false, length = 50) private String code;
    @Column(name = "name_ar", nullable = false, length = 255) private String nameAr;
    @Enumerated(EnumType.STRING) @Column(name = "context_type", nullable = false)
    @Builder.Default private EncounterType contextType = EncounterType.ANY;
    @Column(name = "amount_limit", precision = 15, scale = 2) private BigDecimal amountLimit;
    @Column(name = "times_limit") private Integer timesLimit;
    @Column(name = "days_limit") private Integer daysLimit;
    @Enumerated(EnumType.STRING) @Column(name = "period_type", nullable = false)
    @Builder.Default private LimitPeriodType periodType = LimitPeriodType.POLICY_PERIOD;
    @Column(name = "period_value") @Builder.Default private Integer periodValue = 1;
    @Enumerated(EnumType.STRING) @Column(name = "counting_method", nullable = false)
    @Builder.Default private CountingMethod countingMethod = CountingMethod.EACH_LINE;
    @Enumerated(EnumType.STRING) @Column(name = "consumption_basis", nullable = false)
    @Builder.Default private ConsumptionBasis consumptionBasis = ConsumptionBasis.COMPANY_SHARE;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_bucket_id") private BenefitLimitBucket parentBucket;
    @Enumerated(EnumType.STRING) @Column(name = "limit_role", nullable = false, length = 30)
    @Builder.Default private LimitRole limitRole = LimitRole.STANDARD;
    /** Explicit medical meaning; never inferred from parent depth. Mirrors are excluded from resolution. */
    @Enumerated(EnumType.STRING) @Column(name = "benefit_scope_type", length = 20)
    private BenefitScopeType benefitScopeType;
    /** Who shares the accumulator. FAMILY remains DB-disabled until its policy is implemented. */
    @Enumerated(EnumType.STRING) @Column(name = "beneficiary_scope_type", nullable = false, length = 20)
    @Builder.Default private BeneficiaryScopeType beneficiaryScopeType = BeneficiaryScopeType.MEMBER;
    @Column(nullable = false) @Builder.Default private boolean shared = false;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
