package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryBulkMoveDto;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing Medical Categories (Reference Data).
 * 
 * Business Rules:
 * 1. Code must be unique and immutable
 * 2. Categories are canonical flat reference data; parent/multi-parent hierarchy is ignored
 * 3. Cannot delete category with active services
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MedicalCategoryService {

    private static final Pattern AUTO_CATEGORY_CODE_PATTERN = Pattern.compile("^CAT(\\d+)$");

    private final MedicalCategoryRepository categoryRepository;
    private final MedicalServiceRepository serviceRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalCategoryResponseDto create(MedicalCategoryCreateDto dto) {
        String normalizedName = dto.getName() == null ? null : dto.getName().trim();
        log.info("Creating medical category with auto-generated code");
        // Legacy parent/multi-parent inputs are intentionally ignored.
        String parentName = null;

        // Create entity
        MedicalCategory category = MedicalCategory.builder()
                .name(normalizedName)
                .parentId(null)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .contexts(parseContexts(dto.getContexts(), dto.getContext()))
                .coveragePercent(dto.getCoveragePercent())
                .build();
        category.getRoots().clear();

        // Generate unique code server-side in CAT001 format (ignore any client code)
        for (int attempt = 0; attempt < 3; attempt++) {
            String generatedCode = generateNextCategoryCode();
            category.setCode(generatedCode);

            try {
                category = categoryRepository.save(category);
                log.info("✅ Created medical category: {} (ID: {})", category.getCode(), category.getId());
                return toDto(category, parentName);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Code collision while creating category with code {}. Retrying...", generatedCode);
                if (attempt == 2) {
                    throw new BusinessRuleException("Failed to generate unique category code. Please retry.");
                }
            }
        }

        throw new BusinessRuleException("Failed to create medical category due to code generation error.");
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
        log.debug("Finding all medical categories, page: {}. Legacy parentId ignored.", pageable.getPageNumber());
        return categoryRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalCategoryResponseDto> findAll(Pageable pageable, Long parentId, Boolean active, String search) {

        log.debug("Finding medical categories - page: {}, active: {}, search: {}. Legacy parentId ignored.",
                pageable.getPageNumber(), active, search);
        String searchParam = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;
        Specification<MedicalCategory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (searchParam != null) {
                String pattern = "%" + searchParam + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)));
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
        log.debug("Finding root categories requested; returning all active flat categories");
        return findAllList();
    }

    @Transactional(readOnly = true)
    public List<MedicalCategoryResponseDto> findChildren(Long parentId) {
        log.debug("Legacy children lookup requested for category {}; hierarchy is disabled", parentId);
        return List.of();
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
        log.debug("Category tree requested; returning flat active category list for backward compatibility");
        return findAllList();
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
        // Legacy parent/multi-parent update inputs are intentionally ignored.
        category.setParentId(null);
        category.getRoots().clear();
        if (dto.getActive() != null) {

            category.setActive(dto.getActive());
        }
        if (dto.getContexts() != null || dto.getContext() != null) {
            category.setContexts(parseContexts(dto.getContexts(), dto.getContext()));
        }
        if (dto.getCoveragePercent() != null) {
            category.setCoveragePercent(dto.getCoveragePercent());
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

    @Transactional
    public void bulkDelete(List<Long> ids) {
        log.info("Bulk deleting (soft) medical categories: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        
        List<MedicalCategory> categories = categoryRepository.findAllById(ids);
        for (MedicalCategory category : categories) {
            category.setActive(false);
            category.setDeleted(true);
        }
        categoryRepository.saveAll(categories);
        log.info("✅ Bulk deleted {} medical categories", categories.size());
    }
    @Transactional
    public void bulkMove(MedicalCategoryBulkMoveDto dto) {
        throw new BusinessRuleException("تم إلغاء نقل التصنيفات داخل شجرة؛ التصنيفات الطبية أصبحت قائمة موحدة مسطحة.");
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
        return MedicalCategoryResponseDto.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .parentId(null)
                .parentName(null)
                .context(!category.getContexts().isEmpty() ? category.getContexts().iterator().next().name() : "ANY")
                .contexts(category.getContexts().stream().map(Enum::name).sorted().collect(Collectors.toList()))
                .active(category.isActive())
                .coveragePercent(category.getCoveragePercent())
                .multiParentIds(List.of())
                .multiParentNames(List.of())
                .children(List.of())
                .childrenCount(0)
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

    private String generateNextCategoryCode() {
        int maxSequence = categoryRepository.findAll().stream()
                .map(MedicalCategory::getCode)
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(AUTO_CATEGORY_CODE_PATTERN::matcher)
                .filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max()
                .orElse(0);

        int next = maxSequence + 1;
        String candidate = formatCategoryCode(next);
        while (categoryRepository.existsByCodeIgnoreCase(candidate)) {
            next++;
            candidate = formatCategoryCode(next);
        }
        return candidate;
    }

    private String formatCategoryCode(int sequence) {
        return String.format("CAT%03d", sequence);
    }

    /**
     * Safely parse a String into a
     * {@link com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext}.
     * Returns {@code CategoryContext.ANY} for null or unrecognised values
     * (backward-compatible).
     */
    private java.util.Set<com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext> parseContexts(
            java.util.List<String> rawContexts, String fallbackContext) {
        if (rawContexts == null || rawContexts.isEmpty()) {
            return new java.util.HashSet<>(java.util.Set.of(parseContext(fallbackContext)));
        }
        java.util.Set<com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext> result = rawContexts.stream()
                .map(this::parseContext)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (result.contains(com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext.ANY) && result.size() > 1) {
            result.remove(com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext.ANY);
        }
        return result;
    }

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
