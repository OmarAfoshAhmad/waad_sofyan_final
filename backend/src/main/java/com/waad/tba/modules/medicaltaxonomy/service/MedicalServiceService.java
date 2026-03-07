package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceLookupDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceResponseDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalSpecialtyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Medical Services (Reference Data).
 * 
 * Business Rules:
 * 1. Code must be unique and immutable
 * 2. Category must exist and be active
 * 3. Base price must be >= 0 (if provided)
 * 4. No coverage, policy, provider, or network logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MedicalServiceService {

    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final MedicalSpecialtyRepository specialtyRepository;
    private final MedicalServiceCategoryRepository serviceCategoryRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalServiceResponseDto create(MedicalServiceCreateDto dto) {
        String normalizedCode = dto.getCode() == null ? null : dto.getCode().trim().toUpperCase();
        String normalizedName = dto.getName() == null ? null : dto.getName().trim();
        String normalizedDescription = dto.getDescription() == null ? null : dto.getDescription().trim();
        log.info("Creating medical service: {}", normalizedCode);

        // Validate code uniqueness
        if (serviceRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleException("Service code already exists: " + normalizedCode);
        }

        // Validate category exists and is active
        MedicalCategory category = categoryRepository.findActiveById(dto.getCategoryId())
                .orElseThrow(() -> new BusinessRuleException("Category not found or inactive: " + dto.getCategoryId()));

        // Validate specialty (if provided)
        MedicalSpecialty specialty = null;
        if (dto.getSpecialtyId() != null) {
            specialty = specialtyRepository.findById(dto.getSpecialtyId())
                    .orElseThrow(() -> new BusinessRuleException("Specialty not found: " + dto.getSpecialtyId()));
            if (Boolean.TRUE.equals(specialty.getDeleted())) {
                throw new BusinessRuleException(
                        "Cannot create service under deleted specialty: " + dto.getSpecialtyId());
            }
            // Cross-validate: specialty must belong to the specified category
            if (specialty.getCategoryId() != null && !specialty.getCategoryId().equals(dto.getCategoryId())) {
                throw new BusinessRuleException(
                        "Specialty " + dto.getSpecialtyId() + " does not belong to category " + dto.getCategoryId());
            }
        }

        // Validate base price (if provided)
        if (dto.getBasePrice() != null && dto.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Base price must be >= 0");
        }

        // Create entity
        MedicalService service = MedicalService.builder()
                .code(normalizedCode)
                .name(normalizedName)
                .categoryId(dto.getCategoryId())
                .specialty(specialty)
                .description(normalizedDescription)
                .basePrice(dto.getBasePrice())
                .requiresPA(dto.getRequiresPA() != null ? dto.getRequiresPA() : false)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        service = serviceRepository.save(service);

        // Auto-link service ↔ category junction table
        serviceCategoryRepository.insertIfAbsent(service.getId(), dto.getCategoryId());

        log.info("✅ Created medical service: {} (ID: {})", service.getCode(), service.getId());

        return toDto(service, category);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public MedicalServiceResponseDto findById(Long id) {
        log.debug("Finding medical service by ID: {}", id);
        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));
        return toDto(service);
    }

    @Transactional(readOnly = true)
    public MedicalServiceResponseDto findByCode(String code) {
        log.debug("Finding medical service by code: {}", code);
        MedicalService service = serviceRepository.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + code));
        return toDto(service);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findAll(Pageable pageable) {
        log.debug("Finding all medical services, page: {}", pageable.getPageNumber());
        return serviceRepository.findByActiveTrue(pageable)
                .map(this::toDto);
    }

    /**
     * Find all medical services with optional status filter
     * 
     * @param pageable pagination info
     * @param active   null = all, true = active only, false = inactive only
     */
    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findAll(Pageable pageable, Boolean active) {
        log.debug("Finding medical services, page: {}, active filter: {}", pageable.getPageNumber(), active);

        Page<MedicalService> services;
        if (active == null) {
            // Return ALL services (including inactive)
            services = serviceRepository.findAll(pageable);
        } else if (active) {
            // Return only active services
            services = serviceRepository.findByActiveTrue(pageable);
        } else {
            // Return only inactive services
            services = serviceRepository.findByActiveFalse(pageable);
        }

        return services.map(this::toDto);
    }

    /**
     * Get statistics about medical services
     * 
     * @return Map with counts: total, active, inactive
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getStats() {
        long total = serviceRepository.count();
        long active = serviceRepository.countByActiveTrue();
        long inactive = serviceRepository.countByActiveFalse();

        return java.util.Map.of(
                "total", total,
                "active", active,
                "inactive", inactive);
    }

    /**
     * Get all active medical services (for dropdowns)
     * 
     * @return List of all active services
     */
    @Transactional(readOnly = true)
    public List<MedicalServiceResponseDto> findAllActive() {
        log.debug("Finding all active medical services for dropdown");
        return serviceRepository.findByActiveTrueOrderByCode()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOOKUP (For MedicalServiceSelector Component)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Unified lookup for medical service selection.
     * 
     * ARCHITECTURAL LAW: MedicalService MUST always be represented as:
     * CODE + NAME + CATEGORY
     * 
     * Features:
     * - Search by: code, nameAr, nameEn, categoryNameAr, categoryNameEn
     * - Optional filter by categoryId
     * - Returns full context for display
     * 
     * @param query      Search term (optional)
     * @param categoryId Filter by category (optional)
     * @return List of services with full category context
     */
    @Transactional(readOnly = true)
    public List<MedicalServiceLookupDto> lookup(String query, Long categoryId) {
        log.debug("Lookup medical services: query={}, categoryId={}", query, categoryId);

        return serviceRepository.lookupServices(query, categoryId)
                .stream()
                .map(p -> MedicalServiceLookupDto.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .name(p.getName())
                        .categoryId(p.getCategoryId())
                        .categoryName(p.getCategoryName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findByCategory(Long categoryId, Pageable pageable) {
        log.debug("Finding services by category: {}", categoryId);
        return serviceRepository.findActiveByCategoryId(categoryId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findServicesRequiringPA(Pageable pageable) {
        log.debug("Finding services requiring PA");
        return serviceRepository.findServicesRequiringPA(pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> search(
            String searchTerm,
            Long categoryId,
            Boolean requiresPA,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        log.debug("Searching services: term={}, category={}, requiresPA={}, price=[{}, {}]",
                searchTerm, categoryId, requiresPA, minPrice, maxPrice);

        return serviceRepository.advancedSearch(searchTerm, categoryId, requiresPA, minPrice, maxPrice, pageable)
                .map(this::toDto);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalServiceResponseDto update(Long id, MedicalServiceUpdateDto dto) {
        log.info("Updating medical service: {}", id);

        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));

        // Update fields (only if provided)
        if (dto.getName() != null) {
            service.setName(dto.getName().trim());
        }
        if (dto.getCategoryId() != null) {
            // Validate category exists and is active
            categoryRepository.findActiveById(dto.getCategoryId())
                    .orElseThrow(
                            () -> new BusinessRuleException("Category not found or inactive: " + dto.getCategoryId()));
            service.setCategoryId(dto.getCategoryId());
            serviceCategoryRepository.insertIfAbsent(service.getId(), dto.getCategoryId());
        }
        if (dto.getBasePrice() != null) {
            if (dto.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Base price must be >= 0");
            }
            service.setBasePrice(dto.getBasePrice());
        }
        if (dto.getDescription() != null) {
            service.setDescription(dto.getDescription().trim());
        }
        if (dto.getRequiresPA() != null) {
            service.setRequiresPA(dto.getRequiresPA());
        }
        if (dto.getActive() != null) {
            service.setActive(dto.getActive());
        }

        service = serviceRepository.save(service);
        log.info("✅ Updated medical service: {}", id);

        return toDto(service);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE (soft-enable / soft-disable)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalServiceResponseDto toggle(Long id) {
        log.info("Toggling medical service: {}", id);

        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));

        boolean nowActive = !service.isActive();
        service.setActive(nowActive);
        service.setDeleted(!nowActive);
        service = serviceRepository.save(service);

        log.info("✅ Toggled medical service {} → active={}", id, nowActive);
        return toDto(service);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(Long id) {
        log.info("Deleting (soft) medical service: {}", id);

        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));

        // Soft delete
        service.setActive(false);
        serviceRepository.save(service);

        log.info("✅ Deleted (soft) medical service: {}", id);
    }

    /**
     * Quick update category only (for inline/table editing)
     * 
     * @param id         Service ID
     * @param categoryId New category ID (null to remove category)
     * @return Updated service DTO
     */
    @Transactional
    public MedicalServiceResponseDto updateCategory(Long id, Long categoryId) {
        log.info("Quick update category for service {} to category {}", id, categoryId);

        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("الخدمة الطبية غير موجودة: " + id));

        // Validate category if provided
        MedicalCategory category = null;
        if (categoryId != null) {
            category = categoryRepository.findActiveById(categoryId)
                    .orElseThrow(() -> new BusinessRuleException("التصنيف غير موجود أو غير نشط: " + categoryId));
        }

        service.setCategoryId(categoryId);
        service = serviceRepository.save(service);
        if (categoryId != null) {
            serviceCategoryRepository.insertIfAbsent(service.getId(), categoryId);
        }

        log.info("✅ Updated category for service {} to {}", id, categoryId);
        return toDto(service, category);
    }

    /**
     * Bulk update category for multiple services
     * 
     * @param serviceIds List of service IDs
     * @param categoryId New category ID
     * @return Map with updated and failed counts
     */
    @Transactional
    public java.util.Map<String, Object> bulkUpdateCategory(List<Long> serviceIds, Long categoryId) {
        log.info("Bulk update category for {} services to category {}", serviceIds.size(), categoryId);

        // Validate category
        MedicalCategory category = categoryRepository.findActiveById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("التصنيف غير موجود أو غير نشط: " + categoryId));

        int updated = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Long serviceId : serviceIds) {
            try {
                serviceRepository.findById(serviceId).ifPresentOrElse(
                        service -> {
                            service.setCategoryId(categoryId);
                            serviceRepository.save(service);
                        },
                        () -> errors.add("الخدمة غير موجودة: " + serviceId));
                updated++;
            } catch (Exception e) {
                failed++;
                errors.add("فشل تحديث الخدمة " + serviceId + ": " + e.getMessage());
                log.warn("Failed to update category for service {}: {}", serviceId, e.getMessage());
            }
        }

        log.info("✅ Bulk category update complete: {} updated, {} failed", updated, failed);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("categoryId", categoryId);
        result.put("categoryName", category.getName());
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    /**
     * Bulk delete all medical services (soft delete)
     * Sets active = false for all services
     * 
     * @return number of services deactivated
     */
    @Transactional
    public int deactivateAll() {
        log.warn("🔴 BULK DEACTIVATE: Deactivating ALL medical services");

        int count = serviceRepository.softDeleteAll();

        log.warn("✅ BULK DEACTIVATE: Deactivated {} medical services", count);
        return count;
    }

    /**
     * Bulk activate all medical services
     * Sets active = true for all services
     * 
     * @return number of services activated
     */
    @Transactional
    public int activateAll() {
        log.warn("🟢 BULK ACTIVATE: Activating ALL medical services");

        int count = serviceRepository.activateAll();

        log.warn("✅ BULK ACTIVATE: Activated {} medical services", count);
        return count;
    }

    /**
     * Permanent delete all medical services
     * ⚠️ WARNING: This is irreversible!
     * 
     * @return number of services permanently deleted
     */
    @Transactional
    public int permanentDeleteAll() {
        log.warn("🗑️ PERMANENT DELETE: Deleting ALL medical services permanently!");

        long count = serviceRepository.count();
        serviceRepository.deleteAll();

        log.warn("✅ PERMANENT DELETE: Deleted {} medical services", count);
        return (int) count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTO MAPPING
    // ═══════════════════════════════════════════════════════════════════════════

    private MedicalServiceResponseDto toDto(MedicalService service) {
        MedicalCategory category = null;
        if (service.getCategoryId() != null) {
            category = categoryRepository.findById(service.getCategoryId())
                    .orElse(null);
        }
        return toDto(service, category);
    }

    private MedicalServiceResponseDto toDto(MedicalService service, MedicalCategory category) {
        return MedicalServiceResponseDto.builder()
                .id(service.getId())
                .code(service.getCode())
                .name(service.getName())
                .categoryId(service.getCategoryId())
                .categoryName(category != null ? category.getName() : null)
                .categoryCode(category != null ? category.getCode() : null)
                .description(service.getDescription())
                .basePrice(service.getBasePrice())
                .requiresPA(service.isRequiresPA())
                .active(service.isActive())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
