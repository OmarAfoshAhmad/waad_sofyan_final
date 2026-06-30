package com.waad.tba.modules.simulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "coverage_simulation_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverageSimulationRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "encounter_type")
    private String encounterType;

    @Column(name = "generated_by_user_id")
    private Long generatedByUserId;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "limit_evaluation_mode")
    private String limitEvaluationMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

    @Column(name = "total_services")
    private int totalServices;

    @Column(name = "covered_count")
    private int coveredCount;

    @Column(name = "excluded_count")
    private int excludedCount;

    @Column(name = "no_rule_count")
    private int noRuleCount;

    @Column(name = "needs_review_count")
    private int needsReviewCount;

    @Column(name = "invalid_category_count")
    private int invalidCategoryCount;

    @Column(name = "context_mismatch_count")
    private int contextMismatchCount;

    @Column(name = "zero_price_count")
    private int zeroPriceCount;

    @OneToMany(mappedBy = "simulationRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CoverageSimulationItem> items = new ArrayList<>();

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
