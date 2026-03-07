package com.waad.tba.modules.employer.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.employer.dto.EmployerCreateDto;
import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.dto.EmployerSelectorDto;
import com.waad.tba.modules.employer.dto.EmployerUpdateDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.mapper.EmployerMapper;
import com.waad.tba.modules.employer.repository.EmployerRepository;
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
@SuppressWarnings("deprecation")
public class EmployerService {

    private static final String EMPLOYER_CODE_PREFIX = "EMP-";
    private static final String EMPLOYER_CODE_PATTERN = "EMP-%";
    private static final int EMPLOYER_CODE_LENGTH = 2; // EMP-01, EMP-02, etc.

    private final EmployerRepository employerRepository;
    private final EmployerMapper mapper;
    private final com.waad.tba.modules.provider.repository.ProviderRepository providerRepository;

    /**
     * Get all active, non-archived employers (paginated)
     * 
     * @param pageable Pagination and sorting parameters
     * @return Page of employers
     */
    public Page<EmployerResponseDto> getAll(Pageable pageable) {
        return employerRepository.findAll(pageable)
                .map(mapper::toResponse);
    }

    /**
     * Get all active, non-archived employers (non-paginated - for backward compatibility)
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
     * Get all employers including archived ones (non-paginated - for backward compatibility)
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
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (principal instanceof com.waad.tba.modules.rbac.entity.User user) {
            String role = user.getUserType();
            Long providerId = user.getProviderId();
            
            if ("PROVIDER_STAFF".equals(role) && providerId != null) {
                log.debug("[EmployerService] Filtering selectors for PROVIDER_STAFF user: {}, Provider: {}", user.getUsername(), providerId);
                
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
     * - Normalizes field names (accepts 'employerCode' or 'code', 'nameAr' or 'name')
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
        
        // Step 1: Normalize and generate code if needed
        String employerCode = normalizeAndGenerateCode(dto.getCode());
        log.debug("[EmployerService] Normalized/Generated code: {}", employerCode);
        
        // Step 2: Validate code uniqueness
        validateCodeUniqueness(employerCode, null);
        
        // Step 3: Build Employer entity (Arabic name only)
        Employer employer = Employer.builder()
                .code(employerCode)
                .name(dto.getName())  // Arabic name (primary and only)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        // Step 4: Persist and return
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
     * @param id Employer ID
     * @param dto EmployerUpdateDto
     * @return Updated employer response
     * @throws ResourceNotFoundException if employer not found
     * @throws BusinessRuleException if code conflict
     */
    @Transactional
    public EmployerResponseDto update(Long id, EmployerUpdateDto dto) {
        log.info("[EmployerService] Updating employer ID: {}", id);
        
        // Step 1: Find existing employer
        Employer employer = findEmployerById(id);
        String oldCode = employer.getCode();
        
        // Step 2: Validate code change (if applicable)
        if (!oldCode.equals(dto.getCode())) {
            log.warn("[EmployerService] Changing employer code from {} to {} for ID: {}", 
                     oldCode, dto.getCode(), id);
            validateCodeUniqueness(dto.getCode(), id);
        }
        
        // Step 3: Update mutable fields (Arabic name only)
        employer.setCode(dto.getCode());
        employer.setName(dto.getName());  // Arabic name (primary and only)
        
        if (dto.getActive() != null) {
            employer.setActive(dto.getActive());
        }
        
        // Step 4: Persist and return
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
     * Use archive() instead to safely hide employers from lists while preserving data integrity.
     * 
     * @param id Employer ID
     * @throws BusinessRuleException Always throws - delete is not allowed
     */
    @Transactional
    public void delete(Long id) {
        throw new BusinessRuleException(
            "لا يمكن حذف الشريك. استخدم الأرشفة بدلاً من ذلك. "
            + "Employer cannot be deleted. Use archive instead to preserve system integrity."
        );
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
        
        // Archive by setting active=false
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
        
        // Restore by setting active=true
        employer.setActive(true);
        Employer updated = employerRepository.save(employer);
        
        log.info("[EmployerService] Restored employer ID: {}", id);
        return mapper.toResponse(updated);
    }

    /**
     * Count active employers
     */
    public long count() {
        return employerRepository.findByActiveTrue().size();
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
     * Validate code uniqueness
     * 
     * @param code Code to validate
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



