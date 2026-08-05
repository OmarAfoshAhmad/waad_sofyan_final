package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.dto.EmployerImportConfirmResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportPreviewResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportRowDto.Action;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the employer bulk-import flow end to end against real PostgreSQL,
 * including the parts that cannot be verified with mocks: that the resulting
 * policy is created as a DRAFT with only its policy-wide default coverage
 * percentage set (no coverage rules, not activated), and that re-importing an
 * existing employer only writes the fields that changed.
 *
 * Not @Transactional: each row is written in its own committed transaction
 * by design (see EmployerImportRowProcessor), so a rollback-wrapping test
 * would not reflect real behavior.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class EmployerImportIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private EmployerImportService importService;

    @Autowired
    private EmployerRepository employerRepository;

    @Autowired
    private BenefitPolicyRepository benefitPolicyRepository;

    private static final String[] HEADERS =
            {"اسم جهة العمل", "رمز الجهة", "رقم الهاتف", "البريد الإلكتروني", "العنوان", "الحد السنوي", "نسبة التغطية"};

    private MockMultipartFile buildWorkbook(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("بيانات جهات العمل");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int r = 1;
            for (String[] rowValues : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < rowValues.length; c++) {
                    if (rowValues[c] != null) row.createCell(c).setCellValue(rowValues[c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "employers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void newEmployerGetsCreatedWithADraftPolicyAtTheGivenCoveragePercent() throws Exception {
        String unique = "IT-" + System.nanoTime();
        String name = "شركة الاستيراد " + unique;
        MockMultipartFile file = buildWorkbook(new String[][]{
                {name, null, "0911234567", "import@" + unique + ".ly", "طرابلس", "75000", "60"}
        });

        EmployerImportPreviewResultDto preview = importService.preview(file);
        assertThat(preview.getValidCount()).isEqualTo(1);
        assertThat(preview.getRows().get(0).getAction()).isEqualTo(Action.CREATE);

        EmployerImportConfirmResultDto result = importService.confirm(preview.getSessionId());

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getResults().get(0).getAction()).isEqualTo("CREATE");
        assertThat(result.getResults().get(0).getPolicyCode()).isNotBlank();

        Employer created = employerRepository.findByNameIgnoreCase(name).orElseThrow();
        assertThat(created.getCode()).startsWith("EMP-");
        assertThat(created.getPhone()).isEqualTo("0911234567");

        var policies = benefitPolicyRepository.findByEmployerIdAndActiveTrue(created.getId());
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).getStatus()).isEqualTo(BenefitPolicyStatus.DRAFT);
        assertThat(policies.get(0).getAnnualLimit()).isEqualByComparingTo(new BigDecimal("75000"));
        assertThat(policies.get(0).getDefaultCoveragePercent()).isEqualTo(60);
    }

    @Test
    void reimportingAnUnchangedEmployerIsANoOpAndDoesNotDuplicateThePolicy() throws Exception {
        String unique = "IT-" + System.nanoTime();
        String name = "شركة بلا تغيير " + unique;
        MockMultipartFile firstFile = buildWorkbook(new String[][]{
                {name, null, "0922222222", "noop@" + unique + ".ly", "بنغازي", null, null}
        });
        EmployerImportPreviewResultDto firstPreview = importService.preview(firstFile);
        importService.confirm(firstPreview.getSessionId());

        Employer created = employerRepository.findByNameIgnoreCase(name).orElseThrow();
        var firstPolicies = benefitPolicyRepository.findByEmployerIdAndActiveTrue(created.getId());
        assertThat(firstPolicies).hasSize(1);
        Long firstPolicyId = firstPolicies.get(0).getId();

        // Re-import the exact same row (same code this time, matching by code).
        MockMultipartFile secondFile = buildWorkbook(new String[][]{
                {name, created.getCode(), "0922222222", "noop@" + unique + ".ly", "بنغازي", null, null}
        });
        EmployerImportPreviewResultDto secondPreview = importService.preview(secondFile);
        assertThat(secondPreview.getRows().get(0).getAction()).isEqualTo(Action.NO_CHANGE);

        EmployerImportConfirmResultDto secondResult = importService.confirm(secondPreview.getSessionId());
        assertThat(secondResult.getSuccessCount()).isEqualTo(1);
        assertThat(secondResult.getResults().get(0).getAction()).isEqualTo("NO_CHANGE");

        // No new policy was created — the existing one is untouched.
        var afterPolicies = benefitPolicyRepository.findByEmployerIdAndActiveTrue(created.getId());
        assertThat(afterPolicies).hasSize(1);
        assertThat(afterPolicies.get(0).getId()).isEqualTo(firstPolicyId);
    }

    @Test
    void reimportingWithOneChangedFieldUpdatesOnlyThatFieldAndPreservesTheRest() throws Exception {
        String unique = "IT-" + System.nanoTime();
        String name = "شركة تحديث جزئي " + unique;
        MockMultipartFile firstFile = buildWorkbook(new String[][]{
                {name, null, "0933333333", "before@" + unique + ".ly", "مصراتة", null, null}
        });
        EmployerImportPreviewResultDto firstPreview = importService.preview(firstFile);
        importService.confirm(firstPreview.getSessionId());
        Employer created = employerRepository.findByNameIgnoreCase(name).orElseThrow();

        // Only the phone changes; email/address cells are left blank and must survive untouched.
        MockMultipartFile secondFile = buildWorkbook(new String[][]{
                {name, created.getCode(), "0944444444", null, null, null, null}
        });
        EmployerImportPreviewResultDto secondPreview = importService.preview(secondFile);
        assertThat(secondPreview.getRows().get(0).getAction()).isEqualTo(Action.UPDATE);
        assertThat(secondPreview.getRows().get(0).getChangedFields()).containsExactly("الهاتف");

        EmployerImportConfirmResultDto secondResult = importService.confirm(secondPreview.getSessionId());
        assertThat(secondResult.getSuccessCount()).isEqualTo(1);

        Employer updated = employerRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getPhone()).isEqualTo("0944444444");
        assertThat(updated.getEmail()).isEqualTo("before@" + unique + ".ly"); // preserved, not erased
        assertThat(updated.getAddress()).isEqualTo("مصراتة"); // preserved, not erased
    }

    @Test
    void aBlankAnnualLimitFallsBackToTheConfiguredDefault() throws Exception {
        String unique = "IT-" + System.nanoTime();
        String name = "شركة الحد الافتراضي " + unique;
        MockMultipartFile file = buildWorkbook(new String[][]{
                {name, null, null, null, null, null, null}
        });

        EmployerImportPreviewResultDto preview = importService.preview(file);
        importService.confirm(preview.getSessionId());

        Employer created = employerRepository.findByNameIgnoreCase(name).orElseThrow();
        var policy = benefitPolicyRepository.findByEmployerIdAndActiveTrue(created.getId()).get(0);
        assertThat(policy.getAnnualLimit()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    void aBlankCoveragePercentFallsBackTo100() throws Exception {
        String unique = "IT-" + System.nanoTime();
        String name = "شركة نسبة التغطية الافتراضية " + unique;
        MockMultipartFile file = buildWorkbook(new String[][]{
                {name, null, null, null, null, null, null}
        });

        EmployerImportPreviewResultDto preview = importService.preview(file);
        importService.confirm(preview.getSessionId());

        Employer created = employerRepository.findByNameIgnoreCase(name).orElseThrow();
        var policy = benefitPolicyRepository.findByEmployerIdAndActiveTrue(created.getId()).get(0);
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.DRAFT);
        assertThat(policy.getDefaultCoveragePercent()).isEqualTo(100);
    }
}
