package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderViewDto {
    private Long id;
    
    /**
     * Provider name (unified field)
     */
    private String name;
    
    private String licenseNumber;
    private String taxNumber;
    private String city;
    private String address;
    private String phone;
    private String email;
    private String providerType;
    private String providerTypeLabel;
    private String networkStatus;
    private String networkStatusLabel;
    private Boolean active;

    /**
     * Whether this provider accepts members from any employer, rather than
     * only the ones explicitly listed in ProviderAllowedEmployer. Omitted
     * from toViewDto() used to make every provider read back as "not
     * allowed" regardless of what was actually stored, forcing a manual
     * re-toggle after every import.
     */
    private Boolean allowAllEmployers;

    /**
     * Whether provider has administrative documents uploaded
     */
    private Boolean hasDocuments;

    /**
     * Number of administrative documents uploaded for this provider
     * (license/certificates/contract files) — batch-computed per page, never per row.
     */
    private Long documentsCount;
    
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    
    /**
     * PHASE 3 REVIEW: defaultDiscountRate removed from DTO.
     * Use GET /api/v1/providers/{id}/contracts to retrieve discount rates.
     */
    // private BigDecimal defaultDiscountRate; // REMOVED FROM DTO
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
