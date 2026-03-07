package com.waad.tba.modules.systemadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO for Creating a New User (SUPER_ADMIN only)
 * Phase 2 - System Administration - User Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateDto {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 200, message = "Full name must not exceed 200 characters")
    private String fullName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Builder.Default
    private Boolean active = true;

    @NotEmpty(message = "At least one role must be assigned")
    private List<String> roles;

    private List<String> permissions;

    // For EMPLOYER_ADMIN role - must provide employerId
    @Positive(message = "Employer ID must be positive")
    private Long employerId;

    // For PROVIDER role - must provide providerId
    @Positive(message = "Provider ID must be positive")
    private Long providerId;
}
