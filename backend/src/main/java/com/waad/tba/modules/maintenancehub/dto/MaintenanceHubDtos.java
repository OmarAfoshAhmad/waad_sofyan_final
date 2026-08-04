package com.waad.tba.modules.maintenancehub.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MaintenanceHubDtos {
    private MaintenanceHubDtos() {
    }

    /** What a detector submits; the registry decides create vs. bump vs. reopen. */
    public record IssueRegistration(
            String issueType,
            String fingerprint,
            Long employerId,
            String entityType,
            String entityId,
            String titleAr,
            String descriptionAr,
            String severity,
            String detectedByRule,
            Map<String, Object> details
    ) {
    }

    public record IssueRowDto(
            Long id,
            String issueType,
            String status,
            String severity,
            Long employerId,
            String entityType,
            String entityId,
            String titleAr,
            Integer occurrenceCount,
            LocalDateTime detectedAt,
            LocalDateTime lastSeenAt,
            String assignedTo,
            String resolvedBy,
            LocalDateTime resolvedAt
    ) {
    }

    public record IssueDetailDto(
            Long id,
            String issueType,
            String status,
            String severity,
            Long employerId,
            String entityType,
            String entityId,
            String titleAr,
            String descriptionAr,
            String detailsJson,
            String detectedByRule,
            Integer occurrenceCount,
            LocalDateTime detectedAt,
            LocalDateTime lastSeenAt,
            String assignedTo,
            LocalDateTime assignedAt,
            String resolvedBy,
            LocalDateTime resolvedAt,
            String resolutionNote,
            List<OperationRowDto> timeline
    ) {
    }

    public record OperationRowDto(
            Long id,
            String operationType,
            String performedBy,
            LocalDateTime performedAt,
            String detailsAr
    ) {
    }

    public record AssignRequest(String assignee) {
    }

    public record ResolveRequest(String note) {
    }

    public record IgnoreRequest(String note) {
    }

    /** Counts for the sidebar badge and the overview dashboard. */
    public record IssueSummaryDto(
            long openCount,
            long inProgressCount,
            long reopenedCount,
            long criticalOpenCount,
            long totalActionable
    ) {
    }
}
