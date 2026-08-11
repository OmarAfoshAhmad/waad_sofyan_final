package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.service.ExcelParserService;
import com.waad.tba.common.excel.service.ExcelTemplateService;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * Characterization tests for {@code importFromExcel} -- built BEFORE any
 * attempt to decompose that ~600-line method (member-closure follow-up),
 * because it had zero test coverage despite touching identity-adjacent data
 * (card numbers, employer assignment, principal/dependent linking). These
 * tests exist to make a future decomposition provably behavior-preserving,
 * not to exhaustively specify every edge case of the import algorithm --
 * the multi-pass dependent-linking logic (fuzzy employer matching, base
 * card number resolution, relationship inference) is deliberately not
 * covered yet and should be extended here before that part is ever touched.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberExcelTemplateServiceTest {

    @Mock
    private ExcelTemplateService templateService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private BarcodeGeneratorService barcodeGeneratorService;
    @Mock
    private BenefitPolicyRepository benefitPolicyRepository;

    // Real instance (not @Mock) wrapping the mocked repository: the dependent-linking
    // tests exercise real card-number regex logic (isPrincipalCardNumber,
    // extractBaseCardNumber, generateForDependent's suffix format), which a bare mock
    // would fake into always-false/null and hide the exact bug this class was built to
    // catch (see class javadoc).
    private CardNumberGeneratorService cardNumberGeneratorService;

    private MemberExcelTemplateService service;
    private Employer employer;

    @BeforeEach
    void setUp() {
        // Real, dependency-free parser -- same convention MemberExcelImportServiceTest
        // uses for MemberImportParser: parsing itself is part of what's under test.
        ExcelParserService parserService = new ExcelParserService();
        cardNumberGeneratorService = spy(new CardNumberGeneratorService(memberRepository));

        service = new MemberExcelTemplateService(
                templateService, parserService, memberRepository, employerRepository,
                barcodeGeneratorService, cardNumberGeneratorService, benefitPolicyRepository);

        employer = Employer.builder().id(10L).code("EMP1").name("Employer One").active(true).build();

        when(employerRepository.findByActiveTrue()).thenReturn(List.of(employer));
        BenefitPolicy activePolicy = BenefitPolicy.builder()
                .id(20L).name("Active Policy").policyCode("POL-1").employer(employer)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1))
                .status(BenefitPolicyStatus.ACTIVE).build();
        when(benefitPolicyRepository.findActiveEffectivePolicyForEmployer(employer.getId(), LocalDate.now()))
                .thenReturn(java.util.Optional.of(activePolicy));
        when(memberRepository.findAllCardNumbers()).thenReturn(List.of());
        when(memberRepository.findActivePrincipalsByEmployerId(anyLong())).thenReturn(List.of());
        // Only the principal-generation entry point is stubbed (it would otherwise hit
        // real employer/date-based logic not relevant here); isPrincipalCardNumber,
        // extractBaseCardNumber and generateForDependent run for real on the spy.
        doReturn("CARD0001", "CARD0002", "CARD0003")
                .when(cardNumberGeneratorService).generateUniqueForPrincipal(any(Member.class));
        when(memberRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Member> batch = invocation.getArgument(0);
            long nextId = 500L;
            for (Member m : batch) {
                if (m.getId() == null) {
                    m.setId(nextId++);
                }
            }
            return batch;
        });
    }

    @Test
    @DisplayName("A single valid principal row is created and reflected in the summary")
    void importFromExcel_singlePrincipalRow_createsOneMember() {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer" },
                new String[] { "Ali Hasan", "EMP1" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSummary().getCreated()).isEqualTo(1);
        assertThat(result.getSummary().getPrincipalsCreated()).isEqualTo(1);
        assertThat(result.getSummary().getRejected()).isZero();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Missing the full_name column fails closed before any row is processed")
    void importFromExcel_missingFullNameColumn_returnsErrorWithoutProcessing() {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "employer" },
                new String[] { "EMP1" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getColumnName()).isEqualTo("TEMPLATE_HEADER");
        assertThat(result.getSummary().getCreated()).isZero();
    }

    @Test
    @DisplayName("Two principal rows with the same name in the same employer: the second is skipped in-file, not duplicated")
    void importFromExcel_duplicatePrincipalInFile_skipsSecondOccurrence() {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer" },
                new String[] { "Ali Hasan", "EMP1" },
                new String[] { "Ali Hasan", "EMP1" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.getSummary().getPrincipalsCreated()).isEqualTo(1);
        assertThat(result.getSummary().getPrincipalsSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("An empty data sheet (header only) processes zero rows without error")
    void importFromExcel_headerOnly_processesNothing() {
        MockMultipartFile file = buildExcelFile(Collections.singletonList(
                new String[] { "full_name", "employer" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.getSummary().getTotalRows()).isZero();
        assertThat(result.getSummary().getCreated()).isZero();
    }

    @Test
    @DisplayName("A dependent row referencing its principal's card number is linked via the in-session cache")
    void importFromExcel_dependentRowWithPrincipalCardReference_createsLinkedDependent() {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "card_number" },
                new String[] { "Ali Hasan", "EMP1", "" },
                new String[] { "Sami Hasan", "EMP1", "CARD0001S1" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSummary().getPrincipalsCreated()).isEqualTo(1);
        assertThat(result.getSummary().getDependentsCreated()).isEqualTo(1);
        assertThat(result.getSummary().getRejected()).isZero();
    }

    @Test
    @DisplayName("A dependent row whose base card number resolves to nothing falls back to the immediately preceding principal row")
    void importFromExcel_dependentRowWithUnresolvableBaseCard_fallsBackToLastSeenPrincipal() {
        // "ZZZZ9S1" extracts to base "ZZZZ9", which matches neither the in-session cache
        // nor the DB (both unstubbed/empty) -- only the lastSeenPrincipal fallback can
        // resolve this row, since it directly follows the principal row in the file.
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "card_number" },
                new String[] { "Ali Hasan", "EMP1", "" },
                new String[] { "Sami Hasan", "EMP1", "ZZZZ9S1" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSummary().getDependentsCreated()).isEqualTo(1);
        assertThat(result.getSummary().getRejected()).isZero();
    }

    @Test
    @DisplayName("Two dependent rows under the same principal with the same name: the second is skipped as an in-file duplicate")
    void importFromExcel_duplicateDependentInFile_skipsSecondOccurrence() {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "card_number" },
                new String[] { "Ali Hasan", "EMP1", "" },
                new String[] { "Sami Hasan", "EMP1", "CARD0001S1" },
                new String[] { "Sami Hasan", "EMP1", "CARD0001S2" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.getSummary().getDependentsCreated()).isEqualTo(1);
        assertThat(result.getSummary().getDependentsSkipped()).isEqualTo(1);
        assertThat(result.getSummary().getSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("A dependent row with no resolvable principal (first row in file, unknown base card) is rejected with a clear error")
    void importFromExcel_dependentRowWithNoResolvablePrincipal_isRejected() {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "full_name", "employer", "card_number" },
                new String[] { "Orphan Dependent", "EMP1", "ZZZZ9S1" }));

        ExcelImportResult result = service.importFromExcel(file, employer.getId());

        assertThat(result.getSummary().getRejected()).isEqualTo(1);
        assertThat(result.getErrors())
                .anyMatch(error -> "card_number".equals(error.getColumnName())
                        && error.getMessageEn().contains("Principal member not found for dependent"));
    }

    private MockMultipartFile buildExcelFile(List<String[]> rows) {
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
                    "file", "members.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
