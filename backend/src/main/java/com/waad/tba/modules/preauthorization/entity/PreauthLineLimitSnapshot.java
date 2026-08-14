package com.waad.tba.modules.preauthorization.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per (line, limit scope), carrying BOTH ceilings a bucket can impose.
 * The dimensions share a row and never share arithmetic: a visit count and a
 * currency amount are not the same kind of number.
 */
@Entity
@Table(name = "preauth_line_limit_snapshots")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreauthLineLimitSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "line_snapshot_id", nullable = false) private Long lineSnapshotId;

    @Column(name = "limit_scope", nullable = false, length = 20) private String limitScope;
    @Column(name = "limit_semantic_key", nullable = false, length = 200) private String limitSemanticKey;
    @Column(name = "bucket_id") private Long bucketId;
    @Column(name = "policy_id", nullable = false) private Long policyId;

    @Column(name = "period_type", nullable = false, length = 20) private String periodType;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end") private LocalDate periodEnd;

    // The monetary dimension. Null throughout when the bucket caps no money.
    @Column(name = "effective_limit", precision = 15, scale = 2) private BigDecimal effectiveLimit;
    @Column(name = "committed_before", precision = 15, scale = 2) private BigDecimal committedBefore;
    @Column(name = "reserved_before", precision = 15, scale = 2)
    @Builder.Default private BigDecimal reservedBefore = BigDecimal.ZERO;
    @Column(name = "actual_remaining_before", precision = 15, scale = 2)
    private BigDecimal actualRemainingBefore;
    @Column(name = "reservable_available_before", precision = 15, scale = 2)
    private BigDecimal reservableAvailableBefore;

    // The occurrence dimension. Null throughout when the bucket caps no count.
    @Column(name = "times_limit") private Integer timesLimit;
    @Column(name = "committed_times_before") private Integer committedTimesBefore;
    @Column(name = "reserved_times_before") private Integer reservedTimesBefore;
    @Column(name = "actual_remaining_times_before") private Integer actualRemainingTimesBefore;
    @Column(name = "reservable_times_before") private Integer reservableTimesBefore;

    @Column(name = "consumption_basis", nullable = false, length = 20) private String consumptionBasis;
    @Column(name = "reserved_unit", nullable = false, length = 10) private String reservedUnit;

    @Column(name = "amount_reserved", precision = 15, scale = 2) private BigDecimal amountReserved;
    @Column(name = "times_reserved") private Integer timesReserved;
    /** Always zero: no day reservation can be honestly derived from one expected date. */
    @Column(name = "days_reserved") @Builder.Default private Integer daysReserved = 0;

    @Column(name = "is_binding", nullable = false) @Builder.Default private boolean binding = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
