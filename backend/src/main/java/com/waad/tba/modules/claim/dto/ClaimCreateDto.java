package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for creating a new Claim (CANONICAL REBUILD 2026-01-16)
 * 
 * ARCHITECTURAL LAWS:
 * 1. visitId is MANDATORY - no standalone claim creation
 * 2. lines (ClaimLineDto) with medicalServiceId is MANDATORY - no free-text
 * services
 * 3. providerId is AUTO-FILLED from JWT security context
 * 4. All prices are AUTO-RESOLVED from Provider Contract
 * 5. If service requires PA, preAuthorizationId is MANDATORY
 * 
 * Data Flow: Visit → MedicalService (from Contract) → ContractPrice →
 * Calculated Amounts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimCreateDto {

    private ClaimStatus status;

    // ==================== MANDATORY FIELDS ====================

    /**
     * REQUIRED: Visit ID that this claim is linked to.
     * ARCHITECTURAL LAW: Claims can ONLY be created from an existing Visit.
     */
    @NotNull(message = "Visit ID is required - Claims must originate from a Visit")
    @Positive(message = "Visit ID must be positive")
    private Long visitId;

    /**
     * REQUIRED: Claim lines with medical services
     * ARCHITECTURAL LAW: At least one service line is required - NO empty claims
     */
    @NotEmpty(message = "At least one claim line (service) is required")
    @Valid
    private List<ClaimLineDto> lines;

    // ==================== AUTO-DERIVED FIELDS ====================

    /**
     * Member ID - AUTO-DERIVED from Visit (can be provided for validation)
     */
    private Long memberId;

    /**
     * Provider ID - AUTO-FILLED from JWT security context
     * ARCHITECTURAL LAW: Provider CANNOT override their identity
     */
    private Long providerId;

    // ==================== DIAGNOSIS (SELECTED, NOT TYPED) ====================

    /**
     * Diagnosis ICD-10 code (selected from dropdown)
     */
    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;

    /**
     * Diagnosis description (auto-populated from code)
     */
    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;

    // ==================== PRE-AUTHORIZATION LINK ====================

    /**
     * Pre-authorization ID (REQUIRED if any service requires PA)
     */
    @Positive(message = "Pre-authorization ID must be positive")
    private Long preAuthorizationId;

    // ==================== OPTIONAL FIELDS ====================

    /**
     * Doctor name who performed the service
     */
    @Size(max = 255, message = "Doctor name must not exceed 255 characters")
    private String doctorName;

    /**
     * Service date (defaults to today)
     */
    @PastOrPresent(message = "Service date cannot be in the future")
    private LocalDate serviceDate;

    /**
     * Additional notes
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    private String rejectionReason;
    private String complaint;

    // ==================== COVERAGE CONTEXT ====================
    private Boolean manualCategoryEnabled;
    private String primaryCategoryCode;
    private Boolean fullCoverage;

    /**
     * Optional: The monthly batch this claim belongs to.
     */
    @Positive(message = "Claim Batch ID must be positive")
    private Long claimBatchId;
}
