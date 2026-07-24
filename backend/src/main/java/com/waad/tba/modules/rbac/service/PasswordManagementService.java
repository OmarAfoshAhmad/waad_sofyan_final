package com.waad.tba.modules.rbac.service;

import com.waad.tba.common.email.*;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.config.SecurityConfigurationProperties;
import com.waad.tba.modules.auth.service.SessionManagementService;
import com.waad.tba.modules.rbac.dto.*;
import com.waad.tba.modules.rbac.entity.*;
import com.waad.tba.modules.rbac.exception.*;
import com.waad.tba.modules.rbac.repository.*;
import com.waad.tba.security.audit.SecurityAuditEvent;
import com.waad.tba.security.audit.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordManagementService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecurityAuditService securityAuditService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecurityConfigurationProperties config;
    private final SystemSettingsService systemSettingsService;
    private final SessionManagementService sessionManagementService;

    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto, String ipAddress, String userAgent) {
        log.info("Password change requested for user ID: {}", userId);

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            securityAuditService.logSecurityEvent(
                    userId, user.getUsername(),
                    SecurityAuditEvent.AuditActionType.PASSWORD_CHANGED,
                    "USER", userId, user.getUsername(),
                    ipAddress, userAgent,
                    SecurityAuditEvent.AuditResult.DENIED,
                    "Incorrect current password", null, null);
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (dto.getNewPassword().equalsIgnoreCase(user.getUsername())) {
            throw new PasswordPolicyViolationException("Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        passwordResetTokenRepository.invalidateAllUserTokens(userId);

        securityAuditService.logPasswordChanged(userId, user.getUsername(), ipAddress, userAgent);

        sessionManagementService.revokeAll(user.getUsername());
        log.info("Password changed successfully for user ID: {}", userId);
    }

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

    @Transactional
    public void requestPasswordReset(ForgotPasswordDto dto, String ipAddress, String userAgent) {
        log.info("Password reset requested for email: {}", dto.getEmail());

        User user = userRepository.findByEmail(dto.getEmail()).orElse(null);

        if (user == null) {
            log.warn("Password reset requested for non-existent email: {}", dto.getEmail());
            return;
        }

        passwordResetTokenRepository.invalidateAllUserTokens(user.getId());

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);

        int expiryMinutes = Math.max(5, Math.min(1440, systemSettingsService.getPasswordResetTokenExpiryMinutes()));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(hashedToken)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        String resetUrl = config.getFrontend().getUrl() + "/auth/reset-password?token=" + rawToken;
        PasswordResetData emailData = new PasswordResetData(
                user.getEmail(),
                user.getFullName(),
                rawToken,
                resetUrl);
        emailService.sendPasswordReset(emailData);

        securityAuditService.logSecurityEvent(
                user.getId(), user.getUsername(),
                SecurityAuditEvent.AuditActionType.PASSWORD_RESET_REQUESTED,
                "USER", user.getId(), user.getUsername(),
                ipAddress, userAgent,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Reset token generated", null, null);

        log.info("Password reset email sent to: {}", dto.getEmail());
    }

    @Transactional
    public void resetPassword(ResetPasswordDto dto, String ipAddress, String userAgent) {
        log.info("Password reset with token: {}", dto.getToken().substring(0, 8) + "...");

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String hashedToken = hashToken(dto.getToken());
        PasswordResetToken token = passwordResetTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token", "TOKEN_NOT_FOUND"));

        if (!token.isValid()) {
            throw new InvalidResetTokenException("Reset token has expired or already been used", dto.getToken());
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.getNewPassword().equalsIgnoreCase(user.getUsername())) {
            throw new PasswordPolicyViolationException("Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.unlockAccount();
        userRepository.save(user);
        sessionManagementService.revokeAll(user.getUsername());

        token.markAsUsed();
        passwordResetTokenRepository.save(token);

        securityAuditService.logSecurityEvent(
                user.getId(), user.getUsername(),
                SecurityAuditEvent.AuditActionType.PASSWORD_RESET,
                "USER", user.getId(), user.getUsername(),
                ipAddress, userAgent,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Password reset via token", null, null);

        log.info("Password reset successfully for user ID: {}", user.getId());
    }

    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedResetTokens = passwordResetTokenRepository.deleteExpiredOrUsedTokens(now);
        log.info("Cleanup: Deleted {} password reset tokens", deletedResetTokens);
    }

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
