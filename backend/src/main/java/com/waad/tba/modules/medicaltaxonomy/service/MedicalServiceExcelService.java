package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportError;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportSummary;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for importing Medical Services from Excel files
 * 
 * Expected Excel format (New Standard):
 * | code | name | category_code | description | base_price | active |
 * 
 * Also supports legacy format:
 * | code | nameAr | categoryCode | priceLyd | active |
 * 
 * Business Rules:
 * - code: unique, required
 * - name: required (Arabic-only system)
 * - category_code: must exist in database
 * - base_price: must be >= 0 (optional)
 * - Mode: upsert (update if exists, insert if not)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MedicalServiceExcelService {

    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;

    /**
     * Import medical services from Excel file
     */
    @Transactional
    public ExcelImportResultDto importFromExcel(MultipartFile file) {
        log.info("[MedicalServiceExcel] Starting import from file: {}", file.getOriginalFilename());

        // Validate file
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

            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessRuleException("الملف لا يحتوي على صف رأس (Header)");
            }

            Map<String, Integer> columnMap = mapColumns(headerRow);
            validateRequiredColumns(columnMap);

            // Load categories cache
            Map<String, MedicalCategory> categoryCache = loadCategoryCache();

            // Process data rows
            int lastRow = sheet.getLastRowNum();
            log.info("[MedicalServiceExcel] Processing {} rows", lastRow);

            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                summary.setTotal(summary.getTotal() + 1);

                try {
                    processRow(row, rowNum + 1, columnMap, categoryCache, summary);
                } catch (Exception e) {
                    log.error("[MedicalServiceExcel] Error processing row {}: {}", rowNum + 1, e.getMessage());
                    summary.setFailed(summary.getFailed() + 1);
                    summary.getErrors().add(ImportError.builder()
                            .row(rowNum + 1)
                            .error(e.getMessage())
                            .build());
                }
            }

            String message = buildSuccessMessage(summary);
            log.info("[MedicalServiceExcel] Import completed: {}", message);

            return ExcelImportResultDto.builder()
                    .success(true)
                    .summary(summary)
                    .message(message)
                    .build();

        } catch (IOException e) {
            log.error("[MedicalServiceExcel] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        } catch (Exception e) {
            log.error("[MedicalServiceExcel] Import failed", e);
            throw new BusinessRuleException("فشل استيراد البيانات: " + e.getMessage());
        }
    }

    /**
     * Process single row
     */
    private void processRow(Row row, int rowNum, Map<String, Integer> columnMap,
            Map<String, MedicalCategory> categoryCache, ImportSummary summary) {

        // Extract data from row - support both new and legacy column names
        String code = getCellValueAsString(row, columnMap.get("code"));
        // Support both 'name' (new) and 'nameAr' (legacy)
        String name = getCellValueAsString(row, columnMap.get("name"));
        if (name == null || name.trim().isEmpty()) {
            name = getCellValueAsString(row, columnMap.get("nameAr"));
        }
        String categoryCode = getCellValueAsString(row, columnMap.get("categoryCode"));
        String description = getCellValueAsString(row, columnMap.get("description"));
        BigDecimal price = getCellValueAsDecimal(row, columnMap.get("priceLyd"));
        Boolean active = getCellValueAsBoolean(row, columnMap.get("active"));

        // Validate required fields
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessRuleException("الرمز (code) مطلوب");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessRuleException("اسم الخدمة (name) مطلوب");
        }

        // Get category
        MedicalCategory category = null;
        if (categoryCode != null && !categoryCode.trim().isEmpty()) {
            category = categoryCache.get(categoryCode.trim());
            if (category == null) {
                throw new BusinessRuleException("التصنيف غير موجود: " + categoryCode);
            }
        }

        // Check if service exists
        MedicalService existingService = serviceRepository.findByCode(code.trim()).orElse(null);

        if (existingService != null) {
            // Update existing service
            existingService.setName(name.trim());
            if (category != null) {
                existingService.setCategoryId(category.getId());
            }
            if (description != null && !description.trim().isEmpty()) {
                existingService.setDescription(description.trim());
            }
            if (price != null) {
                existingService.setBasePrice(price);
            }
            if (active != null) {
                existingService.setActive(active);
            }
            existingService.setUpdatedAt(LocalDateTime.now());

            serviceRepository.save(existingService);
            summary.setUpdated(summary.getUpdated() + 1);
            log.debug("[MedicalServiceExcel] Updated service: {}", code);

        } else {
            // Insert new service
            if (category == null) {
                throw new BusinessRuleException("التصنيف مطلوب للخدمات الجديدة");
            }

            MedicalService newService = MedicalService.builder()
                    .code(code.trim())
                    .name(name.trim())
                    .categoryId(category.getId())
                    .description(description != null ? description.trim() : null)
                    .basePrice(price)
                    .active(active != null ? active : true)
                    .build();

            serviceRepository.save(newService);
            summary.setInserted(summary.getInserted() + 1);
            log.debug("[MedicalServiceExcel] Inserted service: {}", code);
        }
    }

    /**
     * Map column names to indices
     * يدعم أسماء الأعمدة بالعربي والإنجليزي - الجديدة والقديمة
     */
    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        for (Cell cell : headerRow) {
            String columnName = cell.getStringCellValue().trim().toLowerCase();

            // Code column (required)
            if (columnName.equals("code") || columnName.equals("الرمز") || columnName.equals("كود")
                    || columnName.equals("service_code") || columnName.equals("رمز الخدمة")) {
                columnMap.put("code", cell.getColumnIndex());
            }
            // Name column (new standard - unified name)
            else if (columnName.equals("name") || columnName.equals("اسم الخدمة")) {
                columnMap.put("name", cell.getColumnIndex());
            }
            // Legacy nameAr column (for backward compatibility)
            else if (columnName.equals("namear") || columnName.equals("name_ar") || columnName.equals("الاسم")
                    || columnName.equals("الاسم بالعربية")) {
                columnMap.put("nameAr", cell.getColumnIndex());
            }
            // Category column
            else if (columnName.equals("category_code") || columnName.equals("categorycode")
                    || columnName.equals("رمز التصنيف") || columnName.equals("category") || columnName.equals("التصنيف")
                    || columnName.equals("الفئة") || columnName.equals("categoryname")) {
                columnMap.put("categoryCode", cell.getColumnIndex());
            }
            // Description column
            else if (columnName.equals("description") || columnName.equals("الوصف")) {
                columnMap.put("description", cell.getColumnIndex());
            }
            // Price column
            else if (columnName.equals("base_price") || columnName.equals("baseprice") || columnName.equals("pricelyd")
                    || columnName.equals("price_lyd") || columnName.equals("price") || columnName.equals("السعر")
                    || columnName.equals("السعر المرجعي")) {
                columnMap.put("priceLyd", cell.getColumnIndex());
            }
            // Active column
            else if (columnName.equals("active") || columnName.equals("نشط") || columnName.equals("الحالة")) {
                columnMap.put("active", cell.getColumnIndex());
            }
        }

        return columnMap;
    }

    /**
     * Validate required columns exist
     */
    private void validateRequiredColumns(Map<String, Integer> columnMap) {
        List<String> missing = new ArrayList<>();

        if (!columnMap.containsKey("code")) {
            missing.add("code (الرمز)");
        }
        // Accept either 'name' (new) or 'nameAr' (legacy)
        if (!columnMap.containsKey("name") && !columnMap.containsKey("nameAr")) {
            missing.add("name (اسم الخدمة)");
        }

        if (!missing.isEmpty()) {
            throw new BusinessRuleException("أعمدة مطلوبة مفقودة: " + String.join(", ", missing));
        }
    }

    /**
     * Load all categories into cache
     * يدعم البحث بالكود والاسم بالعربي والاسم بالإنجليزي
     */
    private Map<String, MedicalCategory> loadCategoryCache() {
        Map<String, MedicalCategory> cache = new HashMap<>();
        List<MedicalCategory> categories = categoryRepository.findAll();

        for (MedicalCategory category : categories) {
            // البحث بالكود
            if (category.getCode() != null) {
                cache.put(category.getCode().trim(), category);
            }
            // البحث بالاسم
            if (category.getName() != null) {
                cache.put(category.getName().trim(), category);
            }
        }

        log.info("[MedicalServiceExcel] Loaded {} categories into cache (with name lookups)", cache.size());
        return cache;
    }

    /**
     * Get cell value as string
     */
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

    /**
     * Get cell value as decimal
     */
    private BigDecimal getCellValueAsDecimal(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }

        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                try {
                    yield new BigDecimal(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /**
     * Get cell value as boolean
     */
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

    /**
     * Check if row is empty
     */
    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if file is Excel
     */
    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        return filename.endsWith(".xlsx") || filename.endsWith(".xls");
    }

    /**
     * Build success message
     */
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
