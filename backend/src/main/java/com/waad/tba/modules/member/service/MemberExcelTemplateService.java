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
     * Import members from Excel file (CREATE-ONLY)
     */
    // @Transactional - Removed to allow partial success (commit valid rows
    // immediately)
    public ExcelImportResult importFromExcel(MultipartFile file) {
        log.info("[MemberImport] Starting import from file: {}", file.getOriginalFilename());

        ImportSummary summary = ImportSummary.builder().build();
        List<ImportError> errors = new ArrayList<>();

        try (Workbook workbook = parserService.openWorkbook(file)) {
            Sheet sheet = parserService.getDataSheet(workbook);

            // Find header row and column indices
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndices = findColumnIndices(headerRow);

            // Validate mandatory columns
            validateMandatoryColumns(columnIndices, errors);
            if (!errors.isEmpty()) {
                // Return immediately if template is invalid, do not attempt to read data
                return buildErrorResult(summary, errors, "Mandatory columns missing");
            }

            // Build employer lookup map
            Map<String, Employer> employerLookup = buildEmployerLookup();
            Map<Long, Set<String>> existingNamesCache = new HashMap<>();

            // Session cache for newly imported principals (to allow linking dependents in
            // same file)
            Map<String, Member> importedPrincipalsCache = new HashMap<>();

            Set<String> inFileKeys = new HashSet<>();
            List<Member> memberBatch = new ArrayList<>();
            final int BATCH_SIZE = 100;

            // Process data rows (skip header row at index 0, example row at index 1)
            int firstDataRow = 2;
            int lastRow = sheet.getLastRowNum();
            summary.setTotalRows(lastRow - firstDataRow + 1);

            log.info("[MemberImport] Processing {} rows", summary.getTotalRows());

            for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);

                if (parserService.isEmptyRow(row)) {
                    continue;
                }

                try {
                    Member member = parseAndCreateMember(row, rowNum, columnIndices, employerLookup,
                            importedPrincipalsCache, errors);

                    if (member != null) {
                        String fullNameLower = member.getFullName().trim().toLowerCase();
                        Long employerId = member.getEmployer().getId();
                        String duplicateKey = fullNameLower + "::" + employerId;

                        // 1. Check for duplicates within the same Excel file
                        if (inFileKeys.contains(duplicateKey)) {
                            summary.setSkipped(summary.getSkipped() + 1);
                            continue;
                        }

                        // 2. Check for duplicates against existing members in DB (optimized with
                        // lazy-loaded cache)
                        Set<String> existingNames = existingNamesCache.computeIfAbsent(employerId,
                                id -> new HashSet<>(memberRepository.findActiveFullNamesByEmployerId(id)));

                        if (existingNames.contains(fullNameLower)) {
                            summary.setSkipped(summary.getSkipped() + 1);
                            continue;
                        }

                        // 3. Mark as unique and prepare for saving
                        inFileKeys.add(duplicateKey);

                        // 4. Handle IDs and Card Numbers
                        if (member.isPrincipal()) {
                            // If card number provided in Excel, use it. Otherwise generate.
                            if (member.getCardNumber() == null || member.getCardNumber().isBlank()) {
                                member.setCardNumber(cardNumberGeneratorService.generateUniqueForPrincipal(member));
                            }
                            member.setBarcode(barcodeGeneratorService.generateUniqueBarcodeForPrincipal());

                            // Add to session cache for dependents to find
                            importedPrincipalsCache.put(member.getCardNumber(), member);
                        } else {
                            // Dependent ID generation logic
                            if (member.getCardNumber() == null || member.getCardNumber().isBlank()) {
                                member.setCardNumber(cardNumberGeneratorService.generateForDependent(member.getParent(),
                                        member.getRelationship()));
                            }
                        }

                        memberBatch.add(member);
                        summary.setCreated(summary.getCreated() + 1);

                        // 5. Batch save to improve performance
                        if (memberBatch.size() >= BATCH_SIZE) {
                            memberRepository.saveAll(memberBatch);
                            memberBatch.clear();
                            log.info("[MemberImport] Batch saved {} members", BATCH_SIZE);
                        }
                    } else {
                        summary.setRejected(summary.getRejected() + 1);
                    }

                } catch (Exception e) {
                    log.error("[MemberImport] Error processing row {}: {}", rowNum, e.getMessage());
                    errors.add(ImportError.builder()
                            .rowNumber(rowNum - 1)
                            .errorType(ErrorType.PROCESSING_ERROR)
                            .messageAr("خطأ في معالجة الصف: " + e.getMessage())
                            .messageEn("Error processing row: " + e.getMessage())
                            .build());
                    summary.setFailed(summary.getFailed() + 1);
                }
            }

            // Save remaining members in the last batch
            if (!memberBatch.isEmpty()) {
                memberRepository.saveAll(memberBatch);
                log.info("[MemberImport] Final batch saved {} members", memberBatch.size());
            }

            String messageAr = String.format("تم إنشاء %d عضو، تم تخطي %d، فشل %d",
                    summary.getCreated(), summary.getSkipped(), summary.getRejected() + summary.getFailed());
            String messageEn = String.format("Created %d members, skipped %d, failed %d",
                    summary.getCreated(), summary.getSkipped(), summary.getRejected() + summary.getFailed());

            log.info("[MemberImport] Import completed: {}", messageEn);

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
        // MEMBER TYPE IDENTIFICATION - IMPROVED
        // ═══════════════════════════════════════════════════════════════════════════

        // A row is a dependent if:
        // 1. It has a relationship specified
        // 2. OR it specifies a principal card number
        // 3. OR it lacks an employer name (Principals in this system MUST belong to an
        // employer)

        boolean dependentRow = hasRelationship || hasPrincipalCard || !hasEmployerName;

        // If it's a dependent but lacks a relationship, default to a placeholder or
        // fail
        // In some cases, we might want to default to SON/DAUGHTER if unknown but it's
        // better to keep it null and let validator catch it if needed

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
