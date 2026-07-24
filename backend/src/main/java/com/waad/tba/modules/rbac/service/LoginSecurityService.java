package com.waad.tba.modules.rbac.service;

import com.waad.tba.common.email.*;
import com.waad.tba.config.SecurityConfigurationProperties;
import com.waad.tba.modules.rbac.entity.*;
import com.waad.tba.modules.rbac.exception.*;
import com.waad.tba.modules.rbac.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSecurityService {

    private final UserRepository userRepository;
    private final UserLoginAttemptRepository loginAttemptRepository;
    private final UserAuditLogRepository auditLogRepository;
    private final EmailService emailService;
    private final SecurityConfigurationProperties config;

    @Transactional
    public void recordFailedLogin(String username, String reason, String ipAddress, String userAgent) {
        log.warn("Failed login attempt for username: {}, reason: {}", username, reason);

        User user = userRepository.findByUsername(username).orElse(null);

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
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);

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

    @Transactional
    public void recordFailedLogin(String username) {
        recordFailedLogin(username, "Bad credentials", null, null);
    }

    @Transactional
    public void recordSuccessfulLogin(Long userId, String ipAddress, String userAgent) {
        log.info("Successful login for user ID: {}", userId);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

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

        user.resetFailedLoginCount();
        user.updateLastLogin();
        try {
            userRepository.save(user);
        } catch (Exception ex) {
            log.warn("Skipping successful-login user update due to schema mismatch: {}", ex.getMessage());
        }

        auditLog(userId, UserAuditLog.ACTION_LOGIN_SUCCESS,
                "Successful login", ipAddress, userAgent, userId);
    }

    @Transactional
    public void recordSuccessfulLogin(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            recordSuccessfulLogin(user.getId(), null, null);
        }
    }

    public void checkAccountLocked(User user) {
        if (user.isLocked()) {
            log.warn("Login attempt for locked account: {}", user.getUsername());
            throw new AccountLockedException(user.getUsername(), user.getLockedUntil());
        }
    }

    public void checkEmailVerified(User user) {
        if (config.getSecurity().isRequireEmailVerification() && !user.getEmailVerified()) {
            log.warn("Login attempt for unverified email: {}", user.getEmail());
            throw new EmailNotVerifiedException(user.getEmail());
        }
    }

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
