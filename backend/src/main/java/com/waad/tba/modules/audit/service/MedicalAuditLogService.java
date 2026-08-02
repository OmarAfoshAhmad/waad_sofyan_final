package com.waad.tba.modules.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.repository.MedicalAuditLogRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    @Transactional(readOnly = true)
    public Page<AuditLog> searchAuditLogs(
            EntityType entityType,
            String entityId,
            AuditAction action,
            AuditSource source,
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable) {
        return searchAuditLogs(entityType, entityId, null, action, source, correlationId, fromInclusive, toExclusive, pageable);
    }

    /**
     * Same as {@link #searchAuditLogs(EntityType, String, AuditAction, AuditSource, String, Instant, Instant, Pageable)}
     * with an additional entityId allow-list, used to filter by facility/employer without a schema
     * migration: the caller resolves which entity IDs belong to a given provider/employer (e.g. all
     * claim IDs for a provider) and passes them here.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> searchAuditLogs(
            EntityType entityType,
            String entityId,
            java.util.Collection<String> entityIdIn,
            AuditAction action,
            AuditSource source,
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable) {
        return searchAuditLogs(entityType, entityId, entityIdIn, null, action, source, correlationId, fromInclusive,
                toExclusive, pageable);
    }

    /**
     * Fullest overload: also accepts a caller-built {@code facilityFilter} Specification, used to
     * scope results to a provider/employer whose records span multiple {@link EntityType}s (e.g.
     * a provider's CLAIM ids OR VISIT ids OR PREAUTHORIZATION ids) — a single {@code entityIdIn}
     * allow-list can't express that OR-across-types shape safely, since numeric IDs from different
     * entity types can collide.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> searchAuditLogs(
            EntityType entityType,
            String entityId,
            java.util.Collection<String> entityIdIn,
            Specification<AuditLog> facilityFilter,
            AuditAction action,
            AuditSource source,
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable) {

        String normalizedEntityId = normalizeBlank(entityId);
        String normalizedCorrelation = normalizeBlank(correlationId);

        Specification<AuditLog> spec = Specification.where(null);

        if (entityType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (normalizedEntityId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), normalizedEntityId));
        }
        if (entityIdIn != null) {
            // Empty collection means "no matching records for this provider/employer" — force a
            // no-result predicate rather than silently ignoring the filter.
            spec = spec.and((root, query, cb) -> entityIdIn.isEmpty() ? cb.disjunction() : root.get("entityId").in(entityIdIn));
        }
        if (facilityFilter != null) {
            spec = spec.and(facilityFilter);
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (source != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        if (normalizedCorrelation != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("correlationId"), normalizedCorrelation));
        }
        if (fromInclusive != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), fromInclusive));
        }
        if (toExclusive != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("timestamp"), toExclusive));
        }

        return repository.findAll(spec, pageable);
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

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
            if (trimmed.isEmpty()) return null;
            // If already a valid JSON object, array, or quoted string — pass through
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))
                    || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                return trimmed;
            }
            // Plain text — wrap as a JSON string value to satisfy json column constraint
            try {
                return objectMapper.writeValueAsString(trimmed);
            } catch (JsonProcessingException ex) {
                // Last resort: manual escaping
                return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "'") + "\"";
            }
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize audit state payload", ex);
            throw new IllegalArgumentException("Failed to serialize audit state payload", ex);
        }
    }
}
