package com.waad.tba.modules.member.dto;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/** What a rollback of this batch WOULD do -- no writes. */
@Value
@Builder
public class MemberImportRollbackPreviewDto {
    Long importLogId;
    String batchId;
    boolean alreadyRolledBack;
    int createdCount;
    int updatedCount;
    /** Created members that would be deleted (no financial activity found). */
    int wouldRevertCreatedCount;
    /** Updated members still unchanged since import and safe to restore. */
    int wouldRevertUpdatedCount;
    /** Created members kept because they (or a dependent) have financial activity. */
    int wouldSkipCount;
    List<SkipPreview> skips;

    @Value
    @Builder
    public static class SkipPreview {
        Long memberId;
        String memberName;
        String reason;
    }
}
