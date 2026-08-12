package com.waad.tba.modules.member.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto.EmployerOptionDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto.BenefitPolicyOptionDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto.ImportValidationErrorDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto.MemberImportRowDto;
import com.waad.tba.modules.member.dto.MemberImportResultDto;
import com.waad.tba.modules.member.dto.MemberImportResultDto.ImportErrorDetailDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.Member.Relationship;
import com.waad.tba.modules.member.entity.MemberImportError;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberAttributeRepository;
import com.waad.tba.modules.member.repository.MemberImportErrorRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for importing members from Excel files.
 * Orchestrates the import process using dedicated components for parsing, mapping, and processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberExcelImportService {

    private static final int BATCH_SIZE = 100;

    private final MemberRepository memberRepository;
    private final MemberAttributeRepository memberAttributeRepository;
    private final MemberImportLogRepository importLogRepository;
    private final MemberImportErrorRepository importErrorRepository;
    private final EmployerRepository employerRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    
    private final MemberImportParser parser;
    private final MemberImportMapper mapper;
    private final MemberImportRowProcessor rowProcessor;
    private final BarcodeGeneratorService barcodeGeneratorService;
    private final MemberImportAuditRecorder auditRecorder;

    private final VisitRepository visitRepository;
    private final ClaimRepository claimRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;

    public MemberImportPreviewDto parseAndPreview(MultipartFile file) throws Exception {
        return parseAndPreview(file, null, null, null);
    }

    public MemberImportPreviewDto parseAndPreview(MultipartFile file, Map<String, String> customMappings)
            throws Exception {
        return parseAndPreview(file, customMappings, null, null);
    }

    public MemberImportPreviewDto parseAndPreview(
            MultipartFile file,
            Map<String, String> customMappings,
            Integer headerRowNumber) throws Exception {
        return parseAndPreview(file, customMappings, headerRowNumber, null);
    }

    public MemberImportPreviewDto parseAndPreview(
            MultipartFile file,
            Map<String, String> customMappings,
            Integer headerRowNumber,
            Long defaultEmployerId) throws Exception {
        
        log.info("📊 Parsing Excel file for preview: {} (custom mappings: {})",
                file.getOriginalFilename(), customMappings != null ? "yes" : "auto");

        Employer defaultEmployer = null;
        if (defaultEmployerId != null) {
            defaultEmployer = employerRepository.findById(defaultEmployerId)
                    .orElseThrow(() -> new BusinessRuleException("صاحب العمل غير موجود: " + defaultEmployerId));
        }

        String batchId = UUID.randomUUID().toString();
        List<MemberImportRowDto> previewRows = new ArrayList<>();
        List<ImportValidationErrorDto> validationErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> columnMappings = new LinkedHashMap<>();
        List<String> detectedColumns = new ArrayList<>();

        int newCount = 0;
        int warningCount = 0;
        int errorCount = 0;
        int validRows = 0;
        int invalidRows = 0;

        try (InputStream is = file.getInputStream();
                Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int physicalLastRow = sheet.getLastRowNum();

            int resolvedHeaderRowNumber = headerRowNumber != null
                    ? Math.max(0, headerRowNumber)
                    : mapper.detectHeaderRowNumber(sheet);

            Row headerRow = sheet.getRow(resolvedHeaderRowNumber);
            if (headerRow == null) throw new BusinessRuleException("Excel file has no header row");

            Map<Integer, String> columnIndexToName = new HashMap<>();
            Map<String, Integer> fieldToColumnIndex = new HashMap<>();

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String colName = parser.getCellStringValue(headerRow.getCell(i));
                if (colName == null) colName = "";
                String cleanedColName = parser.cleanColumnName(colName);
                String normalizedColName = cleanedColName.toLowerCase();
                columnIndexToName.put(i, normalizedColName);
                detectedColumns.add(cleanedColName);
            }

            if (customMappings != null && !customMappings.isEmpty()) {
                for (Map.Entry<String, String> entry : customMappings.entrySet()) {
                    String excelColumn = entry.getKey().trim().toLowerCase();
                    String systemField = entry.getValue();
                    Integer columnIndex = mapper.findColumnIndexByName(excelColumn, columnIndexToName);
                    if (columnIndex != null) {
                        fieldToColumnIndex.put(systemField, columnIndex);
                        columnMappings.put(systemField, excelColumn);
                    }
                }
            } else {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    String colName = columnIndexToName.get(i);
                    mapper.mapColumnToField(colName, i, fieldToColumnIndex, columnMappings);
                }
            }

            int totalRows = Math.max(0, physicalLastRow - resolvedHeaderRowNumber);
            mapper.validateMandatoryColumns(fieldToColumnIndex, validationErrors);

            int previewLimit = Math.min(totalRows, 50);
            Set<String> seenCardNumbers = new HashSet<>();

            for (int rowIndex = resolvedHeaderRowNumber + 1; rowIndex <= physicalLastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || parser.isEmptyRow(row)) continue;

                int displayRowNumber = rowIndex - resolvedHeaderRowNumber;
                MemberImportRowDto rowDto = rowProcessor.parseRowForPreview(row, displayRowNumber, fieldToColumnIndex,
                        validationErrors, seenCardNumbers, defaultEmployer);

                if ("ERROR".equals(rowDto.getStatus())) {
                    errorCount++;
                    invalidRows++;
                } else {
                    newCount++;
                    validRows++;
                    if ("WARNING".equals(rowDto.getStatus())) warningCount++;
                }

                if (previewRows.size() < previewLimit) previewRows.add(rowDto);
            }

            int importableCount = newCount;
            if (totalRows > previewLimit) warnings.add(String.format("عرض أول %d صف من إجمالي %d صف", previewLimit, totalRows));
            if (warningCount > 0) warnings.add(String.format("%d صف بها تحذيرات - ستُستورد مع ملاحظات", warningCount));
            if (errorCount > 0) warnings.add(String.format("%d صف بها أخطاء - سيتم تخطيها", errorCount));
            if (importableCount == 0) warnings.add("لا يوجد صفوف صالحة للاستيراد");

            return MemberImportPreviewDto.builder()
                    .batchId(batchId).fileName(file.getOriginalFilename()).totalRows(totalRows)
                    .validRows(validRows).invalidRows(invalidRows).newCount(newCount).updateCount(0)
                    .warningCount(warningCount).errorCount(errorCount).detectedColumns(detectedColumns)
                    .columnMappings(columnMappings).previewRows(previewRows).validationErrors(validationErrors)
                    .errors(validationErrors).canProceed(importableCount > 0).matchKeyUsed("CARD_NUMBER").warnings(warnings)
                    .availableEmployers(loadEmployerOptions())
                    .availableBenefitPolicies(loadPolicyOptions())
                    .build();
        }
    }

    public MemberImportResultDto executeImport(MultipartFile file, String batchId, Long employerId, Long benefitPolicyId) throws Exception {
        return executeImport(file, batchId, employerId, benefitPolicyId, null, false);
    }

    public MemberImportResultDto executeImport(MultipartFile file, String batchId, Long employerId, Long benefitPolicyId, Integer headerRowNumber) throws Exception {
        return executeImport(file, batchId, employerId, benefitPolicyId, headerRowNumber, false);
    }

    @org.springframework.transaction.annotation.Transactional
    public void clearOldMembersForFile(MultipartFile file, Long employerId, Integer headerRowNumber) {
        assertCurrentUserCanClearOldMembers();

        if (employerId != null) {
            clearOldMembers(employerId);
        } else {
            Set<Long> detectedEmployerIds = new HashSet<>();
            try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                int physicalLastRow = sheet.getLastRowNum();
                int resolvedHeaderRowNumber = headerRowNumber != null ? Math.max(0, headerRowNumber) : mapper.detectHeaderRowNumber(sheet);
                Row headerRow = sheet.getRow(resolvedHeaderRowNumber);
                if (headerRow != null) {
                    Map<String, Integer> fieldToColumnIndex = new HashMap<>();
                    for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                        String colName = parser.cleanColumnName(parser.getCellStringValue(headerRow.getCell(i))).toLowerCase();
                        mapper.mapColumnToField(colName, i, fieldToColumnIndex, new HashMap<>());
                    }
                    for (int rowIndex = resolvedHeaderRowNumber + 1; rowIndex <= physicalLastRow; rowIndex++) {
                        Row row = sheet.getRow(rowIndex);
                        if (row == null || parser.isEmptyRow(row)) continue;
                        try {
                            Employer rowEmployer = rowProcessor.resolveEmployerForRow(row, rowIndex - resolvedHeaderRowNumber, fieldToColumnIndex, null);
                            if (rowEmployer != null) {
                                detectedEmployerIds.add(rowEmployer.getId());
                            }
                        } catch (Exception e) {
                            // Ignore row parsing errors during scan
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error scanning file for employers to clear", e);
            }
            for (Long empId : detectedEmployerIds) {
                clearOldMembers(empId);
            }
        }
    }

    /**
     * Executes the import as ONE atomic write: PASS structure is a single
     * scan (this pipeline doesn't split principals/dependents into two
     * passes the way MemberExcelTemplateService does, but the same rule
     * applies) inside one @Transactional method, and never returns a
     * success DTO built from counts that didn't actually commit.
     *
     * Row-level failures are split into two different kinds, handled two
     * different ways:
     * - Parsing/business errors (processRowForImport rejecting a row, a
     *   malformed card number, etc.) are EXPECTED per-row outcomes: caught
     *   here, recorded via auditRecorder.recordRowError (its own
     *   REQUIRES_NEW transaction, so the diagnostic survives even if
     *   something else later aborts the whole batch), and the loop moves on
     *   to the next row without touching anything already buffered.
     * - A persistence failure from memberRepository.save/saveAll is a
     *   TECHNICAL failure (e.g. a unique constraint collision) and is NOT
     *   caught here -- it propagates out of this @Transactional method,
     *   rolling back every member this batch has written so far, exactly as
     *   required: a technical save failure cancels the whole batch, not just
     *   the offending row.
     *
     * The import log's COMPLETED finalization happens as the LAST statement
     * of this same transaction (not via the audit recorder's REQUIRES_NEW
     * path) so a successful import and its "done" record commit together --
     * see MemberImportAuditRecorder's Javadoc for why splitting that
     * specific write out would reopen a duplicate-import race.
     */
    @org.springframework.transaction.annotation.Transactional
    public MemberImportResultDto executeImport(MultipartFile file, String batchId, Long employerId, Long benefitPolicyId, Integer headerRowNumber, Boolean clearOldMembers) throws Exception {
        log.info("📥 Executing member import: batchId={}, file={}, employer={}, policy={}, clearOldMembers={}", batchId, file.getOriginalFilename(), employerId, benefitPolicyId, clearOldMembers);

        if (Boolean.TRUE.equals(clearOldMembers)) {
            clearOldMembersForFile(file, employerId, headerRowNumber);
        }

        MemberImportPreviewDto previewGuard = parseAndPreview(file, null, headerRowNumber, employerId);
        if (previewGuard.getValidRows() <= 0) throw new BusinessRuleException("لا يوجد صفوف صالحة للاستيراد");

        byte[] fileBytes = file.getBytes();
        String fileHash = sha256Hex(fileBytes);

        // Idempotency fast-path: this exact file was already fully imported
        // for this employer -- don't re-process it.
        Optional<MemberImportLog> alreadyCompleted =
                importLogRepository.findByEmployerIdAndFileHashAndStatus(employerId, fileHash, MemberImportLog.ImportStatus.COMPLETED);
        if (alreadyCompleted.isPresent()) {
            MemberImportLog previous = alreadyCompleted.get();
            log.info("[MemberImport] Identical file already imported as batch {} -- returning idempotent result", previous.getImportBatchId());
            return MemberImportResultDto.builder()
                    .batchId(previous.getImportBatchId()).status(previous.getStatus().name())
                    .totalProcessed(previous.getTotalRows() != null ? previous.getTotalRows() : 0)
                    .createdCount(previous.getCreatedCount() != null ? previous.getCreatedCount() : 0)
                    .updatedCount(0)
                    .skippedCount(previous.getSkippedCount() != null ? previous.getSkippedCount() : 0)
                    .errorCount(previous.getErrorCount() != null ? previous.getErrorCount() : 0)
                    .completedAt(previous.getCompletedAt())
                    .errors(new ArrayList<>())
                    .message("هذا الملف مطابق لملف مستورد سابقاً بالكامل (الدفعة " + previous.getImportBatchId() + ") - لم تُعَد المعالجة")
                    .build();
        }

        Employer defaultEmployer = employerId != null ? employerRepository.findById(employerId).orElseThrow(() -> new BusinessRuleException("صاحب العمل غير موجود")) : null;
        BenefitPolicy benefitPolicy = benefitPolicyId != null ? benefitPolicyRepository.findById(benefitPolicyId).orElseThrow(() -> new BusinessRuleException("وثيقة المنافع غير موجودة")) : null;

        User currentUser = authorizationService.getCurrentUser();
        Long importLogId = auditRecorder.markStarted(batchId, file.getOriginalFilename(), file.getSize(), fileHash,
                employerId, currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : null);

        importErrorRepository.deleteByImportLogId(importLogId);

        List<ImportErrorDetailDto> errors = new ArrayList<>();
        List<Member> memberBuffer = new ArrayList<>();
        int totalProcessed = 0, createdCount = 0, skippedCount = 0, errorCount = 0;

        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int physicalLastRow = sheet.getLastRowNum();
            int resolvedHeaderRowNumber = headerRowNumber != null ? Math.max(0, headerRowNumber) : mapper.detectHeaderRowNumber(sheet);
            int totalRows = Math.max(0, physicalLastRow - resolvedHeaderRowNumber);

            Row headerRow = sheet.getRow(resolvedHeaderRowNumber);
            Map<Integer, String> columnIndexToName = new HashMap<>();
            Map<String, Integer> fieldToColumnIndex = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String colName = parser.cleanColumnName(parser.getCellStringValue(headerRow.getCell(i))).toLowerCase();
                columnIndexToName.put(i, colName);
                mapper.mapColumnToField(colName, i, fieldToColumnIndex, new HashMap<>());
            }

            Map<String, Member> memberCache = loadExistingMembersForImport(sheet, resolvedHeaderRowNumber,
                    physicalLastRow, fieldToColumnIndex);

            for (int rowIndex = resolvedHeaderRowNumber + 1; rowIndex <= physicalLastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || parser.isEmptyRow(row)) { skippedCount++; continue; }
                totalProcessed++;
                int rowNum = rowIndex - resolvedHeaderRowNumber;

                Member member;
                Member parent;
                try {
                    String cardNumber = parser.getFieldValue(row, fieldToColumnIndex, "cardNumber");
                    CardInfo cardInfo = parseCardNumber(cardNumber);
                    Relationship relationship = cardInfo.relationship;
                    String parentCardNumber = cardInfo.parentCardNumber;

                    parent = null;
                    if (parentCardNumber != null) {
                        String parentCardKey = parentCardNumber.trim().toUpperCase();
                        parent = memberCache.get(parentCardKey);
                        if (parent == null) {
                            parent = createDummyParent(parentCardNumber, row, rowNum, fieldToColumnIndex, defaultEmployer, benefitPolicy);
                            memberCache.put(parentCardKey, parent);
                        }
                    }

                    Member existingMember = null;
                    if (cardNumber != null && !cardNumber.isBlank()) {
                        String cardKey = cardNumber.trim().toUpperCase();
                        existingMember = memberCache.get(cardKey);
                    }

                    member = rowProcessor.processRowForImport(row, rowNum, fieldToColumnIndex, defaultEmployer, benefitPolicy, parent, relationship, existingMember);
                } catch (Exception parseFailure) {
                    // Expected/business-level row failure -- record and move
                    // on. Nothing has been saved for this row yet, so no
                    // rollback is needed and no other row is affected.
                    errorCount++;
                    String rowJson = rowToJson(row, columnIndexToName);
                    auditRecorder.recordRowError(importLogId, rowNum, parseFailure.getMessage(), rowJson);
                    errors.add(ImportErrorDetailDto.builder().rowNumber(rowNum).errorType("SYSTEM").message(parseFailure.getMessage()).build());
                    continue;
                }

                // Persistence calls below are intentionally NOT wrapped in
                // try/catch: a technical failure here (e.g. a unique
                // constraint collision) must abort the entire transaction,
                // not just this row.
                if (parent == null) {
                    member = memberRepository.save(member);
                    if (member.getCardNumber() != null) {
                        memberCache.put(member.getCardNumber().trim().toUpperCase(), member);
                    }
                } else {
                    memberBuffer.add(member);
                    if (member.getCardNumber() != null) {
                        memberCache.put(member.getCardNumber().trim().toUpperCase(), member);
                    }
                }

                createdCount++;
                if (memberBuffer.size() >= BATCH_SIZE) {
                    memberRepository.saveAll(memberBuffer);
                    memberBuffer.clear();
                }
            }
            if (!memberBuffer.isEmpty()) memberRepository.saveAll(memberBuffer);

            // Force any deferred persistence failure to surface HERE, inside
            // this try block, before the import log is finalized as COMPLETED --
            // not after, at an implicit commit-time flush the caller never sees.
            memberRepository.flush();

            MemberImportLog importLog = importLogRepository.findById(importLogId)
                    .orElseThrow(() -> new IllegalStateException("Import log vanished mid-import: " + importLogId));
            importLog.setTotalRows(totalRows);
            importLog.setCreatedCount(createdCount);
            importLog.setErrorCount(errorCount);
            importLog.setEmployerId(employerId);
            importLog.setFileHash(fileHash);
            importLog.markCompleted();
            // Same transaction as the member writes above: if a concurrent
            // identical-file import already completed and grabbed the unique
            // (employerId, fileHash) slot, THIS statement fails here and rolls
            // back every member this transaction just wrote, together with it.
            importLogRepository.saveAndFlush(importLog);

            return MemberImportResultDto.builder()
                    .batchId(batchId).status(importLog.getStatus().name()).totalProcessed(totalProcessed)
                    .createdCount(createdCount).updatedCount(0).skippedCount(skippedCount).errorCount(errorCount)
                    .processingTimeMs(importLog.getProcessingTimeMs()).completedAt(importLog.getCompletedAt())
                    .successRate(totalProcessed > 0 ? (double) createdCount / totalProcessed * 100 : 0)
                    .errors(errors).message(String.format("تم استيراد %d عضو بنجاح، %d أخطاء", createdCount, errorCount))
                    .build();
        } catch (Exception e) {
            log.error("[MemberImport] فشل الحفظ، تراجع كامل عن الدفعة: {}", e.getMessage(), e);
            // REQUIRES_NEW: durably records the failure even though this
            // method's own transaction is about to roll back every member it
            // wrote.
            auditRecorder.markFailed(importLogId, e.getMessage());
            throw e;
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<EmployerOptionDto> loadEmployerOptions() {
        return employerRepository.findAll().stream()
                .map(e -> EmployerOptionDto.builder().id(e.getId()).code(e.getCode()).name(e.getName()).active(e.getActive()).build())
                .toList();
    }

    private List<BenefitPolicyOptionDto> loadPolicyOptions() {
        return benefitPolicyRepository.findAll().stream()
                .map(p -> BenefitPolicyOptionDto.builder().id(p.getId()).policyNumber(p.getPolicyCode()).name(p.getName())
                        .employerId(p.getEmployer() != null ? p.getEmployer().getId() : null)
                        .isActive(p.getStatus() == BenefitPolicy.BenefitPolicyStatus.ACTIVE).build())
                .toList();
    }

    private String rowToJson(Row row, Map<Integer, String> columnIndexToName) {
        Map<String, String> data = new HashMap<>();
        for (Map.Entry<Integer, String> entry : columnIndexToName.entrySet()) {
            String value = parser.getCellStringValue(row.getCell(entry.getKey()));
            if (value != null) data.put(entry.getValue(), value);
        }
        try { return objectMapper.writeValueAsString(data); } catch (JsonProcessingException e) { return "{}"; }
    }

    private void clearOldMembers(Long employerId) {
        log.info("🧹 Clearing old members for employerId={} who do not have financial movements", employerId);
        List<Member> allMembers = memberRepository.findByEmployerId(employerId);
        if (allMembers.isEmpty()) {
            return;
        }

        Set<Long> allMemberIds = new HashSet<>();
        Map<Long, Member> memberMap = new HashMap<>();
        for (Member m : allMembers) {
            allMemberIds.add(m.getId());
            memberMap.put(m.getId(), m);
        }

        // Fetch movements in bulk (3 fast queries)
        Set<Long> idsWithMovements = new HashSet<>();
        idsWithMovements.addAll(visitRepository.findMemberIdsWithVisits(allMemberIds));
        idsWithMovements.addAll(claimRepository.findMemberIdsWithClaims(allMemberIds));
        idsWithMovements.addAll(preAuthorizationRepository.findMemberIdsWithPreAuths(allMemberIds));

        // Determine which members to keep:
        // A member is kept if they have movements, or if they are a principal and have a dependent with movements.
        Set<Long> keepIds = new HashSet<>();
        for (Long id : idsWithMovements) {
            keepIds.add(id);
            Member m = memberMap.get(id);
            if (m != null && m.getParent() != null) {
                keepIds.add(m.getParent().getId());
            }
        }

        // Members to delete are those not in keepIds
        Set<Long> memberIdsToDelete = new HashSet<>(allMemberIds);
        memberIdsToDelete.removeAll(keepIds);

        if (memberIdsToDelete.isEmpty()) {
            return;
        }


        // Separate dependents and principals to delete
        List<Long> dependentsToDelete = new ArrayList<>();
        List<Long> principalsToDelete = new ArrayList<>();

        for (Long id : memberIdsToDelete) {
            Member m = memberMap.get(id);
            if (m != null) {
                if (m.isDependent()) {
                    dependentsToDelete.add(id);
                } else {
                    principalsToDelete.add(id);
                }
            }
        }

        // Delete member attributes for all members to delete in bulk to prevent FK issues
        memberAttributeRepository.deleteByMemberIdIn(memberIdsToDelete);

        // Delete dependents first to avoid parent dependency constraints
        if (!dependentsToDelete.isEmpty()) {
            memberRepository.deleteMembersByIds(dependentsToDelete);
            log.info("🧹 Deleted {} dependent members in bulk", dependentsToDelete.size());
        }

        // Delete principals in bulk
        if (!principalsToDelete.isEmpty()) {
            memberRepository.deleteMembersByIds(principalsToDelete);
            log.info("🧹 Deleted {} principal members in bulk", principalsToDelete.size());
        }
    }

    private void assertCurrentUserCanClearOldMembers() {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null || !"SUPER_ADMIN".equalsIgnoreCase(currentUser.getUserType())) {
            throw new AccessDeniedException("Only SUPER_ADMIN can clear old members during import");
        }
    }

    private Map<String, Member> loadExistingMembersForImport(
            Sheet sheet,
            int resolvedHeaderRowNumber,
            int physicalLastRow,
            Map<String, Integer> fieldToColumnIndex) {

        Set<String> relevantCardNumbers = new HashSet<>();
        for (int rowIndex = resolvedHeaderRowNumber + 1; rowIndex <= physicalLastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || parser.isEmptyRow(row)) {
                continue;
            }

            String cardNumber = parser.getFieldValue(row, fieldToColumnIndex, "cardNumber");
            String normalizedCard = normalizeCardKey(cardNumber);
            if (normalizedCard != null) {
                relevantCardNumbers.add(normalizedCard);

                CardInfo cardInfo = parseCardNumber(cardNumber);
                String normalizedParentCard = normalizeCardKey(cardInfo.parentCardNumber);
                if (normalizedParentCard != null) {
                    relevantCardNumbers.add(normalizedParentCard);
                }
            }
        }

        Map<String, Member> memberCache = new HashMap<>();
        if (relevantCardNumbers.isEmpty()) {
            return memberCache;
        }

        List<String> cards = new ArrayList<>(relevantCardNumbers);
        final int chunkSize = 1000;
        for (int start = 0; start < cards.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, cards.size());
            List<Member> existingMembers = memberRepository.findByCardNumberUpperIn(cards.subList(start, end));
            for (Member member : existingMembers) {
                String key = normalizeCardKey(member.getCardNumber());
                if (key != null) {
                    memberCache.put(key, member);
                }
            }
        }

        log.info("📦 Preloaded {} existing members for import from {} relevant card numbers",
                memberCache.size(), relevantCardNumbers.size());
        return memberCache;
    }

    private String normalizeCardKey(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }
        return cardNumber.trim().toUpperCase();
    }

    public static class CardInfo {
        public final Relationship relationship;
        public final String parentCardNumber;

        public CardInfo(Relationship relationship, String parentCardNumber) {
            this.relationship = relationship;
            this.parentCardNumber = parentCardNumber;
        }
    }

    public CardInfo parseCardNumber(String card) {
        if (card == null || card.isBlank()) {
            return new CardInfo(null, null);
        }
        card = card.trim();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(.*?[0-9]+)-?([WwHhSsDdMmFfBbZz]|[Ss][Rr])(\\d*)$");
        java.util.regex.Matcher matcher = pattern.matcher(card);
        if (matcher.find()) {
            String parentCard = matcher.group(1);
            String relChar = matcher.group(2).toUpperCase();
            Relationship rel = switch (relChar) {
                case "W" -> Relationship.WIFE;
                case "H" -> Relationship.HUSBAND;
                case "S" -> Relationship.SON;
                case "D" -> Relationship.DAUGHTER;
                case "M" -> Relationship.MOTHER;
                case "F" -> Relationship.FATHER;
                default -> null;
            };
            return new CardInfo(rel, parentCard);
        }
        return new CardInfo(null, null);
    }


    private Member createDummyParent(String parentCardNumber, Row row, int rowNum,
                                     Map<String, Integer> fieldToColumnIndex, Employer defaultEmployer,
                                     BenefitPolicy benefitPolicy) {
        Employer rowEmployer = rowProcessor.resolveEmployerForRow(row, rowNum, fieldToColumnIndex, defaultEmployer);
        BenefitPolicy resolvedPolicy = rowProcessor.resolveAndValidatePolicy(benefitPolicy, rowEmployer, rowNum);
        String policyNumber = parser.getFieldValue(row, fieldToColumnIndex, "policyNumber");
        
        Member parent = Member.builder()
                .cardNumber(parentCardNumber)
                .fullName("حامل بطاقة " + parentCardNumber.replace("JFZ2025", ""))
                .employer(rowEmployer)
                .benefitPolicy(resolvedPolicy)
                .policyNumber(policyNumber != null && !policyNumber.isBlank()
                        ? policyNumber
                        : resolvedPolicy.getPolicyCode())
                .status(Member.MemberStatus.ACTIVE)
                .cardStatus(Member.CardStatus.ACTIVE)
                .active(true)
                .barcode(parentCardNumber)
                .build();
        return memberRepository.save(parent);
    }
}
