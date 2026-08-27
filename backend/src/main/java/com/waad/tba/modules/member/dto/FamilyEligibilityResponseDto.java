package com.waad.tba.modules.member.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Family Eligibility Response (Unified Architecture).
 * 
 * When scanning a barcode, the system returns the PRINCIPAL member
 * along with ALL dependents, allowing the user to select who is visiting.
 * 
 * This is the correct UX flow:
 * 1. Scan barcode (principal's barcode)
 * 2. System shows principal + all dependents
 * 3. User selects the person who is visiting
 * 4. System starts visit for selected person
 */
@Schema(description = "Family eligibility response - principal + all dependents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyEligibilityResponseDto {

    /**
     * Eligibility Status - true if ANY family member is eligible.
     * false if ALL family members are blocked/ineligible.
     */
    @Schema(description = "Family eligibility status", example = "true")
    private Boolean eligible;

    /**
     * Eligibility Message - Explanation of eligibility status.
     */
    @Schema(description = "Eligibility message", example = "العائلة مؤهلة - يرجى اختيار العضو")
    private String message;

    /**
     * Principal Member - Head of the family.
     * Always returned when barcode is found.
     */
    @Schema(description = "Principal member (head of family)")
    private MemberViewDto principal;

    /**
     * List of Dependents - All family members.
     * May be empty if principal has no dependents.
     */
    @Schema(description = "List of dependents (family members)")
    private List<DependentViewDto> dependents;

    /**
     * Total Family Members Count - Principal + Dependents.
     */
    @Schema(description = "Total family members count", example = "4")
    private Integer totalFamilyMembers;

    /**
     * Eligible Members Count - Number of eligible family members.
     */
    @Schema(description = "Number of eligible members", example = "3")
    private Integer eligibleMembersCount;

    /**
     * Family Barcode - The barcode that was scanned.
     */
    @Schema(description = "Family barcode (principal's barcode)", example = "WAD-2026-00001234")
    private String familyBarcode;

    /**
     * Benefit Policy Information - Inherited by all family members.
     */
    @Schema(description = "Benefit policy ID", example = "1")
    private Long benefitPolicyId;

    @Schema(description = "Benefit policy name", example = "Gold Coverage Policy")
    private String benefitPolicyName;

    @Schema(description = "Benefit policy status", example = "ACTIVE")
    private String benefitPolicyStatus;

    /**
     * Employer Information - Inherited by all family members.
     */
    @Schema(description = "Employer organization ID", example = "10")
    private Long employerOrgId;

    @Schema(description = "Employer organization name", example = "شركة الكويت للتأمين")
    private String employerOrgName;

    /**
     * Financial Limits (Family Level)
     */
    @Schema(description = "Total family annual limit", example = "50000.00")
    private java.math.BigDecimal annualLimit;

    @Schema(description = "Individual member limit (if applicable)", example = "10000.00")
    private java.math.BigDecimal perMemberLimit;

    @Schema(description = "Total remaining family limit", example = "42000.00")
    private java.math.BigDecimal remainingFamilyLimit;

    /**
     * Whether the financial limit fields above ({@link #annualLimit},
     * {@link #remainingFamilyLimit}) were successfully computed. False means
     * a financial lookup failed and those fields are stale/absent -- the
     * caller must not treat a null/zero remainingFamilyLimit as "no limit
     * left" in that case. Previously this failure was silently swallowed and
     * the response returned as if financial data were simply zero.
     */
    @Schema(description = "False if financial limit data could not be retrieved (fields above are unreliable)", example = "true")
    @Builder.Default
    private Boolean financialDataAvailable = true;

    @Schema(description = "Arabic explanation when financialDataAvailable is false", example = "تعذر جلب بيانات السقف المالي")
    private String financialDataError;

    /**
     * Per-member reason a member was found NOT eligible, keyed by member id.
     * Only populated for ineligible members. A member here does not
     * necessarily mean a genuine coverage denial -- see
     * {@link #systemErrorMemberIds} to distinguish "the rules engine
     * rejected this member" from "the engine could not be reached for this
     * member", which callers must present differently (a system error is
     * retry-able and not the member's fault).
     */
    @Schema(description = "Arabic ineligibility reason per member id, for members that are not eligible")
    private Map<Long, String> ineligibilityReasonsAr;

    @Schema(description = "Member ids whose eligibility check failed with a system error rather than a genuine rule denial")
    private java.util.Set<Long> systemErrorMemberIds;

    // ==================== HELPER METHODS ====================

    /**
     * Check if family has any eligible members.
     */
    public boolean hasEligibleMembers() {
        return eligible != null && eligible && eligibleMembersCount != null && eligibleMembersCount > 0;
    }

    /**
     * Check if principal is eligible.
     */
    public boolean isPrincipalEligible() {
        return principal != null &&
                principal.getEligibilityStatus() != null &&
                principal.getEligibilityStatus() &&
                principal.getActive() != null &&
                principal.getActive();
    }
}
