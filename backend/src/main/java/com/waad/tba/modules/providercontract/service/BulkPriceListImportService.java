package com.waad.tba.modules.providercontract.service;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.BulkImportResultDto;
import com.waad.tba.modules.providercontract.dto.BulkImportResultDto.ProviderImportResult;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.github.pjfanning.xlsx.StreamingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Bulk import service: reads a multi-provider classified price-list Excel file
 * (نتيجة_تصنيف_قوائم_الاسعار_بالقاموس_الموحد.xlsx) and distributes rows to
 * the correct provider contracts.
 *
 * <p>Expected sheet: "كل النتائج" (or first sheet as fallback).
 *
 * <p>Expected columns (Arabic headers, detected by keyword):
 * <ul>
 *   <li>المرفق   → provider name</li>
 *   <li>المجلد   → provider type hint</li>
 *   <li>الكود الأصلي → service_code</li>
 *   <li>اسم الخدمة عربي / اسم الخدمة إنجليزي → service name</li>
 *   <li>السعر    → contract_price</li>
 *   <li>التصنيف التأميني الرئيسي → main category</li>
 *   <li>الاختصاص/البند التأميني → sub category</li>
 *   <li>كود CAT  → CAT code for MedicalCategory lookup</li>
 * </ul>
 *
 * <p>Behaviour:
 * <ul>
 *   <li>All rows are imported regardless of confidence level (default).</li>
 *   <li>Provider is created automatically when not found by name.</li>
 *   <li>Contract (ACTIVE) is created automatically when provider has none.</li>
 *   <li>Duplicate detection: service_code first, service_name second → UPDATE price.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkPriceListImportService {

    private static final String PRIMARY_SHEET = "كل النتائج";

    // Column keyword patterns (Arabic) – matched in lower-case trimmed header
    private static final String KW_PROVIDER    = "المرفق";
    private static final String KW_FOLDER      = "المجلد";
    private static final String KW_CODE        = "الكود الأصلي";
    private static final String KW_NAME_AR     = "اسم الخدمة عربي";
    private static final String KW_NAME_EN     = "اسم الخدمة إنجليزي";
    private static final String KW_PRICE       = "السعر";
    private static final String KW_MAIN_CAT    = "التصنيف التأميني الرئيسي";
    private static final String KW_SUB_CAT     = "الاختصاص/البند التأميني";
    private static final String KW_CAT_CODE    = "كود cat";

    private static final Pattern CAT_CODE_PATTERN =
            Pattern.compile("(CAT[0-9A-Z-]+)", Pattern.CASE_INSENSITIVE);

    private static final int MAX_ERRORS_PER_PROVIDER = 50;

    /** Recorded as the approver on any contract terms this import creates or amends. */
    private static final String BULK_IMPORT_ACTOR = "BULK_PRICE_LIST_IMPORT";

    private final ProviderRepository             providerRepository;
    private final ProviderContractRepository     contractRepository;
    private final ProviderContractPricingItemRepository pricingRepository;
    private final MedicalCategoryRepository      medicalCategoryRepository;
    private final ProviderContractTermsService   termsService;

    @lombok.Data
    private static class ParsedRow {
        private String providerName;
        private String folderHint;
        private String serviceCode;
        private String nameAr;
        private String nameEn;
        private BigDecimal price;
        private String mainCat;
        private String subCat;
        private String catCode;
        private int rowNum;
    }

    private final PlatformTransactionManager     transactionManager;

    /** Generate the canonical multi-provider services and prices import template. */
    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet(PRIMARY_SHEET);
            data.setRightToLeft(true);

            String[] headers = {
                    "المرفق", "المجلد", "الكود الأصلي", "اسم الخدمة عربي",
                    "اسم الخدمة إنجليزي", "السعر", "التصنيف التأميني الرئيسي",
                    "الاختصاص/البند التأميني", "كود CAT"
            };
            String[] example = {
                    "مستشفى السلام", "مستشفى", "SRV-001", "كشف اختصاصي",
                    "Specialist consultation", "50", "العيادات الخارجية",
                    "الكشوفات الطبية", "CAT-DIAGNOSTIC"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row header = data.createRow(0);
            Row sample = data.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sample.createCell(i).setCellValue(example[i]);
                data.setColumnWidth(i, Math.min(45, Math.max(16, headers[i].length() + 5)) * 256);
            }
            data.createFreezePane(0, 1);
            data.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));

            Sheet instructions = workbook.createSheet("تعليمات");
            instructions.setRightToLeft(true);
            String[] notes = {
                    "تعليمات استيراد الخدمات والأسعار الجماعي",
                    "• لا تغيّر أسماء الأعمدة أو صف العناوين.",
                    "• المرفق واسم خدمة واحد على الأقل مطلوبان في كل صف.",
                    "• الكود الأصلي يجب أن يكون ثابتًا وفريدًا داخل المرفق؛ يعاد استخدامه لتحديث الخدمة.",
                    "• السعر رقم بالدينار الليبي دون نص العملة.",
                    "• كود CAT هو كود التصنيف الطبي المعتمد في النظام، مثل CAT-LAB.",
                    "• المجلد يساعد النظام على استنتاج نوع المرفق عند إنشائه تلقائيًا.",
                    "• الصف الثاني مثال توضيحي؛ احذفه قبل تعبئة البيانات الحقيقية.",
                    "• الاستيراد يطابق المرفق بالاسم؛ وحّد الاسم تمامًا مع سجل المرافق لتجنب إنشاء مرفق مكرر."
            };
            for (int i = 0; i < notes.length; i++) instructions.createRow(i).createCell(0).setCellValue(notes[i]);
            instructions.setColumnWidth(0, 110 * 256);

            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Import all rows from the uploaded classified price-list Excel file.
     *
     * @param file the xlsx file (نتيجة_تصنيف_قوائم_الاسعار_بالقاموس_الموحد.xlsx)
     * @return aggregated result with per-provider breakdown
     */
    public BulkImportResultDto importFromClassifiedExcel(MultipartFile file) {
        log.info("[BulkImport] Starting bulk price-list import, file={}", file.getOriginalFilename());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        java.io.File tempFile = null;
        try {
            tempFile = java.io.File.createTempFile("bulk-import-", ".xlsx");
            file.transferTo(tempFile);
            
            try (Workbook workbook = StreamingReader.builder()
                    .rowCacheSize(100)
                    .bufferSize(8192)
                    .open(tempFile)) {

            // ── 1. Locate sheet ──────────────────────────────────────────────
            Sheet sheet = null;
            try {
                sheet = workbook.getSheet(PRIMARY_SHEET);
            } catch (Exception e) {
                log.warn("[BulkImport] Sheet '{}' not found, will fallback to first sheet. ({})", PRIMARY_SHEET, e.getMessage());
            }

            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            if (sheet == null) {
                return failResult("لم يتم العثور على ورقة البيانات في الملف");
            }

            // ── 2. Read rows using iterator ──────────────────────────────────
            Map<String, Integer> cols = null;
            Map<String, List<ParsedRow>> byProvider = new LinkedHashMap<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    cols = detectColumns(row);
                    log.info("[BulkImport] Detected columns: {}", cols);
                    if (!cols.containsKey("provider")) {
                        return failResult("لم يتم العثور على عمود 'المرفق' في الملف");
                    }
                    continue;
                }
                
                if (isEmptyRow(row)) continue;

                String providerName = getCellStr(row, cols.get("provider"));
                if (providerName == null || providerName.isBlank()) continue;
                providerName = providerName.trim();

                ParsedRow pr = new ParsedRow();
                pr.setRowNum(row.getRowNum() + 1);
                pr.setProviderName(providerName);
                if (cols.containsKey("folder")) pr.setFolderHint(getCellStr(row, cols.get("folder")));
                if (cols.containsKey("code")) pr.setServiceCode(getCellStr(row, cols.get("code")));
                if (cols.containsKey("nameAr")) pr.setNameAr(getCellStr(row, cols.get("nameAr")));
                if (cols.containsKey("nameEn")) pr.setNameEn(getCellStr(row, cols.get("nameEn")));
                if (cols.containsKey("price")) pr.setPrice(readDecimal(row, cols.get("price")));
                if (cols.containsKey("mainCat")) pr.setMainCat(getCellStr(row, cols.get("mainCat")));
                if (cols.containsKey("subCat")) pr.setSubCat(getCellStr(row, cols.get("subCat")));
                if (cols.containsKey("catCode")) pr.setCatCode(getCellStr(row, cols.get("catCode")));

                byProvider.computeIfAbsent(providerName, k -> new ArrayList<>()).add(pr);
            }

            if (cols == null) {
                return failResult("الملف فارغ أو لا يحتوي على رؤوس الأعمدة");
            }

            log.info("[BulkImport] Found {} distinct providers", byProvider.size());

            // ── 4. Process each provider ──────────────────────────────────────
            BulkImportResultDto.BulkImportResultDtoBuilder result = BulkImportResultDto.builder()
                    .totalProviders(byProvider.size());

            List<ProviderImportResult> providerResults = new ArrayList<>();
            int totalCreated = 0, totalUpdated = 0, totalSkipped = 0,
                    totalFailed = 0, totalRows = 0, providersCreated = 0, providersMatched = 0;

            for (Map.Entry<String, List<ParsedRow>> entry : byProvider.entrySet()) {
                String providerName = entry.getKey();
                List<ParsedRow> rows = entry.getValue();

                // Detect provider type from folder column (best-effort)
                String folderHint = rows.isEmpty() ? null : rows.get(0).getFolderHint();
                ProviderType providerType = inferProviderType(folderHint);

                // Resolve or create Provider + Contract
                ProviderAndContract pac = tx.execute(status -> {
                    try {
                        return resolveProviderAndContract(providerName, providerType);
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                });

                if (pac == null) {
                    providerResults.add(ProviderImportResult.builder()
                            .providerName(providerName)
                            .status("FAILED")
                            .errors(List.of("فشل إنشاء أو إيجاد مقدم الخدمة"))
                            .build());
                    continue;
                }

                if (pac.providerCreated) providersCreated++; else providersMatched++;

                // Process rows for this provider
                ProviderImportResult.ProviderImportResultBuilder pRes = ProviderImportResult.builder()
                        .providerName(providerName)
                        .providerId(pac.provider.getId())
                        .contractId(pac.contract.getId())
                        .providerCreated(pac.providerCreated)
                        .contractCreated(pac.contractCreated)
                        .contractActivated(pac.contractActivated);

                int pCreated = 0, pUpdated = 0, pSkipped = 0, pFailed = 0;
                List<String> pErrors = new ArrayList<>();

                for (ParsedRow row : rows) {
                    final ProviderContract contract = pac.contract;

                    try {
                        Boolean updated = tx.execute(status -> {
                            try {
                                return processRow(row, contract);
                            } catch (Exception e) {
                                status.setRollbackOnly();
                                throw e;
                            }
                        });

                        if (updated == null) {
                            pSkipped++;
                        } else if (updated) {
                            pUpdated++;
                        } else {
                            pCreated++;
                        }
                    } catch (Exception e) {
                        pFailed++;
                        String msg = "صف " + row.getRowNum() + ": " + e.getMessage();
                        log.warn("[BulkImport] {} - {}", providerName, msg);
                        if (pErrors.size() < MAX_ERRORS_PER_PROVIDER) {
                            pErrors.add(msg);
                        }
                    }
                }

                String status = pFailed == rows.size() ? "FAILED"
                        : pFailed > 0 ? "PARTIAL" : "SUCCESS";

                providerResults.add(pRes
                        .rowsProcessed(rows.size())
                        .created(pCreated)
                        .updated(pUpdated)
                        .skipped(pSkipped)
                        .failed(pFailed)
                        .errors(pErrors)
                        .status(status)
                        .build());

                totalRows    += rows.size();
                totalCreated += pCreated;
                totalUpdated += pUpdated;
                totalSkipped += pSkipped;
                totalFailed  += pFailed;
            }

            String summaryAr = String.format(
                    "تم معالجة %d مرفق (%d جديد، %d موجود). " +
                    "الخدمات: إضافة %d، تحديث %d، تخطي %d، فشل %d.",
                    byProvider.size(), providersCreated, providersMatched,
                    totalCreated, totalUpdated, totalSkipped, totalFailed);
            String summaryEn = String.format(
                    "Processed %d providers (%d new, %d existing). " +
                    "Services: created %d, updated %d, skipped %d, failed %d.",
                    byProvider.size(), providersCreated, providersMatched,
                    totalCreated, totalUpdated, totalSkipped, totalFailed);

            log.info("[BulkImport] Completed. {}", summaryEn);

            return result
                    .providersCreated(providersCreated)
                    .providersMatched(providersMatched)
                    .totalRowsProcessed(totalRows)
                    .totalCreated(totalCreated)
                    .totalUpdated(totalUpdated)
                    .totalSkipped(totalSkipped)
                    .totalFailed(totalFailed)
                    .providerResults(providerResults)
                    .summaryAr(summaryAr)
                    .summaryEn(summaryEn)
                    .build();
            }

        } catch (Exception e) {
            log.error("[BulkImport] Failed to read Excel file", e);
            return failResult("فشل في قراءة الملف: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    log.warn("[BulkImport] Failed to delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROVIDER + CONTRACT RESOLUTION
    // ─────────────────────────────────────────────────────────────────────────

    private static class ProviderAndContract {
        Provider provider;
        ProviderContract contract;
        boolean providerCreated;
        boolean contractCreated;
        boolean contractActivated; // true when an existing DRAFT/SUSPENDED contract was activated
    }

    /**
     * Find-or-create a Provider by name (case-insensitive), then find-or-create
     * its active contract.
     */
    private ProviderAndContract resolveProviderAndContract(String providerName, ProviderType type) {
        ProviderAndContract pac = new ProviderAndContract();

        // ── Provider ──────────────────────────────────────────────────────────
        Optional<Provider> existing = providerRepository.findByNameIgnoreCase(providerName);
        if (existing.isPresent()) {
            pac.provider = existing.get();
            pac.providerCreated = false;
            log.debug("[BulkImport] Matched existing provider: {} (id={})",
                    providerName, pac.provider.getId());
        } else {
            // Auto-generate a unique license number  (can be edited later in UI)
            String licenseNum = "AUTO-" + providerName.replaceAll("\\s+", "-")
                    .replaceAll("[^a-zA-Z0-9\\u0600-\\u06FF-]", "")
                    .substring(0, Math.min(20, providerName.replaceAll("\\s+", "-").length()))
                    + "-" + System.currentTimeMillis() % 100000;

            Provider newProvider = Provider.builder()
                    .name(providerName)
                    .licenseNumber(licenseNum)
                    .providerType(type)
                    .active(true)
                    .allowAllEmployers(false)
                    .build();

            pac.provider = providerRepository.save(newProvider);
            pac.providerCreated = true;
            log.info("[BulkImport] Created new provider: {} (id={})",
                    providerName, pac.provider.getId());
        }

        // ── Contract ──────────────────────────────────────────────────────────
        Optional<ProviderContract> activeContract =
                contractRepository.findActiveContractByProvider(pac.provider.getId());

        if (activeContract.isPresent()) {
            pac.contract = activeContract.get();
            pac.contractCreated = false;
            // Ensure discount is set even on existing active contracts
            if (pac.contract.getDiscountPercent() == null
                    || pac.contract.getDiscountPercent().compareTo(BigDecimal.ZERO) == 0) {
                pac.contract.setDiscountPercent(new BigDecimal("10.00"));
                contractRepository.save(pac.contract);
            }
            termsService.ensureEffectiveTerms(pac.contract, BULK_IMPORT_ACTOR);
        } else {
            // Check if any contract exists (DRAFT / SUSPENDED) to reuse
            List<ProviderContract> anyContracts =
                    contractRepository.findByProviderIdAndActiveTrue(pac.provider.getId());

            if (!anyContracts.isEmpty()) {
                pac.contract = anyContracts.get(0);
                pac.contractCreated = false;
                // Activate it and set default discount
                pac.contract.setStatus(ContractStatus.ACTIVE);
                if (pac.contract.getDiscountPercent() == null
                        || pac.contract.getDiscountPercent().compareTo(BigDecimal.ZERO) == 0) {
                    pac.contract.setDiscountPercent(new BigDecimal("10.00"));
                }
                contractRepository.save(pac.contract);
                termsService.ensureEffectiveTerms(pac.contract, BULK_IMPORT_ACTOR);
                pac.contractActivated = true;
                log.info("[BulkImport] Activated existing contract {} for provider {}",
                        pac.contract.getContractCode(), providerName);
            } else {
                String contractCode = "BULK-" + pac.provider.getId() + "-"
                        + (System.currentTimeMillis() % 1000000);

                ProviderContract newContract = ProviderContract.builder()
                        .contractCode(contractCode)
                        .provider(pac.provider)
                        .status(ContractStatus.ACTIVE)
                        .pricingModel(PricingModel.FIXED)
                        .discountPercent(new BigDecimal("10.00"))
                        .startDate(LocalDate.now())
                        .active(true)
                        .discountBeforeRejection(true)
                        .currency("LYD")
                        .notes("تم إنشاؤه تلقائياً عند استيراد القائمة الموحدة")
                        .build();

                pac.contract = contractRepository.save(newContract);
                // Mandatory: the effective-contract resolver fails closed, so a contract
                // created without terms makes every future claim for this provider
                // unwritable.
                termsService.ensureEffectiveTerms(pac.contract, BULK_IMPORT_ACTOR);
                pac.contractCreated = true;
                log.info("[BulkImport] Created contract {} for provider {} (status=ACTIVE, discount=10%)",
                        contractCode, providerName);
            }
        }

        return pac;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROW PROCESSING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Process one data row.
     *
     * @return {@code null} if skipped, {@code true} if updated, {@code false} if created
     */
    private Boolean processRow(ParsedRow row, ProviderContract contract) {

        // ── Service name (Arabic preferred, English fallback) ─────────────────
        String nameAr = row.getNameAr();
        String nameEn = row.getNameEn();
        String serviceName = (nameAr != null && !nameAr.isBlank()) ? nameAr.trim()
                           : (nameEn != null ? nameEn.trim() : null);

        if (serviceName == null || serviceName.isBlank()) {
            return null; // skip – no meaningful service name
        }
        serviceName = truncate(serviceName, 255);

        // ── Service code ──────────────────────────────────────────────────────
        String serviceCode = row.getServiceCode();
        if (serviceCode != null) serviceCode = truncate(serviceCode.trim(), 50);
        if (serviceCode != null && serviceCode.isBlank()) serviceCode = null;

        // ── Price ─────────────────────────────────────────────────────────────
        BigDecimal price = row.getPrice();
        if (price == null) price = BigDecimal.ZERO;

        // ── Categories ────────────────────────────────────────────────────────
        String mainCat = row.getMainCat();
        String subCat  = row.getSubCat();
        String catCode = row.getCatCode();

        if (mainCat != null) mainCat = truncate(mainCat.trim(), 255);
        if (subCat  != null) subCat  = truncate(subCat.trim(),  255);

        // Resolve MedicalCategory (CAT code first, then name)
        MedicalCategory medCat = resolveMedicalCategory(catCode, mainCat, subCat);

        // ── Upsert ────────────────────────────────────────────────────────────
        return upsert(contract, serviceCode, serviceName, price, mainCat, subCat, medCat);
    }

    /**
     * Find existing item by code then name; update price / category if found,
     * otherwise insert new item.
     *
     * @return true=updated, false=created
     */
    private boolean upsert(ProviderContract contract,
                           String serviceCode, String serviceName,
                           BigDecimal price,
                           String mainCat, String subCat,
                           MedicalCategory medCat) {

        Optional<ProviderContractPricingItem> found = Optional.empty();

        // Priority 1: match by service code
        if (serviceCode != null && !serviceCode.isBlank()) {
            found = pricingRepository.findByContractIdAndServiceCodeActiveTrue(
                    contract.getId(), serviceCode);
        }

        // Priority 2: match by service name (case-insensitive)
        if (found.isEmpty()) {
            found = pricingRepository.findByContractIdAndServiceNameActiveTrue(
                    contract.getId(), serviceName);
        }

        if (found.isPresent()) {
            ProviderContractPricingItem item = found.get();
            item.setContractPrice(price);
            item.setBasePrice(price);
            if (serviceCode != null) item.setServiceCode(serviceCode);
            if (medCat != null)      item.setMedicalCategory(medCat);
            if (mainCat != null)     item.setCategoryName(mainCat);
            if (subCat != null)      item.setSubCategoryName(subCat);
            pricingRepository.save(item);
            return true; // updated
        }

        ProviderContractPricingItem newItem = ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceName(serviceName)
                .serviceCode(serviceCode)
                .basePrice(price)
                .contractPrice(price)
                .maxContractPrice(price)
                .categoryName(mainCat)
                .subCategoryName(subCat)
                .medicalCategory(medCat)
                .active(true)
                .currency("LYD")
                .build();

        pricingRepository.save(newItem);
        return false; // created
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MEDICAL CATEGORY RESOLUTION
    // ─────────────────────────────────────────────────────────────────────────

    private MedicalCategory resolveMedicalCategory(String catCode, String mainCat, String subCat) {
        // 1. Try CAT code directly (e.g. "CAT023")
        if (catCode != null && !catCode.isBlank()) {
            java.util.regex.Matcher m = CAT_CODE_PATTERN.matcher(catCode.trim().toUpperCase());
            if (m.find()) {
                Optional<MedicalCategory> byCode =
                        medicalCategoryRepository.findActiveByCode(m.group(1).toUpperCase());
                if (byCode.isPresent()) return byCode.get();
            }
        }
        // 2. Try sub-category name
        if (subCat != null && !subCat.isBlank()) {
            MedicalCategory c = findCategoryByName(subCat);
            if (c != null) return c;
        }
        // 3. Try main-category name
        if (mainCat != null && !mainCat.isBlank()) {
            return findCategoryByName(mainCat);
        }
        return null;
    }

    private MedicalCategory findCategoryByName(String name) {
        String t = name.trim();
        return medicalCategoryRepository.findFirstByNameAr(t)
                .filter(MedicalCategory::isActive)
                .or(() -> medicalCategoryRepository.findFirstByNameEn(t).filter(MedicalCategory::isActive))
                .or(() -> medicalCategoryRepository.findFirstByName(t).filter(MedicalCategory::isActive))
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROVIDER TYPE INFERENCE
    // ─────────────────────────────────────────────────────────────────────────

    private ProviderType inferProviderType(String folderHint) {
        if (folderHint == null) return ProviderType.CLINIC;
        String f = folderHint.trim();
        if (f.contains("أسنان") || f.contains("اسنان"))   return ProviderType.CLINIC_DEN;
        if (f.contains("مختبر") || f.contains("lab"))      return ProviderType.LAB;
        if (f.contains("بصريات") || f.contains("عيون"))   return ProviderType.CLINIC;
        if (f.contains("علاج طبيعي") || f.contains("physio")) return ProviderType.PHYSIOTHERAPY;
        if (f.contains("مصحة") || f.contains("مستشفى"))   return ProviderType.HOSPITAL;
        return ProviderType.CLINIC;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COLUMN DETECTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build a logical-key → column-index map from the header row.
     * Matching is done on lower-cased trimmed cell values.
     */
    private Map<String, Integer> detectColumns(Row headerRow) {
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String h = getCellStr(cell);
            if (h == null) continue;
            String v = h.trim().toLowerCase();

            if (v.equals(KW_PROVIDER.toLowerCase()) || v.contains("اسم المرفق"))         cols.put("provider", i);
            else if (v.equals(KW_FOLDER.toLowerCase()))                                    cols.put("folder", i);
            else if (v.equals(KW_CODE.toLowerCase()) || v.equals("service_code / الكود") || (v.contains("الكود") && !v.contains("المرفق")))  cols.put("code", i);
            else if (v.equals(KW_NAME_EN.toLowerCase()))                                   cols.put("nameEn", i);
            else if (v.equals(KW_NAME_AR.toLowerCase()) ||
                    (v.contains("اسم الخدمة") && !v.contains("إنجليزي") && !v.contains("انجليزي"))) cols.put("nameAr", i);
            else if (v.equals(KW_PRICE.toLowerCase()) || v.contains("سعر العقد"))          cols.put("price", i);
            else if (v.equals(KW_MAIN_CAT.toLowerCase()) || v.contains("التصنيف الرئيسي")) cols.put("mainCat", i);
            else if (v.contains("البند") || v.contains("الاختصاص") || v.contains("التصنيف الفرعي")) cols.put("subCat", i);
            else if (v.equals(KW_CAT_CODE.toLowerCase()) || v.startsWith("كود cat"))       cols.put("catCode", i);
        }
        return cols;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXCEL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String getCellStr(Row row, Integer colIdx) {
        if (row == null || colIdx == null) return null;
        return getCellStr(row.getCell(colIdx));
    }

    private String getCellStr(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell))
                    return cell.getLocalDateTimeCellValue().toString();
                double d = cell.getNumericCellValue();
                return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); }
                catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            default:      return null;
        }
    }

    private BigDecimal readDecimal(Row row, Integer colIdx) {
        if (row == null || colIdx == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC)
                return BigDecimal.valueOf(cell.getNumericCellValue());
            String v = getCellStr(cell);
            if (v != null && !v.isBlank())
                return new BigDecimal(v.trim().replace(",", ""));
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String v = getCellStr(cell);
                if (v != null && !v.trim().isEmpty()) return false;
            }
        }
        return true;
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private BulkImportResultDto failResult(String messageAr) {
        return BulkImportResultDto.builder()
                .summaryAr("فشل الاستيراد: " + messageAr)
                .summaryEn("Import failed: " + messageAr)
                .build();
    }
}
