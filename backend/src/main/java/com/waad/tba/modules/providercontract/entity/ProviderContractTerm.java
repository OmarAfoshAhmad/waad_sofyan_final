package com.waad.tba.modules.providercontract.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable-in-time financial terms for a provider contract. Periods are [from, to). */
@Entity
@Table(name = "provider_contract_terms", indexes =
        @Index(name = "idx_provider_contract_terms_effective", columnList = "contract_id,effective_from,effective_to"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderContractTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private ProviderContract contract;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @DecimalMin("0.00") @DecimalMax("100.00")
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "discount_before_rejection", nullable = false)
    private Boolean discountBeforeRejection;

    @Column(name = "change_reason", length = 1000)
    private String changeReason;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    public boolean isEffectiveOn(LocalDate date) {
        return date != null && !date.isBefore(effectiveFrom)
                && (effectiveTo == null || date.isBefore(effectiveTo));
    }
}
