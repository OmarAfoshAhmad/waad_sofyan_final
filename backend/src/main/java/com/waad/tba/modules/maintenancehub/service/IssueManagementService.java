package com.waad.tba.modules.maintenancehub.service;

import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueDetailDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRowDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueSummaryDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.OperationRowDto;
import com.waad.tba.modules.maintenancehub.entity.IssueSeverity;
import com.waad.tba.modules.maintenancehub.entity.IssueStatus;
import com.waad.tba.modules.maintenancehub.entity.MaintenanceOperation;
import com.waad.tba.modules.maintenancehub.entity.MaintenanceOperationType;
import com.waad.tba.modules.maintenancehub.entity.SystemIssue;
import com.waad.tba.modules.maintenancehub.repository.MaintenanceOperationRepository;
import com.waad.tba.modules.maintenancehub.repository.SystemIssueRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The human-facing half of the maintenance ledger: browsing, assigning, resolving and
 * ignoring issues that {@link IssueRegistry} recorded. Every state change is written
 * to {@code maintenance_operations_log} so an issue's full history stays queryable.
 */
@Service
@RequiredArgsConstructor
public class IssueManagementService {

    private final SystemIssueRepository issueRepository;
    private final MaintenanceOperationRepository operationRepository;

    @Transactional(readOnly = true)
    public Page<IssueRowDto> list(String issueType, IssueStatus status, IssueSeverity severity,
                                  Long employerId, String assignedTo, Pageable pageable) {
        Specification<SystemIssue> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (issueType != null && !issueType.isBlank()) {
                predicates.add(cb.equal(root.get("issueType"), issueType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (employerId != null) {
                predicates.add(cb.equal(root.get("employerId"), employerId));
            }
            if (assignedTo != null && !assignedTo.isBlank()) {
                predicates.add(cb.equal(root.get("assignedTo"), assignedTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return issueRepository.findAll(spec, pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public IssueDetailDto get(Long id) {
        SystemIssue issue = findOrThrow(id);
        List<OperationRowDto> timeline = operationRepository.findByIssueIdOrderByPerformedAtDesc(id).stream()
                .map(this::toOperationRow)
                .toList();
        return toDetail(issue, timeline);
    }

    @Transactional(readOnly = true)
    public IssueSummaryDto summary() {
        long open = issueRepository.countByStatus(IssueStatus.OPEN);
        long inProgress = issueRepository.countByStatus(IssueStatus.IN_PROGRESS);
        long reopened = issueRepository.countByStatus(IssueStatus.REOPENED);
        long criticalOpen = issueRepository.countByStatusAndSeverity(IssueStatus.OPEN, IssueSeverity.CRITICAL)
                + issueRepository.countByStatusAndSeverity(IssueStatus.REOPENED, IssueSeverity.CRITICAL);
        return new IssueSummaryDto(open, inProgress, reopened, criticalOpen, open + inProgress + reopened);
    }

    @Transactional
    public IssueDetailDto assign(Long id, String assignee, String actor) {
        SystemIssue issue = findOrThrow(id);
        issue.setAssignedTo(blankToNull(assignee));
        issue.setAssignedAt(assignee == null || assignee.isBlank() ? null : LocalDateTime.now());
        if (issue.getStatus() == IssueStatus.OPEN && assignee != null && !assignee.isBlank()) {
            issue.setStatus(IssueStatus.IN_PROGRESS);
        }
        issueRepository.save(issue);
        logOperation(id, MaintenanceOperationType.ISSUE_ASSIGNED, actor,
                assignee == null || assignee.isBlank() ? "أُلغي التعيين" : "تم التعيين إلى " + assignee);
        return get(id);
    }

    @Transactional
    public IssueDetailDto resolve(Long id, String note, String actor) {
        SystemIssue issue = findOrThrow(id);
        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(LocalDateTime.now());
        issue.setResolvedBy(actor);
        issue.setResolutionNote(truncate(note, 2000));
        issueRepository.save(issue);
        logOperation(id, MaintenanceOperationType.ISSUE_RESOLVED, actor,
                note == null || note.isBlank() ? "تم الحل" : note);
        return get(id);
    }

    @Transactional
    public IssueDetailDto ignore(Long id, String note, String actor) {
        SystemIssue issue = findOrThrow(id);
        issue.setStatus(IssueStatus.IGNORED);
        issue.setResolvedAt(LocalDateTime.now());
        issue.setResolvedBy(actor);
        issue.setResolutionNote(truncate(note, 2000));
        issueRepository.save(issue);
        logOperation(id, MaintenanceOperationType.ISSUE_IGNORED, actor,
                note == null || note.isBlank() ? "تم التجاهل" : note);
        return get(id);
    }

    private SystemIssue findOrThrow(Long id) {
        return issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("المشكلة المطلوبة غير موجودة."));
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

    private IssueRowDto toRow(SystemIssue i) {
        return new IssueRowDto(
                i.getId(), i.getIssueType(), i.getStatus().name(), i.getSeverity().name(),
                i.getEmployerId(), i.getEntityType(), i.getEntityId(), i.getTitleAr(),
                i.getOccurrenceCount(), i.getDetectedAt(), i.getLastSeenAt(),
                i.getAssignedTo(), i.getResolvedBy(), i.getResolvedAt());
    }

    private IssueDetailDto toDetail(SystemIssue i, List<OperationRowDto> timeline) {
        return new IssueDetailDto(
                i.getId(), i.getIssueType(), i.getStatus().name(), i.getSeverity().name(),
                i.getEmployerId(), i.getEntityType(), i.getEntityId(), i.getTitleAr(), i.getDescriptionAr(),
                i.getDetailsJson(), i.getDetectedByRule(), i.getOccurrenceCount(), i.getDetectedAt(),
                i.getLastSeenAt(), i.getAssignedTo(), i.getAssignedAt(), i.getResolvedBy(), i.getResolvedAt(),
                i.getResolutionNote(), timeline);
    }

    private OperationRowDto toOperationRow(MaintenanceOperation op) {
        return new OperationRowDto(op.getId(), op.getOperationType(), op.getPerformedBy(),
                op.getPerformedAt(), op.getDetailsAr());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
