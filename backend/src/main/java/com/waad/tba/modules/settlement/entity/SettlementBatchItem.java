package com.waad.tba.modules.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Settlement Batch Item Entity
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║                       SETTLEMENT BATCH ITEM                                   ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Links a claim to a settlement batch.                                          ║
 * ║                                                                               ║
 * ║ Relationship: Each claim can only be in ONE batch (1:1)                       ║
 * ║ Enforced by unique constraint on claim_id                                     ║
 * ║                                                                               ║
 * ║ Amount snapshots are taken when claim is added to batch.                      ║
 * ║ These are IMMUTABLE for audit purposes.                                       ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Entity
@Table(name = "settlement_batch_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Settlement batch this item belongs to
     */
    @Column(name = "settlement_batch_id", nullable = false)
    private Long settlementBatchId;

    /**
     * Claim reference
     * Unique constraint ensures each claim can only be in ONE batch
     */
    @Column(name = "claim_id", nullable = false, unique = true)
    private Long claimId;

    // ═══════════════════════════════════════════════════════════════════════════
    // AMOUNT SNAPSHOTS (captured when added to batch)
    // These are IMMUTABLE for audit purposes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Snapshot of claim.requested_amount at time of adding to batch
     */
    @Column(name = "gross_amount_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmountSnapshot;

    /**
     * Snapshot of claim.net_provider_amount at time of adding to batch
     * This is the actual amount that will be paid
     */
    @Column(name = "net_amount_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmountSnapshot;

    /**
     * Snapshot of claim.patient_copay at time of adding to batch
     */
    @Column(name = "patient_share_snapshot", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal patientShareSnapshot = BigDecimal.ZERO;

    /**
     * When this item was added to the batch
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a batch item from claim data
     * 
     * @param batchId Settlement batch ID
     * @param claimId Claim ID
     * @param grossAmount Claim requested amount
     * @param netAmount Claim net provider amount
     * @param patientShare Claim patient copay
     * @return New SettlementBatchItem
     */
    public static SettlementBatchItem createFromClaim(
            Long batchId,
            Long claimId,
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal patientShare) {
        
        return SettlementBatchItem.builder()
                .settlementBatchId(batchId)
                .claimId(claimId)
                .grossAmountSnapshot(grossAmount != null ? grossAmount : BigDecimal.ZERO)
                .netAmountSnapshot(netAmount != null ? netAmount : BigDecimal.ZERO)
                .patientShareSnapshot(patientShare != null ? patientShare : BigDecimal.ZERO)
                .build();
    }
}
