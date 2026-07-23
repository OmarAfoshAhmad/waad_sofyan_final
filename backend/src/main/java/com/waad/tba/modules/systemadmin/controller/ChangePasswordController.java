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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Controller for user profile operations
 * Self-service only - users manage their own account
 * 
 * Endpoint: POST /api/profile/change-password
 * Authentication: server session for web or mobile access token.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
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
            Authentication authentication,
            HttpServletRequest httpRequest
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

        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(
                ApiResponse.success("تم تغيير كلمة المرور بنجاح", null)
        );
    }

    /**
     * Update profile for the currently authenticated user.
     *
     * PUT /api/v1/profile/me
     * Authentication: any authenticated user; updates only the current account.
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
