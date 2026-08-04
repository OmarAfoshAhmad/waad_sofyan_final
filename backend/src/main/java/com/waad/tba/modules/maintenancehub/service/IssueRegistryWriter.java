package com.waad.tba.modules.maintenancehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRegistration;
import com.waad.tba.modules.maintenancehub.entity.IssueSeverity;
import com.waad.tba.modules.maintenancehub.entity.IssueStatus;
import com.waad.tba.modules.maintenancehub.entity.MaintenanceOperation;
import com.waad.tba.modules.maintenancehub.entity.MaintenanceOperationType;
import com.waad.tba.modules.maintenancehub.entity.SystemIssue;
import com.waad.tba.modules.maintenancehub.repository.MaintenanceOperationRepository;
import com.waad.tba.modules.maintenancehub.repository.SystemIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * The transactional write path behind {@link IssueRegistry}, split into its own bean
 * so {@code REQUIRES_NEW} is actually honored — Spring's transaction proxy does not
 * intercept self-invocation, so this cannot live as a same-class method called via
 * {@code this} from {@code IssueRegistry.register()}.
 */
@Service
@RequiredArgsConstructor
class IssueRegistryWriter {

    private static final Set<IssueStatus> CLOSED_STATUSES = Set.of(IssueStatus.RESOLVED, IssueStatus.IGNORED);

    private final SystemIssueRepository issueRepository;
    private final MaintenanceOperationRepository operationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Long register(IssueRegistration registration) {
        if (registration == null || registration.fingerprint() == null || registration.fingerprint().isBlank()) {
            throw new IllegalArgumentException("Issue fingerprint is required");
        }
        LocalDateTime now = LocalDateTime.now();
        var existingIssue = issueRepository.findByFingerprintForUpdate(registration.fingerprint());
        boolean isNew = existingIssue.isEmpty();

        SystemIssue issue = existingIssue
                .map(existing -> updateExisting(existing, registration, now))
                .orElseGet(() -> createNew(registration, now));

        SystemIssue saved = issueRepository.save(issue);

        if (isNew) {
            logOperation(saved.getId(), MaintenanceOperationType.ISSUE_DETECTED, "SYSTEM",
                    "اكتشاف جديد: " + saved.getTitleAr());
        } else if (saved.getStatus() == IssueStatus.REOPENED && saved.getResolvedAt() == null
                && saved.getOccurrenceCount() != null && saved.getOccurrenceCount() > 1) {
            logOperation(saved.getId(), MaintenanceOperationType.ISSUE_REOPENED, "SYSTEM",
                    "عادت المشكلة بعد إغلاقها — إعادة فتح تلقائية عند الاكتشاف رقم " + saved.getOccurrenceCount());
        }
        return saved.getId();
    }

    private SystemIssue createNew(IssueRegistration registration, LocalDateTime now) {
        return SystemIssue.builder()
                .issueType(registration.issueType())
                .status(IssueStatus.OPEN)
                .severity(parseSeverity(registration.severity()))
                .fingerprint(registration.fingerprint())
                .employerId(registration.employerId())
                .entityType(registration.entityType())
                .entityId(registration.entityId())
                .titleAr(truncate(registration.titleAr(), 300))
                .descriptionAr(truncate(registration.descriptionAr(), 2000))
                .detailsJson(toJson(registration.details()))
                .detectedAt(now)
                .detectedByRule(truncate(registration.detectedByRule(), 120))
                .occurrenceCount(1)
                .lastSeenAt(now)
                .build();
    }

    private SystemIssue updateExisting(SystemIssue existing, IssueRegistration registration, LocalDateTime now) {
        existing.setOccurrenceCount((existing.getOccurrenceCount() == null ? 0 : existing.getOccurrenceCount()) + 1);
        existing.setLastSeenAt(now);
        // Refresh descriptive fields — a later detection may carry more accurate
        // context (e.g. an updated remaining-limit figure) than the first one did.
        existing.setSeverity(parseSeverity(registration.severity()));
        existing.setTitleAr(truncate(registration.titleAr(), 300));
        existing.setDescriptionAr(truncate(registration.descriptionAr(), 2000));
        existing.setDetailsJson(toJson(registration.details()));

        if (CLOSED_STATUSES.contains(existing.getStatus())) {
            existing.setStatus(IssueStatus.REOPENED);
            existing.setResolvedAt(null);
            existing.setResolvedBy(null);
            existing.setResolutionNote(null);
        }
        return existing;
    }

    private void logOperation(Long issueId, String operationType, String performedBy, String detailsAr) {
        operationRepository.save(MaintenanceOperation.builder()
                .issueId(issueId)
                .operationType(operationType)
                .performedBy(performedBy)
                .performedAt(LocalDateTime.now())
                .detailsAr(truncate(detailsAr, 2000))
                .build());
    }

    private static IssueSeverity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return IssueSeverity.MEDIUM;
        }
        try {
            return IssueSeverity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IssueSeverity.MEDIUM;
        }
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
