package com.waad.tba.modules.preauthorization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pre_authorization_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id", nullable = false)
    private PreAuthorization preAuthorization;

    @Column(name = "provider_service_id")
    private Long providerServiceId;

    /**
     * The line's own medical identity (V176). providerServiceId identifies
     * the provider's price-list entry, not a classification -- and the
     * benefit rule that decides which buckets apply is resolved from the
     * CATEGORY. Resolving it from the pre-authorization head instead would
     * price every line of a mixed-category request against a single
     * category, landing holds on the wrong buckets for all but one of them.
     */
    @Column(name = "medical_service_id")
    private Long medicalServiceId;

    @Column(name = "medical_category_id")
    private Long medicalCategoryId;

    @Column(name = "provider_service_code", length = 50)
    private String providerServiceCode;

    @Column(name = "service_name", length = 500)
    private String serviceName;

    @Column(name = "source_type", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SourceType sourceType = SourceType.CONTRACTED;

    public enum SourceType {
        CONTRACTED,
        CONTRACTED_WITH_MANUAL_OVERRIDE,
        UNLISTED
    }

    // ==================== PRICING ====================

    @Column(name = "contract_price", precision = 15, scale = 2)
    private BigDecimal contractPrice;

    @Column(name = "manual_price", precision = 15, scale = 2)
    private BigDecimal manualPrice;

    @Column(name = "requested_amount", precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "variance_amount", precision = 15, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "variance_percentage", precision = 5, scale = 2)
    private BigDecimal variancePercentage;

    @Column(name = "price_variance_status", length = 50)
    @Enumerated(EnumType.STRING)
    private PriceVarianceStatus priceVarianceStatus;

    public enum PriceVarianceStatus {
        MATCH_CONTRACT,
        BELOW_CONTRACT,
        ABOVE_CONTRACT,
        HIGH_VARIANCE,
        CRITICAL_VARIANCE,
        UNLISTED,
        MISSING_PRICE
    }

    @Column(name = "requires_price_review")
    @Builder.Default
    private Boolean requiresPriceReview = false;

    @Column(name = "price_override_reason", length = 1000)
    private String priceOverrideReason;

    // ==================== CATEGORIZATION & COVERAGE ====================

    @Column(name = "insurance_category_code", length = 50)
    private String insuranceCategoryCode;

    @Column(name = "medical_specialty", length = 100)
    private String medicalSpecialty;

    @Column(name = "procedure_type", length = 50)
    private String procedureType;

    @Column(name = "encounter_type", length = 50)
    private String encounterType;

    @Column(name = "coverage_status", length = 50)
    private String coverageStatus;

    @Column(name = "coverage_reason", length = 500)
    private String coverageReason;

    @Column(name = "coverage_percentage")
    private Integer coveragePercentage;

    @Column(name = "patient_share", precision = 15, scale = 2)
    private BigDecimal patientShare;

    @Column(name = "company_share", precision = 15, scale = 2)
    private BigDecimal companyShare;

    @Column(name = "requires_pre_approval")
    @Builder.Default
    private Boolean requiresPreApproval = true;

    @Column(name = "requires_review")
    @Builder.Default
    private Boolean requiresReview = false;

    // ==================== DECISION ====================

    @Column(name = "decision_status", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LineDecisionStatus decisionStatus = LineDecisionStatus.PENDING;

    public enum LineDecisionStatus {
        PENDING,
        APPROVED,
        PARTIALLY_APPROVED,
        REJECTED,
        INFO_REQUESTED
    }

    @Column(name = "decision_reason_code", length = 50)
    private String decisionReasonCode;

    @Column(name = "decision_notes", length = 1000)
    private String decisionNotes;

    // ==================== AUDIT ====================

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
