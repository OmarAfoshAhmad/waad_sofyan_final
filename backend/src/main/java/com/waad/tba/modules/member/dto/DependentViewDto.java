package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.waad.tba.modules.member.entity.Member;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for viewing a DEPENDENT member (unified architecture).
 * 
 * This DTO represents a dependent (family member) in the unified Member
 * structure.
 * It contains all relevant information for displaying a dependent member.
 */
@Schema(description = "DTO for viewing a dependent member (family member)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependentViewDto {

    @Schema(description = "Dependent ID", example = "456")
    private Long id;

    @Schema(description = "Optimistic-lock row version. Required as expectedVersion/expectedVersions when calling "
            + "a family operation (transfer, relationship correction, policy change, reorder, employer transfer) "
            + "so a concurrent edit is rejected instead of silently overwritten.")
    private Long version;

    @Schema(description = "Relationship to principal", example = "SON")
    private Member.Relationship relationship;

    @Schema(description = "Full name", example = "محمد أحمد علي")
    private String fullName;

    @Schema(description = "National Number (الرقم الوطني)", example = "289123456789")
    private String nationalNumber;

    @Schema(description = "Card number (auto-generated with suffix)", example = "123456-01")
    private String cardNumber;

    @Schema(description = "Birth date", example = "2010-05-15")
    private LocalDate birthDate;

    @Schema(description = "Gender", example = "MALE")
    private Member.Gender gender;

    @Schema(description = "Marital status", example = "SINGLE")
    private Member.MaritalStatus maritalStatus;

    @Schema(description = "Phone number", example = "+96512345678")
    private String phone;

    @Schema(description = "Email", example = "dependent@example.com")
    private String email;

    @Schema(description = "Occupation", example = "Student")
    private String occupation;

    @Schema(description = "Member status", example = "ACTIVE")
    private Member.MemberStatus status;

    @Schema(description = "Active flag", example = "true")
    private Boolean active;

    @Schema(description = "Eligibility status", example = "true")
    private Boolean eligibilityStatus;

    @Schema(description = "Notes", example = "Requires special care")
    private String notes;

    @Schema(description = "Photo URL", example = "/api/v1/unified-members/123/photo")
    private String photoUrl;

    @Schema(description = "Profile photo path", example = "members/photos/123.jpg")
    private String profilePhotoPath;

    @Schema(description = "Created at", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Updated at", example = "2024-01-01T10:00:00")
    private LocalDateTime updatedAt;

    // Financial Information (Calculated/Current)
    @Schema(description = "Annual limit (Policy)", example = "10000.00")
    private java.math.BigDecimal annualLimit;

    @Schema(description = "Used amount (Approved claims)", example = "1500.00")
    private java.math.BigDecimal usedAmount;

    @Schema(description = "Remaining limit", example = "8500.00")
    /**
     * What may still be committed: {@code annualLimit - used - reserved},
     * signed. This is the figure an eligibility or claim screen must show,
     * because money already held by an approved pre-authorization is not
     * available to commit again.
     */
    private java.math.BigDecimal remainingLimit;

    /** Money held by approved pre-authorizations, not yet spent. Shown beside
     *  remainingLimit rather than folded into it: the difference between held
     *  and spent is the whole point of the distinction. */
    private java.math.BigDecimal reservedAmount;

    /**
     * The accounting view: {@code annualLimit - usedAmount}, signed. An
     * overspend stays negative here.
     */
    private java.math.BigDecimal actualRemaining;


    @Schema(description = "Utilization percentage", example = "15.0")
    private Double usagePercentage;

    // ==================== DISPLAY FIELDS ====================

    /**
     * Parent Member ID - Always populated for dependents.
     */
    @Schema(description = "Parent member ID", example = "123")
    private Long parentId;

    /**
     * Parent Full Name - For display purposes.
     */
    @Schema(description = "Parent member full name", example = "أحمد محمد علي")
    private String parentFullName;

    /**
     * Family Barcode - Inherited from principal for eligibility checks.
     */
    @Schema(description = "Family barcode (from principal)", example = "WAD-2026-00001234")
    private String familyBarcode;

    // ==================== NOTE ====================
    // Dependents do NOT have their own barcode.
    // They use the principal's barcode for eligibility verification.
}
