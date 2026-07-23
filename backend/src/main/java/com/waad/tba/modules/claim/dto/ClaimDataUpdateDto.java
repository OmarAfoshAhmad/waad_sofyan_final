package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for updating claim DATA fields only.
 * 
 * SECURITY: This DTO can ONLY be used by PROVIDER and EMPLOYER_ADMIN roles.
 * REVIEWERS are NOT allowed to modify these fields.
 * 
 * Allowed in statuses: DRAFT, NEEDS_CORRECTION only.
 * 
 * Fields NOT included here (financial/review fields):
 * - status (except the trusted internal correction re-approval path)
 * - approvedAmount (reviewer-only field)
 * - reviewerComment (reviewer-only field)
 * 
 * @since Provider Portal Security Fix (Phase 0)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimDataUpdateDto {

    /**
     * Doctor name - can be corrected by provider
     */
    private String doctorName;

    /**
     * Diagnosis Code (ICD-10) - can be corrected by provider
     */
    @NotBlank(message = "Diagnosis code is required")
    private String diagnosisCode;

    /**
     * Diagnosis Description - can be corrected by provider
     */
    private String diagnosisDescription;

    /**
     * Link to PreAuthorization (if applicable)
     * Provider can link/unlink during DRAFT phase
     */
    private Long preAuthorizationId;

    private String complaint;
    private String rejectionReason;
    private EncounterType encounterType;
    private Boolean fullCoverage;

    /** New status for trusted internal re-approval/rejection edit paths. */
    private ClaimStatus status;

    /**
     * Claim lines - can be modified in DRAFT only
     * Prices are still contract-driven and validated by backend
     */
    @Valid
    private List<ClaimLineDto> lines;

    /**
     * Attachments - can be added/removed
     */
    private List<ClaimAttachmentDto> attachments;
}
