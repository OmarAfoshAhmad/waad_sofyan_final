package com.waad.tba.modules.provider.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The providers screen offered "استيراد من إكسل" and no way out. This export
 * exists to close that, and its only real contract is that the file it produces
 * is the file the importer accepts -- same columns, same order, same headers.
 *
 * An export whose columns drift from the template it claims to match is worse
 * than no export at all: it looks importable, and either fails halfway through
 * a real import or, quietly, lands one column's values in another column's
 * field. So this test does not check the export in isolation; it compares the
 * produced header row against the header row of the template the importer
 * itself generates, cell by cell.
 *
 * It also pins the one column that must never carry data: the template has an
 * initial_password column because importing a provider can create its portal
 * user, and a spreadsheet leaving the building is the last place a credential
 * belongs. The system holds only a hash, so there is nothing truthful to write
 * there anyway.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderExcelExportRoundTripIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ProviderExcelExportService exportService;
    @Autowired private ProviderExcelTemplateService templateService;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String providerName;

    @BeforeEach
    void seedAProviderWithAContract() {
        String s = suffix();
        providerName = "مقدم التصدير " + s;

        long providerId = jdbc.queryForObject(
                "INSERT INTO providers (name, license_number, provider_type, city, phone, email,"
                        + " address, network_status, allow_all_employers, active)"
                        + " VALUES (?, ?, 'PHARMACY', 'بنغازي', '0910000000', ?, 'شارع الاختبار',"
                        + " 'IN_NETWORK', true, true) RETURNING id",
                Long.class, providerName, "EXP-LIC-" + s, "exp" + s + "@waad.ly");

        jdbc.update("INSERT INTO provider_contracts (contract_code, contract_number, provider_id,"
                        + " start_date, end_date, status, discount_percent, discount_before_rejection, active)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 15.00, true, true)",
                "EXP-CON-" + s, "EXP-NUM-" + s, providerId,
                LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6));
    }

    /**
     * Reads the header the IMPORTER would read: ExcelParserService.getDataSheet
     * takes the sheet named "Data" before anything else, and treats its row 0 as
     * the header. Comparing sheet 0 by index would compare the template's title
     * banner instead -- which is exactly what this test caught the first time.
     */
    private static List<String> importableHeaderOf(byte[] workbookBytes) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheet("Data");
            assertThat(sheet).as("the importer resolves the sheet named Data").isNotNull();
            Row header = sheet.getRow(0);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                names.add(header.getCell(i) == null ? "" : header.getCell(i).getStringCellValue().trim());
            }
            return names;
        }
    }

    @Test
    @DisplayName("the exported header row is the import template's header row, cell for cell")
    void exportedHeadersMatchTheImportTemplateExactly() throws Exception {
        List<String> exported = importableHeaderOf(exportService.exportProviders());
        List<String> template = importableHeaderOf(templateService.generateTemplate());

        assertThat(exported)
                .as("an export that does not match the template it claims to match is a trap: "
                        + "it looks importable and lands values in the wrong fields")
                .containsExactlyElementsOf(template);
    }

    @Test
    @DisplayName("the exported row carries the provider and its contract")
    void theExportedRowCarriesProviderAndContract() throws Exception {
        byte[] bytes = exportService.exportProviders();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Data");
            Row row = null;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row candidate = sheet.getRow(i);
                if (candidate != null && candidate.getCell(0) != null
                        && providerName.equals(candidate.getCell(0).getStringCellValue())) {
                    row = candidate;
                    break;
                }
            }
            assertThat(row).as("the seeded provider must appear in the export").isNotNull();

            assertThat(row.getCell(1).getStringCellValue()).as("نوع المقدم").isEqualTo("PHARMACY");
            assertThat(row.getCell(2).getStringCellValue()).as("المدينة").isEqualTo("بنغازي");
            assertThat(row.getCell(9).getStringCellValue()).as("الشبكة").isEqualTo("داخل الشبكة");
            assertThat(row.getCell(10).getStringCellValue()).as("شبكة عامة").isEqualTo("نعم");

            // The contract half of the row -- the part the user asked for.
            assertThat(row.getCell(11).getStringCellValue())
                    .as("تاريخ بداية العقد").isEqualTo(LocalDate.now().minusMonths(6).toString());
            assertThat(row.getCell(12).getStringCellValue()).as("المدة بالأشهر").isEqualTo("12");
            assertThat(row.getCell(13).getStringCellValue()).as("نسبة الخصم").isEqualTo("15");
            assertThat(row.getCell(15).getStringCellValue()).as("الحالة").isEqualTo("ACTIVE");
        }
    }

    @Test
    @DisplayName("no exported row carries a password, ever")
    void theInitialPasswordColumnIsAlwaysEmpty() throws Exception {
        byte[] bytes = exportService.exportProviders();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Data");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(8) == null) {
                    continue;
                }
                assertThat(row.getCell(8).getStringCellValue())
                        .as("row %d: the system stores only a password hash, so there is nothing "
                                + "truthful to write here -- and a spreadsheet that leaves the "
                                + "building is the last place a credential belongs", i)
                        .isEmpty();
            }
        }
    }
}
