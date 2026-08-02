package com.waad.tba.modules.medicaldictionary.entity;

import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_list_classification_sessions", indexes = {
        @Index(name = "idx_price_list_session_status", columnList = "status"),
        @Index(name = "idx_price_list_session_provider", columnList = "provider_id"),
        @Index(name = "idx_price_list_session_contract", columnList = "contract_id"),
        @Index(name = "idx_price_list_session_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListClassificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_name", nullable = false, length = 300)
    private String sessionName;

    @Column(name = "original_file_name", length = 500)
    private String originalFileName;

    @Column(name = "source_fingerprint", length = 64)
    private String sourceFingerprint;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "provider_name", length = 255)
    private String providerName;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "contract_code", length = 100)
    private String contractCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private PriceListSessionStatus status = PriceListSessionStatus.DRAFT;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "high_confidence_count", nullable = false)
    @Builder.Default
    private Integer highConfidenceCount = 0;

    @Column(name = "needs_review_count", nullable = false)
    @Builder.Default
    private Integer needsReviewCount = 0;

    @Column(name = "unknown_count", nullable = false)
    @Builder.Default
    private Integer unknownCount = 0;

    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private Integer duplicateCount = 0;

    @Column(name = "ranged_price_count", nullable = false)
    @Builder.Default
    private Integer rangedPriceCount = 0;

    @Column(name = "posted_count", nullable = false)
    @Builder.Default
    private Integer postedCount = 0;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PriceListClassificationItem> items = new ArrayList<>();

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
