package com.waad.tba.modules.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Explains which employer/period a slice of a {@link ProviderPayment} settled.
 *
 * Interpretation only: an allocation NEVER debits the provider account. The
 * account is debited exactly once, when the payment header is posted. Keeping
 * allocations non-financial is what makes double-debiting structurally
 * impossible rather than merely avoided by convention.
 *
 * The database enforces that the allocations of a payment never exceed its
 * amount (deferred constraint triggers in V137), in both directions: adding
 * allocations beyond the amount fails, and lowering the amount below the
 * allocated total fails too.
 */
@Entity
@Table(name = "provider_payment_allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPaymentAllocation {

    public enum AllocationMethod {
        /** Proposed by the oldest-period-first algorithm. */
        AUTO_FIFO,
        /** Proportional split inside a single period that the payment cannot fully cover. */
        AUTO_PROPORTIONAL,
        /** Entered or adjusted by the accountant. Requires an override reason. */
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private ProviderPayment payment;

    @Column(name = "employer_id", nullable = false)
    private Long employerId;

    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    @Column(name = "target_month", nullable = false)
    private Integer targetMonth;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * What was outstanding for this employer/period at the moment the allocation
     * was decided. Persisted so the proposal can be justified later even after a
     * backdated claim changes the outstanding figure — a real scenario here,
     * since backdated claims are a supported first-class case.
     */
    @Column(name = "outstanding_at_allocation", precision = 15, scale = 2)
    private BigDecimal outstandingAtAllocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_method", nullable = false, length = 30)
    @Builder.Default
    private AllocationMethod allocationMethod = AllocationMethod.AUTO_FIFO;

    /** Mandatory whenever the accountant departs from the proposal. */
    @Column(name = "override_reason", length = 1000)
    private String overrideReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
