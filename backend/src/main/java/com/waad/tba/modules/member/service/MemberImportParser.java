package com.waad.tba.modules.member.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.waad.tba.modules.member.entity.Member.Gender;
import com.waad.tba.modules.member.entity.Member.Relationship;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility component for parsing Excel cells and rows.
 * Handles normalization, date parsing, and gender resolution.
 */
@Slf4j
@Component
public class MemberImportParser {

    public String cleanColumnName(String input) {
        if (input == null) {
            return "";
        }

        return input
                .replaceAll("[\r\n]+", " ")
                .replace("\u00A0", " ")
                .replace("\u200B", "")
                .replace("\u200E", "")
                .replace("\u200F", "")
                .replace("\uFEFF", "")
                .replaceAll("\\*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String normalizeExcelValue(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\r\\n]+", " ")
                .replace("\u00A0", " ")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\u200E", "")
                .replace("\u200F", "")
                .replace("\u202A", "")
                .replace("\u202B", "")
                .replace("\u202C", "")
                .replace("\uFEFF", "")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا');
    }

    public String getCellStringValue(Cell cell) {
        if (cell == null)
            return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    public boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    public Gender parseGender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.toLowerCase().trim();
        if (v.contains("male") || v.contains("ذكر") || v.equals("m")) {
            return Gender.MALE;
        }
        if (v.contains("female") || v.contains("أنثى") || v.equals("f")) {
            return Gender.FEMALE;
        }
        return null;
    }

    public LocalDate parseDate(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e1) {
            try {
                String[] parts = value.split("[/\\-]");
                if (parts.length == 3) {
                    int day = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    int year = Integer.parseInt(parts[2]);
                    if (year < 100)
                        year += 2000;
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception e2) {
                log.warn("Could not parse date: {}", value);
            }
        }
        return null;
    }

    public Relationship parseRelationship(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String val = normalizeExcelValue(value).toLowerCase();
        if (val.contains("موظف") || val.contains("موظفه") || val.contains("موظفة") || val.contains("مشترك") || val.contains("رئيسي")) {
            return null; // Principal
        }
        if (val.contains("زوجة") || val.contains("زوجه") || val.contains("زوجة شهيد") || val.contains("زوجة شهيد")) {
            return Relationship.WIFE;
        }
        if (val.equals("زوج")) {
            return Relationship.HUSBAND;
        }
        if (val.contains("ابن") || val.contains("ولد") || val.contains("ابناء")) {
            return Relationship.SON;
        }
        if (val.contains("ابنة") || val.contains("ابنه") || val.contains("بنت")) {
            return Relationship.DAUGHTER;
        }
        if (val.contains("أب") || val.contains("اب") || val.contains("والد")) {
            return Relationship.FATHER;
        }
        if (val.contains("أم") || val.contains("ام") || val.contains("والدة") || val.contains("والده")) {
            return Relationship.MOTHER;
        }
        return null;
    }



    public String getFieldValue(Row row, Map<String, Integer> fieldToColumnIndex, String field) {
        Integer colIndex = fieldToColumnIndex.get(field);
        if (colIndex == null)
            return null;
        return normalizeExcelValue(getCellStringValue(row.getCell(colIndex)));
    }
}
