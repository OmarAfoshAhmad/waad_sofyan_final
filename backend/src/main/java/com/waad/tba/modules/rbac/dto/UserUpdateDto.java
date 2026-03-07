package com.waad.tba.modules.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String phone;
    private Boolean active;

    // Role / user type
    @Pattern(regexp = "^(SUPER_ADMIN|MEDICAL_REVIEWER|ACCOUNTANT|PROVIDER_STAFF|EMPLOYER_ADMIN|DATA_ENTRY|FINANCE_VIEWER)$", message = "نوع المستخدم غير صالح")
    private String userType;

    // Employer/Provider associations
    private Long employerId;
    private Long providerId;

    // Custom permissions for EMPLOYER users
    private Boolean canViewClaims;
    private Boolean canViewVisits;
    private Boolean canViewReports;
    private Boolean canViewMembers;
    private Boolean canViewBenefitPolicies;
}
