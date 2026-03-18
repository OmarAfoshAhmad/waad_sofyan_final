package com.waad.tba.modules.provider.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.waad.tba.modules.providercontract.entity.ProviderContract;

import org.hibernate.annotations.Formula;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "providers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(unique = true, nullable = false, length = 100)
    private String licenseNumber;

    @Column(length = 50)
    private String taxNumber;

    @Column(length = 100)
    private String city;

    @Column(length = 500)
    private String address;

    @Column(length = 50)
    private String phone;

    @Column(length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderType providerType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NetworkTier networkStatus;

    private LocalDate contractStartDate;

    private LocalDate contractEndDate;

    /** @deprecated Use ProviderContract.discountPercent instead */
    @Deprecated(since = "Phase 3 - 2026-02-12", forRemoval = false)
    @Column(precision = 5, scale = 2)
    private BigDecimal defaultDiscountRate;

    // allowAllEmployers=true → global network | false → only allowedEmployers list
    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProviderAllowedEmployer> allowedEmployers = new ArrayList<>();

    @Column(name = "allow_all_employers", nullable = false)
    @Builder.Default
    private Boolean allowAllEmployers = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String updatedBy;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProviderContract> contracts = new ArrayList<>();

    @Formula("(SELECT CASE WHEN COUNT(d.id) > 0 THEN 'true' ELSE 'false' END FROM provider_admin_documents d WHERE d.provider_id = id)")
    private Boolean hasDocuments;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ProviderType {
        HOSPITAL,
        CLINIC,
        LAB,
        PHARMACY,
        RADIOLOGY
    }

    public enum NetworkTier {
        IN_NETWORK,
        OUT_OF_NETWORK,
        PREFERRED
    }
}
