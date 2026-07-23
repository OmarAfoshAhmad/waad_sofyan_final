package com.waad.tba.modules.eligibility.dto;

import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Eligibility Check Request DTO
 * Phase E1 - Eligibility Engine
 * 
 * Input for eligibility verification.
 * 
 * Required:
 * - memberId: The member to check eligibility for
 * - serviceDate: The date of service
 * 
 * Optional:
 * - benefitPolicyId: If not provided, uses member's current benefit policy
 * - providerId: For network validation (future)
 * - serviceCode: For service-specific rules (future)
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityCheckRequest {

    /**
     * Member ID to check eligibility for
     * Required
     */
    @NotNull(message = "Member ID is required")
    private Long memberId;

    /**
     * Benefit Policy ID to verify against
     * Optional - if null, uses member's current active benefit policy
     */
    private Long benefitPolicyId;

    /**
     * Provider ID for network validation
     * Optional - for future in-network checks
     */
    private Long providerId;

    /**
     * Service date to check eligibility for
     * Required
     */
    @NotNull(message = "Service date is required")
    private LocalDate serviceDate;

    /**
     * Service code (CPT, ICD, etc.)
     * Optional - for service-specific eligibility rules
     */
    private String serviceCode;
    
    /**
     * CANONICAL: Medical Category ID
     * Optional - for category-specific coverage rules
     * When provided with serviceCode, enables accurate coverage resolution:
     * Same service + different category → different coverage
     */
    private Long medicalCategoryId;
    
    /**
     * CANONICAL: Medical Service ID
     * Optional - FK to MedicalService table
     * Preferred over serviceCode for precise coverage lookup
     */
    private Long medicalServiceId;

    /** Care setting used for context-specific coverage resolution. */
    @Builder.Default
    private EncounterType encounterType = EncounterType.OUTPATIENT;

    /**
     * Convenient factory method for simple checks
     */
    public static EligibilityCheckRequest of(Long memberId, LocalDate serviceDate) {
        return EligibilityCheckRequest.builder()
                .memberId(memberId)
                .serviceDate(serviceDate)
                .build();
    }

    /**
     * Factory method with benefit policy override
     */
    public static EligibilityCheckRequest of(Long memberId, Long benefitPolicyId, LocalDate serviceDate) {
        return EligibilityCheckRequest.builder()
                .memberId(memberId)
                .benefitPolicyId(benefitPolicyId)
                .serviceDate(serviceDate)
                .build();
    }

    /**
     * Full factory method
     */
    public static EligibilityCheckRequest of(Long memberId, Long benefitPolicyId, Long providerId, 
                                              LocalDate serviceDate, String serviceCode) {
        return EligibilityCheckRequest.builder()
                .memberId(memberId)
                .benefitPolicyId(benefitPolicyId)
                .providerId(providerId)
                .serviceDate(serviceDate)
                .serviceCode(serviceCode)
                .build();
    }
}
