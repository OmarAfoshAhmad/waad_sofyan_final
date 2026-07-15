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
    @Column(nullable = false) @Builder.Default private boolean shared = false;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
