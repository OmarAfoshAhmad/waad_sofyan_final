package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.provider.dto.ProviderClaimReportDto;
import com.waad.tba.modules.provider.dto.ProviderPreAuthReportDto;
import com.waad.tba.modules.provider.dto.ProviderVisitReportDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class ProviderReportExcelService {

    public byte[] exportClaimsReport(List<ProviderClaimReportDto> rows) {
        String[] headers = {
                "رقم المطالبة", "تاريخ الخدمة", "اسم المريض", "باركود المريض", "الشركة",
                "المبلغ المطلوب", "المبلغ الموافق", "الصافي", "الحالة", "عدد الخدمات", "التشخيص"
        };

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Claims Report");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle amountStyle = createAmountStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            createHeaderRow(sheet, headers, headerStyle);

            int rowNum = 1;
            for (ProviderClaimReportDto item : rows) {
                Row row = sheet.createRow(rowNum++);
                setTextCell(row, 0, item.getClaimNumber(), textStyle);
                setDateCell(row, 1, item.getClaimDate(), dateStyle, textStyle);
                setTextCell(row, 2, item.getMemberName(), textStyle);
                setTextCell(row, 3, item.getMemberBarcode(), textStyle);
                setTextCell(row, 4, item.getEmployerName(), textStyle);
                setAmountCell(row, 5, item.getClaimedAmount(), amountStyle);
                setAmountCell(row, 6, item.getApprovedAmount(), amountStyle);
                setAmountCell(row, 7, item.getNetAmount(), amountStyle);
                setTextCell(row, 8, item.getStatusLabel(), textStyle);
                setIntegerCell(row, 9, item.getServicesCount(), textStyle);
                setTextCell(row, 10, item.getDiagnosis(), textStyle);
            }

            autosize(sheet, headers.length);
            return toBytes(workbook);
        } catch (IOException e) {
            log.error("Failed to generate claims report Excel", e);
            throw new RuntimeException("فشل إنشاء ملف إكسل لتقرير المطالبات", e);
        }
    }

    public byte[] exportPreAuthReport(List<ProviderPreAuthReportDto> rows) {
        String[] headers = {
                "رقم الموافقة", "تاريخ الطلب", "اسم المريض", "باركود المريض", "الخدمة",
                "الجلسات المطلوبة", "الجلسات الموافق عليها", "المستخدم", "المبلغ المطلوب", "المبلغ الموافق", "الحالة"
        };

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("PreAuth Report");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle amountStyle = createAmountStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            createHeaderRow(sheet, headers, headerStyle);

            int rowNum = 1;
            for (ProviderPreAuthReportDto item : rows) {
                Row row = sheet.createRow(rowNum++);
                setTextCell(row, 0, item.getPreAuthNumber(), textStyle);
                setDateCell(row, 1, item.getRequestDate(), dateStyle, textStyle);
                setTextCell(row, 2, item.getMemberName(), textStyle);
                setTextCell(row, 3, item.getMemberBarcode(), textStyle);
                setTextCell(row, 4, item.getServiceName(), textStyle);
                setIntegerCell(row, 5, item.getSessionsRequested(), textStyle);
                setIntegerCell(row, 6, item.getSessionsApproved(), textStyle);
                setIntegerCell(row, 7, item.getSessionsUsed(), textStyle);
                setAmountCell(row, 8, item.getRequestedAmount(), amountStyle);
                setAmountCell(row, 9, item.getApprovedAmount(), amountStyle);
                setTextCell(row, 10, item.getStatusLabel(), textStyle);
            }

            autosize(sheet, headers.length);
            return toBytes(workbook);
        } catch (IOException e) {
            log.error("Failed to generate pre-auth report Excel", e);
            throw new RuntimeException("فشل إنشاء ملف إكسل لتقرير الموافقات", e);
        }
    }

    public byte[] exportVisitsReport(List<ProviderVisitReportDto> rows) {
        String[] headers = {
                "رقم الزيارة", "تاريخ الزيارة", "اسم المريض", "باركود المريض", "الشركة",
                "نوع الزيارة", "الشكوى الرئيسية", "عدد المطالبات", "عدد الموافقات", "إجمالي المبلغ", "الحالة"
        };

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Visits Report");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle amountStyle = createAmountStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            createHeaderRow(sheet, headers, headerStyle);

            int rowNum = 1;
            for (ProviderVisitReportDto item : rows) {
                Row row = sheet.createRow(rowNum++);
                setTextCell(row, 0, item.getVisitNumber(), textStyle);
                setDateCell(row, 1, item.getVisitDate(), dateStyle, textStyle);
                setTextCell(row, 2, item.getMemberName(), textStyle);
                setTextCell(row, 3, item.getMemberBarcode(), textStyle);
                setTextCell(row, 4, item.getEmployerName(), textStyle);
                setTextCell(row, 5, item.getVisitTypeLabel(), textStyle);
                setTextCell(row, 6, item.getChiefComplaint(), textStyle);
                setIntegerCell(row, 7, item.getClaimCount(), textStyle);
                setIntegerCell(row, 8, item.getPreAuthCount(), textStyle);
                setAmountCell(row, 9, item.getTotalAmount(), amountStyle);
                setTextCell(row, 10, item.getStatusLabel(), textStyle);
            }

            autosize(sheet, headers.length);
            return toBytes(workbook);
        } catch (IOException e) {
            log.error("Failed to generate visits report Excel", e);
            throw new RuntimeException("فشل إنشاء ملف إكسل لتقرير الزيارات", e);
        }
    }

    private void createHeaderRow(XSSFSheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = createTextStyle(workbook);
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
        return style;
    }

    private CellStyle createAmountStyle(Workbook workbook) {
        CellStyle style = createTextStyle(workbook);
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private void setTextCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(com.waad.tba.common.excel.ExcelSanitizer.sanitize(value != null ? value : "-"));
        cell.setCellStyle(style);
    }

    private void setIntegerCell(Row row, int col, Integer value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setCellValue(0);
        }
        cell.setCellStyle(style);
    }

    private void setAmountCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : BigDecimal.ZERO.doubleValue());
        cell.setCellStyle(style);
    }

    private void setDateCell(Row row, int col, LocalDate value, CellStyle dateStyle, CellStyle textStyle) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue("-");
            cell.setCellStyle(textStyle);
        }
    }

    private void autosize(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            int bounded = Math.min(width + 1000, 22000);
            sheet.setColumnWidth(i, bounded);
        }
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
