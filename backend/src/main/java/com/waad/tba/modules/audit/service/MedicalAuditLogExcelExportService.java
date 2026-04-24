package com.waad.tba.modules.audit.service;

import com.waad.tba.modules.audit.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalAuditLogExcelExportService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MedicalAuditLogService medicalAuditLogService;

    @Transactional(readOnly = true)
    public byte[] exportClaimAuditLogs(Long claimId, String correlationId, int maxRows) throws IOException {
        int safeMaxRows = Math.min(Math.max(1, maxRows), 20000);

        List<AuditLog> rows = medicalAuditLogService
                .searchClaimAuditLogs(
                        claimId,
                        correlationId,
                        PageRequest.of(0, safeMaxRows, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("MedicalAuditLogs");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            String[] headers = {
                    "ID",
                    "EntityType",
                    "EntityId",
                    "Action",
                    "UserId",
                    "Role",
                    "TimestampUTC",
                    "Reason",
                    "CorrelationId",
                    "Source",
                    "Version",
                    "BeforeState",
                    "AfterState"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (AuditLog logRow : rows) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                put(row, col++, logRow.getId(), normalStyle);
                put(row, col++, logRow.getEntityType() != null ? logRow.getEntityType().name() : null, normalStyle);
                put(row, col++, logRow.getEntityId(), normalStyle);
                put(row, col++, logRow.getAction() != null ? logRow.getAction().name() : null, normalStyle);
                put(row, col++, logRow.getUserId(), normalStyle);
                put(row, col++, logRow.getRole(), normalStyle);
                put(row, col++, logRow.getTimestamp() != null
                        ? TS_FORMATTER.format(logRow.getTimestamp().atZone(ZoneOffset.UTC))
                        : null, normalStyle);
                put(row, col++, logRow.getReason(), normalStyle);
                put(row, col++, logRow.getCorrelationId(), normalStyle);
                put(row, col++, logRow.getSource() != null ? logRow.getSource().name() : null, normalStyle);
                put(row, col++, logRow.getVersion(), normalStyle);
                put(row, col++, logRow.getBeforeState(), normalStyle);
                put(row, col++, logRow.getAfterState(), normalStyle);
            }

            int[] widths = { 10, 16, 16, 16, 10, 14, 22, 28, 26, 12, 10, 48, 48 };
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            log.info("Exported {} medical audit logs to XLSX", rows.size());
            return outputStream.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportClaimAuditLogsByDate(
            Long claimId,
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            int maxRows) throws IOException {

        int safeMaxRows = Math.min(Math.max(1, maxRows), 20000);

        List<AuditLog> rows = medicalAuditLogService
                .searchClaimAuditLogsByDate(
                        claimId,
                        correlationId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, safeMaxRows, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent();

        return buildWorkbook(rows);
    }

    private byte[] buildWorkbook(List<AuditLog> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("MedicalAuditLogs");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            String[] headers = {
                    "ID",
                    "EntityType",
                    "EntityId",
                    "Action",
                    "UserId",
                    "Role",
                    "TimestampUTC",
                    "Reason",
                    "CorrelationId",
                    "Source",
                    "Version",
                    "BeforeState",
                    "AfterState"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (AuditLog logRow : rows) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                put(row, col++, logRow.getId(), normalStyle);
                put(row, col++, logRow.getEntityType() != null ? logRow.getEntityType().name() : null, normalStyle);
                put(row, col++, logRow.getEntityId(), normalStyle);
                put(row, col++, logRow.getAction() != null ? logRow.getAction().name() : null, normalStyle);
                put(row, col++, logRow.getUserId(), normalStyle);
                put(row, col++, logRow.getRole(), normalStyle);
                put(row, col++, logRow.getTimestamp() != null
                        ? TS_FORMATTER.format(logRow.getTimestamp().atZone(ZoneOffset.UTC))
                        : null, normalStyle);
                put(row, col++, logRow.getReason(), normalStyle);
                put(row, col++, logRow.getCorrelationId(), normalStyle);
                put(row, col++, logRow.getSource() != null ? logRow.getSource().name() : null, normalStyle);
                put(row, col++, logRow.getVersion(), normalStyle);
                put(row, col++, logRow.getBeforeState(), normalStyle);
                put(row, col++, logRow.getAfterState(), normalStyle);
            }

            int[] widths = { 10, 16, 16, 16, 10, 14, 22, 28, 26, 12, 10, 48, 48 };
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            log.info("Exported {} medical audit logs to XLSX", rows.size());
            return outputStream.toByteArray();
        }
    }

    private void put(Row row, int index, Object value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : String.valueOf(value));
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNormalStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
