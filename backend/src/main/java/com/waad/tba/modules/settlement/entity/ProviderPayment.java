package com.waad.tba.modules.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One real bank transfer to a provider — the payment document.
 *
 * A payment is made to the PROVIDER as a whole (half or all of what is owed),
 * then explained by {@link ProviderPaymentAllocation} rows describing which
 * employer/period it settled. The allocations are interpretation only: they
 * never debit the account. The single debit happens once, when this header is
 * posted.
 *
 * Lifecycle: DRAFT -> POSTED -> REVERSED. A posted payment is never edited;
 * corrections are made by reversing and creating a replacement, which keeps the
 * ledger append-only and the audit trail intact.
 */
@Entity
@Table(name = "provider_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPayment {

    public enum Status {
        /** Being prepared. Allocations may be incomplete. No ledger effect. */
        DRAFT,
        /** Committed to the ledger. Immutable. */
        POSTED,
        /** Neutralised by a compensating ledger entry. Excluded from reconciliation. */
        REVERSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    /**
     * Bank/commercial reference for the transfer. Deliberately NOT the
     * idempotency key: the old path used this single field for both, so a
     * legitimate second transfer sharing a reference was rejected as a duplicate
     * request, while a retried request without a reference created a duplicate.
     */
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    /** Request-level de-duplication. Unique when present. */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "attachment_path", length = 500)
    private String attachmentPath;

    /** The one ledger entry produced by posting this payment. */
    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "posted_by", length = 150)
    private String postedBy;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversed_by", length = 150)
    private String reversedBy;

    @Column(name = "reversal_reason", length = 1000)
    private String reversalReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProviderPaymentAllocation> allocations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Derived values ────────────────────────────────────────────────────────

    /** Sum of allocations. Never exceeds {@link #amount} (enforced in the DB). */
    public BigDecimal getAllocatedAmount() {
        return allocations.stream()
                .map(ProviderPaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * The part of the transfer not yet attributed to any period. Surfaced
     * explicitly rather than hidden, so an over-payment is visible as a real
     * amount instead of being clamped away.
     */
    public BigDecimal getUnallocatedAmount() {
        return amount.subtract(getAllocatedAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isFullyAllocated() {
        return getUnallocatedAmount().signum() == 0;
    }

    public boolean isEditable() {
        return status == Status.DRAFT;
    }

    /** Only posted payments count toward the provider's paid total. */
    public boolean countsTowardPaidTotal() {
        return status == Status.POSTED;
    }

    public void addAllocation(ProviderPaymentAllocation allocation) {
        allocation.setPayment(this);
        allocations.add(allocation);
    }
}
