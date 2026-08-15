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
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.AuthorizedMemberScope;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import com.waad.tba.modules.member.security.MemberScopeFilter;

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
    private final MemberQueryAccessPolicy queryAccessPolicy;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long DIRECT_EXPORT_MAX_ROWS = 50_000;
    // Rows kept in memory at once before SXSSFWorkbook flushes them to a temp
    // file; keeps memory flat regardless of export size instead of holding the
    // whole workbook (up to 50k rows) in RAM like XSSFWorkbook did.
    private static final int STREAMING_WINDOW_SIZE = 500;
    // Fixed column widths (in POI's 1/256-of-a-character units) replace
    // autoSizeColumn: SXSSFWorkbook can't auto-size flushed-out rows, and
    // autoSizeColumn was itself an O(rows) pass per column on the old path.
    private static final int[] COLUMN_WIDTHS = {
            8 * 256, 28 * 256, 16 * 256, 16 * 256, 20 * 256, 26 * 256, 14 * 256,
            13 * 256, 10 * 256, 14 * 256, 26 * 256, 14 * 256, 14 * 256, 18 * 256, 14 * 256
    };
    private static final String[] REIMPORTABLE_HEADERS = {
            "full_name", "employer", "relationship", "principal_card_number",
            "card_number", "member_status", "birth_date", "civil_id",
            "employee_number", "phone", "email", "gender", "policy_number"
    };

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
        return exportToExcel(searchQuery, employerId, benefitPolicyId, null, null, includeDeleted);
    }

    @Transactional(readOnly = true)
    public byte[] exportToExcel(String searchQuery, Long employerId, Long benefitPolicyId,
            String status, String type, Boolean includeDeleted) throws IOException {

        log.info("📊 [Excel Export] Starting export - Query: {}, Employer: {}, Policy: {}, Deleted: {}",
                searchQuery, employerId, benefitPolicyId, includeDeleted);

        AuthorizedMemberScope scope = queryAccessPolicy.requireListing(MemberOperation.EXPORT, employerId);

        // Authorization is part of the query itself. A preliminary check followed
        // by an unscoped export would still be an IDOR under a spoofed/null filter.
        Specification<Member> spec = buildSpecification(
                searchQuery, scope, benefitPolicyId, status, type, includeDeleted);

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

        // Streaming workbook: keeps only STREAMING_WINDOW_SIZE rows in memory at
        // once (flushes the rest to a temp file), instead of building the entire
        // workbook in RAM like XSSFWorkbook did -- matters once exports approach
        // DIRECT_EXPORT_MAX_ROWS.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW_SIZE)) {
            try {
                Sheet sheet = workbook.createSheet("Members");
                sheet.setRightToLeft(true); // Arabic RTL

                for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                    sheet.setColumnWidth(i, COLUMN_WIDTHS[i]);
                }

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

                // Write to byte array
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                workbook.write(outputStream);

                log.info("✅ [Excel Export] Export completed: {} rows", members.size());

                return outputStream.toByteArray();
            } finally {
                // Deletes the temp file(s) SXSSFWorkbook wrote while streaming --
                // required, unlike XSSFWorkbook, or they leak on every export.
                workbook.dispose();
            }
        }
    }

    /**
     * Canonical operational export whose schema is exactly the live import
     * template. This is deliberately separate from exportToExcel(), which is a
     * human-readable report and must never be presented as a backup/re-import
     * file.
     */
    @Transactional(readOnly = true)
    public byte[] exportReimportableExcel(String searchQuery, Long employerId,
            Long benefitPolicyId, String status, String type, Boolean includeDeleted) throws IOException {
        AuthorizedMemberScope scope = queryAccessPolicy.requireListing(MemberOperation.EXPORT, employerId);
        Specification<Member> spec = buildSpecification(
                searchQuery, scope, benefitPolicyId, status, type, includeDeleted);
        long exportCount = memberRepository.count(spec);
        if (exportCount > DIRECT_EXPORT_MAX_ROWS) {
            throw new BusinessRuleException("نتيجة التصدير القابل لإعادة الاستيراد تتجاوز الحد "
                    + DIRECT_EXPORT_MAX_ROWS + "؛ ضيّق نطاق التصفية");
        }
        // Principal-first order makes the file readable and remains valid even
        // for consumers that do not implement the importer's forward-reference
        // resolver. The explicit principal_card_number is still authoritative.
        List<Member> members = memberRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "id"));
        members.sort(java.util.Comparator.comparing(Member::isDependent)
                .thenComparing(Member::getId));

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW_SIZE)) {
            try {
                Sheet sheet = workbook.createSheet("members_import");
                sheet.setRightToLeft(false);
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle normalStyle = createNormalStyle(workbook);
                Row header = sheet.createRow(0);
                for (int i = 0; i < REIMPORTABLE_HEADERS.length; i++) {
                    createCell(header, i, REIMPORTABLE_HEADERS[i], headerStyle);
                    sheet.setColumnWidth(i, i == 0 || i == 1 ? 28 * 256 : 20 * 256);
                }
                int rowNumber = 1;
                for (Member member : members) {
                    createReimportableRow(sheet, rowNumber++, member, normalStyle);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                workbook.write(output);
                return output.toByteArray();
            } finally {
                workbook.dispose();
            }
        }
    }

    private void createReimportableRow(Sheet sheet, int rowNumber, Member member, CellStyle style) {
        Row row = sheet.createRow(rowNumber);
        int column = 0;
        createCell(row, column++, member.getFullName(), style);
        createCell(row, column++, member.getEmployer() == null ? "" : member.getEmployer().getName(), style);
        createCell(row, column++, member.getParent() == null ? "PRINCIPAL"
                : member.getRelationship() == null ? "" : member.getRelationship().name(), style);
        createCell(row, column++, member.getParent() == null ? "" : member.getParent().getCardNumber(), style);
        createCell(row, column++, member.getCardNumber(), style);
        createCell(row, column++, member.getStatus() == null ? "" : member.getStatus().name(), style);
        createCell(row, column++, member.getBirthDate() == null ? ""
                : member.getBirthDate().format(DATE_FORMATTER), style);
        createCell(row, column++, member.getNationalNumber() != null
                ? member.getNationalNumber() : member.getCivilId(), style);
        createCell(row, column++, member.getEmployeeNumber(), style);
        createCell(row, column++, member.getPhone(), style);
        createCell(row, column++, member.getEmail(), style);
        createCell(row, column++, member.getGender() == null ? "" : member.getGender().name(), style);
        createCell(row, column, member.getBenefitPolicy() == null ? ""
                : member.getBenefitPolicy().getPolicyCode(), style);
    }

    /**
     * Build specification for filtering members
     */
    private Specification<Member> buildSpecification(
            String searchQuery,
            AuthorizedMemberScope scope,
            Long benefitPolicyId,
            String status,
            String type,
            Boolean includeDeleted) {
        Specification<Member> spec = (root, query, cb) -> MemberScopeFilter.toPredicate(
                scope, root.get("employer").get("id"), cb);

        // Search query
        if (searchQuery != null && !searchQuery.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), "%" + searchQuery.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("nationalNumber")), "%" + searchQuery.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("cardNumber")), "%" + searchQuery.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("barcode")), "%" + searchQuery.toLowerCase() + "%")));
        }

        // Benefit policy filter
        if (benefitPolicyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("benefitPolicy").get("id"), benefitPolicyId));
        }

        if (status != null && !status.isBlank()) {
            Member.MemberStatus parsed;
            try {
                parsed = Member.MemberStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BusinessRuleException("حالة المستفيد غير معروفة: " + status);
            }
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        if (type != null && !type.isBlank()) {
            if ("PRINCIPAL".equalsIgnoreCase(type)) {
                spec = spec.and((root, query, cb) -> cb.isNull(root.get("parent")));
            } else if ("DEPENDENT".equalsIgnoreCase(type)) {
                spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("parent")));
            } else {
                throw new BusinessRuleException("نوع المستفيد غير معروف: " + type);
            }
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
        String memberType = member.getParent() == null ? "موظف / Employee" : "تابع / Dependent";
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
