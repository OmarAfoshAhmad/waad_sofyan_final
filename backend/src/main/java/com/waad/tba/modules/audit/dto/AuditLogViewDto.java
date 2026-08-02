package com.waad.tba.modules.audit.dto;

import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Read-only view of {@link AuditLog} enriched with the facility (provider) and company
 * (employer) names the underlying entity belongs to — resolved at read time, without adding
 * any column to the immutable {@code medical_audit_logs} table.
 */
@Getter
@Builder
public class AuditLogViewDto {
    private final Long id;
    private final EntityType entityType;
    private final String entityId;
    private final AuditAction action;
    private final Long userId;
    private final String role;
    private final Instant timestamp;
    private final String reason;
    private final String beforeState;
    private final String afterState;
    private final String correlationId;
    private final AuditSource source;

    /** Provider/facility name, resolved from the audited entity — null when not applicable. */
    private final String facilityName;
    /** Employer/company name, resolved from the audited entity — null when not applicable. */
    private final String companyName;

    public static AuditLogViewDto of(AuditLog log, String facilityName, String companyName) {
        return AuditLogViewDto.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .userId(log.getUserId())
                .role(log.getRole())
                .timestamp(log.getTimestamp())
                .reason(log.getReason())
                .beforeState(log.getBeforeState())
                .afterState(log.getAfterState())
                .correlationId(log.getCorrelationId())
                .source(log.getSource())
                .facilityName(facilityName)
                .companyName(companyName)
                .build();
    }
}
