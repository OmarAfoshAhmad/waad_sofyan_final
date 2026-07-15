package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.benefitpolicy.enums.ConsumptionMode;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "benefit_rule_buckets", uniqueConstraints =
        @UniqueConstraint(name = "uq_benefit_rule_bucket", columnNames = {"rule_id", "bucket_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BenefitRuleBucket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false) private BenefitPolicyRule rule;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bucket_id", nullable = false) private BenefitLimitBucket bucket;
    @Column(name = "consumption_order", nullable = false) @Builder.Default private Integer consumptionOrder = 1;
    @Enumerated(EnumType.STRING) @Column(name = "consumption_mode", nullable = false)
    @Builder.Default private ConsumptionMode consumptionMode = ConsumptionMode.PRIMARY;
    @Column(nullable = false) @Builder.Default private boolean mandatory = true;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
