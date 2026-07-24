package com.waad.tba.modules.rbac.service;

import com.waad.tba.common.email.*;
import com.waad.tba.config.SecurityConfigurationProperties;
import com.waad.tba.modules.rbac.dto.*;
import com.waad.tba.modules.rbac.entity.*;
import com.waad.tba.modules.rbac.exception.*;
import com.waad.tba.modules.rbac.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserAuditLogRepository auditLogRepository;
    private final EmailService emailService;
    private final SecurityConfigurationProperties config;

    @Transactional
    public void sendEmailVerification(User user) {
        log.info("Sending email verification for user: {}", user.getEmail());

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

        String verificationUrl = config.getFrontend().getUrl() + "/auth/verify-email?token=" + rawToken;
        EmailVerificationData emailData = new EmailVerificationData(
                user.getEmail(),
                user.getFullName(),
                rawToken,
                verificationUrl);
        emailService.sendEmailVerification(emailData);

        log.info("Email verification sent to: {}", user.getEmail());
    }

    @Transactional
    public void verifyEmail(VerifyEmailDto dto, String ipAddress, String userAgent) {
        log.info("Email verification triggered");

        String hashedToken = hashToken(dto.getToken());
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(hashedToken)
                .orElseThrow(
                        () -> new InvalidResetTokenException("Invalid or expired verification token", "TOKEN_NOT_FOUND"));

        if (!token.isValid()) {
            throw new InvalidResetTokenException("Verification token has expired or already been used", dto.getToken());
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);

        token.markAsVerified();
        emailVerificationTokenRepository.save(token);

        auditLog(user.getId(), UserAuditLog.ACTION_EMAIL_VERIFIED,
                "Success: Email verified", ipAddress, userAgent, null);

        log.info("Email verified successfully for user: {}", user.getEmail());
    }

    @Transactional
    public void resendEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getEmailVerified()) {
            throw new IllegalArgumentException("Email already verified");
        }

        emailVerificationTokenRepository.markAllUserTokensAsVerified(userId);
        sendEmailVerification(user);
    }

    @Transactional
    public void resendEmailVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        resendEmailVerification(user.getId());
    }

    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedVerificationTokens = emailVerificationTokenRepository.deleteExpiredOrVerifiedTokens(now);
        log.info("Cleanup: Deleted {} email verification tokens", deletedVerificationTokens);
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

    private void auditLog(Long userId, String action, String details,
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
}
