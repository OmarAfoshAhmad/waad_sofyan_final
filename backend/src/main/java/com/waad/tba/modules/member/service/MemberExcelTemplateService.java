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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
                // Mandatory Fields
                ExcelTemplateColumn.builder()
                        .name("full_name")
                        .nameAr("الاسم الكامل")
                        .type(ColumnType.TEXT)
                        .required(true)
                        .example("أحمد محمد علي")
                        .description("Full name in Arabic (mandatory)")
                        .descriptionAr("الاسم الكامل بالعربية (إجباري)")
                        .width(25)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("employer")
                        .nameAr("جهة العمل")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("شركة النفط الليبية")
                        .description("Required for principals only (must match lookup sheet)")
                        .descriptionAr("إجباري للعضو الرئيسي فقط (يجب أن يطابق ورقة البحث)")
                        .width(30)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("principal_card_number")
                        .nameAr("رقم بطاقة الرئيسي")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("000123")
                        .description("If provided with relationship, row is imported as a dependent")
                        .descriptionAr("عند إدخاله مع القرابة يتم استيراد الصف كتابع")
                        .width(20)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("relationship")
                        .nameAr("القرابة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("SON")
                        .description(
                                "Dependent relationship (WIFE, HUSBAND, SON, DAUGHTER, FATHER, MOTHER, BROTHER, SISTER)")
                        .descriptionAr("قرابة التابع (WIFE, HUSBAND, SON, DAUGHTER, FATHER, MOTHER, BROTHER, SISTER)")
                        .width(22)
                        .build(),

                ExcelTemplateColumn.builder()
                        .name("card_number")
                        .nameAr("رقم البطاقة")
                        .type(ColumnType.TEXT)
                        .required(false)
                        .example("001234")
                        .description("Member card number (optional, system will generate if empty)")
                        .descriptionAr("رقم بطاقة العضو (اختياري، سيقوم النظام بالتوليد إذا كان فارغاً)")
                        .width(20)
                        .build());
    }

    private List<ExcelLookupData> buildLookupSheets() {
        // Fetch all employers
        List<Employer> employers = employerRepository.findByActiveTrue();

        List<List<String>> employerData = employers.stream()
                .map(emp -> Arrays.<String>asList(
                        emp.getId().toString(),
                        emp.getName() != null ? emp.getName() : ""))
                .collect(Collectors.toList());

        ExcelLookupData employersLookup = ExcelLookupData.builder()
                .sheetName("Employers")
                .sheetNameAr("جهات العمل")
                .headers(Arrays.asList("ID", "Name"))
                .data(employerData)
                .description("List of valid employers - Use exact name from this sheet")
                .descriptionAr("قائمة جهات العمل الصالحة - استخدم الاسم المطابق تماماً من هذه الورقة")
                .build();

        List<List<String>> relationshipData = Arrays.stream(Member.Relationship.values())
                .map(r -> Arrays.asList(r.name(), relationshipAr(r)))
                .collect(Collectors.toList());

        ExcelLookupData relationshipsLookup = ExcelLookupData.builder()
                .sheetName("Relationships")
                .sheetNameAr("القرابة")
                .headers(Arrays.asList("Code", "Arabic"))
                .data(relationshipData)
                .description("Valid dependent relationship values")
                .descriptionAr("قيم القرابة المسموح بها للتابعين")
                .build();

        return List.of(employersLookup, relationshipsLookup);
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
    public ExcelImportResult importFromExcel(MultipartFile file) {
        log.info("[MemberImport] Starting import from file: {}", file.getOriginalFilename());

        ImportSummary summary = ImportSummary.builder().build();
        List<ImportError> errors = new ArrayList<>();

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
                        String pCard = normalizeCardNumber(
                                getCellValue(row, columnIndices.get("principal_card_number")));
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
            int pass1Processed = 0;

            log.info("[MemberImport] PASS-1 بدء — {} صف رئيسي للمعالجة", pass1Total);

            for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (parserService.isEmptyRow(row))
                    continue;

                // Skip dependent rows entirely in pass 1
                if (isDependentRow(row, columnIndices))
                    continue;

                try {
                    Member member = parseAndCreateMember(row, rowNum, columnIndices,
                            employerLookup, importedPrincipalsCache, errors);

                    if (member == null) {
                        summary.setRejected(summary.getRejected() + 1);
                        pass1Processed++;
                        continue;
                    }

                    String fullNameLower = member.getFullName().trim().toLowerCase();
                    Long employerId = member.getEmployer().getId();
                    String inFileKey = "P::" + employerId + "::" + fullNameLower;

                    if (inFileKeys.contains(inFileKey)) {
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setPrincipalsSkipped(summary.getPrincipalsSkipped() + 1);
                        pass1Processed++;
                        continue;
                    }

                    Set<String> existingNames = existingPrincipalNamesCache.computeIfAbsent(employerId,
                            id -> new HashSet<>(memberRepository.findActivePrincipalNamesByEmployerId(id)));
                    if (existingNames.contains(fullNameLower)) {
                        // Principal already in DB — load it via name+employer (reliable).
                        // Pre-pass card mapping was fragile; direct DB query is guaranteed.
                        inFileKeys.add(inFileKey);
                        memberRepository.findActivePrincipalByFullNameLowerAndEmployerId(fullNameLower, employerId)
                                .ifPresent(p -> importedPrincipalsCache.put(p.getCardNumber(), p));
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setPrincipalsSkipped(summary.getPrincipalsSkipped() + 1);
                        pass1Processed++;
                        continue;
                    }

                    inFileKeys.add(inFileKey);

                    // Assign card number: prefer the value dependents already reference
                    // (pre-pass), then fall back to auto-generate.
                    if (member.getCardNumber() == null || member.getCardNumber().isBlank()) {
                        String mappedCard = principalRowToCardNumber.get(rowNum);
                        if (mappedCard != null && !usedCardNumbers.contains(mappedCard)) {
                            member.setCardNumber(mappedCard);
                        } else {
                            member.setCardNumber(
                                    cardNumberGeneratorService.generateUniqueForPrincipal(member));
                        }
                    }
                    if (usedCardNumbers.contains(member.getCardNumber())) {
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setPrincipalsSkipped(summary.getPrincipalsSkipped() + 1);
                        pass1Processed++;
                        continue;
                    }

                    member.setBarcode(barcodeGeneratorService.generateUniqueBarcodeForPrincipal());
                    principalBatch.add(member);
                    pass1Processed++;

                    if (principalBatch.size() >= BATCH_SIZE) {
                        List<Member> saved = memberRepository.saveAll(principalBatch);
                        for (Member s : saved) {
                            usedCardNumbers.add(s.getCardNumber());
                            importedPrincipalsCache.put(s.getCardNumber(), s);
                            // Keep cache fresh — newly saved principals are now in DB
                            existingPrincipalNamesCache
                                    .computeIfAbsent(s.getEmployer().getId(), k -> new HashSet<>())
                                    .add(s.getFullName().trim().toLowerCase());
                        }
                        summary.setCreated(summary.getCreated() + saved.size());
                        summary.setPrincipalsCreated(summary.getPrincipalsCreated() + saved.size());
                        principalBatch.clear();
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

            // Flush remaining principals
            if (!principalBatch.isEmpty()) {
                List<Member> saved = memberRepository.saveAll(principalBatch);
                for (Member s : saved) {
                    usedCardNumbers.add(s.getCardNumber());
                    importedPrincipalsCache.put(s.getCardNumber(), s);
                    existingPrincipalNamesCache
                            .computeIfAbsent(s.getEmployer().getId(), k -> new HashSet<>())
                            .add(s.getFullName().trim().toLowerCase());
                }
                summary.setCreated(summary.getCreated() + saved.size());
                summary.setPrincipalsCreated(summary.getPrincipalsCreated() + saved.size());
                principalBatch.clear();
            }

            log.info("[MemberImport] PASS-1 اكتمل — أُنشئ {} رئيسي، تُخطّي {} رئيسي",
                    summary.getPrincipalsCreated(), summary.getPrincipalsSkipped());

            // ══════════════════════════════════════════════════════════════
            // PASS 2 — DEPENDENTS ONLY
            // ══════════════════════════════════════════════════════════════
            List<Member> dependentBatch = new ArrayList<>();
            int pass2Processed = 0;
            // In-memory ordinal counter per (parentId::RELATIONSHIP) to avoid DB-count lag
            // during batching. generateForDependent queries countByParentIdAndRelationship
            // from DB, but pending batch members aren't saved yet → all same-type deps
            // under one parent get ordinal 1 → card collision → wrongly skipped.
            // Fix: seed from DB once per key, then increment in memory.
            Map<String, Integer> ordinalCounters = new HashMap<>();
            log.info("[MemberImport] PASS-2 بدء — {} صف تابع للمعالجة", pass2Total);

            for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (parserService.isEmptyRow(row))
                    continue;

                // Skip principal rows — already saved in pass 1
                if (!isDependentRow(row, columnIndices))
                    continue;

                try {
                    Member member = parseAndCreateMember(row, rowNum, columnIndices,
                            employerLookup, importedPrincipalsCache, errors);

                    if (member == null) {
                        summary.setRejected(summary.getRejected() + 1);
                        pass2Processed++;
                        continue;
                    }

                    String fullNameLower = member.getFullName().trim().toLowerCase();
                    Long employerId = member.getEmployer().getId();
                    Long parentId = member.getParent() != null ? member.getParent().getId() : null;
                    String inFileKey = "D::" + parentId + "::" + fullNameLower;

                    if (inFileKeys.contains(inFileKey)) {
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setDependentsSkipped(summary.getDependentsSkipped() + 1);
                        pass2Processed++;
                        continue;
                    }

                    // DB duplicate check — uses Object[] to avoid JPQL CONCAT type mismatch
                    Set<String> existingDepKeys = existingDependentKeysCache.computeIfAbsent(employerId,
                            this::buildDependentKeySet);
                    if (existingDepKeys.contains(parentId + "::" + fullNameLower)) {
                        inFileKeys.add(inFileKey);
                        summary.setSkipped(summary.getSkipped() + 1);
                        summary.setDependentsSkipped(summary.getDependentsSkipped() + 1);
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
                                + "-" + rel.getCardCode() + currentOrdinal);
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
                                    + "-" + rel.getCardCode() + ordinal;
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
            keys.add(parentId + "::" + name);
        }
        return keys;
    }

    private Map<String, Integer> findColumnIndices(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();

        indices.put("full_name", parserService.findColumnIndex(headerRow,
                "full_name", "الاسم الكامل", "full name", "اسم الموظف"));
        indices.put("employer", parserService.findColumnIndex(headerRow,
                "employer", "جهة العمل", "emp name", "company"));
        indices.put("principal_card_number", parserService.findColumnIndex(headerRow,
                "principal_card_number", "رقم بطاقة الرئيسي", "principal card", "parent card"));
        indices.put("relationship", parserService.findColumnIndex(headerRow,
                "relationship", "القرابة", "rel type", "صلة القرابة"));
        indices.put("card_number", parserService.findColumnIndex(headerRow,
                "card_number", "رقم البطاقة", "member card", "معرّف البطاقة"));

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
        return value.trim().toUpperCase(Locale.ROOT);
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

    /**
     * Quick check whether a row represents a dependent (without full parsing).
     * Same logic as parseAndCreateMember's type identification.
     */
    private boolean isDependentRow(Row row, Map<String, Integer> columnIndices) {
        String employerName = getCellValue(row, columnIndices.get("employer"));
        String principalCard = normalizeCardNumber(getCellValue(row, columnIndices.get("principal_card_number")));
        String relationship = normalizeText(getCellValue(row, columnIndices.get("relationship")));

        boolean hasPrincipalCard = principalCard != null && !principalCard.isBlank();
        boolean hasRelationship = relationship != null && !relationship.isBlank();

        // A row is dependent ONLY if it explicitly references a principal card
        // or declares a relationship. Missing employer does NOT make it a
        // dependent — it's a principal with a validation error.
        boolean dependent = hasPrincipalCard || hasRelationship;

        // "موظف" / "SELF" / "PRINCIPAL" means it's actually a principal
        if (hasRelationship && (relationship.equalsIgnoreCase("موظف") ||
                relationship.equalsIgnoreCase("self") ||
                relationship.equalsIgnoreCase("principal"))) {
            dependent = false;
        }
        return dependent;
    }

    private Member parseAndCreateMember(
            Row row,
            int rowNum,
            Map<String, Integer> columnIndices,
            Map<String, Employer> employerLookup,
            Map<String, Member> sessionPrincipals,
            List<ImportError> errors) {
        // Extract values
        String fullName = normalizeMemberName(getCellValue(row, columnIndices.get("full_name")));
        String employerName = getCellValue(row, columnIndices.get("employer"));
        String principalCardNumber = normalizeCardNumber(getCellValue(row, columnIndices.get("principal_card_number")));
        String relationshipValue = normalizeText(getCellValue(row, columnIndices.get("relationship")));
        String excelCardNumber = normalizeCardNumber(getCellValue(row, columnIndices.get("card_number")));

        boolean hasPrincipalCard = principalCardNumber != null && !principalCardNumber.isBlank();
        boolean hasRelationship = relationshipValue != null && !relationshipValue.isBlank();
        boolean hasEmployerName = employerName != null && !employerName.isBlank();

        // ═══════════════════════════════════════════════════════════════════════════
        // MEMBER TYPE IDENTIFICATION
        // ═══════════════════════════════════════════════════════════════════════════
        // A row is dependent ONLY if it explicitly references a principal card
        // or declares a relationship. Missing employer is a validation error
        // on a principal row — it must NOT change the classification.

        boolean dependentRow = hasPrincipalCard || hasRelationship;

        // Special Case: If it says "موظف" or "self" in relationship, it's actually a
        // principal
        if (hasRelationship && (relationshipValue.equalsIgnoreCase("موظف") ||
                relationshipValue.equalsIgnoreCase("SELF") ||
                relationshipValue.equalsIgnoreCase("PRINCIPAL"))) {
            dependentRow = false;
        }

        // Validate mandatory fields
        boolean hasErrors = false;

        if (fullName == null || fullName.trim().isEmpty()) {
            errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "full_name",
                    "الاسم الكامل مطلوب", "Full name is required", fullName, "Unknown"));
            hasErrors = true;
        }

        Member.Relationship relationship = null; // Moved this declaration here to be in scope for dependentRow logic

        if (dependentRow) {
            if (!hasPrincipalCard) {
                errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "principal_card_number",
                        "رقم بطاقة الرئيسي مطلوب لإضافة تابع", "Principal card number is required for dependent rows",
                        principalCardNumber, fullName));
                hasErrors = true;
            }
            if (!hasRelationship) {
                errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "relationship",
                        "حقل القرابة مطلوب لإضافة تابع", "Relationship is required for dependent rows",
                        relationshipValue, fullName));
                hasErrors = true;
            }
        } else {
            if (employerName == null || employerName.trim().isEmpty()) {
                errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "employer",
                        "جهة العمل مطلوبة للعضو الرئيسي", "Employer is required for principal rows", employerName,
                        fullName));
                hasErrors = true;
            }
        }

        // Removed validation for birth_date (Optional in V112)
        // Removed validation for gender (Optional in V112)

        Employer employer = null;
        Member principal = null;

        if (dependentRow) {
            if (hasPrincipalCard) {
                // Try session cache first
                principal = sessionPrincipals.get(principalCardNumber);

                // Then try DB
                if (principal == null) {
                    principal = memberRepository.findByCardNumber(principalCardNumber)
                            .orElse(null);
                }

                if (principal == null || !principal.isPrincipal() || !Boolean.TRUE.equals(principal.getActive())) {
                    errors.add(createError(rowNum, ErrorType.LOOKUP_FAILED, "principal_card_number",
                            "لم يتم العثور على عضو رئيسي صالح برقم البطاقة: " + principalCardNumber,
                            "Valid principal not found by card number: " + principalCardNumber,
                            principalCardNumber, fullName));
                    hasErrors = true;
                }
            }

            if (hasRelationship) {
                relationship = parseRelationship(relationshipValue);
                if (relationship == null) {
                    errors.add(createError(rowNum, ErrorType.INVALID_FORMAT, "relationship",
                            "قيمة القرابة غير صحيحة: " + relationshipValue,
                            "Invalid relationship value: " + relationshipValue,
                            relationshipValue, fullName));
                    hasErrors = true;
                }
            }

            if (principal != null) {
                employer = principal.getEmployer();
            }
        } else {
            // Principal row
            employer = findEmployerFuzzy(employerName, employerLookup);

            if (employer == null && employerName != null && !employerName.trim().isEmpty()) {
                errors.add(createError(rowNum, ErrorType.LOOKUP_FAILED, "employer",
                        "جهة العمل غير موجودة: " + employerName + ". تأكد من تطابق الاسم مع قائمة جهات العمل.",
                        "Employer not found: " + employerName + ". Please check the Employers lookup sheet.",
                        employerName, fullName));
                hasErrors = true;
            }
        }

        if (hasErrors) {
            return null;
        }

        Member member;

        if (dependentRow) {
            member = Member.builder()
                    .fullName(fullName.trim())
                    .employer(employer)
                    .parent(principal)
                    .relationship(relationship)
                    .cardNumber(excelCardNumber)
                    .status(MemberStatus.ACTIVE)
                    .build();
        } else {
            member = Member.builder()
                    .fullName(fullName.trim())
                    .employer(employer)
                    .cardNumber(excelCardNumber)
                    .status(MemberStatus.ACTIVE)
                    .build();
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

        return switch (normalizeText(value)) {
            case "زوجه" -> Member.Relationship.WIFE;
            case "زوج" -> Member.Relationship.HUSBAND;
            case "ابن" -> Member.Relationship.SON;
            case "ابنه", "بنت" -> Member.Relationship.DAUGHTER;
            case "اب" -> Member.Relationship.FATHER;
            case "ام" -> Member.Relationship.MOTHER;
            case "اخ" -> Member.Relationship.BROTHER;
            case "اخت" -> Member.Relationship.SISTER;
            default -> null;
        };
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
}
