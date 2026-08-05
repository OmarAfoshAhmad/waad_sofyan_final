package com.waad.tba.modules.employer.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.dto.EmployerImportConfirmResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportPreviewResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportRowDto;
import com.waad.tba.modules.employer.dto.EmployerImportRowDto.Action;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.providercontract.service.PricingImportSessionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bulk Excel import for employers (جهات العمل). Every row also ends up with
 * exactly one ACTIVE insurance policy (وثيقة تأمين) — see
 * {@link EmployerImportRowProcessor} for how that guarantee is atomic per row
 * and never duplicates an existing active policy.
 *
 * Column headers are matched by meaning, not position: any order works, and
 * common Arabic/English variants for each field are recognized (see
 * {@link #FIELD_ALIASES}). A row whose code or name matches an existing
 * employer is treated as an update candidate — and even then, only fields
 * that actually differ are written; a blank cell never erases existing data.
 *
 * Two-stage flow, matching {@code ProviderContractImportService}:
 *   1) preview(file)  — parses + validates every row, persists nothing,
 *      determines what would happen (create/update/no-change) for each,
 *      caches the parsed rows under a sessionId.
 *   2) confirm(sessionId) — applies only the rows that were valid at preview
 *      time, each in its own try/catch so one bad row never blocks the batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployerImportService {

    private static final String SHEET_NAME = "بيانات جهات العمل";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^\\+?[\\d\\s\\-()]{7,25}$");

    private enum Field { CODE, NAME, PHONE, EMAIL, ADDRESS, ANNUAL_LIMIT, COVERAGE_PERCENT }

    /**
     * Header text → field, matched by substring after normalization (trim,
     * lowercase, strip "★"/parenthetical hints). Order in the sheet never
     * matters — only which alias each column header contains.
     */
    private static final Map<Field, String[]> FIELD_ALIASES = Map.of(
            Field.CODE, new String[]{"رمز", "code"},
            Field.NAME, new String[]{"اسم", "جهة العمل", "name"},
            Field.PHONE, new String[]{"هاتف", "phone", "tel"},
            Field.EMAIL, new String[]{"بريد", "email", "mail"},
            Field.ADDRESS, new String[]{"عنوان", "address"},
            Field.ANNUAL_LIMIT, new String[]{"حد سنوي", "الحد السنوي", "annual limit", "annuallimit"},
            Field.COVERAGE_PERCENT, new String[]{"نسبة التغطية", "نسبه التغطيه", "coverage percent", "coveragepercent"}
    );

    private final EmployerRepository employerRepository;
    private final EmployerImportRowProcessor rowProcessor;
    private final PricingImportSessionCache sessionCache;

    @Value("${waad.employer-import.default-annual-limit:100000}")
    private BigDecimal defaultAnnualLimit;

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
                    "اسم جهة العمل ★",
                    "رمز الجهة (اختياري - يُولَّد تلقائياً إن تُرك فارغاً)",
                    "رقم الهاتف (اختياري)",
                    "البريد الإلكتروني (اختياري)",
                    "العنوان (اختياري)",
                    "الحد السنوي لوثيقة التأمين (اختياري - قيمة افتراضية إن تُرك فارغاً)",
                    "نسبة التغطية % (اختياري - 100% إن تُرك فارغاً)"
            };
            boolean[] required = {true, false, false, false, false, false, false};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(required[i] ? requiredStyle : optionalStyle);
                sheet.setColumnWidth(i, 30 * 256);
            }

            Row example = sheet.createRow(1);
            String[] exampleValues = {
                    "شركة المثال للتجارة", "", "0912345678", "contact@example.com",
                    "طرابلس، ليبيا", "مثال - احذف هذا الصف", "100"
            };
            for (int i = 0; i < exampleValues.length; i++) {
                Cell cell = example.createCell(i);
                cell.setCellValue(exampleValues[i]);
                cell.setCellStyle(exampleStyle);
            }

            createInstructionsSheet(workbook);

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
                "تعليمات استيراد جهات العمل:",
                "1. الحقل الإلزامي الوحيد: اسم جهة العمل. ترتيب الأعمدة غير مهم — يتم التعرف عليها تلقائياً من عنوان كل عمود.",
                "2. رمز الجهة اختياري، وإن تُرك فارغاً يُولَّد تلقائياً بنفس نمط الإنشاء اليدوي (EMP-01, EMP-02, ...).",
                "3. إن كان الرمز أو الاسم يطابق جهة عمل موجودة مسبقاً، تُعامل كتحديث لا كخطأ — ويُحدَّث فقط الحقل الذي تغيّر فعلياً؛ "
                        + "أي خلية فارغة في الملف لا تمحو بيانات موجودة.",
                "4. كل جهة عمل (جديدة أو محدَّثة) بلا وثيقة تأمين نشطة تحصل تلقائياً على وثيقة جديدة كمسودة (غير مفعّلة)، "
                        + "بنسبة التغطية المحدَّدة في العمود المخصص لذلك (أو 100% إن تُرك فارغاً) — دون إنشاء أي قواعد تغطية تفصيلية. "
                        + "يجب إضافة قواعد التغطية وتفعيل الوثيقة يدوياً بعد الاستيراد. جهة تملك وثيقة نشطة بالفعل لا تُمس.",
                "5. الحد السنوي لوثيقة التأمين اختياري؛ إن تُرك فارغاً يُستخدم حد افتراضي مضبوط على مستوى النظام.",
                "6. نسبة التغطية اختيارية (0-100)؛ إن تُركت فارغة تُستخدم نسبة 100% كقيمة افتراضية للوثيقة.",
                "7. المعاينة (قبل التأكيد) تعرض لكل صف ماذا سيحدث بالضبط: إنشاء / تحديث (وأي حقول ستتغيّر) / بلا تغيير.",
                "8. الصفوف التي تحتوي أخطاء لن تُحفظ؛ يمكنك تنزيل تقرير الأخطاء وتصحيح الملف وإعادة رفعه."
        };
        for (String line : lines) {
            sheet.createRow(r++).createCell(0).setCellValue(line);
        }
        sheet.setColumnWidth(0, 100 * 256);
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
    // COLUMN DETECTION (order-independent, alias-based)
    // ═══════════════════════════════════════════════════════════════════════

    /** Maps each recognized field to the column index whose header matched one of its aliases. */
    private Map<Field, Integer> detectColumns(Row headerRow) {
        Map<Field, Integer> columns = new HashMap<>();
        if (headerRow == null) {
            return columns;
        }
        for (int col = headerRow.getFirstCellNum(); col < headerRow.getLastCellNum(); col++) {
            String raw = cellToString(headerRow.getCell(col));
            if (raw == null || raw.isBlank()) continue;
            String normalized = normalizeHeader(raw);

            for (Map.Entry<Field, String[]> entry : FIELD_ALIASES.entrySet()) {
                if (columns.containsKey(entry.getKey())) continue; // first match wins
                for (String alias : entry.getValue()) {
                    if (normalized.contains(alias)) {
                        columns.put(entry.getKey(), col);
                        break;
                    }
                }
            }
        }
        return columns;
    }

    private static String normalizeHeader(String raw) {
        return raw.replace("★", "")
                .replaceAll("\\(.*?\\)", "") // drop parenthetical hints like "(اختياري)"
                .trim()
                .toLowerCase();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PREVIEW (structural + reference validation, no persistence)
    // ═══════════════════════════════════════════════════════════════════════

    public EmployerImportPreviewResultDto preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }

        List<EmployerImportRowDto> rows = new ArrayList<>();
        Set<String> codesSeenInFile = new HashSet<>();
        Set<String> namesSeenInFile = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessRuleException("لم يتم العثور على ورقة البيانات");
            }

            Map<Field, Integer> columns = detectColumns(sheet.getRow(0));
            if (!columns.containsKey(Field.NAME)) {
                throw new BusinessRuleException(
                        "لم يتم التعرف على عمود اسم جهة العمل. تأكد أن عنوان أحد الأعمدة يحتوي كلمة \"اسم\" أو \"name\".");
            }

            int lastRow = sheet.getLastRowNum();
            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (isEmptyRow(row)) continue;

                EmployerImportRowDto parsed = parseAndValidateRow(row, rowNum + 1, columns, codesSeenInFile, namesSeenInFile);
                rows.add(parsed);
            }
        } catch (IOException e) {
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        }

        long validCount = rows.stream().filter(EmployerImportRowDto::isValid).count();
        String sessionId = sessionCache.put(rows);

        return EmployerImportPreviewResultDto.builder()
                .sessionId(sessionId)
                .totalRows(rows.size())
                .validCount((int) validCount)
                .invalidCount(rows.size() - (int) validCount)
                .rows(rows)
                .build();
    }

    private EmployerImportRowDto parseAndValidateRow(Row row, int rowNumber, Map<Field, Integer> columns,
                                                      Set<String> codesSeenInFile, Set<String> namesSeenInFile) {
        EmployerImportRowDto.EmployerImportRowDtoBuilder b = EmployerImportRowDto.builder().rowNumber(rowNumber);
        List<String> errors = new ArrayList<>();

        String codeRaw = strCell(row, columns.get(Field.CODE));
        String nameRaw = strCell(row, columns.get(Field.NAME));
        String phoneRaw = strCell(row, columns.get(Field.PHONE));
        String emailRaw = strCell(row, columns.get(Field.EMAIL));
        String addressRaw = strCell(row, columns.get(Field.ADDRESS));
        String annualLimitRaw = strCell(row, columns.get(Field.ANNUAL_LIMIT));
        String coveragePercentRaw = strCell(row, columns.get(Field.COVERAGE_PERCENT));

        b.codeRaw(codeRaw).nameRaw(nameRaw).phoneRaw(phoneRaw).emailRaw(emailRaw)
                .addressRaw(addressRaw).annualLimitRaw(annualLimitRaw).coveragePercentRaw(coveragePercentRaw);

        if (nameRaw == null || nameRaw.isBlank()) {
            errors.add("اسم جهة العمل مطلوب");
            b.errors(errors);
            return b.build();
        }
        String name = nameRaw.trim();
        String code = (codeRaw != null && !codeRaw.isBlank()) ? codeRaw.trim().toUpperCase() : null;
        String phone = validatePhone(phoneRaw, errors);
        String email = validateEmail(emailRaw, errors);
        String address = (addressRaw != null && !addressRaw.isBlank()) ? addressRaw.trim() : null;
        BigDecimal annualLimit = parseAnnualLimit(annualLimitRaw, errors);
        Integer coveragePercent = parseCoveragePercent(coveragePercentRaw, errors);
        b.coveragePercent(coveragePercent);

        // --- Match against an existing employer: code first (more specific), then name ---
        Employer existing = null;
        if (code != null) {
            existing = employerRepository.findByCode(code).orElse(null);
        }
        if (existing == null) {
            existing = employerRepository.findByNameIgnoreCase(name).orElse(null);
        }

        if (existing == null) {
            // --- CREATE path: guard against duplicates within the file itself ---
            if (!namesSeenInFile.add(name.toLowerCase())) {
                errors.add("اسم جهة العمل مكرر داخل نفس الملف: " + name);
            }
            if (code != null && !codesSeenInFile.add(code)) {
                errors.add("رمز الجهة مكرر داخل نفس الملف: " + code);
            }
            b.code(code).name(name).phone(phone).email(email).address(address).annualLimit(annualLimit)
                    .action(Action.CREATE);
        } else {
            // --- UPDATE/NO_CHANGE path: only fields that actually differ count as a change ---
            List<String> changed = new ArrayList<>();
            if (code != null && !code.equalsIgnoreCase(existing.getCode())) {
                // A different code was supplied for an employer matched by name — that's
                // effectively a rename of the code, which risks colliding with another
                // employer; reject explicitly rather than silently reassigning it.
                if (employerRepository.existsByCodeIgnoreCaseAndIdNot(code, existing.getId())) {
                    errors.add("رمز الجهة مستخدم بالفعل لجهة عمل أخرى: " + code);
                } else {
                    changed.add("الرمز");
                }
            }
            if (!name.equalsIgnoreCase(existing.getName())) changed.add("الاسم");
            if (differs(phone, existing.getPhone())) changed.add("الهاتف");
            if (differs(email, existing.getEmail())) changed.add("البريد الإلكتروني");
            if (differs(address, existing.getAddress())) changed.add("العنوان");
            // A matched employer that's currently archived is always reactivated by
            // the row processor regardless of whether any visible field also
            // changed (see EmployerImportRowProcessor) — reflect that here so the
            // preview never claims "no change" for a row that will in fact
            // un-archive the employer.
            if (!Boolean.TRUE.equals(existing.getActive())) changed.add("الحالة (سيُعاد تفعيلها)");

            b.code(code).name(name).phone(phone).email(email).address(address).annualLimit(annualLimit)
                    .existingEmployerId(existing.getId())
                    .changedFields(changed)
                    .action(changed.isEmpty() ? Action.NO_CHANGE : Action.UPDATE);
        }

        b.errors(errors);
        return b.build();
    }

    private static boolean differs(String rowValue, String currentValue) {
        // A blank row value never counts as a "change" — see EmployerImportRowProcessor.merge().
        if (rowValue == null || rowValue.isBlank()) return false;
        return !rowValue.equals(currentValue);
    }

    private String validatePhone(String phoneRaw, List<String> errors) {
        if (phoneRaw == null || phoneRaw.isBlank()) return null;
        if (!PHONE_PATTERN.matcher(phoneRaw.trim()).matches()) {
            errors.add("صيغة رقم الهاتف غير صحيحة: " + phoneRaw);
            return null;
        }
        return phoneRaw.trim();
    }

    private String validateEmail(String emailRaw, List<String> errors) {
        if (emailRaw == null || emailRaw.isBlank()) return null;
        if (!EMAIL_PATTERN.matcher(emailRaw.trim()).matches()) {
            errors.add("صيغة البريد الإلكتروني غير صحيحة: " + emailRaw);
            return null;
        }
        return emailRaw.trim();
    }

    private BigDecimal parseAnnualLimit(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal value = new BigDecimal(raw.trim().replace(",", ""));
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("الحد السنوي لوثيقة التأمين يجب ألا يكون سالباً");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add("الحد السنوي لوثيقة التأمين غير صالح: " + raw);
            return null;
        }
    }

    private Integer parseCoveragePercent(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal value = new BigDecimal(raw.trim().replace("%", "").trim());
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                errors.add("نسبة التغطية يجب أن تكون بين 0 و 100");
                return null;
            }
            return value.intValue();
        } catch (NumberFormatException e) {
            errors.add("نسبة التغطية غير صالحة: " + raw);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIRM (apply only the rows that were valid at preview time)
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public EmployerImportConfirmResultDto confirm(String sessionId) {
        Object cached = sessionCache.get(sessionId);
        if (!(cached instanceof List)) {
            throw new BusinessRuleException("جلسة الاستيراد غير موجودة أو انتهت صلاحيتها. يرجى إعادة رفع الملف.");
        }
        List<EmployerImportRowDto> rows = (List<EmployerImportRowDto>) cached;

        List<EmployerImportConfirmResultDto.RowResult> results = new ArrayList<>();
        int successCount = 0;
        int skipped = 0;

        for (EmployerImportRowDto row : rows) {
            if (!row.isValid()) {
                skipped++;
                results.add(EmployerImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .employerName(row.getNameRaw())
                        .success(false)
                        .message("تم تخطي الصف لوجود أخطاء تحقق: " + String.join("; ", row.getErrors()))
                        .build());
                continue;
            }

            try {
                EmployerImportRowProcessor.Created created =
                        rowProcessor.ensureEmployerAndPolicy(row, defaultAnnualLimit);
                successCount++;
                results.add(EmployerImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .employerName(created.employer().getName())
                        .employerCode(created.employer().getCode())
                        .policyCode(created.policy() != null ? created.policy().getPolicyCode() : null)
                        .action(created.action().name())
                        .success(true)
                        .message(successMessage(created))
                        .build());
            } catch (BusinessRuleException | IllegalStateException ex) {
                results.add(EmployerImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .employerName(row.getName())
                        .success(false)
                        .message(cleanMessage(ex.getMessage()))
                        .build());
            } catch (RuntimeException ex) {
                log.error("Unexpected error importing employer row {}", row.getRowNumber(), ex);
                results.add(EmployerImportConfirmResultDto.RowResult.builder()
                        .rowNumber(row.getRowNumber())
                        .employerName(row.getName())
                        .success(false)
                        .message("تعذر معالجة جهة العمل. يرجى المحاولة لاحقاً.")
                        .build());
            }
        }

        sessionCache.remove(sessionId);

        return EmployerImportConfirmResultDto.builder()
                .totalRows(rows.size())
                .skippedInvalidCount(skipped)
                .successCount(successCount)
                .failedCount(rows.size() - successCount)
                .results(results)
                .build();
    }

    private static String successMessage(EmployerImportRowProcessor.Created created) {
        String employerPart = switch (created.action()) {
            case CREATE -> "تم إنشاء جهة العمل";
            case UPDATE -> "تم تحديث بيانات جهة العمل";
            case NO_CHANGE -> "لا يوجد تغيير في بيانات جهة العمل";
        };
        String policyPart = created.policyAlreadyExisted()
                ? "لديها وثيقة تأمين بالفعل"
                : "وتم إنشاء وثيقة تأمين جديدة كمسودة — أضف قواعد التغطية وفعِّلها يدوياً";
        return employerPart + " — " + policyPart;
    }

    /** Strips the "CODE_DUPLICATE:"/"NAME_DUPLICATE:" prefix EmployerService throws with. */
    private static String cleanMessage(String message) {
        if (message == null) return "حدث خطأ غير متوقع";
        int colon = message.indexOf(':');
        return colon > 0 && colon < 20 ? message.substring(colon + 1) : message;
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
        List<EmployerImportRowDto> rows = (List<EmployerImportRowDto>) cached;

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("أخطاء الاستيراد");
            sheet.setRightToLeft(true);

            CellStyle headerStyle = headerStyle(workbook, true);
            Row header = sheet.createRow(0);
            String[] headers = {"رقم الصف", "اسم جهة العمل", "الرمز", "الأخطاء"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (EmployerImportRowDto row : rows) {
                if (row.isValid()) continue;
                Row dataRow = sheet.createRow(r++);
                dataRow.createCell(0).setCellValue(row.getRowNumber());
                dataRow.createCell(1).setCellValue(row.getNameRaw() != null ? row.getNameRaw() : "");
                dataRow.createCell(2).setCellValue(row.getCodeRaw() != null ? row.getCodeRaw() : "");
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
            String value = cellToString(row.getCell(i));
            if (value != null && !value.trim().isEmpty()) return false;
        }
        return true;
    }

    private String strCell(Row row, Integer col) {
        if (row == null || col == null) return null;
        String value = cellToString(row.getCell(col));
        return value != null ? value.trim() : null;
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
