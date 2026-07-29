package com.waad.tba.modules.member.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for exporting Members to Excel format
 * 
 * Features:
 * - Export all members or filtered members
 * - Paginated data retrieval for large datasets
 * - Arabic RTL support
 * - Formatted columns (dates, enums, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberExcelExportService {

    private final MemberRepository memberRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long DIRECT_EXPORT_MAX_ROWS = 50_000;

    /**
     * Export members to Excel with optional filters
     * 
     * @param searchQuery     Optional search query
     * @param employerId      Optional employer filter
     * @param benefitPolicyId Optional policy filter
     * @param includeDeleted  Include soft-deleted members
     * @return Excel file as byte array
     */
    @Transactional(readOnly = true)
    public byte[] exportToExcel(
            String searchQuery,
            Long employerId,
            Long benefitPolicyId,
            Boolean includeDeleted) throws IOException {

        log.info("📊 [Excel Export] Starting export - Query: {}, Employer: {}, Policy: {}, Deleted: {}",
                searchQuery, employerId, benefitPolicyId, includeDeleted);

        // Build specification for filtering
        Specification<Member> spec = buildSpecification(searchQuery, employerId, benefitPolicyId, includeDeleted);

        long exportCount = memberRepository.count(spec);
        if (exportCount > DIRECT_EXPORT_MAX_ROWS) {
            throw new BusinessRuleException(
                    "نتيجة التصدير كبيرة جداً (" + exportCount + " مستفيد). "
                            + "يرجى تضييق الفلتر حسب جهة العمل أو الوثيقة أو البحث. "
                            + "الحد الأقصى للتصدير المباشر هو " + DIRECT_EXPORT_MAX_ROWS + " مستفيد.");
        }

        // Fetch data
        List<Member> members = memberRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));

        log.info("📊 [Excel Export] Found {} members to export", members.size());

        // Create Excel workbook
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Members");
            sheet.setRightToLeft(true); // Arabic RTL

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            // Create header row
            createHeaderRow(sheet, headerStyle);

            // Create data rows
            int rowNum = 1;
            for (Member member : members) {
                createDataRow(sheet, rowNum++, member, dateStyle, normalStyle);
            }

            // Auto-size columns
            for (int i = 0; i < 15; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("✅ [Excel Export] Export completed: {} rows", members.size());

            return outputStream.toByteArray();
        }
    }

    /**
     * Build specification for filtering members
     */
    private Specification<Member> buildSpecification(
            String searchQuery,
            Long employerId,
            Long benefitPolicyId,
            Boolean includeDeleted) {
        Specification<Member> spec = (root, query, cb) -> cb.conjunction();

        // Search query
        if (searchQuery != null && !searchQuery.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), "%" + searchQuery.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("nationalNumber")), "%" + searchQuery.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("cardNumber")), "%" + searchQuery.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("barcode")), "%" + searchQuery.toLowerCase() + "%")));
        }

        // Employer filter
        if (employerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("employer").get("id"), employerId));
        }

        // Benefit policy filter
        if (benefitPolicyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("benefitPolicy").get("id"), benefitPolicyId));
        }

        // Active filter (soft delete)
        if (includeDeleted == null || !includeDeleted) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("active")));
        }

        return spec;
    }

    /**
     * Create header row
     */
    private void createHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);

        String[] headers = {
                "الرقم / ID",
                "الاسم الكامل / Full Name",
                "الرقم الوطني / National ID",
                "رقم البطاقة / Card Number",
                "الباركود / Barcode",
                "الجهة / Employer",
                "رقم الموظف / Employee No",
                "تاريخ الميلاد / Birth Date",
                "الجنس / Gender",
                "الهاتف / Phone",
                "البريد / Email",
                "الحالة / Status",
                "الجنسية / Nationality",
                "نوع العضو / Type",
                "محذوف / Deleted"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Create data row for a member
     */
    private void createDataRow(Sheet sheet, int rowNum, Member member, CellStyle dateStyle, CellStyle normalStyle) {
        Row row = sheet.createRow(rowNum);

        int colNum = 0;

        // ID
        createCell(row, colNum++, member.getId() != null ? member.getId().toString() : "", normalStyle);

        // Full Name
        createCell(row, colNum++, member.getFullName(), normalStyle);

        // National Number
        createCell(row, colNum++, member.getNationalNumber(), normalStyle);

        // Card Number
        createCell(row, colNum++, member.getCardNumber(), normalStyle);

        // Barcode
        createCell(row, colNum++, member.getBarcode(), normalStyle);

        // Employer
        createCell(row, colNum++,
                member.getEmployer() != null ? member.getEmployer().getName() : "",
                normalStyle);

        // Employee Number
        createCell(row, colNum++, member.getEmployeeNumber(), normalStyle);

        // Birth Date
        if (member.getBirthDate() != null) {
            createCell(row, colNum++, member.getBirthDate().format(DATE_FORMATTER), dateStyle);
        } else {
            createCell(row, colNum++, "", dateStyle);
        }

        // Gender
        createCell(row, colNum++,
                member.getGender() != null ? member.getGender().name() : "",
                normalStyle);

        // Phone
        createCell(row, colNum++, member.getPhone(), normalStyle);

        // Email
        createCell(row, colNum++, member.getEmail(), normalStyle);

        // Status
        createCell(row, colNum++,
                member.getStatus() != null ? member.getStatus().name() : "",
                normalStyle);

        // Nationality
        createCell(row, colNum++, member.getNationality(), normalStyle);

        // Member Type
        String memberType = member.getParent() == null ? "رئيسي / Principal" : "تابع / Dependent";
        createCell(row, colNum++, memberType, normalStyle);

        // Active/Deleted
        createCell(row, colNum++, member.getActive() != null && member.getActive() ? "نشط / Active" : "محذوف / Deleted",
                normalStyle);
    }

    /**
     * Create cell with value and style
     */
    private void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Create header cell style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * Create date cell style
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * Create normal cell style
     */
    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT); // RTL for Arabic
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }
}
