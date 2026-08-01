package com.waad.tba.modules.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Boolean active;
    private String role;
    private Long employerId;
    private Long providerId;
    private String providerName;
    private Boolean canViewClaims;
    private Boolean canViewVisits;
    private Boolean canViewReports;
    private Boolean canViewMembers;
    private Boolean canViewBenefitPolicies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
