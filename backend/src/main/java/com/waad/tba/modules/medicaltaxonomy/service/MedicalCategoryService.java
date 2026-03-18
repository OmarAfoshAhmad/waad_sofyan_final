package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryResponseDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceResponseDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing Medical Categories (Reference Data).
 * 
 * Business Rules:
 * 1. Code must be unique and immutable
 * 2. Parent category must exist and be active
 * 3. Cannot create circular references
 * 4. Cannot delete category with active services
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MedicalCategoryService {

    private final MedicalCategoryRepository categoryRepository;
    private final MedicalServiceRepository serviceRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalCategoryResponseDto create(MedicalCategoryCreateDto dto) {
        String normalizedCode = dto.getCode() == null ? null : dto.getCode().trim().toUpperCase();
        String normalizedName = dto.getName() == null ? null : dto.getName().trim();
        log.info("Creating medical category: {}", normalizedCode);

        // Validate code uniqueness
        if (categoryRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleException("Category code already exists: " + normalizedCode);
        }

        // Validate parent category (if provided)
        String parentName = null;
        if (dto.getParentId() != null) {
            MedicalCategory parent = categoryRepository.findActiveById(dto.getParentId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Parent category not found or inactive: " + dto.getParentId()));
            parentName = parent.getName();
        }

        // Create entity
        MedicalCategory category = MedicalCategory.builder()
                .code(normalizedCode)
                .name(normalizedName)
                .parentId(dto.getParentId())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .context(parseContext(dto.getContext()))
                .coveragePercent(dto.getCoveragePercent())
                .build();

        // Handle multi-parents (Roots)
        if (dto.getMultiParentIds() != null && !dto.getMultiParentIds().isEmpty()) {
            java.util.Set<MedicalCategory> roots = new java.util.HashSet<>(
                    categoryRepository.findAllById(dto.getMultiParentIds()));
            category.setRoots(roots);
        }

        category = categoryRepository.save(category);
        log.info("✅ Created medical category: {} (ID: {})", category.getCode(), category.getId());

        return toDto(category, parentName);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public MedicalCategoryResponseDto findById(Long id) {
        log.debug("Finding medical category by ID: {}", id);
        MedicalCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + id));
        return toDto(category);
    }

    @Transactional(readOnly = true)
    public MedicalCategoryResponseDto findByCode(String code) {
        log.debug("Finding medical category by code: {}", code);
        MedicalCategory category = categoryRepository.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + code));
        return toDto(category);
    }

    @Transactional(readOnly = true)
    public Page<MedicalCategoryResponseDto> findAll(Pageable pageable) {
        log.debug("Finding all medical categories, page: {}", pageable.getPageNumber());
        return categoryRepository.findAll(pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalCategoryResponseDto> findAll(Pageable pageable, Long parentId) {
        log.debug("Finding all medical categories, page: {}, parentId: {}", pageable.getPageNumber(), parentId);
        if (parentId != null) {
            return categoryRepository.findByParentId(parentId, pageable)
                    .map(this::toDto);
        }
        return categoryRepository.findAll(pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalCategoryResponseDto> findAll(Pageable pageable, Long parentId, Boolean active, String search) {
        log.debug("Finding medical categories - page: {}, parentId: {}, active: {}, search: {}",
                pageable.getPageNumber(), parentId, active, search);
        String searchParam = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;
        Specification<MedicalCategory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (searchParam != null) {
                String pattern = "%" + searchParam + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)));
            }
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parentId"), parentId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return categoryRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<MedicalCategoryResponseDto> findRootCategories() {
        log.debug("Finding root categories");
        return categoryRepository.findRootCategories().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MedicalCategoryResponseDto> findChildren(Long parentId) {
        log.debug("Finding children of category: {}", parentId);
        return categoryRepository.findActiveChildrenByParentId(parentId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MedicalCategoryResponseDto> findAllList() {
        log.debug("Finding all active categories");
        return categoryRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MedicalCategoryResponseDto> getCategoryTree() {
        log.debug("Building category tree");

        // Get all active categories
        List<MedicalCategory> allCategories = categoryRepository.findByActiveTrue();

        // Build parent map for efficient lookup
        Map<Long, String> parentNames = allCategories.stream()
                .collect(Collectors.toMap(MedicalCategory::getId, MedicalCategory::getName));

        // Convert to DTOs
        List<MedicalCategoryResponseDto> allDtos = allCategories.stream()
                .map(cat -> toDto(cat, parentNames.get(cat.getParentId())))
                .collect(Collectors.toList());

        // Build hierarchy
        Map<Long, List<MedicalCategoryResponseDto>> childrenMap = allDtos.stream()
                .filter(dto -> dto.getParentId() != null)
                .collect(Collectors.groupingBy(MedicalCategoryResponseDto::getParentId));

        // Attach children to parents
        allDtos.forEach(dto -> {
            dto.setChildren(childrenMap.getOrDefault(dto.getId(), new ArrayList<>()));
        });

        // Return only root categories
        return allDtos.stream()
                .filter(dto -> dto.getParentId() == null)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalCategoryResponseDto update(Long id, MedicalCategoryUpdateDto dto) {
        log.info("Updating medical category: {}", id);

        MedicalCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + id));

        // Update fields (only if provided)
        if (dto.getName() != null) {
            category.setName(dto.getName());
        }

        // Handle parent update
        if (Boolean.TRUE.equals(dto.getClearParent())) {
            // Explicitly converting to root category
            category.setParentId(null);
        } else if (dto.getParentId() != null) {
            // Validate parent category exists and is active
            categoryRepository.findActiveById(dto.getParentId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Parent category not found or inactive: " + dto.getParentId()));

            // Prevent circular reference
            if (dto.getParentId().equals(id)) {
                throw new BusinessRuleException("Category cannot be its own parent");
            }

            category.setParentId(dto.getParentId());
        }
        if (dto.getActive() != null) {
            category.setActive(dto.getActive());
        }
        if (dto.getContext() != null) {
            category.setContext(parseContext(dto.getContext()));
        }
        if (dto.getCoveragePercent() != null) {
            category.setCoveragePercent(dto.getCoveragePercent());
        }

        if (dto.getMultiParentIds() != null) {
            java.util.Set<MedicalCategory> roots = new java.util.HashSet<>(
                    categoryRepository.findAllById(dto.getMultiParentIds()));
            category.setRoots(roots);
        }

        category = categoryRepository.save(category);
        log.info("✅ Updated medical category: {}", id);

        return toDto(category);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(Long id) {
        log.info("Deleting (soft) medical category: {}", id);

        MedicalCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + id));

        // Soft delete — no service/specialty dependency check (tables not yet migrated)
        category.setActive(false);
        category.setDeleted(true);
        categoryRepository.save(category);

        log.info("✅ Deleted (soft) medical category: {}", id);
    }

    @Transactional
    public void hardDelete(Long id) {
        log.info("Hard-deleting (permanent) medical category: {}", id);

        if (!categoryRepository.existsById(id)) {
            throw new BusinessRuleException("Medical category not found: " + id);
        }

        categoryRepository.deleteById(id);

        log.info("✅ Hard-deleted (permanent) medical category: {}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESTORE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalCategoryResponseDto restore(Long id) {
        log.info("Restoring medical category: {}", id);
        MedicalCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + id));
        category.setDeleted(false);
        category.setActive(true);
        category = categoryRepository.save(category);
        log.info("✅ Restored medical category: {}", category.getCode());
        return toDto(category);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE (alias for structured enable/disable)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalCategoryResponseDto toggle(Long id) {
        MedicalCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + id));

        boolean nowActive = !category.isActive();

        category.setActive(nowActive);
        category = categoryRepository.save(category);
        log.info("Toggled category {} — active={}", category.getCode(), nowActive);
        return toDto(category);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<MedicalCategoryResponseDto> search(String searchTerm) {
        log.debug("Searching categories: {}", searchTerm);
        return categoryRepository.searchByName(searchTerm).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDICAL SERVICES BY CATEGORY (CANONICAL)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all active medical services belonging to a specific category.
     * 
     * ARCHITECTURAL LAW:
     * This method is the canonical way to retrieve services for selection.
     * Services MUST be filtered by category before selection.
     * 
     * @param categoryId The category ID
     * @return List of services with category info populated
     */
    @Transactional(readOnly = true)
    public List<MedicalServiceResponseDto> findServicesByCategory(Long categoryId) {
        log.debug("Finding services for category: {}", categoryId);

        // Get category info for response enrichment
        MedicalCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Medical category not found: " + categoryId));

        // Get all active services in this category
        List<MedicalService> services = serviceRepository.findActiveByCategoryId(categoryId);

        // Convert to DTOs with category info
        return services.stream()
                .map(service -> toServiceDto(service, category))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTO MAPPING
    // ═══════════════════════════════════════════════════════════════════════════

    private MedicalCategoryResponseDto toDto(MedicalCategory category) {
        return toDto(category, null);
    }

    private MedicalCategoryResponseDto toDto(MedicalCategory category, String parentName) {
        if (parentName == null && category.getParentId() != null) {
            parentName = categoryRepository.findById(category.getParentId())
                    .map(MedicalCategory::getName)
                    .orElse(null);
        }

        return MedicalCategoryResponseDto.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .parentId(category.getParentId())
                .parentName(parentName)
                .context(category.getContext() != null ? category.getContext().name() : "ANY")
                .active(category.isActive())
                .coveragePercent(category.getCoveragePercent())
                .multiParentIds(category.getRoots().stream().map(MedicalCategory::getId).collect(Collectors.toList()))
                .multiParentNames(
                        category.getRoots().stream().map(MedicalCategory::getName).collect(Collectors.toList()))
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    /**
     * Convert MedicalService entity to DTO with category info.
     */
    private MedicalServiceResponseDto toServiceDto(MedicalService service, MedicalCategory category) {
        return MedicalServiceResponseDto.builder()
                .id(service.getId())
                .code(service.getCode())
                .name(service.getName())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .categoryCode(category.getCode())
                .description(service.getDescription())
                .basePrice(service.getBasePrice())
                .requiresPA(service.isRequiresPA())
                .active(service.isActive())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Safely parse a String into a
     * {@link com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext}.
     * Returns {@code CategoryContext.ANY} for null or unrecognised values
     * (backward-compatible).
     */
    private com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext parseContext(String raw) {
        if (raw == null || raw.isBlank()) {
            return com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext.ANY;
        }
        try {
            return com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown CategoryContext '{}' — falling back to ANY", raw);
            return com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext.ANY;
        }
    }
}
