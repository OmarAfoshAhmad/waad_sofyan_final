package com.waad.tba.modules.medical.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medical.dto.CatalogSearchResultDto;
import com.waad.tba.modules.medical.dto.CatalogTreeCategoryDto;
import com.waad.tba.modules.medical.service.MedicalCatalogService;
import com.waad.tba.modules.medicaltaxonomy.dto.CatalogCategoryNodeDto;
import com.waad.tba.modules.medicaltaxonomy.service.MedicalCatalogHierarchyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;

import java.util.concurrent.TimeUnit;

/**
 * Unified Medical Catalog — read-only view endpoints.
 *
 * <p>
 * Two endpoints:
 * <ul>
 * <li>{@code GET /api/v1/medical-catalog/tree} — full hierarchical catalog</li>
 * <li>{@code GET /api/v1/medical-catalog/search?q=} — flat ILIKE search (max
 * 50)</li>
 * </ul>
 *
 * <p>
 * Access: SUPER_ADMIN and DATA_ENTRY only.
 * No write operations — catalog mutations go through the existing CRUD
 * endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/medical-catalog")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ENTRY')")
public class MedicalCatalogController {

    private final MedicalCatalogService catalogService;
    private final MedicalCatalogHierarchyService hierarchyService;

    /**
     * Returns the full medical catalog as a hierarchical category→services tree.
     *
     * <p>
     * Implementation guarantee: single SQL query, no N+1.
     * Categories with no services are not included in the response.
     *
     * @return list of category nodes, each with child service list
     */
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CatalogTreeCategoryDto>>> getTree() {
        log.debug("GET /api/v1/medical-catalog/tree");
        List<CatalogTreeCategoryDto> tree = catalogService.getTree();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(ApiResponse.success(tree));
    }

    /**
     * Searches for medical services matching the query string.
     *
     * <p>
     * Searches across:
     * <ul>
     * <li>medical_services.code</li>
     * <li>medical_services.name_ar</li>
     * <li>medical_services.name_en</li>
     * <li>ent_service_aliases.alias_text</li>
     * </ul>
     *
     * <p>
     * Uses LOWER(col) LIKE :pattern for case-insensitive matching.
     * Returns at most 50 results ordered by service code.
     *
     * @param q search query (required, min 1 char; returns empty list if blank)
     * @return flat list of matching services with category context
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CatalogSearchResultDto>>> search(
            @RequestParam(name = "q", defaultValue = "") String q) {
        log.debug("GET /api/v1/medical-catalog/search?q='{}'", q);
        List<CatalogSearchResultDto> results = catalogService.search(q);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ─── Taxonomy hierarchy (3-level: Category → Specialty → Service) ──────

    /**
     * Returns the full 3-level catalog hierarchy:
     * Category → Specialty → Service (active records only, ordered by nameAr).
     */
    @GetMapping("/hierarchy")
    public ResponseEntity<ApiResponse<List<CatalogCategoryNodeDto>>> getHierarchy() {
        log.debug("GET /api/v1/medical-catalog/hierarchy");
        List<CatalogCategoryNodeDto> tree = hierarchyService.getTree();
        return ResponseEntity.ok(ApiResponse.success("Catalog hierarchy retrieved", tree));
    }

    /**
     * Returns a single category node with its nested specialties and services.
     */
    @GetMapping("/hierarchy/{categoryId}")
    public ResponseEntity<ApiResponse<CatalogCategoryNodeDto>> getCategoryHierarchy(
            @org.springframework.web.bind.annotation.PathVariable Long categoryId) {
        log.debug("GET /api/v1/medical-catalog/hierarchy/{}", categoryId);
        CatalogCategoryNodeDto node = hierarchyService.getCategoryNode(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Category node retrieved", node));
    }
}
