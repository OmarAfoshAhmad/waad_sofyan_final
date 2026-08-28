package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.dto.PreAuthorizationAuditDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAudit;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAudit.AuditAction;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationAuditRepository;
import com.waad.tba.modules.preauthorization.security.AuthorizedPreAuthScope;
import com.waad.tba.modules.preauthorization.security.PreAuthAccessScopeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing PreAuthorization Audit Trail
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PreAuthorizationAuditService {

    private final PreAuthorizationAuditRepository auditRepository;
    private final PreAuthAccessScopeResolver accessScopeResolver;

    /**
     * Log a CREATE action
     */
    @Transactional
    public void logCreate(Long preAuthId, String referenceNumber, String changedBy, String notes) {
        PreAuthorizationAudit audit = PreAuthorizationAudit.createAudit(
                preAuthId, referenceNumber, changedBy, notes
        );
        auditRepository.save(audit);
        log.info("[AUDIT] CREATE - PreAuth {}, User: {}", referenceNumber, changedBy);
    }

    /**
     * Log a field UPDATE
     */
    @Transactional
    public void logUpdate(
            Long preAuthId, 
            String referenceNumber, 
            String changedBy,
            String fieldName, 
            Object oldValue, 
            Object newValue
    ) {
        // Only log if value actually changed
        if (oldValue == null && newValue == null) {
            return;
        }
        if (oldValue != null && oldValue.equals(newValue)) {
            return;
        }

        PreAuthorizationAudit audit = PreAuthorizationAudit.fieldUpdateAudit(
                preAuthId,
                referenceNumber,
                changedBy,
                fieldName,
                oldValue != null ? oldValue.toString() : null,
                newValue != null ? newValue.toString() : null
        );
        auditRepository.save(audit);
        log.info("[AUDIT] UPDATE - PreAuth {}, Field: {}, User: {}", 
                 referenceNumber, fieldName, changedBy);
    }

    /**
     * Log an APPROVE action
     */
    @Transactional
    public void logApprove(Long preAuthId, String referenceNumber, String changedBy, String notes) {
        PreAuthorizationAudit audit = PreAuthorizationAudit.approveAudit(
                preAuthId, referenceNumber, changedBy, notes
        );
        auditRepository.save(audit);
        log.info("[AUDIT] APPROVE - PreAuth {}, User: {}", referenceNumber, changedBy);
    }

    /**
     * Log a REJECT action
     */
    @Transactional
    public void logReject(Long preAuthId, String referenceNumber, String changedBy, String reason) {
        PreAuthorizationAudit audit = PreAuthorizationAudit.rejectAudit(
                preAuthId, referenceNumber, changedBy, reason
        );
        auditRepository.save(audit);
        log.info("[AUDIT] REJECT - PreAuth {}, User: {}", referenceNumber, changedBy);
    }

    /**
     * Log a CANCEL action
     */
    @Transactional
    public void logCancel(Long preAuthId, String referenceNumber, String changedBy, String reason) {
        PreAuthorizationAudit audit = PreAuthorizationAudit.cancelAudit(
                preAuthId, referenceNumber, changedBy, reason
        );
        auditRepository.save(audit);
        log.info("[AUDIT] CANCEL - PreAuth {}, User: {}", referenceNumber, changedBy);
    }

    /**
     * Log a DELETE action
     */
    @Transactional
    public void logDelete(Long preAuthId, String referenceNumber, String changedBy) {
        PreAuthorizationAudit audit = PreAuthorizationAudit.deleteAudit(
                preAuthId, referenceNumber, changedBy
        );
        auditRepository.save(audit);
        log.info("[AUDIT] DELETE - PreAuth {}, User: {}", referenceNumber, changedBy);
    }

    /**
     * Get audit history for a specific PreAuthorization
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationAuditDto> getAuditHistory(Long preAuthId, Pageable pageable) {
        log.debug("[AUDIT] Fetching history for PreAuth ID: {}", preAuthId);
        return auditRepository.findByPreAuthorizationIdOrderByChangeDateDesc(preAuthId, pageable)
                .map(PreAuthorizationAuditDto::fromEntity);
    }

    /**
     * Get full audit history for a specific PreAuthorization (non-paginated)
     */
    @Transactional(readOnly = true)
    public List<PreAuthorizationAuditDto> getFullAuditHistory(Long preAuthId) {
        log.debug("[AUDIT] Fetching full history for PreAuth ID: {}", preAuthId);
        return auditRepository.findByPreAuthorizationIdOrderByChangeDateDesc(preAuthId)
                .stream()
                .map(PreAuthorizationAuditDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get audit records by user
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationAuditDto> getAuditsByUser(String username, Pageable pageable) {
        log.debug("[AUDIT] Fetching audits for user: {}", username);
        AuthorizedPreAuthScope scope = accessScopeResolver.requireViewScope();
        return auditRepository.findByChangedByScoped(username, scopeKind(scope), scopeIds(scope), pageable)
                .map(PreAuthorizationAuditDto::fromEntity);
    }

    /**
     * Get audit records by action type
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationAuditDto> getAuditsByAction(String action, Pageable pageable) {
        log.debug("[AUDIT] Fetching audits for action: {}", action);
        AuditAction auditAction = AuditAction.valueOf(action);
        AuthorizedPreAuthScope scope = accessScopeResolver.requireViewScope();
        return auditRepository.findByActionScoped(auditAction, scopeKind(scope), scopeIds(scope), pageable)
                .map(PreAuthorizationAuditDto::fromEntity);
    }

    /**
     * Get recent audit records (last N days)
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationAuditDto> getRecentAudits(int days, Pageable pageable) {
        log.debug("[AUDIT] Fetching audits from last {} days", days);
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);
        AuthorizedPreAuthScope scope = accessScopeResolver.requireViewScope();
        return auditRepository.findRecentAuditsScoped(sinceDate, scopeKind(scope), scopeIds(scope), pageable)
                .map(PreAuthorizationAuditDto::fromEntity);
    }

    /**
     * Search audit records
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationAuditDto> searchAudits(String query, Pageable pageable) {
        log.debug("[AUDIT] Searching audits with query: {}", query);
        AuthorizedPreAuthScope scope = accessScopeResolver.requireViewScope();
        return auditRepository.searchScoped(query, scopeKind(scope), scopeIds(scope), pageable)
                .map(PreAuthorizationAuditDto::fromEntity);
    }

    /**
     * Get audit statistics
     */
    @Transactional(readOnly = true)
    public AuditStatistics getStatistics() {
        AuthorizedPreAuthScope scope = accessScopeResolver.requireViewScope();
        return AuditStatistics.builder()
                .totalAudits(auditRepository.countScoped(null, scopeKind(scope), scopeIds(scope)))
                .createCount(auditRepository.countScoped(AuditAction.CREATE, scopeKind(scope), scopeIds(scope)))
                .updateCount(auditRepository.countScoped(AuditAction.UPDATE, scopeKind(scope), scopeIds(scope)))
                .approveCount(auditRepository.countScoped(AuditAction.APPROVE, scopeKind(scope), scopeIds(scope)))
                .rejectCount(auditRepository.countScoped(AuditAction.REJECT, scopeKind(scope), scopeIds(scope)))
                .cancelCount(auditRepository.countScoped(AuditAction.CANCEL, scopeKind(scope), scopeIds(scope)))
                .deleteCount(auditRepository.countScoped(AuditAction.DELETE, scopeKind(scope), scopeIds(scope)))
                .build();
    }

    private String scopeKind(AuthorizedPreAuthScope scope) {
        return scope.kind().name();
    }

    private Collection<Long> scopeIds(AuthorizedPreAuthScope scope) {
        return scope.ids().isEmpty() ? Set.of(-1L) : scope.ids();
    }

    /**
     * Audit Statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditStatistics {
        private long totalAudits;
        private long createCount;
        private long updateCount;
        private long approveCount;
        private long rejectCount;
        private long cancelCount;
        private long deleteCount;
    }
}
