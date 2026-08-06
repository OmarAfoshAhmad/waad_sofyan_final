package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.waad.tba.modules.benefitpolicy.dto.BenefitOpeningBalanceImportDto.Preview;
import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class BenefitOpeningBalanceImportServiceTest {
    @Mock MemberRepository memberRepository;
    @Mock BenefitPolicyRepository policyRepository;
    @Mock BenefitLimitBucketRepository bucketRepository;
    @Mock BenefitBucketAdjustmentRepository adjustmentRepository;
    @Mock BenefitBucketUsageService usageService;
    @Mock AuthorizationService authorizationService;

    private BenefitOpeningBalanceImportService service;
    private BenefitPolicy policy;
    private BenefitLimitBucket bucket;

    @BeforeEach
    void setUp() {
        service = new BenefitOpeningBalanceImportService(memberRepository, policyRepository, bucketRepository,
                adjustmentRepository, usageService, authorizationService);
        policy = BenefitPolicy.builder().id(1L).policyCode("POL-1").name("وثيقة الاختبار")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).build();
        bucket = BenefitLimitBucket.builder().id(20L).policy(policy).code("DENTAL")
                .nameAr("الأسنان").amountLimit(new BigDecimal("600.00"))
                .periodType(LimitPeriodType.ANNUAL).active(true).build();
        Member member = Member.builder().id(10L).cardNumber("CARD-1").fullName("مستفيد اختبار")
                .benefitPolicy(policy).build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(memberRepository.findByCardNumber("CARD-1")).thenReturn(Optional.of(member));
        when(bucketRepository.findByPolicyIdAndCodeIgnoreCase(1L, "DENTAL")).thenReturn(Optional.of(bucket));
        when(adjustmentRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(usageService.totals(eq(10L), eq(20L), any(), any(), isNull()))
                .thenReturn(new BenefitBucketUsageService.UsageTotals(BigDecimal.ZERO, 0, 0L));
    }

    @Test
    void derivesOpeningUsageFromLimitMinusRemaining() throws Exception {
        Preview preview = service.preview(file("600", "0", "600"), 1L, "BATCH-1");

        assertThat(preview.invalidRows()).isZero();
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.totalOpeningUsage()).isEqualByComparingTo("600.00");
        assertThat(preview.rows().get(0).usedAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void rejectsInconsistentUsedAmount() throws Exception {
        Preview preview = service.preview(file("600", "100", "400"), 1L, "BATCH-1");

        assertThat(preview.invalidRows()).isEqualTo(1);
        assertThat(preview.rows().get(0).errors())
                .anyMatch(message -> message.contains("لا يساوي السقف الأصلي ناقص المتبقي"));
    }

    private MockMultipartFile file(String limit, String remaining, String used) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("الأرصدة الافتتاحية");
            var header = sheet.createRow(0);
            String[] headers = {"رقم البطاقة", "رمز السقف", "تاريخ الرصيد", "السقف الأصلي",
                    "المبلغ المستخدم", "الرصيد المتبقي"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("CARD-1"); row.createCell(1).setCellValue("DENTAL");
            row.createCell(2).setCellValue("05-08-2026"); row.createCell(3).setCellValue(limit);
            row.createCell(4).setCellValue(used); row.createCell(5).setCellValue(remaining);
            workbook.write(output);
            return new MockMultipartFile("file", "opening.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
