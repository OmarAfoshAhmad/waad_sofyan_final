package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ARCHITECTURAL REBUILD (2026-01-15): Visit-Centric, Contract-Driven
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * WHAT CANNOT BE UPDATED (Contract-Driven, derived from Visit):
 * - providerName (from Visit.Provider)
 * - visitDate (from Visit.visitDate)
 * - requestedAmount (calculated from ClaimLines)
 * - lines (prices from ProviderContract)
 * 
 * WHAT CAN BE UPDATED:
 * - doctorName (correction)
 * - diagnosisCode/diagnosisDescription (correction)
 * - status (via state machine)
 * - approvedAmount (by reviewer)
 * - reviewerComment
 * - preAuthorizationId (linking)
 * - attachments
 * - active
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimUpdateDto {

    // ═══════════════════════════════════════════════════════════════════════════
    // ALLOWED UPDATES (Non-architectural corrections)
    // ═══════════════════════════════════════════════════════════════════════════

    private String doctorName;

    /**
     * Diagnosis Code (ICD-10 or local code) - correction only
     */
    private String diagnosisCode;

    /**
     * Diagnosis Description - correction only
     */
    private String diagnosisDescription;

    /**
     * @deprecated Status changes should use workflow endpoints:
     *             /submit, /start-review, /approve, /reject, /settle
     *             This field is kept for backwards compatibility but will be
     *             ignored.
     */
    @Deprecated
    private ClaimStatus status;

    /**
     * Approved amount - set by reviewer during approval
     */
    @DecimalMin(value = "0.00", message = "Approved amount must be >= 0")
    private BigDecimal approvedAmount;

    /**
     * Reviewer comment - set during review
     */
    @Size(max = 2000, message = "Reviewer comment must not exceed 2000 characters")
    private String reviewerComment;

    /**
     * Link to PreAuthorization (if not set during creation)
     */
    private Long preAuthorizationId;

    /**
     * Attachments can be updated (added/removed)
     */
    private List<ClaimAttachmentDto> attachments;

    /**
     * Active status - for soft delete
     */
    private Boolean active;

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOVED FIELDS (ARCHITECTURAL VIOLATIONS if allowed to update)
    // ═══════════════════════════════════════════════════════════════════════════
    // - providerName: derived from Visit.Provider
    // - visitDate: derived from Visit.visitDate
    // - requestedAmount: calculated from lines
    // - lines: prices from ProviderContract, cannot be changed
    // - benefitPackageId: from Member, not Claim

    /**
     * @deprecated Lines cannot be updated - prices are contract-driven
     */
    @Deprecated
    private List<ClaimLineDto> lines;
}
