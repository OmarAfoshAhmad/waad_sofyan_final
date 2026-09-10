package com.waad.tba.modules.member.dto;

import java.time.LocalDate;

import com.waad.tba.modules.member.entity.Member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a DEPENDENT member (unified architecture).
 * 
 * This DTO is used when creating dependents inline with a principal member,
 * or when creating a standalone dependent via POST /api/members with parentId.
 * 
 * Key Differences from Principal:
 * - NO barcode (inherited from parent)
 * - relationship is REQUIRED
 * - cardNumber is auto-generated as {parent_card}{relationship_code}{sequence}
 *   unless an explicit manual correction is supplied
 * - parentId is auto-assigned during creation
 */
@Schema(description = "DTO for creating a dependent member (family member)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("deprecation")
public class DependentMemberDto {

    @Schema(description = "Relationship type", example = "SON", required = true)
    @NotNull(message = "Relationship is required for dependents")
    private Member.Relationship relationship;

    @Schema(description = "Full name", example = "محمد أحمد علي", required = true)
    @NotBlank(message = "Full name is required")
    private String fullName;

    @Schema(description = "National Number (الرقم الوطني) - OPTIONAL", example = "289123456789")
    private String nationalNumber;

    @Schema(description = "Card number - OPTIONAL; auto-generated when blank", example = "JFZ20253214D1")
    private String cardNumber;

    @Schema(description = "Birth date - OPTIONAL", example = "2010-05-15")
    private LocalDate birthDate;

    @Schema(description = "Gender - OPTIONAL, defaults to UNDEFINED", example = "MALE")
    private Member.Gender gender;

    @Schema(description = "Marital status - OPTIONAL", example = "SINGLE")
    private Member.MaritalStatus maritalStatus;

    @Schema(description = "Phone number - OPTIONAL", example = "+96512345678")
    private String phone;

    @Schema(description = "Email - OPTIONAL", example = "dependent@example.com")
    private String email;

    @Schema(description = "Occupation - OPTIONAL", example = "Student")
    private String occupation;

    @Schema(description = "Notes - OPTIONAL", example = "Requires special care")
    private String notes;

    @Schema(description = "Active flag - defaults to true", example = "true")
    private Boolean active;

    // ==================== FORBIDDEN FIELDS ====================
    // The following fields are NOT allowed for dependents:
    
    // ❌ NO barcode - dependents don't have barcodes
    // ✅ cardNumber may be supplied manually; otherwise auto-generated from parent
    // ❌ NO parentId - set automatically during creation
    // ❌ NO employerId - inherited from principal
    // ❌ NO benefitPolicyId - inherited from principal
    // ❌ NO policyNumber - inherited from principal
    // ❌ NO dependents - dependents cannot have sub-dependents (single level only)
}

