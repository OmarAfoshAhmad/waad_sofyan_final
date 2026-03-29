package com.waad.tba.modules.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider Account Entity
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ PROVIDER ACCOUNT ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Financial account for each healthcare provider. ║
 * ║ Tracks running balance, total approved, and total paid amounts. ║
 * ║ ║
 * ║ Relationship: 1:1 with Provider ║
 * ║ Each provider has exactly ONE account. ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Balance Formula:
 * running_balance = total_approved - total_paid
 * 
 * Transaction Types:
 * CREDIT: Claim approved → balance increases
 * DEBIT: Batch paid → balance decreases
 */
@Entity
@Table(name = "provider_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Provider reference (1:1 relationship)
     * Each provider has exactly ONE account
     */
    @Column(name = "provider_id", nullable = false, unique = true)
    private Long providerId;

    /**
     * Current outstanding balance (approved - paid)
     * This is what the insurance company owes the provider
     */
    @Column(name = "running_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal runningBalance = BigDecimal.ZERO;

    /**
     * Cumulative total of all approved claim amounts
     * Only increases, never decreases
     */
    @Column(name = "total_approved", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalApproved = BigDecimal.ZERO;

    /**
     * Cumulative total of all settlement payments
     * Only increases, never decreases
     */
    @Column(name = "total_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPaid = BigDecimal.ZERO;

    /**
     * Account status
     * ACTIVE: Normal operations
     * SUSPENDED: Temporary hold (e.g., dispute)
     * CLOSED: No new transactions allowed
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /**
     * Timestamp of last transaction on this account
     */
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking version
     * Prevents race conditions when updating balance
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Credit the account (claim approved)
     * Increases running_balance and total_approved
     * 
     * @param amount Amount to credit (must be positive)
     */
    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot credit account with status: " + status);
        }
        this.runningBalance = this.runningBalance.add(amount);
        this.totalApproved = this.totalApproved.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
        assertBalanceInvariant();
    }

    /**
     * Debit the account (batch paid)
     * Decreases running_balance, increases total_paid
     * 
     * @param amount Amount to debit (must be positive)
     */
    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot debit account with status: " + status);
        }
        if (amount.compareTo(this.runningBalance) > 0) {
            throw new IllegalStateException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            this.runningBalance, amount));
        }
        this.runningBalance = this.runningBalance.subtract(amount);
        this.totalPaid = this.totalPaid.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
        assertBalanceInvariant();
    }

    /**
     * Reverse a prior credit (claim reversal / deletion of approved claim).
     * Decreases both running_balance and total_approved so UI totals stay accurate.
     * Does NOT touch total_paid (which represents actual settlement payments only).
     *
     * @param amount Amount to reverse (must be positive and <= running_balance)
     */
    public void reverseCredit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reversal amount must be positive");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot reverse credit on account with status: " + status);
        }
        if (amount.compareTo(this.runningBalance) > 0) {
            throw new IllegalStateException(
                    String.format("Insufficient balance for reversal. Available: %s, Requested: %s",
                            this.runningBalance, amount));
        }
        this.runningBalance = this.runningBalance.subtract(amount);
        this.totalApproved = this.totalApproved.subtract(amount);
        this.lastTransactionAt = LocalDateTime.now();
        assertBalanceInvariant();
    }

    /**
     * Suspend the account (temporary hold)
     */
    public void suspend() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot suspend a closed account");
        }
        this.status = AccountStatus.SUSPENDED;
    }

    /**
     * Reactivate a suspended account
     */
    public void reactivate() {
        if (status != AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Can only reactivate suspended accounts");
        }
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Close the account (no new transactions)
     */
    public void close() {
        this.status = AccountStatus.CLOSED;
    }

    /**
     * Check if account can accept transactions
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Check if account has outstanding balance
     */
    public boolean hasOutstandingBalance() {
        return runningBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Financial Invariant: running_balance MUST equal total_approved - total_paid.
     * Called after every credit/debit/reverseCredit to catch arithmetic drift.
     */
    private void assertBalanceInvariant() {
        BigDecimal expected = this.totalApproved.subtract(this.totalPaid);
        if (expected.compareTo(this.runningBalance) != 0) {
            throw new IllegalStateException(
                    String.format("BALANCE INVARIANT VIOLATED for account %d (provider %d): "
                            + "runningBalance=%s but totalApproved(%s) - totalPaid(%s) = %s",
                            this.id, this.providerId,
                            this.runningBalance, this.totalApproved, this.totalPaid, expected));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUM: Account Status
    // ═══════════════════════════════════════════════════════════════════════════

    public enum AccountStatus {
        /** Normal operations - can credit and debit */
        ACTIVE("نشط"),

        /** Temporary hold - no transactions allowed */
        SUSPENDED("معلق"),

        /** Permanently closed - no transactions allowed */
        CLOSED("مغلق");

        private final String arabicLabel;

        AccountStatus(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }
}
