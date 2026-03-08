package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.guard.DeletionGuard;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.benefitpolicy.dto.*;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing Benefit Policies.
 * 
 * Business Rules:
 * 1. startDate must be before endDate
 * 2. annualLimit must be >= 0
 * 3. Only one ACTIVE policy per employer at any given date range
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitPolicyService {

    private final BenefitPolicyRepository benefitPolicyRepository;
    private final EmployerRepository employerRepository;
    private final MemberRepository memberRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all benefit policies (paginated)
     */
    @Transactional(readOnly = true)
    public Page<BenefitPolicyResponseDto> findAll(Pageable pageable) {
        log.debug("Finding all benefit policies, page: {}", pageable.getPageNumber());
        Page<BenefitPolicyResponseDto> result = benefitPolicyRepository.findByActiveTrue(pageable)
                .map(BenefitPolicyResponseDto::fromEntity);
        log.info("[BENEFIT-POLICIES] Retrieved {} records (total: {})",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    /**
     * Get soft-deleted benefit policies (paginated)
     */
    @Transactional(readOnly = true)
    public Page<BenefitPolicyResponseDto> findDeleted(Pageable pageable) {
        return benefitPolicyRepository.findByActiveFalse(pageable)
                .map(BenefitPolicyResponseDto::fromEntity);
    }

    /**
     * Get benefit policy by ID
     */
    @Transactional(readOnly = true)
    public BenefitPolicyResponseDto findById(Long id) {
        log.debug("Finding benefit policy by ID: {}", id);
        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));
        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    /**
     * Get benefit policy by policy code
     */
    @Transactional(readOnly = true)
    public BenefitPolicyResponseDto findByPolicyCode(String policyCode) {
        log.debug("Finding benefit policy by code: {}", policyCode);
        BenefitPolicy policy = benefitPolicyRepository.findByPolicyCode(policyCode)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + policyCode));
        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    /**
     * Get all policies for an employer
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyResponseDto> findByEmployer(Long employerOrgId) {
        log.debug("Finding benefit policies for employer: {}", employerOrgId);
        return benefitPolicyRepository.findByEmployerIdAndActiveTrue(employerOrgId)
                .stream()
                .map(BenefitPolicyResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get paginated policies for an employer
     */
    @Transactional(readOnly = true)
    public Page<BenefitPolicyResponseDto> findByEmployer(Long employerOrgId, Pageable pageable) {
        log.debug("Finding benefit policies for employer: {}, page: {}", employerOrgId, pageable.getPageNumber());
        return benefitPolicyRepository.findByEmployerIdAndActiveTrue(employerOrgId, pageable)
                .map(BenefitPolicyResponseDto::fromEntity);
    }

    /**
     * Get active policies with a specific status
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyResponseDto> findByStatus(BenefitPolicyStatus status) {
        log.debug("Finding benefit policies with status: {}", status);
        return benefitPolicyRepository.findByStatusAndActiveTrue(status)
                .stream()
                .map(BenefitPolicyResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get effective policy for an employer on a specific date
     */
    @Transactional(readOnly = true)
    public BenefitPolicyResponseDto findEffectiveForEmployer(Long employerOrgId, LocalDate date) {
        log.debug("Finding effective policy for employer {} on {}", employerOrgId, date);
        return benefitPolicyRepository.findActiveEffectivePolicyForEmployer(employerOrgId, date)
                .map(BenefitPolicyResponseDto::fromEntity)
                .orElse(null);
    }

    /**
     * Search policies by name or code
     */
    @Transactional(readOnly = true)
    public Page<BenefitPolicyResponseDto> search(String search, Pageable pageable) {
        log.debug("Searching benefit policies: {}", search);
        return benefitPolicyRepository.searchByNameOrCode(search, pageable)
                .map(BenefitPolicyResponseDto::fromEntity);
    }

    /**
     * Get selector list for dropdowns
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicySelectorDto> getSelectors() {
        log.debug("Getting benefit policy selectors");
        return benefitPolicyRepository.findByActiveTrue()
                .stream()
                .map(bp -> BenefitPolicySelectorDto.builder()
                        .id(bp.getId())
                        .label(bp.getName())
                        .policyCode(bp.getPolicyCode())
                        .effective(bp.isEffective())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get selector list for an employer
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicySelectorDto> getSelectorsForEmployer(Long employerOrgId) {
        log.debug("Getting benefit policy selectors for employer: {}", employerOrgId);
        return benefitPolicyRepository.findByEmployerIdAndActiveTrue(employerOrgId)
                .stream()
                .map(bp -> BenefitPolicySelectorDto.builder()
                        .id(bp.getId())
                        .label(bp.getName())
                        .policyCode(bp.getPolicyCode())
                        .effective(bp.isEffective())
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto create(BenefitPolicyCreateDto dto) {
        log.info("Creating new benefit policy: {}", dto.getName());

        // Validate dates
        validateDates(dto.getStartDate(), dto.getEndDate());

        // Get employer
        Employer employer = employerRepository.findById(dto.getEmployerOrgId())
                .orElseThrow(() -> new BusinessRuleException("Employer not found: " + dto.getEmployerOrgId()));

        // Insurance organization is deprecated - no longer used

        // Determine initial status
        BenefitPolicyStatus status = BenefitPolicyStatus.DRAFT;
        if (dto.getStatus() != null) {
            try {
                status = BenefitPolicyStatus.valueOf(dto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid status: " + dto.getStatus());
            }
        }

        // If activating, check for overlapping active policies
        if (status == BenefitPolicyStatus.ACTIVE) {
            checkOverlappingActivePolicy(dto.getEmployerOrgId(), dto.getStartDate(), dto.getEndDate(), null);
        }

        // Auto-generate policyCode if not provided
        String policyCode = dto.getPolicyCode();
        if (policyCode == null || policyCode.isBlank()) {
            policyCode = generatePolicyCode();
            log.debug("Auto-generated policy code: {}", policyCode);
        } else {
            // Validate format if provided
            if (!policyCode.matches("POL-\\d{4}-\\d{3}")) {
                throw new BusinessRuleException("Policy code must follow format POL-YYYY-XXX (e.g., POL-2025-001)");
            }
            // Check uniqueness
            if (benefitPolicyRepository.findByPolicyCode(policyCode).isPresent()) {
                throw new BusinessRuleException("Policy code already exists: " + policyCode);
            }
        }

        // Build entity
        BenefitPolicy policy = BenefitPolicy.builder()
                .name(dto.getName())
                .policyCode(policyCode)
                .description(dto.getDescription())
                .employer(employer)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .annualLimit(dto.getAnnualLimit())
                .defaultCoveragePercent(dto.getDefaultCoveragePercent() != null ? dto.getDefaultCoveragePercent() : 80)
                .perMemberLimit(dto.getPerMemberLimit())
                .perFamilyLimit(dto.getPerFamilyLimit())
                .status(status)
                .notes(dto.getNotes())
                .active(true)
                .build();

        policy = benefitPolicyRepository.save(policy);
        log.info("✅ Created benefit policy: {} (ID: {})", policy.getName(), policy.getId());

        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update an existing benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto update(Long id, BenefitPolicyUpdateDto dto) {
        log.info("Updating benefit policy: {}", id);

        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));

        // Update fields if provided
        if (dto.getName() != null) {
            policy.setName(dto.getName());
        }
        if (dto.getPolicyCode() != null) {
            policy.setPolicyCode(dto.getPolicyCode());
        }
        if (dto.getDescription() != null) {
            policy.setDescription(dto.getDescription());
        }
        if (dto.getAnnualLimit() != null) {
            policy.setAnnualLimit(dto.getAnnualLimit());
        }
        if (dto.getDefaultCoveragePercent() != null) {
            policy.setDefaultCoveragePercent(dto.getDefaultCoveragePercent());
        }
        if (dto.getPerMemberLimit() != null) {
            policy.setPerMemberLimit(dto.getPerMemberLimit());
        }
        if (dto.getPerFamilyLimit() != null) {
            policy.setPerFamilyLimit(dto.getPerFamilyLimit());
        }
        if (dto.getNotes() != null) {
            policy.setNotes(dto.getNotes());
        }

        // Handle date changes with validation
        LocalDate newStartDate = dto.getStartDate() != null ? dto.getStartDate() : policy.getStartDate();
        LocalDate newEndDate = dto.getEndDate() != null ? dto.getEndDate() : policy.getEndDate();

        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            validateDates(newStartDate, newEndDate);

            // If policy is active, check for overlapping
            if (policy.getStatus() == BenefitPolicyStatus.ACTIVE) {
                checkOverlappingActivePolicy(
                        policy.getEmployer().getId(),
                        newStartDate, newEndDate, policy.getId());
            }

            policy.setStartDate(newStartDate);
            policy.setEndDate(newEndDate);
        }

        policy = benefitPolicyRepository.save(policy);
        log.info("✅ Updated benefit policy: {}", id);

        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Activate a benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto activate(Long id) {
        log.info("Activating benefit policy: {}", id);

        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));

        // Check for overlapping active policies
        checkOverlappingActivePolicy(
                policy.getEmployer().getId(),
                policy.getStartDate(),
                policy.getEndDate(),
                policy.getId());

        policy.activate();
        policy = benefitPolicyRepository.save(policy);
        log.info("✅ Activated benefit policy: {}", id);

        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    /**
     * Deactivate (expire) a benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto deactivate(Long id) {
        log.info("Deactivating benefit policy: {}", id);

        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));

        policy.deactivate();
        policy = benefitPolicyRepository.save(policy);
        log.info("✅ Deactivated benefit policy: {}", id);

        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    /**
     * Suspend a benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto suspend(Long id) {
        log.info("Suspending benefit policy: {}", id);

        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));

        policy.suspend();
        policy = benefitPolicyRepository.save(policy);
        log.info("✅ Suspended benefit policy: {}", id);

        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    /**
     * Cancel a benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto cancel(Long id) {
        log.info("Cancelling benefit policy: {}", id);

        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));

        policy.setStatus(BenefitPolicyStatus.CANCELLED);
        policy = benefitPolicyRepository.save(policy);
        log.info("✅ Cancelled benefit policy: {}", id);

        return BenefitPolicyResponseDto.fromEntity(policy);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Soft delete a benefit policy
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting benefit policy: {}", id);

        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));

        DeletionGuard.of("وثيقة التأمين")
                .check("مستفيدون مسجلون بها", memberRepository.countByBenefitPolicyIdAndActiveTrue(id))
                .throwIfBlocked("أنهِ تسجيل المستفيدين في هذه الوثيقة أولاً.");

        // Soft delete
        policy.setActive(false);
        policy.setStatus(BenefitPolicyStatus.CANCELLED);
        benefitPolicyRepository.save(policy);

        log.info("✅ Soft deleted benefit policy: {}", id);
    }

    /**
     * Permanently delete a soft-deleted benefit policy (hard delete from DB)
     */
    @Transactional
    public void permanentDelete(Long id) {
        log.info("Permanently deleting benefit policy: {}", id);
        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));
        if (policy.isActive()) {
            throw new BusinessRuleException(
                    "لا يمكن الحذف النهائي إلا للوثائق المحذوفة مسبقاً. استخدم الحذف العادي أولاً.");
        }
        benefitPolicyRepository.deleteById(id);
        log.info("✅ Permanently deleted benefit policy: {}", id);
    }

    /**
     * Restore a soft-deleted benefit policy
     */
    @Transactional
    public BenefitPolicyResponseDto restore(Long id) {
        log.info("Restoring benefit policy: {}", id);
        BenefitPolicy policy = benefitPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Benefit policy not found: " + id));
        policy.setActive(true);
        policy.setStatus(BenefitPolicyStatus.DRAFT);
        BenefitPolicy saved = benefitPolicyRepository.save(policy);
        log.info("✅ Restored benefit policy: {}", id);
        return BenefitPolicyResponseDto.fromEntity(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAINTENANCE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Auto-expire policies that have passed their end date
     */
    @Transactional
    public int expireOldPolicies() {
        log.info("Running auto-expiration of old policies");

        List<BenefitPolicy> expiredPolicies = benefitPolicyRepository.findExpiredActivePolicies(LocalDate.now());

        for (BenefitPolicy policy : expiredPolicies) {
            policy.setStatus(BenefitPolicyStatus.EXPIRED);
            benefitPolicyRepository.save(policy);
            log.debug("Auto-expired policy: {} (ID: {})", policy.getName(), policy.getId());
        }

        log.info("✅ Auto-expired {} policies", expiredPolicies.size());
        return expiredPolicies.size();
    }

    /**
     * Get policies expiring within N days
     */
    @Transactional(readOnly = true)
    public List<BenefitPolicyResponseDto> getPoliciesExpiringSoon(int days) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        return benefitPolicyRepository.findPoliciesExpiringSoon(today, futureDate)
                .stream()
                .map(BenefitPolicyResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate that startDate is before endDate
     */
    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessRuleException("Start date and end date are required");
        }
        if (!startDate.isBefore(endDate)) {
            throw new BusinessRuleException("Start date must be before end date");
        }
    }

    /**
     * Check if there's an overlapping active policy for the employer
     */
    private void checkOverlappingActivePolicy(Long employerOrgId, LocalDate startDate, LocalDate endDate,
            Long excludeId) {
        boolean hasOverlap;
        if (excludeId != null) {
            hasOverlap = benefitPolicyRepository.existsOverlappingActivePolicy(
                    employerOrgId, startDate, endDate, excludeId);
        } else {
            hasOverlap = benefitPolicyRepository.existsOverlappingActivePolicyNew(
                    employerOrgId, startDate, endDate);
        }

        if (hasOverlap) {
            throw new BusinessRuleException(
                    "يوجد بالفعل وثيقة تأمين فعالة لهذا الشريك في نفس الفترة الزمنية. " +
                            "يُسمح بوثيقة فعالة واحدة فقط لكل شريك في أي فترة.");
        }
    }

    /**
     * Example: POL-2025-001, POL-2025-002, etc.
     */
    private String generatePolicyCode() {
        int year = LocalDate.now().getYear();
        String yearPrefix = String.format("POL-%d-", year);

        // Find the highest existing code for this year
        Optional<String> maxCode = benefitPolicyRepository.findMaxPolicyCodeByYearPrefix(yearPrefix);

        int nextSequence = 1;
        if (maxCode.isPresent()) {
            // Extract sequence number from code (e.g., "POL-2025-005" → 5)
            String code = maxCode.get();
            String sequencePart = code.substring(code.lastIndexOf('-') + 1);
            try {
                nextSequence = Integer.parseInt(sequencePart) + 1;
            } catch (NumberFormatException e) {
                nextSequence = 1;
            }
        }

        String generatedCode = String.format("POL-%d-%03d", year, nextSequence);
        return generatedCode;
    }
}
