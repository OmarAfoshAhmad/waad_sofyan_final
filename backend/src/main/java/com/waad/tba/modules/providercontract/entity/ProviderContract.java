package com.waad.tba.modules.providercontract.entity;

import com.waad.tba.modules.provider.entity.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider Contract — pricing agreement with a healthcare provider.
 * Only ONE active contract per provider at any time.
 */
@Entity(name = "ModernProviderContract")
@Table(name = "provider_contracts", indexes = {
        @Index(name = "idx_contracts_provider_id", columnList = "provider_id"),
        @Index(name = "idx_contracts_status", columnList = "status"),
        @Index(name = "idx_contracts_contract_code", columnList = "contract_code"),
        @Index(name = "idx_contracts_start_date", columnList = "start_date"),
        @Index(name = "idx_contracts_end_date", columnList = "end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Contract code is required")
    @Size(max = 50)
    @Column(name = "contract_code", nullable = false, unique = true, length = 50)
    private String contractCode;

    @Column(name = "contract_number", length = 100)
    private String contractNumber;

    @NotNull(message = "Provider is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 20)
    @Builder.Default
    private PricingModel pricingModel = PricingModel.DISCOUNT;

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** @deprecated Use ProviderContractPricingItem.discountPercent instead */
    @Deprecated(since = "2026-01-22", forRemoval = false)
    @Column(name = "discount_rate", precision = 5, scale = 2)
    private BigDecimal discountRate;

    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "signed_date")
    private LocalDate signedDate;

    /** @deprecated Calculate from sum of pricing items */
    @Deprecated(since = "2026-01-22", forRemoval = false)
    @DecimalMin(value = "0.00")
    @Column(name = "total_value", precision = 15, scale = 2)
    private BigDecimal totalValue;

    @Size(max = 3)
    @Column(length = 3)
    @Builder.Default
    private String currency = "LYD";

    @Size(max = 100)
    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = false;

    @Size(max = 100)
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Size(max = 50)
    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Size(max = 100)
    @Email(message = "Invalid email format")
    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Pricing items for this contract
     * 
     * CASCADE POLICY: Financial data must use PERSIST/MERGE only (never REMOVE/ALL)
     * - Prevents accidental deletion of pricing audit trail
     * - Complies with FK cascade policy for financial/historical data
     * - orphanRemoval = false to preserve pricing history
     */
    @OneToMany(mappedBy = "contract", cascade = { CascadeType.PERSIST, CascadeType.MERGE }, orphanRemoval = false)
    @Builder.Default
    private List<ProviderContractPricingItem> pricingItems = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Size(max = 100)
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public enum ContractStatus {
        DRAFT, ACTIVE, SUSPENDED, EXPIRED, TERMINATED
    }

    public enum PricingModel {
        FIXED, DISCOUNT, TIERED, NEGOTIATED
    }

    public boolean isCurrentlyEffective() {
        LocalDate today = LocalDate.now();
        return startDate != null && !startDate.isAfter(today) &&
                (endDate == null || !endDate.isBefore(today));
    }

    public boolean hasExpired() {
        return endDate != null && endDate.isBefore(LocalDate.now());
    }

    public boolean canActivate() {
        return status == ContractStatus.DRAFT || status == ContractStatus.SUSPENDED;
    }

    public boolean canSuspend() {
        return status == ContractStatus.ACTIVE;
    }

    public boolean canTerminate() {
        return status == ContractStatus.ACTIVE || status == ContractStatus.SUSPENDED;
    }

    public boolean canModifyPricing() {
        return status == ContractStatus.DRAFT || status == ContractStatus.ACTIVE || status == ContractStatus.SUSPENDED;
    }

    public void addPricingItem(ProviderContractPricingItem item) {
        pricingItems.add(item);
        item.setContract(this);
    }

    public void removePricingItem(ProviderContractPricingItem item) {
        pricingItems.remove(item);
        item.setContract(null);
    }

    public int getActivePricingItemsCount() {
        return (int) pricingItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .count();
    }
}
