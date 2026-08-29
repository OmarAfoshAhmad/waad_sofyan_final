package com.waad.tba.modules.member.dto;

import com.waad.tba.modules.member.entity.MemberImportLog;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * What the import-history screen reads, and nothing else.
 *
 * The endpoint used to return the entity itself, which meant every column
 * ever added to member_import_logs -- file hashes, the import scope hash,
 * internal ids -- travelled to the browser, and the screen's contract was
 * whatever the table happened to look like that week.
 *
 * It also had no way to express the one thing the screen most needs to say.
 * {@code interrupted} is not a stored status and must not become one: it is
 * a reading of a stored status against the clock, and the row it describes
 * is a row nothing will ever write to again.
 *
 * @param interrupted the batch is still marked PROCESSING long after any
 *        import could still be running -- meaning the process that owned it
 *        died mid-flight. Its members died with it: executeImport is a
 *        single transaction, so an import that never reached its final
 *        statement committed nothing at all. The row is stale bookkeeping,
 *        not a running job and not a partial write, and the same file can be
 *        uploaded again (the idempotency index only reserves COMPLETED).
 */
public record MemberImportLogSummaryDto(
        Long id,
        String importBatchId,
        String fileName,
        String importedByUsername,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        Integer totalRows,
        Integer createdCount,
        Integer updatedCount,
        Integer skippedCount,
        Integer errorCount,
        String status,
        /** Safe by construction: see MemberExcelImportService.readableFailure. */
        String errorMessage,
        boolean interrupted) {

    public static MemberImportLogSummaryDto from(MemberImportLog log, Duration staleAfter, LocalDateTime now) {
        return new MemberImportLogSummaryDto(
                log.getId(),
                log.getImportBatchId(),
                log.getFileName(),
                log.getImportedByUsername(),
                log.getCreatedAt(),
                log.getStartedAt(),
                log.getTotalRows(),
                log.getCreatedCount(),
                log.getUpdatedCount(),
                log.getSkippedCount(),
                log.getErrorCount(),
                log.getStatus() == null ? null : log.getStatus().name(),
                log.getErrorMessage(),
                isInterrupted(log, staleAfter, now));
    }

    private static boolean isInterrupted(MemberImportLog log, Duration staleAfter, LocalDateTime now) {
        if (log.getStatus() != MemberImportLog.ImportStatus.PROCESSING) {
            return false;
        }
        // A batch with no startedAt is marked PROCESSING by markStarted, which
        // sets both in the same call -- so a missing one means a row written
        // by something that is not this import path. Not claimed as dead.
        LocalDateTime startedAt = log.getStartedAt();
        return startedAt != null && startedAt.plus(staleAfter).isBefore(now);
    }
}
