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
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Medical Services Excel Template Generator and Import Service
 * 
 * STRICT RULES:
 * - Templates MUST be downloaded from system
 * - Create-only mode (upsert if code exists)
 * - Service code is required and unique
 * - Category lookup is MANDATORY
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MedicalServiceExcelTemplateService {

    private final ExcelTemplateService templateService;
    private final ExcelParserService parserService;
    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final com.waad.tba.modules.medicaltaxonomy.repository.MedicalSpecialtyRepository specialtyRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate Medical Services import template
     */
    public byte[] generateTemplate() throws IOException {
        log.info("[MedicalServiceTemplate] Generating Excel template");

        List<ExcelTemplateColumn> columns = buildColumnDefinitions();
        List<ExcelLookupData> lookups = buildLookupSheets();

        return templateService.generateTemplate("Medical Services / الخدمات الطبية", columns, lookups);
    }

    private List<ExcelTemplateColumn> buildColumnDefinitions() {
        return List.of(
                // Service Code - Mandatory & Unique
                ExcelTemplateColumn.builder()
                        .name("code")
                        .nameAr("رمز الخدمة")
                        .type(ColumnType.TEXT)
                        .required(true)
                        .example("SRV-CARDIO-001")
                        .description("رمز الخدمة الفريد (إجباري) - لا يمكن تعديله لاحقاً")
                        .descriptionAr("رمز الخدمة الفريد (إجباري) - يستعمل لتحديث الخدمة إذا كانت موجودة")
                        .width(20)
                        .build(),

                // Name AR - Mandatory
                ExcelTemplateColumn.builder()
                        .name("name")
                        .nameAr("اسم الخدمة (عربي)")
                        .type(ColumnType.TEXT)
                        .required(true)
                        .example("فحص القلب الشامل")
                        .description("اسم الخدمة بالعربي (إجباري)")
                        .descriptionAr("اسم الخدمة بالعربية (إجباري)")
                        .width(35)
                        .build(),

                // Name EN - Optional
                ExcelTemplateColumn.builder()
                        .name("name_en")
                        .nameAr("اسم الخدمة (إنجليزي)")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("Comprehensive Cardiac Exam")
                        .description("Service name in English (optional)")
                        .descriptionAr("اسم الخدمة بالإنجليزية (اختياري)")
                        .width(35)
                        .build(),

                // Category Code - Mandatory Lookup
                ExcelTemplateColumn.builder()
                        .name("category_code")
                        .nameAr("رمز التصنيف")
                        .type(ColumnType.TEXT)
                        .required(true)
                        .example("CONSULTATION")
                        .description("رمز التصنيف (إجباري) - راجع ورقة التصنيفات")
                        .descriptionAr("رمز التصنيف من ورقة Lookup (إجباري)")
                        .width(20)
                        .build(),

                // Specialty Code - Optional Lookup
                ExcelTemplateColumn.builder()
                        .name("specialty_code")
                        .nameAr("رمز التخصص")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("SP-CARDIO")
                        .description("رمز التخصص الطبي (اختياري) - راجع ورقة التخصصات")
                        .descriptionAr("رمز التخصص من ورقة Lookup (اختياري)")
                        .width(20)
                        .build(),

                // Cost (Authoritative) - Optional
                ExcelTemplateColumn.builder()
                        .name("cost")
                        .nameAr("السعر المعتمد")
                        .type(ColumnType.NUMBER)
                        .required(false)
                        .example("250.00")
                        .description("السعر الرسمي المعتمد في الكتالوج (اختياري)")
                        .descriptionAr("السعر الرسمي المعتمد في الكتالوج الموحد (اختياري)")
                        .width(18)
                        .build(),

                // Base Price (Legacy) - Optional
                ExcelTemplateColumn.builder()
                        .name("base_price")
                        .nameAr("تقدير السعر")
                        .type(ColumnType.NUMBER)
                        .required(false)
                        .example("200.00")
                        .description("تقدير للسعر أو السعر القديم (اختياري)")
                        .descriptionAr("تقدير للسعر أو السعر القديم (اختياري)")
                        .width(15)
                        .build(),

                // Description - Optional
                ExcelTemplateColumn.builder()
                        .name("description")
                        .nameAr("الوصف")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("فحص شامل للقلب يتضمن تخطيط القلب...")
                        .description("وصف الخدمة (اختياري)")
                        .descriptionAr("وصف الخدمة (اختياري)")
                        .width(40)
                        .build(),

                // Active - Optional Boolean
                ExcelTemplateColumn.builder()
                        .name("active")
                        .nameAr("نشط")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("نعم")
                        .description("نعم / لا (الافتراضي: نعم)")
                        .descriptionAr("هل الخدمة نشطة؟ (نعم/لا)")
                        .width(12)
                        .build());
    }

    private List<ExcelLookupData> buildLookupSheets() {
        // Load categories and specialties
        List<MedicalCategory> categories = categoryRepository.findByActiveTrue();
        List<com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialties = specialtyRepository.findAll();

        List<List<String>> categoryData = categories.stream()
                .map(cat -> Arrays.<String>asList(
                        cat.getCode(),
                        cat.getName() != null ? cat.getName() : ""))
                .collect(Collectors.toList());

        List<List<String>> specialtyData = specialties.stream()
                .filter(sp -> !Boolean.TRUE.equals(sp.getDeleted()))
                .map(sp -> Arrays.<String>asList(
                        sp.getCode(),
                        sp.getNameAr() != null ? sp.getNameAr() : ""))
                .collect(Collectors.toList());

        return List.of(
                ExcelLookupData.builder()
                        .sheetName("التصنيفات")
                        .headers(Arrays.asList("رمز التصنيف", "اسم التصنيف"))
                        .data(categoryData)
                        .description("استخدم رمز التصنيف (العمود الأول) في عمود category_code")
                        .descriptionAr("قائمة التصنيفات المتاحة - استخدم الرمز في ملف البيانات")
                        .build(),
                ExcelLookupData.builder()
                        .sheetName("التخصصات")
                        .headers(Arrays.asList("رمز التخصص", "اسم التخصص"))
                        .data(specialtyData)
                        .description("استخدم رمز التخصص في عمود specialty_code")
                        .descriptionAr("قائمة التخصصات المتاحة - استخدم الرمز في ملف البيانات")
                        .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT FROM EXCEL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Import medical services from Excel template
     */
    @Transactional
    public ExcelImportResult importFromExcel(MultipartFile file) {
        log.info("[MedicalServiceImport] Starting import from file: {}", file.getOriginalFilename());

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

            // Load caches for lookup
            Map<String, MedicalCategory> categoryCache = loadCategoryCache();
            Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialtyCache = loadSpecialtyCache();

            // Process data rows
            int lastRow = dataSheet.getLastRowNum();
            log.info("[MedicalServiceImport] Processing {} rows", lastRow);

            for (int rowNum = 2; rowNum <= lastRow; rowNum++) { // Start from row 2 (after header)
                Row row = dataSheet.getRow(rowNum);
                if (row == null || parserService.isEmptyRow(row)) {
                    continue;
                }

                summary.setTotalRows(summary.getTotalRows() + 1);

                try {
                    processRow(row, rowNum + 1, categoryCache, specialtyCache, summary, errors);
                } catch (Exception e) {
                    log.warn("[MedicalServiceImport] Row {} failed: {}", rowNum + 1, e.getMessage());
                    summary.setFailed(summary.getFailed() + 1);
                    errors.add(ImportError.builder()
                            .rowNumber(rowNum + 1)
                            .errorType(ErrorType.PROCESSING_ERROR)
                            .messageAr(e.getMessage())
                            .messageEn(e.getMessage())
                            .build());
                }
            }

            log.info("[MedicalServiceImport] Import completed: {} total, {} created, {} updated, {} failed",
                    summary.getTotalRows(), summary.getCreated(), summary.getUpdated(), summary.getFailed());

            return ExcelImportResult.builder()
                    .summary(summary)
                    .errors(errors)
                    .success(summary.getCreated() + summary.getUpdated() > 0)
                    .messageAr(String.format("تم استيراد %d خدمة", summary.getCreated() + summary.getUpdated()))
                    .messageEn(String.format("Imported %d services", summary.getCreated() + summary.getUpdated()))
                    .build();

        } catch (IOException e) {
            log.error("[MedicalServiceImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel");
        }
    }

    private void processRow(Row row, int rowNumber, Map<String, MedicalCategory> categoryCache,
            Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialtyCache,
            ImportSummary summary, List<ImportError> errors) {
        // Read columns based on new template structure
        String code = getCellValue(row, 0); // Column A: code
        String nameAr = getCellValue(row, 1); // Column B: name (Arabic)
        String nameEn = getCellValue(row, 2); // Column C: name_en (English)
        String categoryCode = getCellValue(row, 3); // Column D: category_code
        String specialtyCode = getCellValue(row, 4); // Column E: specialty_code
        String costStr = getCellValue(row, 5); // Column F: cost
        String priceStr = getCellValue(row, 6); // Column G: base_price
        String description = getCellValue(row, 7); // Column H: description
        String activeStr = getCellValue(row, 8); // Column I: active

        // Validate required fields
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessRuleException("رمز الخدمة مطلوب");
        }

        if (nameAr == null || nameAr.trim().isEmpty()) {
            throw new BusinessRuleException("اسم الخدمة بالعربي مطلوب");
        }

        if (categoryCode == null || categoryCode.trim().isEmpty()) {
            throw new BusinessRuleException("رمز التصنيف مطلوب");
        }

        // Lookup category by code
        MedicalCategory category = categoryCache.get(categoryCode.trim());
        if (category == null) {
            throw new BusinessRuleException("التصنيف غير موجود: " + categoryCode);
        }

        // Lookup specialty by code (Optional)
        com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty specialty = null;
        if (specialtyCode != null && !specialtyCode.trim().isEmpty()) {
            specialty = specialtyCache.get(specialtyCode.trim());
        }

        // Parse prices
        BigDecimal cost = parsePrice(costStr);
        BigDecimal basePrice = parsePrice(priceStr);

        // Parse active flag
        boolean active = parseBoolean(activeStr, true); // Default to true

        // Check if service exists (upsert logic)
        Optional<MedicalService> existingOpt = serviceRepository.findByCode(code.trim());

        MedicalService service;
        boolean isUpdate = false;

        if (existingOpt.isPresent()) {
            // Update existing
            service = existingOpt.get();
            isUpdate = true;
        } else {
            // Create new
            service = new MedicalService();
            service.setCode(code.trim());
        }

        // Set/Update fields
        service.setName(nameAr.trim());
        service.setNameAr(nameAr.trim());
        if (nameEn != null)
            service.setNameEn(nameEn.trim());

        service.setCategoryId(category.getId());
        if (specialty != null) {
            service.setSpecialty(specialty);
        }

        if (description != null && !description.trim().isEmpty()) {
            service.setDescription(description.trim());
        }

        if (cost != null)
            service.setCost(cost);
        service.setBasePrice(basePrice);
        service.setActive(active);
        service.setMaster(true); // Master catalog import always marks as master

        // Save
        serviceRepository.save(service);

        if (isUpdate) {
            summary.setUpdated(summary.getUpdated() + 1);
        } else {
            summary.setCreated(summary.getCreated() + 1);
        }
    }

    private Map<String, MedicalCategory> loadCategoryCache() {
        List<MedicalCategory> categories = categoryRepository.findAll();
        Map<String, MedicalCategory> cache = new HashMap<>();

        for (MedicalCategory cat : categories) {
            // Index by code (primary lookup)
            if (cat.getCode() != null) {
                cache.put(cat.getCode().trim(), cat);
            }
            // Also index by name for backward compatibility
            if (cat.getName() != null) {
                cache.put(cat.getName().trim(), cat);
            }
        }

        return cache;
    }

    private Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> loadSpecialtyCache() {
        List<com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> specialties = specialtyRepository.findAll();
        Map<String, com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty> cache = new HashMap<>();

        for (com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty sp : specialties) {
            if (sp.getCode() != null) {
                cache.put(sp.getCode().trim().toUpperCase(), sp);
            }
        }
        return cache;
    }

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(priceStr.trim());
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("السعر يجب أن يكون أكبر من أو يساوي صفر");
            }
            return price;
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("تنسيق السعر غير صحيح: " + priceStr);
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
                normalized.equals("true") ||
                normalized.equals("1");
    }
}
