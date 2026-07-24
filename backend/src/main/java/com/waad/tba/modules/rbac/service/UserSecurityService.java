package com.waad.tba.modules.rbac.service;

import com.waad.tba.modules.rbac.dto.*;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.audit.SecurityAuditEvent;
import com.waad.tba.security.audit.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Security Service - Facade
 *
 * Delegates to specialized security services:
 * - PasswordManagementService: password change, reset, cleanup
 * - EmailVerificationService: email verification operations
 * - LoginSecurityService: login attempts, account lockout
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSecurityService {

    private final PasswordManagementService passwordManagementService;
    private final EmailVerificationService emailVerificationService;
    private final LoginSecurityService loginSecurityService;
    private final SecurityAuditService securityAuditService;
    private final UserRepository userRepository;

    // =====================================================
    // PASSWORD MANAGEMENT
    // =====================================================

    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto, String ipAddress, String userAgent) {
        passwordManagementService.changePassword(userId, dto, ipAddress, userAgent);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        passwordManagementService.changePassword(username, currentPassword, newPassword);
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordDto dto, String ipAddress, String userAgent) {
        passwordManagementService.requestPasswordReset(dto, ipAddress, userAgent);
    }

    @Transactional
    public void resetPassword(ResetPasswordDto dto, String ipAddress, String userAgent) {
        passwordManagementService.resetPassword(dto, ipAddress, userAgent);
    }

    // =====================================================
    // EMAIL VERIFICATION
    // =====================================================

    @Transactional
    public void sendEmailVerification(User user) {
        emailVerificationService.sendEmailVerification(user);
    }

    @Transactional
    public void verifyEmail(VerifyEmailDto dto, String ipAddress, String userAgent) {
        emailVerificationService.verifyEmail(dto, ipAddress, userAgent);
    }

    @Transactional
    public void resendEmailVerification(Long userId) {
        emailVerificationService.resendEmailVerification(userId);
    }

    @Transactional
    public void resendEmailVerification(String email) {
        emailVerificationService.resendEmailVerification(email);
    }

    // =====================================================
    // ACCOUNT LOCKOUT
    // =====================================================

    @Transactional
    public void recordFailedLogin(String username, String reason, String ipAddress, String userAgent) {
        loginSecurityService.recordFailedLogin(username, reason, ipAddress, userAgent);
    }

    @Transactional
    public void recordFailedLogin(String username) {
        loginSecurityService.recordFailedLogin(username);
    }

    @Transactional
    public void recordSuccessfulLogin(Long userId, String ipAddress, String userAgent) {
        loginSecurityService.recordSuccessfulLogin(userId, ipAddress, userAgent);
    }

    @Transactional
    public void recordSuccessfulLogin(String username) {
        loginSecurityService.recordSuccessfulLogin(username);
    }

    public void checkAccountLocked(User user) {
        loginSecurityService.checkAccountLocked(user);
    }

    public void checkEmailVerified(User user) {
        loginSecurityService.checkEmailVerified(user);
    }

    // =====================================================
    // CLEANUP & MAINTENANCE
    // =====================================================

    @Transactional
    public void cleanupExpiredTokens() {
        passwordManagementService.cleanupExpiredTokens();
        emailVerificationService.cleanupExpiredTokens();
    }

    // =====================================================
    // AUDIT LOGGING (backward compatibility)
    // =====================================================

    @Transactional
    public void auditLog(Long userId, SecurityAuditEvent.AuditActionType action, String details,
            String ipAddress, String userAgent) {
        String targetUsername = userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("user#" + userId);

        securityAuditService.logUserAdminEvent(action, userId, targetUsername, details, ipAddress, userAgent);
    }
}
