package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "benefit_groups", uniqueConstraints =
        @UniqueConstraint(name = "uq_benefit_group_policy_code", columnNames = {"policy_id", "code"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BenefitGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private BenefitPolicy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_definition_id")
    private BenefitDefinition benefitDefinition;

    @Column(name = "coverage_percent") private Integer coveragePercent;
    @Column(name = "copay_percentage", precision = 5, scale = 2) private BigDecimal copayPercentage;
    @Column(name = "requires_preapproval", nullable = false) @Builder.Default private boolean requiresPreApproval = false;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "source_clause", columnDefinition = "TEXT") private String sourceClause;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 255)
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 20)
    @Builder.Default
    private EncounterType contextType = EncounterType.ANY;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_mode", nullable = false, length = 20)
    private AggregationMode aggregationMode;

    @Column(nullable = false) @Builder.Default
    private boolean active = true;

    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
