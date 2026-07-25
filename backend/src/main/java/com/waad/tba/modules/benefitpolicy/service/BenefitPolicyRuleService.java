package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.dto.*;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Iterator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing Benefit Policy Rules.
 * Handles CRUD operations and coverage lookups for claims/eligibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BenefitPolicyRuleService {

    private final BenefitPolicyRuleRepository ruleRepository;
    private final BenefitPolicyRepository policyRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final CoverageDecisionService coverageDecisionService;
    private final jakarta.persistence.EntityManager em;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all rules for a policy
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyRuleResponseDto> findByPolicy(Long policyId) {
        validatePolicyExists(policyId);
        return ruleRepository.findByBenefitPolicyId(policyId)
                .stream()
                .map(BenefitPolicyRuleResponseDto::fromEntity)
                .toList();
    }

    /**
     * Find all rules for a policy (paginated)
     */
    @Transactional(readOnly = true)
    public Page<BenefitPolicyRuleResponseDto> findByPolicy(Long policyId, Pageable pageable) {
        validatePolicyExists(policyId);
        Page<BenefitPolicyRule> rulesPage = ruleRepository.findByBenefitPolicyId(policyId, pageable);
        List<BenefitPolicyRuleResponseDto> dtoList = rulesPage.getContent().stream()
                .map(BenefitPolicyRuleResponseDto::fromEntity)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, rulesPage.getTotalElements());
    }

    /**
     * Find active rules only for a policy
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyRuleResponseDto> findActiveByPolicy(Long policyId) {
        validatePolicyExists(policyId);
        return ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(policyId)
                .stream()
                .map(BenefitPolicyRuleResponseDto::fromEntity)
                .toList();
    }

    /**
     * Find a specific rule by ID
     */
    @Transactional(readOnly = true)
    public BenefitPolicyRuleResponseDto findById(Long ruleId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));
        return BenefitPolicyRuleResponseDto.fromEntity(rule);
    }

    /**
     * Find category-level rules for a policy
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyRuleResponseDto> findCategoryRules(Long policyId) {
        validatePolicyExists(policyId);
        return ruleRepository.findCategoryRulesForPolicy(policyId)
                .stream()
                .map(BenefitPolicyRuleResponseDto::fromEntity)
                .toList();
    }

    /**
     * Find service-level rules for a policy.
     * 
     * @deprecated Since V228 all rules are category-level. Always returns empty
     *             list.
     */
    @Transactional(readOnly = true)
    @Deprecated
    public List<BenefitPolicyRuleResponseDto> findServiceRules(Long policyId) {
        return java.util.Collections.emptyList();
    }

    /**
     * Find rules requiring pre-approval for a policy
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyRuleResponseDto> findPreApprovalRules(Long policyId) {
        validatePolicyExists(policyId);
        return ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndRequiresPreApprovalTrue(policyId)
                .stream()
                .map(BenefitPolicyRuleResponseDto::fromEntity)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COVERAGE LOOKUP (For Claims & Eligibility)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find the coverage rule for a specific service within a policy.
     * 
     * This is the main lookup method for claims processing:
     * 1. First checks for a direct service rule
     * 2. Falls back to category rule if no service rule exists
     * 3. Returns empty if not covered
     * 
     * @param policyId  The benefit policy ID
     * @param serviceId The medical service ID
     * @return The applicable rule, or empty if not covered
     */
    @Transactional(readOnly = true)
    public Optional<BenefitPolicyRuleResponseDto> findCoverageForService(Long policyId, Long serviceId) {
        return findCoverageForService(policyId, serviceId, null);
    }

    @Transactional(readOnly = true)
    public Optional<BenefitPolicyRuleResponseDto> findCoverageForService(Long policyId, Long serviceId,
            Long categoryOverrideId) {
        return findCoverageForService(policyId, serviceId, categoryOverrideId,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT);
    }

    @Transactional(readOnly = true)
    public void assertBelongsToPolicy(Long ruleId, Long policyId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessRuleException("قاعدة التغطية غير موجودة: " + ruleId));
        if (rule.getBenefitPolicy() == null || !policyId.equals(rule.getBenefitPolicy().getId())) {
            throw new BusinessRuleException("قاعدة التغطية لا تتبع وثيقة التغطية المحددة");
        }
    }

    @Transactional(readOnly = true)
    public Optional<BenefitPolicyRuleResponseDto> findCoverageForService(
            Long policyId,
            Long serviceId,
            Long serviceCategoryId,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType) {
        return coverageDecisionService.resolve(CoverageDecisionRequest.builder()
                .policyId(policyId).serviceId(serviceId).serviceCategoryId(serviceCategoryId)
                .encounterType(encounterType != null ? encounterType
                        : com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT)
                .serviceDate(java.time.LocalDate.now()).build()).appliedRuleOptional();
    }

    /**
     * Check if a service is covered under a policy.
     * A service is covered only when an active rule resolves for its category
     * and encounter context. Missing rules fail closed; the policy default is a
     * percentage inherited by an existing rule, never an implicit coverage rule.
     */
    @Transactional(readOnly = true)
    public boolean isServiceCovered(Long policyId, Long serviceId, Long categoryOverrideId) {
        return findCoverageForService(policyId, serviceId, categoryOverrideId).isPresent();
    }

    /**
     * Check if a service requires pre-approval under a policy
     */
    @Transactional(readOnly = true)
    public boolean requiresPreApproval(Long policyId, Long serviceId, Long categoryOverrideId) {
        return findCoverageForService(policyId, serviceId, categoryOverrideId)
                .map(BenefitPolicyRuleResponseDto::isRequiresPreApproval)
                .orElse(false);
    }

    /**
     * Get coverage percentage for a service under a policy.
     * Returns zero when no explicit contextual rule exists (fail closed).
     */
    @Transactional(readOnly = true)
    public int getCoveragePercent(Long policyId, Long serviceId, Long categoryOverrideId) {
        return findCoverageForService(policyId, serviceId, categoryOverrideId)
                .map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent)
                .orElse(0);
    }

    /**
     * Get the policy-level default coverage percent.
     */
    @Transactional(readOnly = true)
    public int getDefaultCoveragePercent(Long policyId) {
        return policyRepository.findById(policyId)
                .map(BenefitPolicy::getDefaultCoveragePercent)
                .orElse(0);
    }

    /**
     * Check if a member has exceeded usage limits for a service
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkUsageLimit(Long policyId, Long serviceId, Long categoryId, Long memberId,
            Integer year) {
        return checkUsageLimit(policyId, serviceId, categoryId, null, memberId, year, null);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkUsageLimit(Long policyId, Long serviceId, Long categoryId, Long memberId,
            Integer year, Long excludeClaimId) {
        return checkUsageLimit(policyId, serviceId, categoryId, null, memberId, year, excludeClaimId);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkUsageLimit(Long policyId, Long serviceId, Long categoryId,
            Long serviceCategoryId, Long memberId, Integer year, Long excludeClaimId) {

        return checkUsageLimit(policyId, serviceId, categoryId, serviceCategoryId, memberId, year,
                excludeClaimId, com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkUsageLimit(Long policyId, Long serviceId, Long categoryId,
            Long serviceCategoryId, Long memberId, Integer year, Long excludeClaimId,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType) {

        // Resolve usage rule using the same dual-key logic as coverage lookup:
        // categoryId=context override, serviceCategoryId=service intrinsic category.
        Long resolvedCategoryId = serviceCategoryId != null ? serviceCategoryId : categoryId;
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();
        java.time.LocalDate referenceDate = java.time.LocalDate.of(targetYear, 1, 1);
        var decision = coverageDecisionService.resolve(CoverageDecisionRequest.builder()
                .policyId(policyId).serviceId(serviceId).serviceCategoryId(resolvedCategoryId)
                .memberId(memberId).serviceDate(referenceDate).excludeClaimId(excludeClaimId)
                .encounterType(encounterType).build());
        if (!decision.covered()) {
            return java.util.Map.of("covered", false);
        }

        BenefitPolicyRuleResponseDto rule = decision.appliedRule();
        var limits = decision.limitsOrEmpty();
        if (limits.isEmpty()) {
            return java.util.Map.of("covered", true, "hasLimit", false, "ruleId", rule.getId());
        }

        var amountLimit = limits.stream().filter(limit -> limit.amountLimit() != null)
                .min(java.util.Comparator.comparing(CoverageLimitSnapshot::amountLimit))
                .orElse(null);
        var timesLimit = limits.stream().filter(limit -> limit.timesLimit() != null)
                .min(java.util.Comparator.comparing(CoverageLimitSnapshot::timesLimit))
                .orElse(null);
        var daysLimit = limits.stream().filter(limit -> limit.daysLimit() != null)
                .min(java.util.Comparator.comparing(CoverageLimitSnapshot::daysLimit))
                .orElse(null);
        boolean amountExceeded = limits.stream().anyMatch(limit -> limit.amountLimit() != null
                && limit.usedAmount().compareTo(limit.amountLimit()) >= 0);
        boolean timesExceeded = limits.stream().anyMatch(limit -> limit.timesLimit() != null
                && limit.usedTimes() >= limit.timesLimit());
        boolean daysExceeded = limits.stream().anyMatch(limit -> limit.daysLimit() != null
                && limit.usedDays() >= limit.daysLimit());

        java.util.Map<String, Object> usageMap = new java.util.HashMap<>();
        usageMap.put("covered", true);
        usageMap.put("hasLimit", true);
        usageMap.put("ruleId", rule.getId());
        usageMap.put("medicalCategoryId", rule.getMedicalCategoryId());
        usageMap.put("timesLimit", timesLimit == null ? null : timesLimit.timesLimit());
        usageMap.put("amountLimit", amountLimit == null ? null : amountLimit.amountLimit());
        usageMap.put("daysLimit", daysLimit == null ? null : daysLimit.daysLimit());
        usageMap.put("usedCount", timesLimit == null ? 0 : timesLimit.usedTimes());
        usageMap.put("usedAmount", amountLimit == null ? java.math.BigDecimal.ZERO : amountLimit.usedAmount());
        usageMap.put("usedDays", daysLimit == null ? 0 : daysLimit.usedDays());
        usageMap.put("exceeded", timesExceeded || amountExceeded || daysExceeded);
        usageMap.put("timesExceeded", timesExceeded);
        usageMap.put("amountExceeded", amountExceeded);
        usageMap.put("daysExceeded", daysExceeded);
        usageMap.put("limits", limits);
        return usageMap;
    }

    /**
     * Bulk check coverage for a list of items to avoid N+1 API calls from frontend.
     */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> checkBulkCoverage(Long policyId,
            BulkCoverageCheckDto request) {
        java.util.List<java.util.Map<String, Object>> responses = new java.util.ArrayList<>();

        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            return responses;
        }

        for (BulkCoverageCheckDto.BulkCoverageLineDto line : request.getLines()) {
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", line.getId());

            // Check coverage for this specific line
            Optional<BenefitPolicyRuleResponseDto> ruleOpt = findCoverageForService(
                    policyId, line.getServiceId(),
                    line.getServiceCategoryId() != null ? line.getServiceCategoryId() : line.getCategoryId(),
                    request.getEncounterType());

            boolean isCovered = ruleOpt.isPresent();
            boolean explicitlyNotCovered = ruleOpt.map(r -> !r.isActive()).orElse(false);

            int coveragePercent = ruleOpt.map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent)
                    .orElse(0);

            if (explicitlyNotCovered) {
                coveragePercent = 0;
            }

            boolean requiresPreApproval = ruleOpt.map(BenefitPolicyRuleResponseDto::isRequiresPreApproval)
                    .orElse(false);

            result.put("covered", isCovered);
            result.put("notCovered", explicitlyNotCovered || ruleOpt.isEmpty());
            result.put("coveragePercent", explicitlyNotCovered ? 0 : coveragePercent);
            result.put("requiresPreApproval", requiresPreApproval);

            java.util.Map<String, Object> usageDetails = null;

            if (request.getMemberId() != null) {
                // Check usage using the same line args
                java.util.Map<String, Object> usage = checkUsageLimit(policyId, line.getServiceId(),
                        line.getCategoryId(),
                        line.getServiceCategoryId(), request.getMemberId(), request.getYear(),
                        request.getExcludeClaimId());
                if (usage != null && Boolean.TRUE.equals(usage.get("hasLimit"))) {
                    usageDetails = usage;
                }
            }

            result.put("usageExceeded", usageDetails != null && Boolean.TRUE.equals(usageDetails.get("exceeded")));
            result.put("usageDetails", usageDetails);

            responses.add(result);
        }
        return responses;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new rule for a policy
     */
    public BenefitPolicyRuleResponseDto create(Long policyId, BenefitPolicyRuleCreateDto dto) {
        log.info("Creating rule for policy {} - category: {}",
                policyId, dto.getMedicalCategoryId());

        // Validate policy exists
        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicy", "id", policyId));

        // Validate category XOR service (exactly one must be set)
        validateTargetXor(dto.getMedicalCategoryId(), dto.getMedicalServiceId());

        // Build the rule
        BenefitPolicyRule rule = BenefitPolicyRule.builder()
                .benefitPolicy(policy)
                .encounterType(parseEncounterType(dto.getEncounterType()))
                .coveragePercent(dto.getCoveragePercent())
                .copayPercentage(dto.getCopayPercentage())
                .inheritanceEnabled(Boolean.TRUE.equals(dto.getInheritanceEnabled()))
                .priority(dto.getPriority() != null ? dto.getPriority() : 100)
                .waitingPeriodDays(dto.getWaitingPeriodDays() != null ? dto.getWaitingPeriodDays() : 0)
                .requiresPreApproval(dto.getRequiresPreApproval() != null ? dto.getRequiresPreApproval() : false)
                .notes(dto.getNotes())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        // Set category (service-level rules removed in V228)
        if (dto.getMedicalCategoryId() != null) {
            MedicalCategory category = categoryRepository.findById(dto.getMedicalCategoryId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("MedicalCategory", "id", dto.getMedicalCategoryId()));

            // Upsert behavior for same policy+category:
            // - active existing rule: update it
            // - soft-deleted existing rule: restore and update it
            Optional<BenefitPolicyRule> existingRuleOpt = ruleRepository
                    .findByBenefitPolicyIdAndMedicalCategoryIdAndEncounterType(
                            policyId, dto.getMedicalCategoryId(), parseEncounterType(dto.getEncounterType()));

            if (existingRuleOpt.isPresent()) {
                BenefitPolicyRule existingRule = existingRuleOpt.get();
                boolean wasActive = existingRule.isActive();

                existingRule.setCoveragePercent(dto.getCoveragePercent());
                existingRule.setCopayPercentage(dto.getCopayPercentage());
                existingRule.setInheritanceEnabled(Boolean.TRUE.equals(dto.getInheritanceEnabled()));
                existingRule.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
                existingRule.setWaitingPeriodDays(dto.getWaitingPeriodDays() != null ? dto.getWaitingPeriodDays() : 0);
                existingRule.setRequiresPreApproval(
                        dto.getRequiresPreApproval() != null ? dto.getRequiresPreApproval() : false);
                existingRule.setNotes(dto.getNotes());
                existingRule.setActive(dto.getActive() != null ? dto.getActive() : true);
                existingRule.setDeleted(false);

                BenefitPolicyRule restored = ruleRepository.save(existingRule);
                log.info("Upserted existing rule {} for policy {} (category: {}, wasActive: {})",
                        restored.getId(), policyId, dto.getMedicalCategoryId(), wasActive);
                return BenefitPolicyRuleResponseDto.fromEntity(restored);
            }

            rule.setMedicalCategory(category);
        }

        BenefitPolicyRule saved = ruleRepository.save(rule);
        log.info("Created rule {} for policy {}", saved.getId(), policyId);

        return BenefitPolicyRuleResponseDto.fromEntity(saved);
    }

    /**
     * Bulk create rules for a policy
     */
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXCEL IMPORT & EXPORT
    // ═══════════════════════════════════════════════════════════════════════════

    public byte[] generateImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Benefit Rules");
            
            // Header
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("اسم الخدمة الموحد (لا تقم بتعديله)");
            headerRow.createCell(1).setCellValue("نسبة التغطية (مثال: 100, 75, 0)");
            headerRow.createCell(2).setCellValue("السقوف للمرات");
            headerRow.createCell(3).setCellValue("المبالغ");

            // Data
            List<MedicalCategory> categories = categoryRepository.findByActiveTrue();
            int rowIdx = 1;
            for (MedicalCategory category : categories) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(category.getName());
                // Columns 1, 2, 3 left blank for the user to fill
            }

            sheet.setColumnWidth(0, 15000);
            sheet.setColumnWidth(1, 8000);
            sheet.setColumnWidth(2, 6000);
            sheet.setColumnWidth(3, 6000);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel template", e);
            throw new BusinessRuleException("فشل في توليد قالب الإكسل");
        }
    }

    public void importRulesFromExcel(Long policyId, MultipartFile file) {
        validatePolicyExists(policyId);
        if (file.isEmpty()) {
            throw new BusinessRuleException("الملف المرفوع فارغ");
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();
            
            int rowNumber = 0;
            while (rows.hasNext()) {
                Row currentRow = rows.next();
                
                // Skip header
                if (rowNumber == 0) {
                    rowNumber++;
                    continue;
                }
                
                Cell serviceNameCell = currentRow.getCell(0);
                if (serviceNameCell == null || serviceNameCell.getStringCellValue().trim().isEmpty()) {
                    continue; // Skip empty rows
                }
                String serviceName = serviceNameCell.getStringCellValue().trim();
                
                // Read coverage percent
                Integer coveragePercent = null;
                Cell coverageCell = currentRow.getCell(1);
                if (coverageCell != null) {
                    if (coverageCell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        coveragePercent = (int) coverageCell.getNumericCellValue();
                    } else if (coverageCell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        String covStr = coverageCell.getStringCellValue().replaceAll("[^\\d]", "");
                        if (!covStr.isEmpty()) {
                            coveragePercent = Integer.parseInt(covStr);
                        }
                    }
                }

                // Read times limit
                Integer timesLimit = null;
                Cell timesCell = currentRow.getCell(2);
                if (timesCell != null) {
                    if (timesCell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        timesLimit = (int) timesCell.getNumericCellValue();
                    } else if (timesCell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        String timesStr = timesCell.getStringCellValue().replaceAll("[^\\d]", "");
                        if (!timesStr.isEmpty()) {
                            timesLimit = Integer.parseInt(timesStr);
                        }
                    }
                }

                // Read amount limit
                BigDecimal amountLimit = null;
                Cell amountCell = currentRow.getCell(3);
                if (amountCell != null) {
                    if (amountCell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        amountLimit = BigDecimal.valueOf(amountCell.getNumericCellValue());
                    } else if (amountCell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        String amountStr = amountCell.getStringCellValue().replaceAll("[^\\d.]", "");
                        if (!amountStr.isEmpty()) {
                            try {
                                amountLimit = new BigDecimal(amountStr);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // Find Category by Name
                Optional<MedicalCategory> categoryOpt = categoryRepository.findFirstByName(serviceName);
                if (categoryOpt.isEmpty()) {
                    log.warn("Skipping unknown service name in Excel import: {}", serviceName);
                    continue; // Skip if not found
                }
                
                Long categoryId = categoryOpt.get().getId();

                // Build DTO and Create/Update Rule
                BenefitPolicyRuleCreateDto dto = BenefitPolicyRuleCreateDto.builder()
                        .medicalCategoryId(categoryId)
                        .coveragePercent(coveragePercent)
                        .active(true)
                        .requiresPreApproval(false)
                        .waitingPeriodDays(0)
                        .notes("مستورد من إكسل")
                        .build();
                
                create(policyId, dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            throw new BusinessRuleException("حدث خطأ أثناء قراءة ملف الإكسل. يرجى التأكد من أن الملف هو قالب سليم.");
        }
    }

    public List<BenefitPolicyRuleResponseDto> createBulk(Long policyId, List<BenefitPolicyRuleCreateDto> dtos) {
        log.info("Bulk creating {} rules for policy {}", dtos.size(), policyId);

        return dtos.stream()
                .map(dto -> create(policyId, dto))
                .toList();
    }

    /** Initialize one explicit rule per approved category/context pair. */
    public List<BenefitPolicyRuleResponseDto> initializeStandardRules(Long policyId) {
        log.info("Initializing approved category/context rules for policy {}", policyId);

        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicy", "id", policyId));

        List<BenefitPolicyRuleResponseDto> results = new java.util.ArrayList<>();

        for (MedicalCategory category : categoryRepository.findByActiveTrue()) {
          for (com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext categoryContext : category.getContexts()) {
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType =
                    com.waad.tba.modules.providercontract.enums.EncounterType.valueOf(categoryContext.name());
            Optional<BenefitPolicyRule> existingRuleOpt = ruleRepository
                    .findByBenefitPolicyIdAndMedicalCategoryIdAndEncounterType(
                            policyId, category.getId(), encounterType);

            if (existingRuleOpt.isPresent()) {
                BenefitPolicyRule existingRule = existingRuleOpt.get();
                boolean requiresUpdate = false;

                if (existingRule.isDeleted()) {
                    existingRule.setDeleted(false);
                    requiresUpdate = true;
                }
                if (!existingRule.isActive()) {
                    existingRule.setActive(true);
                    requiresUpdate = true;
                }
                Integer expectedCoverage = category.getCoveragePercent() != null
                        ? category.getCoveragePercent().intValue()
                        : policy.getDefaultCoveragePercent();
                if (existingRule.getCoveragePercent() == null
                        || !existingRule.getCoveragePercent().equals(expectedCoverage)) {
                    existingRule.setCoveragePercent(expectedCoverage);
                    requiresUpdate = true;
                }
                if (existingRule.isRequiresPreApproval()) {
                    existingRule.setRequiresPreApproval(false);
                    requiresUpdate = true;
                }
                if (existingRule.getWaitingPeriodDays() == null || existingRule.getWaitingPeriodDays() != 0) {
                    existingRule.setWaitingPeriodDays(0);
                    requiresUpdate = true;
                }
                if (existingRule.getNotes() == null || !existingRule.getNotes().contains("تم الإنشاء تلقائياً")) {
                    existingRule.setNotes("تم الإنشاء تلقائياً — القواعد القياسية");
                    requiresUpdate = true;
                }

                if (requiresUpdate) {
                    BenefitPolicyRule saved = ruleRepository.save(existingRule);
                    log.info("Updated approved rule {} for category {} / {}", saved.getId(), category.getCode(), encounterType);
                    results.add(BenefitPolicyRuleResponseDto.fromEntity(saved));
                } else {
                    results.add(BenefitPolicyRuleResponseDto.fromEntity(existingRule));
                }
                continue;
            }

            BenefitPolicyRuleCreateDto dto = BenefitPolicyRuleCreateDto.builder()
                    .medicalCategoryId(category.getId())
                    .encounterType(encounterType.name())
                    .coveragePercent(
                            category.getCoveragePercent() != null ? category.getCoveragePercent().intValue()
                                    : policy.getDefaultCoveragePercent())
                    .active(true)
                    .requiresPreApproval(false)
                    .waitingPeriodDays(0)
                    .notes("تم الإنشاء تلقائياً — قائمة التصنيفات المعتمدة")
                    .build();

            try {
                results.add(create(policyId, dto));
            } catch (Exception e) {
                log.error("Failed to initialize rule for category {} / {}: {}",
                        category.getCode(), encounterType, e.getMessage());
            }
          }
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update an existing rule
     * Note: Cannot change the target (category/service) after creation
     */
    public BenefitPolicyRuleResponseDto update(Long ruleId, BenefitPolicyRuleUpdateDto dto) {
        log.info("Updating rule {}", ruleId);

        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));

        // Update fields if provided
        // Rule-level caps were retired by the bucket cutover; buckets are the only limit source.
        rule.setCoveragePercent(dto.getCoveragePercent());
        rule.setCopayPercentage(dto.getCopayPercentage());
        rule.setNotes(dto.getNotes());

        if (dto.getEncounterType() != null) {
            rule.setEncounterType(parseEncounterType(dto.getEncounterType()));
        }
        if (dto.getInheritanceEnabled() != null) {
            rule.setInheritanceEnabled(dto.getInheritanceEnabled());
        }
        if (dto.getPriority() != null) {
            rule.setPriority(dto.getPriority());
        }

        if (dto.getWaitingPeriodDays() != null) {
            rule.setWaitingPeriodDays(dto.getWaitingPeriodDays());
        }
        if (dto.getRequiresPreApproval() != null) {
            rule.setRequiresPreApproval(dto.getRequiresPreApproval());
        }
        if (dto.getActive() != null) {
            rule.setActive(dto.getActive());
        }

        BenefitPolicyRule saved = ruleRepository.save(rule);
        log.info("Updated rule {}", ruleId);

        return BenefitPolicyRuleResponseDto.fromEntity(saved);
    }

    /**
     * Toggle rule active status
     */
    public BenefitPolicyRuleResponseDto toggleActive(Long ruleId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));

        if (rule.isDeleted()) {
            throw new BusinessRuleException("لا يمكن تغيير حالة قاعدة موجودة في سلة المحذوفات. قم بالاستعادة أولاً.");
        }

        rule.setActive(!rule.isActive());
        BenefitPolicyRule saved = ruleRepository.save(rule);

        log.info("Toggled rule {} active status to {}", ruleId, saved.isActive());
        return BenefitPolicyRuleResponseDto.fromEntity(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Delete a rule (soft delete by moving to trash)
     */
    public void delete(Long ruleId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));

        rule.setActive(false);
        rule.setDeleted(true);
        ruleRepository.save(rule);

        log.info("Soft deleted rule {}", ruleId);
    }

    /**
     * Restore a soft-deleted rule from trash
     */
    public BenefitPolicyRuleResponseDto restore(Long ruleId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));

        rule.setDeleted(false);
        rule.setActive(true);
        BenefitPolicyRule saved = ruleRepository.save(rule);

        log.info("Restored rule {} from trash", ruleId);
        return BenefitPolicyRuleResponseDto.fromEntity(saved);
    }

    /**
     * Permanently delete a rule
     */
    public void hardDelete(Long ruleId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));
        
        ruleRepository.delete(rule);
        log.info("Hard deleted rule {}", ruleId);
    }

    /**
     * Delete all rules for a policy
     */
    public void deleteAllForPolicy(Long policyId) {
        validatePolicyExists(policyId);
        
        List<BenefitPolicyRule> rules = ruleRepository.findByBenefitPolicyId(policyId);
        ruleRepository.deleteAll(rules);
        
        log.info("Hard deleted {} rules for policy {}", rules.size(), policyId);
    }

    /**
     * Deactivate all rules for a policy (soft delete)
     */
    public int deactivateAllForPolicy(Long policyId) {
        validatePolicyExists(policyId);
        int count = ruleRepository.deactivateAllForPolicy(policyId);
        log.info("Deactivated {} rules for policy {}", count, policyId);
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void validatePolicyExists(Long policyId) {
        if (!policyRepository.existsById(policyId)) {
            throw new ResourceNotFoundException("BenefitPolicy", "id", policyId);
        }
    }

    private void validateTargetXor(Long categoryId, Long serviceId) {
        if (categoryId == null) {
            throw new BusinessRuleException(
                    "Rule must target a medical category (medicalCategoryId required). " +
                            "Service-level rules have been removed as of V228.");
        }
        if (serviceId != null) {
            throw new BusinessRuleException(
                    "Service-level rules are no longer supported (removed in V228). " +
                            "Use medicalCategoryId only.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get rule count for a policy
     */
    @Transactional(readOnly = true)
    public long countByPolicy(Long policyId) {
        return ruleRepository.countByBenefitPolicyId(policyId);
    }

    /**
     * Get active rule count for a policy
     */
    @Transactional(readOnly = true)
    public long countActiveByPolicy(Long policyId) {
        return ruleRepository.countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(policyId);
    }

    /**
     * Resolve the effective category ID for a service.
     * Since V228, returns the service's direct categoryId (no junction table).
     * 
     * @deprecated Use categoryId directly. Will be removed when MedicalService is
     *             fully dropped.
     */
    @Deprecated
    private Long resolveCategoryIdForCoverage(com.waad.tba.modules.medicaltaxonomy.entity.MedicalService service) {
        if (service == null) {
            return null;
        }
        return service.getCategoryId();
    }

    private void collectAllChildCategoryIds(Long parentId, List<Long> result) {
        List<MedicalCategory> children = categoryRepository.findByParentId(parentId);
        for (MedicalCategory child : children) {
            if (!result.contains(child.getId())) {
                result.add(child.getId());
                collectAllChildCategoryIds(child.getId(), result);
            }
        }
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAvailableTemplates() {
        String sql = "SELECT id, name, description, is_default FROM benefit_policy_templates WHERE active = true";
        List<?> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream()
                .map(row -> {
                    Object[] array = (Object[]) row;
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", ((Number) array[0]).longValue());
                    map.put("name", (String) array[1]);
                    map.put("description", (String) array[2]);
                    map.put("isDefault", (Boolean) array[3]);
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Apply a benefit template's rules to a policy
     */
    public void applyTemplate(Long policyId, Long templateId, String mode) {
        log.info("Applying template {} to policy {} with mode {}", templateId, policyId, mode);
        
        if ("REPLACE".equalsIgnoreCase(mode)) {
            deactivateAllForPolicy(policyId);
        }
        
        // 1. Fetch the template
        String templateSql = "SELECT id, name FROM benefit_policy_templates WHERE id = :templateId AND active = true";
        List<?> templates = em.createNativeQuery(templateSql)
                .setParameter("templateId", templateId)
                .getResultList();
        if (templates.isEmpty()) {
            throw new ResourceNotFoundException("BenefitPolicyTemplate", "id", templateId);
        }

        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicy", "id", policyId));

        // 2. Fetch the rules for the template
        String rulesSql = "SELECT medical_category_id, coverage_percent, times_limit, amount_limit, requires_pre_approval FROM benefit_policy_template_rules WHERE template_id = :templateId AND active = true";
        List<?> templateRules = em.createNativeQuery(rulesSql)
                .setParameter("templateId", templateId)
                .getResultList();

        log.info("Found {} rules for template {}", templateRules.size(), templateId);

        for (Object row : templateRules) {
            Object[] array = (Object[]) row;
            Long medicalCategoryId = ((Number) array[0]).longValue();
            Integer coveragePercent = policy.getDefaultCoveragePercent();
            Integer timesLimit = array[2] != null ? ((Number) array[2]).intValue() : null;
            java.math.BigDecimal amountLimit = array[3] != null ? (java.math.BigDecimal) array[3] : null;
            Boolean requiresPreApproval = array[4] != null ? (Boolean) array[4] : false;

            // Check if rule already exists for this policy and category
            Optional<BenefitPolicyRule> existingRuleOpt = ruleRepository
                    .findByBenefitPolicyIdAndMedicalCategoryId(policyId, medicalCategoryId);

            if (existingRuleOpt.isPresent()) {
                BenefitPolicyRule rule = existingRuleOpt.get();
                rule.setCoveragePercent(coveragePercent);
                rule.setRequiresPreApproval(requiresPreApproval);
                rule.setDeleted(false);
                rule.setActive(true);
                ruleRepository.save(rule);
            } else {
                MedicalCategory category = categoryRepository.findById(medicalCategoryId)
                        .orElse(null);
                if (category == null) {
                    continue;
                }
                BenefitPolicyRule rule = BenefitPolicyRule.builder()
                        .benefitPolicy(policy)
                        .medicalCategory(category)
                        .coveragePercent(coveragePercent)
                        .requiresPreApproval(requiresPreApproval)
                        .waitingPeriodDays(0)
                        .active(true)
                        .deleted(false)
                        .build();
                ruleRepository.save(rule);
            }
        }
        log.info("Template {} successfully applied to policy {}", templateId, policyId);
    }

    /**
     * Copy all rules from an existing policy to a target policy
     */
    public void copyRulesFromPolicy(Long targetPolicyId, Long sourcePolicyId, String mode) {
        log.info("Copying rules from policy {} to policy {} with mode {}", sourcePolicyId, targetPolicyId, mode);

        if ("REPLACE".equalsIgnoreCase(mode)) {
            deactivateAllForPolicy(targetPolicyId);
        }

        BenefitPolicy targetPolicy = policyRepository.findById(targetPolicyId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicy", "id", targetPolicyId));

        List<BenefitPolicyRule> sourceRules = ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(sourcePolicyId);

        log.info("Found {} active rules from source policy {}", sourceRules.size(), sourcePolicyId);

        for (BenefitPolicyRule sourceRule : sourceRules) {
            Long medicalCategoryId = sourceRule.getMedicalCategory().getId();
            Integer coveragePercent = sourceRule.getCoveragePercent();
            Integer timesLimit = null;
            java.math.BigDecimal amountLimit = null;
            Boolean requiresPreApproval = sourceRule.isRequiresPreApproval();

            // Check if rule already exists for this policy and category
            Optional<BenefitPolicyRule> existingRuleOpt = ruleRepository
                    .findByBenefitPolicyIdAndMedicalCategoryIdAndEncounterType(
                            targetPolicyId, medicalCategoryId, sourceRule.getEncounterType());

            if (existingRuleOpt.isPresent()) {
                BenefitPolicyRule rule = existingRuleOpt.get();
                rule.setCoveragePercent(coveragePercent);
                rule.setRequiresPreApproval(requiresPreApproval != null ? requiresPreApproval : false);
                rule.setDeleted(false);
                rule.setActive(true);
                ruleRepository.save(rule);
            } else {
                MedicalCategory category = categoryRepository.findById(medicalCategoryId).orElse(null);
                if (category == null) {
                    continue;
                }
                BenefitPolicyRule newRule = BenefitPolicyRule.builder()
                        .benefitPolicy(targetPolicy)
                        .medicalCategory(category)
                        .encounterType(sourceRule.getEncounterType())
                        .coveragePercent(coveragePercent)
                        .copayPercentage(sourceRule.getCopayPercentage())
                        .inheritanceEnabled(sourceRule.isInheritanceEnabled())
                        .priority(sourceRule.getPriority())
                        .requiresPreApproval(requiresPreApproval != null ? requiresPreApproval : false)
                        .waitingPeriodDays(0)
                        .active(true)
                        .deleted(false)
                        .build();
                ruleRepository.save(newRule);
            }
        }
        log.info("Successfully copied rules from policy {} to policy {}", sourcePolicyId, targetPolicyId);
    }

    private com.waad.tba.modules.providercontract.enums.EncounterType parseEncounterType(String raw) {
        if (raw == null || raw.isBlank()) {
            return com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT;
        }
        try {
            return com.waad.tba.modules.providercontract.enums.EncounterType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("سياق قاعدة التغطية غير صالح: " + raw);
        }
    }
}
