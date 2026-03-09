package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.modules.medicaltaxonomy.dto.CatalogCategoryNodeDto;
import com.waad.tba.modules.medicaltaxonomy.dto.CatalogCategoryNodeDto.CatalogServiceNodeDto;
import com.waad.tba.modules.medicaltaxonomy.dto.CatalogCategoryNodeDto.CatalogSpecialtyNodeDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalSpecialtyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the 3-level catalog hierarchy tree:
 *
 * <pre>
 * Category
 *   └── Specialty[]
 *         └── Service[]
 * </pre>
 *
 * Endpoint: {@code GET /api/v1/medical-catalog}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalCatalogHierarchyService {

    private final MedicalCategoryRepository categoryRepository;
    private final MedicalSpecialtyRepository specialtyRepository;
    private final MedicalServiceRepository serviceRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Return the full catalog tree (active categories only, ordered by nameAr).
     */
    @Transactional(readOnly = true)
    public List<CatalogCategoryNodeDto> getTree() {
        log.debug("Building medical catalog hierarchy tree based on Phase 10 Roots");

        // 1. Get the 8 Roots (parentId is NULL)
        List<MedicalCategory> roots = categoryRepository.findRootCategories();
        
        // 2. Build the tree
        return roots.stream()
                .map(this::buildRootNode)
                .collect(Collectors.toList());
    }

    private CatalogCategoryNodeDto buildRootNode(MedicalCategory root) {
        // Find sub-categories mapped to this root (Many-to-Many)
        List<MedicalCategory> subCats = categoryRepository.findByRootId(root.getId());
        
        List<CatalogCategoryNodeDto> subCatNodes = subCats.stream()
                .map(this::buildCategoryNode) 
                .collect(Collectors.toList());

        return CatalogCategoryNodeDto.builder()
                .id(root.getId())
                .code(root.getCode())
                .nameAr(root.getNameAr() != null ? root.getNameAr() : root.getName())
                .nameEn(root.getNameEn())
                .children(subCatNodes) 
                .specialtyCount(0)
                .serviceCount(subCatNodes.stream().mapToInt(CatalogCategoryNodeDto::getServiceCount).sum())
                .build();
    }

    /**
     * Return hierarchy for a single category (drill-down view).
     */
    @Transactional(readOnly = true)
    public CatalogCategoryNodeDto getCategoryNode(Long categoryId) {
        MedicalCategory cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new com.waad.tba.common.exception.BusinessRuleException(
                        "Category not found: " + categoryId));
        return buildCategoryNode(cat);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════

    private CatalogCategoryNodeDto buildCategoryNode(MedicalCategory cat) {
        List<MedicalSpecialty> specialties =
                specialtyRepository.findAllByCategoryIdAndDeletedFalseOrderByNameAr(cat.getId());

        List<CatalogSpecialtyNodeDto> specialtyNodes = specialties.stream()
                .map(this::buildSpecialtyNode)
                .collect(Collectors.toList());

        int totalServices = specialtyNodes.stream()
                .mapToInt(CatalogSpecialtyNodeDto::getServiceCount)
                .sum();

        return CatalogCategoryNodeDto.builder()
                .id(cat.getId())
                .code(cat.getCode())
                .nameAr(cat.getNameAr() != null ? cat.getNameAr() : cat.getName())
                .nameEn(cat.getNameEn())
                .specialtyCount(specialtyNodes.size())
                .serviceCount(totalServices)
                .specialties(specialtyNodes)
                .build();
    }

    private CatalogSpecialtyNodeDto buildSpecialtyNode(MedicalSpecialty sp) {
        List<MedicalService> services = serviceRepository.findActiveBySpecialtyId(sp.getId());

        List<CatalogServiceNodeDto> serviceNodes = services.stream()
                .map(this::buildServiceNode)
                .collect(Collectors.toList());

        return CatalogSpecialtyNodeDto.builder()
                .id(sp.getId())
                .code(sp.getCode())
                .nameAr(sp.getNameAr())
                .nameEn(sp.getNameEn())
                .serviceCount(serviceNodes.size())
                .services(serviceNodes)
                .build();
    }

    private CatalogServiceNodeDto buildServiceNode(MedicalService svc) {
        return CatalogServiceNodeDto.builder()
                .id(svc.getId())
                .code(svc.getCode())
                .nameAr(svc.getNameAr() != null ? svc.getNameAr() : svc.getName())
                .nameEn(svc.getNameEn())
                .status(svc.getStatus() != null ? svc.getStatus().name() : "ACTIVE")
                .isMaster(svc.isMaster())
                .build();
    }
}
