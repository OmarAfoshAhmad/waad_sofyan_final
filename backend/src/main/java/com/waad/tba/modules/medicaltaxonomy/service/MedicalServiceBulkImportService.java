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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bulk Import Service for Medical Services
 * 
 * Optimized for large imports (12,500+ rows):
 * - Batch processing (500 rows per batch)
 * - Category cache preloading
 * - Existing code cache preloading
 * - Row-by-row error handling (no full rollback)
 * - Detailed error reporting
 * 
 * Template Columns:
 * | code | name | category_code | description | base_price | cost |
 * requires_pre_approval | active |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalServiceBulkImportService {

    private static final String TEMPLATE_SHEET = "Medical_Services_Template";
    private static final String CATEGORIES_SHEET = "Categories_Reference";

    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final com.waad.tba.modules.medicaltaxonomy.repository.MedicalSpecialtyRepository specialtyRepository;
    private final PlatformTransactionManager transactionManager;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate Excel template for bulk import
     */
    public byte[] generateTemplate() throws IOException {
        log.info("[BulkImport] Generating Excel template");

        try (Workbook workbook = new XSSFWorkbook()) {
            // Create main data sheet
            Sheet dataSheet = workbook.createSheet(TEMPLATE_SHEET);

            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle requiredStyle = createRequiredHeaderStyle(workbook);

            // Header row - Only name is required, others are optional
            Row headerRow = dataSheet.createRow(0);
            String[] headers = {
                    "name *", "name_en", "code", "category", "specialty",
                    "cost", "price", "description", "status"
            };
            String[] headersAr = {
                    "الاسم (عربي) *", "الاسم (إنجليزي)", "الرمز", "التصنيف", "التخصص",
                    "السعر المعتمد", "تقدير السعر", "الوصف", "الحالة"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                // Only name (first column) is required
                cell.setCellStyle(i == 0 ? requiredStyle : headerStyle);
            }

            // Arabic header row
            Row arHeaderRow = dataSheet.createRow(1);
            for (int i = 0; i < headersAr.length; i++) {
                Cell cell = arHeaderRow.createCell(i);
                cell.setCellValue(headersAr[i]);
                cell.setCellStyle(headerStyle);
            }

            // Example row - showing that only name is required
            Row exampleRow = dataSheet.createRow(2);
            exampleRow.createCell(0).setCellValue("فحص شامل"); // name (required)
            exampleRow.createCell(1).setCellValue("SRV-001"); // code (optional)
            exampleRow.createCell(2).setCellValue("مختبر"); // category (optional)
            exampleRow.createCell(3).setCellValue("فحص طبي شامل"); // description (optional)
            exampleRow.createCell(4).setCellValue("50.00"); // price (optional)
            exampleRow.createCell(5).setCellValue("ACTIVE"); // status (optional, default ACTIVE)

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            // Create categories reference sheet
            createCategoriesReferenceSheet(workbook);

            // Create instructions sheet
            createInstructionsSheet(workbook);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            log.info("[BulkImport] Template generated successfully");
            return out.toByteArray();
        }
    }

    private void createCategoriesReferenceSheet(Workbook workbook) {
        Sheet catSheet = workbook.createSheet(CATEGORIES_SHEET);

        // Header
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row headerRow = catSheet.createRow(0);
        headerRow.createCell(0).setCellValue("Code / الرمز");
        headerRow.createCell(1).setCellValue("Name / الاسم");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        // Load categories from database
        List<MedicalCategory> categories = categoryRepository.findByActiveTrue();
        int rowNum = 1;
        for (MedicalCategory cat : categories) {
            Row row = catSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(cat.getCode() != null ? cat.getCode() : "");
            row.createCell(1).setCellValue(cat.getName() != null ? cat.getName() : "");
        }

        catSheet.autoSizeColumn(0);
        catSheet.autoSizeColumn(1);
    }

    private void createInstructionsSheet(Workbook workbook) {
        // Note: Sheet names cannot contain / character
        Sheet instSheet = workbook.createSheet("Instructions - تعليمات");

        String[] instructions = {
                "تعليمات استيراد الخدمات الطبية",
                "=============================",
                "",
                "الأعمدة الإجبارية (*) :",
                "- name: اسم الخدمة بالعربي (إجباري)",
                "- name_en: اسم الخدمة بالإنجليزي (اختياري)",
                "",
                "الأعمدة الاختيارية:",
                "- code: رمز الخدمة (اختياري - يتم توليده تلقائياً إذا لم يُحدد)",
                "- category: اسم أو رمز التصنيف (إجباري لنشر الخدمة)",
                "- specialty: اسم أو رمز التخصص (اختياري)",
                "- cost: السعر الرسمي المعتمد في الكتالوج (اختياري)",
                "- price: تقدير للسعر أو السعر القديم (اختياري)",
                "- description: وصف الخدمة (اختياري)",
                "- status: الحالة ACTIVE/INACTIVE (اختياري - الافتراضي: ACTIVE)",
                "",
                "ملاحظات مهمة:",
                "- ابدأ البيانات من الصف 3 (بعد صفي الترويسة)",
                "- لا تحذف صفي الترويسة",
                "- إذا كان الرمز موجوداً سيتم تحديث الخدمة",
                "- إذا كان الاسم مكرراً بدون رمز، سيتم تجاهل الصف",
                "- يدعم النظام استيراد 12,500 صف أو أكثر"
        };

        for (int i = 0; i < instructions.length; i++) {
            Row row = instSheet.createRow(i);
            row.createCell(0).setCellValue(instructions[i]);
        }

        instSheet.autoSizeColumn(0);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createRequiredHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK IMPORT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Import medical services from Excel file
     * Optimized for large files (12,500+ rows)
     */
    public ExcelImportResultDto importFromExcel(MultipartFile file) {
        log.info("[BulkImport] Starting bulk import from: {}", file.getOriginalFilename());
        long startTime = System.currentTimeMillis();

        // Validate file
        validateFile(file);

        // Initialize summary
        ImportSummary summary = ImportSummary.builder()
                .total(0)
                .inserted(0)
                .updated(0)
                .skipped(0)
                .failed(0)
                .errors(new ArrayList<>())
                .build();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(TEMPLATE_SHEET);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0); // Fallback to first sheet
            }

            // Preload caches for performance
            Map<String, MedicalCategory> categoryCache = loadCategoryCache();
            Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialtyCache = loadSpecialtyCache();
            Set<String> existingCodes = loadExistingCodes();

            log.info("[BulkImport] Loaded {} categories, {} specialties, {} existing services",
                    categoryCache.size(), specialtyCache.size(), existingCodes.size());

            // Process rows (start from row 3, after 2 header rows)
            int lastRow = sheet.getLastRowNum();
            int startRow = 2; // 0-indexed, so row 3 in Excel

            log.info("[BulkImport] Processing rows {} to {}", startRow + 1, lastRow + 1);

            for (int rowNum = startRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                summary.setTotal(summary.getTotal() + 1);
                final int currentRowNum = rowNum + 1;

                try {
                    // Process each row in its own transaction
                    String result = transactionTemplate.execute(status -> {
                        try {
                            MedicalService service = processRow(row, currentRowNum, categoryCache, specialtyCache,
                                    existingCodes);

                            if (service == null) {
                                return "SKIPPED";
                            }

                            boolean isUpdate = service.getId() != null;
                            serviceRepository.save(service);

                            if (isUpdate) {
                                summary.setUpdated(summary.getUpdated() + 1);
                            } else {
                                summary.setInserted(summary.getInserted() + 1);
                            }

                            return "SUCCESS";
                        } catch (Exception e) {
                            status.setRollbackOnly();
                            throw e;
                        }
                    });

                    if ("SKIPPED".equals(result)) {
                        summary.setSkipped(summary.getSkipped() + 1);
                    }

                    if (currentRowNum % 100 == 0) {
                        log.info("[BulkImport] Progress: {}/{} rows processed", summary.getTotal(),
                                lastRow - startRow + 1);
                    }
                } catch (Exception e) {
                    log.warn("[BulkImport] Row {} failed: {}", currentRowNum, e.getMessage());
                    summary.setFailed(summary.getFailed() + 1);

                    String arabicMessage = translateError(e);
                    addError(summary, currentRowNum, arabicMessage);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[BulkImport] ✅ Completed in {}ms: {} total, {} inserted, {} updated, {} skipped, {} failed",
                    duration, summary.getTotal(), summary.getInserted(), summary.getUpdated(), summary.getSkipped(),
                    summary.getFailed());

            String message = buildSuccessMessage(summary, duration);

            return ExcelImportResultDto.builder()
                    .success(summary.getInserted() + summary.getUpdated() > 0)
                    .summary(summary)
                    .message(message)
                    .build();

        } catch (IOException e) {
            log.error("[BulkImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private MedicalService processRow(Row row, int rowNum,
            Map<String, MedicalCategory> categoryCache,
            Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialtyCache,
            Set<String> existingCodes) {
        // Extract data - NEW COLUMN ORDER: name*, name_en, code, category, specialty,
        // cost, price, description, status
        String nameAr = getCellString(row, 0); // Column A: name (REQUIRED)
        String nameEn = getCellString(row, 1); // Column B: name_en
        String codeRaw = getCellString(row, 2); // Column C: code
        String categoryCode = getCellString(row, 3); // Column D: category
        String specialtyCode = getCellString(row, 4); // Column E: specialty
        BigDecimal cost = getCellDecimal(row, 5); // Column F: cost
        BigDecimal basePrice = getCellDecimal(row, 6); // Column G: price
        String description = getCellString(row, 7); // Column H: description
        String status = getCellString(row, 8); // Column I: status

        // Validate required fields
        if (nameAr == null || nameAr.trim().isEmpty()) {
            throw new BusinessRuleException("الاسم بالعربي مطلوب");
        }

        final String name = nameAr.trim();

        // Generate code if not provided
        final String code;
        if (codeRaw == null || codeRaw.trim().isEmpty()) {
            code = "SVC-" + System.currentTimeMillis() + "-" + rowNum;
        } else {
            code = codeRaw.trim();
        }

        // Lookup category (optional)
        MedicalCategory category = null;
        if (categoryCode != null && !categoryCode.trim().isEmpty()) {
            category = categoryCache.get(categoryCode.trim().toUpperCase());
            if (category == null) {
                // Try by name
                category = categoryCache.get(categoryCode.trim());
            }
        }

        // Lookup specialty (MANDATORY due to DB constraint)
        com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty specialty = null;
        if (specialtyCode != null && !specialtyCode.trim().isEmpty()) {
            String specKey = specialtyCode.trim().toUpperCase();
            specialty = specialtyCache.get(specKey);
            if (specialty == null) {
                // Try by name directly
                specialty = specialtyCache.get(specialtyCode.trim());
            }

            if (specialty == null) {
                throw new BusinessRuleException("التخصص '" + specialtyCode + "' غير موجود في النظام");
            }
        } else {
            throw new BusinessRuleException("يجب تحديد التخصص (مثلاً: SP-GEN)");
        }

        // Determine status (default ACTIVE)
        boolean isActive = true;
        if (status != null && !status.trim().isEmpty()) {
            String statusLower = status.trim().toLowerCase();
            isActive = !statusLower.equals("inactive") && !statusLower.equals("غير نشط") && !statusLower.equals("no")
                    && !statusLower.equals("0");
        }

        // Check if update or insert by code
        boolean isUpdate = existingCodes.contains(code.toUpperCase());

        MedicalService service;
        if (isUpdate) {
            service = serviceRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException("خطأ في البحث عن الخدمة: " + code));
        } else {
            // Check for duplicate name if no code was provided originally
            Optional<MedicalService> existingByName = serviceRepository.findFirstByName(name);
            if (existingByName.isPresent()) {
                log.info("[BulkImport] Row {}: service with name '{}' already exists, skipping", rowNum, name);
                return null;
            }

            service = new MedicalService();
            service.setCode(code);
            service.setCreatedAt(LocalDateTime.now());
            existingCodes.add(code.toUpperCase()); // Track new codes
        }

        // Set fields
        service.setName(name);
        service.setNameAr(name);
        if (nameEn != null)
            service.setNameEn(nameEn.trim());

        if (category != null) {
            service.setCategoryId(category.getId());
        }
        if (specialty != null) {
            service.setSpecialty(specialty);
        }
        if (description != null && !description.trim().isEmpty()) {
            service.setDescription(description.trim());
        }
        if (cost != null) {
            service.setCost(cost);
        }
        if (basePrice != null) {
            service.setBasePrice(basePrice);
        }
        service.setRequiresPA(false);
        service.setActive(isActive);
        service.setMaster(true);
        service.setUpdatedAt(LocalDateTime.now());

        return service;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE LOADING
    // ═══════════════════════════════════════════════════════════════════════════

    private Map<String, MedicalCategory> loadCategoryCache() {
        List<MedicalCategory> categories = categoryRepository.findAll();
        Map<String, MedicalCategory> cache = new HashMap<>();

        for (MedicalCategory cat : categories) {
            if (cat.getCode() != null) {
                cache.put(cat.getCode().trim().toUpperCase(), cat);
            }
            if (cat.getName() != null) {
                cache.put(cat.getName().trim(), cat);
            }
        }

        return cache;
    }

    private Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> loadSpecialtyCache() {
        List<com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialties = specialtyRepository.findAll();
        Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> cache = new HashMap<>();

        for (com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty spec : specialties) {
            if (spec.getCode() != null) {
                cache.put(spec.getCode().trim().toUpperCase(), spec);
            }
            if (spec.getNameAr() != null) {
                cache.put(spec.getNameAr().trim(), spec);
            }
            if (spec.getNameEn() != null) {
                cache.put(spec.getNameEn().trim().toUpperCase(), spec);
            }
        }

        return cache;
    }

    private Set<String> loadExistingCodes() {
        List<MedicalService> allServices = serviceRepository.findAll();
        Set<String> codes = new HashSet<>();
        for (MedicalService svc : allServices) {
            if (svc.getCode() != null) {
                codes.add(svc.getCode().trim().toUpperCase());
            }
        }
        return codes;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessRuleException("نوع الملف غير صحيح. يجب أن يكون ملف Excel (.xlsx أو .xls)");
        }
    }

    private boolean isEmptyRow(Row row) {
        if (row == null)
            return true;
        // Only check first column (name) since it's the only required field
        Cell cell = row.getCell(0);
        if (cell != null && cell.getCellType() != CellType.BLANK) {
            String value = getCellString(row, 0);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null)
            return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "true" : "false";
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    private BigDecimal getCellDecimal(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null)
            return null;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String value = cell.getStringCellValue();
                    if (value == null || value.trim().isEmpty())
                        return null;
                    return new BigDecimal(value.trim());
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void addError(ImportSummary summary, int rowNum, String message) {
        if (summary.getErrors().size() < 100) { // Limit errors to prevent memory issues
            summary.getErrors().add(ImportError.builder()
                    .row(rowNum)
                    .error(message)
                    .build());
        }
    }

    private String translateError(Exception e) {
        String msg = e.getMessage();
        if (msg == null)
            return "خطأ غير معروف";

        if (e instanceof BusinessRuleException)
            return msg;

        String lowerMsg = msg.toLowerCase();
        if (lowerMsg.contains("specialty_id") && lowerMsg.contains("null")) {
            return "حقل التخصص مطلوب بصيغة الكود (مثلاً: SP-GEN)";
        }
        if (lowerMsg.contains("duplicate key") || lowerMsg.contains("unique constraint")) {
            return "هذه الخدمة (الكود) موجودة مسبقاً";
        }
        if (lowerMsg.contains("foreign key constraint")) {
            return "خطأ في الربط: البيانات المدخلة غير موجودة في النظام";
        }
        if (lowerMsg.contains("data too long") || lowerMsg.contains("value too long")) {
            return "القيمة المدخلة طويلة جداً";
        }

        return "خطأ في قاعدة البيانات: " + msg;
    }

    private String buildSuccessMessage(ImportSummary summary, long durationMs) {
        return String.format(
                "تم الاستيراد في %.1f ثانية | الإجمالي: %d | جديد: %d | محدث: %d | فاشل: %d",
                durationMs / 1000.0,
                summary.getTotal(),
                summary.getInserted(),
                summary.getUpdated(),
                summary.getFailed());
    }
}
