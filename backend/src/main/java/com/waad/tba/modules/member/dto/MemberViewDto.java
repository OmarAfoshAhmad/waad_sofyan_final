package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.waad.tba.modules.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "DTO for viewing member details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberViewDto {

    @Schema(description = "Member ID", example = "1")
    private Long id;

    // Personal Information
    @Schema(description = "Full name", example = "أحمد محمد علي")
    private String fullName;

    @Schema(description = "National Number (الرقم الوطني)", example = "289123456789")
    private String nationalNumber;

    @Schema(description = "Card number (رقم بطاقة العضو)", example = "CARD-12345")
    private String cardNumber;

    @Schema(description = "Barcode (auto-generated unique identifier for QR)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String barcode;

    @Schema(description = "Birth date", example = "1990-01-15")
    private LocalDate birthDate;

    @Schema(description = "Gender", example = "MALE")
    private Member.Gender gender;

    @Schema(description = "Marital status", example = "MARRIED")
    private Member.MaritalStatus maritalStatus;

    @Schema(description = "Phone number", example = "+96512345678")
    private String phone;

    @Schema(description = "Email address", example = "ahmed@example.com")
    private String email;

    @Schema(description = "Address", example = "طرابلس، شارع الجمهورية، عمارة 15")
    private String address;

    @Schema(description = "Nationality", example = "Libyan")
    private String nationality;

    // Insurance Information
    @Schema(description = "Policy number", example = "POL-2024-001")
    private String policyNumber;

    // Benefit Policy Information
    @Schema(description = "Benefit Policy ID", example = "1")
    private Long benefitPolicyId;

    @Schema(description = "Benefit Policy name", example = "Gold Coverage Policy")
    private String benefitPolicyName;

    @Schema(description = "Benefit Policy code", example = "POL-GOLD-001")
    private String benefitPolicyCode;

    @Schema(description = "Benefit Policy status", example = "ACTIVE")
    private String benefitPolicyStatus;

    @Schema(description = "Benefit Policy start date", example = "2024-01-01")
    private LocalDate benefitPolicyStartDate;

    @Schema(description = "Benefit Policy end date", example = "2025-01-01")
    private LocalDate benefitPolicyEndDate;

    // Employment Information
    @Schema(description = "Employer ID", example = "1")
    private Long employerId;

    @Schema(description = "Employer name", example = "ABC Company")
    private String employerName;

    @Schema(description = "Employer code", example = "EMP-001")
    private String employerCode;

    @Schema(description = "Employee number", example = "EMP-001")
    private String employeeNumber;

    @Schema(description = "Join date", example = "2024-01-01")
    private LocalDate joinDate;

    @Schema(description = "Occupation", example = "Software Engineer")
    private String occupation;

    @Schema(description = "Calculated Age")
    public Integer getAge() {
        if (this.birthDate == null) {
            return null;
        }
        return java.time.Period.between(this.birthDate, java.time.LocalDate.now()).getYears();
    }

    // Membership Status
    @Schema(description = "Member status", example = "ACTIVE")
    private Member.MemberStatus status;

    @Schema(description = "Start date", example = "2024-01-01")
    private LocalDate startDate;

    @Schema(description = "End date", example = "2025-01-01")
    private LocalDate endDate;

    @Schema(description = "Card status", example = "ACTIVE")
    private Member.CardStatus cardStatus;

    @Schema(description = "Blocked reason", example = "Exceeded limit")
    private String blockedReason;

    // Eligibility
    @Schema(description = "Eligibility status", example = "true")
    private Boolean eligibilityStatus;

    @Schema(description = "Photo URL", example = "https://example.com/photo.jpg")
    private String photoUrl;

    @Schema(description = "Profile photo storage path", example = "members/photos/abc.jpg")
    private String profilePhotoPath;

    @Schema(description = "Notes", example = "VIP member")
    private String notes;

    @Schema(description = "Active flag", example = "true")
    private Boolean active;

    // Audit
    @Schema(description = "Created by", example = "admin")
    private String createdBy;

    @Schema(description = "Updated by", example = "admin")
    private String updatedBy;

    @Schema(description = "Created at", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Updated at", example = "2024-01-01T10:00:00")
    private LocalDateTime updatedAt;

    // Financial Information (Calculated/Current)
    @Schema(description = "Annual limit (Policy)", example = "10000.00")
    private java.math.BigDecimal annualLimit;

    @Schema(description = "Used amount (Approved claims)", example = "2500.00")
    private java.math.BigDecimal usedAmount;

    @Schema(description = "Remaining limit", example = "7500.00")
    private java.math.BigDecimal remainingLimit;

    @Schema(description = "Utilization percentage", example = "25.0")
    private Double usagePercentage;

    // ==================== UNIFIED MEMBER ARCHITECTURE ====================

    /**
     * Member Type - AUTO-CALCULATED.
     * PRINCIPAL: This is a head of family (parent_id = null)
     * DEPENDENT: This is a family member (parent_id != null)
     */
    @Schema(description = "Member type", example = "PRINCIPAL")
    private String type; // PRINCIPAL or DEPENDENT

    /**
     * Parent Member ID - Only for DEPENDENT members.
     * NULL for PRINCIPAL members.
     */
    @Schema(description = "Parent member ID - for dependents only", example = "123")
    private Long parentId;

    /**
     * Parent Member Full Name - Only for DEPENDENT members.
     */
    @Schema(description = "Parent member full name - for dependents only", example = "أحمد محمد علي")
    private String parentFullName;

    /**
     * Relationship Type - Only for DEPENDENT members.
     * NULL for PRINCIPAL members.
     */
    @Schema(description = "Relationship type - for dependents only", example = "SON")
    private Member.Relationship relationship;

    /**
     * List of Dependents - Only populated for PRINCIPAL members.
     * Empty/NULL for DEPENDENT members.
     * 
     * This replaces the old "familyMembers" concept.
     */
    @Schema(description = "List of dependents (family members)")
    private List<DependentViewDto> dependents;

    /**
     * Dependents Count - Number of dependents for this principal.
     * 0 for DEPENDENT members.
     */
    @Schema(description = "Number of dependents", example = "3")
    private Integer dependentsCount;

    // Flexible Attributes
    @Schema(description = "List of custom attributes (key-value pairs)")
    private List<MemberAttributeDto> attributes;
}
