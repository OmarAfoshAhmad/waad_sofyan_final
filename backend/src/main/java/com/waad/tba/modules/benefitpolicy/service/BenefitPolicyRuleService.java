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
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
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
    private final MedicalServiceRepository serviceRepository;
    private final MedicalServiceCategoryRepository serviceCategoryRepository;
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
        return ruleRepository.findByBenefitPolicyIdAndActiveTrue(policyId)
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
     * Find service-level rules for a policy
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyRuleResponseDto> findServiceRules(Long policyId) {
        validatePolicyExists(policyId);
        return ruleRepository.findServiceRulesForPolicy(policyId)
                .stream()
                .map(BenefitPolicyRuleResponseDto::fromEntity)
                .toList();
    }

    /**
     * Find rules requiring pre-approval for a policy
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyRuleResponseDto> findPreApprovalRules(Long policyId) {
        validatePolicyExists(policyId);
        return ruleRepository.findByBenefitPolicyIdAndRequiresPreApprovalTrue(policyId)
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
        Long serviceCategoryId = null;
        Long sid = (serviceId != null && serviceId > 0) ? serviceId : null;

        // 1. Resolve actual category from service if possible
        if (sid != null) {
            MedicalService service = serviceRepository.findById(sid)
                    .orElseThrow(() -> new ResourceNotFoundException("MedicalService", "id", sid));
            serviceCategoryId = resolveCategoryIdForCoverage(service);
        }

        // 2. Resolve parent of the override (or service cat if no override) for
        // fallback
        Long targetForParentRef = (categoryOverrideId != null) ? categoryOverrideId : serviceCategoryId;
        Long parentCategoryId = null;
        if (targetForParentRef != null) {
            parentCategoryId = categoryRepository.findById(targetForParentRef)
                    .map(MedicalCategory::getParentId)
                    .orElse(null);
        }

        // 3. Search for best rule with the new prioritized architecture
        Optional<BenefitPolicyRule> ruleOpt = ruleRepository.findBestRuleForService(
                policyId, sid, serviceCategoryId, categoryOverrideId, parentCategoryId);

        if (ruleOpt.isPresent()) {
            return ruleOpt.map(BenefitPolicyRuleResponseDto::fromEntity);
        }

        // FALLBACK TO POLICY DEFAULT IF ALLOWED
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
     * Check if a member has exceeded usage limits for a service
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkUsageLimit(Long policyId, Long serviceId, Long categoryId, Long memberId,
            Integer year) {
        return checkUsageLimit(policyId, serviceId, categoryId, memberId, year, null);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkUsageLimit(Long policyId, Long serviceId, Long categoryId, Long memberId,
            Integer year, Long excludeClaimId) {

        Optional<BenefitPolicyRuleResponseDto> ruleOpt = findCoverageForService(policyId, serviceId, categoryId);
        if (ruleOpt.isEmpty()) {
            return java.util.Map.of("covered", false);
        }

        BenefitPolicyRuleResponseDto rule = ruleOpt.get();
        if (rule.getTimesLimit() == null && rule.getAmountLimit() == null) {
            return java.util.Map.of("covered", true, "hasLimit", false);
        }

        // Query usage from DB
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();

        // Decide whether to query by Service or Category based on the rule level
        boolean isCategoryRule = rule.getMedicalServiceId() == null;

        // ─────────────────────────────────────────────────────────────────────
        // FIX: Handle NULL fields in historical claim lines
        //
        // Old claims may have:
        // - appliedCategoryId = null → fall back to serviceCategoryId
        // - medicalService = null → fall back to serviceCode match
        //
        // Using LEFT JOIN + COALESCE ensures those claims ARE counted.
        // ─────────────────────────────────────────────────────────────────────
        String usageFilter;
        String serviceCode = null;

        if (isCategoryRule) {
            // COALESCE: if appliedCategoryId is null, use serviceCategoryId as fallback
            usageFilter = "COALESCE(cl.appliedCategoryId, cl.serviceCategoryId) = :catId";
        } else {
            // LEFT JOIN on medicalService so lines with medicalService=null are not
            // silently dropped
            // Also match by serviceCode for unmapped/free-text entries
            serviceCode = serviceRepository.findById(rule.getMedicalServiceId())
                    .map(MedicalService::getCode).orElse(null);
            if (serviceCode != null) {
                usageFilter = "(ms.id = :serviceId OR cl.serviceCode = :serviceCode)";
            } else {
                usageFilter = "ms.id = :serviceId";
            }
        }

        String joinClause = isCategoryRule
                ? "FROM ClaimLine cl JOIN cl.claim c "
                : "FROM ClaimLine cl LEFT JOIN cl.medicalService ms JOIN cl.claim c ";

        String q = "SELECT SUM(cl.quantity), SUM(cl.totalPrice) " + joinClause +
                "WHERE c.member.id = :memberId " +
                "AND " + usageFilter + " " +
                "AND c.status NOT IN :excludeStatuses " +
                "AND c.active = true " +
                (excludeClaimId != null ? "AND c.id <> :excludeClaimId " : "") +
                "AND YEAR(c.serviceDate) = :year";

        log.debug("[checkUsageLimit] Query: {} | isCategoryRule={} | year={} | serviceCode={}",
                q, isCategoryRule, targetYear, serviceCode);

        var query = em.createQuery(q)
                .setParameter("memberId", memberId)
                .setParameter("excludeStatuses", java.util.List.of(ClaimStatus.REJECTED))
                .setParameter("year", targetYear);

        if (excludeClaimId != null) {
            query.setParameter("excludeClaimId", excludeClaimId);
        }

        if (isCategoryRule) {
            query.setParameter("catId", rule.getMedicalCategoryId());
        } else {
            query.setParameter("serviceId", rule.getMedicalServiceId());
            if (serviceCode != null) {
                query.setParameter("serviceCode", serviceCode);
            }
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
        usageMap.put("medicalServiceId", rule.getMedicalServiceId());
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

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new rule for a policy
     */
    public BenefitPolicyRuleResponseDto create(Long policyId, BenefitPolicyRuleCreateDto dto) {
        log.info("Creating rule for policy {} - category: {}, service: {}",
                policyId, dto.getMedicalCategoryId(), dto.getMedicalServiceId());

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

        // Set category or service
        if (dto.getMedicalCategoryId() != null) {
            MedicalCategory category = categoryRepository.findById(dto.getMedicalCategoryId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("MedicalCategory", "id", dto.getMedicalCategoryId()));

            // Check for duplicate category rule
            if (ruleRepository.existsCategoryRule(policyId, dto.getMedicalCategoryId(), null)) {
                throw new BusinessRuleException("يوجد قاعدة تغطية لهذا التصنيف بالفعل في هذه الوثيقة");
            }

            rule.setMedicalCategory(category);
        } else {
            MedicalService service = serviceRepository.findById(dto.getMedicalServiceId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("MedicalService", "id", dto.getMedicalServiceId()));

            // Check for duplicate service rule
            if (ruleRepository.existsServiceRule(policyId, dto.getMedicalServiceId(), null)) {
                throw new BusinessRuleException("A rule for this service already exists in this policy");
            }

            rule.setMedicalService(service);
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

        rule.setActive(!rule.isActive());
        BenefitPolicyRule saved = ruleRepository.save(rule);

        log.info("Toggled rule {} active status to {}", ruleId, saved.isActive());
        return BenefitPolicyRuleResponseDto.fromEntity(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Delete a rule (soft delete by setting active=false)
     */
    public void delete(Long ruleId) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", "id", ruleId));

        rule.setActive(false);
        ruleRepository.save(rule);

        log.info("Soft deleted rule {}", ruleId);
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
        boolean hasCategory = categoryId != null;
        boolean hasService = serviceId != null;

        if (hasCategory && hasService) {
            throw new BusinessRuleException(
                    "Rule must target either a category OR a service, not both. " +
                            "Remove one of: medicalCategoryId or medicalServiceId");
        }

        if (!hasCategory && !hasService) {
            throw new BusinessRuleException(
                    "Rule must target at least a category or a service. " +
                            "Provide either medicalCategoryId or medicalServiceId");
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
        return ruleRepository.countByBenefitPolicyIdAndActiveTrue(policyId);
    }

    /**
     * Resolve the effective category ID for a service.
     * Prefers the primary category link from the junction table,
     * falling back to the legacy categoryId column.
     */
    private Long resolveCategoryIdForCoverage(MedicalService service) {
        if (service == null || service.getId() == null) {
            return null;
        }

        return serviceCategoryRepository
                .findFirstByServiceIdAndActiveTrueOrderByIsPrimaryDescIdAsc(service.getId())
                .map(link -> link.getCategoryId())
                .orElse(service.getCategoryId());
    }
}
