package com.waad.tba.modules.providercontract.service;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.PricingImportPreviewDto;
import com.waad.tba.modules.providercontract.dto.PricingImportPreviewItemDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The price-list review screen classifies through the approved alias table.
 *
 * <p>It used to classify through PricingItemClassificationEngine, which reads
 * service_specialty_insurance_map -- a table V84 emptied during the cutover to
 * context-independent categories. With no rules left, every row of every file
 * came back "No matching classification rule found", which was not even true:
 * there were no rules to match against. Nothing had seeded that table since,
 * and no endpoint could add one.
 *
 * <p>These tests would all have failed before the change, and the first one
 * would have failed for every service on earth.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PriceListImportUsesApprovedAliasesIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ProviderContractPricingExcelService excelService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;

    @Test
    void classifiesARowCarryingAnApprovedClassification() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = preview(contract,
                row("كشف باطنية", "SRV-1", "120", "عيادات خارجية"));

        PricingImportPreviewItemDto item = preview.getItems().get(0);
        assertThat(item.getProposedCategoryCode()).isEqualTo("CAT-COV-OUTPATIENT");
        assertThat(item.isRequiresReview()).isFalse();
        assertThat(item.getClassificationSource()).isEqualTo("CLAIM_CONTEXT_ALIAS");
    }

    /**
     * V211 added this one. It is here because the reference list is the contract
     * with the user: a classification they approved must be understood.
     */
    @Test
    void classifiesAnApprovedClassificationAddedByV211() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = preview(contract,
                row("جبيرة", "SRV-2", "60", "إصابات عمل"));

        PricingImportPreviewItemDto item = preview.getItems().get(0);
        assertThat(item.getProposedCategoryCode()).isEqualTo("CAT-WORK-INJURY");
        assertThat(item.isRequiresReview()).isFalse();
    }

    /**
     * The heading that only fixes a context reaches review with no category
     * proposed -- the service decides that, not the heading.
     */
    @Test
    void sendsAContextOnlyHeadingToReviewWithoutInventingACategory() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = preview(contract,
                row("ولادة قيصرية", "SRV-3", "3000", "ولادة طبيعية وقيصرية"));

        PricingImportPreviewItemDto item = preview.getItems().get(0);
        assertThat(item.getProposedCategoryCode()).isNull();
        assertThat(item.isRequiresReview()).isTrue();
    }

    /**
     * An unknown heading is reported as unknown, and the message names it rather
     * than claiming no rule exists. The old wording blamed a rule table the user
     * could neither see nor fill.
     */
    @Test
    void namesTheUnknownClassificationInsteadOfBlamingAMissingRule() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = preview(contract,
                row("جهاز التنفس الاصطناعي", "SRV-4", "500", "تصنيف لم يُعتمد"));

        PricingImportPreviewItemDto item = preview.getItems().get(0);
        assertThat(item.isRequiresReview()).isTrue();
        assertThat(item.getProposedCategoryCode()).isNull();
        assertThat(item.getReviewReason())
                .contains("تصنيف لم يُعتمد")
                .doesNotContain("No matching classification rule found");
        assertThat(item.getClassificationSource()).isEqualTo("NO_APPROVED_ALIAS");
    }

    private ProviderContract contract() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Provider provider = providerRepository.save(Provider.builder()
                .name("Price List Provider " + suffix)
                .licenseNumber("PL-" + suffix)
                .providerType(ProviderType.HOSPITAL)
                .active(true)
                .build());
        return contractRepository.saveAndFlush(ProviderContract.builder()
                .contractCode("PLC-" + suffix)
                .provider(provider)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.ACTIVE)
                .active(true)
                .build());
    }

    private record SheetRow(String serviceName, String code, String price, String mainCategory) {}

    private static SheetRow row(String serviceName, String code, String price, String mainCategory) {
        return new SheetRow(serviceName, code, price, mainCategory);
    }

    private PricingImportPreviewDto preview(ProviderContract contract, SheetRow... rows) throws IOException {
        return excelService.importForPreview(contract.getId(),
                new MockMultipartFile("file", "prices.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        workbook(List.of(rows))));
    }

    private byte[] workbook(List<SheetRow> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("اسم الخدمة");
            header.createCell(1).setCellValue("الكود");
            header.createCell(2).setCellValue("السعر");
            header.createCell(3).setCellValue("التصنيف الرئيسي");

            int index = 1;
            for (SheetRow row : rows) {
                Row sheetRow = sheet.createRow(index++);
                sheetRow.createCell(0).setCellValue(row.serviceName());
                sheetRow.createCell(1).setCellValue(row.code());
                sheetRow.createCell(2).setCellValue(row.price());
                sheetRow.createCell(3).setCellValue(row.mainCategory());
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
