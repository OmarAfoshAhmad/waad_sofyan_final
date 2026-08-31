package com.waad.tba.modules.providercontract.entity;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.enums.ClassificationStatus;
import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-service pricing within a provider contract. Unique per contract+service.
 */
@Entity
@Table(name = "provider_contract_pricing_items", indexes = {
        @Index(name = "idx_pricing_contract_id", columnList = "contract_id"),
        @Index(name = "idx_pricing_category_id", columnList = "medical_category_id"),
        @Index(name = "idx_pricing_active", columnList = "active"),
        @Index(name = "idx_pricing_service_name", columnList = "service_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderContractPricingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Contract is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private ProviderContract contract;

    @Size(max = 255)
    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Size(max = 50)
    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Size(max = 255)
    @Column(name = "category_name", length = 255)
    private String categoryName;

    /** Sub-category name imported from provider price lists. */
    @Size(max = 255)
    @Column(name = "sub_category_name", length = 255)
    private String subCategoryName;

    /** Provider specialty label imported from provider price lists. */
    @Size(max = 255)
    @Column(name = "specialty", length = 255)
    private String specialty;

    @Column(name = "quantity")
    @Builder.Default
    private Integer quantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_category_id")
    private MedicalCategory medicalCategory;

    @DecimalMin(value = "0.00")
    @Column(name = "base_price", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal basePrice = BigDecimal.ZERO;

    @DecimalMin(value = "0.00")
    @Column(name = "contract_price", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal contractPrice = BigDecimal.ZERO;

    @DecimalMin(value = "0.00")
    @Column(name = "max_contract_price", precision = 15, scale = 2)
    private BigDecimal maxContractPrice;

    // Auto-calculated: (basePrice - contractPrice) / basePrice * 100
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Size(max = 50)
    @Column(length = 50)
    @Builder.Default
    private String unit = "service";

    @Size(max = 3)
    @Column(length = 3)
    @Builder.Default
    private String currency = "LYD";

    @NotNull(message = "Effective-from date is required")
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Size(max = 100)
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // --- Classification & Review Fields ---

    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_type", length = 20)
    private EncounterType encounterType;

    @Size(max = 60)
    @Column(name = "claim_context_code", length = 60)
    private String claimContextCode;

    @Column(name = "requires_review", nullable = false)
    @Builder.Default
    private Boolean requiresReview = false;

    @Size(max = 500)
    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_status", length = 20)
    @Builder.Default
    private ClassificationStatus classificationStatus = ClassificationStatus.AUTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 10)
    @Builder.Default
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.LOW;

    @Size(max = 50)
    @Column(name = "classification_source", length = 50)
    private String classificationSource;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Size(max = 255)
    @Column(name = "imported_main_category", length = 255)
    private String importedMainCategory;

    @Size(max = 255)
    @Column(name = "imported_sub_category", length = 255)
    private String importedSubCategory;

    /** Auditable source and V50 decision copied from the classified price-list row. */
    @Column(name = "source_price_list_item_id")
    private Long sourcePriceListItemId;

    @Column(name = "dictionary_release_id")
    private Long dictionaryReleaseId;

    @Column(name = "dictionary_version", length = 40)
    private String dictionaryVersion;

    @Column(name = "dictionary_concept_code", length = 100)
    private String dictionaryConceptCode;

    @Column(name = "classification_method_v50", length = 80)
    private String classificationMethodV50;

    @Column(name = "classification_evidence_id")
    private Long classificationEvidenceId;

    @PrePersist
    @PreUpdate
    public void calculateDiscountPercent() {
        if (effectiveFrom == null && contract != null) {
            // Historical callers created the first price together with its contract.
            // The contract start is the only deterministic, auditable fallback;
            // never use LocalDate.now() because that would rewrite historical meaning.
            effectiveFrom = contract.getStartDate();
        }
        if (basePrice != null && contractPrice != null && basePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = basePrice.subtract(contractPrice)
                    .divide(basePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Ensure discount is within bounds
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                discount = BigDecimal.ZERO;
            } else if (discount.compareTo(BigDecimal.valueOf(100)) > 0) {
                discount = BigDecimal.valueOf(100);
            }

            this.discountPercent = discount;
        }
    }

    public boolean isCurrentlyEffective() {
        LocalDate today = LocalDate.now();

        // Use item-specific dates if available, otherwise use contract dates
        LocalDate effectiveStart = effectiveFrom != null ? effectiveFrom
                : (contract != null ? contract.getStartDate() : null);
        LocalDate effectiveEnd = effectiveTo != null ? effectiveTo : (contract != null ? contract.getEndDate() : null);

        if (effectiveStart == null) {
            return false;
        }

        return !effectiveStart.isAfter(today) &&
                (effectiveEnd == null || today.isBefore(effectiveEnd));
    }

    public BigDecimal getSavingsAmount() {
        if (basePrice == null || contractPrice == null) {
            return BigDecimal.ZERO;
        }
        return basePrice.subtract(contractPrice);
    }

    public MedicalCategory getEffectiveCategory() {
        if (medicalCategory != null) {
            return medicalCategory;
        }
        // MedicalService only has categoryId, not category entity
        // Caller should fetch category separately if needed
        return null;
    }
}
