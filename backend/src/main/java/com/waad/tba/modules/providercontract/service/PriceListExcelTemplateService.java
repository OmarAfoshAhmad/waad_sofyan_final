package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.ErrorType;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportSummary;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Generates and imports Excel price-list templates for provider contracts.
 *
 * Template columns:
 * service_name (required) | service_code | contract_price
 * medical_category_code | medical_category_name | notes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListExcelTemplateService {

    private static final String IMPORT_RUNTIME_MARKER = "PriceListImport-v2026-04-24-01";

    /**
     * Prevents CSV/XLSX formula injection: values in this export can
     * originate from provider-portal-submitted pricing items (service name,
     * code, notes). If such a value starts with a formula-trigger character
     * ('=', '+', '-', '@') Excel/LibreOffice will evaluate it as a formula
     * when the exported file is opened, which can be abused for remote
     * command execution or data exfiltration against whoever opens the
     * export (typically SUPER_ADMIN/ACCOUNTANT). Prefixing with a single
     * quote forces spreadsheet applications to treat the cell as literal text.
     */
    private static String sanitizeForExcel(String value) {
        return com.waad.tba.common.excel.ExcelSanitizer.sanitize(value);
    }

    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final PlatformTransactionManager transactionManager;

    private static final String SHEET_NAME = "Pricing_Template";

    // Template column indices (0-based)
    private static final int COL_SERVICE_NAME = 0;
    private static final int COL_SERVICE_CODE = 1;
    private static final int COL_CONTRACT_PRICE = 2;
    private static final int COL_CATEGORY = 3;
    private static final int COL_SUB_CATEGORY = 4;
    private static final int COL_NOTES = 5;

    // Max field lengths (must match DB column constraints)
    private static final int MAX_SERVICE_NAME = 255;
    private static final int MAX_SERVICE_CODE = 50;
    private static final int MAX_CATEGORY = 255;
    private static final int MAX_NOTES = 2000;

    /**
     * Pattern to extract service codes embedded in names, e.g. "Incision WE-001" →
     * "WE-001".
     */
    private static final Pattern CODE_IN_NAME_PATTERN = Pattern.compile("[A-Z]{2,4}-[A-Z0-9-]+");
    private static final Pattern CATEGORY_CODE_PATTERN = Pattern.compile("(CAT[0-9A-Z-]+)", Pattern.CASE_INSENSITIVE);

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate an Excel import template for the given contract.
     * Only validates contract existence; template content is static.
     */
    @Transactional(readOnly = true)
    public byte[] generateTemplate(Long contractId) throws IOException {
        if (contractId == null) {
            throw new BusinessRuleException("معرف العقد غير صالح");
        }
        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessRuleException("العقد غير موجود: " + contractId));
        if (Boolean.FALSE.equals(contract.getActive())) {
            throw new BusinessRuleException("لا يمكن استيراد الأسعار لعقد غير نشط");
        }

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

            // contract_price (optional - what we pay)
            Cell cell3 = headerRow.createCell(COL_CONTRACT_PRICE);
            cell3.setCellValue("contract_price / سعر العقد");
            cell3.setCellStyle(headerStyle);

            // medical_category_code (required for accurate coverage)
            Cell cell4 = headerRow.createCell(COL_CATEGORY);
            cell4.setCellValue("medical_category_code / كود التصنيف الطبي");
            cell4.setCellStyle(headerStyle);

            // medical_category_name (optional display/helper)
            Cell cellSub = headerRow.createCell(COL_SUB_CATEGORY);
            cellSub.setCellValue("medical_category_name / اسم التصنيف الطبي");
            cellSub.setCellStyle(headerStyle);

            // notes (optional)
            Cell cell6 = headerRow.createCell(COL_NOTES);
            cell6.setCellValue("notes / ملاحظات");
            cell6.setCellStyle(headerStyle);

            // Row 1: Example data row
            Row exampleRow = sheet.createRow(1);

            Cell ex0 = exampleRow.createCell(COL_SERVICE_NAME);
            ex0.setCellValue("فحص شامل");
            ex0.setCellStyle(exampleStyle);

            Cell ex1 = exampleRow.createCell(COL_SERVICE_CODE);
            ex1.setCellValue("MC-001");
            ex1.setCellStyle(exampleStyle);

            Cell ex3 = exampleRow.createCell(COL_CONTRACT_PRICE);
            ex3.setCellValue(100.00);
            ex3.setCellStyle(exampleStyle);

            Cell ex4 = exampleRow.createCell(COL_CATEGORY);
            ex4.setCellValue("CAT-DIAGNOSTIC");
            ex4.setCellStyle(exampleStyle);

            Cell exSub = exampleRow.createCell(COL_SUB_CATEGORY);
            exSub.setCellValue("الكشوفات الطبية");
            exSub.setCellStyle(exampleStyle);

            Cell ex6 = exampleRow.createCell(COL_NOTES);
            ex6.setCellValue("مثال - احذف هذا الصف");
            ex6.setCellStyle(exampleStyle);

            // Set column widths
            sheet.setColumnWidth(COL_SERVICE_NAME, 40 * 256);
            sheet.setColumnWidth(COL_SERVICE_CODE, 15 * 256);
            sheet.setColumnWidth(COL_CONTRACT_PRICE, 15 * 256);
            sheet.setColumnWidth(COL_CATEGORY, 25 * 256);
            sheet.setColumnWidth(COL_SUB_CATEGORY, 25 * 256);
            sheet.setColumnWidth(COL_NOTES, 40 * 256);

            // Add instructions sheet
            createInstructionsSheet(workbook, contract);

            // Add categories lookup sheet
            createCategoriesLookupSheet(workbook);

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            byte[] result = outputStream.toByteArray();
            log.info("[PriceListTemplate] Template generated: {} bytes", result.length);

            return result;
        }
    }

    /**
     * Export all pricing items for a contract to an Excel file.
     */
    @Transactional(readOnly = true)
    public byte[] exportPricingToExcel(Long contractId) throws IOException {
        if (contractId == null) {
            throw new BusinessRuleException("معرف العقد غير صالح");
        }
        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessRuleException("العقد غير موجود: " + contractId));

        // Use SXSSFWorkbook to keep only 100 rows in memory, preventing OOM for large contracts
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
            org.apache.poi.xssf.streaming.SXSSFSheet sheet = workbook.createSheet("Pricing_Export");
            sheet.setRightToLeft(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createExampleStyle(workbook);

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"service_name / اسم الخدمة", "service_code / الكود", "contract_price / سعر العقد", "medical_category_code / كود التصنيف الطبي", "medical_category_name / اسم التصنيف الطبي", "notes / ملاحظات"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            int pageNumber = 0;
            int pageSize = 1000;
            org.springframework.data.domain.Page<ProviderContractPricingItem> page;

            do {
                page = pricingRepository.findByContractIdAndActiveTrue(contractId, org.springframework.data.domain.PageRequest.of(pageNumber, pageSize));
                
                for (ProviderContractPricingItem item : page.getContent()) {
                    Row row = sheet.createRow(rowNum++);
                    
                    Cell c0 = row.createCell(COL_SERVICE_NAME);
                    c0.setCellValue(sanitizeForExcel(item.getServiceName() != null ? item.getServiceName() : "-"));
                    c0.setCellStyle(dataStyle);

                    Cell c1 = row.createCell(COL_SERVICE_CODE);
                    c1.setCellValue(sanitizeForExcel(item.getServiceCode() != null ? item.getServiceCode() : "-"));
                    c1.setCellStyle(dataStyle);

                    Cell c2 = row.createCell(COL_CONTRACT_PRICE);
                    if (item.getContractPrice() != null) {
                        c2.setCellValue(item.getContractPrice().doubleValue());
                    }
                    c2.setCellStyle(dataStyle);

                    String categoryCode = "-";
                    String categoryName = "-";
                    if (item.getMedicalCategory() != null) {
                        MedicalCategory cat = item.getMedicalCategory();
                        categoryCode = cat.getCode() != null ? cat.getCode() : "-";
                        categoryName = displayCategoryName(cat);
                    } else if (item.getCategoryName() != null) {
                        categoryName = item.getCategoryName();
                    }
                    
                    Cell c3 = row.createCell(COL_CATEGORY);
                    c3.setCellValue(sanitizeForExcel(categoryCode));
                    c3.setCellStyle(dataStyle);

                    Cell c4 = row.createCell(COL_SUB_CATEGORY);
                    c4.setCellValue(sanitizeForExcel(categoryName));
                    c4.setCellStyle(dataStyle);

                    Cell c5 = row.createCell(COL_NOTES);
                    c5.setCellValue(sanitizeForExcel(item.getNotes() != null ? item.getNotes() : ""));
                    c5.setCellStyle(dataStyle);
                }
                pageNumber++;
            } while (page.hasNext());

            sheet.trackAllColumnsForAutoSizing();
            sheet.setColumnWidth(COL_SERVICE_NAME, 40 * 256);
            sheet.setColumnWidth(COL_SERVICE_CODE, 15 * 256);
            sheet.setColumnWidth(COL_CONTRACT_PRICE, 15 * 256);
            sheet.setColumnWidth(COL_CATEGORY, 25 * 256);
            sheet.setColumnWidth(COL_SUB_CATEGORY, 25 * 256);
            sheet.setColumnWidth(COL_NOTES, 40 * 256);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
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
                "2. الأعمدة الاختيارية: service_code (الكود)، medical_category_code (كود التصنيف الطبي)، medical_category_name (اسم التصنيف الطبي)، notes (ملاحظات)");
        sheet.createRow(rowNum++).createCell(0).setCellValue(
                "3. إذا كان الكود موجوداً في اسم الخدمة (مثل WE-001)، سيتعرف عليه النظام تلقائياً حتى بدون عمود الكود");
        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("4. contract_price اختياري - إذا كان فارغاً يتم حفظه 0 تلقائياً");
        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("5. يربط النظام الخدمة تلقائياً بالقاموس الموحد بالأولوية التالية: (الكود ثم الاسم)");
        sheet.createRow(rowNum++).createCell(0).setCellValue(
                "6. إذا لم يتم الربط التلقائي، تظهر الخدمة في 'مركز الربط' متبوعة بالتصنيف الذي أدخلته هنا");
        sheet.createRow(rowNum++).createCell(0).setCellValue(
                "7. يرجى الرجوع لورقة 'التصنيفات المتاحة' لمعرفة الرموز والمسميات الصحيحة للتصنيفات الطبية.");

        sheet.setColumnWidth(0, 80 * 256);
    }

    private void createCategoriesLookupSheet(XSSFWorkbook workbook) {
        List<MedicalCategory> categories = medicalCategoryRepository.findByActiveTrue();
        
        XSSFSheet sheet = workbook.createSheet("التصنيفات المتاحة");
        sheet.setRightToLeft(true);

        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"رمز التصنيف", "اسم التصنيف (عربي)", "اسم التصنيف (إنجليزي)", "رمز التصنيف الأب"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Map categories by ID for quick parent lookup
        Map<Long, MedicalCategory> catMap = new HashMap<>();
        for (MedicalCategory cat : categories) {
            catMap.put(cat.getId(), cat);
        }

        int rowNum = 1;
        for (MedicalCategory cat : categories) {
            Row row = sheet.createRow(rowNum++);
            
            // Column 0: Code
            row.createCell(0).setCellValue(cat.getCode() != null ? cat.getCode() : "");
            
            // Column 1: Arabic Name
            row.createCell(1).setCellValue(cat.getNameAr() != null ? cat.getNameAr() : (cat.getName() != null ? cat.getName() : ""));
            
            // Column 2: English Name
            row.createCell(2).setCellValue(cat.getNameEn() != null ? cat.getNameEn() : "");
            
            // Column 3: Parent Code
            String parentCode = "";
            if (cat.getParentId() != null) {
                MedicalCategory parent = catMap.get(cat.getParentId());
                if (parent != null) {
                    parentCode = parent.getCode();
                } else {
                    parentCode = medicalCategoryRepository.findById(cat.getParentId())
                            .map(MedicalCategory::getCode)
                            .orElse("");
                }
            }
            row.createCell(3).setCellValue(parentCode);
        }

        // Adjust column widths
        sheet.setColumnWidth(0, 25 * 256);
        sheet.setColumnWidth(1, 35 * 256);
        sheet.setColumnWidth(2, 35 * 256);
        sheet.setColumnWidth(3, 25 * 256);
    }

    private CellStyle createHeaderStyle(org.apache.poi.ss.usermodel.Workbook workbook) {
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

    private CellStyle createRequiredHeaderStyle(org.apache.poi.ss.usermodel.Workbook workbook) {
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

    private CellStyle createExampleStyle(org.apache.poi.ss.usermodel.Workbook workbook) {
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
    // IMPORT FROM EXCEL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Import pricing items from the Excel template.
     * Each row is processed in its own transaction; one bad row never kills the
     * batch.
     */
    public ExcelImportResult importFromExcel(Long contractId, MultipartFile file) {
        String safeFileName = file != null ? file.getOriginalFilename().replaceAll("[\\r\\n]", "_") : "null";
        log.info("[PriceListImport][{}] Starting import for contract ID: {} from file: {}",
                IMPORT_RUNTIME_MARKER, contractId, safeFileName);

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

        if (file == null || file.isEmpty()) {
            return buildErrorResult(summary, errors, "الملف فارغ");
        }
        if (contractId == null) {
            throw new BusinessRuleException("معرف العقد غير صالح");
        }

        // Validate contract
        ProviderContract contract = contractRepository.findById(contractId)
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

            String messageAr = String.format("النتيجة: إنشاء %d، تحديث %d، فاشل %d، تخطي %d",
                    summary.getCreated(), summary.getUpdated(), summary.getFailed(), summary.getSkipped());
            String messageEn = String.format("Import result: Created %d, Updated %d, Failed %d, Skipped %d",
                    summary.getCreated(), summary.getUpdated(), summary.getFailed(), summary.getSkipped());

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

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
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
            // Prices detection
            else if (value.contains("base_price") || value.contains("السعر الأساسي") || value.contains("standard")) {
                indices.put("base_price", i);
            } else if (value.contains("contract_price") || value.contains("سعر العقد") || value.contains("unit_price")
                    || value.contains("السعر") || value.equals("price")) {
                indices.put("contract_price", i);
            }
            // unified medical category detection (modern template)
            else if (value.contains("medical_category_code") || value.contains("category_code")
                    || value.contains("كود التصنيف") || value.contains("رمز التصنيف")) {
                indices.put("medical_category_code", i);
            } else if (value.contains("medical_category_name") || value.contains("category_name")
                    || value.contains("اسم التصنيف الطبي") || value.contains("التصنيف الطبي الموحد")) {
                indices.put("medical_category_name", i);
            }
            // legacy category/classification detection (kept for old provider files)
            else if (value.contains("main_category") || value.contains("التصنيف الرئيسي")) {
                indices.put("main_category", i);
            } else if (value.contains("sub_category") || value.contains("التصنيف الفرعي") || value.equals("البند")
                    || value.contains("sub_cat")) {
                indices.put("sub_category", i);
            } else if (value.contains("category") || value.contains("classification") || value.contains("تصنيف")
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

        // --- 1. Extract Service Name (REQUIRED) ---
        Integer serviceNameIdx = columnIndices.get("service_name");
        String serviceName = serviceNameIdx != null ? getCellStringValue(row.getCell(serviceNameIdx)) : null;

        if (serviceName == null || serviceName.trim().isEmpty() ||
                serviceName.contains("مثال") || serviceName.toLowerCase().contains("example")) {
            return null;
        }

        serviceName = truncate(serviceName.trim(), 255);

        // --- 2. Extract Optional Fields ---
        String serviceCode = null;
        Integer serviceCodeIdx = columnIndices.get("service_code");
        if (serviceCodeIdx != null) {
            String excelCode = getCellStringValue(row.getCell(serviceCodeIdx));
            if (excelCode != null && !excelCode.isBlank()) {
                serviceCode = truncate(excelCode.trim(), 50);
            }
        }
        if (serviceCode == null)
            serviceCode = extractCodeFromName(serviceName);

        BigDecimal basePrice = null;
        Integer basePriceIdx = columnIndices.get("base_price");
        if (basePriceIdx != null)
            basePrice = readBigDecimal(row.getCell(basePriceIdx), rowNum);

        BigDecimal contractPrice = null;
        BigDecimal maxContractPrice = null;
        Integer contractPriceIdx = columnIndices.get("contract_price");
        if (contractPriceIdx != null) {
            PriceRange range = parsePriceRange(row.getCell(contractPriceIdx), rowNum);
            if (range != null) {
                contractPrice = range.min;
                maxContractPrice = range.max;
            }
        }

        if (basePrice == null && contractPrice != null)
            basePrice = contractPrice;
        else if (contractPrice == null && basePrice != null)
            contractPrice = basePrice;

        if (basePrice == null)
            basePrice = BigDecimal.ZERO;
        if (contractPrice == null)
            contractPrice = BigDecimal.ZERO;
        if (maxContractPrice == null)
            maxContractPrice = contractPrice;

        String rawCategoryCode = null;
        Integer categoryCodeIdx = columnIndices.get("medical_category_code");
        if (categoryCodeIdx != null) {
            rawCategoryCode = getCellStringValue(row.getCell(categoryCodeIdx));
            if (rawCategoryCode != null)
                rawCategoryCode = truncate(rawCategoryCode.trim(), 80);
        }

        String rawCategoryName = null;
        Integer categoryIdx = columnIndices.get("medical_category_name");
        if (categoryIdx == null)
            categoryIdx = columnIndices.get("main_category");
        if (categoryIdx == null)
            categoryIdx = columnIndices.get("provider_category");
        if (categoryIdx != null) {
            rawCategoryName = getCellStringValue(row.getCell(categoryIdx));
            if (rawCategoryName != null)
                rawCategoryName = truncate(rawCategoryName.trim(), 255);
        }

        String rawSubCategoryName = null;
        Integer subCategoryIdx = columnIndices.get("sub_category");
        if (subCategoryIdx != null) {
            rawSubCategoryName = getCellStringValue(row.getCell(subCategoryIdx));
            if (rawSubCategoryName != null)
                rawSubCategoryName = truncate(rawSubCategoryName.trim(), 255);
        }

        MedicalCategory resolvedCategory = resolveMedicalCategory(rawCategoryCode, rawCategoryName, rawSubCategoryName);

        String categoryName = rawCategoryName;
        String subCategoryName = rawSubCategoryName;

        if (resolvedCategory != null) {
            categoryName = displayCategoryName(resolvedCategory);
            subCategoryName = null;
        } else if (categoryName == null) {
            categoryName = subCategoryName;
        }

        Integer notesIdx = columnIndices.get("notes");
        String notes = notesIdx != null ? getCellStringValue(row.getCell(notesIdx)) : null;
        if (notes != null)
            notes = truncate(notes.trim(), MAX_NOTES);

        return ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceName(serviceName)
                .serviceCode(serviceCode)
                .medicalCategory(resolvedCategory)
                .categoryName(categoryName)
                .subCategoryName(subCategoryName)
                .basePrice(basePrice)
                .contractPrice(contractPrice)
                .maxContractPrice(maxContractPrice)
                .notes(notes)
                .active(true)
                .build();
    }

    /**
     * Insert or update a pricing item by service code (preferred) then service
     * name.
     * Searches all active items regardless of mapping status.
     *
     * @return true if an existing record was updated, false if a new one was
     *         created
     */
    private boolean upsertPricingItem(ProviderContract contract, ProviderContractPricingItem draft) {
        Optional<ProviderContractPricingItem> existing = Optional.empty();

        String safeServiceCode = draft.getServiceCode() == null ? "" : draft.getServiceCode().trim();
        String safeServiceName = draft.getServiceName() == null ? "" : draft.getServiceName().trim();
        String safeCategoryName = draft.getCategoryName() == null ? "" : draft.getCategoryName().trim();
        String safeSubCategoryName = draft.getSubCategoryName() == null ? "" : draft.getSubCategoryName().trim();

        // Prefer stable indexed lookups first, then validate category identity in Java.
        if (!safeServiceName.isBlank()) {
            if (!safeServiceCode.isBlank()) {
                existing = pricingRepository.findByContractIdAndServiceCodeActiveTrue(contract.getId(), safeServiceCode)
                        .filter(item -> sameImportIdentity(item, safeCategoryName, safeSubCategoryName));
            }
            if (existing.isEmpty()) {
                existing = pricingRepository.findByContractIdAndServiceNameActiveTrue(contract.getId(), safeServiceName)
                        .filter(item -> sameImportIdentity(item, safeCategoryName, safeSubCategoryName));
            }
        }

        if (existing.isEmpty() && !safeServiceCode.isBlank() && safeServiceName.isBlank()) {
            existing = pricingRepository
                    .findByContractIdAndServiceCodeActiveTrue(contract.getId(), safeServiceCode);
        }
        if (existing.isEmpty() && safeServiceCode.isBlank() && !safeServiceName.isBlank()
                && safeCategoryName.isBlank() && safeSubCategoryName.isBlank()) {
            existing = pricingRepository
                    .findByContractIdAndServiceNameActiveTrue(contract.getId(), safeServiceName);
        }

        if (existing.isPresent()) {
            ProviderContractPricingItem item = existing.get();
            item.setServiceCode(draft.getServiceCode());
            item.setMedicalCategory(draft.getMedicalCategory());
            item.setCategoryName(draft.getCategoryName());
            item.setSubCategoryName(draft.getSubCategoryName());
            item.setSpecialty(draft.getSpecialty());
            item.setBasePrice(draft.getBasePrice());
            item.setContractPrice(draft.getContractPrice());
            item.setMaxContractPrice(draft.getMaxContractPrice());
            item.setNotes(draft.getNotes());
            pricingRepository.save(item);
            return true;
        }

        pricingRepository.save(draft);
        return false;
    }

    private boolean sameImportIdentity(ProviderContractPricingItem item, String categoryName, String subCategoryName) {
        String itemCategory = item.getCategoryName() == null ? "" : item.getCategoryName().trim();
        String itemSubCategory = item.getSubCategoryName() == null ? "" : item.getSubCategoryName().trim();

        return itemCategory.equalsIgnoreCase(categoryName) && itemSubCategory.equalsIgnoreCase(subCategoryName);
    }

    /**
     * Extracts an embedded service code from a name, e.g. "Incision WE-001" →
     * "WE-001".
     */
    private String extractCodeFromName(String name) {
        if (name == null || name.isBlank())
            return null;
        java.util.regex.Matcher m = CODE_IN_NAME_PATTERN.matcher(name);
        return m.find() ? m.group() : null;
    }

    private static class PriceRange {
        BigDecimal min;
        BigDecimal max;
        PriceRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }
    }

    private PriceRange parsePriceRange(Cell cell, int rowNum) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue());
                return new PriceRange(val, val);
            } else {
                String val = getCellStringValue(cell);
                if (val != null && !val.trim().isEmpty()) {
                    val = val.trim().replace(",", "");
                    if (val.contains("-")) {
                        String[] parts = val.split("-");
                        if (parts.length == 2) {
                            BigDecimal min = new BigDecimal(parts[0].trim());
                            BigDecimal max = new BigDecimal(parts[1].trim());
                            // ensure min is smaller or equal
                            if (min.compareTo(max) > 0) {
                                BigDecimal temp = min;
                                min = max;
                                max = temp;
                            }
                            return new PriceRange(min, max);
                        }
                    }
                    BigDecimal single = new BigDecimal(val);
                    return new PriceRange(single, single);
                }
            }
        } catch (Exception e) {
            log.debug("[PriceListImport] Invalid price range value at row {}: {}", rowNum, e.getMessage());
        }
        return null;
    }

    private BigDecimal readBigDecimal(Cell cell, int rowNum) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                String val = getCellStringValue(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return new BigDecimal(val.trim().replace(",", ""));
                }
            }
        } catch (Exception e) {
            log.debug("[PriceListImport] Invalid numeric value at row {}: {}", rowNum, e.getMessage());
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

    // ═══════════════════════════════════════════════════════════════════════════
    // CATEGORY RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves a MedicalCategory using the modern unified category code first.
     * Legacy main/sub category names remain supported only as a compatibility
     * fallback for old provider files.
     */
    private MedicalCategory resolveMedicalCategory(String rawCategoryCode, String rawCategoryName, String rawSubCategoryName) {
        if (rawCategoryCode != null && !rawCategoryCode.isBlank()) {
            MedicalCategory byCode = findCategoryByCodeOrName(rawCategoryCode);
            if (byCode != null) {
                return byCode;
            }
        }
        if (rawCategoryName != null && !rawCategoryName.isBlank()) {
            MedicalCategory byName = findCategoryByCodeOrName(rawCategoryName);
            if (byName != null) {
                return byName;
            }
        }
        if (rawSubCategoryName != null && !rawSubCategoryName.isBlank()) {
            return findCategoryByCodeOrName(rawSubCategoryName);
        }
        return null;
    }

    private MedicalCategory findCategoryByCodeOrName(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isBlank()) {
            return null;
        }

        java.util.regex.Matcher codeMatcher = CATEGORY_CODE_PATTERN.matcher(trimmed.toUpperCase());
        if (codeMatcher.find()) {
            String extractedCode = codeMatcher.group(1).toUpperCase();
            Optional<MedicalCategory> byExtractedCode = medicalCategoryRepository.findActiveByCode(extractedCode);
            if (byExtractedCode.isPresent()) {
                return byExtractedCode.get();
            }
        }

        Optional<MedicalCategory> exactMatch = medicalCategoryRepository.findActiveByCode(trimmed)
                .or(() -> medicalCategoryRepository.findFirstByName(trimmed).filter(MedicalCategory::isActive))
                .or(() -> medicalCategoryRepository.findFirstByNameAr(trimmed).filter(MedicalCategory::isActive))
                .or(() -> medicalCategoryRepository.findFirstByNameEn(trimmed).filter(MedicalCategory::isActive));

        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        String normalizedInput = normalizeCategoryToken(trimmed);
        return medicalCategoryRepository.findByActiveTrue().stream()
                .filter(cat -> {
                    String code = normalizeCategoryToken(cat.getCode());
                    String name = normalizeCategoryToken(cat.getName());
                    String nameAr = normalizeCategoryToken(cat.getNameAr());
                    String nameEn = normalizeCategoryToken(cat.getNameEn());

                    return (!code.isBlank() && (normalizedInput.contains(code) || code.contains(normalizedInput))) ||
                            (!name.isBlank() && (normalizedInput.contains(name) || name.contains(normalizedInput))) ||
                            (!nameAr.isBlank()
                                    && (normalizedInput.contains(nameAr) || nameAr.contains(normalizedInput)))
                            ||
                            (!nameEn.isBlank()
                                    && (normalizedInput.contains(nameEn) || nameEn.contains(normalizedInput)));
                })
                .findFirst()
                .orElse(null);
    }

    private String normalizeCategoryToken(String value) {
        if (value == null) {
            return "";
        }
        return value
                .toLowerCase()
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('-', ' ')
                .replace('_', ' ')
                .replace('،', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String displayCategoryName(MedicalCategory category) {
        if (category == null) {
            return null;
        }
        if (category.getNameAr() != null && !category.getNameAr().isBlank()) {
            return truncate(category.getNameAr().trim(), 255);
        }
        if (category.getName() != null && !category.getName().isBlank()) {
            return truncate(category.getName().trim(), 255);
        }
        return truncate(category.getCode(), 255);
    }

    private String truncate(String text, int maxLen) {
        if (text == null)
            return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
