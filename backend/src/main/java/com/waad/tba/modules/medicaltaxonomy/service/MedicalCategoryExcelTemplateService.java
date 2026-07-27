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
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
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
    private static final String APPROVED_CATEGORIES_SHEET = "تصنيفات الخدمات المعتمدة";
    
    private final ExcelTemplateService templateService;
    private final ExcelParserService parserService;
    private final MedicalCategoryRepository categoryRepository;
    private final com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository serviceRepository;
    private final com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    private final com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository pricingItemRepository;

    /** Mirrors MedicalCategoryService#assertNotFinanciallyLinked — a category
     * referenced by a service, coverage rule, or contract price must not be
     * silently deactivated by a bulk import, since findByActiveTrue() feeds
     * live coverage/pricing resolution. */
    private boolean isFinanciallyLinked(Long categoryId) {
        return serviceRepository.countByCategoryId(categoryId) > 0
                || benefitPolicyRuleRepository.countByMedicalCategoryId(categoryId) > 0
                || pricingItemRepository.countByMedicalCategoryId(categoryId) > 0;
    }
    
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
        
        try (Workbook workbook = parserService.openWorkbook(file)) {
            Sheet approvedSheet = workbook.getSheet(APPROVED_CATEGORIES_SHEET);
            boolean approvedFormat = approvedSheet != null;
            Sheet dataSheet = approvedFormat ? approvedSheet : parserService.getDataSheet(workbook);
            int firstDataRow = approvedFormat ? 1 : 2;

            validateHeaders(dataSheet, approvedFormat);
            List<ParsedCategoryRow> parsedRows = parseAndValidateRows(dataSheet, firstDataRow, approvedFormat, errors);
            summary.setTotalRows(parsedRows.size());

            if (!errors.isEmpty() || parsedRows.isEmpty()) {
                summary.setFailed(errors.size());
                String message = parsedRows.isEmpty()
                        ? "لم تُقرأ أي تصنيفات. الملف غير مطابق لقالب التصنيفات أو ملف الاعتماد النهائي."
                        : "لم يتم الحفظ: يوجد " + errors.size() + " أخطاء. صُحح الملف ثم أعد الاستيراد.";
                return ExcelImportResult.builder().summary(summary).errors(errors).success(false)
                        .messageAr(message).messageEn("Import validation failed; no data was changed").build();
            }

            // Destructive replacement is allowed only after the entire workbook validates.
            // Categories still referenced by a medical service, coverage rule, or
            // contract price are never deactivated here — findByActiveTrue() feeds
            // live coverage/pricing resolution, so silently dropping one out from
            // under an in-force policy would be a financial-integrity bug, not a
            // catalog cleanup.
            if (clearOld) {
                log.info("[MedicalCategoryImport] Validation passed; deactivating old, unreferenced categories...");
                List<MedicalCategory> allCategories = categoryRepository.findAll();
                int skipped = 0;
                for (MedicalCategory c : allCategories) {
                    if (isFinanciallyLinked(c.getId())) {
                        skipped++;
                        continue;
                    }
                    c.setActive(false);
                }
                categoryRepository.saveAll(allCategories);
                if (skipped > 0) {
                    log.warn("[MedicalCategoryImport] Skipped deactivating {} categories with active financial references", skipped);
                }
            }

            Map<String, MedicalCategory> savedByCode = new HashMap<>();
            for (ParsedCategoryRow parsed : parsedRows) {
                MedicalCategory category = categoryRepository.findByCode(parsed.code()).orElseGet(MedicalCategory::new);
                boolean created = category.getId() == null;
                if (created) category.setCode(parsed.code());
                category.setName(parsed.name());
                category.setNameAr(parsed.name());
                boolean requestedInactive = !parsed.active();
                if (requestedInactive && !created && isFinanciallyLinked(category.getId())) {
                    log.warn("[MedicalCategoryImport] Row for code {} requested inactive but category has active financial references; keeping active", parsed.code());
                    category.setActive(true);
                } else {
                    category.setActive(parsed.active());
                }
                category.setDeleted(false);
                category.setDeletedAt(null);
                category.setDeletedBy(null);
                category.setContexts(new HashSet<>(parsed.contexts()));
                category.setParentId(null);
                savedByCode.put(parsed.code(), categoryRepository.save(category));
                if (created) summary.setCreated(summary.getCreated() + 1);
                else summary.setUpdated(summary.getUpdated() + 1);
            }

            // Resolve hierarchy after every imported code exists.
            for (ParsedCategoryRow parsed : parsedRows) {
                if (parsed.parentCode() == null) continue;
                MedicalCategory parent = Optional.ofNullable(savedByCode.get(parsed.parentCode()))
                        .orElseGet(() -> categoryRepository.findByCode(parsed.parentCode()).orElseThrow());
                MedicalCategory child = savedByCode.get(parsed.code());
                child.setParentId(parent.getId());
                categoryRepository.save(child);
            }

            log.info("[MedicalCategoryImport] Import completed: {} total, {} created, {} updated, {} failed",
                    summary.getTotalRows(), summary.getCreated(), summary.getUpdated(), summary.getFailed());

            return ExcelImportResult.builder()
                    .summary(summary)
                    .errors(errors)
                    .success(true)
                    .messageAr(String.format("تم استيراد %d تصنيف وتفعيلها بنجاح", summary.getCreated() + summary.getUpdated()))
                    .messageEn(String.format("Imported %d categories", summary.getCreated() + summary.getUpdated()))
                    .build();
        } catch (IOException e) {
            log.error("[MedicalCategoryImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel");
        }
    }

    private void validateHeaders(Sheet sheet, boolean approvedFormat) {
        Row header = sheet.getRow(0);
        String first = header == null ? null : getCellValue(header, approvedFormat ? 1 : 0);
        String second = header == null ? null : getCellValue(header, approvedFormat ? 2 : 1);
        boolean valid = approvedFormat
                ? "الكود المعتمد".equals(first) && "الاسم النهائي المعتمد".equals(second)
                : first != null && first.toLowerCase().contains("code") && second != null && second.toLowerCase().contains("name");
        if (!valid) throw new BusinessRuleException(
                "أعمدة الملف غير معروفة. استخدم قالب النظام أو ملف «التصنيفات_النهائية_المعتمدة_للوثائق.xlsx» دون تغيير رؤوس الأعمدة.");
    }

    private List<ParsedCategoryRow> parseAndValidateRows(Sheet sheet, int firstRow, boolean approvedFormat,
                                                          List<ImportError> errors) {
        List<ParsedCategoryRow> result = new ArrayList<>();
        Set<String> inputCodes = new HashSet<>();
        for (int rowIndex = firstRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || parserService.isEmptyRow(row)) continue;
            int codeColumn = approvedFormat ? 1 : 0;
            int nameColumn = approvedFormat ? 2 : 1;
            String code = clean(getCellValue(row, codeColumn));
            String name = clean(getCellValue(row, nameColumn));
            String parentCode = approvedFormat ? null : clean(getCellValue(row, 2));
            String activeValue = getCellValue(row, approvedFormat ? 4 : 3);
            Set<CategoryContext> contexts;
            try {
                contexts = approvedFormat ? parseContexts(getCellValue(row, 3)) : Set.of(CategoryContext.ANY);
            } catch (IllegalArgumentException ex) {
                addError(errors, rowIndex + 1, "السياقات المسموحة", getCellValue(row, 3), ex.getMessage());
                continue;
            }
            if (code == null) { addError(errors, rowIndex + 1, "رمز التصنيف", null, "رمز التصنيف مطلوب"); continue; }
            if (name == null) { addError(errors, rowIndex + 1, "اسم التصنيف", null, "اسم التصنيف مطلوب"); continue; }
            if (!inputCodes.add(code)) { addError(errors, rowIndex + 1, "رمز التصنيف", code, "رمز مكرر داخل الملف"); continue; }
            boolean active = approvedFormat ? "معتمد".equals(clean(activeValue)) : parseBoolean(activeValue, true);
            result.add(new ParsedCategoryRow(code, name, parentCode, active, contexts, rowIndex + 1));
        }
        for (ParsedCategoryRow row : result) {
            if (row.parentCode() != null && !inputCodes.contains(row.parentCode())
                    && categoryRepository.findByCode(row.parentCode()).isEmpty()) {
                addError(errors, row.rowNumber(), "رمز التصنيف الأب", row.parentCode(), "التصنيف الأب غير موجود في الملف أو النظام");
            }
        }
        return result;
    }

    private Set<CategoryContext> parseContexts(String raw) {
        String value = clean(raw);
        if (value == null) return Set.of(CategoryContext.ANY);
        Set<CategoryContext> contexts = new LinkedHashSet<>();
        for (String token : value.split("\\+")) {
            String normalized = token.trim().toUpperCase();
            try { contexts.add(CategoryContext.valueOf(normalized)); }
            catch (Exception ex) { throw new IllegalArgumentException("سياق غير معتمد: " + token.trim()); }
        }
        return contexts.isEmpty() ? Set.of(CategoryContext.ANY) : contexts;
    }

    private void addError(List<ImportError> errors, int row, String field, String value, String message) {
        errors.add(ImportError.builder().rowNumber(row).fieldName(field).value(value)
                .errorType(ErrorType.INVALID_FORMAT).messageAr(message).messageEn(message).build());
    }

    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private record ParsedCategoryRow(String code, String name, String parentCode, boolean active,
                                     Set<CategoryContext> contexts, int rowNumber) {}
    
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
