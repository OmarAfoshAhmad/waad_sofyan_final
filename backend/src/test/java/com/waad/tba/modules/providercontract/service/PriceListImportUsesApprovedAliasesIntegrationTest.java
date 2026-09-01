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

    /**
     * The column heading a price list is most likely to use. COLUMN_MAPPINGS has
     * recognised "التصنيف" all along, but the classifier read only the main and
     * sub headings, so the value went into a key nobody consumed: every row of
     * such a file came back unclassified, and the reason named an empty label
     * because there was nothing to name.
     */
    @Test
    void readsTheClassificationFromTheTemplatesOwnCategoryColumn() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = excelService.importForPreview(contract.getId(),
                new MockMultipartFile("file", "prices.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        workbookWithCategoryColumn()));

        PricingImportPreviewItemDto item = preview.getItems().get(0);
        assertThat(item.getProposedCategoryCode()).isEqualTo("CAT-COV-INPATIENT");
        assertThat(item.isRequiresReview()).isFalse();
    }

    /** A file with no classification column at all says so, rather than «». */
    @Test
    void saysTheColumnIsMissingRatherThanNamingAnEmptyClassification() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = excelService.importForPreview(contract.getId(),
                new MockMultipartFile("file", "prices.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        workbookWithoutAnyClassificationColumn()));

        PricingImportPreviewItemDto item = preview.getItems().get(0);
        assertThat(item.isRequiresReview()).isTrue();
        assertThat(item.getReviewReason())
                .isEqualTo("لا يحتوي الملف على عمود تصنيف لهذه الخدمة.")
                .doesNotContain("«»");
    }

    /**
     * The exact header row every approved price list carries. Those two
     * classification columns were absent from COLUMN_MAPPINGS, so the code
     * column -- which holds the medical_categories code outright -- was never
     * read, and every row of all 37 files came back unclassified while naming
     * an empty label as the reason.
     */
    @Test
    void classifiesTheRealPriceListFormatFromItsOwnCategoryCode() throws IOException {
        ProviderContract contract = contract();

        PricingImportPreviewDto preview = excelService.importForPreview(contract.getId(),
                new MockMultipartFile("file", "prices.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        approvedPriceListWorkbook()));

        assertThat(preview.getItems()).hasSize(2);
        assertThat(preview.getItems())
                .allSatisfy(item -> assertThat(item.isRequiresReview()).isFalse());
        assertThat(preview.getItems().get(0).getProposedCategoryCode()).isEqualTo("CAT-COV-INPATIENT");
        assertThat(preview.getItems().get(1).getProposedCategoryCode()).isEqualTo("CAT-DENT-ROUTINE");
    }

    private byte[] approvedPriceListWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("service_name / اسم الخدمة ★");
            header.createCell(1).setCellValue("service_code / الكود");
            header.createCell(2).setCellValue("contract_price / سعر العقد");
            header.createCell(3).setCellValue("medical_category_code / كود التصنيف الطبي");
            header.createCell(4).setCellValue("medical_category_name / اسم التصنيف الطبي");
            header.createCell(5).setCellValue("notes / ملاحظات");

            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("اقامه بسرير واحد بغرفه زوجيه / اليوم");
            first.createCell(1).setCellValue("AC-001");
            first.createCell(2).setCellValue("300");
            first.createCell(3).setCellValue("CAT-COV-INPATIENT");
            first.createCell(4).setCellValue("إيواء");
            first.createCell(5).setCellValue("القسم/المصدر: خدمات الايواء");

            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("كشف الطبيب عام");
            second.createCell(1).setCellValue("D1");
            second.createCell(2).setCellValue("15");
            second.createCell(3).setCellValue("CAT-DENT-ROUTINE");
            second.createCell(4).setCellValue("علاج الأسنان الروتيني");
            second.createCell(5).setCellValue("البيان باللاتيني: Diagnosis");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] workbookWithCategoryColumn() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("اسم الخدمة");
            header.createCell(1).setCellValue("الكود");
            header.createCell(2).setCellValue("السعر");
            header.createCell(3).setCellValue("التصنيف");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("جهاز التنفس الاصطناعي");
            row.createCell(1).setCellValue("WE-001");
            row.createCell(2).setCellValue("500");
            row.createCell(3).setCellValue("إيواء");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] workbookWithoutAnyClassificationColumn() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("اسم الخدمة");
            header.createCell(1).setCellValue("الكود");
            header.createCell(2).setCellValue("السعر");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("إنعاش القلب والرئتين");
            row.createCell(1).setCellValue("WE-003");
            row.createCell(2).setCellValue("800");

            workbook.write(out);
            return out.toByteArray();
        }
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
