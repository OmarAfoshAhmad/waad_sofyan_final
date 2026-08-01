package com.waad.tba.modules.employer.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.guard.DeletionGuard;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.dto.EmployerCreateDto;
import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.dto.EmployerSelectorDto;
import com.waad.tba.modules.employer.dto.EmployerUpdateDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.mapper.EmployerMapper;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Employer Service - Phase 2 Implementation
 * 
 * Features:
 * - Auto-code generation (EMP-01, EMP-02, ...)
 * - Field normalization (name ↔ nameAr)
 * - Validation and error handling
 * - Uses Organization Entity (CANONICAL)
 * 
 * This service works with {@link Employer} entity directly.
 * All CRUD operations work with Employer table (companies).
 * 
 * @see Employer
 * @see EMPLOYER_API_CONTRACT.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployerService {

    private static final String EMPLOYER_CODE_PREFIX = "EMP-";
    private static final String EMPLOYER_CODE_PATTERN = "EMP-%";
    private static final int EMPLOYER_CODE_LENGTH = 2; // EMP-01, EMP-02, etc.

    private final EmployerRepository employerRepository;
    private final EmployerMapper mapper;
    private final com.waad.tba.modules.provider.repository.ProviderRepository providerRepository;
    private final MemberRepository memberRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;

    /**
     * Get all active, non-archived employers (paginated)
     * 
     * @param pageable Pagination and sorting parameters
     * @return Page of employers
     */
    public Page<EmployerResponseDto> getAll(Pageable pageable) {
        return employerRepository.findByActiveTrue(pageable)
                .map(mapper::toResponse);
    }

    /**
     * Get all active, non-archived employers (non-paginated - for backward
     * compatibility)
     * WARNING: Use paginated version for production
     */
    public List<EmployerResponseDto> getAllNonPaginated() {
        return employerRepository.findByActiveTrue()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Get all employers including archived ones (paginated)
     */
    public Page<EmployerResponseDto> getAllIncludingArchived(Pageable pageable) {
        return employerRepository.findAll(pageable)
                .map(mapper::toResponse);
    }

    public Page<EmployerResponseDto> getPage(Boolean active, String query, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();
        return employerRepository.searchPage(active, normalizedQuery, pageable).map(mapper::toResponse);
    }

    /**
     * Get all active employers (non-paginated)
     */
    public List<EmployerResponseDto> getActiveEmployers() {
        return employerRepository.findByActiveTrue()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Get all employers including archived ones (non-paginated - for backward
     * compatibility)
     */
    public List<EmployerResponseDto> getAllIncludingArchivedNonPaginated() {
        return employerRepository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Get employer selectors (for dropdowns) - excludes archived
     * Non-paginated as dropdowns typically need all options
     * 
     * ROLE ISOLATION (2026-03-06):
     * If current user is PROVIDER_STAFF, filter by their allowed employers.
     */
    public List<EmployerSelectorDto> getSelectors() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        if (principal instanceof com.waad.tba.modules.rbac.entity.User user) {
            String role = user.getUserType();
            Long providerId = user.getProviderId();

            if ("PROVIDER_STAFF".equals(role) && providerId != null) {
                log.debug("[EmployerService] Filtering selectors for PROVIDER_STAFF user: {}, Provider: {}",
                        user.getUsername(), providerId);

                com.waad.tba.modules.provider.entity.Provider provider = providerRepository.findById(providerId)
                        .orElse(null);

                if (provider != null) {
                    // If provider allows all, return all active
                    if (Boolean.TRUE.equals(provider.getAllowAllEmployers())) {
                        return employerRepository.findByActiveTrue().stream().map(mapper::toSelector).toList();
                    }

                    // Otherwise, return only authorized ones
                    return provider.getAllowedEmployers().stream()
                            .filter(pae -> Boolean.TRUE.equals(pae.getActive()) && pae.getEmployer() != null)
                            .map(pae -> mapper.toSelector(pae.getEmployer()))
                            .toList();
                }

                // If provider not found, return empty list (defensive security)
                return List.of();
            }
        }

        return employerRepository.findByActiveTrue()
                .stream()
                .map(mapper::toSelector)
                .toList();
    }

    /**
     * Get employer by ID
     */
    public EmployerResponseDto getById(Long id) {
        Employer employer = findEmployerById(id);
        return mapper.toResponse(employer);
    }

    /**
     * Create new employer with auto-code generation
     * 
     * Phase 2 Features:
     * - Auto-generates code if not provided (EMP-01, EMP-02, ...)
     * - Normalizes field names (accepts 'employerCode' or 'code', 'nameAr' or
     * 'name')
     * - Validates uniqueness of code
     * - Sets default active=true
     * 
     * @param dto EmployerCreateDto (code is optional)
     * @return Created employer response
     * @throws BusinessRuleException if code already exists
     */
    @Transactional
    public EmployerResponseDto create(EmployerCreateDto dto) {
        log.info("[EmployerService] Creating employer with name: {}", dto.getName());

        String employerCode = dto.getCode().trim().toUpperCase();
        validateEmployerTerms(dto.getContractStartDate(), dto.getContractEndDate(), dto.getMaxMemberLimit(), 0L);

        // Validate code uniqueness
        if (employerRepository.existsByCodeIgnoreCase(employerCode)) {
            throw new IllegalStateException("CODE_DUPLICATE:هذا الرمز مستخدم مسبقاً، اختر رمزاً آخر");
        }

        // Validate name uniqueness
        if (employerRepository.existsByNameIgnoreCase(dto.getName().trim())) {
            throw new IllegalStateException("NAME_DUPLICATE:اسم جهة العمل هذا مستخدم مسبقاً، اختر اسماً آخر");
        }

        Employer employer = Employer.builder()
                .code(employerCode)
                .name(dto.getName().trim())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .businessType(dto.getBusinessType())
                .website(dto.getWebsite())
                .logoUrl(dto.getLogoUrl())
                .crNumber(dto.getCrNumber())
                .taxNumber(dto.getTaxNumber())
                .contractStartDate(dto.getContractStartDate())
                .contractEndDate(dto.getContractEndDate())
                .maxMemberLimit(dto.getMaxMemberLimit())
                .build();

        Employer saved = employerRepository.save(employer);
        log.info("[EmployerService] Created employer with ID: {} and code: {}", saved.getId(), saved.getCode());

        return mapper.toResponse(saved);
    }

    /**
     * Update existing employer
     * 
     * Phase 2 Features:
     * - Normalizes field names
     * - Validates code uniqueness (if changed)
     * - Updates mutable fields only (name, nameEn, active)
     * - Preserves auto-generated codes (warning logged if code changes)
     * 
     * @param id  Employer ID
     * @param dto EmployerUpdateDto
     * @return Updated employer response
     * @throws ResourceNotFoundException if employer not found
     * @throws BusinessRuleException     if code conflict
     */
    @Transactional
    public EmployerResponseDto update(Long id, EmployerUpdateDto dto) {
        log.info("[EmployerService] Updating employer ID: {}", id);

        Employer employer = findEmployerById(id);

        // Validate code uniqueness (exclude self)
        if (employerRepository.existsByCodeIgnoreCaseAndIdNot(dto.getCode().trim().toUpperCase(), id)) {
            throw new IllegalStateException("CODE_DUPLICATE:هذا الرمز مستخدم مسبقاً، اختر رمزاً آخر");
        }

        // Validate name uniqueness (exclude self)
        if (employerRepository.existsByNameIgnoreCaseAndIdNot(dto.getName().trim(), id)) {
            throw new IllegalStateException("NAME_DUPLICATE:اسم جهة العمل هذا مستخدم مسبقاً، اختر اسماً آخر");
        }

        employer.setCode(dto.getCode().trim().toUpperCase());
        employer.setName(dto.getName().trim());

        if (dto.getActive() != null && !dto.getActive().equals(employer.getActive())) {
            throw new BusinessRuleException("غيّر حالة جهة العمل عبر إجراء الأرشفة أو الاستعادة لضمان سلامة العلاقات");
        }
        validateEmployerTerms(dto.getContractStartDate(), dto.getContractEndDate(), dto.getMaxMemberLimit(),
                memberRepository.countByEmployerIdAndActiveTrue(id));
        employer.setAddress(dto.getAddress());
        employer.setPhone(dto.getPhone());
        employer.setEmail(dto.getEmail());
        employer.setBusinessType(dto.getBusinessType());
        employer.setWebsite(dto.getWebsite());
        employer.setLogoUrl(dto.getLogoUrl());
        employer.setCrNumber(dto.getCrNumber());
        employer.setTaxNumber(dto.getTaxNumber());
        employer.setContractStartDate(dto.getContractStartDate());
        employer.setContractEndDate(dto.getContractEndDate());
        employer.setMaxMemberLimit(dto.getMaxMemberLimit());

        Employer updated = employerRepository.save(employer);
        log.info("[EmployerService] Updated employer ID: {}", id);

        return mapper.toResponse(updated);
    }

    /**
     * Delete employer - DISABLED
     * 
     * Employers cannot be deleted because they are linked to:
     * - Members
     * - Benefit Policies
     * - Claims
     * - Providers
     * 
     * Use archive() instead to safely hide employers from lists while preserving
     * data integrity.
     * 
     * @param id Employer ID
     * @throws BusinessRuleException Always throws - delete is not allowed
     */
    @Transactional
    public void delete(Long id) {
        findEmployerById(id);
        throw new BusinessRuleException("الحذف النهائي لجهة العمل غير مسموح لحماية السجل التأميني؛ استخدم الأرشفة");
    }

    /**
     * Archive employer (safe alternative to delete)
     * 
     * Sets archived=true, hiding employer from default lists while keeping:
     * - All database records intact
     * - Member relationships
     * - Benefit Policy relationships
     * - Claim history
     * - Provider links
     * 
     * @param id Employer ID
     * @return Updated employer response
     * @throws ResourceNotFoundException if employer not found
     */
    @Transactional
    public EmployerResponseDto archive(Long id) {
        log.info("[EmployerService] Archiving employer ID: {}", id);

        Employer employer = findEmployerById(id);

        // NOTE: only ACTIVE members/policies block archiving. Historical
        // (already-inactive) members must never block it — otherwise any employer
        // that ever had a member deactivated becomes permanently un-archivable
        // even with zero active members, which was the actual bug reported.
        DeletionGuard.of("جهة العمل")
                .check("مستفيدون نشطون", memberRepository.countByEmployerIdAndActiveTrue(id))
                .check("وثائق تأمين نشطة", benefitPolicyRepository.countByEmployerIdAndActiveTrue(id))
                .throwIfBlocked("أوقف تفعيل المستفيدين وأنهِ الوثائق أولاً.");

        employer.setActive(false);
        Employer updated = employerRepository.save(employer);

        log.info("[EmployerService] Archived employer ID: {}", id);
        return mapper.toResponse(updated);
    }

    /**
     * Restore archived employer
     * 
     * Sets archived=false, making employer visible again in default lists.
     * 
     * @param id Employer ID
     * @return Updated employer response
     * @throws ResourceNotFoundException if employer not found
     */
    @Transactional
    public EmployerResponseDto restore(Long id) {
        log.info("[EmployerService] Restoring employer ID: {}", id);

        Employer employer = findEmployerById(id);

        // Re-run the same contract-terms validation update() enforces —
        // restore() previously skipped it entirely, so an employer could
        // come back active with a contract end date before its start date,
        // or a member limit already exceeded by members added while archived.
        validateEmployerTerms(employer.getContractStartDate(), employer.getContractEndDate(),
                employer.getMaxMemberLimit(), memberRepository.countByEmployerIdAndActiveTrue(id));

        // Restore by setting active=true
        employer.setActive(true);
        Employer updated = employerRepository.save(employer);

        log.info("[EmployerService] Restored employer ID: {}", id);
        return mapper.toResponse(updated);
    }

    /**
     * Bulk archive — each employer is archived independently so one blocked
     * employer (active members/policies) never discards the others in the batch.
     */
    @Transactional
    public com.waad.tba.modules.employer.dto.BulkEmployerResultDto bulkArchive(List<Long> ids) {
        return bulkApply(ids, this::archive);
    }

    /**
     * Bulk restore — same independent-per-item semantics as {@link #bulkArchive}.
     */
    @Transactional
    public com.waad.tba.modules.employer.dto.BulkEmployerResultDto bulkRestore(List<Long> ids) {
        return bulkApply(ids, this::restore);
    }

    private com.waad.tba.modules.employer.dto.BulkEmployerResultDto bulkApply(
            List<Long> ids, java.util.function.Function<Long, EmployerResponseDto> operation) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessRuleException("يجب تحديد جهات العمل المطلوبة");
        }

        List<com.waad.tba.modules.employer.dto.BulkEmployerResultDto.EmployerResult> results = new java.util.ArrayList<>();
        int successCount = 0;

        for (Long id : ids) {
            try {
                EmployerResponseDto updated = operation.apply(id);
                successCount++;
                results.add(com.waad.tba.modules.employer.dto.BulkEmployerResultDto.EmployerResult.builder()
                        .employerId(id).employerName(updated.getName()).success(true)
                        .message("تمت العملية بنجاح").build());
            } catch (BusinessRuleException | ResourceNotFoundException ex) {
                results.add(com.waad.tba.modules.employer.dto.BulkEmployerResultDto.EmployerResult.builder()
                        .employerId(id).success(false).message(ex.getMessage()).build());
            } catch (RuntimeException ex) {
                log.error("Unexpected error during bulk operation for employer {}", id, ex);
                results.add(com.waad.tba.modules.employer.dto.BulkEmployerResultDto.EmployerResult.builder()
                        .employerId(id).success(false).message("حدث خطأ غير متوقع").build());
            }
        }

        return com.waad.tba.modules.employer.dto.BulkEmployerResultDto.builder()
                .totalCount(ids.size())
                .successCount(successCount)
                .failedCount(ids.size() - successCount)
                .results(results)
                .build();
    }

    /**
     * Count active employers
     */
    public long count() {
        return employerRepository.countByActiveTrue();
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    /**
     * Find employer by ID
     * 
     * @param id Employer ID
     * @return Employer entity
     * @throws ResourceNotFoundException if not found
     */
    private Employer findEmployerById(Long id) {
        return employerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employer not found with id: " + id));
    }

    /**
     * Normalize code and generate if null/empty
     * 
     * Auto-Code Generation Logic:
     * 1. Query max code with pattern EMP-%
     * 2. Extract numeric suffix
     * 3. Increment by 1
     * 4. Format as EMP-XX (zero-padded)
     * 
     * Examples:
     * - No existing codes → EMP-01
     * - Max code EMP-03 → EMP-04
     * - Max code EMP-99 → EMP-100 (grows as needed)
     * 
     * @param providedCode Code from DTO (may be null)
     * @return Normalized code or auto-generated code
     */
    private String normalizeAndGenerateCode(String providedCode) {
        // If code provided, use it (trim whitespace)
        if (providedCode != null && !providedCode.trim().isEmpty()) {
            return providedCode.trim();
        }

        // Auto-generate code
        log.debug("[EmployerService] Auto-generating employer code...");

        List<Employer> employers = employerRepository.findAll();

        int nextNumber = 1; // Default: EMP-01

        if (!employers.isEmpty()) {
            // Find max code that matches pattern
            int currentMax = employers.stream()
                    .filter(e -> e.getCode() != null && e.getCode().startsWith(EMPLOYER_CODE_PREFIX))
                    .map(e -> {
                        try {
                            String suffix = e.getCode().substring(EMPLOYER_CODE_PREFIX.length());
                            return Integer.parseInt(suffix);
                        } catch (Exception ex) {
                            return 0;
                        }
                    })
                    .max(Integer::compareTo)
                    .orElse(0);

            nextNumber = currentMax + 1;
            log.debug("[EmployerService] Max existing code number: {}", currentMax);
        }

        String generatedCode = String.format("%s%0" + EMPLOYER_CODE_LENGTH + "d", EMPLOYER_CODE_PREFIX, nextNumber);
        log.info("[EmployerService] Auto-generated employer code: {}", generatedCode);

        return generatedCode;
    }

    /**
     * Check if an employer code is available (case-insensitive, always uppercased).
     *
     * @param code      code to check (will be uppercased)
     * @param excludeId employer ID to exclude (pass null for create)
     * @return true if available
     */
    public boolean isCodeAvailable(String code, Long excludeId) {
        String upper = code.trim().toUpperCase();
        if (excludeId == null) return !employerRepository.existsByCodeIgnoreCase(upper);
        return !employerRepository.existsByCodeIgnoreCaseAndIdNot(upper, excludeId);
    }

    /**
     * Check if an employer name is available (case-insensitive).
     *
     * @param name      name to check
     * @param excludeId employer ID to exclude (pass null for create)
     * @return true if available
     */
    public boolean isNameAvailable(String name, Long excludeId) {
        String trimmed = name.trim();
        if (excludeId == null) return !employerRepository.existsByNameIgnoreCase(trimmed);
        return !employerRepository.existsByNameIgnoreCaseAndIdNot(trimmed, excludeId);
    }

    private void validateEmployerTerms(java.time.LocalDate startDate, java.time.LocalDate endDate,
                                       Integer maxMemberLimit, long activeMembers) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("تاريخ نهاية التعاقد يجب ألا يسبق تاريخ البداية");
        }
        if (maxMemberLimit != null && maxMemberLimit < activeMembers) {
            throw new BusinessRuleException("الحد الأقصى للمستفيدين أقل من عدد المستفيدين النشطين الحالي: " + activeMembers);
        }
    }

    /**
     * Validate code uniqueness
     * 
     * @param code      Code to validate
     * @param excludeId ID to exclude from check (for updates)
     * @throws BusinessRuleException if code already exists
     */
    private void validateCodeUniqueness(String code, Long excludeId) {
        Optional<Employer> existing = employerRepository.findByCode(code);

        if (existing.isPresent()) {
            Employer existingEmployer = existing.get();

            // If updating, allow same code for same ID
            if (excludeId != null && existingEmployer.getId().equals(excludeId)) {
                return;
            }

            log.error("[EmployerService] Code already exists: {}", code);
            throw new BusinessRuleException("Employer code already exists: " + code);
        }
    }
}
