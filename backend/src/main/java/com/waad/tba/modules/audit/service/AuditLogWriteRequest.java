package com.waad.tba.modules.audit.service;

import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuditLogWriteRequest {
    EntityType entityType;
    String entityId;
    AuditAction action;
    String reason;
    Object beforeState;
    Object afterState;
    String correlationId;
    AuditSource source;
    Integer version;
}
