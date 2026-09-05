package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for Provider Visit operations.
 * 
 * Used for:
 * - Visit registration response
 * - Visit log entries
 * - Visit details retrieval
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVisitResponse {
    
    /**
     * Was the operation successful?
     */
    private Boolean success;
    
    /**
     * Success/error message
     */
    private String message;
    
    // ==================== VISIT DATA ====================
    
    /**
     * Visit ID
     */
    private Long visitId;
    
    /**
     * Visit date
     */
    private LocalDate visitDate;
    
    /**
     * Visit type (OUTPATIENT, INPATIENT, etc.)
     */
    private String visitType;
    
    /**
     * Visit type label (Arabic)
     */
    private String visitTypeLabel;
    
    /**
     * Visit status (REGISTERED, IN_PROGRESS, etc.)
     */
    private String status;
    
    /**
     * Visit status label (Arabic)
     */
    private String statusLabel;
    
    // ==================== MEMBER DATA ====================
    
    /**
     * Member ID
     */
    private Long memberId;
    
    /**
     * Member full name
     */
    private String memberName;
    
    /**
     * Member civil ID
     */
    private String memberCivilId;
    
    /**
     * Member barcode
     */
    private String memberBarcode;
    
    /**
     * Member card number
     */
    private String memberCardNumber;
    
    /**
     * Member status (ACTIVE, INACTIVE)
     */
    private String memberStatus;
    
    // ==================== EMPLOYER DATA ====================
    
    /**
     * Employer ID
     */
    private Long employerId;

    /**
     * Employer name
     */
    private String employerName;
    
    // ==================== PROVIDER DATA ====================
    
    /**
     * Provider ID
     */
    private Long providerId;
    
    /**
     * Provider name
     */
    private String providerName;
    
    /**
     * Doctor name
     */
    private String doctorName;
    
    /**
     * Medical specialty
     */
    private String specialty;
    
    // ==================== MEDICAL DATA ====================
    
    /**
     * Diagnosis
     */
    private String diagnosis;
    
    /**
     * Treatment
     */
    private String treatment;
    
    /**
     * Total amount
     */
    private BigDecimal totalAmount;
    
    /**
     * Notes
     */
    private String notes;
    
    // ==================== WORKFLOW FLAGS ====================
    
    /**
     * Whether a claim can be created from this visit
     */
    private Boolean canCreateClaim;
    
    /**
     * Whether a pre-authorization can be created from this visit
     */
    private Boolean canCreatePreAuth;
    
    /**
     * Number of claims linked to this visit
     */
    private Integer claimCount;
    
    /**
     * Latest claim ID (if any)
     */
    private Long latestClaimId;
    
    /**
     * Latest claim status (DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, SETTLED, etc.)
     */
    private String latestClaimStatus;
    
    /**
     * Latest claim status label (Arabic)
     */
    private String latestClaimStatusLabel;

    /**
     * Number of pre-authorizations linked to this visit
     */
    private Integer preAuthCount;
    
    /**
     * Latest pre-authorization ID (if any)
     */
    private Long latestPreAuthId;
    
    /**
     * Latest pre-authorization status (PENDING, APPROVED, REJECTED, etc.)
     */
    private String latestPreAuthStatus;
    
    /**
     * Latest pre-authorization status label (Arabic)
     */
    private String latestPreAuthStatusLabel;
    
    // ==================== AUDIT ====================
    
    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Updated timestamp
     */
    private LocalDateTime updatedAt;
}
