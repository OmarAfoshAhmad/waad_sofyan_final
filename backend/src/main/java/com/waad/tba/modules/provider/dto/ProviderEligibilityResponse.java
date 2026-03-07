package com.waad.tba.modules.provider.dto;

import com.waad.tba.modules.member.dto.MemberViewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Provider Eligibility Response.
 * 
 * Returned to healthcare providers after eligibility check.
 * Contains all information needed for service delivery decision.
 * 
 * Key Information:
 * - Eligibility status (ELIGIBLE / INELIGIBLE)
 * - Principal member details
 * - All family members (for selection)
 * - Coverage information
 * - Annual limit usage
 * - Benefit policy details
 * 
 * @since Phase 1 - Provider Portal
 */
@Schema(description = "Provider eligibility response - complete member and coverage information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderEligibilityResponse {
    
    // ==================== ELIGIBILITY STATUS ====================
    
    /**
     * Overall Eligibility Status.
     * true = At least ONE family member is eligible for service
     * false = ALL family members are ineligible
     */
    @Schema(description = "Overall eligibility status", example = "true")
    private Boolean eligible;
    
    /**
     * Eligibility Message - Clear explanation for provider.
     */
    @Schema(
        description = "Eligibility message in Arabic", 
        example = "العائلة مؤهلة - يرجى اختيار المريض من القائمة"
    )
    private String message;
    
    /**
     * Status Code for UI styling.
     * - SUCCESS: Green - Eligible
     * - WARNING: Yellow - Partially eligible (some limits reached)
     * - ERROR: Red - Ineligible
     */
    @Schema(description = "Status code for UI", example = "SUCCESS")
    private String statusCode;
    
    // ==================== MEMBER INFORMATION ====================
    
    /**
     * Principal Member - Head of family.
     * This is the member whose card was scanned.
     */
    @Schema(description = "Principal member (card holder)")
    private MemberViewDto principalMember;
    
    /**
     * Family Members - Principal + All Dependents.
     * Provider should display this list for patient selection.
     */
    @Schema(description = "All family members (principal + dependents)")
    private List<FamilyMemberInfo> familyMembers;
    
    /**
     * Total Family Size.
     */
    @Schema(description = "Total family members count", example = "4")
    private Integer totalFamilyMembers;
    
    /**
     * Eligible Members Count.
     */
    @Schema(description = "Number of eligible members", example = "3")
    private Integer eligibleMembersCount;
    
    // ==================== COVERAGE INFORMATION ====================
    
    /**
     * Benefit Policy Information.
     */
    @Schema(description = "Benefit policy ID", example = "1")
    private Long benefitPolicyId;
    
    @Schema(description = "Benefit policy name", example = "Gold Coverage Policy")
    private String benefitPolicyName;
    
    @Schema(description = "Policy status", example = "ACTIVE")
    private String policyStatus;
    
    /**
     * Employer Information.
     */
    @Schema(description = "Employer organization name", example = "شركة ليبيا للتأمين")
    private String employerName;
    
    /**
     * Coverage Summary - Quick overview for provider.
     */
    @Schema(description = "Coverage type", example = "Gold Plan - Full Coverage")
    private String coverageType;
    
    @Schema(description = "Coverage effective date", example = "2026-01-01")
    private String effectiveDate;
    
    @Schema(description = "Coverage end date", example = "2026-12-31")
    private String endDate;
    
    // ==================== ANNUAL LIMIT INFORMATION ====================
    
    /**
     * Annual Limit for Principal Member.
     * NOTE: Each member has INDEPENDENT annual limit.
     */
    @Schema(description = "Principal member annual limit (LYD)", example = "5000.00")
    private BigDecimal principalAnnualLimit;
    
    @Schema(description = "Principal member used amount (LYD)", example = "1250.50")
    private BigDecimal principalUsedAmount;
    
    @Schema(description = "Principal member remaining limit (LYD)", example = "3749.50")
    private BigDecimal principalRemainingLimit;
    
    @Schema(description = "Principal member usage percentage", example = "25.01")
    private Double principalUsagePercentage;
    
    // ==================== ADDITIONAL INFORMATION ====================
    
    /**
     * Warnings or Alerts for Provider.
     */
    @Schema(description = "Warning messages (if any)", example = "[\"Annual limit 80% reached for member Ahmad\"]")
    private List<String> warnings;
    
    /**
     * Covered Services Summary.
     */
    @Schema(description = "List of covered service categories", example = "[\"Consultations\", \"Laboratory Tests\", \"Radiology\"]")
    private List<String> coveredServices;
    
    /**
     * Request Timestamp.
     */
    @Schema(description = "Check timestamp", example = "2026-01-11T10:30:00")
    private String checkTimestamp;
    
    /**
     * Barcode that was checked.
     */
    @Schema(description = "Member barcode", example = "WAD-2026-00001234")
    private String barcode;
    
    // ==================== NESTED DTOs ====================
    
    /**
     * Family Member Information (Simplified for Provider View).
     */
    @Schema(description = "Family member information for selection")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyMemberInfo {
        
        @Schema(description = "Member ID", example = "123")
        private Long memberId;
        
        @Schema(description = "Is principal (true) or dependent (false)", example = "true")
        private Boolean isPrincipal;
        
        @Schema(description = "Full name", example = "أحمد محمد علي")
        private String fullName;
        
        @Schema(description = "Relationship to principal", example = "SELF")
        private String relationship;
        
        @Schema(description = "Birth date", example = "1990-05-15")
        private String birthDate;
        
        @Schema(description = "Age", example = "35")
        private Integer age;
        
        @Schema(description = "Gender", example = "MALE")
        private String gender;
        
        @Schema(description = "National ID", example = "123456789012")
        private String nationalId;
        
        @Schema(description = "Member barcode (principals only)", example = "WAD-2026-00001234")
        private String barcode;
        
        @Schema(description = "Eligibility status", example = "true")
        private Boolean eligible;
        
        @Schema(description = "Eligibility message", example = "مؤهل للخدمة")
        private String eligibilityMessage;
        
        @Schema(description = "Annual limit (LYD)", example = "5000.00")
        private BigDecimal annualLimit;
        
        @Schema(description = "Used amount (LYD)", example = "1250.50")
        private BigDecimal usedAmount;
        
        @Schema(description = "Remaining limit (LYD)", example = "3749.50")
        private BigDecimal remainingLimit;
        
        @Schema(description = "Usage percentage", example = "25.01")
        private Double usagePercentage;
        
        @Schema(description = "Active status", example = "true")
        private Boolean active;
        
        @Schema(description = "Card number (for display)", example = "****1234")
        private String cardNumber;
        
        @Schema(description = "Profile photo URL", example = "/api/v1/unified-members/123/photo")
        private String profileImage;
        
        @Schema(description = "Photo path in storage", example = "/uploads/members/123.jpg")
        private String photoPath;
    }
}
