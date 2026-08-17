package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpeningConsumptionImportServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberPolicyResolver memberPolicyResolver;
    @Mock private BenefitConsumptionEntryWriter entryWriter;
    @Mock private OpeningConsumptionImportPreviewTicketService ticketService;
    @Mock private JdbcTemplate jdbc;

    private OpeningConsumptionImportService service;
    private final LocalDate referenceDate = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        service = new OpeningConsumptionImportService(
                memberRepository, memberPolicyResolver, entryWriter, ticketService, jdbc);
    }

    private MockMultipartFile workbookWithRows(Object[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0); // header
            int r = 1;
            for (Object[] cells : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < cells.length; c++) {
                    if (cells[c] == null) continue;
                    var cell = row.createCell(c);
                    if (cells[c] instanceof Number n) cell.setCellValue(n.doubleValue());
                    else cell.setCellValue(cells[c].toString());
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return new MockMultipartFile("file", "opening.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        }
    }

    @Test
    @DisplayName("Preview reports a missing member and a negative amount as invalid rows, valid rows as valid")
    void parseAndPreview_ClassifiesRows() throws Exception {
        Member ok = Member.builder().id(1L).fullName("Ali").build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(ok));
        when(memberRepository.findById(2L)).thenReturn(Optional.empty());
        when(memberPolicyResolver.resolveFor(eq(ok), eq(referenceDate)))
                .thenReturn(Optional.of(BenefitPolicy.builder().id(9L).build()));
        when(ticketService.issue(any(), eq(referenceDate))).thenReturn("TOKEN-1");

        var file = workbookWithRows(
                new Object[]{1, 100.00, 1, "row A"},
                new Object[]{2, 50.00, 0, "row B - missing member"},
                new Object[]{1, -5.00, 0, "row C - negative amount"});

        var preview = service.parseAndPreview(file, referenceDate);

        assertThat(preview.getTotalRows()).isEqualTo(3);
        assertThat(preview.getValidRows()).isEqualTo(1);
        assertThat(preview.getInvalidRows()).isEqualTo(2);
        assertThat(preview.getPreviewToken()).isEqualTo("TOKEN-1");
        assertThat(preview.getRows().get(0).isValid()).isTrue();
        assertThat(preview.getRows().get(1).isValid()).isFalse();
        assertThat(preview.getRows().get(1).getErrors()).anyMatch(e -> e.contains("غير موجود"));
        assertThat(preview.getRows().get(2).isValid()).isFalse();
    }

    @Test
    @DisplayName("Execute aborts the whole file, with no writes, when any row is invalid on re-check")
    void executeConfirmedImport_AbortsWholeFileOnAnyInvalidRow() throws Exception {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        var file = workbookWithRows(new Object[]{1, 100.00, 0, null});

        assertThatThrownBy(() -> service.executeConfirmedImport(
                file, referenceDate, "TOKEN-1", "BATCH-1", "opening balances", "legacy system export"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("غير صالحة");

        verify(entryWriter, never()).appendOpeningConsumption(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Execute posts one movement per valid row, all under the same batch, scoped POLICY_GENERAL")
    void executeConfirmedImport_PostsOneOpeningConsumptionPerRow() throws Exception {
        Member member = Member.builder().id(1L).fullName("Ali").build();
        BenefitPolicy policy = BenefitPolicy.builder().id(9L).annualLimit(new BigDecimal("10000")).build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPolicyResolver.resolveFor(eq(member), eq(referenceDate))).thenReturn(Optional.of(policy));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any())).thenReturn(500L);

        var file = workbookWithRows(new Object[]{1, 100.00, 2, "carried forward"});

        var result = service.executeConfirmedImport(
                file, referenceDate, "TOKEN-1", "BATCH-1", "opening balances", "legacy system export");

        assertThat(result.getBatchId()).isEqualTo(500L);
        assertThat(result.getPostedRows()).isEqualTo(1);
        verify(entryWriter).appendOpeningConsumption(
                eq(policy), eq(1L), eq(500L), isNull(),
                eq(BenefitBucketConsumption.LimitScope.POLICY_GENERAL),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)),
                eq(new BigDecimal("100.0")), eq(2), anyString());
        verify(ticketService).consume("TOKEN-1", file, referenceDate);
        verify(entryWriter).flush();
    }

    @Test
    @DisplayName("A null reference date is refused outright -- no implicit today")
    void parseAndPreview_RequiresExplicitReferenceDate() throws Exception {
        var file = workbookWithRows(new Object[]{1, 100.00, 0, null});

        assertThatThrownBy(() -> service.parseAndPreview(file, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
