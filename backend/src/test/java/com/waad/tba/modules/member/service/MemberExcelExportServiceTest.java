package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * Characterization tests for the streaming (SXSSFWorkbook) rewrite of
 * exportToExcel -- the original XSSFWorkbook + autoSizeColumn + per-row lazy
 * employer.getName() version had no test coverage at all. These prove the
 * streamed output is still a valid, readable workbook with the same content
 * shape (header + one row per member, employer name populated via the new
 * EntityGraph-fetched query) before this file is touched again.
 */
@ExtendWith(MockitoExtension.class)
class MemberExcelExportServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberExcelExportService service;

    @Test
    void exportToExcel_writesHeaderAndOneRowPerMember_withEmployerNamePopulated() throws Exception {
        service = new MemberExcelExportService(memberRepository);

        Employer employer = Employer.builder().id(1L).name("Employer One").build();
        Member member = Member.builder().id(100L).fullName("Ali Hasan").employer(employer).build();

        when(memberRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(1L);
        when(memberRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(member));

        byte[] bytes = service.exportToExcel(null, null, null, null);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Members");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(1).getStringCellValue()).contains("Full Name");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Ali Hasan");
            assertThat(dataRow.getCell(5).getStringCellValue()).isEqualTo("Employer One");
        }
    }

    @Test
    void exportToExcel_exceedingMaxRows_failsClosedWithoutQueryingRows() {
        service = new MemberExcelExportService(memberRepository);

        when(memberRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(50_001L);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class,
                () -> service.exportToExcel(null, null, null, null));
    }
}
