package com.waad.tba.modules.rbac.service;

import com.waad.tba.common.email.*;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.config.SecurityConfigurationProperties;
import com.waad.tba.modules.rbac.dto.*;
import com.waad.tba.modules.rbac.entity.*;
import com.waad.tba.modules.rbac.exception.*;
import com.waad.tba.modules.rbac.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * User Security Service
 * 
 * Handles all security-related operations:
 * - Password management (change, reset)
 * - Email verification
 * - Account lockout
 * - Audit logging
 * - Login attempt tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSecurityService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserLoginAttemptRepository loginAttemptRepository;
    private final UserAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecurityConfigurationProperties config;
    private final SystemSettingsService systemSettingsService;

    // =====================================================
    // PASSWORD MANAGEMENT
    // =====================================================

    /**
     * Change password for logged-in user
     * 
     * @param userId    Current user ID
     * @param dto       Password change data
     * @param ipAddress Client IP
     * @param userAgent Client user agent
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto, String ipAddress, String userAgent) {
        log.info("Password change requested for user ID: {}", userId);

        // Validate passwords match
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            auditLog(userId, UserAuditLog.ACTION_PASSWORD_CHANGE,
                    "Failed: Incorrect current password", ipAddress, userAgent, userId);
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Check password != username
        if (dto.getNewPassword().equalsIgnoreCase(user.getUsername())) {
            throw new PasswordPolicyViolationException("Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        // Update password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        // Invalidate all password reset tokens
        passwordResetTokenRepository.invalidateAllUserTokens(userId);

        // Audit log
        auditLog(userId, UserAuditLog.ACTION_PASSWORD_CHANGE,
                "Success: Password changed by user", ipAddress, userAgent, userId);

        log.info("Password changed successfully for user ID: {}", userId);
    }

    /**
     * Change password (by username)
     * 
     * @param username        Current username
     * @param currentPassword Current password
     * @param newPassword     New password
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword(currentPassword);
        dto.setNewPassword(newPassword);
        dto.setConfirmPassword(newPassword);

        changePassword(user.getId(), dto, null, null);
    }

    /**
     * Request password reset (forgot password)
     * 
     * @param dto       Forgot password data
     * @param ipAddress Client IP
     * @param userAgent Client user agent
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordDto dto, String ipAddress, String userAgent) {
        log.info("Password reset requested for email: {}", dto.getEmail());

        // Find user by email (fail silently for security)
        User user = userRepository.findByEmail(dto.getEmail()).orElse(null);

        if (user == null) {
            log.warn("Password reset requested for non-existent email: {}", dto.getEmail());
            // Don't reveal that email doesn't exist
            return;
        }

        // Invalidate any existing tokens for this user
        passwordResetTokenRepository.invalidateAllUserTokens(user.getId());

        // Generate new token
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        
        int expiryMinutes = Math.max(5, Math.min(1440, systemSettingsService.getPasswordResetTokenExpiryMinutes()));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .email(user.getEmail()) // Required by entity
                .token(hashedToken)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Send email with RAW token
        String resetUrl = config.getFrontend().getUrl() + "/auth/reset-password?token=" + rawToken;
        PasswordResetData emailData = new PasswordResetData(
                user.getEmail(),
                user.getFullName(),
                rawToken,
                resetUrl);
        emailService.sendPasswordReset(emailData);

        // Audit log
        auditLog(user.getId(), "PASSWORD_RESET_REQUESTED",
                "Reset token generated", ipAddress, userAgent, null);

        log.info("Password reset email sent to: {}", dto.getEmail());
    }

    /**
     * Reset password using token
     * 
     * @param dto       Reset password data
     * @param ipAddress Client IP
     * @param userAgent Client user agent
     */
    @Transactional
    public void resetPassword(ResetPasswordDto dto, String ipAddress, String userAgent) {
        log.info("Password reset with token: {}", dto.getToken().substring(0, 8) + "...");

        // Validate passwords match
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Find token (look up by HASH)
        String hashedToken = hashToken(dto.getToken());
        PasswordResetToken token = passwordResetTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token", "TOKEN_NOT_FOUND"));

        // Validate token
        if (!token.isValid()) {
            throw new InvalidResetTokenException("Reset token has expired or already been used", dto.getToken());
        }

        // Get user
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check password != username
        if (dto.getNewPassword().equalsIgnoreCase(user.getUsername())) {
            throw new PasswordPolicyViolationException("Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        // Update password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.unlockAccount(); // Unlock if locked
        userRepository.save(user);

        // Mark token as used
        token.markAsUsed();
        passwordResetTokenRepository.save(token);

        // Audit log
        auditLog(user.getId(), UserAuditLog.ACTION_PASSWORD_RESET,
                "Success: Password reset via token", ipAddress, userAgent, null);

        log.info("Password reset successfully for user ID: {}", user.getId());
    }

    // =====================================================
    // EMAIL VERIFICATION
    // =====================================================

    /**
     * Generate and send email verification token
     * 
     * @param user User to verify
     */
    @Transactional
    public void sendEmailVerification(User user) {
        log.info("Sending email verification for user: {}", user.getEmail());

        // Generate token
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusHours(config.getSecurity().getEmailVerificationTokenValidityHours());

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .userId(user.getId())
                .token(hashedToken)
                .expiresAt(expiresAt)
                .verified(false)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        // Send email with RAW token
        String verificationUrl = config.getFrontend().getUrl() + "/auth/verify-email?token=" + rawToken;
        EmailVerificationData emailData = new EmailVerificationData(
                user.getEmail(),
                user.getFullName(),
                rawToken,
                verificationUrl);
        emailService.sendEmailVerification(emailData);

        log.info("Email verification sent to: {}", user.getEmail());
    }

    /**
     * Verify email using token
     * 
     * @param dto       Verification data
     * @param ipAddress Client IP
     * @param userAgent Client user agent
     */
    @Transactional
    public void verifyEmail(VerifyEmailDto dto, String ipAddress, String userAgent) {
        log.info("Email verification triggered");

        // Find token (look up by HASH)
        String hashedToken = hashToken(dto.getToken());
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(hashedToken)
                .orElseThrow(
                        () -> new InvalidResetTokenException("Invalid or expired verification token", "TOKEN_NOT_FOUND"));

        // Validate token
        if (!token.isValid()) {
            throw new InvalidResetTokenException("Verification token has expired or already been used", dto.getToken());
        }

        // Get user
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Mark email as verified
        user.setEmailVerified(true);
        userRepository.save(user);

        // Mark token as verified
        token.markAsVerified();
        emailVerificationTokenRepository.save(token);

        // Audit log
        auditLog(user.getId(), UserAuditLog.ACTION_EMAIL_VERIFIED,
                "Success: Email verified", ipAddress, userAgent, null);

        log.info("Email verified successfully for user: {}", user.getEmail());
    }

    /**
     * Resend email verification
     * 
     * @param userId User ID
     */
    @Transactional
    public void resendEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getEmailVerified()) {
            throw new IllegalArgumentException("Email already verified");
        }

        // Invalidate existing tokens
        emailVerificationTokenRepository.markAllUserTokensAsVerified(userId);

        // Send new verification
        sendEmailVerification(user);
    }

    /**
     * Resend email verification (by email)
     * 
     * @param email User email
     */
    @Transactional
    public void resendEmailVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        resendEmailVerification(user.getId());
    }

    // =====================================================
    // ACCOUNT LOCKOUT
    // =====================================================

    /**
     * Record failed login attempt
     * 
     * @param username  Username attempted
     * @param reason    Failure reason
     * @param ipAddress Client IP
     * @param userAgent Client user agent
     */
    @Transactional
    public void recordFailedLogin(String username, String reason, String ipAddress, String userAgent) {
        log.warn("Failed login attempt for username: {}, reason: {}", username, reason);

        User user = userRepository.findByUsername(username).orElse(null);

        // Record attempt
        UserLoginAttempt attempt = UserLoginAttempt.builder()
                .userId(user != null ? user.getId() : null)
                .username(username)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(false)
                .failedReason(reason)
                .build();

        try {
            loginAttemptRepository.save(attempt);
        } catch (Exception ex) {
            log.warn("Skipping failed login attempt persistence due to schema mismatch: {}", ex.getMessage());
        }

        if (user != null) {
            // Increment failed count
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);

            // Lock account if threshold reached (configurable max attempts)
            if (user.getFailedLoginCount() >= config.getSecurity().getMaxFailedLoginAttempts()) {
                int lockoutMinutes = config.getSecurity().getAccountLockoutDurationMinutes();
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));

                try {
                    userRepository.save(user);
                } catch (Exception ex) {
                    log.warn("Skipping account lock persistence due to schema mismatch: {}", ex.getMessage());
                    return;
                }

                sendAccountLockedNotification(user);
                auditLog(user.getId(), UserAuditLog.ACTION_ACCOUNT_LOCKED,
                        String.format("Account locked after %d failed attempts for %d minutes",
                                user.getFailedLoginCount(), lockoutMinutes),
                        ipAddress, userAgent, null);
            } else {
                try {
                    userRepository.save(user);
                } catch (Exception ex) {
                    log.warn("Skipping failed-login counter update due to schema mismatch: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Record failed login (simplified for authentication events)
     * 
     * @param username Username attempted
     */
    @Transactional
    public void recordFailedLogin(String username) {
        recordFailedLogin(username, "Bad credentials", null, null);
    }

    /**
     * Record successful login
     * 
     * @param userId    User ID
     * @param ipAddress Client IP
     * @param userAgent Client user agent
     */
    @Transactional
    public void recordSuccessfulLogin(Long userId, String ipAddress, String userAgent) {
        log.info("Successful login for user ID: {}", userId);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        // Record attempt
        UserLoginAttempt attempt = UserLoginAttempt.builder()
                .userId(userId)
                .username(user.getUsername())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .build();

        try {
            loginAttemptRepository.save(attempt);
        } catch (Exception ex) {
            log.warn("Skipping successful login attempt persistence due to schema mismatch: {}", ex.getMessage());
        }

        // Reset failed count and update last login
        user.resetFailedLoginCount();
        user.updateLastLogin();
        try {
            userRepository.save(user);
        } catch (Exception ex) {
            log.warn("Skipping successful-login user update due to schema mismatch: {}", ex.getMessage());
        }

        // Audit log
        auditLog(userId, UserAuditLog.ACTION_LOGIN_SUCCESS,
                "Successful login", ipAddress, userAgent, userId);
    }

    /**
     * Record successful login (simplified for authentication events)
     * 
     * @param username Username that logged in
     */
    @Transactional
    public void recordSuccessfulLogin(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            recordSuccessfulLogin(user.getId(), null, null);
        }
    }

    /**
     * Check if account is locked
     * 
     * @param user User to check
     * @throws AccountLockedException if account is locked
     */
    public void checkAccountLocked(User user) {
        if (user.isLocked()) {
            log.warn("Login attempt for locked account: {}", user.getUsername());
            throw new AccountLockedException(user.getUsername(), user.getLockedUntil());
        }
    }

    /**
     * Check if email verification is required
     * 
     * @param user User to check
     * @throws EmailNotVerifiedException if email not verified
     */
    public void checkEmailVerified(User user) {
        if (config.getSecurity().isRequireEmailVerification() && !user.getEmailVerified()) {
            log.warn("Login attempt for unverified email: {}", user.getEmail());
            throw new EmailNotVerifiedException(user.getEmail());
        }
    }

    /**
     * Send account locked notification
     */
    private void sendAccountLockedNotification(User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String lockedUntil = user.getLockedUntil().format(formatter);

        AccountLockedData emailData = new AccountLockedData(
                user.getEmail(),
                user.getFullName(),
                lockedUntil,
                user.getFailedLoginCount());

        emailService.sendAccountLocked(emailData);
    }

    // =====================================================
    // AUDIT LOGGING
    // =====================================================

    /**
     * Create audit log entry
     */
    @Transactional
    public void auditLog(Long userId, String action, String details,
            String ipAddress, String userAgent, Long performedBy) {
        UserAuditLog auditLog = UserAuditLog.builder()
                .userId(userId)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .performedBy(performedBy)
                .build();

        try {
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Skipping audit log persistence due to schema mismatch: {}", ex.getMessage());
        }
    }

    // =====================================================
    // CLEANUP & MAINTENANCE
    // =====================================================

    /**
     * Clean up expired tokens (should be run periodically via scheduled job)
     */
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        int deletedResetTokens = passwordResetTokenRepository.deleteExpiredOrUsedTokens(now);
        int deletedVerificationTokens = emailVerificationTokenRepository.deleteExpiredOrVerifiedTokens(now);

        log.info("Cleanup: Deleted {} password reset tokens and {} verification tokens",
                deletedResetTokens, deletedVerificationTokens);
    }

    /**
     * Hashing helper for tokens and code-based identifiers.
     * Uses SHA-256 with Base64 encoding.
     */
    private String hashToken(String token) {
        if (token == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to hash token: SHA-256 algorithm not found", e);
            throw new RuntimeException("Security configuration error: MessageDigest SHA-256 missing");
        }
    }
}
