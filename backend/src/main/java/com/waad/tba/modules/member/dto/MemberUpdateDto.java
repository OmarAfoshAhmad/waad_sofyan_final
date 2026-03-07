package com.waad.tba.modules.member.dto;

import java.time.LocalDate;

import com.waad.tba.modules.member.entity.Member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for UPDATING an existing Member (Principal)
 * 
 * ARCHITECTURAL HARDENING:
 * - ALL fields are Optional (no @NotNull, @NotBlank)
 * - Backend validates only what changed
 * - Frontend sends null for unchanged fields
 * - NO familyMembers (use separate Family Member endpoints)
 * - NO attributes (handle separately)
 * - NO barcode (immutable - generated at creation)
 * 
 * This prevents VALIDATION_ERROR (400) when updating member
 */
@Schema(description = "DTO for updating an existing member - all fields optional")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberUpdateDto {

    // Personal Information
    @Schema(description = "Full name", example = "أحمد محمد علي")
    private String fullName;

    @Schema(description = "National Number (الرقم الوطني)", example = "289123456789")
    private String nationalNumber;

    @Schema(description = "Card number", example = "CARD-12345")
    private String cardNumber;

    @Schema(description = "Birth date", example = "1990-01-15")
    private LocalDate birthDate;

    @Schema(description = "Gender", example = "MALE")
    private Member.Gender gender;

    @Schema(description = "Marital status", example = "MARRIED")
    private Member.MaritalStatus maritalStatus;

    @Schema(description = "Phone number", example = "+96512345678")
    private String phone;

    @Schema(description = "Email address", example = "ahmed@example.com")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(description = "Address", example = "طرابلس، شارع الجمهورية، عمارة 15")
    private String address;

    @Schema(description = "Nationality", example = "Libyan")
    private String nationality;

    // Employment Information
    @Schema(description = "Employer / Organization ID", example = "10")
    private Long employerId;

    // Insurance Information
    @Schema(description = "Policy number", example = "POL-2024-001")
    private String policyNumber;

    // Benefit Policy Assignment
    @Schema(description = "Benefit Policy ID to assign to member", example = "1")
    private Long benefitPolicyId;

    // Employment Information
    @Schema(description = "Employee number", example = "EMP-001")
    private String employeeNumber;

    @Schema(description = "Join date", example = "2024-01-01")
    private LocalDate joinDate;

    @Schema(description = "Occupation", example = "Software Engineer")
    private String occupation;

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

    @Schema(description = "Notes", example = "VIP member")
    private String notes;

    @Schema(description = "Active flag", example = "true")
    private Boolean active;

    // ==================== UNIFIED ARCHITECTURE - UPDATE SUPPORT ====================
    
    /**
     * Relationship Type - Can be updated for DEPENDENT members only.
     * 
     * PRINCIPAL members: Must remain NULL
     * DEPENDENT members: Can be changed (e.g., SON → DAUGHTER if correction needed)
     * 
     * NOTE: Changing a PRINCIPAL to DEPENDENT or vice versa is NOT allowed.
     */
    @Schema(description = "Relationship type - for dependents only", example = "DAUGHTER")
    private Member.Relationship relationship;

    // ==================== FORBIDDEN FIELDS (ARCHITECTURAL HARDENING) ====================
    // 
    // ❌ NO parentId - cannot change parent after creation (immutable)
    // ❌ NO barcode - immutable, generated at creation for principals only
    // ❌ NO cardNumber - managed by system (for dependents: auto-generated from parent)
    // ❌ NO dependents - use POST /api/members with parentId to add new dependents
    // ❌ NO familyMembers - deprecated, use unified member creation
    // ❌ NO attributes - handle separately if needed
    //
    // This prevents VALIDATION_ERROR (400) and ensures architectural integrity
}
