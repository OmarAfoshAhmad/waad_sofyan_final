package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * P-07: a workbook with one valid rule row and one row naming an unapproved
 * medical category must not import the valid row and merely report the bad
 * one -- it must import NOTHING.
 *
 * The service already separates "validate every row" from "apply the rows",
 * only calling {@code apply()} when {@code errors.isEmpty()} -- a
 * validate-then-commit design, backed by {@code @Transactional} as a second
 * layer in case {@code apply()} itself throws partway. This test proves
 * that design actually holds against a real database, not just that the
 * code reads that way.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BenefitStructureImportAtomicityIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitStructureImportService importService;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private BenefitPolicyRuleRepository rules;
    @Autowired private BenefitGroupRepository groups;
    @Autowired private BenefitLimitBucketRepository buckets;
    @Autowired private EmployerRepository employers;
    @Autowired private MedicalCategoryRepository categories;
    @Autowired private UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long policyId;
    private String validCategoryCode;

    @BeforeEach
    void seedADraftPolicyAndOneApprovedCategory() {
        String s = suffix();
        String username = "polimport-" + suffix();
        users.save(User.builder().username(username).password("x").fullName("Import Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));

        Employer employer = employers.save(Employer.builder()
                .name("جهة اختبار الاستيراد " + s).code("PIMP-" + s).active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("وثيقة اختبار الاستيراد " + s).policyCode("PIMP-POL-" + s).employer(employer)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .annualLimit(java.math.BigDecimal.valueOf(10000)).defaultCoveragePercent(80)
                .status(BenefitPolicy.BenefitPolicyStatus.DRAFT).active(true).build());
        policyId = policy.getId();

        validCategoryCode = "PIMP-CAT-" + s;
        categories.save(MedicalCategory.builder().code(validCategoryCode).name("فئة معتمدة").active(true).build());
    }

    @Test
    @DisplayName("a workbook mixing one valid rule and one unapproved-category rule imports nothing")
    void mixedValidAndInvalidRowsImportsNothingAtAll() throws Exception {
        MockMultipartFile workbook = simplifiedWorkbookWith(
                new String[] {validCategoryCode, "قاعدة صحيحة", "OUTPATIENT", "80"},
                new String[] {"NOT-AN-APPROVED-CODE", "قاعدة خاطئة", "OUTPATIENT", "50"});

        assertThatThrownBy(() -> importService.importWorkbook(
                policyId, workbook, false, BenefitStructureImportService.ImportMode.MERGE))
                .as("one row naming an unapproved category must reject the whole workbook")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("تصنيف غير معتمد");

        assertThat(rules.countByBenefitPolicyId(policyId))
                .as("the OTHER, valid row must not have been imported either -- all or nothing, "
                        + "never a partial import that silently drops just the bad row")
                .isZero();
    }

    @Test
    @DisplayName("the same workbook with only the valid row imports it")
    void onlyTheValidRowImportsCleanly() throws Exception {
        MockMultipartFile workbook = simplifiedWorkbookWith(
                new String[] {validCategoryCode, "قاعدة صحيحة", "OUTPATIENT", "80"});

        var result = importService.importWorkbook(
                policyId, workbook, false, BenefitStructureImportService.ImportMode.MERGE);

        assertThat(result.getErrors()).isEmpty();
        assertThat(rules.countByBenefitPolicyId(policyId))
                .as("sanity check: the same fixture, without the bad row, actually imports")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("REPLACE reuses existing visible group and bucket names when the file renames their codes")
    void replaceCanRenameCodesWithoutCollidingWithVisibleNameIndexes() throws Exception {
        BenefitPolicy policy = policies.findById(policyId).orElseThrow();
        BenefitGroup oldGroup = groups.save(BenefitGroup.builder()
                .policy(policy)
                .code("OLD-MATERNITY-GROUP")
                .nameAr("الولادة الطبيعية والقيصرية")
                .contextType(EncounterType.INPATIENT)
                .aggregationMode(AggregationMode.SHARED)
                .active(true)
                .build());
        buckets.save(BenefitLimitBucket.builder()
                .policy(policy)
                .benefitGroup(oldGroup)
                .code("OLD-MATERNITY-BUCKET")
                .nameAr("الولادة الطبيعية والقيصرية")
                .contextType(EncounterType.INPATIENT)
                .amountLimit(java.math.BigDecimal.valueOf(4000))
                .periodType(LimitPeriodType.POLICY_PERIOD)
                .periodValue(1)
                .countingMethod(CountingMethod.EACH_UNIT)
                .consumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT)
                .benefitScopeType(BenefitScopeType.GROUP)
                .shared(true)
                .active(true)
                .build());

        MockMultipartFile workbook = simplifiedWorkbookWithGroup(
                "NEW-MATERNITY-GROUP", "الولادة الطبيعية والقيصرية",
                "");

        var result = importService.importWorkbook(policyId, workbook, false, BenefitStructureImportService.ImportMode.REPLACE);

        assertThat(result.getErrors()).isEmpty();
        assertThat(groups.findByPolicyIdAndCodeIgnoreCase(policyId, "NEW-MATERNITY-GROUP")).isPresent();
        assertThat(groups.findByPolicyIdAndCodeIgnoreCase(policyId, "OLD-MATERNITY-GROUP")).isEmpty();
        assertThat(buckets.findByPolicyIdAndCodeIgnoreCase(policyId, "AUTO-GRP-NEW-MATERNITY-GROUP")).isPresent();
        assertThat(buckets.findByPolicyIdAndCodeIgnoreCase(policyId, "OLD-MATERNITY-BUCKET")).isEmpty();
    }

    /** The "المنافع" simplified sheet BenefitStructureImportService.parseSimplified reads. */
    private MockMultipartFile simplifiedWorkbookWith(String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("المنافع");
            Row header = sheet.createRow(0);
            String[] headers = {"كود التصنيف", "اسم المنفعة", "السياق", "نسبة التغطية"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            workbook.write(out);
            return new MockMultipartFile("file", "benefits.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private MockMultipartFile simplifiedWorkbookWithGroup(String groupCode, String groupName, String benefitCodes) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet benefits = workbook.createSheet("المنافع");
            Row benefitsHeader = benefits.createRow(0);
            String[] benefitHeaders = {"كود التصنيف", "اسم المنفعة", "السياق", "نسبة التغطية"};
            for (int i = 0; i < benefitHeaders.length; i++) benefitsHeader.createCell(i).setCellValue(benefitHeaders[i]);

            Sheet sheet = workbook.createSheet("المجموعات");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "كود المجموعة", "اسم المجموعة", "السياق", "أكواد المنافع مفصولة بفاصلة",
                    "السقف المالي", "حد المرات", "حد الأيام", "الفترة", "قيمة الفترة", "طريقة العد",
                    "أساس احتساب السقف", "نشط", "سياق القرار"
            };
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(groupCode);
            row.createCell(1).setCellValue(groupName);
            row.createCell(2).setCellValue("INPATIENT");
            row.createCell(3).setCellValue(benefitCodes);
            row.createCell(4).setCellValue(4000);
            row.createCell(7).setCellValue("POLICY_PERIOD");
            row.createCell(8).setCellValue(1);
            row.createCell(9).setCellValue("EACH_UNIT");
            row.createCell(10).setCellValue("ELIGIBLE_AMOUNT");
            row.createCell(11).setCellValue("نعم");
            row.createCell(12).setCellValue("INPATIENT");

            workbook.write(out);
            return new MockMultipartFile("file", "benefits-groups.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
