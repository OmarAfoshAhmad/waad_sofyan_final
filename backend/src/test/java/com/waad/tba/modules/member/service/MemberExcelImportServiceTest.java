package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
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
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;

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
    @Mock
    private CardNumberGeneratorService cardNumberGeneratorService;
    @Mock
    private VisitRepository visitRepository;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private PreAuthorizationRepository preAuthorizationRepository;
    @Mock
    private MemberImportAuditRecorder auditRecorder;
    @Mock
    private com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository policyAssignmentRepository;
    @Mock
    private com.waad.tba.modules.member.repository.MemberStatusHistoryRepository statusHistoryRepository;
    @Mock
    private com.waad.tba.modules.member.repository.MemberHardDeleteAuditRepository hardDeleteAuditRepository;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate statusTransitionJdbcTemplate;

    @InjectMocks
    private MemberExcelImportService service;

    private Employer employer;

    @BeforeEach
    void setUp() {
        MemberImportParser parser = new MemberImportParser();
        MemberImportMapper mapper = new MemberImportMapper(parser);
        MemberPolicyResolver policyResolverForStatus = new MemberPolicyResolver(
                policyAssignmentRepository, benefitPolicyRepository);
        MemberStatusTransitionService statusTransitionService = new MemberStatusTransitionService(
                memberRepository, statusHistoryRepository, hardDeleteAuditRepository, benefitPolicyRepository,
                statusTransitionJdbcTemplate, policyResolverForStatus);
        MemberPolicyResolver memberPolicyResolver = new MemberPolicyResolver(
                policyAssignmentRepository, benefitPolicyRepository);
        MemberImportRowProcessor rowProcessor = new MemberImportRowProcessor(
                parser, employerRepository, benefitPolicyRepository, barcodeGeneratorService,
                cardNumberGeneratorService, statusTransitionService);

        service = new MemberExcelImportService(
                memberRepository,
                attributeRepository,
                importLogRepository,
                importErrorRepository,
                employerRepository,
                benefitPolicyRepository,
                authorizationService,
                new ObjectMapper(),
                parser,
                mapper,
                rowProcessor,
                barcodeGeneratorService,
                auditRecorder,
                statusTransitionService,
                memberPolicyResolver,
                policyAssignmentRepository,
                visitRepository,
                claimRepository,
                preAuthorizationRepository);

        employer = Employer.builder().id(10L).code("EMP1").name("Employer One").active(true).build();

        BenefitPolicy activePolicy = BenefitPolicy.builder()
                .id(20L).name("Active Policy").policyCode("POL-1").employer(employer)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1))
                .status(BenefitPolicyStatus.ACTIVE).build();

        when(employerRepository.findAll()).thenReturn(List.of(employer));
        when(benefitPolicyRepository.findAll()).thenReturn(List.of(activePolicy));
        when(benefitPolicyRepository.findActiveEffectivePolicyForEmployer(10L, LocalDate.now()))
                .thenReturn(Optional.of(activePolicy));
        when(authorizationService.getCurrentUser()).thenReturn(User.builder().id(1L).username("tester").build());

        when(employerRepository.findByNameIgnoreCase(eq("EMP1"))).thenReturn(Optional.empty());
        when(employerRepository.findByCode(eq("EMP1"))).thenReturn(Optional.of(employer));
        when(employerRepository.findByNameIgnoreCase(eq("BAD"))).thenReturn(Optional.empty());
        when(employerRepository.findByCode(eq("BAD"))).thenReturn(Optional.empty());

        when(importLogRepository.findByImportBatchId(anyString())).thenReturn(Optional.empty());
        when(importLogRepository.findByImportScopeHashAndStatus(anyString(), eq(MemberImportLog.ImportStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(importLogRepository.save(any(MemberImportLog.class))).thenAnswer(invocation -> {
            MemberImportLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(100L);
            }
            return log;
        });
        when(importLogRepository.saveAndFlush(any(MemberImportLog.class))).thenAnswer(invocation -> {
            MemberImportLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(100L);
            }
            return log;
        });
        when(importLogRepository.findById(anyLong())).thenAnswer(invocation ->
                Optional.of(MemberImportLog.builder().id(invocation.getArgument(0)).importBatchId("test-batch")
                        .startedAt(LocalDateTime.now()).build()));
        when(auditRecorder.markStarted(anyString(), any(), anyLong(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(100L);

        doNothing().when(importErrorRepository).deleteByImportLogId(anyLong());
        when(policyAssignmentRepository.findMemberIdsWithAnyAssignment(any())).thenReturn(List.of());
        when(policyAssignmentRepository.findByMemberIdAndAssignmentEndDateIsNull(anyLong()))
                .thenReturn(Optional.empty());
        when(policyAssignmentRepository.save(any(com.waad.tba.modules.member.entity.MemberPolicyAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(barcodeGeneratorService.generateForPrincipal()).thenReturn("WAD-2026-00000001", "WAD-2026-00000002");
        when(cardNumberGeneratorService.generateUniqueForPrincipal(any(Member.class))).thenReturn("CARD-0001",
                "CARD-0002");
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
    void parsePreview_smartColumnMapping_shouldSuccessfullyMapColumns() throws Exception {
        MockMultipartFile file = buildExcelFile(List.of(
                new String[] { "  اسم المستفيد  * ", " جهة العمل (الشركه) ", "الرقم الوطني للمستفيد", "تاريخ البدء", "رقم العقد" },
                new String[] { "Ali Hasan", "EMP1", "1234567890", "2026-02-01", "POL-1" }));

        MemberImportPreviewDto preview = service.parseAndPreview(file, null, 0);

        assertThat(preview.getTotalRows()).isEqualTo(1);
        assertThat(preview.getValidRows()).isEqualTo(1);
        assertThat(preview.getInvalidRows()).isEqualTo(0);
        assertThat(preview.isCanProceed()).isTrue();
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

    @Test
    void parseCardNumber_shouldCorrectlyParsePrefixAndRelationship() {
        var card1 = service.parseCardNumber("JFZ20255001S1");
        assertThat(card1.relationship).isEqualTo(com.waad.tba.modules.member.entity.Member.Relationship.SON);
        assertThat(card1.parentCardNumber).isEqualTo("JFZ20255001");

        var card2 = service.parseCardNumber("JFZ20255005");
        assertThat(card2.relationship).isNull();
        assertThat(card2.parentCardNumber).isNull();

        var card3 = service.parseCardNumber("JFZ202531982A");
        assertThat(card3.relationship).isNull();
        assertThat(card3.parentCardNumber).isNull();

        var card4 = service.parseCardNumber("LCC-2025-12345-D1");
        assertThat(card4.relationship).isEqualTo(com.waad.tba.modules.member.entity.Member.Relationship.DAUGHTER);
        assertThat(card4.parentCardNumber).isEqualTo("LCC-2025-12345");
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
