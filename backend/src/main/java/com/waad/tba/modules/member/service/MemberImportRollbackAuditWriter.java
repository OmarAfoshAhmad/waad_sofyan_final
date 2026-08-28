package com.waad.tba.modules.member.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.member.entity.MemberImportRollback;
import com.waad.tba.modules.member.repository.MemberImportRollbackRepository;

import lombok.RequiredArgsConstructor;

/** Independent durable writer for failed rollback attempts. */
@Service
@RequiredArgsConstructor
public class MemberImportRollbackAuditWriter {
    private final MemberImportRollbackRepository rollbackRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeFailure(Long importLogId, String reason, String performedBy,
            LocalDateTime startedAt, String errorMessage) {
        rollbackRepository.saveAndFlush(MemberImportRollback.builder()
                .importLogId(importLogId)
                .reason(reason)
                .performedBy(performedBy != null ? performedBy : "system")
                .status(MemberImportRollback.Status.FAILED)
                .errorMessage(errorMessage)
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build());
    }
}
