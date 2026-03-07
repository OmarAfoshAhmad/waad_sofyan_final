package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.ErrorType;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportSummary;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalServiceCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.medical.entity.ProviderRawService;
import com.waad.tba.modules.medical.entity.ProviderServiceMapping;
import com.waad.tba.modules.medical.enums.MappingStatus;
import com.waad.tba.modules.medical.repository.ProviderRawServiceRepository;
import com.waad.tba.modules.medical.repository.ProviderServiceMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Price List Excel Template Service - SIMPLIFIED VERSION
 * 
 * Purpose: Generate and import pricing items for provider contracts
 * 
 * SIMPLIFIED DESIGN (2026-01-14):
 * - Only ONE mandatory column: service_name
 * - Optional: unit_price, quantity, notes
 * - NO medical service lookup required
 * - NO complex validation
 * - Currency is system default (LYD)
 * 
 * Template Structure:
 * - Sheet: Pricing_Template
 * - Columns: service_name (required), unit_price, quantity, notes
 * 
 * @version 3.0
 * @since 2026-01-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListExcelTemplateService {

    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final MedicalServiceCategoryRepository medicalServiceCategoryRepository;
    private final ProviderRawServiceRepository providerRawServiceRepository;
    private final ProviderServiceMappingRepository providerServiceMappingRepository;
    private final PlatformTransactionManager transactionManager;

    private static final String SHEET_NAME = "Pricing_Template";

    // Column indices (0-based) - simplified upload template
    private static final int COL_SERVICE_NAME = 0;
    private static final int COL_SERVICE_CODE = 1; // NEW
    private static final int COL_UNIT_PRICE = 2;
    private static final int COL_CATEGORY = 3; // NEW
    private static final int COL_SPECIALTY = 4; // NEW
    private static final int COL_NOTES = 5;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION - SIMPLIFIED
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate simple Price List import template
     * 
     * Template is generated ALWAYS - no dependencies on existing data
     * 
     * @param contractId Contract ID (for validation only)
     * @return Excel template bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateTemplate(Long contractId) throws IOException {
        log.info("[PriceListTemplate] Generating simple template for contract ID: {}", contractId);

        // 1. Validate contract exists (returns 400 if not found)
        if (contractId == null) {
            throw new BusinessRuleException("معرف العقد غير صالح");
        }

        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessRuleException("العقد غير موجود - Invalid contractId: " + contractId));

        // Check if inactive (soft deleted)
        if (Boolean.FALSE.equals(contract.getActive())) {
            throw new BusinessRuleException("لا يمكن استيراد الأسعار لعقد غير نشط");
        }

        // 2. Generate template (NO database dependencies)
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Create main sheet
            XSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.setRightToLeft(true); // RTL for Arabic

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle requiredStyle = createRequiredHeaderStyle(workbook);
            CellStyle exampleStyle = createExampleStyle(workbook);

            // Row 0: Header row with column names
            Row headerRow = sheet.createRow(0);

            // service_name (REQUIRED)
            Cell cell0 = headerRow.createCell(COL_SERVICE_NAME);
            cell0.setCellValue("service_name / اسم الخدمة ★");
            cell0.setCellStyle(requiredStyle);

            // service_code (NEW - optional)
            Cell cell1 = headerRow.createCell(COL_SERVICE_CODE);
            cell1.setCellValue("service_code / الكود");
            cell1.setCellStyle(headerStyle);

            // unit_price (optional)
            Cell cell2 = headerRow.createCell(COL_UNIT_PRICE);
            cell2.setCellValue("unit_price / السعر");
            cell2.setCellStyle(headerStyle);

            // category (NEW - optional)
            Cell cell3 = headerRow.createCell(COL_CATEGORY);
            cell3.setCellValue("category / التصنيف");
            cell3.setCellStyle(headerStyle);

            // specialty (NEW - optional)
            Cell cell4 = headerRow.createCell(COL_SPECIALTY);
            cell4.setCellValue("specialty / التخصص");
            cell4.setCellStyle(headerStyle);

            // notes (optional)
            Cell cell5 = headerRow.createCell(COL_NOTES);
            cell5.setCellValue("notes / ملاحظات");
            cell5.setCellStyle(headerStyle);

            // Row 1: Example data row
            Row exampleRow = sheet.createRow(1);

            Cell ex0 = exampleRow.createCell(COL_SERVICE_NAME);
            ex0.setCellValue("فحص شامل");
            ex0.setCellStyle(exampleStyle);

            Cell ex1 = exampleRow.createCell(COL_SERVICE_CODE);
            ex1.setCellValue("MC-001");
            ex1.setCellStyle(exampleStyle);

            Cell ex2 = exampleRow.createCell(COL_UNIT_PRICE);
            ex2.setCellValue(100.00);
            ex2.setCellStyle(exampleStyle);

            Cell ex3 = exampleRow.createCell(COL_CATEGORY);
            ex3.setCellValue("عيادات خارجية");
            ex3.setCellStyle(exampleStyle);

            Cell ex4 = exampleRow.createCell(COL_SPECIALTY);
            ex4.setCellValue("باطنة");
            ex4.setCellStyle(exampleStyle);

            Cell ex5 = exampleRow.createCell(COL_NOTES);
            ex5.setCellValue("مثال - احذف هذا الصف");
            ex5.setCellStyle(exampleStyle);

            // Set column widths
            sheet.setColumnWidth(COL_SERVICE_NAME, 40 * 256);
            sheet.setColumnWidth(COL_SERVICE_CODE, 15 * 256);
            sheet.setColumnWidth(COL_UNIT_PRICE, 15 * 256);
            sheet.setColumnWidth(COL_CATEGORY, 25 * 256);
            sheet.setColumnWidth(COL_SPECIALTY, 25 * 256);
            sheet.setColumnWidth(COL_NOTES, 40 * 256);

            // Add instructions sheet
            createInstructionsSheet(workbook, contract);

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            byte[] result = outputStream.toByteArray();
            log.info("[PriceListTemplate] Template generated: {} bytes", result.length);

            return result;
        }
    }

    private void createInstructionsSheet(XSSFWorkbook workbook, ProviderContract contract) {
        XSSFSheet sheet = workbook.createSheet("التعليمات");
        sheet.setRightToLeft(true);

        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        int rowNum = 0;

        // Contract info
        Row row0 = sheet.createRow(rowNum++);
        row0.createCell(0).setCellValue("معلومات العقد:");
        row0.getCell(0).setCellStyle(titleStyle);

        Row row1 = sheet.createRow(rowNum++);
        row1.createCell(0).setCellValue(
                "رقم العقد: " + (contract.getContractCode() != null ? contract.getContractCode() : contract.getId()));

        Row row2 = sheet.createRow(rowNum++);
        String providerName = contract.getProvider() != null ? contract.getProvider().getName() : "غير محدد";
        row2.createCell(0).setCellValue("مقدم الخدمة: " + providerName);

        rowNum++;

        // Instructions
        Row row3 = sheet.createRow(rowNum++);
        row3.createCell(0).setCellValue("تعليمات الاستخدام:");
        row3.getCell(0).setCellStyle(titleStyle);

        sheet.createRow(rowNum++).createCell(0).setCellValue("1. العمود الإلزامي الوحيد: service_name (اسم الخدمة)");
        sheet.createRow(rowNum++).createCell(0).setCellValue(
                "2. الأعمدة الاختيارية الجديدة: service_code (الكود)، category (التصنيف)، specialty (التخصص)");
        sheet.createRow(rowNum++).createCell(0).setCellValue(
                "3. إذا كان الكود موجوداً في اسم الخدمة (مثل WE-001)، سيتعرف عليه النظام تلقائياً حتى بدون عمود الكود");
        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("4. unit_price اختياري - إذا كان فارغاً يتم حفظه 0 تلقائياً");
        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("5. يربط النظام الخدمة تلقائياً بالقاموس الموحد بالأولوية التالية: (الكود ثم الاسم)");
        sheet.createRow(rowNum++).createCell(0).setCellValue(
                "6. إذا لم يتم الربط التلقائي، تظهر الخدمة في 'مركز الربط' متبوعة بالتصنيف والتخصص الذي أدخلته هنا");

        sheet.setColumnWidth(0, 80 * 256);
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createRequiredHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createExampleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT FROM EXCEL - SIMPLIFIED
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Import pricing items from Excel template - SIMPLIFIED
     * 
     * Rules:
     * - service_name is REQUIRED
     * - unit_price defaults to 0 if empty
     * - quantity defaults to 0 if empty
     * - Empty rows are skipped
     * - No medical service lookup - just text storage
     * 
     * ARCHITECTURAL FIX (2026-03-01):
     * - Uses @Transactional for the entire batch
     * - Each row is wrapped in try-catch so one bad row doesn't kill the import
     * - Repository lookups use findFirstBy* to handle duplicate names safely
     * - syncProviderRawAndMapping is wrapped in its own try-catch (non-critical)
     */
    public ExcelImportResult importFromExcel(Long contractId, MultipartFile file) {
        log.info("[PriceListImport] Starting import for contract ID: {} from file: {}",
                contractId, file.getOriginalFilename());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ImportSummary summary = ImportSummary.builder()
                .totalRows(0)
                .created(0)
                .updated(0)
                .skipped(0)
                .rejected(0)
                .failed(0)
                .build();
        List<ImportError> errors = new ArrayList<>();

        // Validate file
        if (file == null || file.isEmpty()) {
            return buildErrorResult(summary, errors, "الملف فارغ");
        }

        // Validate contract
        ProviderContract contract = contractRepository.findById(Objects.requireNonNull(contractId))
                .orElseThrow(() -> new BusinessRuleException("العقد غير موجود"));

        if (Boolean.FALSE.equals(contract.getActive())) {
            throw new BusinessRuleException("لا يمكن استيراد الأسعار لعقد غير نشط");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            // Find the data sheet
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                // Try first sheet as fallback
                sheet = workbook.getSheetAt(0);
            }

            if (sheet == null) {
                return buildErrorResult(summary, errors, "لم يتم العثور على ورقة البيانات");
            }

            // Find header row and validate service_name column exists
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return buildErrorResult(summary, errors, "لم يتم العثور على صف العناوين");
            }

            // Find column indices (flexible - supports both English and Arabic)
            Map<String, Integer> columnIndices = findColumnIndices(headerRow);

            if (columnIndices.get("service_name") == null) {
                errors.add(ImportError.builder()
                        .rowNumber(0)
                        .errorType(ErrorType.MISSING_REQUIRED)
                        .columnName("service_name")
                        .messageAr("عمود اسم الخدمة مفقود")
                        .messageEn("service_name column is missing")
                        .build());
                return buildErrorResult(summary, errors, "عمود اسم الخدمة مفقود");
            }

            // Process data rows (skip header row)
            int lastRow = sheet.getLastRowNum();
            summary.setTotalRows(lastRow); // Excluding header

            log.info("[PriceListImport] Processing {} rows", lastRow);

            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);

                if (isEmptyRow(row)) {
                    summary.setSkipped(summary.getSkipped() + 1);
                    continue;
                }

                final int currentRowNum = rowNum;

                try {
                    // Process each row in its own transaction
                    transactionTemplate.execute(status -> {
                        try {
                            ProviderContractPricingItem pricing = parseRow(row, currentRowNum, columnIndices, contract,
                                    errors);

                            if (pricing != null) {
                                // Truncate long strings
                                truncateStrings(pricing);

                                boolean updated = upsertPricingItem(contract, pricing);
                                if (updated) {
                                    summary.setUpdated(summary.getUpdated() + 1);
                                } else {
                                    summary.setCreated(summary.getCreated() + 1);
                                }
                            } else {
                                summary.setRejected(summary.getRejected() + 1);
                            }
                            return true;
                        } catch (Exception e) {
                            status.setRollbackOnly();
                            throw e;
                        }
                    });

                } catch (Exception e) {
                    log.error("[PriceListImport] Error processing row {}: {}", currentRowNum, e.getMessage());
                    errors.add(ImportError.builder()
                            .rowNumber(currentRowNum)
                            .errorType(ErrorType.PROCESSING_ERROR)
                            .messageAr("خطأ في معالجة الصف: " + e.getMessage())
                            .messageEn("Error processing row: " + e.getMessage())
                            .build());
                    summary.setFailed(summary.getFailed() + 1);
                }
            }

            String messageAr = String.format("تم إنشاء %d بند، تخطي %d، رفض %d",
                    summary.getCreated(), summary.getSkipped(), summary.getRejected());
            String messageEn = String.format("Created %d items, skipped %d, rejected %d",
                    summary.getCreated(), summary.getSkipped(), summary.getRejected());

            log.info("[PriceListImport] Import completed: {}", messageEn);

            boolean success = (summary.getCreated() + summary.getUpdated()) > 0;

            return ExcelImportResult.builder()
                    .summary(summary)
                    .errors(errors)
                    .success(success)
                    .messageAr(messageAr)
                    .messageEn(messageEn)
                    .build();

        } catch (IOException e) {
            log.error("[PriceListImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        }
    }

    private Map<String, Integer> findColumnIndices(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();

        for (int i = 0; i <= headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null)
                continue;

            String value = getCellStringValue(cell).toLowerCase().trim();

            // service_name detection
            if (value.contains("service_name") || value.contains("اسم الخدمة") || value.equals("الخدمة")) {
                indices.put("service_name", i);
            }
            // service_code detection
            else if (value.contains("service_code") || value.contains("code") || value.contains("كود")
                    || value.contains("رمز")) {
                indices.put("service_code", i);
            }
            // unit_price detection
            else if (value.contains("unit_price") || value.contains("السعر") || value.contains("price")) {
                indices.put("unit_price", i);
            }
            // category/classification detection
            else if (value.contains("category") || value.contains("classification") || value.contains("تصنيف")
                    || value.contains("فئة")) {
                indices.put("provider_category", i);
            }
            // specialty detection
            else if (value.contains("specialty") || value.contains("تخصص")) {
                indices.put("provider_specialty", i);
            }
            // notes detection
            else if (value.contains("notes") || value.contains("ملاحظات")) {
                indices.put("notes", i);
            }
        }

        log.info("[PriceListImport] Found columns: {}", indices);
        return indices;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null)
            return true;

        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private ProviderContractPricingItem parseRow(
            Row row,
            int rowNum,
            Map<String, Integer> columnIndices,
            ProviderContract contract,
            List<ImportError> errors) {
        // Get service name (REQUIRED)
        Integer serviceNameIdx = columnIndices.get("service_name");
        String serviceName = serviceNameIdx != null ? getCellStringValue(row.getCell(serviceNameIdx)) : null;

        if (serviceName == null || serviceName.trim().isEmpty() ||
                serviceName.contains("مثال") || serviceName.toLowerCase().contains("example")) {
            // Skip rows without service name or contain example placeholder
            return null;
        }

        serviceName = truncate(serviceName.trim(), 255);

        // Get unit_price (optional, default 0)
        BigDecimal unitPrice = BigDecimal.ZERO;
        Integer unitPriceIdx = columnIndices.get("unit_price");
        if (unitPriceIdx != null) {
            Cell priceCell = row.getCell(unitPriceIdx);
            if (priceCell != null) {
                try {
                    if (priceCell.getCellType() == CellType.NUMERIC) {
                        unitPrice = BigDecimal.valueOf(priceCell.getNumericCellValue());
                    } else {
                        String priceStr = getCellStringValue(priceCell);
                        if (priceStr != null && !priceStr.trim().isEmpty()) {
                            unitPrice = new BigDecimal(priceStr.trim());
                        }
                    }
                } catch (NumberFormatException e) {
                    // Keep default 0
                    log.debug("[PriceListImport] Invalid price at row {}, using 0", rowNum);
                }
            }
        }

        int quantity = 0;

        // Get notes (optional)
        String notes = null;
        Integer notesIdx = columnIndices.get("notes");
        if (notesIdx != null) {
            notes = getCellStringValue(row.getCell(notesIdx));
            if (notes != null)
                notes = notes.trim();
        }

        String serviceCode = null;
        String categoryName = null;

        // 1. Resolve Service Code (Excel column vs Regex Extraction)
        Integer serviceCodeIdx = columnIndices.get("service_code");
        if (serviceCodeIdx != null) {
            String excelCode = getCellStringValue(row.getCell(serviceCodeIdx));
            if (excelCode != null && !excelCode.isBlank()) {
                serviceCode = truncate(excelCode.trim(), 50);
            }
        }

        // If no code column or it was empty, try extracting from name (Regex)
        if (serviceCode == null) {
            serviceCode = extractCodeFromName(serviceName);
            if (serviceCode != null) {
                log.debug("[PriceListImport] Extracted code '{}' from name '{}'", serviceCode, serviceName);
            }
        }

        // 2. Capture Raw Classifications (Metadata)
        String rawCategory = null;
        Integer categoryIdx = columnIndices.get("provider_category");
        if (categoryIdx != null) {
            rawCategory = getCellStringValue(row.getCell(categoryIdx));
            if (rawCategory != null)
                rawCategory = truncate(rawCategory.trim(), 255);
        }

        String rawSpecialty = null;
        Integer specialtyIdx = columnIndices.get("provider_specialty");
        if (specialtyIdx != null) {
            rawSpecialty = getCellStringValue(row.getCell(specialtyIdx));
            if (rawSpecialty != null)
                rawSpecialty = truncate(rawSpecialty.trim(), 255);
        }

        // 3. Attempt Medical Service Lookup
        MedicalService medicalService = null;

        // Priority 1: Match by Code
        if (serviceCode != null) {
            medicalService = medicalServiceRepository.findByCode(serviceCode).orElse(null);
        }

        // Priority 2: Match by exact Name
        if (medicalService == null) {
            medicalService = medicalServiceRepository.findFirstByName(serviceName).orElse(null);
        }

        // Priority 3: Smart Match (Check Arabic/English separately)
        if (medicalService == null) {
            medicalService = attemptSmartNameMatch(serviceName);
        }

        if (medicalService != null) {
            serviceCode = medicalService.getCode(); // Update code to canonical if matched
            categoryName = resolveCategoryNameFromUnifiedCatalog(medicalService);
        }

        // 4. Sync to ProviderRawService & Mapping Center (NON-CRITICAL)
        try {
            syncProviderRawAndMapping(contract, serviceName, serviceCode, rawCategory, rawSpecialty, medicalService);
        } catch (Exception e) {
            log.warn("[PriceListImport] Raw service sync skipped for '{}': {}", serviceName, e.getMessage());
        }

        return ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceName(serviceName)
                .serviceCode(serviceCode)
                .categoryName(categoryName)
                .contractPrice(unitPrice)
                .quantity(quantity)
                .notes(notes)
                .medicalService(medicalService)
                .active(true)
                .build();
    }

    private boolean upsertPricingItem(ProviderContract contract, ProviderContractPricingItem draft) {
        if (draft.getMedicalService() != null) {
            Optional<ProviderContractPricingItem> existingMapped = pricingRepository
                    .findByContractAndMedicalService(contract, draft.getMedicalService());

            if (existingMapped.isPresent()) {
                ProviderContractPricingItem existing = existingMapped.get();
                applyImportValues(existing, draft);
                pricingRepository.save(existing);
                return true;
            }

            Optional<ProviderContractPricingItem> existingUnmappedByName = pricingRepository
                    .findActiveUnmappedByContractAndServiceName(contract.getId(), draft.getServiceName());
            if (existingUnmappedByName.isPresent()) {
                ProviderContractPricingItem existing = existingUnmappedByName.get();
                applyImportValues(existing, draft);
                existing.setMedicalService(draft.getMedicalService());
                pricingRepository.save(existing);
                return true;
            }
        } else {
            Optional<ProviderContractPricingItem> existingUnmappedByName = pricingRepository
                    .findActiveUnmappedByContractAndServiceName(contract.getId(), draft.getServiceName());
            if (existingUnmappedByName.isPresent()) {
                ProviderContractPricingItem existing = existingUnmappedByName.get();
                applyImportValues(existing, draft);
                pricingRepository.save(existing);
                return true;
            }
        }

        pricingRepository.save(draft);
        return false;
    }

    private void applyImportValues(ProviderContractPricingItem target, ProviderContractPricingItem source) {
        target.setServiceName(source.getServiceName());
        target.setServiceCode(source.getServiceCode());
        target.setCategoryName(source.getCategoryName());
        target.setContractPrice(source.getContractPrice());
        target.setQuantity(source.getQuantity());
        target.setNotes(source.getNotes());
        target.setActive(true);
    }

    private String resolveCategoryNameFromUnifiedCatalog(MedicalService medicalService) {
        if (medicalService == null || medicalService.getId() == null) {
            return null;
        }

        Optional<MedicalServiceCategory> mapped = medicalServiceCategoryRepository
                .findFirstByServiceIdAndActiveTrueOrderByIsPrimaryDescIdAsc(medicalService.getId());

        if (mapped.isPresent()) {
            Optional<MedicalCategory> category = medicalCategoryRepository.findById(mapped.get().getCategoryId());
            if (category.isPresent()) {
                return category.get().getName();
            }
        }

        if (medicalService.getCategoryId() != null) {
            return medicalCategoryRepository.findById(medicalService.getCategoryId())
                    .map(MedicalCategory::getName)
                    .orElse(null);
        }

        return null;
    }

    private void syncProviderRawAndMapping(
            ProviderContract contract,
            String serviceName,
            String serviceCode,
            String rawCategory,
            String rawSpecialty,
            MedicalService matchedService) {
        if (contract == null || contract.getProvider() == null) {
            return;
        }

        String rawName = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : null;
        if (rawName == null) {
            return;
        }

        Long providerId = contract.getProvider().getId();

        ProviderRawService raw = providerRawServiceRepository
                .findFirstByProviderIdAndRawNameIgnoreCase(providerId, rawName)
                .orElseGet(() -> ProviderRawService.builder()
                        .providerId(providerId)
                        .rawName(rawName)
                        .normalizedName(rawName.toLowerCase())
                        .source("CONTRACT_PRICING_IMPORT")
                        .createdAt(LocalDateTime.now())
                        .status(MappingStatus.PENDING)
                        .build());

        // Update metadata
        if (serviceCode != null && !serviceCode.isBlank()) {
            raw.setCode(serviceCode.trim());
        }
        if (rawCategory != null) {
            raw.setProviderCategory(rawCategory);
        }
        if (rawSpecialty != null) {
            raw.setProviderSpecialty(rawSpecialty);
        }

        if (matchedService != null) {
            raw.setStatus(MappingStatus.AUTO_MATCHED);
            raw.setConfidenceScore(BigDecimal.valueOf(100));
        }

        raw = providerRawServiceRepository.save(raw);

        if (matchedService == null) {
            return;
        }

        final ProviderRawService savedRaw = raw;

        ProviderServiceMapping mapping = providerServiceMappingRepository
                .findByProviderRawServiceId(savedRaw.getId())
                .orElseGet(() -> ProviderServiceMapping.builder()
                        .providerRawService(savedRaw)
                        .build());

        mapping.setMedicalService(matchedService);
        mapping.setMappingStatus(MappingStatus.AUTO_MATCHED);
        mapping.setConfidenceScore(BigDecimal.valueOf(100));
        mapping.setMappedAt(LocalDateTime.now());
        mapping.setMappedBy(null);
        providerServiceMappingRepository.save(mapping);
    }

    /**
     * Extracts a service code from a name string (e.g., "Incision WE-001" ->
     * "WE-001")
     */
    private String extractCodeFromName(String name) {
        if (name == null || name.isBlank())
            return null;

        // Patterns: SP-XXXX, MC-XXXX, WE-XXXX, SRV-XXXX
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Z]{2,4}-[A-Z0-9-]+");
        java.util.regex.Matcher matcher = pattern.matcher(name);

        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null)
            return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
            default:
                return null;
        }
    }

    private ExcelImportResult buildErrorResult(ImportSummary summary, List<ImportError> errors, String message) {
        return ExcelImportResult.builder()
                .summary(summary)
                .errors(errors)
                .success(false)
                .messageAr("فشل الاستيراد: " + message)
                .messageEn("Import failed: " + message)
                .build();
    }

    private MedicalService attemptSmartNameMatch(String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return null;
        }

        String input = serviceName.trim();

        // 1. Try exact match on nameAr or nameEn
        Optional<MedicalService> match = medicalServiceRepository.findFirstByNameAr(input);
        if (match.isPresent())
            return match.get();

        match = medicalServiceRepository.findFirstByNameEn(input);
        if (match.isPresent())
            return match.get();

        // 2. Split by common separators (/, -, (, ), |) and try individual parts
        String[] parts = input.split("[/\\\\-|\\\\(\\\\)|]");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.length() < 3)
                continue;

            match = medicalServiceRepository.findFirstByName(trimmedPart);
            if (match.isPresent())
                return match.get();

            match = medicalServiceRepository.findFirstByNameAr(trimmedPart);
            if (match.isPresent())
                return match.get();

            match = medicalServiceRepository.findFirstByNameEn(trimmedPart);
            if (match.isPresent())
                return match.get();
        }

        return null;
    }

    private String truncate(String text, int length) {
        if (text == null)
            return null;
        return text.length() <= length ? text : text.substring(0, length);
    }

    private void truncateStrings(ProviderContractPricingItem item) {
        if (item.getServiceName() != null && item.getServiceName().length() > 500) {
            item.setServiceName(item.getServiceName().substring(0, 500));
        }
        if (item.getNotes() != null && item.getNotes().length() > 500) {
            item.setNotes(item.getNotes().substring(0, 500));
        }
    }
}
