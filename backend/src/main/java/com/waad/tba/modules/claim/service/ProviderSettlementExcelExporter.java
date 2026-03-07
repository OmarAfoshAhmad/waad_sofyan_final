package com.waad.tba.modules.claim.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

// POI imports for image/drawing support

import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto;
import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto.ClaimDetail;
import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto.ServiceLineDetail;
import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import com.waad.tba.modules.pdf.service.PdfCompanySettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Excel Export Service for Provider Settlement Reports.
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ PROVIDER SETTLEMENT REPORT - EXCEL EXPORT SERVICE ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Purpose: Export settlement reports to Excel format ║
 * ║ Rule: Uses SAME DTO as UI - NO recalculation of amounts ║
 * ║ Validation: Logs warnings if financial totals are inconsistent ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Validation Rule:
 * - Approved + Rejected + PatientShare should equal Total Requested
 * - Net Payable = Approved - Patient Share
 * - Log warning if mismatch > 0.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderSettlementExcelExporter {

    private final PdfCompanySettingsService companySettingsService;

    private static final String CURRENCY_FORMAT = "#,##0.000";
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    /**
     * Export Provider Settlement Report to Excel bytes.
     * 
     * @param report The SAME DTO used by UI (no recalculation)
     * @return Excel file as byte array
     */
    public byte[] exportToExcel(ProviderSettlementReportDto report) throws IOException {
        log.info("📊 [EXCEL-EXPORT] Starting export for provider: {}, period: {} to {}",
                report.getProviderName(), report.getFromDate(), report.getToDate());

        // Validate financial consistency before export
        validateFinancialConsistency(report);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // Create Summary Sheet
            createSummarySheet(workbook, report);

            // Create Details Sheet (line-level)
            createDetailsSheet(workbook, report);

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("📊 [EXCEL-EXPORT] Export completed successfully: {} claims",
                    report.getTotalClaimsCount());

            return outputStream.toByteArray();
        }
    }

    /**
     * Validate financial totals consistency.
     * Logs WARNING if mismatch > 0.01 but does NOT throw exception.
     */
    private void validateFinancialConsistency(ProviderSettlementReportDto report) {
        BigDecimal totalRequested = safe(report.getTotalRequestedAmount());
        BigDecimal totalApproved = safe(report.getTotalApprovedAmount());
        BigDecimal totalRejected = safe(report.getTotalRejectedAmount());
        BigDecimal totalPatientShare = safe(report.getTotalPatientShare());
        BigDecimal netProviderAmount = safe(report.getNetProviderAmount());

        // ══════════════════════════════════════════════════════════════════════
        // Validation 1: Approved + Rejected should equal Total Requested
        // (PatientShare is deducted from what insurance pays, not from gross)
        // ══════════════════════════════════════════════════════════════════════
        BigDecimal expectedTotal = totalApproved.add(totalRejected);
        BigDecimal diff1 = totalRequested.subtract(expectedTotal).abs();

        if (diff1.compareTo(TOLERANCE) > 0) {
            log.warn("⚠️ [FINANCIAL-MISMATCH] Provider: {} | " +
                    "Total Requested ({}) ≠ Approved ({}) + Rejected ({}) | " +
                    "Difference: {} | Report: {}",
                    report.getProviderName(),
                    totalRequested, totalApproved, totalRejected,
                    diff1, report.getReportNumber());
        }

        // ══════════════════════════════════════════════════════════════════════
        // Validation 2: Net Payable = Approved - Patient Share
        // ══════════════════════════════════════════════════════════════════════
        BigDecimal expectedNetPayable = totalApproved.subtract(totalPatientShare);
        BigDecimal diff2 = netProviderAmount.subtract(expectedNetPayable).abs();

        if (diff2.compareTo(TOLERANCE) > 0) {
            log.warn("⚠️ [FINANCIAL-MISMATCH] Provider: {} | " +
                    "Net Payable ({}) ≠ Approved ({}) - Patient Share ({}) | " +
                    "Difference: {} | Report: {}",
                    report.getProviderName(),
                    netProviderAmount, totalApproved, totalPatientShare,
                    diff2, report.getReportNumber());
        }

        // Log summary
        log.info("📊 [FINANCIAL-CHECK] Provider: {} | Requested: {} | Approved: {} | " +
                "Rejected: {} | Patient Share: {} | Net Payable: {}",
                report.getProviderName(),
                totalRequested, totalApproved, totalRejected, totalPatientShare, netProviderAmount);
    }

    /**
     * Create Summary Sheet with company branding header and report totals.
     */
    private void createSummarySheet(Workbook workbook, ProviderSettlementReportDto report) {
        Sheet sheet = workbook.createSheet("ملخص التسوية");
        sheet.setRightToLeft(true); // Arabic RTL support

        // Get company settings for branding
        PdfCompanySettings settings = companySettingsService.getActiveSettings();

        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle companyStyle = createCompanyHeaderStyle(workbook);

        int rowNum = 0;

        // ══════════════════════════════════════════════════════════════════════
        // COMPANY BRANDING HEADER
        // ══════════════════════════════════════════════════════════════════════

        // Add company logo if available
        if (settings.hasLogo() && settings.getLogoData() != null) {
            try {
                int pictureIdx = workbook.addPicture(settings.getLogoData(), Workbook.PICTURE_TYPE_PNG);
                CreationHelper helper = workbook.getCreationHelper();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(0);
                anchor.setRow1(rowNum);
                anchor.setCol2(2);
                anchor.setRow2(rowNum + 3);
                drawing.createPicture(anchor, pictureIdx);
                rowNum += 4; // Leave space for logo
            } catch (Exception e) {
                log.warn("Failed to add logo to Excel: {}", e.getMessage());
            }
        }

        // Company Name (from settings)
        Row companyRow = sheet.createRow(rowNum++);
        Cell companyCell = companyRow.createCell(0);
        companyCell.setCellValue(settings.getCompanyName() != null ? settings.getCompanyName() : "نظام وعد الطبي");
        companyCell.setCellStyle(companyStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));

        rowNum++; // Empty row

        // ══════════════════════════════════════════════════════════════════════
        // REPORT HEADER SECTION
        // ══════════════════════════════════════════════════════════════════════

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("تقرير تسوية مقدم الخدمة");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));

        rowNum++; // Empty row

        // Report Info
        createLabelValueRow(sheet, rowNum++, "رقم التقرير:", report.getReportNumber(), labelStyle);
        createLabelValueRow(sheet, rowNum++, "تاريخ التقرير:", formatDate(report.getReportDate()), labelStyle);
        createLabelValueRow(sheet, rowNum++, "مقدم الخدمة:", report.getProviderName(), labelStyle);
        createLabelValueRow(sheet, rowNum++, "الفترة من:", formatDate(report.getFromDate()), labelStyle);
        createLabelValueRow(sheet, rowNum++, "الفترة إلى:", formatDate(report.getToDate()), labelStyle);

        rowNum++; // Empty row

        // ══════════════════════════════════════════════════════════════════════
        // SUMMARY TOTALS SECTION
        // ══════════════════════════════════════════════════════════════════════

        Row summaryHeaderRow = sheet.createRow(rowNum++);
        Cell summaryHeaderCell = summaryHeaderRow.createCell(0);
        summaryHeaderCell.setCellValue("ملخص الأرقام المالية");
        summaryHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));

        // Summary data
        createFinancialRow(sheet, rowNum++, "عدد المطالبات:",
                report.getTotalClaimsCount() != null ? report.getTotalClaimsCount() : 0L, labelStyle, currencyStyle);
        createFinancialRow(sheet, rowNum++, "إجمالي المبلغ المطلوب (Gross):",
                report.getTotalRequestedAmount(), labelStyle, currencyStyle);
        createFinancialRow(sheet, rowNum++, "إجمالي المبلغ المعتمد (Approved):",
                report.getTotalApprovedAmount(), labelStyle, currencyStyle);
        createFinancialRow(sheet, rowNum++, "إجمالي المبلغ المرفوض (Rejected):",
                report.getTotalRejectedAmount(), labelStyle, currencyStyle);
        createFinancialRow(sheet, rowNum++, "إجمالي حصة المؤمن عليه (Patient Share):",
                report.getTotalPatientShare(), labelStyle, currencyStyle);

        rowNum++; // Empty row

        // Net Payable (highlighted)
        Row netRow = sheet.createRow(rowNum++);
        Cell netLabelCell = netRow.createCell(0);
        netLabelCell.setCellValue("صافي المستحق للمقدم (Net Payable):");
        netLabelCell.setCellStyle(headerStyle);

        Cell netValueCell = netRow.createCell(1);
        if (report.getNetProviderAmount() != null) {
            netValueCell.setCellValue(report.getNetProviderAmount().doubleValue());
        }
        netValueCell.setCellStyle(currencyStyle);

        rowNum += 2; // Empty rows before footer

        // ══════════════════════════════════════════════════════════════════════
        // COMPANY FOOTER (from settings)
        // ══════════════════════════════════════════════════════════════════════

        CellStyle footerStyle = createFooterStyle(workbook);

        // Footer separator (empty row)
        sheet.createRow(rowNum++);

        // Company contact info
        StringBuilder footerText = new StringBuilder();
        if (settings.getAddress() != null && !settings.getAddress().isEmpty()) {
            footerText.append("العنوان: ").append(settings.getAddress());
        }
        if (settings.getPhone() != null && !settings.getPhone().isEmpty()) {
            if (footerText.length() > 0)
                footerText.append(" | ");
            footerText.append("هاتف: ").append(settings.getPhone());
        }
        if (settings.getEmail() != null && !settings.getEmail().isEmpty()) {
            if (footerText.length() > 0)
                footerText.append(" | ");
            footerText.append("بريد: ").append(settings.getEmail());
        }

        if (footerText.length() > 0) {
            Row contactRow = sheet.createRow(rowNum++);
            Cell contactCell = contactRow.createCell(0);
            contactCell.setCellValue(footerText.toString());
            contactCell.setCellStyle(footerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        }

        // Custom footer text
        if (settings.getFooterText() != null && !settings.getFooterText().isEmpty()) {
            Row customFooterRow = sheet.createRow(rowNum++);
            Cell customFooterCell = customFooterRow.createCell(0);
            customFooterCell.setCellValue(settings.getFooterText());
            customFooterCell.setCellStyle(footerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        }

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }

        // Set minimum column widths
        sheet.setColumnWidth(0, 10000);
        sheet.setColumnWidth(1, 6000);
    }

    /**
     * Create Details Sheet with claim-level and line-level details.
     */
    private void createDetailsSheet(Workbook workbook, ProviderSettlementReportDto report) {
        Sheet sheet = workbook.createSheet("تفاصيل المطالبات");
        sheet.setRightToLeft(true);

        // Get company settings for branding
        PdfCompanySettings settings = companySettingsService.getActiveSettings();

        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle companyStyle = createCompanyHeaderStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);

        int rowNum = 0;

        // ══════════════════════════════════════════════════════════════════════
        // COMPANY BRANDING HEADER (simplified - logo only on Summary sheet)
        // ══════════════════════════════════════════════════════════════════════

        // Company Name
        Row companyRow = sheet.createRow(rowNum++);
        Cell companyCell = companyRow.createCell(0);
        companyCell.setCellValue(settings.getCompanyName() != null ? settings.getCompanyName() : "نظام وعد الطبي");
        companyCell.setCellStyle(companyStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 6));

        // Report Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(
                "تفاصيل مطالبات التسوية - " + (report.getProviderName() != null ? report.getProviderName() : ""));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 6));

        rowNum++; // Empty row before data

        // ══════════════════════════════════════════════════════════════════════
        // HEADER ROW
        // ══════════════════════════════════════════════════════════════════════
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                "رقم المطالبة",
                "رقم الموافقة",
                "اسم المريض",
                "رقم التأمين",
                "كود الخدمة",
                "اسم الخدمة",
                "تاريخ الخدمة",
                "الكمية",
                "سعر الوحدة",
                "المبلغ الإجمالي",
                "المبلغ المعتمد",
                "المبلغ المرفوض",
                "حصة المريض",
                "الحالة"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ══════════════════════════════════════════════════════════════════════
        // DATA ROWS (Line-level detail)
        // ══════════════════════════════════════════════════════════════════════
        if (report.getClaims() != null) {
            for (ClaimDetail claim : report.getClaims()) {
                if (claim.getLines() != null && !claim.getLines().isEmpty()) {
                    // If claim has lines, output each line
                    for (ServiceLineDetail line : claim.getLines()) {
                        Row row = sheet.createRow(rowNum++);
                        int col = 0;

                        row.createCell(col++)
                                .setCellValue(claim.getClaimNumber() != null ? claim.getClaimNumber() : "");
                        row.createCell(col++)
                                .setCellValue(claim.getPreAuthNumber() != null ? claim.getPreAuthNumber() : "");
                        row.createCell(col++)
                                .setCellValue(claim.getPatientName() != null ? claim.getPatientName() : "");
                        row.createCell(col++)
                                .setCellValue(claim.getInsuranceNumber() != null ? claim.getInsuranceNumber() : "");
                        row.createCell(col++).setCellValue(line.getServiceCode() != null ? line.getServiceCode() : "");
                        row.createCell(col++).setCellValue(line.getServiceName() != null ? line.getServiceName() : "");

                        Cell dateCell = row.createCell(col++);
                        if (line.getServiceDate() != null) {
                            dateCell.setCellValue(formatDate(line.getServiceDate()));
                        }
                        dateCell.setCellStyle(dateStyle);

                        Cell qtyCell = row.createCell(col++);
                        if (line.getQuantity() != null) {
                            qtyCell.setCellValue(line.getQuantity());
                        }

                        Cell unitPriceCell = row.createCell(col++);
                        if (line.getUnitPrice() != null) {
                            unitPriceCell.setCellValue(line.getUnitPrice().doubleValue());
                        }
                        unitPriceCell.setCellStyle(currencyStyle);

                        Cell grossCell = row.createCell(col++);
                        if (line.getGrossAmount() != null) {
                            grossCell.setCellValue(line.getGrossAmount().doubleValue());
                        }
                        grossCell.setCellStyle(currencyStyle);

                        Cell approvedCell = row.createCell(col++);
                        if (line.getApprovedAmount() != null) {
                            approvedCell.setCellValue(line.getApprovedAmount().doubleValue());
                        }
                        approvedCell.setCellStyle(currencyStyle);

                        Cell rejectedCell = row.createCell(col++);
                        if (line.getRejectedAmount() != null) {
                            rejectedCell.setCellValue(line.getRejectedAmount().doubleValue());
                        }
                        rejectedCell.setCellStyle(currencyStyle);

                        Cell patientShareCell = row.createCell(col++);
                        if (line.getPatientShare() != null) {
                            patientShareCell.setCellValue(line.getPatientShare().doubleValue());
                        }
                        patientShareCell.setCellStyle(currencyStyle);

                        row.createCell(col++).setCellValue(
                                line.getLineStatusArabic() != null ? line.getLineStatusArabic() : "");
                    }
                } else {
                    // No lines - output claim summary row
                    Row row = sheet.createRow(rowNum++);
                    int col = 0;

                    row.createCell(col++).setCellValue(claim.getClaimNumber() != null ? claim.getClaimNumber() : "");
                    row.createCell(col++)
                            .setCellValue(claim.getPreAuthNumber() != null ? claim.getPreAuthNumber() : "");
                    row.createCell(col++).setCellValue(claim.getPatientName() != null ? claim.getPatientName() : "");
                    row.createCell(col++)
                            .setCellValue(claim.getInsuranceNumber() != null ? claim.getInsuranceNumber() : "");
                    row.createCell(col++).setCellValue("-"); // Service code
                    row.createCell(col++).setCellValue("إجمالي المطالبة"); // Service name

                    Cell dateCell = row.createCell(col++);
                    if (claim.getServiceDate() != null) {
                        dateCell.setCellValue(formatDate(claim.getServiceDate()));
                    }
                    dateCell.setCellStyle(dateStyle);

                    row.createCell(col++).setCellValue(1); // Quantity
                    row.createCell(col++).setCellValue(""); // Unit price

                    Cell grossCell = row.createCell(col++);
                    if (claim.getGrossAmount() != null) {
                        grossCell.setCellValue(claim.getGrossAmount().doubleValue());
                    }
                    grossCell.setCellStyle(currencyStyle);

                    Cell approvedCell = row.createCell(col++);
                    if (claim.getNetAmount() != null) {
                        approvedCell.setCellValue(claim.getNetAmount().doubleValue());
                    }
                    approvedCell.setCellStyle(currencyStyle);

                    Cell rejectedCell = row.createCell(col++);
                    if (claim.getRejectedAmount() != null) {
                        rejectedCell.setCellValue(claim.getRejectedAmount().doubleValue());
                    }
                    rejectedCell.setCellStyle(currencyStyle);

                    Cell patientShareCell = row.createCell(col++);
                    if (claim.getPatientShare() != null) {
                        patientShareCell.setCellValue(claim.getPatientShare().doubleValue());
                    }
                    patientShareCell.setCellStyle(currencyStyle);

                    row.createCell(col++).setCellValue(
                            claim.getStatusArabic() != null ? claim.getStatusArabic() : "");
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════════

    private void createLabelValueRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(style);

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "-");
    }

    private void createFinancialRow(Sheet sheet, int rowNum, String label, BigDecimal value,
            CellStyle labelStyle, CellStyle currencyStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        if (value != null) {
            valueCell.setCellValue(value.doubleValue());
        }
        valueCell.setCellStyle(currencyStyle);
    }

    private void createFinancialRow(Sheet sheet, int rowNum, String label, Long value,
            CellStyle labelStyle, CellStyle currencyStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        if (value != null) {
            valueCell.setCellValue(value);
        }
    }

    private String formatDate(LocalDate date) {
        if (date == null)
            return "-";
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STYLE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(CURRENCY_FORMAT));
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("yyyy-mm-dd"));
        return style;
    }

    /**
     * Style for company name header (large, bold, centered).
     */
    private CellStyle createCompanyHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * Style for footer text (smaller, gray, centered).
     */
    private CellStyle createFooterStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}
