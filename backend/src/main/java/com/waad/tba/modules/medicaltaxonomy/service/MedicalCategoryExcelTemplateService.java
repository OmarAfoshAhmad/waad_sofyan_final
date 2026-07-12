package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.ErrorType;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportSummary;
import com.waad.tba.common.excel.dto.ExcelLookupData;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn.ColumnType;
import com.waad.tba.common.excel.service.ExcelParserService;
import com.waad.tba.common.excel.service.ExcelTemplateService;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Medical Categories Excel Template Generator and Import Service
 * 
 * STRICT RULES:
 * - Templates MUST be downloaded from system
 * - Create-only mode (upsert if code exists)
 * - Category code is required and unique
 * - Simple flat structure (no parent categories in Phase 1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalCategoryExcelTemplateService {
    
    private final ExcelTemplateService templateService;
    private final ExcelParserService parserService;
    private final MedicalCategoryRepository categoryRepository;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generate Medical Categories import template
     */
    public byte[] generateTemplate() throws IOException {
        log.info("[MedicalCategoryTemplate] Generating Excel template");
        
        List<ExcelTemplateColumn> columns = buildColumnDefinitions();
        List<ExcelLookupData> lookups = Collections.emptyList(); // No lookups for categories
        
        return templateService.generateTemplate("Medical Categories / الفئات الطبية", columns, lookups);
    }
    
    private List<ExcelTemplateColumn> buildColumnDefinitions() {
        return List.of(
            // Category Code - Mandatory & Unique
            ExcelTemplateColumn.builder()
                .name("code")
                .nameAr("رمز التصنيف")
                .type(ColumnType.TEXT)
                .required(true)
                .example("CONSULTATION")
                .description("رمز التصنيف الفريد (إجباري) - لا يمكن تعديله لاحقاً")
                .descriptionAr("رمز التصنيف الفريد (إجباري)")
                .width(18)
                .build(),
                
            // Name - Mandatory (Arabic-only system)
            ExcelTemplateColumn.builder()
                .name("name")
                .nameAr("اسم التصنيف")
                .type(ColumnType.TEXT)
                .required(true)
                .example("استشارات طبية")
                .description("اسم التصنيف (إجباري)")
                .descriptionAr("اسم التصنيف (إجباري)")
                .width(35)
                .build(),
                
            // Parent Code - Optional (for hierarchy)
            ExcelTemplateColumn.builder()
                .name("parent_code")
                .nameAr("رمز التصنيف الأب")
                .type(ColumnType.TEXT)
                .required(false)
                .example("MEDICAL")
                .description("رمز التصنيف الأب للتصنيفات الفرعية (اختياري)")
                .descriptionAr("رمز التصنيف الأب للتصنيفات الفرعية (اختياري)")
                .width(20)
                .build(),
                
            // Active - Optional Boolean
            ExcelTemplateColumn.builder()
                .name("active")
                .nameAr("نشط")
                .type(ColumnType.TEXT)
                .required(false)
                .example("نعم")
                .description("نعم / لا (الافتراضي: نعم)")
                .descriptionAr("هل التصنيف نشط؟ (نعم/لا)")
                .width(12)
                .build()
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT FROM EXCEL
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Import medical categories from Excel template
     */
    @Transactional
    public ExcelImportResult importFromExcel(MultipartFile file, boolean clearOld) {
        log.info("[MedicalCategoryImport] Starting import from file: {}, clearOld: {}", file.getOriginalFilename(), clearOld);
        
        if (clearOld) {
            log.info("[MedicalCategoryImport] Clearing old categories (soft delete)...");
            List<MedicalCategory> allCategories = categoryRepository.findAll();
            categoryRepository.deleteAll(allCategories);
        }
        
        // Validate file
        if (file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }
        
        ImportSummary summary = ImportSummary.builder()
                .totalRows(0)
                .created(0)
                .updated(0)
                .skipped(0)
                .failed(0)
                .build();
        
        List<ImportError> errors = new ArrayList<>();
        
        try {
            Workbook workbook = parserService.openWorkbook(file);
            Sheet dataSheet = parserService.getDataSheet(workbook);
            
            // Process data rows
            int lastRow = dataSheet.getLastRowNum();
            log.info("[MedicalCategoryImport] Processing {} rows", lastRow);
            
            for (int rowNum = 2; rowNum <= lastRow; rowNum++) { // Start from row 2 (after header)
                Row row = dataSheet.getRow(rowNum);
                if (row == null || parserService.isEmptyRow(row)) {
                    continue;
                }
                
                summary.setTotalRows(summary.getTotalRows() + 1);
                
                try {
                    processRow(row, rowNum + 1, summary, errors);
                } catch (Exception e) {
                    log.warn("[MedicalCategoryImport] Row {} failed: {}", rowNum + 1, e.getMessage());
                    summary.setFailed(summary.getFailed() + 1);
                    errors.add(ImportError.builder()
                            .rowNumber(rowNum + 1)
                            .errorType(ErrorType.PROCESSING_ERROR)
                            .messageAr(e.getMessage())
                            .messageEn(e.getMessage())
                            .build());
                }
            }
            
            log.info("[MedicalCategoryImport] Import completed: {} total, {} created, {} updated, {} failed",
                    summary.getTotalRows(), summary.getCreated(), summary.getUpdated(), summary.getFailed());
            
            return ExcelImportResult.builder()
                    .summary(summary)
                    .errors(errors)
                    .success(summary.getCreated() + summary.getUpdated() > 0)
                    .messageAr(String.format("تم استيراد %d فئة", summary.getCreated() + summary.getUpdated()))
                    .messageEn(String.format("Imported %d categories", summary.getCreated() + summary.getUpdated()))
                    .build();
            
        } catch (IOException e) {
            log.error("[MedicalCategoryImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel");
        }
    }
    
    private void processRow(Row row, int rowNumber, ImportSummary summary, List<ImportError> errors) {
        // Read columns based on new template structure
        String code = getCellValue(row, 0);         // Column A: code
        String name = getCellValue(row, 1);         // Column B: name
        String parentCode = getCellValue(row, 2);   // Column C: parent_code
        String activeStr = getCellValue(row, 3);    // Column D: active
        
        // Validate required fields
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessRuleException("رمز التصنيف مطلوب");
        }
        
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessRuleException("اسم التصنيف مطلوب");
        }
        
        // Parse active flag
        boolean active = parseBoolean(activeStr, true); // Default to true
        
        // Resolve parent if provided
        Long parentId = null;
        if (parentCode != null && !parentCode.trim().isEmpty()) {
            Optional<MedicalCategory> parentOpt = categoryRepository.findByCode(parentCode.trim());
            if (parentOpt.isPresent()) {
                parentId = parentOpt.get().getId();
            } else {
                log.warn("[MedicalCategoryImport] Row {}: Parent code '{}' not found, will be created as root", rowNumber, parentCode);
            }
        }
        
        // Check if category exists (upsert logic)
        Optional<MedicalCategory> existingOpt = categoryRepository.findByCode(code.trim());
        
        MedicalCategory category;
        boolean isUpdate = false;
        
        if (existingOpt.isPresent()) {
            // Update existing
            category = existingOpt.get();
            isUpdate = true;
        } else {
            // Create new
            category = new MedicalCategory();
            category.setCode(code.trim());
        }
        
        // Set/Update fields
        category.setName(name.trim());
        category.setParentId(parentId);
        category.setActive(active);
        
        // Save
        categoryRepository.save(category);
        
        if (isUpdate) {
            summary.setUpdated(summary.getUpdated() + 1);
        } else {
            summary.setCreated(summary.getCreated() + 1);
        }
    }
    
    private String getCellValue(Row row, int columnIndex) {
        return parserService.getCellValueAsString(row.getCell(columnIndex));
    }
    
    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        String normalized = value.trim().toLowerCase();
        return normalized.equals("yes") || 
               normalized.equals("نعم") || 
               normalized.equals("نشط") || 
               normalized.equals("true") || 
               normalized.equals("1");
    }
}
