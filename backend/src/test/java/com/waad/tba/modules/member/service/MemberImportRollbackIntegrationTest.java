package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberAttribute;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.repository.MemberAttributeRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.AuthorizedImportScope;
import com.waad.tba.modules.member.security.MemberImportAccessPolicy;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberImportRollbackIntegrationTest extends PostgresIntegrationTestBase {
    private static final String[] HEADER = { "full_name", "employer", "national_id", "start_date",
            "card_number", "phone", "job_title", "department" };

    @Autowired MemberExcelImportService importService;
    @Autowired MemberImportRollbackService rollbackService;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberAttributeRepository memberAttributeRepository;
    @Autowired EmployerRepository employerRepository;
    @Autowired BenefitPolicyRepository benefitPolicyRepository;
    @Autowired MemberImportLogRepository importLogRepository;
    @Autowired MemberImportBatchRowRepository batchRowRepository;
    @Autowired MemberImportRollbackRepository rollbackRepository;
    @MockitoBean MemberImportAccessPolicy importAccessPolicy;
    @MockitoBean AuthorizationService authorizationService;
    @MockitoSpyBean MemberStatusTransitionService statusTransitionService;

    private User actor;

    @BeforeEach
    void authorize() {
        actor = User.builder().id(7001L).username("rollback-admin").fullName("Rollback Admin")
                .email("rollback@test.local").userType("SUPER_ADMIN").active(true).build();
        when(authorizationService.getCurrentUser()).thenReturn(actor);
        when(authorizationService.requireCurrentUser()).thenReturn(actor);
        AuthorizedImportScope scope = mock(AuthorizedImportScope.class);
        when(scope.covers(any())).thenReturn(true);
        when(scope.mayClearAbsentMembers()).thenReturn(true);
        when(importAccessPolicy.require(any(), anySet(), anyBoolean())).thenReturn(scope);
        when(importAccessPolicy.requireRollback(any())).thenReturn(scope);
    }

    @Test
    void createdFamilyIsDeletedAtomicallyWhileImmutableBatchEvidenceSurvives() throws Exception {
        Fixture fixture = fixture();
        String principalCard = "RBP" + fixture.suffix();
        String dependentCard = principalCard + "S1";
        String batch = "rollback-created-" + fixture.suffix();

        importService.executeImport(excel(List.of(HEADER,
                row("Imported Principal", fixture.employer(), "N-P", principalCard, "091", "Engineer", "IT"),
                row("Imported Dependent", fixture.employer(), "N-D", dependentCard, "092", "Student", "Family"))),
                batch, fixture.employer().getId(), fixture.policy().getId(), 0, false);

        MemberImportLog log = importLogRepository.findByImportBatchId(batch).orElseThrow();
        List<Long> trackedIds = batchRowRepository.findByImportLogId(log.getId()).stream()
                .map(row -> row.getMemberId()).toList();
        assertThat(trackedIds).hasSize(2);

        var result = rollbackService.execute(log.getId(), "إلغاء ملف أُرسل بالخطأ");

        assertThat(result.getRevertedCreatedCount()).isEqualTo(2);
        assertThat(memberRepository.findAllById(trackedIds)).isEmpty();
        assertThat(batchRowRepository.findByImportLogId(log.getId())).hasSize(2);
        assertThat(rollbackRepository.findByImportLogIdAndStatus(log.getId(),
                com.waad.tba.modules.member.entity.MemberImportRollback.Status.COMPLETED)).isPresent();
    }

    @Test
    void unchangedUpdatedMemberRestoresAllImportOwnedFieldsIncludingAttributes() throws Exception {
        Fixture fixture = fixture();
        String card = "RBU" + fixture.suffix();
        Member original = memberRepository.save(Member.builder().fullName("Original Name").cardNumber(card)
                .barcode(card).nationalNumber("OLD-N").phone("090").employer(fixture.employer())
                .benefitPolicy(fixture.policy()).policyNumber(fixture.policy().getPolicyCode())
                .status(Member.MemberStatus.PENDING).active(false).cardStatus(Member.CardStatus.ACTIVE)
                .startDate(LocalDate.of(2025, 1, 1)).build());
        original.getAttributes().add(MemberAttribute.builder().member(original).attributeCode("job_title")
                .attributeValue("Accountant").source(MemberAttribute.AttributeSource.MANUAL).build());
        original.getAttributes().add(MemberAttribute.builder().member(original).attributeCode("work_location")
                .attributeValue("HQ").source(MemberAttribute.AttributeSource.API).sourceReference("hr-1").build());
        original = memberRepository.saveAndFlush(original);
        original.setEmployer(fixture.employer());
        original.setBenefitPolicy(fixture.policy());
        initializeTemporalAssignments(original);

        String batch = "rollback-updated-" + fixture.suffix();
        importService.executeImport(excel(List.of(HEADER,
                row("Imported Name", fixture.employer(), "NEW-N", card, "099", "Manager", "Operations"))),
                batch, fixture.employer().getId(), fixture.policy().getId(), 0, false);
        MemberImportLog log = importLogRepository.findByImportBatchId(batch).orElseThrow();

        rollbackService.execute(log.getId(), "استعادة بيانات ما قبل الملف");

        Member restored = memberRepository.findById(original.getId()).orElseThrow();
        assertThat(restored.getFullName()).isEqualTo("Original Name");
        assertThat(restored.getNationalNumber()).isEqualTo("OLD-N");
        assertThat(restored.getPhone()).isEqualTo("090");
        assertThat(memberAttributeRepository.findByMemberId(restored.getId())).extracting(MemberAttribute::getAttributeCode,
                MemberAttribute::getAttributeValue, MemberAttribute::getSource)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("job_title", "Accountant", MemberAttribute.AttributeSource.MANUAL),
                        org.assertj.core.groups.Tuple.tuple("work_location", "HQ", MemberAttribute.AttributeSource.API));
    }

    @Test
    void laterManualEditIsReportedAndNeverOverwritten() throws Exception {
        Fixture fixture = fixture();
        String card = "RBS" + fixture.suffix();
        String batch = "rollback-stale-" + fixture.suffix();
        importService.executeImport(excel(List.of(HEADER,
                row("Imported", fixture.employer(), "N-S", card, "091", "Engineer", "IT"))),
                batch, fixture.employer().getId(), fixture.policy().getId(), 0, false);
        Member member = memberRepository.findByCardNumber(card).orElseThrow();
        member.setPhone("MANUAL-LATER");
        memberRepository.saveAndFlush(member);
        MemberImportLog log = importLogRepository.findByImportBatchId(batch).orElseThrow();

        var result = rollbackService.execute(log.getId(), "التراجع دون سحق تعديل لاحق");

        assertThat(result.getRevertedCreatedCount()).isZero();
        assertThat(result.getSkippedCount()).isOne();
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getPhone()).isEqualTo("MANUAL-LATER");
    }

    @Test
    void lateFailureRollsBackEarlierRestorationsWhileFailureAuditSurvives() throws Exception {
        Fixture fixture = fixture();
        String firstCard = "RBF" + fixture.suffix();
        String secondCard = "RBG" + fixture.suffix();
        Member first = existingMember(fixture, firstCard, "First Before", "090");
        Member second = existingMember(fixture, secondCard, "Second Before", "091");
        String batch = "rollback-failure-" + fixture.suffix();
        importService.executeImport(excel(List.of(HEADER,
                row("First Imported", fixture.employer(), "NF", firstCard, "098", "A", "B"),
                row("Second Imported", fixture.employer(), "NG", secondCard, "099", "C", "D"))),
                batch, fixture.employer().getId(), fixture.policy().getId(), 0, false);
        MemberImportLog log = importLogRepository.findByImportBatchId(batch).orElseThrow();
        doThrow(new IllegalStateException("simulated late rollback failure"))
                .when(statusTransitionService).restoreStatusAfterImport(
                        org.mockito.ArgumentMatchers.argThat(m -> m.getId().equals(second.getId())),
                        any(), any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> rollbackService.execute(log.getId(), "اختبار ذرية الفشل المتأخر"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(memberRepository.findById(first.getId()).orElseThrow().getFullName()).isEqualTo("First Imported");
        assertThat(memberRepository.findById(second.getId()).orElseThrow().getFullName()).isEqualTo("Second Imported");
        assertThat(rollbackRepository.findByImportLogIdAndStatus(log.getId(),
                com.waad.tba.modules.member.entity.MemberImportRollback.Status.COMPLETED)).isEmpty();
        assertThat(rollbackRepository.findByImportLogIdAndStatus(log.getId(),
                com.waad.tba.modules.member.entity.MemberImportRollback.Status.FAILED)).isPresent();
    }

    @Test
    void concurrentRollbackRequestsProduceExactlyOneCompletedEffect() throws Exception {
        Fixture fixture = fixture();
        String card = "RBC" + fixture.suffix();
        String batch = "rollback-concurrent-" + fixture.suffix();
        importService.executeImport(excel(List.of(HEADER,
                row("Concurrent", fixture.employer(), "NC", card, "091", "A", "B"))),
                batch, fixture.employer().getId(), fixture.policy().getId(), 0, false);
        MemberImportLog log = importLogRepository.findByImportBatchId(batch).orElseThrow();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                try {
                    rollbackService.execute(log.getId(), "طلبان متزامنان");
                    return true;
                } catch (RuntimeException expectedLoser) {
                    return false;
                }
            };
            Future<Boolean> one = pool.submit(attempt);
            Future<Boolean> two = pool.submit(attempt);
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(memberRepository.findByCardNumber(card)).isEmpty();
        assertThat(rollbackRepository.findAll().stream()
                .filter(r -> r.getImportLogId().equals(log.getId()))
                .filter(r -> r.getStatus() == com.waad.tba.modules.member.entity.MemberImportRollback.Status.COMPLETED))
                .hasSize(1);
    }

    private Member existingMember(Fixture fixture, String card, String name, String phone) {
        Member member = memberRepository.saveAndFlush(Member.builder().fullName(name).cardNumber(card).barcode(card)
                .nationalNumber("OLD-" + card).phone(phone).employer(fixture.employer())
                .benefitPolicy(fixture.policy()).policyNumber(fixture.policy().getPolicyCode())
                .status(Member.MemberStatus.PENDING).active(false).cardStatus(Member.CardStatus.ACTIVE)
                .startDate(LocalDate.of(2025, 1, 1)).build());
        member.setEmployer(fixture.employer());
        member.setBenefitPolicy(fixture.policy());
        return initializeTemporalAssignments(member);
    }

    private Fixture fixture() {
        String suffix = Long.toUnsignedString(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 8);
        Employer employer = employerRepository.save(Employer.builder().name("Rollback Employer " + suffix)
                .code("RB-" + suffix).active(true).build());
        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder().name("Rollback Plan " + suffix)
                .policyCode("RB-POL-" + suffix).employer(employer).annualLimit(new BigDecimal("60000"))
                .defaultCoveragePercent(80).startDate(LocalDate.now().minusYears(1))
                .endDate(LocalDate.now().plusYears(1)).status(BenefitPolicy.BenefitPolicyStatus.ACTIVE)
                .active(true).build());
        return new Fixture(suffix, employer, policy);
    }

    private String[] row(String name, Employer employer, String nationalId, String card, String phone,
            String jobTitle, String department) {
        return new String[] { name, employer.getName(), nationalId, "2026-01-01", card, phone, jobTitle, department };
    }

    private MockMultipartFile excel(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Members");
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).length; c++) row.createCell(c).setCellValue(rows.get(r)[c]);
            }
            workbook.write(out);
            return new MockMultipartFile("file", "members.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private record Fixture(String suffix, Employer employer, BenefitPolicy policy) {}
}
