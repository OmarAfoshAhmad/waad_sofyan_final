package com.waad.tba.modules.claim.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "claim_pending_services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimPendingService {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;
    @Column(name = "proposed_service_code", length = 50)
    private String proposedServiceCode;
    @Column(name = "proposed_service_name", nullable = false, length = 255)
    private String proposedServiceName;
    @Column(name = "proposed_category_id")
    private Long proposedCategoryId;
    @Column(name = "proposed_category_code", length = 50) private String proposedCategoryCode;
    @Column(name = "proposed_category_name", length = 200) private String proposedCategoryName;
    @Column(name = "new_category_requested", nullable = false)
    @Builder.Default private Boolean newCategoryRequested = false;
    @Column(name = "proposed_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal proposedUnitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private PendingServiceStatus status = PendingServiceStatus.PRELIMINARY;

    @Column(name = "dictionary_release_id") private Long dictionaryReleaseId;
    @Column(name = "dictionary_version", length = 40) private String dictionaryVersion;
    @Column(name = "dictionary_concept_code", length = 100) private String dictionaryConceptCode;
    @Column(name = "classification_method", length = 80) private String classificationMethod;
    @Column(name = "classification_reason", length = 2000) private String classificationReason;
    @Column(name = "classification_evidence_id") private Long classificationEvidenceId;

    @Column(name = "final_service_code", length = 50) private String finalServiceCode;
    @Column(name = "final_service_name", length = 255) private String finalServiceName;
    @Column(name = "final_category_id") private Long finalCategoryId;
    @Column(name = "final_unit_price", precision = 15, scale = 2) private BigDecimal finalUnitPrice;
    @Column(name = "linked_pricing_item_id") private Long linkedPricingItemId;
    @Column(name = "decision_reason", length = 2000) private String decisionReason;
    @Column(name = "entered_by", nullable = false) private Long enteredBy;
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Version @Column(nullable = false) @Builder.Default private Long version = 0L;

    public BigDecimal effectiveUnitPrice() {
        return finalUnitPrice != null ? finalUnitPrice : proposedUnitPrice;
    }

    public Long effectiveCategoryId() {
        return finalCategoryId != null ? finalCategoryId : proposedCategoryId;
    }
}
