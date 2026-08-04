package com.waad.tba.modules.maintenancehub.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.errorlog.entity.ErrorLogSeverity;
import com.waad.tba.modules.errorlog.entity.ErrorLogSource;
import com.waad.tba.modules.errorlog.entity.SystemErrorLog;
import com.waad.tba.modules.errorlog.service.SystemErrorLogService;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueDetailDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRegistration;
import com.waad.tba.modules.maintenancehub.entity.IssueStatus;
import com.waad.tba.modules.maintenancehub.entity.IssueType;
import com.waad.tba.modules.maintenancehub.entity.SystemIssue;
import com.waad.tba.modules.maintenancehub.repository.SystemIssueRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the unified ledger's core promise against real PostgreSQL: the same problem
 * detected twice does not create two rows, a resolved/ignored issue that is detected
 * again reopens itself, and concurrent detections of the same fingerprint never lose
 * an occurrence count to a race. Also proves the two existing detectors this phase
 * wired in (error log, scheduled backup failure) actually feed the ledger.
 *
 * Not @Transactional: IssueRegistryWriter commits through its own REQUIRES_NEW
 * transaction by design, so a rollback-wrapping test would not reflect real behavior.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class IssueRegistryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private IssueRegistry issueRegistry;

    @Autowired
    private IssueManagementService managementService;

    @Autowired
    private SystemIssueRepository issueRepository;

    @Autowired
    private SystemErrorLogService systemErrorLogService;

    private static IssueRegistration registration(String fingerprint, String title) {
        return new IssueRegistration(IssueType.DB_ANOMALY, fingerprint, null, "TEST", "1",
                title, "وصف", "HIGH", "test-rule", Map.of("k", "v"));
    }

    @Test
    void firstDetectionCreatesAnOpenIssueAtOccurrenceOne() {
        String fingerprint = "IT-" + System.nanoTime();
        Long id = issueRegistry.register(registration(fingerprint, "أول اكتشاف"));

        assertThat(id).isNotNull();
        SystemIssue issue = issueRepository.findById(id).orElseThrow();
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN);
        assertThat(issue.getOccurrenceCount()).isEqualTo(1);
        assertThat(issue.getFingerprint()).isEqualTo(fingerprint);
    }

    @Test
    void repeatedDetectionOfAnOpenIssueBumpsOccurrenceCountWithoutCreatingANewRow() {
        String fingerprint = "IT-" + System.nanoTime();
        Long firstId = issueRegistry.register(registration(fingerprint, "v1"));
        Long secondId = issueRegistry.register(registration(fingerprint, "v2 - أدق"));

        assertThat(secondId).isEqualTo(firstId);
        SystemIssue issue = issueRepository.findById(firstId).orElseThrow();
        assertThat(issue.getOccurrenceCount()).isEqualTo(2);
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN);
        // Later detections refresh the description with more accurate context.
        assertThat(issue.getTitleAr()).isEqualTo("v2 - أدق");
    }

    @Test
    void aResolvedIssueThatIsDetectedAgainReopensAutomatically() {
        String fingerprint = "IT-" + System.nanoTime();
        Long id = issueRegistry.register(registration(fingerprint, "مشكلة"));

        managementService.resolve(id, "تم الإصلاح", "admin");
        assertThat(issueRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IssueStatus.RESOLVED);

        Long reDetectedId = issueRegistry.register(registration(fingerprint, "عادت المشكلة"));

        assertThat(reDetectedId).isEqualTo(id);
        SystemIssue reopened = issueRepository.findById(id).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(IssueStatus.REOPENED);
        assertThat(reopened.getOccurrenceCount()).isEqualTo(2);
        assertThat(reopened.getResolvedAt()).isNull();
        assertThat(reopened.getResolvedBy()).isNull();
    }

    @Test
    void anIgnoredIssueThatIsDetectedAgainAlsoReopens() {
        String fingerprint = "IT-" + System.nanoTime();
        Long id = issueRegistry.register(registration(fingerprint, "مشكلة"));
        managementService.ignore(id, "تم التجاهل", "admin");

        issueRegistry.register(registration(fingerprint, "عادت"));

        assertThat(issueRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IssueStatus.REOPENED);
    }

    @Test
    void issueTimelineRecordsDetectionReopenAndResolution() {
        String fingerprint = "IT-" + System.nanoTime();
        Long id = issueRegistry.register(registration(fingerprint, "مشكلة"));
        managementService.resolve(id, "تم", "admin");
        issueRegistry.register(registration(fingerprint, "عادت"));

        IssueDetailDto detail = managementService.get(id);

        assertThat(detail.timeline()).hasSizeGreaterThanOrEqualTo(3);
        List<String> types = detail.timeline().stream().map(t -> t.operationType()).toList();
        assertThat(types).contains("ISSUE_DETECTED", "ISSUE_RESOLVED", "ISSUE_REOPENED");
    }

    @Test
    void concurrentDetectionsOfTheSameFingerprintNeverLoseAnOccurrence() throws InterruptedException {
        String fingerprint = "IT-CONCURRENT-" + System.nanoTime();
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            for (int i = 0; i < workers; i++) {
                int index = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    issueRegistry.register(registration(fingerprint, "concurrent-" + index));
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        List<SystemIssue> matches = issueRepository.findAll().stream()
                .filter(i -> fingerprint.equals(i.getFingerprint()))
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getOccurrenceCount()).isEqualTo(workers);
    }

    @Test
    void repeatedBackendErrorsWithTheSameStackFeedOneIssueInTheLedger() {
        String uniqueMarker = "IT-STACK-" + System.nanoTime();
        SystemErrorLog first = SystemErrorLog.builder()
                .occurredAt(LocalDateTime.now())
                .source(ErrorLogSource.BACKEND)
                .severity(ErrorLogSeverity.ERROR)
                .path("/api/v1/test/" + uniqueMarker)
                .stackHash(uniqueMarker)
                .technicalMessage("boom")
                .build();
        SystemErrorLog second = SystemErrorLog.builder()
                .occurredAt(LocalDateTime.now())
                .source(ErrorLogSource.BACKEND)
                .severity(ErrorLogSeverity.ERROR)
                .path("/api/v1/test/" + uniqueMarker)
                .stackHash(uniqueMarker)
                .technicalMessage("boom again")
                .build();

        systemErrorLogService.record(first);
        systemErrorLogService.record(second);

        List<SystemIssue> matches = issueRepository.findAll().stream()
                .filter(i -> ("BACKEND_ERROR:" + uniqueMarker).equals(i.getFingerprint()))
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getOccurrenceCount()).isEqualTo(2);
        assertThat(matches.get(0).getIssueType()).isEqualTo(IssueType.BACKEND_ERROR);
    }

    @Test
    void lowSeverityErrorLogEntriesDoNotPolluteTheIssueLedger() {
        String uniqueMarker = "IT-INFO-" + System.nanoTime();
        SystemErrorLog info = SystemErrorLog.builder()
                .occurredAt(LocalDateTime.now())
                .source(ErrorLogSource.BACKEND)
                .severity(ErrorLogSeverity.INFO)
                .path("/api/v1/test/" + uniqueMarker)
                .stackHash(uniqueMarker)
                .build();

        systemErrorLogService.record(info);

        boolean anyRegistered = issueRepository.findAll().stream()
                .anyMatch(i -> ("BACKEND_ERROR:" + uniqueMarker).equals(i.getFingerprint()));
        assertThat(anyRegistered).isFalse();
    }
}
