package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.entity.MemberImportError;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.entity.MemberImportLog.ImportStatus;
import com.waad.tba.modules.member.repository.MemberImportErrorRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the parts of the import audit trail that must survive
 * independently of whether the member-writing transaction in
 * {@link MemberExcelImportService#executeImport} ultimately commits or rolls
 * back.
 *
 * The "started" marker and any row-level errors are written in their own
 * REQUIRES_NEW transaction (same reasoning as EligibilityAuditWriter for the
 * eligibility-check audit trail: a durable record of "we attempted this, and
 * here's what went wrong on the way" must not vanish just because the
 * attempt itself later failed and rolled back). markFailed is the same --
 * if the whole batch aborts, the reason must still be readable afterward.
 *
 * The COMPLETED finalization is deliberately NOT here -- it happens as the
 * last statement of the SAME transaction as the member writes, in
 * MemberExcelImportService itself, so a successful import and its "done"
 * record commit or roll back together as one atomic outcome. See
 * MemberExcelImportService.executeImport for why: if COMPLETED were written
 * via REQUIRES_NEW too, a race between two imports of the identical file
 * could let both sets of members commit (each transaction already
 * independently succeeded) with only the LOSING request's separate
 * COMPLETED write failing on the unique index -- leaving duplicate members
 * despite the idempotency check.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberImportAuditRecorder {

    private final MemberImportLogRepository importLogRepository;
    private final MemberImportErrorRepository importErrorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long markStarted(String batchId, String fileName, long fileSizeBytes, String fileHash,
            Long employerId, Long userId, String username) {
        MemberImportLog log = importLogRepository.findByImportBatchId(batchId)
                .orElseGet(() -> MemberImportLog.builder().importBatchId(batchId).build());
        log.setFileName(fileName);
        log.setFileSizeBytes(fileSizeBytes);
        log.setFileHash(fileHash);
        log.setEmployerId(employerId);
        log.setImportedByUserId(userId);
        log.setImportedByUsername(username != null ? username : "system");
        log.markStarted();
        log = importLogRepository.saveAndFlush(log);
        return log.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRowError(Long importLogId, int rowNumber, String message, String rowJson) {
        try {
            MemberImportLog log = importLogRepository.getReferenceById(importLogId);
            importErrorRepository.save(MemberImportError.systemError(log, rowNumber, message, rowJson));
        } catch (Exception e) {
            // Never let a diagnostic write break the import itself.
            log.error("[MemberImport][AUDIT_LOG_FAILURE] Failed to record row error for importLogId={}, row={}: {}",
                    importLogId, rowNumber, e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long importLogId, String errorMessage) {
        try {
            MemberImportLog log = importLogRepository.findById(importLogId).orElse(null);
            if (log == null) {
                return;
            }
            log.markFailed(errorMessage);
            importLogRepository.saveAndFlush(log);
        } catch (Exception e) {
            log.error("[MemberImport][AUDIT_LOG_FAILURE] Failed to record import failure for importLogId={}: {}",
                    importLogId, e.getMessage(), e);
        }
    }
}
