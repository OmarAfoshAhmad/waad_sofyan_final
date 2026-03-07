package com.waad.tba.modules.auth.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.modules.auth.dto.ForgotPasswordRequest;
import com.waad.tba.modules.auth.dto.LoginRequest;
import com.waad.tba.modules.auth.dto.LoginResponse;
import com.waad.tba.modules.auth.dto.RegisterRequest;
import com.waad.tba.modules.auth.dto.ResetPasswordRequest;
import com.waad.tba.modules.auth.service.AuthService;
import com.waad.tba.modules.rbac.dto.ChangePasswordDto;
import com.waad.tba.modules.rbac.dto.ForgotPasswordDto;
import com.waad.tba.modules.rbac.dto.ResetPasswordDto;
import com.waad.tba.modules.rbac.dto.VerifyEmailDto;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.rbac.service.UserSecurityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication, registration, and password reset")
public class AuthController {

        private final AuthService authService;
        private final AuthenticationManager authenticationManager;
        private final UserSecurityService securityService;
        private final UserRepository userRepository;
        private final SystemSettingsService systemSettingsService;

        private static final String PASSWORD_RESET_METHOD_OTP = "OTP";
        private static final String PASSWORD_RESET_METHOD_TOKEN = "TOKEN";

        private boolean isOtpFlowEnabled() {
                return PASSWORD_RESET_METHOD_OTP.equals(systemSettingsService.getPasswordResetMethod());
        }

        private boolean isTokenFlowEnabled() {
                return PASSWORD_RESET_METHOD_TOKEN.equals(systemSettingsService.getPasswordResetMethod());
        }

        /**
         * SESSION-BASED LOGIN (Phase A)
         * Authenticates user and creates HTTP session with HttpOnly cookie
         * NO JWT token returned - relies on JSESSIONID cookie
         */
        @PostMapping("/session/login")
        @Operation(summary = "Session-based login", description = "Authenticates user and creates HTTP session. Returns user info without JWT token.")
        public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> sessionLogin(
                        @Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest) {

                // Authenticate user (identifier can be username or email)
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getIdentifier(),
                                                request.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Validate role bindings (PROVIDER must have linked providerId, etc.)
                String username = authentication.getName();
                userRepository.findByUsername(username).ifPresent(authService::validateRoleBindingsBeforeLogin);

                // Create HTTP session
                HttpSession session = httpRequest.getSession(true);

                // Get user info from AuthService (using authenticated username)
                LoginResponse.UserInfo userInfo = authService.getUserInfo(username);

                // AUDIT FIX (TASK A): Store ONLY userId and username in session
                // Do NOT store roles - they will be loaded from DB on each request
                // This ensures role changes take effect immediately without requiring re-login
                session.setAttribute("userId", userInfo.getId());
                session.setAttribute("username", userInfo.getUsername());
                // Note: employerId is used for employer scoping
                session.setAttribute("employerId", userInfo.getEmployerId());

                return ResponseEntity.ok(ApiResponse.success("Login successful", userInfo));
        }

        /**
         * GET CURRENT USER FROM SESSION (Phase A)
         * Returns user info if valid session exists
         */
        @GetMapping("/session/me")
        @Operation(summary = "Get current user from session", description = "Returns the currently authenticated user's profile from HTTP session.")
        public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> getSessionUser(HttpServletRequest request) {
                HttpSession session = request.getSession(false);

                if (session == null || session.getAttribute("userId") == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.<LoginResponse.UserInfo>error("No active session"));
                }

                // AUDIT FIX (TASK A): Fetch current user data from DB (including latest roles)
                // This ensures role changes are reflected immediately without re-login
                String username = (String) session.getAttribute("username");
                LoginResponse.UserInfo userInfo = authService.getUserInfo(username);

                return ResponseEntity.ok(ApiResponse.success(userInfo));
        }

        /**
         * LOGOUT FROM SESSION (Phase A)
         * Invalidates HTTP session
         */
        @PostMapping("/session/logout")
        @Operation(summary = "Logout from session", description = "Invalidates the current HTTP session and clears authentication.")
        public ResponseEntity<ApiResponse<Void>> sessionLogout(HttpServletRequest request) {
                HttpSession session = request.getSession(false);

                if (session != null) {
                        session.invalidate();
                }

                SecurityContextHolder.clearContext();

                return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
        }

        // ========== EXISTING JWT-BASED ENDPOINTS (TEMPORARY - Phase B) ==========

        @PostMapping("/login")
        @Operation(summary = "User login", description = "Authenticates user credentials and returns a JWT token with user information.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class), examples = @io.swagger.v3.oas.annotations.media.ExampleObject(value = "{\n  \"status\": \"error\",\n  \"code\": \"VALIDATION_ERROR\",\n  \"message\": \"Username is required\",\n  \"timestamp\": \"2025-01-01T10:00:00Z\",\n  \"path\": \"/api/auth/login\"\n}"))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class), examples = @io.swagger.v3.oas.annotations.media.ExampleObject(value = "{\n  \"status\": \"error\",\n  \"code\": \"INVALID_CREDENTIALS\",\n  \"message\": \"Invalid username or password\",\n  \"timestamp\": \"2025-01-01T10:00:00Z\",\n  \"path\": \"/api/auth/login\"\n}"))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class), examples = @io.swagger.v3.oas.annotations.media.ExampleObject(value = "{\n  \"status\": \"error\",\n  \"code\": \"INTERNAL_ERROR\",\n  \"message\": \"Unexpected server error\",\n  \"timestamp\": \"2025-01-01T10:00:00Z\",\n  \"path\": \"/api/auth/login\"\n}")))
        })
        public ResponseEntity<ApiResponse<LoginResponse>> login(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Login credentials") @Valid @RequestBody LoginRequest request) {
                LoginResponse response = authService.login(request);
                return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        }

        @PostMapping("/register")
        @Operation(summary = "User registration", description = "Registers a new user and returns JWT token with the created user information.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registration successful"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<ApiResponse<LoginResponse>> register(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Registration details") @Valid @RequestBody RegisterRequest request) {
                LoginResponse response = authService.register(request);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Registration successful", response));
        }

        @GetMapping("/me")
        @Operation(summary = "Get current user", description = "Returns the currently authenticated user's profile information.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User info retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> getCurrentUser(
                        @Parameter(name = "Authorization", description = "Bearer JWT token", required = false) @RequestHeader(value = "Authorization", required = false) String authHeader) {
                // Handle missing or invalid Authorization header
                if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() <= 7) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.<LoginResponse.UserInfo>builder()
                                                        .status("error")
                                                        .message("Authorization header missing or invalid")
                                                        .build());
                }
                String token = authHeader.substring(7); // Remove "Bearer " prefix
                LoginResponse.UserInfo userInfo = authService.getCurrentUser(token);
                return ResponseEntity.ok(ApiResponse.success(userInfo));
        }

        @PostMapping("/forgot-password")
        @Operation(summary = "Request password reset OTP", description = "Sends a one-time password (OTP) to the user's email to verify password reset.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reset OTP sent"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<ApiResponse<Void>> forgotPassword(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request containing the user's email address") @Valid @RequestBody ForgotPasswordRequest request) {
                if (!isOtpFlowEnabled()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<Void>builder()
                                                        .status("error")
                                                        .message("OTP password reset is disabled by system settings")
                                                        .messageAr("تم تعطيل إعادة تعيين كلمة المرور عبر OTP من إعدادات النظام")
                                                        .timestamp(LocalDateTime.now())
                                                        .build());
                }

                authService.sendResetOtp(request.getEmail());
                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("Reset OTP sent to your email")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        @PostMapping("/reset-password")
        @Operation(summary = "Reset password", description = "Resets the user's password after verifying the OTP.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid OTP or request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<ApiResponse<Void>> resetPassword(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request containing email, OTP, and new password") @Valid @RequestBody ResetPasswordRequest request) {
                if (!isOtpFlowEnabled()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<Void>builder()
                                                        .status("error")
                                                        .message("OTP password reset is disabled by system settings")
                                                        .messageAr("تم تعطيل إعادة تعيين كلمة المرور عبر OTP من إعدادات النظام")
                                                        .timestamp(LocalDateTime.now())
                                                        .build());
                }

                authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("Password reset successfully")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        // ========== NEW TOKEN-BASED PASSWORD & EMAIL VERIFICATION ENDPOINTS ==========

        /**
         * Request password reset email (Token-based)
         * 
         * POST /api/auth/token/forgot-password
         * 
         * Sends password reset email with token link. Fails silently if user not found.
         */
        @PostMapping("/token/forgot-password")
        @Operation(summary = "Request password reset (Token-based)", description = "Sends password reset email with secure token. Returns success even if user not found (prevents user enumeration).")
        public ResponseEntity<ApiResponse<Void>> tokenForgotPassword(
                        @Valid @RequestBody ForgotPasswordDto dto,
                        HttpServletRequest request) {

                if (!isTokenFlowEnabled()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<Void>builder()
                                                        .status("error")
                                                        .message("Token password reset is disabled by system settings")
                                                        .messageAr("تم تعطيل إعادة تعيين كلمة المرور عبر الرابط من إعدادات النظام")
                                                        .timestamp(LocalDateTime.now())
                                                        .build());
                }

                securityService.requestPasswordReset(dto, request.getRemoteAddr(), request.getHeader("User-Agent"));

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("If an account exists with this email, a password reset link has been sent")
                                .messageAr("إذا كان هناك حساب مرتبط بهذا البريد، سيتم إرسال رابط إعادة تعيين كلمة المرور")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        /**
         * Reset password using token
         * 
         * POST /api/auth/token/reset-password
         * 
         * Resets user password using token from email. Also unlocks account if locked.
         */
        @PostMapping("/token/reset-password")
        @Operation(summary = "Reset password with token", description = "Resets password using token from email. Token is single-use and expires after 1 hour.")
        public ResponseEntity<ApiResponse<Void>> tokenResetPassword(
                        @Valid @RequestBody ResetPasswordDto dto,
                        HttpServletRequest request) {

                if (!isTokenFlowEnabled()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<Void>builder()
                                                        .status("error")
                                                        .message("Token password reset is disabled by system settings")
                                                        .messageAr("تم تعطيل إعادة تعيين كلمة المرور عبر الرابط من إعدادات النظام")
                                                        .timestamp(LocalDateTime.now())
                                                        .build());
                }

                securityService.resetPassword(dto, request.getRemoteAddr(), request.getHeader("User-Agent"));

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("Password reset successfully. Your account has been unlocked.")
                                .messageAr("تم إعادة تعيين كلمة المرور بنجاح. تم إلغاء قفل حسابك.")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        @GetMapping("/password-reset-config")
        @Operation(summary = "Get password reset config (public)", description = "Returns active password reset mode and related parameters for auth screens.")
        public ResponseEntity<ApiResponse<PasswordResetConfigResponse>> getPasswordResetConfig() {
                PasswordResetConfigResponse config = new PasswordResetConfigResponse(
                                systemSettingsService.getPasswordResetMethod(),
                                systemSettingsService.getPasswordResetTokenExpiryMinutes(),
                                systemSettingsService.getPasswordResetOtpExpiryMinutes(),
                                systemSettingsService.getPasswordResetOtpLength());

                return ResponseEntity.ok(ApiResponse.success(config));
        }

        /**
         * Verify email address
         * 
         * POST /api/auth/verify-email
         * 
         * Verifies user's email using token from email.
         */
        @PostMapping("/verify-email")
        @Operation(summary = "Verify email address", description = "Verifies user email using token. Token is single-use and expires after 24 hours.")
        public ResponseEntity<ApiResponse<Void>> verifyEmail(
                        @Valid @RequestBody VerifyEmailDto dto,
                        HttpServletRequest request) {

                securityService.verifyEmail(dto, request.getRemoteAddr(), request.getHeader("User-Agent"));

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("Email verified successfully")
                                .messageAr("تم التحقق من البريد الإلكتروني بنجاح")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        /**
         * Resend email verification
         * 
         * POST /api/auth/resend-verification
         * 
         * Invalidates old tokens and sends new verification email.
         */
        @PostMapping("/resend-verification")
        @Operation(summary = "Resend email verification", description = "Sends new verification email, invalidating previous tokens.")
        public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ForgotPasswordDto dto) {
                securityService.resendEmailVerification(dto.getEmail());

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("Verification email sent successfully")
                                .messageAr("تم إرسال بريد التحقق بنجاح")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        /**
         * Refresh JWT Token with updated permissions
         * 
         * POST /api/v1/auth/refresh-token
         * 
         * Allows authenticated user to get a new JWT token with updated
         * roles/permissions
         * without needing to logout and login again.
         * 
         * USE CASE:
         * - After admin assigns new roles/permissions to user
         * - User can call this endpoint to refresh their token immediately
         * - No need to logout/login to see updated permissions
         */
        @PostMapping("/refresh-token")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Refresh JWT token with updated permissions", description = "Generates new JWT token with current user's updated roles and permissions")
        public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
                        @AuthenticationPrincipal UserDetails userDetails) {

                log.info("🔄 Refreshing JWT token for user: {}", userDetails.getUsername());

                // Get fresh user data with updated roles/permissions from database
                LoginResponse refreshedToken = authService.refreshUserToken(userDetails.getUsername());

                return ResponseEntity.ok(ApiResponse.success(
                                "Token refreshed successfully with updated permissions",
                                refreshedToken));
        }

        /**
         * Change current user's password
         * 
         * PUT /api/users/me/password
         * 
         * Allows authenticated user to change password. Requires current password.
         */
        @PutMapping("/users/me/password")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Change password", description = "Changes authenticated user's password. Requires current password verification.")
        public ResponseEntity<ApiResponse<Void>> changePassword(
                        @Valid @RequestBody ChangePasswordDto dto,
                        @AuthenticationPrincipal UserDetails userDetails) {

                securityService.changePassword(userDetails.getUsername(), dto.getCurrentPassword(),
                                dto.getNewPassword());

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .status("success")
                                .message("Password changed successfully")
                                .messageAr("تم تغيير كلمة المرور بنجاح")
                                .timestamp(LocalDateTime.now())
                                .build());
        }

        public record PasswordResetConfigResponse(
                        String method,
                        int tokenExpiryMinutes,
                        int otpExpiryMinutes,
                        int otpLength) {
        }
}
