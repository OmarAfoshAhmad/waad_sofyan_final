package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.ContractImportConfirmResultDto;
import com.waad.tba.modules.providercontract.dto.ContractImportPreviewResultDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the two-stage bulk-import flow for NEW provider contracts:
 * preview() must validate structurally + against business rules without
 * persisting anything, and confirm() must persist only the rows that were
 * valid at preview time — each row independently, per the
 * never-collapse-partial-success requirement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderContractImportServiceTest {

    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private ProviderContractRepository contractRepository;
    @Mock
    private ProviderContractService contractService;

    private ProviderContractImportService importService;

    private static final int COL_PROVIDER_NAME = 0;
    private static final int COL_PRICING_SCOPE = 1;
    private static final int COL_EMPLOYER_NAME = 2;
    private static final int COL_CONTRACT_CODE = 3;
    private static final int COL_STATUS = 4;
    private static final int COL_START_DATE = 8;

    @BeforeEach
    void setUp() {
        importService = new ProviderContractImportService(
                providerRepository, employerRepository, contractRepository, contractService,
                new PricingImportSessionCache());
    }

    private MockMultipartFile buildWorkbook(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("بيانات العقود");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 19; i++) header.createCell(i).setCellValue("h" + i);

            int r = 1;
            for (String[] rowValues : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < rowValues.length; c++) {
                    if (rowValues[c] != null) row.createCell(c).setCellValue(rowValues[c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "contracts.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private String[] row(String providerName, String scope, String employerName, String contractCode,
            String status, String startDate) {
        String[] r = new String[19];
        r[COL_PROVIDER_NAME] = providerName;
        r[COL_PRICING_SCOPE] = scope;
        r[COL_EMPLOYER_NAME] = employerName;
        r[COL_CONTRACT_CODE] = contractCode;
        r[COL_STATUS] = status;
        r[COL_START_DATE] = startDate;
        return r;
    }

    @Test
    void previewMarksRowValidWhenProviderExistsAndRequiredFieldsPresent() throws Exception {
        Provider provider = Provider.builder().id(5L).name("مستشفى الأمل").build();
        when(providerRepository.findByNameIgnoreCase("مستشفى الأمل")).thenReturn(Optional.of(provider));
        when(contractRepository.existsByContractCode(any())).thenReturn(false);

        MockMultipartFile file = buildWorkbook(new String[][] {
                row("مستشفى الأمل", "GLOBAL", null, "", "DRAFT", "2026-01-01")
        });

        ContractImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getInvalidCount()).isZero();
        assertThat(result.getRows().get(0).isValid()).isTrue();
        assertThat(result.getRows().get(0).getProviderId()).isEqualTo(5L);
    }

    @Test
    void previewRejectsRowWhenProviderDoesNotExist() throws Exception {
        when(providerRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

        MockMultipartFile file = buildWorkbook(new String[][] {
                row("مقدم خدمة غير موجود", "GLOBAL", null, "", "DRAFT", "2026-01-01")
        });

        ContractImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("غير موجود"));
    }

    @Test
    void previewRejectsRowMissingStartDate() throws Exception {
        Provider provider = Provider.builder().id(5L).name("مستشفى الأمل").build();
        when(providerRepository.findByNameIgnoreCase("مستشفى الأمل")).thenReturn(Optional.of(provider));

        MockMultipartFile file = buildWorkbook(new String[][] {
                row("مستشفى الأمل", "GLOBAL", null, "", "DRAFT", null)
        });

        ContractImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("تاريخ البدء"));
    }

    @Test
    void previewRejectsDuplicateContractCodeWithinSameFile() throws Exception {
        Provider provider = Provider.builder().id(5L).name("مستشفى الأمل").build();
        when(providerRepository.findByNameIgnoreCase("مستشفى الأمل")).thenReturn(Optional.of(provider));
        when(contractRepository.existsByContractCode("DUP-1")).thenReturn(false);

        MockMultipartFile file = buildWorkbook(new String[][] {
                row("مستشفى الأمل", "GLOBAL", null, "DUP-1", "DRAFT", "2026-01-01"),
                row("مستشفى الأمل", "GLOBAL", null, "DUP-1", "DRAFT", "2026-01-02")
        });

        ContractImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRows().get(1).getErrors()).anyMatch(e -> e.contains("مكرر"));
    }

    @Test
    void previewRejectsEmployerSpecificScopeWithoutEmployer() throws Exception {
        Provider provider = Provider.builder().id(5L).name("مستشفى الأمل").build();
        when(providerRepository.findByNameIgnoreCase("مستشفى الأمل")).thenReturn(Optional.of(provider));

        MockMultipartFile file = buildWorkbook(new String[][] {
                row("مستشفى الأمل", "EMPLOYER_SPECIFIC", null, "", "DRAFT", "2026-01-01")
        });

        ContractImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("جهة العمل"));
    }

    @Test
    void confirmPersistsOnlyValidRowsAndSkipsInvalidOnesWithoutFailingBatch() throws Exception {
        Provider provider = Provider.builder().id(5L).name("مستشفى الأمل").build();
        when(providerRepository.findByNameIgnoreCase("مستشفى الأمل")).thenReturn(Optional.of(provider));
        when(providerRepository.findByNameIgnoreCase("غير موجود")).thenReturn(Optional.empty());

        MockMultipartFile file = buildWorkbook(new String[][] {
                row("مستشفى الأمل", "GLOBAL", null, "", "DRAFT", "2026-01-01"),
                row("غير موجود", "GLOBAL", null, "", "DRAFT", "2026-01-01")
        });

        ContractImportPreviewResultDto preview = importService.preview(file);
        assertThat(preview.getValidCount()).isEqualTo(1);
        assertThat(preview.getInvalidCount()).isEqualTo(1);

        ProviderContractResponseDto created = ProviderContractResponseDto.builder()
                .id(1L).contractCode("C-AUTO-1").status(ContractStatus.DRAFT).build();
        when(contractService.create(any())).thenReturn(created);

        ContractImportConfirmResultDto confirmResult = importService.confirm(preview.getSessionId());

        assertThat(confirmResult.getTotalRows()).isEqualTo(2);
        assertThat(confirmResult.getSuccessCount()).isEqualTo(1);
        assertThat(confirmResult.getSkippedInvalidCount()).isEqualTo(1);
        assertThat(confirmResult.getFailedCount()).isEqualTo(1);
    }

    @Test
    void confirmThrowsWhenSessionExpiredOrUnknown() {
        assertThatThrownBy(() -> importService.confirm("unknown-session"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("جلسة الاستيراد");
    }

    @Test
    void previewRejectsProviderImportTemplateInsteadOfParsingItsMetadataAsContracts() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Metadata").createRow(1).createCell(0)
                    .setCellValue("Module / النموذج:");
            workbook.createSheet("Data").createRow(0).createCell(0)
                    .setCellValue("provider_name");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "providers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

            assertThatThrownBy(() -> importService.preview(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ملف استيراد مقدمي الخدمة")
                    .hasMessageContaining("شاشة مقدمي الخدمة");
        }
    }

    @Test
    void previewRejectsUnknownWorkbookInsteadOfFallingBackToFirstSheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Wrong Sheet").createRow(0).createCell(0).setCellValue("not a contract template");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "wrong.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

            assertThatThrownBy(() -> importService.preview(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("بيانات العقود")
                    .hasMessageContaining("نزّل قالب العقود");
        }
    }
}
