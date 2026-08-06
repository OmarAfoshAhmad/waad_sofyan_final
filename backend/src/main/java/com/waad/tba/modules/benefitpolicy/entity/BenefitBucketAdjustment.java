package com.waad.tba.modules.benefitpolicy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Non-financial usage carried into a benefit bucket (for example, an opening
 * balance migrated from a legacy system). It affects limit availability only
 * and must never appear as a claim, payment, settlement or accounting entry.
 */
@Entity
@Table(name = "benefit_bucket_adjustments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BenefitBucketAdjustment {
    public enum AdjustmentType { OPENING_BALANCE, MANUAL_CORRECTION, REVERSAL }
    public enum Status { ACTIVE, REVERSED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id")
    private BenefitPolicy policy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bucket_id")
    private BenefitLimitBucket bucket;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "amount_delta", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountDelta = BigDecimal.ZERO;

    @Column(name = "times_delta", nullable = false)
    @Builder.Default
    private Integer timesDelta = 0;

    @Column(name = "days_delta", nullable = false)
    @Builder.Default
    private Integer daysDelta = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private AdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "source_batch_id", nullable = false, length = 100)
    private String sourceBatchId;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(length = 1000)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 220)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private BenefitBucketAdjustment reversalOf;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @PrePersist
    void create() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

