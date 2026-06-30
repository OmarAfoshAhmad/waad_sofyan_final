package com.waad.tba.modules.providercontract.entity;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Mapping table to automatically classify services based on specialty or keywords.
 */
@Entity
@Table(name = "service_specialty_insurance_map")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceSpecialtyInsuranceMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_specialty_name_ar", length = 255)
    private String sourceSpecialtyNameAr;

    @Column(name = "source_specialty_name_en", length = 255)
    private String sourceSpecialtyNameEn;

    /**
     * JSON array of regex/keyword patterns.
     */
    @Column(name = "keyword_patterns", columnDefinition = "TEXT")
    private String keywordPatterns;

    @Column(name = "match_field", length = 50)
    @Builder.Default
    private String matchField = "BOTH"; // 'SERVICE_NAME', 'SPECIALTY', 'BOTH'

    @Column(name = "insurance_category_code", nullable = false, length = 50)
    private String insuranceCategoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_encounter_type", length = 20)
    @Builder.Default
    private EncounterType defaultEncounterType = EncounterType.INPATIENT;

    @Column(name = "requires_review")
    @Builder.Default
    private Boolean requiresReview = false;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 10)
    @Builder.Default
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.HIGH;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private ProviderContract contract;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
