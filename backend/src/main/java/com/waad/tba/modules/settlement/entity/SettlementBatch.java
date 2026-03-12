package com.waad.tba.modules.settlement.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Settlement Batch Entity
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ SETTLEMENT BATCH ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Groups multiple claims into a single payment batch for provider settlement.
 * ║
 * ║ ║
 * ║ Workflow: DRAFT → CONFIRMED → PAID ║
 * ║ ║
 * ║ DRAFT: Can add/remove claims ║
 * ║ CONFIRMED: Locked, awaiting payment ║
 * ║ PAID: Payment complete, claims settled ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Entity
@Table(name = "settlement_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique batch number (format: STL-YYYY-NNNNNN)
     * Example: STL-2026-000001
     */
    @Column(name = "batch_number", nullable = false, unique = true, length = 50)
    private String batchNumber;

    /**
     * Direct provider reference (NOT NULL in DB schema V4 - must always be set)
     * This mirrors settlement_batches.provider_id which was the original FK.
     */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /**
     * Provider account this batch belongs to
     */
    @Column(name = "provider_account_id", nullable = false)
    private Long providerAccountId;

    /**
     * Settlement date
     */
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGREGATED AMOUNTS (calculated from items)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Number of claims in this batch
     */
    @Column(name = "total_claims_count", nullable = false)
    @Builder.Default
    private Integer totalClaimsCount = 0;

    /**
     * Sum of requested amounts (gross)
     */
    @Column(name = "total_gross_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalGrossAmount = BigDecimal.ZERO;

    /**
     * Sum of net provider amounts (actual payment)
     */
    @Column(name = "total_net_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalNetAmount = BigDecimal.ZERO;

    /**
     * Legacy required total amount column (kept for DB compatibility).
     * Mirrors totalNetAmount for payment/accounting calculations.
     */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Sum of patient share amounts
     */
    @Column(name = "total_patient_share", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPatientShare = BigDecimal.ZERO;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Batch status (workflow state)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BatchStatus status = BatchStatus.DRAFT;

    /**
     * Notes/comments
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ═══════════════════════════════════════════════════════════════════════════
    // WORKFLOW TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking version
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ═══════════════════════════════════════════════════════════════════════════
    // RELATIONSHIPS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Items in this batch (claims)
     */
    @OneToMany(mappedBy = "settlementBatchId", cascade = { CascadeType.PERSIST,
            CascadeType.MERGE }, orphanRemoval = false, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SettlementBatchItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        // totalAmount mirrors totalNetAmount — kept in sync, never falsified
        totalAmount = (totalNetAmount != null) ? totalNetAmount : BigDecimal.ZERO;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        // totalAmount mirrors totalNetAmount — kept in sync, never falsified
        totalAmount = (totalNetAmount != null) ? totalNetAmount : BigDecimal.ZERO;
        updatedAt = LocalDateTime.now();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if batch can be modified (add/remove claims)
     */
    public boolean isModifiable() {
        return status == BatchStatus.DRAFT;
    }

    /**
     * Check if batch can be confirmed.
     * Requires at least one claim and a positive net amount.
     */
    public boolean canConfirm() {
        return status == BatchStatus.DRAFT
                && totalClaimsCount > 0
                && totalNetAmount != null
                && totalNetAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if batch can be paid
     */
    public boolean canPay() {
        return status == BatchStatus.CONFIRMED;
    }

    /**
     * Confirm the batch (DRAFT → CONFIRMED)
     */
    public void confirm(Long userId) {
        if (!canConfirm()) {
            throw new IllegalStateException(
                    String.format("Cannot confirm batch in status %s with %d claims", status, totalClaimsCount));
        }
        this.status = BatchStatus.CONFIRMED;
        this.confirmedBy = userId;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Mark batch as paid (CONFIRMED → PAID)
     */
    public void pay(Long userId) {
        if (!canPay()) {
            throw new IllegalStateException("Cannot pay batch in status: " + status);
        }
        this.status = BatchStatus.PAID;
        this.paidBy = userId;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * Update totals from items
     */
    public void recalculateTotals() {
        if (items == null || items.isEmpty()) {
            this.totalClaimsCount = 0;
            this.totalGrossAmount = BigDecimal.ZERO;
            this.totalNetAmount = BigDecimal.ZERO;
            this.totalPatientShare = BigDecimal.ZERO;
            return;
        }

        this.totalClaimsCount = items.size();
        this.totalGrossAmount = items.stream()
                .map(SettlementBatchItem::getGrossAmountSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalNetAmount = items.stream()
                .map(SettlementBatchItem::getNetAmountSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalPatientShare = items.stream()
                .map(SettlementBatchItem::getPatientShareSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUM: Batch Status
    // ═══════════════════════════════════════════════════════════════════════════

    public enum BatchStatus {
        /** Draft - can add/remove claims */
        DRAFT("مسودة"),

        /** Confirmed - locked, awaiting payment */
        CONFIRMED("مؤكدة"),

        /** Paid - payment complete */
        PAID("مدفوعة");

        private final String arabicLabel;

        BatchStatus(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUM: Payment Method
    // ═══════════════════════════════════════════════════════════════════════════

    public enum PaymentMethod {
        /** Bank transfer */
        BANK_TRANSFER("تحويل بنكي"),

        /** Check payment */
        CHECK("شيك"),

        /** Cash payment */
        CASH("نقدي"),

        /** Wire transfer */
        WIRE_TRANSFER("حوالة سلكية");

        private final String arabicLabel;

        PaymentMethod(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }
}
