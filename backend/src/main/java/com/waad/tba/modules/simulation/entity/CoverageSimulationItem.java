package com.waad.tba.modules.simulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coverage_simulation_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverageSimulationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_run_id", nullable = false)
    private CoverageSimulationRun simulationRun;

    @Column(name = "provider_service_id")
    private Long providerServiceId;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "source_main_category", length = 255)
    private String sourceMainCategory;

    @Column(name = "source_sub_category", length = 255)
    private String sourceSubCategory;

    @Column(name = "medical_meaning_ar", length = 500)
    private String medicalMeaningAr;

    @Column(name = "procedure_type", length = 50)
    private String procedureType;

    @Column(name = "body_system", length = 50)
    private String bodySystem;

    @Column(name = "explanation_ar", length = 1000)
    private String explanationAr;

    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @Column(name = "classification_source", length = 100)
    private String classificationSource;

    @Column(name = "category_code", length = 50)
    private String categoryCode;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Column(name = "coverage_status", length = 50)
    private String coverageStatus;

    @Column(name = "coverage_reason", length = 500)
    private String coverageReason;

    @Column(name = "recommended_action", length = 500)
    private String recommendedAction;

    @Column(name = "severity", length = 50)
    private String severity;

    @Column(name = "matched_rule_id")
    private Long matchedRuleId;

    @Column(name = "coverage_percent")
    private Integer coveragePercent;

    @Column(name = "patient_share", precision = 15, scale = 2)
    private BigDecimal patientShare;

    @Column(name = "company_share", precision = 15, scale = 2)
    private BigDecimal companyShare;

    @Column(name = "requires_review")
    private boolean requiresReview;

    @Column(name = "requires_pre_approval")
    private boolean requiresPreApproval;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings_json", columnDefinition = "jsonb")
    private String warningsJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
