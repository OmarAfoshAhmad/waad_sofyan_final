package com.waad.tba.modules.providercontract.service;

import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportError;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportSummary;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.dto.ClassificationResult;
import com.waad.tba.modules.providercontract.dto.PricingImportConfirmRequest;
import com.waad.tba.modules.providercontract.dto.PricingImportModificationDto;
import com.waad.tba.modules.providercontract.dto.PricingImportPreviewDto;
import com.waad.tba.modules.providercontract.dto.PricingImportPreviewItemDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.entity.ServiceSpecialtyInsuranceMap;
import com.waad.tba.modules.providercontract.enums.ClassificationStatus;
import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ServiceSpecialtyInsuranceMapRepository;
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
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderContractPricingExcelService {

    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final PricingItemClassificationEngine classificationEngine;
    private final PricingImportSessionCache sessionCache;
    private final ServiceSpecialtyInsuranceMapRepository mapRepository;

    private static final String TEMPLATE_REQUIRED_COLS = "service_name / اسم الخدمة ★";
    private static final String TEMPLATE_OPTIONAL_COLS = "service_code / الكود | unit_price / السعر | category / التصنيف | specialty / التخصص | notes / ملاحظات";

    private static final Map<String, String> COLUMN_MAPPINGS = new java.util.LinkedHashMap<>();

    static {
        // Sequence
        COLUMN_MAPPINGS.put("تسلسل", "sequence");
        COLUMN_MAPPINGS.put("sequence", "sequence");

        // Price List Name
        COLUMN_MAPPINGS.put("قائمة الأسعار", "priceListName");
        COLUMN_MAPPINGS.put("price list", "priceListName");
        COLUMN_MAPPINGS.put("pricelist", "priceListName");

        // Service Name (REQUIRED)
        COLUMN_MAPPINGS.put("service_name / اسم الخدمة ★", "serviceName");
        COLUMN_MAPPINGS.put("service_name", "serviceName");
        COLUMN_MAPPINGS.put("قالب المنتج", "serviceName");
        COLUMN_MAPPINGS.put("service name", "serviceName");
        COLUMN_MAPPINGS.put("product template", "serviceName");
        COLUMN_MAPPINGS.put("اسم الخدمة", "serviceName");
        COLUMN_MAPPINGS.put("اسم الخدمة ★", "serviceName");

        // Service Code
        COLUMN_MAPPINGS.put("service_code / الكود", "serviceCode");
        COLUMN_MAPPINGS.put("service_code", "serviceCode");
        COLUMN_MAPPINGS.put("كود منتج المورد", "serviceCode");
        COLUMN_MAPPINGS.put("supplier product code", "serviceCode");
        COLUMN_MAPPINGS.put("service code", "serviceCode");
        COLUMN_MAPPINGS.put("code", "serviceCode");
        COLUMN_MAPPINGS.put("الكود", "serviceCode");

        // Currency
        COLUMN_MAPPINGS.put("العملة", "currency");
        COLUMN_MAPPINGS.put("currency", "currency");

        // Quantity
        COLUMN_MAPPINGS.put("الكمية", "quantity");
        COLUMN_MAPPINGS.put("quantity", "quantity");

        // Contract Price (REQUIRED)
        COLUMN_MAPPINGS.put("unit_price / السعر", "contractPrice");
        COLUMN_MAPPINGS.put("unit_price", "contractPrice");
        COLUMN_MAPPINGS.put("السعر", "contractPrice");
        COLUMN_MAPPINGS.put("price", "contractPrice");
        COLUMN_MAPPINGS.put("سعر", "contractPrice");
        COLUMN_MAPPINGS.put("contract_price / سعر العقد", "contractPrice");
        COLUMN_MAPPINGS.put("contract_price", "contractPrice");
        COLUMN_MAPPINGS.put("سعر العقد", "contractPrice");

        // Extra fields
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
     * Phase 1: Preview Import
     */
    public PricingImportPreviewDto importForPreview(Long contractId, MultipartFile file) {
        log.info("Starting Excel preview import for contract ID: {}", contractId);

        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        if (contract.getStatus() == ProviderContract.ContractStatus.EXPIRED ||
                contract.getStatus() == ProviderContract.ContractStatus.TERMINATED) {
            throw new IllegalStateException("Cannot import pricing for EXPIRED or TERMINATED contract");
        }

        List<PricingImportPreviewItemDto> items = new ArrayList<>();
        Set<String> usedCodesInSession = new HashSet<>();

        int highConf = 0, mediumConf = 0, lowConf = 0, manualRev = 0, zeroPrice = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IllegalArgumentException("❌ الملف فارغ أو لا يحتوي على سطر رأس (Header). تأكد من استخدام القالب الرسمي.");
            }

            Map<String, Integer> columnIndices = mapColumns(headerRow);

            if (!columnIndices.containsKey("serviceName") && !columnIndices.containsKey("serviceCode")) {
                throw new IllegalArgumentException("❌ الملف لا يطابق القالب المطلوب. يجب توفر اسم الخدمة أو الكود.");
            }

            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) continue;

                String serviceCodeValue = getCellValueAsString(row, columnIndices.get("serviceCode"));
                String serviceNameValue = getCellValueAsString(row, columnIndices.get("serviceName"));
                BigDecimal contractPriceValue = getCellValueAsDecimal(row, columnIndices.get("contractPrice"));
                String currencyValue = getCellValueAsString(row, columnIndices.get("currency"));
                String mainCatCode = getCellValueAsString(row, columnIndices.get("mainCategory"));
                String subCatCode = getCellValueAsString(row, columnIndices.get("subCategory"));
                String specialtyValue = getCellValueAsString(row, columnIndices.get("specialty"));

                if (contractPriceValue == null || contractPriceValue.compareTo(BigDecimal.ZERO) < 0) {
                    continue; // Skip invalid prices for preview
                }

                if (serviceCodeValue == null && serviceNameValue == null) {
                    continue;
                }

                if ((serviceCodeValue == null || serviceCodeValue.isBlank()) && serviceNameValue != null) {
                    serviceCodeValue = generateUniqueServiceCode(contractId, serviceNameValue, usedCodesInSession);
                }
                if (serviceCodeValue != null && !serviceCodeValue.isBlank()) {
                    usedCodesInSession.add(serviceCodeValue.trim().toUpperCase());
                }

                String currency = (currencyValue != null && !currencyValue.isBlank()) ? currencyValue.trim().toUpperCase() : "LYD";
                boolean isZero = contractPriceValue.compareTo(BigDecimal.ZERO) == 0;

                // CLASSIFICATION LOGIC
                MedicalCategory assignedCategory = null;
                ConfidenceLevel confidence = ConfidenceLevel.LOW;
                String classificationSource = "NONE";
                EncounterType encounterType = EncounterType.ANY;
                boolean requiresReview = false;
                String reviewReason = null;

                String targetCatCode = (subCatCode != null && !subCatCode.isBlank()) ? subCatCode : mainCatCode;

                // Step 1: Direct Match (by Code or Name)
                if (targetCatCode != null && !targetCatCode.trim().isBlank()) {
                    String trimTarget = targetCatCode.trim();
                    
                    String codeCandidate = trimTarget;
                    if (codeCandidate.contains(" - ")) {
                        codeCandidate = codeCandidate.substring(0, codeCandidate.indexOf(" - ")).trim();
                    } else if (codeCandidate.contains("- ")) {
                        codeCandidate = codeCandidate.substring(0, codeCandidate.indexOf("- ")).trim();
                    }
                    
                    assignedCategory = categoryRepository.findByCode(codeCandidate).orElse(null);
                    
                    if (assignedCategory == null) {
                        assignedCategory = categoryRepository.findByCode(trimTarget).orElse(null);
                    }
                    if (assignedCategory == null) {
                        assignedCategory = categoryRepository.findFirstByNameAr(trimTarget).orElse(null);
                    }
                    if (assignedCategory == null) {
                        assignedCategory = categoryRepository.findFirstByNameEn(trimTarget).orElse(null);
                    }
                    if (assignedCategory == null) {
                        assignedCategory = categoryRepository.findFirstByName(trimTarget).orElse(null);
                    }

                    if (assignedCategory != null) {
                        confidence = ConfidenceLevel.HIGH;
                        classificationSource = "EXACT_MATCH";
                        encounterType = EncounterType.ANY;
                    }
                }

                // Step 2: Use Engine
                if (assignedCategory == null) {
                    ClassificationResult result = classificationEngine.classify(serviceNameValue, mainCatCode, specialtyValue, contract.getProvider().getId());
                    assignedCategory = result.getCategory();
                    confidence = result.getConfidenceLevel();
                    classificationSource = result.getClassificationSource();
                    encounterType = result.getEncounterType();
                    requiresReview = result.isRequiresReview();
                    reviewReason = result.getReviewReason();
                }

                // Step 3: Override Outpatient code for Inpatient service
                if ("إيواء".equals(mainCatCode) && assignedCategory != null && assignedCategory.getCode().startsWith("CAT-OP")) {
                    assignedCategory = categoryRepository.findByCode("CAT-IP").orElse(null);
                    confidence = ConfidenceLevel.MEDIUM;
                    requiresReview = true;
                    reviewReason = "تصنيف عيادات خارجية مطبَّق على خدمة إيواء — يتطلب مراجعة";
                    classificationSource = "OVERRIDE_OUTPATIENT_IN_INPATIENT";
                }

                PricingImportPreviewItemDto item = PricingImportPreviewItemDto.builder()
                        .rowId(UUID.randomUUID().toString())
                        .serviceName(serviceNameValue)
                        .serviceCode(serviceCodeValue)
                        .contractPrice(contractPriceValue)
                        .currency(currency)
                        .importedMainCategory(mainCatCode)
                        .importedSubCategory(targetCatCode)
                        .proposedCategoryId(assignedCategory != null ? assignedCategory.getId() : null)
                        .proposedCategoryName(assignedCategory != null ? assignedCategory.getName() : null)
                        .proposedCategoryCode(assignedCategory != null ? assignedCategory.getCode() : null)
                        .encounterType(encounterType)
                        .confidenceLevel(confidence)
                        .requiresReview(requiresReview)
                        .reviewReason(reviewReason)
                        .classificationSource(classificationSource)
                        .isPriceZero(isZero)
                        .build();

                items.add(item);

                if (isZero) zeroPrice++;
                if (requiresReview) manualRev++;

                if (confidence == ConfidenceLevel.HIGH) highConf++;
                else if (confidence == ConfidenceLevel.MEDIUM) mediumConf++;
                else lowConf++;
            }

        } catch (Exception e) {
            log.error("Error reading Excel file", e);
            throw new IllegalArgumentException("خطأ في قراءة ملف Excel: " + e.getMessage());
        }

        // Save session
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("contractId", contractId);
        sessionData.put("items", items);
        String sessionId = sessionCache.put(sessionData);

        return PricingImportPreviewDto.builder()
                .importSessionId(sessionId)
                .totalItems(items.size())
                .highConfidenceCount(highConf)
                .mediumConfidenceCount(mediumConf)
                .lowConfidenceCount(lowConf)
                .manualReviewCount(manualRev)
                .zeroPriceCount(zeroPrice)
                .items(items)
                .build();
    }

    /**
     * Phase 2: Confirm Import
     */
    @Transactional
    public ExcelImportResultDto confirmImport(Long contractId, PricingImportConfirmRequest request) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        @SuppressWarnings("unchecked")
        Map<String, Object> sessionData = (Map<String, Object>) sessionCache.get(request.getImportSessionId());

        if (sessionData == null) {
            throw new IllegalArgumentException("جلسة الاستيراد منتهية أو غير صالحة. يرجى إعادة رفع الملف.");
        }

        Long sessionContractId = (Long) sessionData.get("contractId");
        if (!contractId.equals(sessionContractId)) {
            throw new IllegalArgumentException("معرف العقد غير مطابق لجلسة الاستيراد.");
        }

        @SuppressWarnings("unchecked")
        List<PricingImportPreviewItemDto> items = (List<PricingImportPreviewItemDto>) sessionData.get("items");

        ProviderContract contract = contractRepository.findById(contractId).orElseThrow();

        // Apply modifications
        Map<String, PricingImportModificationDto> modsMap = new HashMap<>();
        if (request.getModifications() != null) {
            for (PricingImportModificationDto mod : request.getModifications()) {
                modsMap.put(mod.getRowId(), mod);
            }
        }

        int inserted = 0, updated = 0, skipped = 0;

        for (PricingImportPreviewItemDto item : items) {
            if (request.isSkipZeroPriceItems() && item.isPriceZero()) {
                skipped++;
                continue;
            }

            PricingImportModificationDto mod = modsMap.get(item.getRowId());
            MedicalCategory finalCategory = null;
            EncounterType finalEncounterType = item.getEncounterType();
            ClassificationStatus finalStatus = ClassificationStatus.AUTO;
            Long approvedById = null;
            LocalDateTime approvedAt = null;

            if (mod != null) {
                finalEncounterType = mod.getEncounterType() != null ? mod.getEncounterType() : item.getEncounterType();
                if (mod.getManualCategoryId() != null) {
                    finalCategory = categoryRepository.findById(mod.getManualCategoryId()).orElse(null);
                    finalStatus = ClassificationStatus.MANUAL;
                    // Note: We don't have user ID easily from SecurityContext without querying DB, but skipping for simplicity or assuming we can fetch it.
                    approvedAt = LocalDateTime.now();

                    // Optional: Save as rule
                    if (mod.isSaveAsRule() && finalCategory != null && item.getImportedSubCategory() != null) {
                        saveAsNewRule(item.getImportedSubCategory(), item.getServiceName(), finalCategory.getCode(), finalEncounterType, contract.getProvider());
                    }
                } else {
                    if (item.getProposedCategoryId() != null) {
                        finalCategory = categoryRepository.findById(item.getProposedCategoryId()).orElse(null);
                    }
                    if (item.isRequiresReview()) {
                        finalStatus = ClassificationStatus.PENDING_REVIEW;
                    }
                }
            } else {
                if (item.getProposedCategoryId() != null) {
                    finalCategory = categoryRepository.findById(item.getProposedCategoryId()).orElse(null);
                }
                if (item.isRequiresReview()) {
                    finalStatus = ClassificationStatus.PENDING_REVIEW;
                }
            }

            // Fallback category logic
            String fallbackCategoryName = (item.getImportedMainCategory() != null ? item.getImportedMainCategory() : "") +
                    (item.getImportedMainCategory() != null && item.getImportedSubCategory() != null ? " > " : "") +
                    (item.getImportedSubCategory() != null ? item.getImportedSubCategory() : "");

            Optional<ProviderContractPricingItem> existingOpt = pricingRepository.findByContractIdAndServiceCodeActiveTrue(
                    contractId, item.getServiceCode());

            if (existingOpt.isPresent()) {
                ProviderContractPricingItem existing = existingOpt.get();
                existing.setContractPrice(item.getContractPrice());
                existing.setCurrency(item.getCurrency());
                existing.setMedicalCategory(finalCategory);
                existing.setCategoryName(fallbackCategoryName);
                existing.setUpdatedBy(currentUser);
                existing.setEncounterType(finalEncounterType);
                existing.setConfidenceLevel(item.getConfidenceLevel());
                existing.setClassificationStatus(finalStatus);
                existing.setRequiresReview(finalStatus == ClassificationStatus.PENDING_REVIEW);
                existing.setReviewReason(item.getReviewReason());
                existing.setClassificationSource(item.getClassificationSource());
                existing.setImportedMainCategory(item.getImportedMainCategory());
                existing.setImportedSubCategory(item.getImportedSubCategory());
                if (approvedAt != null) existing.setApprovedAt(approvedAt);
                pricingRepository.save(existing);
                updated++;
            } else {
                ProviderContractPricingItem newItem = ProviderContractPricingItem.builder()
                        .contract(contract)
                        .serviceName(item.getServiceName())
                        .serviceCode(item.getServiceCode())
                        .medicalCategory(finalCategory)
                        .categoryName(fallbackCategoryName)
                        .basePrice(BigDecimal.ZERO)
                        .contractPrice(item.getContractPrice())
                        .currency(item.getCurrency())
                        .unit("خدمة")
                        .active(true)
                        .createdBy(currentUser)
                        .updatedBy(currentUser)
                        .encounterType(finalEncounterType)
                        .confidenceLevel(item.getConfidenceLevel())
                        .classificationStatus(finalStatus)
                        .requiresReview(finalStatus == ClassificationStatus.PENDING_REVIEW)
                        .reviewReason(item.getReviewReason())
                        .classificationSource(item.getClassificationSource())
                        .importedMainCategory(item.getImportedMainCategory())
                        .importedSubCategory(item.getImportedSubCategory())
                        .approvedAt(approvedAt)
                        .build();
                pricingRepository.save(newItem);
                inserted++;
            }
        }

        sessionCache.remove(request.getImportSessionId());

        return ExcelImportResultDto.builder()
                .success(true)
                .message(String.format("تم اعتماد استيراد %d خدمة (إضافة: %d، تحديث: %d، تخطي: %d)", inserted + updated, inserted, updated, skipped))
                .summary(ImportSummary.builder().total(items.size()).inserted(inserted).updated(updated).skipped(skipped).build())
                .build();
    }

    private void saveAsNewRule(String specialty, String serviceName, String catCode, EncounterType encounterType, com.waad.tba.modules.provider.entity.Provider provider) {
        ServiceSpecialtyInsuranceMap rule = ServiceSpecialtyInsuranceMap.builder()
                .sourceSpecialtyNameAr(specialty)
                .keywordPatterns("[\"" + serviceName + "\"]")
                .matchField("BOTH")
                .insuranceCategoryCode(catCode)
                .defaultEncounterType(encounterType != null ? encounterType : EncounterType.INPATIENT)
                .confidenceLevel(ConfidenceLevel.HIGH)
                .priority(5) // high priority for manual overrides
                .provider(provider)
                .isActive(true)
                .build();
        mapRepository.save(rule);
    }

    /**
     * Legacy import fallback for backward compatibility
     */
    @Transactional
    public ExcelImportResultDto importFromExcel(Long contractId, MultipartFile file) {
        PricingImportPreviewDto preview = importForPreview(contractId, file);
        PricingImportConfirmRequest request = new PricingImportConfirmRequest();
        request.setImportSessionId(preview.getImportSessionId());
        return confirmImport(contractId, request);
    }

    // HELPER METHODS
    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String columnName = cell.getStringCellValue().trim().toLowerCase();
            String mappedName = COLUMN_MAPPINGS.get(columnName);
            if (mappedName != null) {
                indices.put(mappedName, i);
            }
        }
        return indices;
    }

    private String generateUniqueServiceCode(Long contractId, String serviceName, Set<String> usedCodesInSession) {
        String base = buildBaseCode(serviceName);
        String candidate = base;
        int counter = 2;
        while (isCodeTaken(contractId, candidate, usedCodesInSession)) {
            candidate = base + "-" + counter;
            counter++;
            if (counter > 9999) {
                candidate = base + "-" + (System.currentTimeMillis() % 100000);
                break;
            }
        }
        return candidate;
    }

    private String buildBaseCode(String name) {
        if (name == null || name.isBlank()) return "GEN-SVC";
        String[] words = name.trim().replaceAll("[^\\p{L}\\p{N}\\s]", " ").split("\\s+");
        StringBuilder sb = new StringBuilder("GEN-");
        int taken = 0;
        for (String w : words) {
            if (w.isBlank()) continue;
            sb.append(w.substring(0, 1).toUpperCase());
            taken++;
            if (taken >= 4) break;
        }
        if (taken == 0) sb.append("SVC");
        return sb.toString();
    }

    private boolean isCodeTaken(Long contractId, String code, Set<String> usedCodesInSession) {
        String upperCode = code.toUpperCase();
        if (usedCodesInSession.contains(upperCode)) return true;
        return pricingRepository.findByContractIdAndServiceCodeActiveTrue(contractId, code).isPresent();
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getCellValueAsString(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private BigDecimal getCellValueAsDecimal(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
                case STRING -> {
                    String value = cell.getStringCellValue().trim();
                    yield value.isEmpty() ? null : new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
