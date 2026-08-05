package com.waad.tba.modules.employer.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.dto.EmployerImportConfirmResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportPreviewResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportRowDto;
import com.waad.tba.modules.employer.dto.EmployerImportRowDto.Action;
import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.providercontract.service.PricingImportSessionCache;
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
 * Covers the employer bulk-import flow: order-independent column detection,
 * create-vs-update-vs-no-change matching against existing employers, and the
 * never-collapse-partial-success confirm() semantics.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployerImportServiceTest {

    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private EmployerImportRowProcessor rowProcessor;

    private EmployerImportService importService;

    @BeforeEach
    void setUp() {
        importService = new EmployerImportService(employerRepository, rowProcessor, new PricingImportSessionCache());
    }

    private MockMultipartFile buildWorkbook(String[] headers, String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("بيانات جهات العمل");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
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

    private static final String[] STANDARD_HEADERS =
            {"اسم جهة العمل ★", "رمز الجهة", "رقم الهاتف", "البريد الإلكتروني", "العنوان", "الحد السنوي"};

    @Test
    void previewRequiresANameColumnToBeDetected() throws Exception {
        MockMultipartFile file = buildWorkbook(
                new String[]{"رمز الجهة", "رقم الهاتف"},
                new String[][]{{"EMP-01", "0911234567"}});

        assertThatThrownBy(() -> importService.preview(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("اسم جهة العمل");
    }

    @Test
    void previewDetectsColumnsRegardlessOfOrder() throws Exception {
        // Shuffled order vs. STANDARD_HEADERS, and different (but recognizable) wording.
        String[] shuffledHeaders = {"Email", "Phone", "اسم الجهة", "العنوان", "رمز", "annual limit"};
        String[][] rows = {{"a@b.com", "0912345678", "شركة الاختبار", "طرابلس", "", "50000"}};
        MockMultipartFile file = buildWorkbook(shuffledHeaders, rows);
        when(employerRepository.findByNameIgnoreCase("شركة الاختبار")).thenReturn(Optional.empty());

        EmployerImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getValidCount()).isEqualTo(1);
        EmployerImportRowDto row = result.getRows().get(0);
        assertThat(row.getName()).isEqualTo("شركة الاختبار");
        assertThat(row.getEmail()).isEqualTo("a@b.com");
        assertThat(row.getPhone()).isEqualTo("0912345678");
        assertThat(row.getAddress()).isEqualTo("طرابلس");
        assertThat(row.getAnnualLimit()).isEqualByComparingTo("50000");
        assertThat(row.getAction()).isEqualTo(Action.CREATE);
    }

    @Test
    void previewMarksBrandNewEmployerAsCreate() throws Exception {
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS,
                new String[][]{{"شركة جديدة", null, null, null, null, null}});
        when(employerRepository.findByNameIgnoreCase("شركة جديدة")).thenReturn(Optional.empty());

        EmployerImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getRows().get(0).getAction()).isEqualTo(Action.CREATE);
        assertThat(result.getRows().get(0).isValid()).isTrue();
    }

    @Test
    void previewMatchesExistingEmployerByCodeAsNoChangeWhenEverythingIdentical() throws Exception {
        Employer existing = Employer.builder().id(1L).code("EMP-01").name("شركة قائمة")
                .phone("0911234567").email("a@b.com").address("طرابلس").active(true).build();
        when(employerRepository.findByCode("EMP-01")).thenReturn(Optional.of(existing));

        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS,
                new String[][]{{"شركة قائمة", "EMP-01", "0911234567", "a@b.com", "طرابلس", null}});

        EmployerImportPreviewResultDto result = importService.preview(file);

        EmployerImportRowDto row = result.getRows().get(0);
        assertThat(row.getAction()).isEqualTo(Action.NO_CHANGE);
        assertThat(row.getExistingEmployerId()).isEqualTo(1L);
        assertThat(row.isValid()).isTrue();
    }

    @Test
    void previewMatchesExistingEmployerByNameAsUpdateWhenAFieldDiffers() throws Exception {
        Employer existing = Employer.builder().id(2L).code("EMP-02").name("شركة أخرى")
                .phone("0911234567").email(null).address(null).active(true).build();
        when(employerRepository.findByNameIgnoreCase("شركة أخرى")).thenReturn(Optional.of(existing));

        // Phone changes from 091... to 092..., email newly supplied — code/address left blank.
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS,
                new String[][]{{"شركة أخرى", null, "0923456789", "new@b.com", null, null}});

        EmployerImportPreviewResultDto result = importService.preview(file);

        EmployerImportRowDto row = result.getRows().get(0);
        assertThat(row.getAction()).isEqualTo(Action.UPDATE);
        assertThat(row.getChangedFields()).containsExactlyInAnyOrder("الهاتف", "البريد الإلكتروني");
    }

    @Test
    void previewDoesNotTreatABlankCellAsAChange() throws Exception {
        Employer existing = Employer.builder().id(3L).code("EMP-03").name("شركة ب")
                .phone("0911234567").email("existing@b.com").address("بنغازي").active(true).build();
        when(employerRepository.findByNameIgnoreCase("شركة ب")).thenReturn(Optional.of(existing));

        // Every optional cell left blank — the row must not appear to "change" anything.
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS,
                new String[][]{{"شركة ب", null, null, null, null, null}});

        EmployerImportPreviewResultDto result = importService.preview(file);

        EmployerImportRowDto row = result.getRows().get(0);
        assertThat(row.getAction()).isEqualTo(Action.NO_CHANGE);
        assertThat(row.getChangedFields()).isEmpty();
    }

    @Test
    void previewRejectsDuplicateNameWithinTheSameFileForNewEmployers() throws Exception {
        when(employerRepository.findByNameIgnoreCase("مكرر")).thenReturn(Optional.empty());
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS, new String[][]{
                {"مكرر", null, null, null, null, null},
                {"مكرر", null, null, null, null, null}
        });

        EmployerImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRows().get(1).getErrors()).anyMatch(e -> e.contains("مكرر داخل نفس الملف"));
    }

    @Test
    void previewRejectsInvalidEmailFormat() throws Exception {
        when(employerRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS,
                new String[][]{{"شركة", null, null, "not-an-email", null, null}});

        EmployerImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("البريد الإلكتروني"));
    }

    @Test
    void previewRejectsNegativeAnnualLimit() throws Exception {
        when(employerRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS,
                new String[][]{{"شركة", null, null, null, null, "-100"}});

        EmployerImportPreviewResultDto result = importService.preview(file);

        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("سالباً"));
    }

    @Test
    void confirmSkipsInvalidRowsWithoutFailingTheBatch() throws Exception {
        when(employerRepository.findByNameIgnoreCase("صالحة")).thenReturn(Optional.empty());
        MockMultipartFile file = buildWorkbook(STANDARD_HEADERS, new String[][]{
                {"صالحة", null, null, null, null, null},
                {null, "EMP-99", "0911234567", null, null, null} // name missing but row not empty -> invalid, not skipped
        });
        EmployerImportPreviewResultDto preview = importService.preview(file);
        assertThat(preview.getTotalRows()).isEqualTo(2);
        assertThat(preview.getValidCount()).isEqualTo(1);

        EmployerResponseDto created = EmployerResponseDto.builder().id(10L).name("صالحة").code("EMP-10").build();
        when(rowProcessor.ensureEmployerAndPolicy(any(), any()))
                .thenReturn(new EmployerImportRowProcessor.Created(created, null, Action.CREATE, false));

        EmployerImportConfirmResultDto result = importService.confirm(preview.getSessionId());

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getSkippedInvalidCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void confirmThrowsWhenSessionExpiredOrUnknown() {
        assertThatThrownBy(() -> importService.confirm("does-not-exist"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("جلسة الاستيراد");
    }
}
