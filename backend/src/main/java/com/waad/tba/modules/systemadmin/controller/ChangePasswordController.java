package com.waad.tba.modules.systemadmin.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systemadmin.dto.ChangePasswordRequest;
import com.waad.tba.modules.systemadmin.dto.ProfileUpdateRequest;
import com.waad.tba.modules.systemadmin.service.UserPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for user profile operations
 * Self-service only - users manage their own account
 * 
 * Endpoint: POST /api/profile/change-password
 * Authentication: JWT required (any authenticated user)
 * 
 * NO @PreAuthorize - all authenticated users can change their password
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ChangePasswordController {

    private final UserPasswordService userPasswordService;

    /**
     * Change password for the currently authenticated user
     * 
     * @param request ChangePasswordRequest with currentPassword, newPassword, confirmPassword
     * @param authentication Spring Security Authentication object
     * @return Success message or error
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        log.info("Change password request received for user: {}", authentication.getName());
        
        // Validate password confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("كلمتا المرور غير متطابقتين");
        }

        // Change password
        userPasswordService.changePassword(
                authentication.getName(),
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(
                ApiResponse.success("تم تغيير كلمة المرور بنجاح", null)
        );
    }

    /**
     * Update profile for the currently authenticated user.
     *
     * PUT /api/v1/profile/me
     * Authentication: JWT required (any authenticated user)
     *
     * Only provided (non-null) fields are updated.
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication
    ) {
        log.info("Profile update request received for user: {}", authentication.getName());

        userPasswordService.updateProfile(
                authentication.getName(),
                request.getFullName(),
                request.getEmail(),
                request.getPhone()
        );

        return ResponseEntity.ok(
                ApiResponse.success("تم تحديث الملف الشخصي بنجاح", null)
        );
    }
}
