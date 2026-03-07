package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportResultDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberAttributeRepository;
import com.waad.tba.modules.member.repository.MemberImportErrorRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberExcelImportServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberAttributeRepository attributeRepository;
    @Mock
    private MemberImportLogRepository importLogRepository;
    @Mock
    private MemberImportErrorRepository importErrorRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private BenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private BarcodeGeneratorService barcodeGeneratorService;

    @InjectMocks
    private MemberExcelImportService service;

    private Employer employer;

    @BeforeEach
    void setUp() {
        service = new MemberExcelImportService(
                memberRepository,
                attributeRepository,
                importLogRepository,
                importErrorRepository,
                employerRepository,
                benefitPolicyRepository,
                authorizationService,
                new ObjectMapper(),
                barcodeGeneratorService);

        employer = Employer.builder().id(10L).code("EMP1").name("Employer One").active(true).build();

        when(employerRepository.findAll()).thenReturn(List.of(employer));
        when(benefitPolicyRepository.findAll()).thenReturn(List.of());
        when(authorizationService.getCurrentUser()).thenReturn(User.builder().id(1L).username("tester").build());

        when(employerRepository.findByNameIgnoreCase(eq("EMP1"))).thenReturn(Optional.empty());
        when(employerRepository.findByCode(eq("EMP1"))).thenReturn(Optional.of(employer));
        when(employerRepository.findByNameIgnoreCase(eq("BAD"))).thenReturn(Optional.empty());
        when(employerRepository.findByCode(eq("BAD"))).thenReturn(Optional.empty());

        when(importLogRepository.findByImportBatchId(anyString())).thenReturn(Optional.empty());
        when(importLogRepository.save(any(MemberImportLog.class))).thenAnswer(invocation -> {
            MemberImportLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(100L);
            }
            return log;
        });

        doNothing().when(importErrorRepository).deleteByImportLogId(anyLong());
        when(memberRepository.findByCivilId(anyString())).thenReturn(Optional.empty());
        when(barcodeGeneratorService.generateForPrincipal()).thenReturn("WAD-2026-00000001", "WAD-2026-00000002");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            if (member.getId() == null) {
                member.setId(200L);
            }
            return member;
        });
    }

    @Test
    void parsePreview_validFile_shouldReturnValidRows() throws Exception {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "national_id", "start_date", "policy_number" },
                new String[] { "Ali Hasan", "EMP1", "1234567890", "2026-02-01", "POL-1" }));

        MemberImportPreviewDto preview = service.parseAndPreview(file, null, 0);

        assertThat(preview.getTotalRows()).isEqualTo(1);
        assertThat(preview.getValidRows()).isEqualTo(1);
        assertThat(preview.getInvalidRows()).isEqualTo(0);
        assertThat(preview.isCanProceed()).isTrue();
    }

    @Test
    void parsePreview_headerOnly_shouldFailValidationWithZeroValidRows() throws Exception {
        MockMultipartFile file = buildExcelFile(Collections.singletonList(
                new String[] { "full_name", "employer", "national_id", "start_date", "policy_number" }));

        MemberImportPreviewDto preview = service.parseAndPreview(file, null, 0);

        assertThat(preview.getTotalRows()).isEqualTo(0);
        assertThat(preview.getValidRows()).isEqualTo(0);
        assertThat(preview.isCanProceed()).isFalse();
    }

    @Test
    void parsePreview_invalidEmployer_shouldReturnRowLevelError() throws Exception {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "national_id", "start_date", "policy_number" },
                new String[] { "Mona Saad", "BAD", "99999", "2026-02-01", "POL-1" }));

        MemberImportPreviewDto preview = service.parseAndPreview(file, null, 0);

        assertThat(preview.getValidRows()).isEqualTo(0);
        assertThat(preview.getInvalidRows()).isEqualTo(1);
        assertThat(preview.getErrors())
                .anyMatch(error -> "employer".equals(error.getField())
                        && error.getMessage().contains("Employer not found"));
    }

    @Test
    void executeImport_mixedValidAndInvalidRows_shouldAllowPartialSuccess() throws Exception {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "national_id", "start_date", "policy_number" },
                new String[] { "Valid Member", "EMP1", "11111", "2026-02-01", "POL-1" },
                new String[] { "Invalid Employer", "BAD", "22222", "2026-02-01", "POL-2" }));

        MemberImportResultDto result = service.executeImport(file, "batch-test-1", null, null, 0);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("PARTIAL");
    }

    private MockMultipartFile buildExcelFile(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Members");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex);
                String[] values = rows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
                    row.createCell(cellIndex).setCellValue(values[cellIndex]);
                }
            }
            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "members.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray());
        }
    }
}
