package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.ContractImportConfirmResultDto;
import com.waad.tba.modules.providercontract.dto.ContractImportPreviewResultDto;
import com.waad.tba.modules.providercontract.dto.ContractImportRowDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractCreateDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingScope;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bulk Excel import for provider contracts (new contracts, not pricing items).
 * Two-stage flow:
 *   1) preview(file)  — parses + validates every row (structural + business rules),
 *      persists nothing, caches the parsed rows under a sessionId.
 *   2) confirm(sessionId) — persists only the rows that were valid at preview time,
 *      each in its own try/catch so one bad row never blocks the rest of the batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderContractImportService {

    private static final String SHEET_NAME = "بيانات العقود";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Column order in the template (0-based)
    private static final int COL_PROVIDER_NAME = 0;
    private static final int COL_PRICING_SCOPE = 1;
    private static final int COL_EMPLOYER_NAME = 2;
    private static final int COL_CONTRACT_CODE = 3;
    private static final int COL_STATUS = 4;
    private static final int COL_PRICING_MODEL = 5;
    private static final int COL_DISCOUNT_PERCENT = 6;
    private static final int COL_DISCOUNT_TIMING = 7;
    private static final int COL_START_DATE = 8;
    private static final int COL_END_DATE = 9;
    private static final int COL_SIGNED_DATE = 10;
    private static final int COL_TOTAL_VALUE = 11;
    private static final int COL_CURRENCY = 12;
    private static final int COL_PAYMENT_TERMS = 13;
    private static final int COL_AUTO_RENEW = 14;
    private static final int COL_CONTACT_PERSON = 15;
    private static final int COL_CONTACT_PHONE = 16;
    private static final int COL_CONTACT_EMAIL = 17;
    private static final int COL_NOTES = 18;

    private final ProviderRepository providerRepository;
    private final EmployerRepository employerRepository;
    private final ProviderContractRepository contractRepository;
    private final ProviderContractService contractService;
    private final PricingImportSessionCache sessionCache;

    // ═══════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════

    public byte[] generateTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.setRightToLeft(true);

            CellStyle requiredStyle = headerStyle(workbook, true);
            CellStyle optionalStyle = headerStyle(workbook, false);
            CellStyle exampleStyle = exampleStyle(workbook);

            String[] headers = {
                    "اسم مقدم الخدمة ★", "نطاق التسعير (GLOBAL/EMPLOYER_SPECIFIC)", "اسم جهة العمل (إن كان النطاق خاص)",
                    "رمز العقد (اختياري)", "الحالة (DRAFT/ACTIVE/SUSPENDED/EXPIRED/TERMINATED)",
                    "نموذج التسعير (FIXED/DISCOUNT/TIERED/NEGOTIATED)", "نسبة الخصم %",
                    "آلية الخصم (قبل المرفوض/بعد المرفوض)", "تاريخ البدء ★ (yyyy-MM-dd)", "تاريخ الانتهاء (yyyy-MM-dd)",
                    "تاريخ التوقيع (yyyy-MM-dd)", "القيمة الإجمالية", "العملة", "شروط الدفع",
                    "تجديد تلقائي (نعم/لا)", "الشخص المسؤول", "هاتف التواصل", "بريد التواصل", "ملاحظات"
            };
            boolean[] required = new boolean[headers.length];
            required[COL_PROVIDER_NAME] = true;
            required[COL_START_DATE] = true;

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(required[i] ? requiredStyle : optionalStyle);
                sheet.setColumnWidth(i, 26 * 256);
            }

            Row example = sheet.createRow(1);
            String[] exampleValues = {
                    "مستشفى النور", "GLOBAL", "", "", "DRAFT", "DISCOUNT", "10", "بعد المرفوض",
                    LocalDate.now().format(DATE_FMT), "", "", "", "LYD", "آجل 30 يوم", "لا", "أحمد علي",
                    "0912345678", "contact@example.com", "مثال - احذف هذا الصف"
            };
            for (int i = 0; i < exampleValues.length; i++) {
                Cell cell = example.createCell(i);
                cell.setCellValue(exampleValues[i]);
                cell.setCellStyle(exampleStyle);
            }

            createInstructionsSheet(workbook);
            createReferenceValuesSheet(workbook);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createInstructionsSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("التعليمات");
        sheet.setRightToLeft(true);
        int r = 0;
        String[] lines = {
                "تعليمات استيراد عقود مقدمي الخدمة:",
                "1. الحقول الإلزامية: اسم مقدم الخدمة، تاريخ البدء.",
                "2. إذا كان نطاق التسعير = EMPLOYER_SPECIFIC، يجب تعبئة اسم جهة العمل، ويجب أن تكون موجودة مسبقاً في النظام.",
                "3. اسم مقدم الخدمة يجب أن يطابق مقدم خدمة موجود بالفعل في النظام (لا يتم إنشاء مقدمي خدمة جدد من هذا الاستيراد).",
                "4. رمز العقد اختياري، وإن تُرك فارغاً يُولَّد تلقائياً. إن تم إدخاله يجب ألا يتكرر مع عقد آخر.",
                "5. لا يمكن إنشاء أكثر من عقد بحالة ACTIVE لنفس مقدم الخدمة ونفس نطاق التسعير.",
                "6. راجع ورقة 'القيم المرجعية' للقيم المقبولة لأعمدة الحالة/النطاق/نموذج التسعير.",
                "7. يتم التحقق من الملف على مرحلتين: معاينة (تحقق فقط بدون حفظ) ثم تأكيد الاستيراد (حفظ الصفوف الصحيحة فقط).",
                "8. الصفوف التي تحتوي أخطاء لن تُحفظ؛ يمكنك تنزيل تقرير الأخطاء وتصحيح الملف وإعادة رفعه."
        };
        for (String line : lines) {
            sheet.createRow(r++).createCell(0).setCellValue(line);
        }
        sheet.setColumnWidth(0, 100 * 256);
    }

    private void createReferenceValuesSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("القيم المرجعية");
        sheet.setRightToLeft(true);

        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("الحالة (Status)");
        h.createCell(1).setCellValue("نموذج التسعير (Pricing Model)");
        h.createCell(2).setCellValue("نطاق التسعير (Pricing Scope)");

        String[] statuses = {"DRAFT", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED"};
        String[] models = {"FIXED", "DISCOUNT", "TIERED", "NEGOTIATED"};
        String[] scopes = {"GLOBAL", "EMPLOYER_SPECIFIC"};
        int max = Math.max(statuses.length, Math.max(models.length, scopes.length));
        for (int i = 0; i < max; i++) {
            Row row = sheet.createRow(i + 1);
            if (i < statuses.length) row.createCell(0).setCellValue(statuses[i]);
            if (i < models.length) row.createCell(1).setCellValue(models[i]);
            if (i < scopes.length) row.createCell(2).setCellValue(scopes[i]);
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 22 * 256);
    }

    private CellStyle headerStyle(Workbook workbook, boolean required) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        if (required) font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor((required ? IndexedColors.DARK_BLUE : IndexedColors.GREY_25_PERCENT).getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle exampleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PREVIEW (structural + business validation, no persistence)
    // ═══════════════════════════════════════════════════════════════════════

    public ContractImportPreviewResultDto preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }

        List<ContractImportRowDto> rows = new ArrayList<>();
        Set<String> codesSeenInFile = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                if (workbook.getSheet("Data") != null && workbook.getSheet("Metadata") != null) {
                    throw new BusinessRuleException(
                            "هذا ملف استيراد مقدمي الخدمة، وليس قالب استيراد العقود. "
                                    + "ارفعه من شاشة مقدمي الخدمة؛ وسيُنشئ النظام مقدم الخدمة وعقده معاً.");
                }
                throw new BusinessRuleException(
                        "قالب العقود غير صحيح: ورقة «" + SHEET_NAME
                                + "» غير موجودة. نزّل قالب العقود من هذه النافذة ثم أعد المحاولة.");
            }

            int lastRow = sheet.getLastRowNum();
            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (isEmptyRow(row)) continue;

                ContractImportRowDto parsed = parseAndValidateRow(row, rowNum + 1, codesSeenInFile);
                rows.add(parsed);
            }
        } catch (IOException e) {
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        }

        long validCount = rows.stream().filter(ContractImportRowDto::isValid).count();
        String sessionId = sessionCache.put(rows);

        return ContractImportPreviewResultDto.builder()
                .sessionId(sessionId)
                .totalRows(rows.size())
                .validCount((int) validCount)
                .invalidCount(rows.size() - (int) validCount)
                .rows(rows)
                .build();
    }

    private ContractImportRowDto parseAndValidateRow(Row row, int rowNumber, Set<String> codesSeenInFile) {
        ContractImportRowDto.ContractImportRowDtoBuilder b = ContractImportRowDto.builder().rowNumber(rowNumber);
        List<String> errors = new ArrayList<>();

        String providerName = strCell(row, COL_PROVIDER_NAME);
        String pricingScopeRaw = strCell(row, COL_PRICING_SCOPE);
        String employerName = strCell(row, COL_EMPLOYER_NAME);
        String contractCode = strCell(row, COL_CONTRACT_CODE);
        String statusRaw = strCell(row, COL_STATUS);
        String pricingModelRaw = strCell(row, COL_PRICING_MODEL);
        String discountTimingRaw = strCell(row, COL_DISCOUNT_TIMING);

        b.providerNameRaw(providerName).employerNameRaw(employerName).contractCode(contractCode)
                .statusRaw(statusRaw).pricingScopeRaw(pricingScopeRaw).pricingModelRaw(pricingModelRaw)
                .discountTimingRaw(discountTimingRaw);

        // --- Provider (required, must already exist) ---
        Provider provider = null;
        if (providerName == null || providerName.isBlank()) {
            errors.add("اسم مقدم الخدمة مطلوب");
        } else {
            provider = providerRepository.findByNameIgnoreCase(providerName.trim()).orElse(null);
            if (provider == null) {
                errors.add("مقدم الخدمة غير موجود في النظام: " + providerName);
            } else {
                b.providerId(provider.getId()).providerName(provider.getName());
            }
        }

        // --- Pricing scope ---
        PricingScope pricingScope = PricingScope.GLOBAL;
        if (pricingScopeRaw != null && !pricingScopeRaw.isBlank()) {
            try {
                pricingScope = PricingScope.valueOf(pricingScopeRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                errors.add("نطاق التسعير غير صالح: " + pricingScopeRaw + " (القيم المقبولة: GLOBAL, EMPLOYER_SPECIFIC)");
            }
        }
        b.pricingScope(pricingScope);

        // --- Employer (required only when EMPLOYER_SPECIFIC) ---
        if (pricingScope == PricingScope.EMPLOYER_SPECIFIC) {
            if (employerName == null || employerName.isBlank()) {
                errors.add("اسم جهة العمل مطلوب عندما يكون نطاق التسعير EMPLOYER_SPECIFIC");
            } else {
                Employer employer = employerRepository.findByNameIgnoreCase(employerName.trim()).orElse(null);
                if (employer == null) {
                    errors.add("جهة العمل غير موجودة في النظام: " + employerName);
                } else {
                    b.employerId(employer.getId());
                }
            }
        }

        // --- Contract code (optional, must be unique) ---
        if (contractCode != null && !contractCode.isBlank()) {
            String trimmedCode = contractCode.trim();
            if (contractRepository.existsByContractCode(trimmedCode)) {
                errors.add("رمز العقد مستخدم مسبقاً: " + trimmedCode);
            } else if (!codesSeenInFile.add(trimmedCode)) {
                errors.add("رمز العقد مكرر داخل نفس الملف: " + trimmedCode);
            }
        }

        // --- Status ---
        ContractStatus status = ContractStatus.DRAFT;
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                status = ContractStatus.valueOf(statusRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                errors.add("الحالة غير صالحة: " + statusRaw);
            }
        }
        b.status(status);

        // Note: the "single ACTIVE contract per provider/scope" rule is enforced by
        // ProviderContractService.create() itself at confirm time — a row that violates
        // it is not rejected here (structural/reference validation only) but will
        // surface as a per-row failure in the confirm-stage results.

        // --- Pricing model ---
        PricingModel pricingModel = PricingModel.DISCOUNT;
        if (pricingModelRaw != null && !pricingModelRaw.isBlank()) {
            try {
                pricingModel = PricingModel.valueOf(pricingModelRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                errors.add("نموذج التسعير غير صالح: " + pricingModelRaw);
            }
        }
        b.pricingModel(pricingModel);

        // --- Discount percent ---
        BigDecimal discountPercent = percentCell(row, COL_DISCOUNT_PERCENT);
        if (discountPercent != null && (discountPercent.compareTo(BigDecimal.ZERO) < 0 || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0)) {
            errors.add("نسبة الخصم يجب أن تكون بين 0 و100");
        }
        b.discountPercent(discountPercent != null ? discountPercent : BigDecimal.ZERO);

        // --- Discount timing ---
        boolean discountBeforeRejection = discountTimingRaw != null && discountTimingRaw.trim().contains("قبل");
        b.discountBeforeRejection(discountBeforeRejection);

        // --- Dates ---
        LocalDate startDate = dateCell(row, COL_START_DATE, "تاريخ البدء", errors);
        LocalDate endDate = dateCell(row, COL_END_DATE, "تاريخ الانتهاء", errors);
        LocalDate signedDate = dateCell(row, COL_SIGNED_DATE, "تاريخ التوقيع", errors);
        if (startDate == null) {
            errors.add("تاريخ البدء مطلوب");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            errors.add("تاريخ البدء يجب أن يكون قبل تاريخ الانتهاء");
        }
        b.startDate(startDate).endDate(endDate).signedDate(signedDate);

        // --- Remaining optional fields ---
        b.totalValue(numCell(row, COL_TOTAL_VALUE));
        String currency = strCell(row, COL_CURRENCY);
        b.currency(currency != null && !currency.isBlank() ? currency.trim() : "LYD");
        b.paymentTerms(strCell(row, COL_PAYMENT_TERMS));
        String autoRenewRaw = strCell(row, COL_AUTO_RENEW);
        b.autoRenew(autoRenewRaw != null && (autoRenewRaw.trim().equals("نعم") || autoRenewRaw.trim().equalsIgnoreCase("true")));
        b.contactPerson(strCell(row, COL_CONTACT_PERSON));
        b.contactPhone(strCell(row, COL_CONTACT_PHONE));
        String email = strCell(row, COL_CONTACT_EMAIL);
        if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            errors.add("صيغة البريد الإلكتروني غير صحيحة: " + email);
        }
        b.contactEmail(email);
        b.notes(strCell(row, COL_NOTES));

        ContractImportRowDto dto = b.build();
        dto.setErrors(errors);
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIRM (persist only the rows that were valid at preview time)
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public ContractImportConfirmResultDto confirm(String sessionId) {
        Object cached = sessionCache.get(sessionId);
        if (!(cached instanceof List)) {
            throw new BusinessRuleException("جلسة الاستيراد غير موجودة أو انتهت صلاحيتها. يرجى إعادة رفع الملف.");
        }
        List<ContractImportRowDto> rows = (List<ContractImportRowDto>) cached;

        List<ContractImportConfirmResultDto.RowResult> results = new ArrayList<>();
        int successCount = 0;
        int skipped = 0;

        for (ContractImportRowDto row : rows) {
            if (!row.isValid()) {
                skipped++;
                results.add(ContractImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .providerName(row.getProviderNameRaw())
                        .success(false)
                        .message("تم تخطي الصف لوجود أخطاء تحقق: " + String.join("; ", row.getErrors()))
                        .build());
                continue;
            }

            try {
                ProviderContractCreateDto createDto = ProviderContractCreateDto.builder()
                        .providerId(row.getProviderId())
                        .pricingScope(row.getPricingScope())
                        .employerId(row.getEmployerId())
                        .contractCode(row.getContractCode())
                        .status(row.getStatus())
                        .pricingModel(row.getPricingModel())
                        .discountPercent(row.getDiscountPercent())
                        .discountBeforeRejection(row.getDiscountBeforeRejection())
                        .startDate(row.getStartDate())
                        .endDate(row.getEndDate())
                        .signedDate(row.getSignedDate())
                        .totalValue(row.getTotalValue())
                        .currency(row.getCurrency())
                        .paymentTerms(row.getPaymentTerms())
                        .autoRenew(row.getAutoRenew())
                        .contactPerson(row.getContactPerson())
                        .contactPhone(row.getContactPhone())
                        .contactEmail(row.getContactEmail())
                        .notes(row.getNotes())
                        .build();

                var created = contractService.create(createDto);
                successCount++;
                results.add(ContractImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .providerName(row.getProviderName())
                        .contractCode(created.getContractCode())
                        .success(true)
                        .message("تم إنشاء العقد بنجاح")
                        .build());
            } catch (BusinessRuleException ex) {
                results.add(ContractImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .providerName(row.getProviderName())
                        .success(false)
                        .message(ex.getMessage())
                        .build());
            } catch (RuntimeException ex) {
                log.error("Unexpected error importing contract row {}", row.getRowNumber(), ex);
                results.add(ContractImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .providerName(row.getProviderName())
                        .success(false)
                        .message("تعذر إنشاء العقد. يرجى المحاولة لاحقاً.")
                        .build());
            }
        }

        sessionCache.remove(sessionId);

        return ContractImportConfirmResultDto.builder()
                .totalRows(rows.size())
                .skippedInvalidCount(skipped)
                .successCount(successCount)
                .failedCount(rows.size() - successCount)
                .results(results)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ERROR REPORT (only invalid rows, with their error messages, downloadable)
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public byte[] generateErrorReport(String sessionId) throws IOException {
        Object cached = sessionCache.get(sessionId);
        if (!(cached instanceof List)) {
            throw new BusinessRuleException("جلسة الاستيراد غير موجودة أو انتهت صلاحيتها.");
        }
        List<ContractImportRowDto> rows = (List<ContractImportRowDto>) cached;

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("أخطاء الاستيراد");
            sheet.setRightToLeft(true);

            CellStyle headerStyle = headerStyle(workbook, true);
            Row header = sheet.createRow(0);
            String[] headers = {"رقم الصف", "مقدم الخدمة", "رمز العقد", "الأخطاء"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (ContractImportRowDto row : rows) {
                if (row.isValid()) continue;
                Row dataRow = sheet.createRow(r++);
                dataRow.createCell(0).setCellValue(row.getRowNumber());
                dataRow.createCell(1).setCellValue(row.getProviderNameRaw() != null ? row.getProviderNameRaw() : "");
                dataRow.createCell(2).setCellValue(row.getContractCode() != null ? row.getContractCode() : "");
                dataRow.createCell(3).setCellValue(String.join(" | ", row.getErrors()));
            }

            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 30 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            sheet.setColumnWidth(3, 90 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CELL HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            String value = cellToString(cell);
            if (value != null && !value.trim().isEmpty()) return false;
        }
        return true;
    }

    private String strCell(Row row, int col) {
        if (row == null) return null;
        String value = cellToString(row.getCell(col));
        return value != null ? value.trim() : null;
    }

    private BigDecimal numCell(Row row, int col) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String value = cellToString(cell);
            if (value == null || value.isBlank()) return null;
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal percentCell(Row row, int col) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                BigDecimal value = BigDecimal.valueOf(cell.getNumericCellValue());
                String format = cell.getCellStyle() == null ? null : cell.getCellStyle().getDataFormatString();
                return format != null && format.contains("%") ? value.multiply(BigDecimal.valueOf(100)) : value;
            }
            String value = cellToString(cell);
            if (value == null || value.isBlank()) return null;
            String normalized = value.trim().replace(",", "");
            if (normalized.endsWith("%")) normalized = normalized.substring(0, normalized.length() - 1).trim();
            return new BigDecimal(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate dateCell(Row row, int col, String label, List<String> errors) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            String value = cellToString(cell);
            if (value == null || value.isBlank()) return null;
            return LocalDate.parse(value.trim(), DATE_FMT);
        } catch (DateTimeParseException | IllegalStateException e) {
            errors.add(label + " غير صالح (الصيغة المطلوبة: yyyy-MM-dd): " + cellToString(cell));
            return null;
        }
    }

    private String cellToString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FMT);
                }
                double numValue = cell.getNumericCellValue();
                return numValue == Math.floor(numValue) ? String.valueOf((long) numValue) : String.valueOf(numValue);
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
}
