package com.waad.tba.modules.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.repository.MedicalAuditLogRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalAuditLogService {

    private static final Long SYSTEM_USER_ID = 0L;
    private static final String SYSTEM_ROLE = "SYSTEM";

    private final MedicalAuditLogRepository repository;
    private final AuthorizationService authorizationService;
    private final CorrelationIdProvider correlationIdProvider;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuditLog record(AuditLogWriteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AuditLogWriteRequest is required");
        }
        if (request.getEntityType() == null) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (request.getEntityId() == null || request.getEntityId().isBlank()) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (request.getAction() == null) {
            throw new IllegalArgumentException("action is required");
        }

        User actor = authorizationService.getCurrentUser();
        Long actorId = actor != null ? actor.getId() : SYSTEM_USER_ID;
        String actorRole = actor != null && actor.getUserType() != null && !actor.getUserType().isBlank()
                ? actor.getUserType()
                : SYSTEM_ROLE;

        String correlationId = request.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = correlationIdProvider.getOrCreate();
        }

        AuditLog auditLog = AuditLog.builder()
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .action(request.getAction())
                .userId(actorId)
                .role(actorRole)
                .reason(request.getReason())
                .beforeState(toJsonOrNull(request.getBeforeState()))
                .afterState(toJsonOrNull(request.getAfterState()))
                .correlationId(correlationId)
                .source(resolveSource(request.getSource(), actor))
                .version(request.getVersion() == null ? 1 : request.getVersion())
                .build();

        return repository.save(auditLog);
    }

    @Transactional
    public void bulkDeleteLogs(List<Long> ids, String password) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("IDs are required for deletion");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required for deletion authorization");
        }

        User actor = authorizationService.getCurrentUser();
        if (actor == null) {
            throw new IllegalStateException("Authentication required");
        }

        log.info("🔐 Attempting bulk delete of {} audit logs by user {}", ids.size(), actor.getEmail());

        if (!passwordEncoder.matches(password, actor.getPassword())) {
            log.error("❌ Audit log deletion failed: Invalid password provided by user {}", actor.getEmail());
            throw new IllegalArgumentException("كلمة المرور غير صحيحة. لا يمكن حذف سجل التدقيق.");
        }

        try {
            int deleted = repository.bulkDeleteByIds(ids);
            log.info("🗑️ Successfully deleted {} audit log entries.", deleted);
        } catch (Exception e) {
            log.error("❌ Database error during audit log deletion: {}", e.getMessage(), e);
            throw new RuntimeException("حدث خطأ أثناء الحذف من قاعدة البيانات: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> searchClaimAuditLogs(Long claimId, String correlationId, Pageable pageable) {
        String normalizedCorrelation = correlationId == null ? null : correlationId.trim();
        if (normalizedCorrelation != null && normalizedCorrelation.isEmpty()) {
            normalizedCorrelation = null;
        }

        String claimEntityId = claimId == null ? null : String.valueOf(claimId);

        if (claimEntityId != null && normalizedCorrelation != null) {
            return repository.findByEntityTypeAndEntityIdAndCorrelationIdOrderByTimestampDesc(
                    EntityType.CLAIM,
                    claimEntityId,
                    normalizedCorrelation,
                    pageable);
        }

        if (claimEntityId != null) {
            return repository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                    EntityType.CLAIM,
                    claimEntityId,
                    pageable);
        }

        if (normalizedCorrelation != null) {
            return repository.findByCorrelationIdOrderByTimestampDesc(normalizedCorrelation, pageable);
        }

        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> searchClaimAuditLogsByDate(
            Long claimId,
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable) {

        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException("fromInclusive and toExclusive are required");
        }
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        String normalizedCorrelation = correlationId == null ? null : correlationId.trim();
        if (normalizedCorrelation != null && normalizedCorrelation.isEmpty()) {
            normalizedCorrelation = null;
        }

        String claimEntityId = claimId == null ? null : String.valueOf(claimId);

        if (claimEntityId != null && normalizedCorrelation != null) {
            return repository.findByEntityTypeAndEntityIdAndCorrelationIdAndTimestampBetweenOrderByTimestampDesc(
                    EntityType.CLAIM,
                    claimEntityId,
                    normalizedCorrelation,
                    fromInclusive,
                    toExclusive,
                    pageable);
        }

        if (claimEntityId != null) {
            return repository.findByEntityTypeAndEntityIdAndTimestampBetweenOrderByTimestampDesc(
                    EntityType.CLAIM,
                    claimEntityId,
                    fromInclusive,
                    toExclusive,
                    pageable);
        }

        if (normalizedCorrelation != null) {
            return repository.findByCorrelationIdAndTimestampBetweenOrderByTimestampDesc(
                    normalizedCorrelation,
                    fromInclusive,
                    toExclusive,
                    pageable);
        }

        return repository.findByTimestampBetweenOrderByTimestampDesc(fromInclusive, toExclusive, pageable);
    }

    private AuditSource resolveSource(AuditSource requestedSource, User actor) {
        if (requestedSource != null) {
            return requestedSource;
        }
        return actor != null ? AuditSource.USER : AuditSource.SYSTEM;
    }

    private String toJsonOrNull(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String value) {
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize audit state payload", ex);
            throw new IllegalArgumentException("Failed to serialize audit state payload", ex);
        }
    }
}
