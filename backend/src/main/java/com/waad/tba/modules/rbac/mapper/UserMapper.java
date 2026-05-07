package com.waad.tba.modules.rbac.mapper;

import com.waad.tba.modules.rbac.dto.*;
import com.waad.tba.modules.rbac.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDto toResponseDto(User user) {
        if (user == null) return null;
        
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .active(user.getActive())
                .role(user.getUserType() != null ? user.getUserType() : "DATA_ENTRY")
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public User toEntity(UserCreateDto dto) {
        if (dto == null) return null;
        
        return User.builder()
                .username(dto.getUsername())
                .password(dto.getPassword()) // Will be encoded by service
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .active(true)
                .emailVerified(true)
                // Employer/Provider associations (2026-01-16)
                .employerId(dto.getEmployerId())
                .providerId(dto.getProviderId())
                // Custom permissions for EMPLOYER users
                .canViewClaims(dto.getCanViewClaims() != null ? dto.getCanViewClaims() : true)
                .canViewVisits(dto.getCanViewVisits() != null ? dto.getCanViewVisits() : true)
                .canViewReports(dto.getCanViewReports() != null ? dto.getCanViewReports() : true)
                .canViewMembers(dto.getCanViewMembers() != null ? dto.getCanViewMembers() : true)
                .canViewBenefitPolicies(dto.getCanViewBenefitPolicies() != null ? dto.getCanViewBenefitPolicies() : true)
                .build();
    }

    public void updateEntityFromDto(User user, UserUpdateDto dto) {
        if (dto == null) return;
        
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        if (dto.getActive() != null) {
            user.setActive(dto.getActive());
        }
        // Employer/Provider associations (2026-01-16)
        if (dto.getEmployerId() != null) {
            user.setEmployerId(dto.getEmployerId());
        }
        if (dto.getProviderId() != null) {
            user.setProviderId(dto.getProviderId());
        }
    }
}
