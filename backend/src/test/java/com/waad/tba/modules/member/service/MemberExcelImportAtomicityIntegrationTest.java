package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportResultDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Real-PostgreSQL proof of the member-import atomicity fix in
 * MemberExcelImportService.executeImport -- the pipeline actually wired to
 * the frontend (via /preview + /execute), not MemberExcelTemplateService's
 * direct /import endpoint, which the UI never calls (confirmed by grepping
 * the frontend before touching any code -- see the commit message).
 *
 * Design under test: the whole row loop -- both PRINCIPAL rows (saved
 * immediately per row) and DEPENDENT rows (buffered, saved via saveAll) --
 * runs inside ONE @Transactional method. A row's PARSE/BUSINESS failure is
 * caught and recorded without touching anything already buffered; a
 * PERSISTENCE failure is NOT caught -- it propagates and rolls back every
 * member the whole file's execution has written so far, principals included.
 * The import-log audit trail survives independently via
 * MemberImportAuditRecorder's REQUIRES_NEW writes for the "started" and
 * "failed" markers, while the "completed" marker is written inside the SAME
 * transaction as the members so success and its audit record commit or roll
 * back together.
 *
 * A note on how the technical-failure tests are constructed: this pipeline's
 * own dedup logic (loadExistingMembersForImport's DB preload + the in-loop
 * memberCache) is deliberately thorough -- a literal duplicate card number
 * anywhere in a file is treated as "update this existing row", not an error,
 * and Member's own invariant that barcode always equals card number closes
 * off forcing a real uk_member_barcode/uk_member_card_number collision
 * through ordinary file content in a single request. The two realistic ways
 * such a persistence failure actually happens are (a) two independent
 * concurrent imports racing on data neither has seen yet (proven for real,
 * no mocking, in concurrentUploadsOfCollidingData_...), and (b) any other
 * persistence-layer fault (a dropped connection, a constraint this pipeline
 * doesn't defend against, etc.) -- proven with a spy on MemberRepository
 * that injects one real DataIntegrityViolationException at the exact point
 * a technical failure would surface, while every other repository call in
 * the test still goes through the real Postgres-backed bean untouched.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberExcelImportAtomicityIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberExcelImportService importService;
    @MockitoSpyBean private MemberRepository memberRepository;
    @Autowired private MemberImportLogRepository importLogRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;

    private Employer newEmployer(String suffix) {
        return employerRepository.save(Employer.builder()
                .name("Import Atomicity Co " + suffix).code("IMP-" + suffix).active(true).build());
    }

    private BenefitPolicy newPolicy(Employer employer, String suffix) {
        return benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-IMP-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
    }

    private MockMultipartFile excel(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Members");
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r);
                String[] values = rows.get(r);
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c]);
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "members.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private static String randomSuffix() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 6);
    }

    private static final String[] HEADER = { "full_name", "employer", "national_id", "start_date", "card_number" };

    @Test
    void validFile_principalAndDependentsAllPersist() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        newPolicy(employer, s);

        MockMultipartFile file = excel(List.of(
                HEADER,
                new String[] { "Principal " + s, employer.getName(), "N1" + s, "2026-01-01", "CARD" + s },
                new String[] { "Dependent " + s, employer.getName(), "N2" + s, "2026-01-01", "CARD" + s + "S1" }));

        MemberImportResultDto result = importService.executeImport(file, "batch-valid-" + s, employer.getId(), null, 0, false);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCreatedCount()).isEqualTo(2);
        assertThat(memberRepository.findByCardNumber("CARD" + s)).isPresent();
        assertThat(memberRepository.findByCardNumber("CARD" + s + "S1")).isPresent();

        MemberImportLog log = importLogRepository.findByImportBatchId("batch-valid-" + s).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(MemberImportLog.ImportStatus.COMPLETED);
        assertThat(log.getCreatedCount()).isEqualTo(2);
    }

    /**
     * Covers: a dependent's persistence failing after its principal (and an
     * earlier sibling dependent) were already written this transaction --
     * NONE of them may remain; the result must never claim success after a
     * rollback; a family with several dependents where the last one fails
     * rolls back the whole family, not just the offending row.
     */
    @Test
    void technicalFailureOnLastDependent_rollsBackPrincipalAndAllDependents() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        newPolicy(employer, s);

        doThrow(new DataIntegrityViolationException("simulated unique constraint violation"))
                .when(memberRepository)
                .saveAll(argThat((List<Member> batch) -> batch != null
                        && batch.stream().anyMatch(m -> ("Child 3 " + s).equals(m.getFullName()))));

        MockMultipartFile file = excel(List.of(
                HEADER,
                new String[] { "Family Head " + s, employer.getName(), "FH" + s, "2026-01-01", "FAM" + s },
                new String[] { "Child 1 " + s, employer.getName(), "C1" + s, "2026-01-01", "FAM" + s + "S1" },
                new String[] { "Child 2 " + s, employer.getName(), "C2" + s, "2026-01-01", "FAM" + s + "D1" },
                new String[] { "Child 3 " + s, employer.getName(), "C3" + s, "2026-01-01", "FAM" + s + "M1" }));

        assertThatThrownBy(() -> importService.executeImport(file, "batch-family-fail-" + s, employer.getId(), null, 0, false))
                .isInstanceOf(Exception.class);

        assertThat(memberRepository.findByCardNumber("FAM" + s)).isEmpty();
        assertThat(memberRepository.findByCardNumber("FAM" + s + "S1")).isEmpty();
        assertThat(memberRepository.findByCardNumber("FAM" + s + "D1")).isEmpty();
        assertThat(memberRepository.findByCardNumber("FAM" + s + "M1")).isEmpty();

        MemberImportLog log = importLogRepository.findByImportBatchId("batch-family-fail-" + s).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(MemberImportLog.ImportStatus.FAILED);
        assertThat(log.getStatus()).isNotEqualTo(MemberImportLog.ImportStatus.COMPLETED);
        assertThat(log.getCreatedCount() == null || log.getCreatedCount() == 0).isTrue();
        assertThat(log.getErrorMessage()).isNotBlank();
    }

    @Test
    void structuralFailureBeforeWriting_zeroChanges() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);

        // header-only, no data rows -> parseAndPreview reports zero valid rows,
        // executeImport must reject BEFORE any write transaction or import-log
        // "started" record is even created.
        MockMultipartFile file = excel(java.util.Collections.singletonList(HEADER));

        assertThatThrownBy(() -> importService.executeImport(file, "batch-empty-" + s, employer.getId(), null, 0, false))
                .isInstanceOf(Exception.class);

        assertThat(importLogRepository.findByImportBatchId("batch-empty-" + s)).isEmpty();
    }

    @Test
    void resubmittingTheIdenticalFile_isIdempotentNotDuplicated() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        newPolicy(employer, s);

        MockMultipartFile file = excel(List.of(
                HEADER,
                new String[] { "Idempotent " + s, employer.getName(), "NI" + s, "2026-01-01", "CARDI" + s }));

        MemberImportResultDto first = importService.executeImport(file, "batch-idem-1-" + s, employer.getId(), null, 0, false);
        assertThat(first.getStatus()).isEqualTo("COMPLETED");
        assertThat(first.getCreatedCount()).isEqualTo(1);

        long countAfterFirst = memberRepository.findByCardNumber("CARDI" + s).stream().count();

        // Second submission: same bytes, a NEW client-generated batchId (as a
        // fresh /preview -> /execute cycle would produce), same employer.
        MemberImportResultDto second = importService.executeImport(file, "batch-idem-2-" + s, employer.getId(), null, 0, false);

        assertThat(second.getMessage()).contains("batch-idem-1-" + s);
        assertThat(second.getCreatedCount()).isEqualTo(1); // reports the ORIGINAL outcome, not a new row
        assertThat(memberRepository.findByCardNumber("CARDI" + s).stream().count()).isEqualTo(countAfterFirst);
        // No second import-log row was created for the duplicate submission.
        assertThat(importLogRepository.findByImportBatchId("batch-idem-2-" + s)).isEmpty();
    }

    @Test
    void concurrentUploadsOfCollidingData_onlyOneSucceedsNoDuplicates() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        newPolicy(employer, s);

        String sharedCard = "RACE" + s;
        MockMultipartFile fileA = excel(List.of(HEADER,
                new String[] { "Racer A " + s, employer.getName(), "RA" + s, "2026-01-01", sharedCard }));
        MockMultipartFile fileB = excel(List.of(HEADER,
                new String[] { "Racer B " + s, employer.getName(), "RB" + s, "2026-01-01", sharedCard }));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Boolean> taskA = () -> {
            startGate.await();
            try {
                importService.executeImport(fileA, "batch-race-a-" + s, employer.getId(), null, 0, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        };
        Callable<Boolean> taskB = () -> {
            startGate.await();
            try {
                importService.executeImport(fileB, "batch-race-b-" + s, employer.getId(), null, 0, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> futureA = pool.submit(taskA);
        Future<Boolean> futureB = pool.submit(taskB);
        startGate.countDown();

        boolean succeededA = futureA.get(60, TimeUnit.SECONDS);
        boolean succeededB = futureB.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one of the two racing imports may have won the card number.
        assertThat(succeededA ^ succeededB).as("exactly one of the two concurrent imports should succeed").isTrue();
        assertThat(memberRepository.findByCardNumber(sharedCard).stream().count()).isEqualTo(1);
    }

    /**
     * Same race as above, but with employerId left null on both requests
     * (the file carries its own "employer" column instead of a single
     * selected employer). Proves the idempotency/uniqueness guard uses
     * NULLS NOT DISTINCT semantics -- a plain UNIQUE index would let
     * unlimited COMPLETED rows share (employer_id=NULL, file_hash=X) since
     * Postgres treats NULL as distinct from NULL by default.
     */
    @Test
    void concurrentUploadsWithNullEmployerId_onlyOneSucceedsNoDuplicates() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        newPolicy(employer, s);

        String sharedCard = "RACENULL" + s;
        MockMultipartFile fileA = excel(List.of(HEADER,
                new String[] { "Racer A " + s, employer.getName(), "RNA" + s, "2026-01-01", sharedCard }));
        MockMultipartFile fileB = excel(List.of(HEADER,
                new String[] { "Racer B " + s, employer.getName(), "RNB" + s, "2026-01-01", sharedCard }));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Boolean> taskA = () -> {
            startGate.await();
            try {
                importService.executeImport(fileA, "batch-race-null-a-" + s, null, null, 0, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        };
        Callable<Boolean> taskB = () -> {
            startGate.await();
            try {
                importService.executeImport(fileB, "batch-race-null-b-" + s, null, null, 0, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> futureA = pool.submit(taskA);
        Future<Boolean> futureB = pool.submit(taskB);
        startGate.countDown();

        boolean succeededA = futureA.get(60, TimeUnit.SECONDS);
        boolean succeededB = futureB.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(succeededA ^ succeededB).as("exactly one of the two concurrent null-employerId imports should succeed").isTrue();
        assertThat(memberRepository.findByCardNumber(sharedCard).stream().count()).isEqualTo(1);
    }

    /**
     * The identical file re-submitted with a DIFFERENT benefit policy is a
     * genuinely different operation and must actually re-execute (applying
     * the new policy), never be short-circuited as "already imported" --
     * proves the idempotency key covers benefitPolicyId, not just file
     * bytes + employer.
     */
    @Test
    void sameFileDifferentBenefitPolicy_isNotTreatedAsDuplicateAndAppliesTheNewPolicy() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policyA = newPolicy(employer, s + "-a");
        BenefitPolicy policyB = newPolicy(employer, s + "-b");

        MockMultipartFile file = excel(List.of(
                HEADER,
                new String[] { "Scoped " + s, employer.getName(), "NS" + s, "2026-01-01", "CARDS" + s }));

        MemberImportResultDto first = importService.executeImport(file, "batch-scope-a-" + s, employer.getId(), policyA.getId(), 0, false);
        assertThat(first.getStatus()).isEqualTo("COMPLETED");
        assertThat(first.getCreatedCount()).isEqualTo(1);
        Member afterFirst = memberRepository.findByCardNumber("CARDS" + s).orElseThrow();
        assertThat(afterFirst.getBenefitPolicy().getId()).isEqualTo(policyA.getId());

        // Same file bytes, same employer, DIFFERENT policy -- must NOT be
        // recognized as the same import.
        MemberImportResultDto second = importService.executeImport(file, "batch-scope-b-" + s, employer.getId(), policyB.getId(), 0, false);

        assertThat(second.getMessage()).doesNotContain("batch-scope-a-" + s);
        // A genuinely different scope resolves the existing card number and
        // updates it in place (same pipeline behavior as any other re-import
        // of a known card number) -- the important assertion is that policy B
        // was actually applied, proving this run really executed rather than
        // being echoed from the first run's stale result.
        Member afterSecond = memberRepository.findByCardNumber("CARDS" + s).orElseThrow();
        assertThat(afterSecond.getBenefitPolicy().getId()).isEqualTo(policyB.getId());

        assertThat(importLogRepository.findByImportBatchId("batch-scope-b-" + s)).isPresent();
    }

    @Test
    void dependentRowBeforePrincipalRow_stillLinksCorrectly() throws Exception {
        String s = randomSuffix();
        Employer employer = newEmployer(s);
        newPolicy(employer, s);

        MockMultipartFile file = excel(List.of(
                HEADER,
                new String[] { "Dependent Early " + s, employer.getName(), "NDE" + s, "2026-01-01", "CARDE" + s + "S1" },
                new String[] { "Principal Late " + s, employer.getName(), "NPL" + s, "2026-01-01", "CARDE" + s }));

        MemberImportResultDto result = importService.executeImport(file, "batch-order-" + s, employer.getId(), null, 0, false);

        assertThat(result.getStatus()).isIn("COMPLETED", "PARTIAL");

        Member principal = memberRepository.findByCardNumber("CARDE" + s).orElseThrow();
        assertThat(principal.getFullName()).isEqualTo("Principal Late " + s);

        Member dependent = memberRepository.findByCardNumber("CARDE" + s + "S1").orElseThrow();
        assertThat(dependent.getParent()).isNotNull();
        assertThat(dependent.getParent().getId()).isEqualTo(principal.getId());
    }
}
