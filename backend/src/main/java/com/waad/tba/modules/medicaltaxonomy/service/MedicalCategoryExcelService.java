package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportError;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportSummary;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for importing Medical Categories from Excel files
 * 
 * Expected Excel format (Canonical Standard):
 * | code | name | active |
 * Legacy parent_code columns are accepted but ignored.
 * 
 * Also supports legacy format:
 * | code | nameAr | active |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalCategoryExcelService {

    private final MedicalCategoryRepository categoryRepository;

    @Transactional
    public ExcelImportResultDto importFromExcel(MultipartFile file) {
        log.info("[MedicalCategoryExcel] Starting import from file: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }

        if (!isExcelFile(file)) {
            throw new BusinessRuleException("نوع الملف غير صحيح. يجب أن يكون ملف Excel (.xlsx أو .xls)");
        }

        ImportSummary summary = ImportSummary.builder()
                .total(0)
                .inserted(0)
                .updated(0)
                .skipped(0)
                .failed(0)
                .errors(new ArrayList<>())
                .build();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessRuleException("الملف لا يحتوي على صف رأس (Header)");
            }

            Map<String, Integer> columnMap = mapColumns(headerRow);
            validateRequiredColumns(columnMap);

            int lastRow = sheet.getLastRowNum();
            log.info("[MedicalCategoryExcel] Processing {} rows", lastRow);

            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                summary.setTotal(summary.getTotal() + 1);

                try {
                    processRow(row, rowNum + 1, columnMap, summary);
                } catch (Exception e) {
                    log.error("[MedicalCategoryExcel] Error processing row {}: {}", rowNum + 1, e.getMessage());
                    summary.setFailed(summary.getFailed() + 1);
                    summary.getErrors().add(ImportError.builder()
                            .row(rowNum + 1)
                            .error(e.getMessage())
                            .build());
                }
            }

            String message = buildSuccessMessage(summary);
            log.info("[MedicalCategoryExcel] Import completed: {}", message);

            return ExcelImportResultDto.builder()
                    .success(true)
                    .summary(summary)
                    .message(message)
                    .build();

        } catch (IOException e) {
            log.error("[MedicalCategoryExcel] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        } catch (Exception e) {
            log.error("[MedicalCategoryExcel] Import failed", e);
            throw new BusinessRuleException("فشل استيراد البيانات: " + e.getMessage());
        }
    }

    private void processRow(Row row, int rowNum, Map<String, Integer> columnMap, ImportSummary summary) {
        String code = getCellValueAsString(row, columnMap.get("code"));
        // Support both 'name' (new) and 'nameAr' (legacy)
        String name = getCellValueAsString(row, columnMap.get("name"));
        if (name == null || name.trim().isEmpty()) {
            name = getCellValueAsString(row, columnMap.get("nameAr"));
        }
        Boolean active = getCellValueAsBoolean(row, columnMap.get("active"));

        if (code == null || code.trim().isEmpty()) {
            throw new BusinessRuleException("الرمز (code) مطلوب");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessRuleException("اسم التصنيف (name) مطلوب");
        }
        Long parentId = null; // canonical flat taxonomy; legacy parent_code ignored

        MedicalCategory existingCategory = categoryRepository.findByCode(code.trim()).orElse(null);

        if (existingCategory != null) {
            existingCategory.setName(name.trim());
            existingCategory.setParentId(parentId);
            if (active != null) {
                existingCategory.setActive(active);
            }
            existingCategory.setUpdatedAt(LocalDateTime.now());
            
            categoryRepository.save(existingCategory);
            summary.setUpdated(summary.getUpdated() + 1);
            log.debug("[MedicalCategoryExcel] Updated category: {}", code);
            
        } else {
            MedicalCategory newCategory = MedicalCategory.builder()
                    .code(code.trim())
                    .name(name.trim())
                    .parentId(parentId)
                    .active(active != null ? active : true)
                    .build();
            
            categoryRepository.save(newCategory);
            summary.setInserted(summary.getInserted() + 1);
            log.debug("[MedicalCategoryExcel] Inserted category: {}", code);
        }
    }

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        
        for (Cell cell : headerRow) {
            String columnName = cell.getStringCellValue().trim().toLowerCase();
            
            // Code column (required)
            if (columnName.equals("code") || columnName.equals("الرمز") || columnName.equals("كود") || columnName.equals("رمز التصنيف")) {
                columnMap.put("code", cell.getColumnIndex());
            } 
            // Name column (new standard - unified name)
            else if (columnName.equals("name") || columnName.equals("اسم التصنيف") || columnName.equals("الاسم")) {
                columnMap.put("name", cell.getColumnIndex());
            }
            // Legacy nameAr column (for backward compatibility)
            else if (columnName.equals("namear") || columnName.equals("name_ar") || columnName.equals("الاسم بالعربية")) {
                columnMap.put("nameAr", cell.getColumnIndex());
            } 
            // Legacy parent_code column is accepted but ignored.
            else if (columnName.equals("parent_code") || columnName.equals("parentcode") || columnName.equals("رمز التصنيف الأب") || columnName.equals("الأب")) {
                columnMap.put("parentCode", cell.getColumnIndex());
            }
            // Active column
            else if (columnName.equals("active") || columnName.equals("نشط") || columnName.equals("الحالة")) {
                columnMap.put("active", cell.getColumnIndex());
            }
        }
        
        return columnMap;
    }

    private void validateRequiredColumns(Map<String, Integer> columnMap) {
        List<String> missing = new ArrayList<>();
        
        if (!columnMap.containsKey("code")) {
            missing.add("code (الرمز)");
        }
        // Accept either 'name' (new) or 'nameAr' (legacy)
        if (!columnMap.containsKey("name") && !columnMap.containsKey("nameAr")) {
            missing.add("name (اسم التصنيف)");
        }
        
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("أعمدة مطلوبة مفقودة: " + String.join(", ", missing));
        }
    }

    private String getCellValueAsString(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private Integer getCellValueAsInteger(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Integer.parseInt(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private Boolean getCellValueAsBoolean(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> {
                String value = cell.getStringCellValue().trim().toLowerCase();
                yield value.equals("true") || value.equals("yes") || value.equals("نعم") || value.equals("1");
            }
            case NUMERIC -> cell.getNumericCellValue() == 1.0;
            default -> null;
        };
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        return filename.endsWith(".xlsx") || filename.endsWith(".xls");
    }

    private String buildSuccessMessage(ImportSummary summary) {
        StringBuilder msg = new StringBuilder();
        msg.append("تم استيراد البيانات بنجاح. ");
        
        if (summary.getInserted() > 0) {
            msg.append(summary.getInserted()).append(" سجل جديد، ");
        }
        if (summary.getUpdated() > 0) {
            msg.append(summary.getUpdated()).append(" سجل محدّث، ");
        }
        if (summary.getFailed() > 0) {
            msg.append(summary.getFailed()).append(" سجل فشل");
        }
        
        return msg.toString();
    }
}

