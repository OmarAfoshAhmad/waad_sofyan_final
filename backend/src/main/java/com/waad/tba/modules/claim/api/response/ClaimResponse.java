package com.waad.tba.modules.claim.api.response;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Claim Response
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * FINANCIAL SAFETY GUARANTEE:
 * All financial fields in this response are READ-ONLY and calculated by
 * backend.
 * Frontend CANNOT modify these values.
 * 
 * This response contains:
 * 1. Claim identification (id, claimNumber)
 * 2. Related entities (member, provider, visit, pre-authorization)
 * 3. Financial snapshot (READ-ONLY amounts)
 * 4. Status and workflow state
 * 5. Audit trail
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponse {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════════════════

    private Long id;

    /**
     * Claim Number (formatted reference: CLM-YYYYMMDD-XXXX)
     */
    private String claimNumber;

    // ═══════════════════════════════════════════════════════════════════════════
    // RELATED ENTITIES
    // ═══════════════════════════════════════════════════════════════════════════

    // Member information
    private Long memberId;
    private String memberFullName;
    private String memberName; // Alias for frontend compatibility
    private String memberNationalNumber;

    // Employer information
    private Long employerId;
    private String employerName;
    private String employerCode;

    // Insurance Company information
    private String insuranceCompanyName;
    private String insuranceCompanyCode;

    // Benefit Package information
    private Long benefitPackageId;
    private String benefitPackageName;
    private String benefitPackageCode;

    // Pre-Authorization information
    private Long preAuthorizationId;
    private String preAuthorizationStatus;

    // Visit information (VISIT-CENTRIC ARCHITECTURE)
    private Long visitId;
    private String visitType;
    private LocalDate serviceDate;

    // Provider information
    private Long providerId;
    private String providerName;
    private String doctorName;

    // Diagnosis (System-Selected)
    private String diagnosisCode;
    private String diagnosisDescription;

    // ═══════════════════════════════════════════════════════════════════════════
    // FINANCIAL SNAPSHOT (READ-ONLY - BACKEND CALCULATED)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Requested amount (calculated from contract pricing)
     * READ-ONLY - Calculated by backend
     */
    private BigDecimal requestedAmount;

    /**
     * Total amount (alias for requestedAmount)
     * READ-ONLY - Calculated by backend
     */
    private BigDecimal totalAmount;

    /**
     * Approved amount (calculated during approval workflow)
     * READ-ONLY - Calculated by backend from:
     * - Provider contract pricing
     * - Benefit policy rules
     * - Cost breakdown engine
     * - Member usage limits
     */
    private BigDecimal approvedAmount;

    /**
     * Refused amount (total line-level refusals + rejections)
     * READ-ONLY - Calculated by backend
     */
    private BigDecimal refusedAmount;

    /**
     * Difference amount (requestedAmount - netProviderAmount)
     * READ-ONLY - Calculated by backend
     */
    private BigDecimal differenceAmount;

    /**
     * Patient co-pay (patient share of cost)
     * READ-ONLY - Calculated by backend from:
     * - Deductibles
     * - Co-pay percentage
     * - Benefit policy rules
     */
    private BigDecimal patientCoPay;

    /**
     * Net provider amount (amount to be paid to provider)
     * READ-ONLY - Calculated by backend (approvedAmount - patientCoPay)
     */
    private BigDecimal netProviderAmount;

    /**
     * Co-pay percentage applied
     * READ-ONLY - From benefit policy
     */
    private BigDecimal coPayPercent;

    /**
     * Deductible applied to this claim
     * READ-ONLY - Calculated by backend
     */
    private BigDecimal deductibleApplied;

    // ═══════════════════════════════════════════════════════════════════════════
    // SETTLEMENT INFORMATION (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Payment reference from finance system
     * READ-ONLY - Set by settlement batch workflow
     */
    private String paymentReference;

    /**
     * Settlement date
     * READ-ONLY - Set when claim is settled
     */
    private LocalDateTime settledAt;

    /**
     * Settlement notes
     * READ-ONLY - From settlement batch
     */
    private String settlementNotes;

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA TRACKING (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Expected completion date (calculated at submission)
     * READ-ONLY - Backend calculates based on SLA rules
     */
    private LocalDate expectedCompletionDate;

    /**
     * Actual completion date (recorded at approval/rejection)
     * READ-ONLY - Backend sets when workflow completes
     */
    private LocalDate actualCompletionDate;

    /**
     * Whether processing was within SLA
     * READ-ONLY - Backend calculates
     */
    private Boolean withinSla;

    /**
     * Business days taken for processing
     * READ-ONLY - Backend calculates
     */
    private Integer businessDaysTaken;

    /**
     * SLA days configured at submission time
     * READ-ONLY - From system configuration
     */
    private Integer slaDaysConfigured;

    /**
     * SLA status (ON_TRACK, AT_RISK, BREACHED, MET)
     * READ-ONLY - Backend calculates
     */
    private String slaStatus;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS AND WORKFLOW (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Current claim status
     * READ-ONLY - Managed by workflow state machine
     */
    private ClaimStatus status;

    /**
     * Status label (localized)
     * READ-ONLY - Backend provides display label
     */
    private String statusLabel;

    /**
     * Reviewer comment
     * READ-ONLY - Set during review/approval/rejection
     */
    private String reviewerComment;

    /**
     * Review timestamp
     * READ-ONLY - Set when claim is reviewed
     */
    private LocalDateTime reviewedAt;

    /**
     * Allowed next statuses for this claim (BACKEND-DRIVEN WORKFLOW)
     * READ-ONLY - Frontend MUST use this to determine available actions
     * Empty set = terminal state or no permissions
     */
    private Set<ClaimStatus> allowedNextStatuses;

    /**
     * Whether the claim can be edited in current status
     * READ-ONLY - Only DRAFT and RETURNED_FOR_INFO allow edits
     */
    private Boolean canEdit;

    // ═══════════════════════════════════════════════════════════════════════════
    // LINE ITEMS AND ATTACHMENTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Service count (number of claim lines)
     * READ-ONLY - Calculated from lines collection
     */
    private Integer serviceCount;

    /**
     * Attachments count
     * READ-ONLY - Calculated from attachments collection
     */
    private Integer attachmentsCount;

    /**
     * Claim lines (services)
     * READ-ONLY - Contains service details and calculated prices
     */
    private List<ClaimLineResponse> lines;

    /**
     * Claim attachments
     * READ-ONLY - Contains attachment metadata
     */
    private List<ClaimAttachmentResponse> attachments;

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT TRAIL (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Active status (soft delete flag)
     * READ-ONLY - Managed by backend
     */
    private Boolean active;

    /**
     * Creation timestamp
     * READ-ONLY - Set by backend on creation
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     * READ-ONLY - Set by backend on updates
     */
    private LocalDateTime updatedAt;

    /**
     * Created by user
     * READ-ONLY - From security context
     */
    private String createdBy;

    /**
     * Last updated by user
     * READ-ONLY - From security context
     */
    private String updatedBy;

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED RESPONSE TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Claim line response (service line item)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimLineResponse {
        private Long id;
        private Long medicalServiceId;
        private String medicalServiceName;
        private String medicalServiceCode;
        private Long serviceCategoryId;
        private String serviceCategoryName;
        private Integer quantity;

        /**
         * Unit price (READ-ONLY - from provider contract)
         */
        private BigDecimal unitPrice;

        /**
         * Total price (READ-ONLY - calculated as quantity * unitPrice)
         */
        private BigDecimal totalPrice;

        /**
         * Approved price (READ-ONLY - calculated during approval)
         */
        private BigDecimal approvedPrice;

        private BigDecimal refusedAmount;
        private String notes;

        // Rejection info
        private Boolean rejected;
        private String rejectionReason;

        // Coverage snapshot
        private Integer coveragePercent;
        private Integer patientSharePercent;
        private BigDecimal benefitLimit; // Added for UI display
        private BigDecimal usedAmount; // Added for UI display
        private BigDecimal remainingAmount; // Added for UI display

        // Detailed Audit Fields
        private BigDecimal requestedUnitPrice;
        private BigDecimal approvedUnitPrice;
        private Integer requestedQuantity;
        private Integer approvedQuantity;
        private String rejectionReasonCode;
        private String reviewerNotes;

        private Long pricingItemId;
        private Boolean active;
    }

    /**
     * Claim attachment response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimAttachmentResponse {
        private Long id;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String fileUrl;
        private String description;
        private Boolean isRequired;
        private LocalDateTime uploadedAt;
        private String uploadedBy;
    }

}
