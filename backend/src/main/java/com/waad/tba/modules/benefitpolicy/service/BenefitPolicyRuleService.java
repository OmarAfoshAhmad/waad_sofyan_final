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
import com.waad.tba.modules.claim.entity.ClaimStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        return findCoverageForService(policyId, serviceId, categoryOverrideId, null);
    }

    @Transactional(readOnly = true)
    public Optional<BenefitPolicyRuleResponseDto> findCoverageForService(Long policyId, Long serviceId,
            Long categoryOverrideId, Long serviceCategoryId) {
        // ═══ Mirror Category Resolution ═══
        // categoryOverrideId = السياق المختار (مثلاً CAT-OP للعيادات الخارجية)
        // serviceCategoryId = التصنيف الجوهري للخدمة (مثلاً CAT-IP-PHYSIO)
        //
        // المنطق: نحدد التصنيف الهدف حسب السياق ثم نبحث عن القاعدة مباشرة.
        // إذا كان السياق في فرع مختلف عن تصنيف الخدمة، نبحث عن التصنيف
        // المرآة (mirror) تحت السياق بنفس الاسم.

        Long targetCategoryId = resolveTargetCategory(categoryOverrideId, serviceCategoryId);

        if (targetCategoryId != null) {
            // 1) ابحث عن قاعدة مطابقة تماماً للتصنيف الهدف
            Optional<BenefitPolicyRule> rule = ruleRepository
                    .findByBenefitPolicyIdAndMedicalCategoryIdAndDeletedFalseAndActiveTrue(policyId, targetCategoryId);
            if (rule.isPresent()) {
                return rule.map(BenefitPolicyRuleResponseDto::fromEntity);
            }

            // 2) إذا لم توجد قاعدة للتصنيف الفرعي، جرّب قاعدة الجذر
            if (categoryOverrideId != null && !categoryOverrideId.equals(targetCategoryId)) {
                Optional<BenefitPolicyRule> rootRule = ruleRepository
                        .findByBenefitPolicyIdAndMedicalCategoryIdAndDeletedFalseAndActiveTrue(policyId,
                                categoryOverrideId);
                if (rootRule.isPresent()) {
                    return rootRule.map(BenefitPolicyRuleResponseDto::fromEntity);
                }
            }
        }

        // 3) القاعدة الافتراضية للوثيقة
        return policyRepository.findById(policyId)
                .map(policy -> BenefitPolicyRuleResponseDto.builder()
                        .benefitPolicyId(policy.getId())
                        .benefitPolicyName(policy.getName())
                        .ruleType("POLICY_DEFAULT")
                        .coveragePercent(policy.getDefaultCoveragePercent())
                        .effectiveCoveragePercent(policy.getDefaultCoveragePercent())
                        .label("تغطية عامة (السياسة)")
                        .active(true)
                        .build());
    }

    /**
     * Mirror Category Resolution — يحدد التصنيف الصحيح للبحث عن القاعدة.
     *
     * عندما يكون السياق (override) في فرع مختلف عن تصنيف الخدمة (service category):
     * - نأخذ اسم التصنيف الفرعي للخدمة (مثلاً "علاج طبيعي")
     * - نبحث تحت السياق عن تصنيف فرعي بنفس الاسم (mirror)
     * - إذا وجدناه نستخدمه، وإلا نرجع للسياق الجذري
     *
     * أمثلة:
     * | السياق | تصنيف الخدمة | النتيجة |
     * |----------|--------------------|--------------------|
     * | CAT-OP | CAT-IP-PHYSIO(201) | CAT-OP-PHYSIO(701) |
     * | CAT-IP | CAT-IP-PHYSIO(201) | CAT-IP-PHYSIO(201) |
     * | CAT-OP | null | CAT-OP(51) |
     * | null | CAT-IP-PHYSIO(201) | 201 |
     */
    private Long resolveTargetCategory(Long categoryOverrideId, Long serviceCategoryId) {
        if (categoryOverrideId == null && serviceCategoryId == null) {
            return null;
        }
        if (categoryOverrideId == null) {
            return serviceCategoryId;
        }
        if (serviceCategoryId == null) {
            return categoryOverrideId;
        }

        // كلا القيمتين موجودتان — نحتاج تحديد الفرع
        MedicalCategory serviceCat = categoryRepository.findById(serviceCategoryId).orElse(null);
        if (serviceCat == null || serviceCat.getParentId() == null) {
            // تصنيف الخدمة جذري أو غير موجود → استخدم السياق
            return categoryOverrideId;
        }

        // إذا تصنيف الخدمة تحت نفس السياق → نفس الفرع، استخدمه مباشرة
        if (serviceCat.getParentId().equals(categoryOverrideId)) {
            return serviceCategoryId;
        }

        // فرع مختلف → ابحث عن المرآة (mirror) تحت السياق بنفس الاسم
        Optional<MedicalCategory> mirror = categoryRepository
                .findFirstByParentIdAndName(categoryOverrideId, serviceCat.getName());
        return mirror.map(MedicalCategory::getId).orElse(categoryOverrideId);
    }

    /**
     * Check if a service is covered under a policy.
     * A service is covered if:
     * 1. There is an explicit rule for it (at service or category level)
     * 2. OR the policy has a default coverage > 0
     */
    @Transactional(readOnly = true)
    public boolean isServiceCovered(Long policyId, Long serviceId, Long categoryOverrideId) {
        if (findCoverageForService(policyId, serviceId, categoryOverrideId).isPresent()) {
            return true;
        }
        return policyRepository.findById(policyId)
                .map(p -> p.getDefaultCoveragePercent() > 0)
                .orElse(false);
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
     * Returns policy default if no specific rule exists.
     */
    @Transactional(readOnly = true)
    public int getCoveragePercent(Long policyId, Long serviceId, Long categoryOverrideId) {
        return findCoverageForService(policyId, serviceId, categoryOverrideId)
                .map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent)
                .orElseGet(() -> {
                    return policyRepository.findById(policyId)
                            .map(BenefitPolicy::getDefaultCoveragePercent)
                            .orElse(0);
                });
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

        // Resolve usage rule using the same dual-key logic as coverage lookup:
        // categoryId=context override, serviceCategoryId=service intrinsic category.
        Optional<BenefitPolicyRuleResponseDto> ruleOpt = findCoverageForService(policyId, serviceId,
                categoryId, serviceCategoryId);
        if (ruleOpt.isEmpty()) {
            return java.util.Map.of("covered", false);
        }

        BenefitPolicyRuleResponseDto rule = ruleOpt.get();
        if (rule.getTimesLimit() == null && rule.getAmountLimit() == null) {
            return java.util.Map.of("covered", true, "hasLimit", false);
        }

        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();

        // All rules are category-level since V228.
        // Use COALESCE for backward-compat with old claim lines.
        String q = "SELECT COUNT(DISTINCT c.id), SUM(cl.totalPrice) " +
                "FROM ClaimLine cl JOIN cl.claim c " +
                "WHERE c.member.id = :memberId " +
                "AND COALESCE(cl.appliedCategoryId, cl.serviceCategoryId) = :catId " +
                "AND c.status NOT IN :excludeStatuses " +
                "AND c.active = true " +
                (excludeClaimId != null ? "AND c.id <> :excludeClaimId " : "") +
                "AND YEAR(c.serviceDate) = :year";

        var query = em.createQuery(q)
                .setParameter("memberId", memberId)
                .setParameter("catId", rule.getMedicalCategoryId())
                .setParameter("excludeStatuses", java.util.List.of(ClaimStatus.REJECTED))
                .setParameter("year", targetYear);

        if (excludeClaimId != null) {
            query.setParameter("excludeClaimId", excludeClaimId);
        }

        Object[] result = (Object[]) query.getSingleResult();
        long usedCount = result[0] != null ? ((Number) result[0]).longValue() : 0;
        java.math.BigDecimal usedAmount = result[1] != null ? (java.math.BigDecimal) result[1]
                : java.math.BigDecimal.ZERO;

        boolean timesExceeded = rule.getTimesLimit() != null && usedCount >= rule.getTimesLimit();
        boolean amountExceeded = rule.getAmountLimit() != null && usedAmount.compareTo(rule.getAmountLimit()) >= 0;

        java.util.Map<String, Object> usageMap = new java.util.HashMap<>();
        usageMap.put("covered", true);
        usageMap.put("hasLimit", true);
        usageMap.put("ruleId", rule.getId());
        usageMap.put("medicalCategoryId", rule.getMedicalCategoryId());
        usageMap.put("timesLimit", rule.getTimesLimit());
        usageMap.put("amountLimit", rule.getAmountLimit());
        usageMap.put("usedCount", usedCount);
        usageMap.put("usedAmount", usedAmount);
        usageMap.put("exceeded", timesExceeded || amountExceeded);
        usageMap.put("timesExceeded", timesExceeded);
        usageMap.put("amountExceeded", amountExceeded);
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
                    policyId, line.getServiceId(), line.getCategoryId(), line.getServiceCategoryId());

            boolean isCovered = ruleOpt.isPresent();
            int fallbackPercent = getDefaultCoveragePercent(policyId);

            // If the service has a specific rule and is not covered (active=false or not
            // found but rule exists? Actually ruleOpt.isEmpty() means not covered or
            // fallback to default)
            // The frontend logic applies fallback manually. Here we integrate it cleanly.
            boolean explicitlyNotCovered = ruleOpt.map(r -> !r.isActive()).orElse(false);

            int coveragePercent = ruleOpt.map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent)
                    .orElse(fallbackPercent);

            if (explicitlyNotCovered) {
                coveragePercent = 0;
            }

            boolean requiresPreApproval = ruleOpt.map(BenefitPolicyRuleResponseDto::isRequiresPreApproval)
                    .orElse(false);

            result.put("covered", isCovered);
            result.put("notCovered", explicitlyNotCovered || (ruleOpt.isEmpty() && fallbackPercent == 0));
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
                .coveragePercent(dto.getCoveragePercent())
                .amountLimit(dto.getAmountLimit())
                .timesLimit(dto.getTimesLimit())
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
                    .findByBenefitPolicyIdAndMedicalCategoryId(policyId, dto.getMedicalCategoryId());

            if (existingRuleOpt.isPresent()) {
                BenefitPolicyRule existingRule = existingRuleOpt.get();
                boolean wasActive = existingRule.isActive();

                existingRule.setCoveragePercent(dto.getCoveragePercent());
                existingRule.setAmountLimit(dto.getAmountLimit());
                existingRule.setTimesLimit(dto.getTimesLimit());
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
    public List<BenefitPolicyRuleResponseDto> createBulk(Long policyId, List<BenefitPolicyRuleCreateDto> dtos) {
        log.info("Bulk creating {} rules for policy {}", dtos.size(), policyId);

        return dtos.stream()
                .map(dto -> create(policyId, dto))
                .toList();
    }

    /**
     * Initialize policy with the 16 standard professional rules.
     * Searches for categories by their standard codes and creates rules for each.
     */
    public List<BenefitPolicyRuleResponseDto> initializeStandardRules(Long policyId) {
        log.info("Initializing standard 16 rules for policy {}", policyId);

        List<String> standardCodes = List.of(
                "CAT-IP-GEN", "CAT-IP-NURSE", "CAT-IP-PHYSIO", "CAT-IP-WORK",
                "CAT-IP-PSYCH", "CAT-IP-MATER", "CAT-IP-COMPL", "CAT-OP-GEN",
                "CAT-OP-RAD", "CAT-OP-MRI", "CAT-OP-DRUG", "CAT-OP-EQUIP",
                "CAT-OP-PHYSIO", "CAT-OP-DENT-R", "CAT-OP-DENT-C", "CAT-OP-GLASS");

        List<BenefitPolicyRuleResponseDto> results = new java.util.ArrayList<>();

        for (String code : standardCodes) {
            Optional<MedicalCategory> categoryOpt = categoryRepository.findByCode(code);
            if (categoryOpt.isPresent()) {
                MedicalCategory category = categoryOpt.get();

                // Skip if rule already exists
                if (ruleRepository.existsCategoryRule(policyId, category.getId(), null)) {
                    log.warn("Rule for category {} already exists, skipping initialization", code);
                    continue;
                }

                BenefitPolicyRuleCreateDto dto = BenefitPolicyRuleCreateDto.builder()
                        .medicalCategoryId(category.getId())
                        .coveragePercent(
                                category.getCoveragePercent() != null ? category.getCoveragePercent().intValue() : 100) // Use
                                                                                                                        // the
                                                                                                                        // default
                                                                                                                        // from
                                                                                                                        // category
                        .active(true)
                        .requiresPreApproval(false)
                        .waitingPeriodDays(0)
                        .notes("تم الإنشاء تلقائياً — القواعد القياسية")
                        .build();

                try {
                    results.add(create(policyId, dto));
                } catch (Exception e) {
                    log.error("Failed to initialize rule for category {}: {}", code, e.getMessage());
                }
            } else {
                log.warn("Standard category code {} not found in database", code);
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
        // coveragePercent: always update (null means "inherit from policy default")
        rule.setCoveragePercent(dto.getCoveragePercent());
        if (dto.getAmountLimit() != null) {
            rule.setAmountLimit(dto.getAmountLimit());
        }
        if (dto.getTimesLimit() != null) {
            rule.setTimesLimit(dto.getTimesLimit());
        }
        if (dto.getWaitingPeriodDays() != null) {
            rule.setWaitingPeriodDays(dto.getWaitingPeriodDays());
        }
        if (dto.getRequiresPreApproval() != null) {
            rule.setRequiresPreApproval(dto.getRequiresPreApproval());
        }
        if (dto.getNotes() != null) {
            rule.setNotes(dto.getNotes());
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
        if (!ruleRepository.existsById(ruleId)) {
            throw new ResourceNotFoundException("Rule", "id", ruleId);
        }
        ruleRepository.deleteById(ruleId);
        log.info("Hard deleted rule {}", ruleId);
    }

    /**
     * Delete all rules for a policy
     */
    public void deleteAllForPolicy(Long policyId) {
        validatePolicyExists(policyId);
        ruleRepository.deleteByBenefitPolicyId(policyId);
        log.info("Deleted all rules for policy {}", policyId);
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
}
