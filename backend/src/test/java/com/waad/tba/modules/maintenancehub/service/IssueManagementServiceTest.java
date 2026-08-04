package com.waad.tba.modules.maintenancehub.service;

import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueDetailDto;
import com.waad.tba.modules.maintenancehub.entity.IssueSeverity;
import com.waad.tba.modules.maintenancehub.entity.IssueStatus;
import com.waad.tba.modules.maintenancehub.entity.MaintenanceOperationType;
import com.waad.tba.modules.maintenancehub.entity.SystemIssue;
import com.waad.tba.modules.maintenancehub.repository.MaintenanceOperationRepository;
import com.waad.tba.modules.maintenancehub.repository.SystemIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueManagementServiceTest {

    @Mock
    private SystemIssueRepository issueRepository;
    @Mock
    private MaintenanceOperationRepository operationRepository;

    private IssueManagementService service;

    @BeforeEach
    void setUp() {
        service = new IssueManagementService(issueRepository, operationRepository);
        lenient().when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(operationRepository.findByIssueIdOrderByPerformedAtDesc(any())).thenReturn(List.of());
    }

    private SystemIssue openIssue() {
        return SystemIssue.builder()
                .id(1L)
                .issueType("BACKEND_ERROR")
                .status(IssueStatus.OPEN)
                .severity(IssueSeverity.HIGH)
                .fingerprint("fp-1")
                .titleAr("عنوان")
                .occurrenceCount(1)
                .detectedAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Assigning an OPEN issue to someone moves it to IN_PROGRESS")
    void assignMovesOpenToInProgress() {
        when(issueRepository.findById(1L)).thenReturn(Optional.of(openIssue()));

        IssueDetailDto result = service.assign(1L, "reviewer1", "admin");

        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.assignedTo()).isEqualTo("reviewer1");
        verify(operationRepository).save(argThat(op -> op.getOperationType().equals(MaintenanceOperationType.ISSUE_ASSIGNED)));
    }

    @Test
    @DisplayName("Unassigning clears assignedTo without forcing a status change")
    void unassignClearsAssignee() {
        SystemIssue issue = openIssue();
        issue.setAssignedTo("reviewer1");
        issue.setStatus(IssueStatus.IN_PROGRESS);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));

        IssueDetailDto result = service.assign(1L, null, "admin");

        assertThat(result.assignedTo()).isNull();
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Resolving records resolver, timestamp and note, and logs the operation")
    void resolveSetsResolutionFields() {
        when(issueRepository.findById(1L)).thenReturn(Optional.of(openIssue()));

        IssueDetailDto result = service.resolve(1L, "تم الإصلاح", "admin");

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(result.resolvedBy()).isEqualTo("admin");
        assertThat(result.resolvedAt()).isNotNull();
        assertThat(result.resolutionNote()).isEqualTo("تم الإصلاح");
        verify(operationRepository).save(argThat(op -> op.getOperationType().equals(MaintenanceOperationType.ISSUE_RESOLVED)));
    }

    @Test
    @DisplayName("Ignoring behaves like resolving but records IGNORED")
    void ignoreSetsIgnoredStatus() {
        when(issueRepository.findById(1L)).thenReturn(Optional.of(openIssue()));

        IssueDetailDto result = service.ignore(1L, "غير مهم", "admin");

        assertThat(result.status()).isEqualTo("IGNORED");
        verify(operationRepository).save(argThat(op -> op.getOperationType().equals(MaintenanceOperationType.ISSUE_IGNORED)));
    }

    @Test
    @DisplayName("Operating on an unknown issue id fails clearly instead of silently no-op'ing")
    void unknownIssueThrows() {
        when(issueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(99L, "note", "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("summary() counts open, in-progress, reopened and critical-open issues")
    void summaryAggregatesCounts() {
        when(issueRepository.countByStatus(IssueStatus.OPEN)).thenReturn(5L);
        when(issueRepository.countByStatus(IssueStatus.IN_PROGRESS)).thenReturn(2L);
        when(issueRepository.countByStatus(IssueStatus.REOPENED)).thenReturn(1L);
        when(issueRepository.countByStatusAndSeverity(IssueStatus.OPEN, IssueSeverity.CRITICAL)).thenReturn(1L);
        when(issueRepository.countByStatusAndSeverity(IssueStatus.REOPENED, IssueSeverity.CRITICAL)).thenReturn(0L);

        var result = service.summary();

        assertThat(result.openCount()).isEqualTo(5);
        assertThat(result.inProgressCount()).isEqualTo(2);
        assertThat(result.reopenedCount()).isEqualTo(1);
        assertThat(result.criticalOpenCount()).isEqualTo(1);
        assertThat(result.totalActionable()).isEqualTo(8);
    }

    private static com.waad.tba.modules.maintenancehub.entity.MaintenanceOperation argThat(
            java.util.function.Predicate<com.waad.tba.modules.maintenancehub.entity.MaintenanceOperation> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
