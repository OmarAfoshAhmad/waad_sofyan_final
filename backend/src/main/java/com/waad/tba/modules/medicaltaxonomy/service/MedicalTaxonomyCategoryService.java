package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.guard.DeletionGuard;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryResponseDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
public class MedicalTaxonomyCategoryService {

    private final MedicalCategoryRepository categoryRepository;
    private final MedicalServiceRepository serviceRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalCategoryResponseDto create(MedicalCategoryCreateDto dto) {
        log.info("Creating medical category: {}", dto.getCode());

        // Validate code uniqueness
        if (categoryRepository.existsByCode(dto.getCode())) {
            throw new BusinessRuleException("Category code already exists: " + dto.getCode());
        }

        // Validate parent category (if provided)
        String parentName = null;
        if (dto.getParentId() != null) {
            MedicalCategory parent = categoryRepository.findActiveById(dto.getParentId())
                    .orElseThrow(() -> new BusinessRuleException("Parent category not found or inactive: " + dto.getParentId()));
            parentName = parent.getName();
        }

        // Create entity
        MedicalCategory category = MedicalCategory.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .parentId(dto.getParentId())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

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
        Page<MedicalCategory> categoriesPage = categoryRepository.findByActiveTrue(pageable);
        List<MedicalCategoryResponseDto> dtoList = categoriesPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, categoriesPage.getTotalElements());
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
        if (dto.getParentId() != null) {
            // Validate parent category exists and is active
            categoryRepository.findActiveById(dto.getParentId())
                    .orElseThrow(() -> new BusinessRuleException("Parent category not found or inactive: " + dto.getParentId()));
            
            // Prevent circular reference
            if (dto.getParentId().equals(id)) {
                throw new BusinessRuleException("Category cannot be its own parent");
            }
            
            category.setParentId(dto.getParentId());
        }
        if (dto.getActive() != null) {
            category.setActive(dto.getActive());
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

        DeletionGuard.of("تصنيف طبي")
                .check("خدمات طبية نشطة", serviceRepository.countActiveByCategoryId(id))
                .throwIfBlocked("أوقف تفعيل الخدمات المرتبطة أولاً.");

        // Soft delete
        category.setActive(false);
        categoryRepository.save(category);

        log.info("✅ Deleted (soft) medical category: {}", id);
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
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
