package com.waad.tba.modules.member.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MemberImportRollbackResultDto {
    Long rollbackId;
    Long importLogId;
    String status;
    int revertedCreatedCount;
    int revertedUpdatedCount;
    int skippedCount;
    LocalDateTime completedAt;
    String message;
}
