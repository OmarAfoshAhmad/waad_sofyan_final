package com.waad.tba.modules.member.service;

import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.waad.tba.modules.member.dto.MemberImportPreviewDto.ImportValidationErrorDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles mapping between Excel columns and Member fields/attributes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberImportMapper {

    private final MemberImportParser parser;

    public int detectHeaderRowNumber(Sheet sheet) {
        int scanLimit = Math.min(sheet.getLastRowNum(), 20);
        int bestRow = 0;
        int bestScore = Integer.MIN_VALUE;

        for (int rowIndex = 0; rowIndex <= scanLimit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            int score = 0;
            for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                String raw = parser.getCellStringValue(row.getCell(cellIndex));
                if (raw == null || raw.isBlank()) {
                    continue;
                }

                if (containsAny(raw, MemberImportFieldConfig.MANDATORY_COLUMNS.get(0))) {
                    score += 10;
                }

                for (String[] variants : MemberImportFieldConfig.MANDATORY_COLUMNS) {
                    if (containsAny(raw, variants)) {
                        score += 2;
                    }
                }

                for (String[] variants : MemberImportFieldConfig.OPTIONAL_FIELD_MAPPINGS.values()) {
                    if (containsAny(raw, variants)) {
                        score += 1;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestRow = rowIndex;
            }
        }

        log.info("📌 Header row detection: chosen row={} score={}", bestRow, bestScore);
        return bestRow;
    }

    public void mapColumnToField(String colName, int index,
            Map<String, Integer> fieldToColumnIndex, Map<String, String> columnMappings) {

        if (colName == null || colName.isBlank()) {
            return;
        }

        for (int i = 0; i < MemberImportFieldConfig.MANDATORY_COLUMNS.size(); i++) {
            String[] variants = MemberImportFieldConfig.MANDATORY_COLUMNS.get(i);
            String fieldName = "fullName";

            for (String variant : variants) {
                if (isSmartMatch(colName, variant)) {
                    fieldToColumnIndex.put(fieldName, index);
                    columnMappings.put(colName, fieldName);
                    return;
                }
            }
        }

        for (Map.Entry<String, String[]> entry : MemberImportFieldConfig.OPTIONAL_FIELD_MAPPINGS.entrySet()) {
            for (String variant : entry.getValue()) {
                if (isSmartMatch(colName, variant)) {
                    fieldToColumnIndex.put(entry.getKey(), index);
                    columnMappings.put(colName, entry.getKey());
                    return;
                }
            }
        }

        for (Map.Entry<String, String[]> entry : MemberImportFieldConfig.ATTRIBUTE_MAPPINGS.entrySet()) {
            for (String variant : entry.getValue()) {
                if (isSmartMatch(colName, variant)) {
                    fieldToColumnIndex.put("attr:" + entry.getKey(), index);
                    columnMappings.put(colName, "attribute:" + entry.getKey());
                    return;
                }
            }
        }

        String normalized = colName.replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        if (!normalized.isBlank()) {
            fieldToColumnIndex.put("attr:" + normalized, index);
            columnMappings.put(colName, "attribute:" + normalized);
        }
    }

    public void validateMandatoryColumns(Map<String, Integer> fieldToColumnIndex,
            List<ImportValidationErrorDto> errors) {

        if (!fieldToColumnIndex.containsKey("fullName")) {
            errors.add(ImportValidationErrorDto.builder()
                    .rowNumber(0).field("header").severity("ERROR")
                    .message("Missing mandatory column: full_name / name (الاسم الكامل)").build());
        }
    }

    private boolean containsAny(String raw, String[] variants) {
        for (String variant : variants) {
            if (isSmartMatch(raw, variant)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSmartMatch(String header, String variant) {
        if (header == null || variant == null) return false;

        String cleanHeader = cleanString(header);
        String cleanVariant = cleanString(variant);

        if (cleanHeader.isEmpty() || cleanVariant.isEmpty()) return false;

        // Exact match of cleaned strings
        if (cleanHeader.equals(cleanVariant)) return true;

        // Substring match:
        // Only allow substring matching if variant is longer (like "fullname", "employeeid") to prevent false positives for short keys
        if (cleanVariant.length() >= 4) {
            return cleanHeader.contains(cleanVariant);
        }

        return false;
    }

    private String cleanString(String input) {
        if (input == null) return "";
        String s = input.toLowerCase();
        // Remove non-alphanumeric and non-Arabic characters
        s = s.replaceAll("[^a-z0-9\\u0621-\\u064A]", "");
        // Normalize Arabic characters to handle common variants
        s = s.replace('أ', 'ا')
             .replace('إ', 'ا')
             .replace('آ', 'ا')
             .replace('ة', 'ه')
             .replace('ى', 'ي');
        return s;
    }

    public Integer findColumnIndexByName(String columnName, Map<Integer, String> columnIndexToName) {
        String lowerName = columnName.toLowerCase();
        for (Map.Entry<Integer, String> entry : columnIndexToName.entrySet()) {
            if (entry.getValue().equals(lowerName) || isSmartMatch(entry.getValue(), columnName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
