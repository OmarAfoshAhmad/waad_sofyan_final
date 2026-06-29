package com.waad.tba.modules.member.service;

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
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.Member.MemberStatus;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Members Excel Template Generator and Import Service
 * 
 * STRICT RULES:
 * - Templates MUST be downloaded from system
 * - Create-only mode (no updates in Phase 1)
 * - Card number is auto-generated (NEVER from Excel)
 * - Employer lookup is MANDATORY
 * - Civil ID is optional and non-unique
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberExcelTemplateService {

    private final ExcelTemplateService templateService;
    private final ExcelParserService parserService;
    private final MemberRepository memberRepository;
    private final EmployerRepository employerRepository;
    private final BarcodeGeneratorService barcodeGeneratorService;
    private final CardNumberGeneratorService cardNumberGeneratorService;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate Members import template
     */
    public byte[] generateTemplate() throws IOException {
        log.info("[MemberTemplate] Generating Excel template");

        List<ExcelTemplateColumn> columns = buildColumnDefinitions();
        List<ExcelLookupData> lookups = buildLookupSheets();

        return templateService.generateTemplate("Members / الأعضاء", columns, lookups);
    }

    private List<ExcelTemplateColumn> buildColumnDefinitions() {
        return List.of(
                ExcelTemplateColumn.builder()
                        .name("full_name")
                        .nameAr("الاسم الكامل")
                        .type(ColumnType.TEXT)
                        .required(true)
                        .example("أحمد محمد علي")
                        .description("Full name (mandatory)")
                        .descriptionAr("الاسم الكامل للمستفيد (إجباري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("employer")
                        .nameAr("جهة العمل")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("المنطقة الحرة جليانة")
                        .description("Employer Name (optional, defaults to selected employer)")
                        .descriptionAr("جهة العمل أو الشركة التابع لها (اختياري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("relationship")
                        .nameAr("الصلة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("ابن")
                        .description("Relationship for dependents (optional for principal)")
                        .descriptionAr("صلة القرابة (رئيسي، ابن، ابنة، زوجة، زوج، أب، أم)")
                        .allowedValues(List.of("رئيسي", "ابن", "ابنة", "زوجة", "زوج", "أب", "أم", "PRINCIPAL", "SON", "DAUGHTER", "WIFE", "HUSBAND", "FATHER", "MOTHER"))
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("principal_card_number")
                        .nameAr("رقم بطاقة الرئيسي")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("JFZ202500001")
                        .description("Principal card number (required for dependents to link them to their principal)")
                        .descriptionAr("رقم بطاقة العضو الرئيسي (مطلوب للتابعين لربطهم بالعائل)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("card_number")
                        .nameAr("رقم البطاقة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("JFZ202500001W1")
                        .description("Member card number (optional, system will generate if empty)")
                        .descriptionAr("رقم بطاقة العضو (اختياري، سيقوم النظام بالتوليد إذا كان فارغاً)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("birth_date")
                        .nameAr("تاريخ الميلاد")
                        .type(ColumnType.DATE)
                        .required(false)
                        .example("1990-05-15")
                        .description("Birth date (optional, format: YYYY-MM-DD)")
                        .descriptionAr("تاريخ الميلاد (اختياري، بصيغة: YYYY-MM-DD)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("civil_id")
                        .nameAr("الرقم الوطني")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("119900000000")
                        .description("National Number / Civil ID (optional)")
                        .descriptionAr("الرقم الوطني للمستفيد (اختياري)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("employee_number")
                        .nameAr("الرقم الوظيفي")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("32232")
                        .description("Employee or Financial Number (optional)")
                        .descriptionAr("الرقم الوظيفي أو المالي للموظف (اختياري)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("phone")
                        .nameAr("رقم الهاتف")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("0910000000")
                        .description("Phone number (optional)")
                        .descriptionAr("رقم الهاتف للتواصل (اختياري)")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("email")
                        .nameAr("البريد الإلكتروني")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("user@example.com")
                        .description("Email address (optional)")
                        .descriptionAr("البريد الإلكتروني (اختياري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("gender")
                        .nameAr("الجنس")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("ذكر")
                        .description("Gender (MALE or FEMALE)")
                        .descriptionAr("الجنس (ذكر أو أنثى)")
                        .allowedValues(List.of("ذكر", "أنثى", "MALE", "FEMALE"))
                        .width(15)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("policy_number")
                        .nameAr("رقم الوثيقة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("POL-2025")
                        .description("Insurance policy number (optional)")
                        .descriptionAr("رقم وثيقة التأمين (اختياري)")
                        .width(20)
                        .build()
        );
    }

    private List<ExcelLookupData> buildLookupSheets() {
        try {
            List<Employer> employers = employerRepository.findByActiveTrue();
            if (employers != null && !employers.isEmpty()) {
                List<List<String>> data = employers.stream()
                        .map(emp -> List.of(
                                emp.getId().toString(),
                                emp.getCode() != null ? emp.getCode() : "",
                                emp.getName() != null ? emp.getName() : ""
                        ))
                        .collect(Collectors.toList());

                return List.of(
                        ExcelLookupData.builder()
                                .sheetName("Employers")
                                .sheetNameAr("جهات العمل")
                                .headers(List.of("ID / المعرف", "Code / الرمز", "Name / الاسم"))
                                .data(data)
                                .description("List of active employers in the system")
                                .descriptionAr("قائمة جهات العمل النشطة في النظام")
                                .build()
                );
            }
        } catch (Exception e) {
            log.error("[MemberTemplate] Error building employer lookup sheet", e);
        }
        return List.of();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Import members from Excel file (CREATE-ONLY).
     * Uses a TWO-PASS approach:
     * Pass 1 — save all PRINCIPALS so they exist in DB.
     * Pass 2 — save all DEPENDENTS (their principal is guaranteed to be in DB).
     */
    public ExcelImportResult importFromExcel(MultipartFile file, Long employerId) {
        log.info("[MemberImport] Starting import from file: {} for employerId: {}", file.getOriginalFilename(), employerId);

        ImportSummary summary = ImportSummary.builder().build();
        List<ImportError> errors = new ArrayList<>();

        Employer selectedEmployer = null;
        if (employerId != null) {
            selectedEmployer = employerRepository.findById(employerId).orElse(null);
        }

        try (Workbook workbook = parserService.openWorkbook(file)) {
            Sheet sheet = parserService.getDataSheet(workbook);

            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndices = findColumnIndices(headerRow);

            validateMandatoryColumns(columnIndices, errors);
            if (!errors.isEmpty()) {
                return buildErrorResult(summary, errors, "Mandatory columns missing");
            }

            Map<String, Employer> employerLookup = buildEmployerLookup();

            // Cache per-employer principal names (DB) — for Pass 1 duplicate detection
            Map<Long, Set<String>> existingPrincipalNamesCache = new HashMap<>();
            // Cache per-employer dependent dedup keys "parentId::lowerName" (DB) — Pass 2
            // Built from Object[] to avoid JPQL CONCAT type issues with Long in Hibernate 6
            Map<Long, Set<String>> existingDependentKeysCache = new HashMap<>();

            // card_number → saved Member (principals saved in pass 1)
            Map<String, Member> importedPrincipalsCache = new HashMap<>();
            // rowNum → saved Member — lets PASS-2 find the principal by row order (most reliable)
            Map<Integer, Member> principalRowToMember = new HashMap<>();
            Set<String> usedCardNumbers = new HashSet<>(memberRepository.findAllCardNumbers());
            // In-file dedup keys:
            // principals → "P::employerId::fullNameLower"
            // dependents → "D::parentId::fullNameLower"
            Set<String> inFileKeys = new HashSet<>();

            final int BATCH_SIZE = 100;
            int firstDataRow = 1;
            int lastRow = sheet.getLastRowNum();
            summary.setTotalRows(lastRow - firstDataRow + 1);

            // Count row types for progress logging
            int pass1Total = 0;
            int pass2Total = 0;
            for (int r = firstDataRow; r <= lastRow; r++) {
                Row rr = sheet.getRow(r);
                if (rr == null || parserService.isEmptyRow(rr))
                    continue;
                if (isDependentRow(rr, columnIndices))
                    pass2Total++;
                else
                    pass1Total++;
            }
            log.info("[MemberImport] شروع — إجمالي: {} صف (رئيسيين: {}، تابعين: {})",
                    summary.getTotalRows(), pass1Total, pass2Total);

            // ══════════════════════════════════════════════════════════════
            // PRE-PASS — Map each principal row to the card number its dependents expect.
            //
            // Excel structure (typical):
            // Row N : Principal (employer filled, principal_card_number empty)
            // Row N+1: Dependent (principal_card_number = JFZ..., relationship = ...)
            // Row N+2: Dependent (same principal_card_number)
            // Row N+3: Next Principal ...
            //
            // We scan in order. When we encounter a DEPENDENT row we note the
            // principal_card_number it references and assign it to the most recent
            // principal row we saw. This gives us the correct 1-to-1 mapping:
            // principalRowNum → cardNumber
            // ══════════════════════════════════════════════════════════════
            Map<Integer, String> principalRowToCardNumber = new HashMap<>();
            int lastSeenPrincipalRow = -1;

            for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || parserService.isEmptyRow(row))
                    continue;

                if (isDependentRow(row, columnIndices)) {
                    // First dependent after a principal tells us that principal's card number
                    if (lastSeenPrincipalRow != -1 && !principalRowToCardNumber.containsKey(lastSeenPrincipalRow)) {
                        String pCard = null;
                        Integer pCardIdx = columnIndices.get("principal_card_number");
                        if (pCardIdx != null) {
                            pCard = normalizeCardNumber(getCellValue(row, pCardIdx));
                        }
                        if ((pCard == null || pCard.isBlank()) && columnIndices.get("card_number") != null) {
                            String excelCard = getCellValue(row, columnIndices.get("card_number"));
                            if (excelCard != null && !excelCard.isBlank()) {
                                String extracted = extractPrincipalCardNumber(excelCard);
                                if (extracted != null && !extracted.equalsIgnoreCase(normalizeCardNumber(excelCard))) {
                                    pCard = extracted;
                                }
                            }
                        }
                        if (pCard != null && !pCard.isBlank()) {
                            principalRowToCardNumber.put(lastSeenPrincipalRow, pCard);
                        }
                    }
                } else {
                    lastSeenPrincipalRow = rowNum;
                }
            }

            log.info("[MemberImport] PRE-PASS: {} صف رئيسي مرتبط برقم بطاقة",
                    principalRowToCardNumber.size());

            // ══════════════════════════════════════════════════════════════
            // PASS 1 — PRINCIPALS ONLY
            // ══════════════════════════════════════════════════════════════
            List<Member> principalBatch = new ArrayList<>();
            List<Integer> principalBatchRowNums = new ArrayList<>();
            List<Member> updateBatch = new ArrayList<>();
            Map<String, Member> existingPrincipalsByName = new HashMap<>();

            if (selectedEmployer != null) {
                log.info("[MemberImport] Pre-loading active principal members for employer Org ID: {}", selectedEmployer.getId());
                List<Member> activePrincipals = memberRepository.findActivePrincipalsByEmployerId(selectedEmployer.getId());
                for (Member p : activePrincipals) {
                    if (p.getFullName() != null) {
                        existingPrincipalsByName.put(normalizeText(p.getFullName()), p);
                    }
                }
                log.info("[MemberImport] Pre-loaded {} active principal members from DB", existingPrincipalsByName.size());
            }

            // ── بناء خريطة البحث بالرقم لتجنب تكرار المفتاح ─────────────────────
            Map<String, Member> existingPrincipalsByCardNumber = new HashMap<>();
            for (Member p : existingPrincipalsByName.values()) {
                if (p.getCardNumber() != null && !p.getCardNumber().isBlank()) {
                    existingPrincipalsByCardNumber.put(p.getCardNumber().trim(), p);
                }
            }

            int pass1Processed = 0;

            log.info("[MemberImport] PASS-1 بدء — {} صف رئيسي للمعالجة", pass1Total);

            for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || parserService.isEmptyRow(row))
                    continue;

                // Skip dependent rows entirely in pass 1
                if (isDependentRow(row, columnIndices))
                    continue;

                try {
                    Member member = parseAndCreateMember(row, rowNum, columnIndices,
                            employerLookup, importedPrincipalsCache, null, selectedEmployer, errors);

                    if (member == null) {
                        summary.setRejected(summary.getRejected() + 1);
                        pass1Processed++;
                        continue;
                    }

                    String fullNameLower = normalizeText(member.getFullName());
                    Long rowEmployerId = member.getEmployer().getId();
                    String inFileKey = "P::" + rowEmployerId + "::" + fullNameLower;

                    if (inFileKeys.contains(inFileKey)) {
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setPrincipalsSkipped(summary.getPrincipalsSkipped() + 1);
                        pass1Processed++;
                        continue;
                    }

                    if (existingPrincipalsByName.containsKey(fullNameLower)) {
                        inFileKeys.add(inFileKey);
                        Member existingPrincipal = existingPrincipalsByName.get(fullNameLower);
                        String oldCardNumber = existingPrincipal.getCardNumber();
                        if (oldCardNumber != null && !oldCardNumber.isBlank()) {
                            importedPrincipalsCache.put(oldCardNumber, existingPrincipal);
                        }

                        // Determine the new clean format card number
                        String targetCardNumber = member.getCardNumber();
                        if (targetCardNumber == null || targetCardNumber.isBlank()) {
                            targetCardNumber = principalRowToCardNumber.get(rowNum);
                        }
                        if (targetCardNumber == null || targetCardNumber.isBlank()) {
                            targetCardNumber = cardNumberGeneratorService.generateUniqueForPrincipal(existingPrincipal);
                        }

                        // Update card number if changed and not already used
                        if (!targetCardNumber.equals(oldCardNumber) && !usedCardNumbers.contains(targetCardNumber)) {
                            log.info("[MemberImport] Updating principal '{}' card number from '{}' to '{}'", 
                                    existingPrincipal.getFullName(), oldCardNumber, targetCardNumber);
                            existingPrincipal.setCardNumber(targetCardNumber);
                        }

                        // Update other fields if available in Excel
                        if (member.getNationalNumber() != null && !member.getNationalNumber().isBlank()) {
                            existingPrincipal.setNationalNumber(member.getNationalNumber());
                        }
                        if (member.getBirthDate() != null) {
                            existingPrincipal.setBirthDate(member.getBirthDate());
                        }
                        if (member.getGender() != null) {
                            existingPrincipal.setGender(member.getGender());
                        }
                        if (member.getPhone() != null && !member.getPhone().isBlank()) {
                            existingPrincipal.setPhone(member.getPhone());
                        }
                        if (member.getMaritalStatus() != null) {
                            existingPrincipal.setMaritalStatus(member.getMaritalStatus());
                        }

                        updateBatch.add(existingPrincipal);

                        if (updateBatch.size() >= BATCH_SIZE) {
                            List<Member> savedList = memberRepository.saveAll(updateBatch);
                            for (Member s : savedList) {
                                usedCardNumbers.add(s.getCardNumber());
                                importedPrincipalsCache.put(s.getCardNumber(), s);
                                existingPrincipalsByName.put(s.getFullName().trim().toLowerCase(), s);
                            }
                            summary.setUpdated(summary.getUpdated() + updateBatch.size());
                            updateBatch.clear();
                        }

                        // سجّل الصف → الرئيسي حتى يجده PASS-2 بالترتيب
                        principalRowToMember.put(rowNum, existingPrincipal);
                        pass1Processed++;
                        continue;
                    }

                    inFileKeys.add(inFileKey);

                    // ── تحديث بالرقم إذا كان رقم البطاقة موجوداً في DB ─────────────────────
                    String excelCardForCheck = member.getCardNumber();
                    if (excelCardForCheck != null && !excelCardForCheck.isBlank()
                            && existingPrincipalsByCardNumber.containsKey(excelCardForCheck)) {
                        Member existingByCard = existingPrincipalsByCardNumber.get(excelCardForCheck);
                        if (member.getNationalNumber() != null && !member.getNationalNumber().isBlank())
                            existingByCard.setNationalNumber(member.getNationalNumber());
                        if (member.getBirthDate() != null)
                            existingByCard.setBirthDate(member.getBirthDate());
                        if (member.getGender() != null)
                            existingByCard.setGender(member.getGender());
                        if (member.getPhone() != null && !member.getPhone().isBlank())
                            existingByCard.setPhone(member.getPhone());
                        updateBatch.add(existingByCard);
                        if (updateBatch.size() >= BATCH_SIZE) {
                            List<Member> savedList = memberRepository.saveAll(updateBatch);
                            for (Member s : savedList) {
                                usedCardNumbers.add(s.getCardNumber());
                                importedPrincipalsCache.put(s.getCardNumber(), s);
                                existingPrincipalsByName.put(s.getFullName().trim().toLowerCase(), s);
                            }
                            summary.setUpdated(summary.getUpdated() + updateBatch.size());
                            updateBatch.clear();
                        }
                        // سجّل الصف → الرئيسي الموجود (تحديث بالرقم)
                        principalRowToMember.put(rowNum, existingByCard);
                        pass1Processed++;
                        continue;
                    }

                    // Assign card number: prefer the value dependents already reference
                    // (pre-pass), then fall back to auto-generate.
                    if (member.getCardNumber() == null || member.getCardNumber().isBlank()) {
                        String mappedCard = principalRowToCardNumber.get(rowNum);
                        if (mappedCard != null && !usedCardNumbers.contains(mappedCard)) {
                            member.setCardNumber(mappedCard);
                        } else {
                            // توليد رقم فريد مع مراعاة الأرقام المولّدة في نفس الدفعة (usedCardNumbers)
                            member.setCardNumber(
                                    generateUniqueCardNumberWithCache(member, usedCardNumbers));
                        }
                    }
                    if (usedCardNumbers.contains(member.getCardNumber())) {
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setPrincipalsSkipped(summary.getPrincipalsSkipped() + 1);
                        pass1Processed++;
                        continue;
                    }
                    // ── تسجيل الرقم فوراً لمنع التكرار داخل نفس الدفعة ───────────────────
                    usedCardNumbers.add(member.getCardNumber());

                    member.setBarcode(barcodeGeneratorService.generateUniqueBarcodeForPrincipal());
                    principalBatch.add(member);
                    principalBatchRowNums.add(rowNum);
                    pass1Processed++;

                    if (principalBatch.size() >= BATCH_SIZE) {
                        List<Member> saved = memberRepository.saveAll(principalBatch);
                        for (int i = 0; i < saved.size(); i++) {
                            Member s = saved.get(i);
                            usedCardNumbers.add(s.getCardNumber());
                            importedPrincipalsCache.put(s.getCardNumber(), s);
                            existingPrincipalsByName.put(s.getFullName().trim().toLowerCase(), s);
                            if (i < principalBatchRowNums.size()) {
                                principalRowToMember.put(principalBatchRowNums.get(i), s);
                            }
                        }
                        summary.setCreated(summary.getCreated() + saved.size());
                        summary.setPrincipalsCreated(summary.getPrincipalsCreated() + saved.size());
                        principalBatch.clear();
                        principalBatchRowNums.clear();
                        log.info("[MemberImport] PASS-1 تقدم — {}/{} رئيسي (أُنشئ {} حتى الآن)",
                                pass1Processed, pass1Total, summary.getPrincipalsCreated());
                    }
                } catch (Exception e) {
                    log.error("[MemberImport] PASS-1 خطأ صف {}: {}", rowNum, e.getMessage());
                    errors.add(ImportError.builder()
                            .rowNumber(rowNum - 1)
                            .errorType(ErrorType.PROCESSING_ERROR)
                            .messageAr("خطأ في معالجة الصف (رئيسي): " + e.getMessage())
                            .messageEn("Error processing principal row: " + e.getMessage())
                            .build());
                    summary.setFailed(summary.getFailed() + 1);
                    principalBatch.clear();
                    pass1Processed++;
                }
            }

            // Flush remaining updates
            if (!updateBatch.isEmpty()) {
                List<Member> savedList = memberRepository.saveAll(updateBatch);
                for (Member s : savedList) {
                    usedCardNumbers.add(s.getCardNumber());
                    importedPrincipalsCache.put(s.getCardNumber(), s);
                }
                summary.setUpdated(summary.getUpdated() + updateBatch.size());
                updateBatch.clear();
            }

            // Flush remaining principals
            if (!principalBatch.isEmpty()) {
                List<Member> saved = memberRepository.saveAll(principalBatch);
                for (int i = 0; i < saved.size(); i++) {
                    Member s = saved.get(i);
                    usedCardNumbers.add(s.getCardNumber());
                    importedPrincipalsCache.put(s.getCardNumber(), s);
                    existingPrincipalsByName.put(normalizeText(s.getFullName()), s);
                    if (i < principalBatchRowNums.size()) {
                        principalRowToMember.put(principalBatchRowNums.get(i), s);
                    }
                }
                summary.setCreated(summary.getCreated() + saved.size());
                summary.setPrincipalsCreated(summary.getPrincipalsCreated() + saved.size());
                principalBatch.clear();
                principalBatchRowNums.clear();
            }


            log.info("[MemberImport] PASS-1 اكتمل — أُنشئ {} رئيسي، تُخطّي {} رئيسي",
                    summary.getPrincipalsCreated(), summary.getPrincipalsSkipped());

            // ══════════════════════════════════════════════════════════════
            // PASS 2 — DEPENDENTS ONLY
            // ══════════════════════════════════════════════════════════════
            if (pass2Total > 0) {
                List<Member> dependentBatch = new ArrayList<>();
                int pass2Processed = 0;
                Map<String, Integer> ordinalCounters = new HashMap<>();
                log.info("[MemberImport] PASS-2 بدء — {} صف تابع للمعالجة", pass2Total);

                Member lastSeenPrincipal = null;
                for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (parserService.isEmptyRow(row))
                        continue;

                    // Skip principal rows — already saved in pass 1
                    if (!isDependentRow(row, columnIndices)) {
                        String fullName = normalizeMemberName(getCellValue(row, columnIndices.get("full_name")));
                        String excelCardNumber = normalizeCardNumber(getCellValue(row, columnIndices.get("card_number")));

                        // 1) أسرع وأدق طريقة: خريطة الصف → الرئيسي المحفوظ في PASS-1
                        lastSeenPrincipal = principalRowToMember.get(rowNum);

                        // 2) بحث بالرقم في الكاش (احتياطي لو كان الرقم موجوداً في الملف)
                        if (lastSeenPrincipal == null && excelCardNumber != null && !excelCardNumber.isBlank()) {
                            lastSeenPrincipal = importedPrincipalsCache.get(excelCardNumber);
                        }
                        // 3) بحث بالاسم في الكاش
                        if (lastSeenPrincipal == null && fullName != null && !fullName.isBlank()) {
                            lastSeenPrincipal = existingPrincipalsByName.get(fullName.trim().toLowerCase());
                        }
                        // 4) بحث مباشر في DB (احتياطي أخير)
                        if (lastSeenPrincipal == null && fullName != null && !fullName.isBlank()) {
                            String employerName = getCellValue(row, columnIndices.get("employer"));
                            Employer employer = findEmployerFuzzy(employerName, employerLookup);
                            if (employer != null) {
                                lastSeenPrincipal = memberRepository.findActivePrincipalByFullNameLowerAndEmployerId(
                                        fullName.trim().toLowerCase(), employer.getId()).orElse(null);
                                if (lastSeenPrincipal != null) {
                                    existingPrincipalsByName.put(fullName.trim().toLowerCase(), lastSeenPrincipal);
                                    importedPrincipalsCache.put(lastSeenPrincipal.getCardNumber(), lastSeenPrincipal);
                                    principalRowToMember.put(rowNum, lastSeenPrincipal);
                                }
                            }
                        }
                        log.debug("[MemberImport] PASS-2: صف رئيسي {} → lastSeenPrincipal = {}",
                                rowNum, lastSeenPrincipal != null ? lastSeenPrincipal.getFullName() + "/" + lastSeenPrincipal.getCardNumber() : "null");
                        continue;
                    }

                    try {
                        Member member = parseAndCreateMember(row, rowNum, columnIndices,
                                employerLookup, importedPrincipalsCache, lastSeenPrincipal, selectedEmployer, errors);

                        if (member == null) {
                            summary.setRejected(summary.getRejected() + 1);
                            pass2Processed++;
                            continue;
                        }

                        String fullNameLower = normalizeText(member.getFullName());
                        Long rowEmployerId = member.getEmployer().getId();
                        Long parentId = member.getParent() != null ? member.getParent().getId() : null;
                        String inFileKey = "D::" + parentId + "::" + fullNameLower;

                        if (inFileKeys.contains(inFileKey)) {
                            summary.setSkipped(summary.getSkipped() + 1);
                            summary.setDependentsSkipped(summary.getDependentsSkipped() + 1);
                            pass2Processed++;
                            continue;
                        }

                        // DB duplicate check — uses Object[] to avoid JPQL CONCAT type mismatch
                        Set<String> existingDepKeys = existingDependentKeysCache.computeIfAbsent(rowEmployerId,
                                this::buildDependentKeySet);
                        if (existingDepKeys.contains(parentId + "::" + fullNameLower)) {
                            inFileKeys.add(inFileKey);
                            
                            // Load the existing dependent from the DB under this parent and with this name
                            List<Member> parentDeps = memberRepository.findByParentId(parentId);
                            Member existingDependent = parentDeps.stream()
                                    .filter(d -> normalizeText(d.getFullName()).equals(fullNameLower))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (existingDependent != null) {
                                String oldCardNumber = existingDependent.getCardNumber();
                                String targetCardNumber = member.getCardNumber();
                                
                                // If not specified in Excel, generate a clean hyphenless card number based on current parent's card
                                if (targetCardNumber == null || targetCardNumber.isBlank()) {
                                    targetCardNumber = cardNumberGeneratorService.generateForDependent(existingDependent.getParent(), existingDependent.getRelationship());
                                }
                                
                                if (targetCardNumber != null && !targetCardNumber.equals(oldCardNumber) && !usedCardNumbers.contains(targetCardNumber)) {
                                    log.info("[MemberImport] Updating dependent '{}' card number from '{}' to '{}'", 
                                            existingDependent.getFullName(), oldCardNumber, targetCardNumber);
                                    existingDependent.setCardNumber(targetCardNumber);
                                }
                                
                                // Update other fields if available in Excel
                                if (member.getNationalNumber() != null && !member.getNationalNumber().isBlank()) {
                                    existingDependent.setNationalNumber(member.getNationalNumber());
                                }
                                if (member.getBirthDate() != null) {
                                    existingDependent.setBirthDate(member.getBirthDate());
                                }
                                if (member.getGender() != null) {
                                    existingDependent.setGender(member.getGender());
                                }
                                if (member.getPhone() != null && !member.getPhone().isBlank()) {
                                    existingDependent.setPhone(member.getPhone());
                                }
                                if (member.getMaritalStatus() != null) {
                                    existingDependent.setMaritalStatus(member.getMaritalStatus());
                                }
                                
                                memberRepository.save(existingDependent);
                                usedCardNumbers.add(existingDependent.getCardNumber());
                                summary.setUpdated(summary.getUpdated() + 1);
                            }
                            pass2Processed++;
                            continue;
                        }

                        inFileKeys.add(inFileKey);
                        // Keep cache fresh for within-session duplicates
                        existingDepKeys.add(parentId + "::" + fullNameLower);

                        if (member.getCardNumber() == null || member.getCardNumber().isBlank()) {
                            // Use in-memory ordinal counter (seeded from DB once) instead of
                            // generateForDependent which re-queries DB and sees stale batch counts
                            Long pid = member.getParent().getId();
                            Member.Relationship rel = member.getRelationship();
                            String ordinalKey = pid + "::" + rel.name();
                            int currentOrdinal = ordinalCounters.computeIfAbsent(ordinalKey,
                                    k -> (int) memberRepository.countByParentIdAndRelationship(pid, rel));
                            currentOrdinal++;
                            ordinalCounters.put(ordinalKey, currentOrdinal);
                            member.setCardNumber(member.getParent().getCardNumber()
                                    + rel.getCardCode() + currentOrdinal);
                        }
                        if (usedCardNumbers.contains(member.getCardNumber())) {
                            // Real collision (card already exists in DB or was assigned this session)
                            // — bump ordinal until we find a free slot instead of dropping the record
                            Long pid = member.getParent().getId();
                            Member.Relationship rel = member.getRelationship();
                            String ordinalKey = pid + "::" + rel.name();
                            int ordinal = ordinalCounters.getOrDefault(ordinalKey, 0);
                            String candidate;
                            do {
                                ordinal++;
                                candidate = member.getParent().getCardNumber()
                                        + rel.getCardCode() + ordinal;
                            } while (usedCardNumbers.contains(candidate) && ordinal < 999);
                            ordinalCounters.put(ordinalKey, ordinal);
                            member.setCardNumber(candidate);
                        }
                        if (usedCardNumbers.contains(member.getCardNumber())) {
                            // Truly exhausted — skip with a clear reason
                            log.warn("[MemberImport] PASS-2 تعذّر توليد رقم بطاقة فريد للصف {} ({})",
                                    rowNum, member.getFullName());
                            summary.setSkipped(summary.getSkipped() + 1);
                            summary.setDependentsSkipped(summary.getDependentsSkipped() + 1);
                            pass2Processed++;
                            continue;
                        }
                        usedCardNumbers.add(member.getCardNumber());

                        // Use JPA proxy for parent FK — avoids detached entity merge issues
                        if (member.getParent() != null && member.getParent().getId() != null) {
                            member.setParent(memberRepository.getReferenceById(member.getParent().getId()));
                        }

                        dependentBatch.add(member);
                        pass2Processed++;

                        if (dependentBatch.size() >= BATCH_SIZE) {
                            memberRepository.saveAll(dependentBatch);
                            summary.setCreated(summary.getCreated() + dependentBatch.size());
                            summary.setDependentsCreated(summary.getDependentsCreated() + dependentBatch.size());
                            dependentBatch.clear();
                            log.info("[MemberImport] PASS-2 تقدم — {}/{} تابع (أُنشئ {} حتى الآن)",
                                    pass2Processed, pass2Total, summary.getDependentsCreated());
                        }
                    } catch (Exception e) {
                        log.error("[MemberImport] PASS-2 خطأ صف {}: {}", rowNum, e.getMessage());
                        errors.add(ImportError.builder()
                                .rowNumber(rowNum - 1)
                                .errorType(ErrorType.PROCESSING_ERROR)
                                .messageAr("خطأ في معالجة الصف (تابع): " + e.getMessage())
                                .messageEn("Error processing dependent row: " + e.getMessage())
                                .build());
                        summary.setFailed(summary.getFailed() + 1);
                        dependentBatch.clear();
                        pass2Processed++;
                    }
                }

                // Flush remaining dependents
                if (!dependentBatch.isEmpty()) {
                    memberRepository.saveAll(dependentBatch);
                    summary.setCreated(summary.getCreated() + dependentBatch.size());
                    summary.setDependentsCreated(summary.getDependentsCreated() + dependentBatch.size());
                    log.info("[MemberImport] PASS-2 دفعة نهائية — {} تابع", dependentBatch.size());
                    dependentBatch.clear();
                }

                log.info("[MemberImport] PASS-2 اكتمل — أُنشئ {} تابع، تُخطّي {} تابع",
                        summary.getDependentsCreated(), summary.getDependentsSkipped());
            }

            String messageAr = String.format(
                    "رئيسيون: أُنشئ %d، تُخطّي %d | تابعون: أُنشئ %d، تُخطّي %d | فشل %d",
                    summary.getPrincipalsCreated(), summary.getPrincipalsSkipped(),
                    summary.getDependentsCreated(), summary.getDependentsSkipped(),
                    summary.getRejected() + summary.getFailed());
            String messageEn = String.format(
                    "Principals: created %d, skipped %d | Dependents: created %d, skipped %d | failed %d",
                    summary.getPrincipalsCreated(), summary.getPrincipalsSkipped(),
                    summary.getDependentsCreated(), summary.getDependentsSkipped(),
                    summary.getRejected() + summary.getFailed());

            log.info("[MemberImport] الاستيراد اكتمل: {}", messageEn);

            return ExcelImportResult.builder()
                    .summary(summary)
                    .errors(errors)
                    .success(summary.getCreated() > 0)
                    .messageAr(messageAr)
                    .messageEn(messageEn)
                    .build();

        } catch (IOException e) {
            log.error("[MemberImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        } catch (Exception e) {
            log.error("[MemberImport] Import failed", e);
            throw new BusinessRuleException("فشل استيراد البيانات: " + e.getMessage());
        }
    }

    /**
     * Build a Set of "parentId::lowerName" dependent dedup keys for an employer.
     * Uses Object[] from DB to avoid JPQL CONCAT incompatibility with Long args.
     */
    private Set<String> buildDependentKeySet(Long employerId) {
        List<Object[]> rows = memberRepository.findActiveDependentParentIdAndNamesByEmployerId(employerId);
        Set<String> keys = new HashSet<>(rows.size() * 2);
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null)
                continue;
            Long parentId = ((Number) row[0]).longValue();
            String name = (String) row[1];
            keys.add(parentId + "::" + normalizeText(name));
        }
        return keys;
    }

    private Integer findColumnIndexSmart(Row headerRow, String... synonyms) {
        if (headerRow == null || synonyms == null || synonyms.length == 0) {
            return null;
        }

        List<String> normalizedSyns = Arrays.stream(synonyms)
                .map(this::normalizeText)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // Pass 1: Exact normalized match
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String headerVal = parserService.getCellValueAsString(cell);
            if (headerVal == null) continue;

            String cleanHeader = normalizeText(headerVal.replace("*", ""));
            if (normalizedSyns.contains(cleanHeader)) {
                return i;
            }
        }

        // Pass 2: Substring normalized match
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String headerVal = parserService.getCellValueAsString(cell);
            if (headerVal == null) continue;

            String cleanHeader = normalizeText(headerVal.replace("*", ""));
            for (String syn : normalizedSyns) {
                if (syn.length() >= 4 && (cleanHeader.contains(syn) || syn.contains(cleanHeader))) {
                    return i;
                }
            }
        }

        return null;
    }

    private Map<String, Integer> findColumnIndices(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();

        // تسجيل جميع ترويسات الملف للمساعدة في التشخيص
        if (headerRow != null) {
            StringBuilder headerDump = new StringBuilder("[MemberImport] Excel Headers Found: ");
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String v = parserService.getCellValueAsString(cell);
                    if (v != null && !v.isBlank()) {
                        headerDump.append("[").append(i).append(":'").append(v.trim()).append("'] ");
                    }
                }
            }
            log.info("{}", headerDump);
        }

        indices.put("full_name", findColumnIndexSmart(headerRow,
                "full_name", "الاسم الكامل", "full name", "اسم الموظف", "الاسم", "اسم المستفيد", "المستفيد", "الاسم بالعربية", "اسم المريض", "اسم المؤمن عليه"));
        indices.put("employer", findColumnIndexSmart(headerRow,
                "employer", "جهة العمل", "emp name", "company", "الشركة", "الشركه", "اسم الشركة", "المؤسسة", "صاحب العمل", "مكان العمل", "اسم جهة العمل"));
        indices.put("principal_card_number", findColumnIndexSmart(headerRow,
                "principal_card_number", "رقم بطاقة الرئيسي", "principal card", "parent card", "رقم بطاقة العائل",
                "رقم بطاقة الكفيل", "رقم تأمين الرئيسي", "رقم التأمين الرئيسي", "رقم بطاقة الموظف", "رقم الموظف",
                "رقم المشترك", "رقم التأمين", "رقم الاشتراك", "رقم العضوية"));
        indices.put("relationship", findColumnIndexSmart(headerRow,
                "relationship", "القرابة", "rel type", "صلة القرابة", "الصلة", "الصلة بالمشترك", "درجة القرابة", "الصله", "نوع العلاقة", "الصلة بالمؤمن"));
        indices.put("card_number", findColumnIndexSmart(headerRow,
                "card_number", "رقم البطاقة", "member card", "معرّف البطاقة", "رقم الكارت",
                "رقم بطاقة التابع", "رقم بطاقة العضو", "رقم بطاقة المستفيد",
                "رقم بطاقه", "رقم البطاقه", "رقم الكارته", "رقم التامين",
                "card no", "card number", "insurance card", "رقم وثيقة التأمين", "رقم الوثيقة"));
        indices.put("birth_date", findColumnIndexSmart(headerRow,
                "birth_date", "المواليد", "تاريخ الميلاد", "تاريخ الولادة", "birth date", "date of birth", "dob", "birthdate"));

        log.info("[MemberImport] Final Column Indices Detection: {}", indices);
        return indices;
    }

    private void validateMandatoryColumns(Map<String, Integer> columnIndices, List<ImportError> errors) {
        // RELAXED VALIDATION: full_name is mandatory column.
        // employer is required only for principal rows.
        // principal_card_number + relationship are optional and used for dependent
        // rows.
        String[] mandatoryColKeys = {
                "full_name"
        };

        List<String> missingMandatoryCols = new ArrayList<>();

        for (String col : mandatoryColKeys) {
            if (columnIndices.get(col) == null) {
                missingMandatoryCols.add(col);
            }
        }

        if (!missingMandatoryCols.isEmpty()) {
            errors.add(ImportError.builder()
                    .rowNumber(0)
                    .errorType(ErrorType.MISSING_REQUIRED)
                    .columnName("TEMPLATE_HEADER")
                    .messageAr("الأعمدة الإجبارية مفقودة: " + String.join(", ", missingMandatoryCols)
                            + ". يجب وجود عمود الاسم الكامل.")
                    .messageEn("Missing mandatory columns: " + String.join(", ", missingMandatoryCols)
                            + ". full_name column is required.")
                    .build());
        }

    }

    private String normalizeText(String text) {
        if (text == null)
            return "";
        return text.trim().toLowerCase()
                .replaceAll("[أإآ]", "ا")
                .replaceAll("ة", "ه")
                .replaceAll("ى", "ي")
                .replaceAll("\\s+", " ");
    }

    private String normalizeCardNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim().toUpperCase(Locale.ROOT);
        
        // Remove hyphens if any (to support the clean hyphenless format like JFZ13544D1)
        clean = clean.replace("-", "");
        
        // Convert any relationship suffix without a digit to end with 1 (e.g. W -> W1)
        String regex = "(W|H|S|D|F|M|B|SR)$";
        if (clean.matches(".+" + regex)) {
            clean = clean + "1";
        }
        
        // Strip leading zeros in ordinals (e.g., D01 -> D1, W02 -> W2)
        clean = clean.replaceAll("(W|H|S|D|F|M|B|SR)0+([1-9][0-9]*)$", "$1$2");
        
        return clean;
    }

    private Map<String, Employer> buildEmployerLookup() {
        List<Employer> employers = employerRepository.findByActiveTrue();
        Map<String, Employer> lookup = new HashMap<>();

        for (Employer emp : employers) {
            // By ID
            String idStr = emp.getId().toString();
            lookup.put(idStr, emp);

            // By Name (normalized) - Employer has 'name' field
            if (emp.getName() != null) {
                lookup.put(normalizeText(emp.getName()), emp);
                // Also store exact name (case-insensitive)
                lookup.put(emp.getName().trim().toLowerCase(), emp);
            }

            // By Code if available
            if (emp.getCode() != null) {
                lookup.put(emp.getCode().trim().toLowerCase(), emp);
            }
        }

        log.debug("[MemberImport] Built employer lookup with {} entries for {} employers",
                lookup.size(), employers.size());

        return lookup;
    }

    /**
     * Try to find employer with fuzzy matching
     */
    private Employer findEmployerFuzzy(String employerName, Map<String, Employer> employerLookup) {
        if (employerName == null || employerName.trim().isEmpty()) {
            return null;
        }

        // Try exact normalized match
        String normalizedInput = normalizeText(employerName);
        Employer employer = employerLookup.get(normalizedInput);
        if (employer != null)
            return employer;

        // Try exact case-insensitive
        employer = employerLookup.get(employerName.trim().toLowerCase());
        if (employer != null)
            return employer;

        // Try ID match
        employer = employerLookup.get(employerName.trim());
        if (employer != null)
            return employer;

        // Try partial match (check if any key contains our input or vice versa)
        String inputLower = employerName.trim().toLowerCase();
        for (Map.Entry<String, Employer> entry : employerLookup.entrySet()) {
            String key = entry.getKey();
            if (key.contains(inputLower) || inputLower.contains(key)) {
                log.debug("[MemberImport] Found partial match: '{}' matches '{}'", employerName, key);
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean isDependentRow(Row row, Map<String, Integer> columnIndices) {
        String excelCardNumber = normalizeCardNumber(getCellValue(row, columnIndices.get("card_number")));
        if (excelCardNumber == null || excelCardNumber.isBlank()) {
            return false;
        }
        return !cardNumberGeneratorService.isPrincipalCardNumber(excelCardNumber);
    }

    private Member parseAndCreateMember(
            Row row,
            int rowNum,
            Map<String, Integer> columnIndices,
            Map<String, Employer> employerLookup,
            Map<String, Member> sessionPrincipals,
            Member lastSeenPrincipal,
            Employer selectedEmployer,
            List<ImportError> errors) {
        // Extract values
        String fullName = normalizeMemberName(getCellValue(row, columnIndices.get("full_name")));
        String employerName = getCellValue(row, columnIndices.get("employer"));
        
        String excelCardNumber = normalizeCardNumber(getCellValue(row, columnIndices.get("card_number")));

        // Validate mandatory fields
        boolean hasErrors = false;

        if (fullName == null || fullName.trim().isEmpty()) {
            errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "full_name",
                    "الاسم الكامل مطلوب", "Full name is required", fullName, "Unknown"));
            hasErrors = true;
        }

        Employer employer = selectedEmployer;
        if (employer == null) {
            employer = findEmployerFuzzy(employerName, employerLookup);
        }

        if (employer == null) {
            errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "employer",
                    "جهة العمل مطلوبة ويجب تحديدها من شاشة الاستيراد", 
                    "Employer is required and must be selected in the import screen", 
                    employerName, fullName));
            hasErrors = true;
        }

        LocalDate birthDate = null;
        Integer birthDateIdx = columnIndices.get("birth_date");
        if (birthDateIdx != null) {
            birthDate = getCellValueAsDate(row, birthDateIdx);
        }

        boolean isDependent = !cardNumberGeneratorService.isPrincipalCardNumber(excelCardNumber);

        Member.Relationship relationship = null;
        Member parent = null;

        if (isDependent) {
            String cleanCard = excelCardNumber.trim().toUpperCase(Locale.ROOT).replace("-", "");
            for (Member.Relationship rel : Member.Relationship.values()) {
                if (cleanCard.matches(".*" + rel.getCardCode() + "[0-9]*$")) {
                    relationship = rel;
                    break;
                }
            }

            String baseCardNumber = cardNumberGeneratorService.extractBaseCardNumber(excelCardNumber);
            if (baseCardNumber != null) {
                // 1) كاش الدفعة (الرقم المُولَّد في DB)
                parent = sessionPrincipals.get(baseCardNumber);
                // 2) DB بالرقم المستخرج من بطاقة التابع
                if (parent == null) {
                    parent = memberRepository.findByCardNumber(baseCardNumber).orElse(null);
                }
                // 3) DB مع تجاهل الـ hyphens (للأرقام القديمة)
                if (parent == null) {
                    parent = memberRepository.findByCardNumberIgnoreHyphens(baseCardNumber).orElse(null);
                }
            }

            // 4) احتياطي أخير: استخدام lastSeenPrincipal (الرئيسي السابق بالترتيب في الملف)
            if (parent == null && lastSeenPrincipal != null) {
                log.warn("[MemberImport] لم يُعثر على الرئيسي برقم '{}' للتابع '{}' — سيُستخدم lastSeenPrincipal: '{}/{}'",
                        baseCardNumber, fullName, lastSeenPrincipal.getFullName(), lastSeenPrincipal.getCardNumber());
                parent = lastSeenPrincipal;
            }

            if (parent == null) {
                errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "card_number",
                        "لم يتم العثور على العضو الرئيسي للتابع (رقم البطاقة الأساسي غير موجود أو لم يسبقه سجل رئيسي)",
                        "Principal member not found for dependent (invalid base card and no preceding principal row)",
                        excelCardNumber, fullName));
                hasErrors = true;
            }
            if (relationship == null) {
                errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "card_number",
                        "تعذر استخراج صلة القرابة من رقم البطاقة",
                        "Could not extract relationship from card number",
                        excelCardNumber, fullName));
                hasErrors = true;
            }
        }

        if (hasErrors) {
            return null;
        }

        Member member = Member.builder()
                .fullName(fullName.trim())
                .employer(employer)
                .cardNumber(excelCardNumber)
                .birthDate(birthDate)
                .status(MemberStatus.ACTIVE)
                .build();
        
        if (isDependent) {
            member.setRelationship(relationship);
            member.setParent(parent);
        }

        return member;
    }

    private Member.Relationship parseRelationship(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Member.Relationship.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // Try Arabic aliases
        }

        String normText = normalizeText(value);
        if (normText.contains("زوجه") || normText.contains("زوجة")) {
            return Member.Relationship.WIFE;
        }
        if (normText.equals("زوج")) {
            Member.Gender inferred = GenderInferenceUtil.inferGender(value);
            if (inferred == Member.Gender.FEMALE) {
                return Member.Relationship.WIFE;
            }
            return Member.Relationship.HUSBAND;
        }
        if (normText.contains("ابن") && !normText.contains("ابنه")) {
            return Member.Relationship.SON;
        }
        if (normText.contains("ابنه") || normText.contains("بنت")) {
            return Member.Relationship.DAUGHTER;
        }
        if (normText.equals("اب")) {
            return Member.Relationship.FATHER;
        }
        if (normText.equals("ام")) {
            return Member.Relationship.MOTHER;
        }
        return null;
    }

    private String relationshipAr(Member.Relationship relationship) {
        return switch (relationship) {
            case WIFE -> "زوجة";
            case HUSBAND -> "زوج";
            case SON -> "ابن";
            case DAUGHTER -> "ابنة";
            case FATHER -> "أب";
            case MOTHER -> "أم";
            case BROTHER -> "أخ";
            case SISTER -> "أخت";
        };
    }

    private String normalizeMemberName(String fullName) {
        if (fullName == null) {
            return null;
        }
        return fullName.trim().replaceAll("\\s+", " ");
    }

    private String getCellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return parserService.getCellValueAsString(row.getCell(columnIndex));
    }

    private LocalDate getCellValueAsDate(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return parserService.getCellValueAsDate(row.getCell(columnIndex));
    }

    private String extractPrincipalCardNumber(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) return null;
        return cardNumberGeneratorService.extractBaseCardNumber(cardNo);
    }

    private ImportError createError(int rowNum, ErrorType type, String columnName,
            String messageAr, String messageEn, String value, String rowIdentifier) {
        return ImportError.builder()
                .rowNumber(rowNum + 1) // Excel 1-based row number
                .errorType(type)
                .columnName(columnName)
                .messageAr(messageAr)
                .messageEn(messageEn)
                .value(value)
                .rowIdentifier(rowIdentifier)
                .build();
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
    /**
     * يولّد رقم بطاقة فريد للعضو الأساسي مع مراعاة الأرقام المولّدة مسبقاً في نفس الدفعة.
     * يتحقق أولاً من usedCardNumbers (الذاكرة) ثم من DB لضمان عدم التكرار.
     */
    private String generateUniqueCardNumberWithCache(Member member, Set<String> usedCardNumbers) {
        if (member.getEmployer() == null || member.getEmployer().getCode() == null) {
            throw new IllegalStateException("Employer with code must be set before generating a card number");
        }

        String employerCode = member.getEmployer().getCode().trim().toUpperCase(java.util.Locale.ROOT);
        boolean hasEmployeeNumber = member.getEmployeeNumber() != null
                && !member.getEmployeeNumber().trim().isEmpty();

        if (hasEmployeeNumber) {
            String cardNumber = employerCode + member.getEmployeeNumber().trim();
            if (usedCardNumbers.contains(cardNumber) || memberRepository.existsByCardNumber(cardNumber)) {
                // الموظف موجود مسبقاً — نعيد نفس الرقم ليُعالَج كتحديث
                return cardNumber;
            }
            return cardNumber;
        }

        // مسار عشوائي: تكرار حتى إيجاد رقم فريد في الذاكرة وDB معاً
        final int MAX_ATTEMPTS = 100;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String randomNum = String.format("%08d",
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 100_000_000));
            String cardNumber = employerCode + randomNum;
            if (!usedCardNumbers.contains(cardNumber) && !memberRepository.existsByCardNumber(cardNumber)) {
                return cardNumber;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique card number for '" + member.getFullName() +
                        "' after " + MAX_ATTEMPTS + " attempts.");
    }
}

