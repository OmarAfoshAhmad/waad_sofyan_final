package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.ErrorType;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportSummary;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Generates and imports Excel coverage-rule templates for Benefit Policies.
 *
 * Template Sheet 1 — "قواعد التغطية" (data entry):
 * رمز التصنيف | اسم التصنيف | التصنيف الأب | نسبة التغطية % | سقف المبلغ (د.ل)
 * |
 * سقف المرات | فترة الانتظار (أيام) | موافقة مسبقة (نعم/لا) | ملاحظات
 *
 * Template Sheet 2 — "التصنيفات المرجعية" (read-only reference):
 * رمز التصنيف | الاسم بالعربية | الاسم بالإنجليزية | التصنيف الأب
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitPolicyRuleExcelService {

    private static final String TEMPLATE_SHEET = "قواعد_التغطية";
    private static final String REFERENCE_SHEET = "التصنيفات_المرجعية";
    private static final String MARKER_CELL_VALUE = "WAAD-RULES-TEMPLATE-v2026-07";

    // Column indices for import sheet (0-based)
    private static final int COL_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_PARENT = 2;
    private static final int COL_COVERAGE = 3;
    private static final int COL_AMOUNT = 4;
    private static final int COL_TIMES = 5;
    private static final int COL_WAITING = 6;
    private static final int COL_PREAPPROVAL = 7;
    private static final int COL_NOTES = 8;

    private static final int HEADER_ROW = 0;
    private static final int MARKER_ROW = 1; // hidden row with template marker
    private static final int DATA_START_ROW = 2;

    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final MedicalCategoryRepository categoryRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generates an Excel import template for the given policy.
     * Sheet1: data-entry rows pre-filled with existing rules (editable).
     * Sheet2: unified categories reference list (locked, for lookup).
     */
    @Transactional(readOnly = true)
    public byte[] generateTemplate(Long policyId) throws IOException {
        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessRuleException("الوثيقة غير موجودة: " + policyId));

        List<MedicalCategory> allCategories = categoryRepository.findByActiveTrue();
        allCategories.sort(Comparator.comparing(c -> Optional.ofNullable(c.getCode()).orElse("")));

        // Existing rules to pre-fill
        List<BenefitPolicyRule> existingRules = ruleRepository
                .findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(policyId);
        Map<Long, BenefitPolicyRule> rulesByCategoryId = new HashMap<>();
        for (BenefitPolicyRule r : existingRules) {
            if (r.getMedicalCategory() != null) {
                rulesByCategoryId.put(r.getMedicalCategory().getId(), r);
            }
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet dataSheet = wb.createSheet(TEMPLATE_SHEET);
            XSSFSheet refSheet = wb.createSheet(REFERENCE_SHEET);

            dataSheet.setRightToLeft(true);
            refSheet.setRightToLeft(true);

            StylePack styles = new StylePack(wb);

            buildReferenceSheet(refSheet, allCategories, styles);
            buildDataSheet(dataSheet, wb, allCategories, rulesByCategoryId, policy, styles);

            // Lock reference sheet
            refSheet.protectSheet("readonly");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            log.info("[BPRuleExcel] Template generated for policy={}, categories={}, existingRules={}",
                    policyId, allCategories.size(), existingRules.size());
            return out.toByteArray();
        }
    }

    private void buildDataSheet(XSSFSheet sheet, XSSFWorkbook wb,
            List<MedicalCategory> categories,
            Map<Long, BenefitPolicyRule> existingRules,
            BenefitPolicy policy,
            StylePack styles) {

        // ── Column widths ─────────────────────────────────────────────────────
        sheet.setColumnWidth(COL_CODE, 5_000);
        sheet.setColumnWidth(COL_NAME, 10_000);
        sheet.setColumnWidth(COL_PARENT, 8_000);
        sheet.setColumnWidth(COL_COVERAGE, 4_500);
        sheet.setColumnWidth(COL_AMOUNT, 5_000);
        sheet.setColumnWidth(COL_TIMES, 4_000);
        sheet.setColumnWidth(COL_WAITING, 5_000);
        sheet.setColumnWidth(COL_PREAPPROVAL, 5_000);
        sheet.setColumnWidth(COL_NOTES, 10_000);

        // ── Header row ────────────────────────────────────────────────────────
        Row header = sheet.createRow(HEADER_ROW);
        header.setHeightInPoints(28);
        String[] headers = {
                "رمز التصنيف *",
                "اسم التصنيف",
                "التصنيف الأب",
                "نسبة التغطية % *",
                "سقف المبلغ (د.ل)",
                "سقف المرات",
                "فترة الانتظار (أيام)",
                "موافقة مسبقة",
                "ملاحظات"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle);
        }

        // ── Hidden marker row ─────────────────────────────────────────────────
        Row markerRow = sheet.createRow(MARKER_ROW);
        markerRow.setHeightInPoints(0.1f);
        markerRow.setZeroHeight(true);
        Cell markerCell = markerRow.createCell(0);
        markerCell.setCellValue(MARKER_CELL_VALUE);
        markerCell.setCellStyle(styles.hiddenStyle);

        // ── Info comment ──────────────────────────────────────────────────────
        Comment comment = sheet.createDrawingPatriarch()
                .createCellComment(new XSSFClientAnchor(0, 0, 0, 0, 0, 0, 3, 3));
        comment.setString(new XSSFRichTextString(
                "وثيقة: " + policy.getName() + " (" + policy.getPolicyCode() + ")\n" +
                        "• عمود 'رمز التصنيف' مطلوب — استخدم الرموز من ورقة 'التصنيفات_المرجعية'\n" +
                        "• 'نسبة التغطية' مطلوبة (0-100)\n" +
                        "• 'موافقة مسبقة': اكتب نعم أو لا\n" +
                        "• الصفوف الموجودة مسبقاً ستُحدَّث، والجديدة ستُضاف\n" +
                        "• لا تعدّل أو تحذف الصف الأول (العناوين)"));
        header.getCell(COL_CODE).setCellComment(comment);

        // ── Dropdown validation: pre-approval (yes/no) ────────────────────────
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        DataValidationConstraint dvConstraint = dvHelper.createExplicitListConstraint(new String[] { "نعم", "لا" });
        CellRangeAddressList preApprovalRange = new CellRangeAddressList(
                DATA_START_ROW, DATA_START_ROW + categories.size() + 500,
                COL_PREAPPROVAL, COL_PREAPPROVAL);
        DataValidation dv = dvHelper.createValidation(dvConstraint, preApprovalRange);
        dv.setSuppressDropDownArrow(false); // show dropdown arrow
        sheet.addValidationData(dv);

        // ── Data rows: one row per category ──────────────────────────────────
        // Build parent-code lookup
        Map<Long, String> idToCode = new HashMap<>();
        for (MedicalCategory cat : categories) {
            idToCode.put(cat.getId(), cat.getCode());
        }

        int rowIdx = DATA_START_ROW;
        for (MedicalCategory cat : categories) {
            Row row = sheet.createRow(rowIdx++);
            BenefitPolicyRule rule = existingRules.get(cat.getId());

            // Code (locked — user must not change)
            Cell codeCell = row.createCell(COL_CODE);
            codeCell.setCellValue(cat.getCode());
            codeCell.setCellStyle(styles.lockedStyle);

            // Name (locked)
            Cell nameCell = row.createCell(COL_NAME);
            nameCell.setCellValue(displayName(cat));
            nameCell.setCellStyle(styles.lockedStyle);

            // Parent (locked)
            Cell parentCell = row.createCell(COL_PARENT);
            String parentCode = cat.getParentId() != null ? idToCode.getOrDefault(cat.getParentId(), "") : "—";
            parentCell.setCellValue(parentCode);
            parentCell.setCellStyle(styles.lockedStyle);

            // Coverage %
            Cell covCell = row.createCell(COL_COVERAGE);
            if (rule != null && rule.getCoveragePercent() != null) {
                covCell.setCellValue(rule.getCoveragePercent());
                covCell.setCellStyle(styles.existingStyle);
            } else {
                covCell.setCellValue(policy.getDefaultCoveragePercent() != null ? policy.getDefaultCoveragePercent() : 80);
                covCell.setCellStyle(styles.editableStyle);
            }

            // Amount limit
            Cell amtCell = row.createCell(COL_AMOUNT);
            if (rule != null && rule.getAmountLimit() != null) {
                amtCell.setCellValue(rule.getAmountLimit().doubleValue());
                amtCell.setCellStyle(styles.existingStyle);
            } else {
                amtCell.setCellStyle(styles.editableStyle);
            }

            // Times limit
            Cell timesCell = row.createCell(COL_TIMES);
            if (rule != null && rule.getTimesLimit() != null) {
                timesCell.setCellValue(rule.getTimesLimit());
                timesCell.setCellStyle(styles.existingStyle);
            } else {
                timesCell.setCellStyle(styles.editableStyle);
            }

            // Waiting period
            Cell waitCell = row.createCell(COL_WAITING);
            if (rule != null) {
                waitCell.setCellValue(rule.getWaitingPeriodDays() != null ? rule.getWaitingPeriodDays() : 0);
                waitCell.setCellStyle(styles.existingStyle);
            } else {
                waitCell.setCellValue(0);
                waitCell.setCellStyle(styles.editableStyle);
            }

            // Pre-approval
            Cell preCell = row.createCell(COL_PREAPPROVAL);
            preCell.setCellValue(rule != null && rule.isRequiresPreApproval() ? "نعم" : "لا");
            preCell.setCellStyle(rule != null ? styles.existingStyle : styles.editableStyle);

            // Notes
            Cell notesCell = row.createCell(COL_NOTES);
            notesCell.setCellValue(rule != null && rule.getNotes() != null ? rule.getNotes() : "");
            notesCell.setCellStyle(rule != null ? styles.existingStyle : styles.editableStyle);
        }

        // Freeze header
        sheet.createFreezePane(0, DATA_START_ROW);
    }

    private void buildReferenceSheet(XSSFSheet sheet, List<MedicalCategory> categories, StylePack styles) {
        sheet.setColumnWidth(0, 5_500);
        sheet.setColumnWidth(1, 12_000);
        sheet.setColumnWidth(2, 12_000);
        sheet.setColumnWidth(3, 6_000);

        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        String[] headers = { "رمز التصنيف", "الاسم بالعربية", "الاسم بالإنجليزية", "رمز التصنيف الأب" };
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle);
        }

        Map<Long, String> idToCode = new HashMap<>();
        for (MedicalCategory cat : categories) {
            idToCode.put(cat.getId(), cat.getCode());
        }

        int rowIdx = 1;
        for (MedicalCategory cat : categories) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(cat.getCode());
            row.createCell(1).setCellValue(cat.getNameAr() != null ? cat.getNameAr() : cat.getName());
            row.createCell(2).setCellValue(cat.getNameEn() != null ? cat.getNameEn() : "");
            row.createCell(3).setCellValue(
                    cat.getParentId() != null ? idToCode.getOrDefault(cat.getParentId(), "") : "—");
        }

        sheet.createFreezePane(0, 1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT FROM EXCEL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Imports coverage rules from a filled Excel template.
     * Creates new rules and updates existing ones (upsert by category code).
     */
    @Transactional
    public ExcelImportResult importRules(Long policyId, MultipartFile file, boolean clearOld) {
        ImportSummary summary = new ImportSummary();
        List<ImportError> errors = new ArrayList<>();

        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessRuleException("الوثيقة غير موجودة: " + policyId));

        if (clearOld) {
            log.info("[BPRuleExcel] Clearing old rules for policy={}", policyId);
            List<BenefitPolicyRule> existingRules = ruleRepository.findByBenefitPolicyId(policyId);
            ruleRepository.deleteAll(existingRules);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = wb.getSheet(TEMPLATE_SHEET);
            if (sheet == null) {
                if (wb.getSheet("Rules") != null && wb.getSheet("Groups") != null && wb.getSheet("Buckets") != null) {
                    return buildErrorResult(summary, errors,
                            "هذا ملف بنية المنافع والأوعية، وليس ملف قواعد التغطية البسيط. " +
                                    "ارفعه من تبويب «مجموعات المنافع والسقوف» باستخدام زر «فحص الملف».");
                }
                if (wb.getNumberOfSheets() > 0) {
                    sheet = wb.getSheetAt(0);
                    log.info("[BPRuleExcel] Sheet '{}' not found, using the first sheet '{}'", TEMPLATE_SHEET, sheet.getSheetName());
                } else {
                    return buildErrorResult(summary, errors,
                            "الملف غير صحيح: يجب أن يحتوي على ورقة باسم '" + TEMPLATE_SHEET + "'. " +
                                    "استخدم القالب الذي تم تحميله من النظام.");
                }
            }

            // Verify marker - BYPASSED AS REQUESTED
            /*
            Row markerRow = sheet.getRow(MARKER_ROW);
            if (markerRow == null || !MARKER_CELL_VALUE.equals(getCellString(markerRow, 0))) {
                return buildErrorResult(summary, errors,
                        "الملف غير معتمد: يجب استخدام القالب الصادر من النظام فقط.");
            }
            */

            // Build category lookup by code
            Map<String, MedicalCategory> categoryByCode = new HashMap<>();
            for (MedicalCategory cat : categoryRepository.findByActiveTrue()) {
                categoryByCode.put(cat.getCode().trim().toUpperCase(), cat);
            }

            // Build existing rules lookup
            Map<Long, BenefitPolicyRule> existingByCatId = new HashMap<>();
            for (BenefitPolicyRule r : ruleRepository.findByBenefitPolicyId(policyId)) {
                if (r.getMedicalCategory() != null) {
                    existingByCatId.put(r.getMedicalCategory().getId(), r);
                }
            }

            int lastRow = sheet.getLastRowNum();
            for (int rowIdx = DATA_START_ROW; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null)
                    continue;

                String categoryCode = getCellString(row, COL_CODE).trim().toUpperCase();
                if (categoryCode.isEmpty())
                    continue;

                summary.setTotalRows(summary.getTotalRows() + 1);

                // Validate category code
                MedicalCategory category = categoryByCode.get(categoryCode);
                if (category == null) {
                    errors.add(ImportError.builder()
                            .rowNumber(rowIdx + 1)
                            .fieldName("رمز التصنيف")
                            .value(categoryCode)
                            .messageAr("رمز التصنيف غير موجود في قائمة التصنيفات المعتمدة")
                            .errorType(ErrorType.LOOKUP_FAILED)
                            .build());
                    summary.setRejected(summary.getRejected() + 1);
                    continue;
                }

                // Parse coverage percent (required)
                String covStr = getCellString(row, COL_COVERAGE);
                Integer coveragePercent = null;
                if (!covStr.isEmpty()) {
                    try {
                        double val = Double.parseDouble(covStr.replaceAll("[^0-9.]", ""));
                        if (val < 0 || val > 100)
                            throw new NumberFormatException("out of range");
                        coveragePercent = (int) val;
                    } catch (NumberFormatException e) {
                        errors.add(ImportError.builder()
                                .rowNumber(rowIdx + 1)
                                .fieldName("نسبة التغطية")
                                .value(covStr)
                                .messageAr("نسبة التغطية يجب أن تكون رقماً بين 0 و 100")
                                .errorType(ErrorType.INVALID_FORMAT)
                                .build());
                        summary.setRejected(summary.getRejected() + 1);
                        continue;
                    }
                }

                // Parse amount limit (optional)
                BigDecimal amountLimit = null;
                String amtStr = getCellString(row, COL_AMOUNT);
                if (!amtStr.isEmpty()) {
                    try {
                        amountLimit = new BigDecimal(amtStr.replaceAll("[^0-9.]", ""));
                        if (amountLimit.compareTo(BigDecimal.ZERO) < 0)
                            throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        errors.add(ImportError.builder()
                                .rowNumber(rowIdx + 1)
                                .fieldName("سقف المبلغ")
                                .value(amtStr)
                                .messageAr("سقف المبلغ يجب أن يكون رقماً موجباً")
                                .errorType(ErrorType.INVALID_FORMAT)
                                .build());
                        summary.setRejected(summary.getRejected() + 1);
                        continue;
                    }
                }

                // Parse times limit (optional)
                Integer timesLimit = null;
                String timesStr = getCellString(row, COL_TIMES);
                if (!timesStr.isEmpty()) {
                    try {
                        timesLimit = Integer.parseInt(timesStr.replaceAll("[^0-9]", ""));
                        if (timesLimit < 0)
                            throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        errors.add(ImportError.builder()
                                .rowNumber(rowIdx + 1)
                                .fieldName("سقف المرات")
                                .value(timesStr)
                                .messageAr("سقف المرات يجب أن يكون عدداً صحيحاً موجباً")
                                .errorType(ErrorType.INVALID_FORMAT)
                                .build());
                        summary.setRejected(summary.getRejected() + 1);
                        continue;
                    }
                }

                // Parse waiting period
                int waitingDays = 0;
                String waitStr = getCellString(row, COL_WAITING);
                if (!waitStr.isEmpty()) {
                    try {
                        waitingDays = Integer.parseInt(waitStr.replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException ignored) {
                        /* default 0 */ }
                }

                // Parse pre-approval
                boolean requiresPreApproval = getCellString(row, COL_PREAPPROVAL).trim().equals("نعم");

                // Notes
                String notes = getCellString(row, COL_NOTES).trim();
                if (notes.isEmpty())
                    notes = null;

                // Upsert rule
                BenefitPolicyRule existing = existingByCatId.get(category.getId());
                if (existing != null) {
                    // Update
                    existing.setCoveragePercent(coveragePercent);
                    existing.setAmountLimit(null);
                    existing.setTimesLimit(null);
                    existing.setWaitingPeriodDays(waitingDays);
                    existing.setRequiresPreApproval(requiresPreApproval);
                    existing.setNotes(notes);
                    existing.setActive(true);
                    existing.setDeleted(false);
                    ruleRepository.save(existing);
                    summary.setUpdated(summary.getUpdated() + 1);
                } else {
                    // Create
                    BenefitPolicyRule newRule = BenefitPolicyRule.builder()
                            .benefitPolicy(policy)
                            .medicalCategory(category)
                            .coveragePercent(coveragePercent)
                            // Limits belong exclusively to linked benefit buckets.
                            .amountLimit(null)
                            .timesLimit(null)
                            .waitingPeriodDays(waitingDays)
                            .requiresPreApproval(requiresPreApproval)
                            .notes(notes)
                            .active(true)
                            .deleted(false)
                            .build();
                    ruleRepository.save(newRule);
                    summary.setCreated(summary.getCreated() + 1);
                }
            }

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BPRuleExcel] Import error for policy={}: {}", policyId, e.getMessage(), e);
            return buildErrorResult(summary, errors, "خطأ في قراءة الملف: " + e.getMessage());
        }

        boolean success = summary.getRejected() == 0;
        log.info("[BPRuleExcel] Import done policy={}: created={}, updated={}, rejected={}",
                policyId, summary.getCreated(), summary.getUpdated(), summary.getRejected());

        return ExcelImportResult.builder()
                .summary(summary)
                .errors(errors)
                .success(success)
                .messageAr(success
                        ? "تم الاستيراد بنجاح: " + summary.getCreated() + " جديد, " + summary.getUpdated() + " محدَّث"
                        : "اكتمل الاستيراد مع " + summary.getRejected() + " أخطاء")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String getCellString(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                // Return as integer string if whole number, else decimal
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                        ? String.valueOf((long) d)
                        : String.valueOf(d);
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "نعم" : "لا";
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue().trim();
                }
            }
            default -> "";
        };
    }

    private String displayName(MedicalCategory cat) {
        if (cat.getNameAr() != null && !cat.getNameAr().isBlank())
            return cat.getNameAr();
        if (cat.getName() != null && !cat.getName().isBlank())
            return cat.getName();
        return cat.getCode();
    }

    private ExcelImportResult buildErrorResult(ImportSummary summary, List<ImportError> errors, String msg) {
        return ExcelImportResult.builder()
                .summary(summary)
                .errors(errors)
                .success(false)
                .messageAr(msg)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STYLE PACK (inner helper)
    // ═══════════════════════════════════════════════════════════════════════════

    private static class StylePack {
        final XSSFCellStyle headerStyle;
        final XSSFCellStyle lockedStyle;
        final XSSFCellStyle editableStyle;
        final XSSFCellStyle existingStyle;
        final XSSFCellStyle hiddenStyle;

        StylePack(XSSFWorkbook wb) {
            XSSFFont boldFont = wb.createFont();
            boldFont.setBold(true);
            boldFont.setColor(IndexedColors.WHITE.getIndex());
            boldFont.setFontHeightInPoints((short) 12);

            // Header: dark teal
            headerStyle = wb.createCellStyle();
            headerStyle
                    .setFillForegroundColor(new XSSFColor(new byte[] { (byte) 0x2E, (byte) 0x86, (byte) 0x6D }, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(boldFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Locked (category info — grey)
            lockedStyle = wb.createCellStyle();
            lockedStyle
                    .setFillForegroundColor(new XSSFColor(new byte[] { (byte) 0xF0, (byte) 0xF0, (byte) 0xF0 }, null));
            lockedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            lockedStyle.setBorderBottom(BorderStyle.THIN);
            lockedStyle.setBorderTop(BorderStyle.THIN);
            lockedStyle.setBorderLeft(BorderStyle.THIN);
            lockedStyle.setBorderRight(BorderStyle.THIN);

            // Editable (white + border)
            editableStyle = wb.createCellStyle();
            editableStyle.setBorderBottom(BorderStyle.THIN);
            editableStyle.setBorderTop(BorderStyle.THIN);
            editableStyle.setBorderLeft(BorderStyle.THIN);
            editableStyle.setBorderRight(BorderStyle.THIN);

            // Existing rule (light green)
            existingStyle = wb.createCellStyle();
            existingStyle
                    .setFillForegroundColor(new XSSFColor(new byte[] { (byte) 0xE8, (byte) 0xF5, (byte) 0xE9 }, null));
            existingStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            existingStyle.setBorderBottom(BorderStyle.THIN);
            existingStyle.setBorderTop(BorderStyle.THIN);
            existingStyle.setBorderLeft(BorderStyle.THIN);
            existingStyle.setBorderRight(BorderStyle.THIN);

            // Hidden style (for marker row)
            hiddenStyle = wb.createCellStyle();
            XSSFFont hiddenFont = wb.createFont();
            hiddenFont.setColor(IndexedColors.WHITE.getIndex());
            hiddenStyle.setFont(hiddenFont);
        }
    }
}
