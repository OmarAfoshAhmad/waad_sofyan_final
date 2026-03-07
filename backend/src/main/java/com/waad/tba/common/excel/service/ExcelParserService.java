package com.waad.tba.common.excel.service;

import com.waad.tba.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Unified service for parsing Excel import files
 * 
 * Provides common utilities for reading cells, validating file format, etc.
 */
@Slf4j
@Service
public class ExcelParserService {

    /** Maximum allowed Excel file size: 10 MB */
    private static final long MAX_EXCEL_FILE_SIZE = 10L * 1024 * 1024;

    /**
     * Validate Excel file format and size.
     */
    public void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }

        if (file.getSize() > MAX_EXCEL_FILE_SIZE) {
            throw new BusinessRuleException(
                    "حجم الملف يتجاوز الحد المسموح (10 ميغابايت)");
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new BusinessRuleException(
                    "نوع الملف غير صحيح. يجب أن يكون ملف Excel (.xlsx أو .xls)");
        }
    }

    /**
     * Open workbook from multipart file
     */
    public Workbook openWorkbook(MultipartFile file) throws IOException {
        validateExcelFile(file);
        try (InputStream inputStream = file.getInputStream()) {
            return WorkbookFactory.create(inputStream);
        }
    }

    /**
     * Get first data sheet (skip metadata sheet)
     */
    public Sheet getDataSheet(Workbook workbook) {
        // Look for "Data" sheet first
        Sheet dataSheet = workbook.getSheet("Data");
        if (dataSheet != null) {
            return dataSheet;
        }

        // Otherwise, get first visible sheet
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (!workbook.isSheetHidden(i)) {
                return workbook.getSheetAt(i);
            }
        }

        throw new BusinessRuleException(
                "لم يتم العثور على ورقة بيانات صالحة في الملف");
    }

    /**
     * Check if row is empty
     */
    public boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get cell value as string (handles all cell types)
     */
    public String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();

                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Date date = cell.getDateCellValue();
                        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
                    } else {
                        double numValue = cell.getNumericCellValue();
                        // Return as integer if it's a whole number
                        if (numValue == Math.floor(numValue)) {
                            return String.valueOf((long) numValue);
                        }
                        return String.valueOf(numValue);
                    }

                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());

                case FORMULA:
                    try {
                        return cell.getStringCellValue().trim();
                    } catch (Exception e) {
                        try {
                            double numValue = cell.getNumericCellValue();
                            if (numValue == Math.floor(numValue)) {
                                return String.valueOf((long) numValue);
                            }
                            return String.valueOf(numValue);
                        } catch (Exception ex) {
                            return null;
                        }
                    }

                case BLANK:
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("[ExcelParser] Error reading cell value: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get cell value as LocalDate
     */
    public LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (cell.getCellType() == CellType.STRING) {
                String dateStr = cell.getStringCellValue().trim();
                return LocalDate.parse(dateStr);
            }
        } catch (Exception e) {
            log.warn("[ExcelParser] Error parsing date from cell: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get cell value as Integer
     */
    public Integer getCellValueAsInteger(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (!value.isEmpty()) {
                    return Integer.parseInt(value);
                }
            }
        } catch (Exception e) {
            log.warn("[ExcelParser] Error parsing integer from cell: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get cell value as Double
     */
    public Double getCellValueAsDouble(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (!value.isEmpty()) {
                    return Double.parseDouble(value);
                }
            }
        } catch (Exception e) {
            log.warn("[ExcelParser] Error parsing double from cell: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get cell value as Boolean
     */
    public Boolean getCellValueAsBoolean(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.BOOLEAN) {
                return cell.getBooleanCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim().toLowerCase();
                if (value.equals("true") || value.equals("yes") || value.equals("1") ||
                        value.equals("نعم") || value.equals("صحيح")) {
                    return true;
                } else if (value.equals("false") || value.equals("no") || value.equals("0") ||
                        value.equals("لا") || value.equals("خطأ")) {
                    return false;
                }
            } else if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue() != 0;
            }
        } catch (Exception e) {
            log.warn("[ExcelParser] Error parsing boolean from cell: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Find column index by header name (case-insensitive, handles Arabic/English)
     * 
     * ENHANCED: Handles multi-line headers (e.g., "الاسم الكامل\nfull_name")
     */
    public Integer findColumnIndex(Row headerRow, String... headerNames) {
        if (headerRow == null || headerNames == null || headerNames.length == 0) {
            return null;
        }

        // Pass 1: Look for exact matches (most robust)
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null)
                continue;
            String headerValue = getCellValueAsString(cell);
            if (headerValue == null)
                continue;
            headerValue = headerValue.replace("*", "").trim();
            String[] headerParts = headerValue.split("[\\r\\n]+");

            for (String name : headerNames) {
                String nameTrimmed = name.trim();
                // Check full value exact
                if (headerValue.equalsIgnoreCase(nameTrimmed))
                    return i;
                // Check parts exact
                for (String part : headerParts) {
                    if (part.trim().equalsIgnoreCase(nameTrimmed))
                        return i;
                }
            }
        }

        // Pass 2: Look for partial matches (if exact not found)
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null)
                continue;
            String headerValue = getCellValueAsString(cell);
            if (headerValue == null)
                continue;
            headerValue = headerValue.replace("*", "").trim();
            String headerLower = headerValue.toLowerCase();
            String[] headerParts = headerValue.split("[\\r\\n]+");

            for (String name : headerNames) {
                String nameLower = name.trim().toLowerCase();
                if (nameLower.length() < 3)
                    continue; // Avoid matching extremely short keywords like 'id' partially

                if (headerLower.contains(nameLower))
                    return i;
                for (String part : headerParts) {
                    if (part.toLowerCase().contains(nameLower))
                        return i;
                }
            }
        }

        return null;
    }
}
