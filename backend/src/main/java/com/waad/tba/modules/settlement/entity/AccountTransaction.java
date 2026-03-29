package com.waad.tba.modules.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Account Transaction Entity
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ ACCOUNT TRANSACTION ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ IMMUTABLE audit trail for all financial movements on provider accounts. ║
 * ║ ║
 * ║ Transaction Types: ║
 * ║ CREDIT: Increases balance (claim approved) ║
 * ║ DEBIT: Decreases balance (batch paid) ║
 * ║ ║
 * ║ IMPORTANT: This entity is IMMUTABLE. ║
 * ║ No UPDATE or DELETE allowed (enforced by database trigger). ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Entity
@Table(name = "account_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Provider account this transaction belongs to
     */
    @Column(name = "provider_account_id", nullable = false)
    private Long providerAccountId;

    /**
     * Transaction type
     * CREDIT: Increases balance (e.g., claim approved)
     * DEBIT: Decreases balance (e.g., batch paid)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    /**
     * Transaction amount (always positive)
     * Direction determined by transaction_type
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Account balance BEFORE this transaction
     */
    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    /**
     * Account balance AFTER this transaction
     */
    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Reference type - what triggered this transaction
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private ReferenceType referenceType;

    /**
     * Reference ID (claim_id for CLAIM_APPROVED, batch_id for BATCH_PAID)
     * May be null for ADJUSTMENT
     */
    @Column(name = "reference_id")
    private Long referenceId;

    /**
     * Human-readable description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * User who created this transaction
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * Transaction timestamp (IMMUTABLE)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Legacy date-only column required by old schema versions.
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (transactionDate == null) {
            transactionDate = createdAt.toLocalDate();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a CREDIT transaction (claim approved)
     */
    public static AccountTransaction createClaimApprovedCredit(
            Long accountId,
            Long claimId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        BigDecimal balanceAfter = balanceBefore.add(amount);

        return AccountTransaction.builder()
                .providerAccountId(accountId)
                .transactionType(TransactionType.CREDIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceType(ReferenceType.CLAIM_APPROVAL)
                .referenceId(claimId)
                .description(String.format("اعتماد مطالبة رقم %d - إضافة %s", claimId, amount))
                .createdBy(userId)
                .build();
    }

    /**
     * Create a DEBIT transaction (batch paid)
     */
    public static AccountTransaction createBatchPaidDebit(
            Long accountId,
            Long batchId,
            String batchNumber,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        return AccountTransaction.builder()
                .providerAccountId(accountId)
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceType(ReferenceType.SETTLEMENT_PAYMENT)
                .referenceId(batchId)
                .description(String.format("دفع دفعة تسوية %s - خصم %s", batchNumber, amount))
                .createdBy(userId)
                .build();
    }

    /**
     * Create a DEBIT transaction when a claim is individually settled (not via
     * batch).
     * Has a dedicated ReferenceType so idempotency can be checked.
     */
    public static AccountTransaction createClaimSettlementDebit(
            Long accountId,
            Long claimId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        return AccountTransaction.builder()
                .providerAccountId(accountId)
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceType(ReferenceType.CLAIM_SETTLEMENT)
                .referenceId(claimId)
                .description(String.format("تسوية مطالبة فردية رقم %d - خصم %s", claimId, amount))
                .createdBy(userId)
                .build();
    }

    /**
     * Create an ADJUSTMENT transaction (manual correction)
     */
    public static AccountTransaction createAdjustment(
            Long accountId,
            BigDecimal amount,
            boolean isCredit,
            BigDecimal balanceBefore,
            String reason,
            Long userId) {

        BigDecimal balanceAfter = isCredit
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);

        return AccountTransaction.builder()
                .providerAccountId(accountId)
                .transactionType(isCredit ? TransactionType.CREDIT : TransactionType.DEBIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceType(ReferenceType.ADJUSTMENT)
                .referenceId(null)
                .description(String.format("تسوية يدوية: %s", reason))
                .createdBy(userId)
                .build();
    }

    /**
     * Create a REVERSAL transaction
     */
    public static AccountTransaction createReversal(
            Long accountId,
            Long originalTransactionId,
            BigDecimal amount,
            boolean isCredit,
            BigDecimal balanceBefore,
            String reason,
            Long userId) {

        BigDecimal balanceAfter = isCredit
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);

        return AccountTransaction.builder()
                .providerAccountId(accountId)
                .transactionType(isCredit ? TransactionType.CREDIT : TransactionType.DEBIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceType(ReferenceType.ADJUSTMENT)
                .referenceId(originalTransactionId)
                .description(String.format("عكس حركة رقم %d: %s", originalTransactionId, reason))
                .createdBy(userId)
                .build();
    }

    /**
     * FIX #12: Create a dedicated CLAIM_REVERSAL DEBIT transaction.
     * Uses CLAIM_REVERSAL referenceType + claimId as referenceId so that
     * idempotency checks can find it by (CLAIM_REVERSAL, claimId).
     */
    public static AccountTransaction createClaimReversalDebit(
            Long accountId,
            Long claimId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        return AccountTransaction.builder()
                .providerAccountId(accountId)
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceType(ReferenceType.CLAIM_REVERSAL)
                .referenceId(claimId)
                .description(String.format("عكس اعتماد مطالبة مرفوضة رقم %d - خصم %s", claimId, amount))
                .createdBy(userId)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUM: Transaction Type
    // ═══════════════════════════════════════════════════════════════════════════

    public enum TransactionType {
        /** Credit - increases account balance */
        CREDIT("إضافة"),

        /** Debit - decreases account balance */
        DEBIT("خصم");

        private final String arabicLabel;

        TransactionType(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUM: Reference Type
    // ═══════════════════════════════════════════════════════════════════════════

    public enum ReferenceType {
        /** Claim approved - CREDIT transaction */
        CLAIM_APPROVAL("اعتماد مطالبة"),

        /** Settlement batch paid - DEBIT transaction */
        SETTLEMENT_PAYMENT("دفع دفعة تسوية"),

        /** Individual claim settlement (direct pay, not via batch) */
        CLAIM_SETTLEMENT("تسوية مطالبة فردية"),

        /**
         * FIX #12: Claim reversal — DEBIT that mirrors a prior CLAIM_APPROVAL CREDIT.
         * Distinct from ADJUSTMENT so idempotency checks can find it by (type, claimId).
         */
        CLAIM_REVERSAL("عكس اعتماد مطالبة"),

        /** Manual adjustment */
        ADJUSTMENT("تسوية يدوية");

        private final String arabicLabel;

        ReferenceType(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }
}
