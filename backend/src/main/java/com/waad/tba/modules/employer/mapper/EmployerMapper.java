package com.waad.tba.modules.employer.mapper;

import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.dto.EmployerSelectorDto;
import com.waad.tba.modules.employer.entity.Employer;
import org.springframework.stereotype.Component;

/**
 * Employer mapper - maps Employer entity to Employer DTOs.
 * Simplified: Arabic name only (no English name)
 */
@Component
public class EmployerMapper {

    /**
     * Map Employer entity to EmployerResponseDto
     * 
     * Field Mapping:
     * - employer.name → dto.name (will be serialized as 'nameAr' via @JsonProperty)
     * - employer.code → dto.code
     * - employer.active → dto.active
     * - Includes audit timestamps
     */
    public EmployerResponseDto toResponse(Employer employer) {
        return EmployerResponseDto.builder()
                .id(employer.getId())
                .code(employer.getCode())
                .name(employer.getName())
                .active(employer.getActive())
                .archived(false)
                .isDefault(Boolean.TRUE.equals(employer.getIsDefault()))
                .logoUrl(employer.getLogoUrl())
                .businessType(employer.getBusinessType())
                .website(employer.getWebsite())
                .phone(employer.getPhone())
                .email(employer.getEmail())
                .address(employer.getAddress())
                .crNumber(employer.getCrNumber())
                .taxNumber(employer.getTaxNumber())
                .contractStartDate(employer.getContractStartDate())
                .contractEndDate(employer.getContractEndDate())
                .maxMemberLimit(employer.getMaxMemberLimit())
                .createdAt(employer.getCreatedAt())
                .updatedAt(employer.getUpdatedAt())
                .build();
    }

    /**
     * Map Employer entity to EmployerSelectorDto (for dropdowns)
     */
    public EmployerSelectorDto toSelector(Employer employer) {
        return EmployerSelectorDto.builder()
                .id(employer.getId())
                .label(employer.getName())  // Use Arabic name for display
                .code(employer.getCode())   // Include code for filtering
                .build();
    }
}


