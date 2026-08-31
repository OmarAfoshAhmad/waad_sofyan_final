package com.waad.tba.modules.medicaldictionary.entity;

import com.waad.tba.modules.medicaldictionary.enums.PriceListItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_list_classification_items", indexes = {
        @Index(name = "idx_price_list_item_session", columnList = "session_id"),
        @Index(name = "idx_price_list_item_status", columnList = "status"),
        @Index(name = "idx_price_list_item_category", columnList = "medical_category_id"),
        @Index(name = "idx_price_list_item_provider_code", columnList = "provider_service_code"),
        @Index(name = "idx_price_list_item_posted", columnList = "posted_pricing_item_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListClassificationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PriceListClassificationSession session;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(name = "source_sheet", length = 255)
    private String sourceSheet;

    @Column(name = "source_classification", length = 255)
    private String sourceClassification;

    @Column(name = "claim_context_code", length = 60)
    private String claimContextCode;

    @Column(name = "provider_service_code", length = 100)
    private String providerServiceCode;

    @Column(name = "provider_service_name", nullable = false, length = 500)
    private String providerServiceName;

    @Column(name = "canonical_name", length = 300)
    private String canonicalName;

    @Column(name = "dictionary_entry_id")
    private Long dictionaryEntryId;

    @Column(name = "medical_category_id")
    private Long medicalCategoryId;

    @Column(name = "medical_category_code", length = 100)
    private String medicalCategoryCode;

    @Column(name = "medical_category_name", length = 255)
    private String medicalCategoryName;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "dictionary_release_id")
    private Long dictionaryReleaseId;

    @Column(name = "dictionary_version", length = 40)
    private String dictionaryVersion;

    @Column(name = "dictionary_concept_code", length = 100)
    private String dictionaryConceptCode;

    @Column(name = "classification_method", length = 80)
    private String classificationMethod;

    @Column(name = "classification_reason", length = 2000)
    private String classificationReason;

    @Column(name = "classification_exception_type", length = 100)
    private String classificationExceptionType;

    @Column(name = "classification_evidence_id")
    private Long classificationEvidenceId;

    @Column(name = "classification_exclude_precision", nullable = false)
    @Builder.Default
    private Boolean classificationExcludePrecision = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private PriceListItemStatus status = PriceListItemStatus.UNKNOWN;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "min_price", precision = 15, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 15, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "price_label", length = 100)
    private String priceLabel;

    @Column(name = "duplicate_name", nullable = false)
    @Builder.Default
    private Boolean duplicateName = false;

    @Column(name = "merged_duplicate", nullable = false)
    @Builder.Default
    private Boolean mergedDuplicate = false;

    @Column(name = "merged_source_count", nullable = false)
    @Builder.Default
    private Integer mergedSourceCount = 1;

    @Column(name = "merge_notes", length = 2000)
    private String mergeNotes;

    @Column(name = "manual_review_note", length = 2000)
    private String manualReviewNote;

    @Column(name = "posted_pricing_item_id")
    private Long postedPricingItemId;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
