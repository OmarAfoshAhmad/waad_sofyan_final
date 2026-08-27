package com.waad.tba.modules.member.service;

import com.waad.tba.common.excel.dto.ExcelLookupData;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn.ColumnType;
import com.waad.tba.common.excel.service.ExcelTemplateService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Members Excel Template Generator.
 *
 * Import used to live here too (a two-pass, non-transactional pipeline);
 * it was removed because the frontend never called it -- only
 * MemberExcelImportService's /preview + /execute pipeline is live, and it
 * has the atomicity/audit/idempotency guarantees this one never did. See
 * MemberExcelImportService for the real import path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberExcelTemplateService {

    private final ExcelTemplateService templateService;
    private final EmployerRepository employerRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate Members import template
     */
    public byte[] generateTemplate() throws IOException {
        log.info("[MemberTemplate] Generating Excel template");

        List<ExcelTemplateColumn> columns = buildColumnDefinitions();
        List<ExcelLookupData> lookups = buildLookupSheets();

        return templateService.generateTemplate("Members / الأعضاء", columns, lookups);
    }

    private List<ExcelTemplateColumn> buildColumnDefinitions() {
        return List.of(
                ExcelTemplateColumn.builder()
                        .name("full_name")
                        .nameAr("الاسم الكامل")
                        .type(ColumnType.TEXT)
                        .required(true)
                        .example("أحمد محمد علي")
                        .description("Full name (mandatory)")
                        .descriptionAr("الاسم الكامل للمستفيد (إجباري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("employer")
                        .nameAr("جهة العمل")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("المنطقة الحرة جليانة")
                        .description("Employer Name (optional, defaults to selected employer)")
                        .descriptionAr("جهة العمل أو الشركة التابع لها (اختياري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("relationship")
                        .nameAr("الصلة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("ابن")
                        .description("Relationship for dependents (optional for principal)")
                        .descriptionAr("صلة القرابة (موظف، ابن، ابنة، زوجة، زوج، أب، أم)")
                        .allowedValues(List.of("موظف", "رئيسي", "ابن", "ابنة", "زوجة", "زوج", "أب", "أم", "PRINCIPAL", "SON", "DAUGHTER", "WIFE", "HUSBAND", "FATHER", "MOTHER"))
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("principal_card_number")
                        .nameAr("رقم بطاقة الموظف")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("JFZ202500001")
                        .description("Principal card number (required for dependents to link them to their principal)")
                        .descriptionAr("رقم بطاقة الموظف (مطلوب للتابعين لربطهم بالعائل)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("card_number")
                        .nameAr("رقم البطاقة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("JFZ202500001W1")
                        .description("Member card number (optional, system will generate if empty)")
                        .descriptionAr("رقم بطاقة العضو (اختياري، سيقوم النظام بالتوليد إذا كان فارغاً)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("member_status")
                        .nameAr("حالة العضوية")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("نشط")
                        .description("Membership eligibility status; benefit exhaustion is imported separately")
                        .descriptionAr("حالة العضوية فقط؛ اكتمال سقف منفعة لا يوقف المستفيد")
                        .allowedValues(List.of("نشط", "موقوف", "منتهي", "قيد المراجعة", "ACTIVE", "SUSPENDED", "TERMINATED", "PENDING"))
                        .width(18)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("birth_date")
                        .nameAr("تاريخ الميلاد")
                        .type(ColumnType.DATE)
                        .required(false)
                        .example("1990-05-15")
                        .description("Birth date (optional, format: YYYY-MM-DD)")
                        .descriptionAr("تاريخ الميلاد (اختياري، بصيغة: YYYY-MM-DD)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("civil_id")
                        .nameAr("الرقم الوطني")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("119900000000")
                        .description("National Number / Civil ID (optional)")
                        .descriptionAr("الرقم الوطني للمستفيد (اختياري)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("employee_number")
                        .nameAr("الرقم الوظيفي")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("32232")
                        .description("Employee or Financial Number (optional)")
                        .descriptionAr("الرقم الوظيفي أو المالي للموظف (اختياري)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("phone")
                        .nameAr("رقم الهاتف")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("0910000000")
                        .description("Phone number (optional)")
                        .descriptionAr("رقم الهاتف للتواصل (اختياري)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("email")
                        .nameAr("البريد الإلكتروني")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("user@example.com")
                        .description("Email address (optional)")
                        .descriptionAr("البريد الإلكتروني (اختياري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("gender")
                        .nameAr("الجنس")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("ذكر")
                        .description("Gender (MALE or FEMALE)")
                        .descriptionAr("الجنس (ذكر أو أنثى)")
                        .allowedValues(List.of("ذكر", "أنثى", "MALE", "FEMALE"))
                        .width(15)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("policy_number")
                        .nameAr("رقم الوثيقة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("POL-2025")
                        .description("Insurance policy number (optional)")
                        .descriptionAr("رقم وثيقة التأمين (اختياري)")
                        .width(20)
                        .build()
        );
    }

    private List<ExcelLookupData> buildLookupSheets() {
        try {
            List<Employer> employers = employerRepository.findByActiveTrue();
            if (employers != null && !employers.isEmpty()) {
                List<List<String>> data = employers.stream()
                        .map(emp -> List.of(
                                emp.getId().toString(),
                                emp.getCode() != null ? emp.getCode() : "",
                                emp.getName() != null ? emp.getName() : ""
                        ))
                        .collect(Collectors.toList());

                return List.of(
                        ExcelLookupData.builder()
                                .sheetName("Employers")
                                .sheetNameAr("جهات العمل")
                                .headers(List.of("ID / المعرف", "Code / الرمز", "Name / الاسم"))
                                .data(data)
                                .description("List of active employers in the system")
                                .descriptionAr("قائمة جهات العمل النشطة في النظام")
                                .build()
                );
            }
        } catch (Exception e) {
            log.error("[MemberTemplate] Error building employer lookup sheet", e);
        }
        return List.of();
    }
}
