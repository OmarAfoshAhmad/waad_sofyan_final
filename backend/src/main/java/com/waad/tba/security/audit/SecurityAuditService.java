package com.waad.tba.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SecurityAuditService {

    private final SecurityAuditEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Log a security event with all relevant context.
     * Never logs passwords, tokens, or sensitive data.
     */
    public SecurityAuditEvent logSecurityEvent(
            Long actorId,
            String actorUsername,
            SecurityAuditEvent.AuditActionType actionType,
            String targetType,
            Long targetId,
            String targetIdentifier,
            String requestIp,
            String userAgent,
            SecurityAuditEvent.AuditResult result,
            String safeReason,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {

        try {
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .actorId(actorId)
                    .actorUsername(actorUsername)
                    .actionType(actionType)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetIdentifier(targetIdentifier)
                    .requestIp(requestIp)
                    .userAgent(userAgent)
                    .result(result)
                    .safeReason(safeReason)
                    .beforeState(serializeState(beforeState))
                    .afterState(serializeState(afterState))
                    .correlationId(UUID.randomUUID().toString())
                    .eventTimestamp(LocalDateTime.now())
                    .build();

            SecurityAuditEvent saved = repository.save(event);

            log.info("🔐 [AUDIT] {} by {} on {} ({}): {}",
                    actionType, actorUsername, targetType, result, safeReason);

            return saved;
        } catch (Exception e) {
            log.error("❌ Failed to log security audit event", e);
            return null;
        }
    }

    /**
     * Log login success
     */
    public void logLoginSuccess(Long userId, String username, String ip, String userAgent) {
        logSecurityEvent(
                userId, username,
                SecurityAuditEvent.AuditActionType.LOGIN_SUCCESS,
                "USER", userId, username,
                ip, userAgent,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "User logged in successfully",
                null, null
        );
    }

    /**
     * Log login failure
     */
    public void logLoginFailure(String username, String ip, String userAgent, String reason) {
        logSecurityEvent(
                null, username,
                SecurityAuditEvent.AuditActionType.LOGIN_FAILED,
                "USER", null, username,
                ip, userAgent,
                SecurityAuditEvent.AuditResult.DENIED,
                reason,
                null, null
        );
    }

    /**
     * Log password change
     */
    public void logPasswordChanged(Long userId, String username, String ip, String userAgent) {
        logSecurityEvent(
                userId, username,
                SecurityAuditEvent.AuditActionType.PASSWORD_CHANGED,
                "USER", userId, username,
                ip, userAgent,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Password changed",
                null, null
        );
    }

    /**
     * Log account deactivation
     */
    public void logAccountDeactivated(Long userId, String username, String actorName, String reason) {
        logSecurityEvent(
                null, actorName,
                SecurityAuditEvent.AuditActionType.ACCOUNT_DEACTIVATED,
                "USER", userId, username,
                null, null,
                SecurityAuditEvent.AuditResult.SUCCESS,
                reason,
                null, null
        );
    }

    /**
     * Log file access denied
     */
    public void logFileAccessDenied(Long userId, String username, String filename, String reason) {
        logSecurityEvent(
                userId, username,
                SecurityAuditEvent.AuditActionType.FILE_ACCESS_DENIED,
                "FILE", null, filename,
                null, null,
                SecurityAuditEvent.AuditResult.DENIED,
                reason,
                null, null
        );
    }

    /**
     * Log account lockout after failed attempts
     */
    public void logAccountLocked(Long userId, String username, String ip) {
        logSecurityEvent(
                userId, username,
                SecurityAuditEvent.AuditActionType.ACCOUNT_LOCKED,
                "USER", userId, username,
                ip, null,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Account locked after failed login attempts",
                null, null
        );
    }

    /**
     * Serialize map to JSON string safely, filtering out sensitive data
     */
    private String serializeState(Map<String, Object> state) {
        if (state == null) {
            return null;
        }

        Map<String, Object> filtered = state.entrySet().stream()
                .filter(e -> !isSensitiveField(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));

        try {
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            log.warn("Failed to serialize audit state", e);
            return null;
        }
    }

    private boolean isSensitiveField(String key) {
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("key")
                || lower.contains("credential");
    }
}
