package com.waad.tba.modules.providercontract.service;

import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportError;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportSummary;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service for importing Provider Contract Pricing Items from Excel files.
 * 
 * Excel Format (based on Odoo product.supplierinfo):
 * - تسلسل (Sequence) - Row number [OPTIONAL]
 * - قائمة الأسعار (Price List Name) - Used to find contract [REQUIRED]
 * - قالب المنتج (Service Name Arabic) - Medical service name [REQUIRED]
 * - كود منتج المورد (Service Code) - Service code for exact match [OPTIONAL]
 * - العملة (Currency) - Default: LYD [OPTIONAL]
 * - الكمية (Quantity) - Ignored (always 0 in Odoo) [OPTIONAL]
 * - السعر (Contract Price) - Negotiated price [REQUIRED]
 * 
 * Business Logic:
 * - Find Contract by providerId + priceListName or active contract
 * - Match MedicalService by code (preferred) or nameAr
 * - Use MedicalService.priceLyd as basePrice
 * - Upsert: Update if (contract_id, service_id) exists, Insert if new
 * - Calculate discountPercent automatically
 * 
 * @version 1.0
 * @since 2025-01-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderContractPricingExcelService {

    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingRepository;
    private final MedicalCategoryRepository categoryRepository;

    /**
     * Column name mappings (supports both Arabic and English)
     */
    /**
     * REQUIRED column names in the official template.
     * Displayed in error messages when the file doesn't match.
     */
    private static final String TEMPLATE_REQUIRED_COLS = "service_name / اسم الخدمة ★";
    private static final String TEMPLATE_OPTIONAL_COLS = "service_code / الكود | unit_price / السعر | category / التصنيف | specialty / التخصص | notes / ملاحظات";

    private static final Map<String, String> COLUMN_MAPPINGS = new java.util.LinkedHashMap<>();

    static {
        // ─── Sequence (optional) ────────────────────────────────────────────────
        COLUMN_MAPPINGS.put("تسلسل", "sequence");
        COLUMN_MAPPINGS.put("sequence", "sequence");

        // ─── Price List Name (optional) ─────────────────────────────────────────
        COLUMN_MAPPINGS.put("قائمة الأسعار", "priceListName");
        COLUMN_MAPPINGS.put("price list", "priceListName");
        COLUMN_MAPPINGS.put("pricelist", "priceListName");

        // ─── Service Name (REQUIRED) ────────────────────────────────────────────
        // New official template format
        COLUMN_MAPPINGS.put("service_name / اسم الخدمة ★", "serviceName");
        COLUMN_MAPPINGS.put("service_name", "serviceName");
        // Legacy / Odoo format
        COLUMN_MAPPINGS.put("قالب المنتج", "serviceName");
        COLUMN_MAPPINGS.put("service name", "serviceName");
        COLUMN_MAPPINGS.put("product template", "serviceName");
        COLUMN_MAPPINGS.put("اسم الخدمة", "serviceName");
        COLUMN_MAPPINGS.put("اسم الخدمة ★", "serviceName");

        // ─── Service Code (optional) ────────────────────────────────────────────
        // New official template format
        COLUMN_MAPPINGS.put("service_code / الكود", "serviceCode");
        COLUMN_MAPPINGS.put("service_code", "serviceCode");
        // Legacy / Odoo format
        COLUMN_MAPPINGS.put("كود منتج المورد", "serviceCode");
        COLUMN_MAPPINGS.put("supplier product code", "serviceCode");
        COLUMN_MAPPINGS.put("service code", "serviceCode");
        COLUMN_MAPPINGS.put("code", "serviceCode");
        COLUMN_MAPPINGS.put("الكود", "serviceCode");

        // ─── Currency (optional) ────────────────────────────────────────────────
        COLUMN_MAPPINGS.put("العملة", "currency");
        COLUMN_MAPPINGS.put("currency", "currency");

        // ─── Quantity (optional / ignored) ──────────────────────────────────────
        COLUMN_MAPPINGS.put("الكمية", "quantity");
        COLUMN_MAPPINGS.put("quantity", "quantity");

        // ─── Contract Price (required) ──────────────────────────────────────────
        // New official template format
        COLUMN_MAPPINGS.put("unit_price / السعر", "contractPrice");
        COLUMN_MAPPINGS.put("unit_price", "contractPrice");
        // Legacy / Odoo format
        COLUMN_MAPPINGS.put("السعر", "contractPrice");
        COLUMN_MAPPINGS.put("price", "contractPrice");
        COLUMN_MAPPINGS.put("سعر", "contractPrice");

        // ─── Extra fields (optional, stored as notes) ────────────────────────────
        COLUMN_MAPPINGS.put("category / التصنيف", "category");
        COLUMN_MAPPINGS.put("category", "category");
        COLUMN_MAPPINGS.put("التصنيف", "category");
        COLUMN_MAPPINGS.put("main_category / التصنيف الرئيسي", "mainCategory");
        COLUMN_MAPPINGS.put("main_category", "mainCategory");
        COLUMN_MAPPINGS.put("التصنيف الرئيسي", "mainCategory");
        COLUMN_MAPPINGS.put("sub_category / البند (التصنيف الفرعي)", "subCategory");
        COLUMN_MAPPINGS.put("sub_category", "subCategory");
        COLUMN_MAPPINGS.put("البند", "subCategory");
        COLUMN_MAPPINGS.put("التصنيف الفرعي", "subCategory");
        COLUMN_MAPPINGS.put("specialty / التخصص", "specialty");
        COLUMN_MAPPINGS.put("specialty", "specialty");
        COLUMN_MAPPINGS.put("التخصص", "specialty");
        COLUMN_MAPPINGS.put("notes / ملاحظات", "notes");
        COLUMN_MAPPINGS.put("notes", "notes");
        COLUMN_MAPPINGS.put("ملاحظات", "notes");
    }

    /**
     * Import pricing items from Excel file
     * 
     * @param contractId The provider contract ID
     * @param file       Excel file (.xlsx or .xls)
     * @return Import result with statistics
     */
    @Transactional
    @SuppressWarnings("deprecation")
    public ExcelImportResultDto importFromExcel(Long contractId, MultipartFile file) {
        log.info("Starting Excel import for contract ID: {}", contractId);

        // Verify contract exists and is modifiable
        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        if (contract.getStatus() == ProviderContract.ContractStatus.EXPIRED ||
                contract.getStatus() == ProviderContract.ContractStatus.TERMINATED) {
            throw new IllegalStateException("Cannot import pricing for EXPIRED or TERMINATED contract");
        }

        List<ImportError> errors = new ArrayList<>();
        int totalRows = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        try (InputStream is = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                return ExcelImportResultDto.builder()
                        .success(false)
                        .message("❌ الملف فارغ أو لا يحتوي على سطر رأس (Header). تأكد من استخدام القالب الرسمي.")
                        .build();
            }

            // Map column indices
            Map<String, Integer> columnIndices = mapColumns(headerRow);

            // Collect actual column names for error reporting
            List<String> actualHeaders = new java.util.ArrayList<>();
            for (Cell hCell : headerRow) {
                if (hCell != null && hCell.getCellType() != CellType.BLANK) {
                    actualHeaders.add(hCell.getStringCellValue().trim());
                }
            }
            log.info("Detected headers: {}", actualHeaders);

            // Validate required column: service name
            if (!columnIndices.containsKey("serviceName") && !columnIndices.containsKey("serviceCode")) {
                String detectedCols = actualHeaders.isEmpty() ? "(لا توجد أعمدة مكتشفة)"
                        : String.join(" | ", actualHeaders);
                return ExcelImportResultDto.builder()
                        .success(false)
                        .message(String.format(
                                "❌ الملف لا يطابق القالب المطلوب.%n" +
                                        "العمود الإلزامي المفقود: '%s'%n%n" +
                                        "الأعمدة المكتشفة في ملفك: %s%n%n" +
                                        "الأعمدة المطلوبة في القالب الرسمي:%n" +
                                        "  ★ إلزامي: %s%n" +
                                        "  ○ اختياري: %s%n%n" +
                                        "يرجى تحميل القالب الرسمي من صفحة العقد والمحاولة مرة أخرى.",
                                TEMPLATE_REQUIRED_COLS, detectedCols,
                                TEMPLATE_REQUIRED_COLS, TEMPLATE_OPTIONAL_COLS))
                        .build();
            }

            // Get current username
            String currentUser = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // Track codes used in THIS import session to avoid duplicates within the batch
            Set<String> usedCodesInSession = new HashSet<>();

            // Process rows
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                totalRows++;

                try {
                    // Extract data
                    String serviceCodeValue = getCellValueAsString(row, columnIndices.get("serviceCode"));
                    String serviceNameValue = getCellValueAsString(row, columnIndices.get("serviceName"));
                    BigDecimal contractPriceValue = getCellValueAsDecimal(row, columnIndices.get("contractPrice"));
                    String currencyValue = getCellValueAsString(row, columnIndices.get("currency"));
                    String mainCatCode = getCellValueAsString(row, columnIndices.get("mainCategory"));
                    String subCatCode = getCellValueAsString(row, columnIndices.get("subCategory"));

                    log.debug("Row {}: serviceCode='{}', serviceName='{}', price={}",
                            rowNum + 1, serviceCodeValue, serviceNameValue, contractPriceValue);

                    // Validate required fields
                    if (contractPriceValue == null) {
                        errors.add(ImportError.builder()
                                .row(rowNum + 1)
                                .column("السعر")
                                .error("السعر مطلوب")
                                .build());
                        skipped++;
                        continue;
                    }

                    if (contractPriceValue.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add(ImportError.builder()
                                .row(rowNum + 1)
                                .column("السعر")
                                .error("السعر يجب أن يكون >= 0")
                                .build());
                        skipped++;
                        continue;
                    }

                    // No MedicalService catalog lookup — use serviceCode/serviceName directly.
                    if (serviceCodeValue == null && serviceNameValue == null) {
                        errors.add(ImportError.builder()
                                .row(rowNum + 1)
                                .column("service_name / service_code")
                                .error("يجب توفير اسم الخدمة أو الكود")
                                .build());
                        skipped++;
                        continue;
                    }

                    // Auto-generate code when not provided in Excel
                    if ((serviceCodeValue == null || serviceCodeValue.isBlank()) && serviceNameValue != null) {
                        serviceCodeValue = generateUniqueServiceCode(
                                contractId, serviceNameValue, usedCodesInSession);
                        log.debug("Auto-generated code '{}' for service '{}'", serviceCodeValue, serviceNameValue);
                    }

                    // Register this code as used in the current session
                    if (serviceCodeValue != null && !serviceCodeValue.isBlank()) {
                        usedCodesInSession.add(serviceCodeValue.trim().toUpperCase());
                    }

                    // Identifier for logs
                    String identifier = (serviceCodeValue != null && !serviceCodeValue.isBlank())
                            ? serviceCodeValue
                            : serviceNameValue;

                    // basePrice defaults to zero (no catalog to pull from)
                    BigDecimal basePrice = BigDecimal.ZERO;

                    // Set currency (default: LYD)
                    String currency = (currencyValue != null && !currencyValue.isBlank())
                            ? currencyValue.trim().toUpperCase()
                            : "LYD";

                    // Resolve Medical Category from Excel if provided
                    MedicalCategory assignedCategory = null;
                    String targetCatCode = (subCatCode != null && !subCatCode.isBlank()) ? subCatCode : mainCatCode;

                    if (targetCatCode != null && !targetCatCode.isBlank()) {
                        assignedCategory = categoryRepository.findByCode(targetCatCode.trim())
                                .orElse(null);
                        if (assignedCategory == null) {
                            // Try and find by name if code fails
                            assignedCategory = categoryRepository.findAll().stream()
                                    .filter(c -> c.getName().equalsIgnoreCase(targetCatCode.trim()))
                                    .findFirst()
                                    .orElse(null);
                        }
                    }

                    // Check if pricing item already exists (upsert by service code or name)
                    Optional<ProviderContractPricingItem> existingOpt = Optional.empty();

                    if (serviceCodeValue != null && !serviceCodeValue.isBlank()) {
                        existingOpt = pricingRepository.findByContractIdAndServiceCodeActiveTrue(
                                contract.getId(), serviceCodeValue.trim());
                    } else if (serviceNameValue != null && !serviceNameValue.isBlank()) {
                        existingOpt = pricingRepository.findByContractIdAndServiceNameActiveTrue(
                                contract.getId(), serviceNameValue.trim());
                    }

                    String fallbackCategoryName = null;
                    if (mainCatCode != null || subCatCode != null) {
                        fallbackCategoryName = (mainCatCode != null ? mainCatCode : "") +
                                (mainCatCode != null && subCatCode != null ? " > " : "") +
                                (subCatCode != null ? subCatCode : "");
                    }

                    if (existingOpt.isPresent()) {
                        // UPDATE
                        ProviderContractPricingItem existing = existingOpt.get();
                        existing.setBasePrice(basePrice);
                        existing.setContractPrice(contractPriceValue);
                        existing.setCurrency(currency);
                        existing.setServiceCode(serviceCodeValue);
                        existing.setMedicalCategory(assignedCategory);
                        existing.setCategoryName(fallbackCategoryName);
                        existing.setUpdatedBy(currentUser);
                        existing.setActive(true);
                        // discountPercent calculated automatically via @PreUpdate

                        pricingRepository.save(existing);
                        updated++;

                        log.debug("Updated pricing: contract={}, service={}, price={}",
                                contractId, identifier, contractPriceValue);
                    } else {
                        // INSERT
                        ProviderContractPricingItem newItem = ProviderContractPricingItem.builder()
                                .contract(contract)
                                .serviceName(serviceNameValue)
                                .serviceCode(serviceCodeValue)
                                .medicalCategory(assignedCategory)
                                .categoryName(fallbackCategoryName)
                                .basePrice(basePrice)
                                .contractPrice(contractPriceValue)
                                .currency(currency)
                                .unit("خدمة")
                                .active(true)
                                .createdBy(currentUser)
                                .updatedBy(currentUser)
                                .build();

                        pricingRepository.save(newItem);
                        inserted++;

                        log.debug("Inserted pricing: contract={}, service={}, price={}",
                                contractId, identifier, contractPriceValue);
                    }

                } catch (Exception e) {
                    log.error("Error processing row {}: {}", rowNum + 1, e.getMessage());
                    errors.add(ImportError.builder()
                            .row(rowNum + 1)
                            .column("معالجة")
                            .error(e.getMessage())
                            .build());
                    skipped++;
                }
            }

        } catch (Exception e) {
            log.error("Error reading Excel file", e);
            return ExcelImportResultDto.builder()
                    .success(false)
                    .message("خطأ في قراءة ملف Excel: " + e.getMessage())
                    .build();
        }

        // Build result
        ImportSummary summary = ImportSummary.builder()
                .total(totalRows)
                .inserted(inserted)
                .updated(updated)
                .skipped(skipped)
                .failed(errors.size())
                .errors(errors)
                .build();

        boolean success = (inserted + updated) > 0;
        String message = String.format(
                "تم استيراد %d عنصر تسعير بنجاح (إضافة: %d، تحديث: %d، تخطي: %d، فشل: %d)",
                inserted + updated, inserted, updated, skipped, errors.size());

        return ExcelImportResultDto.builder()
                .success(success)
                .message(message)
                .summary(summary)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Map column names to indices
     */
    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();

        log.info("Excel Header Row Analysis:");
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null)
                continue;

            String columnName = cell.getStringCellValue().trim().toLowerCase();
            String mappedName = COLUMN_MAPPINGS.get(columnName);

            log.info("  Column {}: '{}' -> mapped to '{}'", i, columnName, mappedName);

            if (mappedName != null) {
                indices.put(mappedName, i);
            }
        }

        log.info("Mapped columns: {}", indices.keySet());
        return indices;
    }

    /**
     * Generates a unique service code for services that have no code in the Excel
     * file.
     * Format: GEN-{INITIALS}-{disambiguator}
     * Example: "فحص دم شامل" → "GEN-FDS" or "GEN-FDS-2" if GEN-FDS is taken.
     */
    private String generateUniqueServiceCode(Long contractId, String serviceName, Set<String> usedCodesInSession) {
        // Build base code from initials of words (up to 4 words, first char each)
        String base = buildBaseCode(serviceName);
        String candidate = base;

        int counter = 2;
        while (isCodeTaken(contractId, candidate, usedCodesInSession)) {
            candidate = base + "-" + counter;
            counter++;
            if (counter > 9999) {
                // Ultimate fallback using timestamp suffix to guarantee uniqueness
                candidate = base + "-" + (System.currentTimeMillis() % 100000);
                break;
            }
        }
        return candidate;
    }

    private String buildBaseCode(String name) {
        if (name == null || name.isBlank())
            return "GEN-SVC";
        // Remove punctuation and split by whitespace
        String[] words = name.trim().replaceAll("[^\\p{L}\\p{N}\\s]", " ").split("\\s+");
        StringBuilder sb = new StringBuilder("GEN-");
        int taken = 0;
        for (String w : words) {
            if (w.isBlank())
                continue;
            // Append first character (uppercase via codePoint for Arabic/Latin)
            String ch = w.substring(0, 1).toUpperCase();
            sb.append(ch);
            taken++;
            if (taken >= 4)
                break;
        }
        if (taken == 0)
            sb.append("SVC");
        return sb.toString();
    }

    private boolean isCodeTaken(Long contractId, String code, Set<String> usedCodesInSession) {
        String upperCode = code.toUpperCase();
        if (usedCodesInSession.contains(upperCode))
            return true;
        return pricingRepository.findByContractIdAndServiceCodeActiveTrue(contractId, code).isPresent();
    }

    /**
     * Check if row is empty
     */
    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get cell value as String
     */
    private String getCellValueAsString(Row row, Integer colIndex) {
        if (colIndex == null)
            return null;

        Cell cell = row.getCell(colIndex);
        if (cell == null)
            return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    /**
     * Get cell value as BigDecimal
     */
    private BigDecimal getCellValueAsDecimal(Row row, Integer colIndex) {
        if (colIndex == null)
            return null;

        Cell cell = row.getCell(colIndex);
        if (cell == null)
            return null;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue())
                            .setScale(2, RoundingMode.HALF_UP);
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty())
                        return null;
                    return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid decimal value in cell: {}", cell.getStringCellValue());
            return null;
        }
    }
}
