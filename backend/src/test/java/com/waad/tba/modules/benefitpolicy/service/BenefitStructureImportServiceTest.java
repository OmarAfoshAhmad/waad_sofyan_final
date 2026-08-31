package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureImportResult;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitDefinitionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.claimcontext.entity.ClaimContextDefinition;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class BenefitStructureImportServiceTest {

    @Mock BenefitPolicyRepository policyRepository;
    @Mock BenefitPolicyRuleRepository ruleRepository;
    @Mock MedicalCategoryRepository categoryRepository;
    @Mock BenefitGroupRepository groupRepository;
    @Mock BenefitLimitBucketRepository bucketRepository;
    @Mock BenefitRuleBucketRepository linkRepository;
    @Mock BenefitDefinitionRepository definitionRepository;
    @Mock ClaimContextDefinitionRepository claimContextRepository;

    @InjectMocks BenefitStructureImportService service;

    @Test
    void importWorkbook_allowsInitialImportForActiveSuspendedEmptyPolicy() {
        BenefitPolicy policy = BenefitPolicy.builder()
                .id(1L)
                .status(BenefitPolicy.BenefitPolicyStatus.SUSPENDED)
                .active(true)
                .build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(ruleRepository.countByBenefitPolicyId(1L)).thenReturn(0L);
        when(groupRepository.countByPolicyId(1L)).thenReturn(0L);
        when(bucketRepository.countByPolicyId(1L)).thenReturn(0L);
        when(linkRepository.countByRuleBenefitPolicyId(1L)).thenReturn(0L);

        BenefitStructureImportResult result = service.importWorkbook(
                1L,
                workbook("benefits.xlsx", service.createSimplifiedTemplate()),
                false,
                BenefitStructureImportService.ImportMode.MERGE);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getMode()).isEqualTo("MERGE");
    }

    @Test
    void importWorkbook_blocksInitialImportForCancelledEmptyPolicy() {
        BenefitPolicy policy = BenefitPolicy.builder()
                .id(1L)
                .status(BenefitPolicy.BenefitPolicyStatus.CANCELLED)
                .active(true)
                .build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(ruleRepository.countByBenefitPolicyId(1L)).thenReturn(0L);
        when(groupRepository.countByPolicyId(1L)).thenReturn(0L);
        when(bucketRepository.countByPolicyId(1L)).thenReturn(0L);
        when(linkRepository.countByRuleBenefitPolicyId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.importWorkbook(
                1L,
                workbook("benefits.xlsx", service.createSimplifiedTemplate()),
                false,
                BenefitStructureImportService.ImportMode.MERGE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("مسودة");
    }

    @Test
    void simplifiedTemplateMakesTheFinancialLimitBasisExplicit() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.createSimplifiedTemplate()))) {
            assertThat(workbook.getSheet("المنافع").getRow(0).getCell(12).getStringCellValue())
                    .isEqualTo("أساس احتساب السقف");
            assertThat(workbook.getSheet("المجموعات").getRow(0).getCell(10).getStringCellValue())
                    .isEqualTo("أساس احتساب السقف");
        }
    }

    @Test
    void simplifiedImportAcceptsEligibleAmountAsTheDeclaredGroupLimitBasis() throws Exception {
        BenefitPolicy policy = BenefitPolicy.builder().id(1L)
                .status(BenefitPolicy.BenefitPolicyStatus.DRAFT).active(true).build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(groupRepository.findByPolicyIdAndCodeIgnoreCase(1L, "GRP-MATERNITY")).thenReturn(Optional.empty());
        when(bucketRepository.findByPolicyIdAndCodeIgnoreCase(1L, "AUTO-GRP-GRP-MATERNITY")).thenReturn(Optional.empty());

        byte[] source = service.createSimplifiedTemplate();
        byte[] populated;
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row row = workbook.getSheet("المجموعات").createRow(1);
            row.createCell(0).setCellValue("GRP-MATERNITY");
            row.createCell(1).setCellValue("الولادة الطبيعية والقيصرية");
            row.createCell(2).setCellValue("INPATIENT");
            // The parser contract is under test here; rule/link validation is covered separately.
            row.createCell(3).setCellValue("");
            row.createCell(4).setCellValue(4000);
            row.createCell(7).setCellValue("POLICY_PERIOD");
            row.createCell(8).setCellValue(1);
            row.createCell(9).setCellValue("EACH_UNIT");
            row.createCell(10).setCellValue("ELIGIBLE_AMOUNT");
            row.createCell(11).setCellValue("نعم");
            workbook.write(output);
            populated = output.toByteArray();
        }

        BenefitStructureImportResult result = service.importWorkbook(1L,
                workbook("maternity.xlsx", populated), true, BenefitStructureImportService.ImportMode.MERGE);

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void simplifiedImportCanDeclareMaternitySeparatelyFromItsInpatientEncounter() throws Exception {
        BenefitPolicy policy = BenefitPolicy.builder().id(1L)
                .status(BenefitPolicy.BenefitPolicyStatus.DRAFT).active(true).build();
        MedicalCategory category = MedicalCategory.builder().id(90L).code("CAT-COV-INPATIENT")
                .name("إيواء").active(true).deleted(false).contexts(Set.of(CategoryContext.INPATIENT)).build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(categoryRepository.findByCode("CAT-COV-INPATIENT")).thenReturn(Optional.of(category));
        when(claimContextRepository.findById("MATERNITY")).thenReturn(Optional.of(
                ClaimContextDefinition.builder().code("MATERNITY").nameAr("ولادة وحمل")
                        .baseEncounterType(EncounterType.INPATIENT).active(true).build()));

        byte[] populated;
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.createSimplifiedTemplate()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row row = workbook.getSheet("المنافع").createRow(1);
            row.createCell(0).setCellValue("CAT-COV-INPATIENT");
            row.createCell(1).setCellValue("الولادة من قوائم الإيواء");
            row.createCell(2).setCellValue("INPATIENT");
            row.createCell(3).setCellValue(75);
            row.createCell(4).setCellValue(25);
            row.createCell(5).setCellValue("لا");
            row.createCell(9).setCellValue("POLICY_PERIOD");
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("EACH_UNIT");
            row.createCell(12).setCellValue("ELIGIBLE_AMOUNT");
            row.createCell(13).setCellValue("نعم");
            row.createCell(14).setCellValue("MATERNITY");
            workbook.write(output);
            populated = output.toByteArray();
        }

        BenefitStructureImportResult result = service.importWorkbook(1L,
                workbook("maternity-context.xlsx", populated), true, BenefitStructureImportService.ImportMode.MERGE);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getRules()).isEqualTo(1);
    }

    private MockMultipartFile workbook(String name, byte[] content) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }
}
