package com.waad.tba.modules.member.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Failure-tolerant facade; audit failure never hides the original rollback error. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberImportRollbackAuditRecorder {
    private final MemberImportRollbackAuditWriter writer;

    public boolean recordFailure(Long importLogId, String reason, String performedBy,
            LocalDateTime startedAt, String errorMessage) {
        try {
            writer.writeFailure(importLogId, reason, performedBy, startedAt, errorMessage);
            return true;
        } catch (Exception auditError) {
            log.error("[MemberImportRollback][AUDIT_LOG_FAILURE] importLogId={}: {}",
                    importLogId, auditError.getMessage(), auditError);
            return false;
        }
    }
}
