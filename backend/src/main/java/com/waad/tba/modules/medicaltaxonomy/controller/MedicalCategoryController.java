package com.waad.tba.modules.medicaltaxonomy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryResponseDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceResponseDto;
import com.waad.tba.modules.medicaltaxonomy.service.MedicalCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Medical Category management (Reference Data).
 * 
 * Endpoints (9 total):
 * 1. POST /api/medical-categories - Create category
 * 2. GET /api/medical-categories/{id} - Get by ID
 * 3. GET /api/medical-categories - List all (paginated)
 * 4. PUT /api/medical-categories/{id} - Update category
 * 5. DELETE /api/medical-categories/{id} - Soft delete
 * 6. GET /api/medical-categories/code/{code} - Get by code
 * 7. GET /api/medical-categories/{id}/children - Get subcategories
 * 8. GET /api/medical-categories/tree - Get hierarchy tree
 * 9. GET /api/medical-categories/root - Get root categories
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/medical-categories")
@RequiredArgsConstructor
@Tag(name = "Medical Category", description = "Medical category reference data management")
@PreAuthorize("isAuthenticated()")
public class MedicalCategoryController {

    private final MedicalCategoryService categoryService;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create medical category", description = "Create a new medical category")
    public ResponseEntity<ApiResponse<MedicalCategoryResponseDto>> create(
            @Valid @RequestBody MedicalCategoryCreateDto dto) {
        log.info("[MEDICAL-CATEGORIES] POST /api/medical-categories - code={}", dto.getCode());

        MedicalCategoryResponseDto result = categoryService.create(dto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Medical category created successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "List all active categories", description = "Get list of all active medical categories (non-paginated)")
    public ResponseEntity<ApiResponse<List<MedicalCategoryResponseDto>>> findAllList() {
        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/all");

        List<MedicalCategoryResponseDto> result = categoryService.findAllList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get category by ID", description = "Retrieve a medical category by its ID")
    public ResponseEntity<ApiResponse<MedicalCategoryResponseDto>> findById(@PathVariable("id") Long id) {
        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/{}", id);

        MedicalCategoryResponseDto result = categoryService.findById(id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "List all categories", description = "Get paginated list of all medical categories with optional filters")
    public ResponseEntity<ApiResponse<Page<MedicalCategoryResponseDto>>> findAll(
            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(name = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir,
            @Parameter(description = "Filter by parent category ID") @RequestParam(name = "parentId", required = false) Long parentId,
            @Parameter(description = "Filter by active status") @RequestParam(name = "active", required = false) Boolean active,
            @Parameter(description = "Search by name or code") @RequestParam(name = "search", required = false) String search) {

        log.info(
                "[MEDICAL-CATEGORIES] GET /api/medical-categories - page={}, size={}, parentId={}, active={}, search={}",
                page, size, parentId, active, search);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<MedicalCategoryResponseDto> result = categoryService.findAll(pageable, parentId, active, search);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get category by code", description = "Retrieve a medical category by its unique code")
    public ResponseEntity<ApiResponse<MedicalCategoryResponseDto>> findByCode(@PathVariable("code") String code) {
        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/code/{}", code);

        MedicalCategoryResponseDto result = categoryService.findByCode(code);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get subcategories", description = "Get all direct children of a category")
    public ResponseEntity<ApiResponse<List<MedicalCategoryResponseDto>>> findChildren(@PathVariable("id") Long id) {
        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/{}/children", id);

        List<MedicalCategoryResponseDto> result = categoryService.findChildren(id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDICAL SERVICES BY CATEGORY (CANONICAL ENDPOINT)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all active medical services belonging to a specific category.
     * 
     * ARCHITECTURAL LAW:
     * This endpoint is the ONLY way to retrieve services for selection.
     * Direct service selection without category is NOT allowed.
     * 
     * Flow: Category Selection → This Endpoint → Service Selection → Coverage
     * Resolution
     */
    @GetMapping("/{id}/medical-services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get services by category (CANONICAL)", description = "Get all active medical services belonging to this category. "
            +
            "This is the canonical endpoint for service selection - services MUST be filtered by category first.")
    public ResponseEntity<ApiResponse<List<MedicalServiceResponseDto>>> getServicesByCategory(
            @Parameter(description = "Category ID") @PathVariable("id") Long id) {

        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/{}/medical-services - Canonical service lookup", id);

        // Validate category exists
        categoryService.findById(id); // Throws if not found

        // Get services for this category
        List<MedicalServiceResponseDto> services = categoryService.findServicesByCategory(id);

        log.info("[MEDICAL-CATEGORIES] Found {} services for category {}", services.size(), id);

        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get category tree", description = "Get full hierarchical tree of all categories")
    public ResponseEntity<ApiResponse<List<MedicalCategoryResponseDto>>> getCategoryTree() {
        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/tree");

        List<MedicalCategoryResponseDto> result = categoryService.getCategoryTree();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/root")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get root categories", description = "Get all root categories (parentId = null)")
    public ResponseEntity<ApiResponse<List<MedicalCategoryResponseDto>>> findRootCategories() {
        log.info("[MEDICAL-CATEGORIES] GET /api/medical-categories/root");

        List<MedicalCategoryResponseDto> result = categoryService.findRootCategories();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update category", description = "Update an existing medical category (code is immutable)")
    public ResponseEntity<ApiResponse<MedicalCategoryResponseDto>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody MedicalCategoryUpdateDto dto) {

        log.info("[MEDICAL-CATEGORIES] PUT /api/medical-categories/{}", id);

        MedicalCategoryResponseDto result = categoryService.update(id, dto);

        return ResponseEntity.ok(ApiResponse.success("Medical category updated successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete category", description = "Soft delete a medical category (sets active = false)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        log.info("[MEDICAL-CATEGORIES] DELETE /api/medical-categories/{}", id);
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("تم حذف التصنيف بنجاح", null));
    }

    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Hard delete category", description = "Permanently remove a medical category from the database. This action is irreversible.")
    public ResponseEntity<ApiResponse<Void>> hardDelete(@PathVariable("id") Long id) {
        log.info("[MEDICAL-CATEGORIES] DELETE /api/medical-categories/{}/hard", id);
        categoryService.hardDelete(id);
        return ResponseEntity.ok(ApiResponse.success("تم الحذف النهائي للتصنيف بنجاح", null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESTORE
    // ═══════════════════════════════════════════════════════════════════════════

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Restore category", description = "Restore a soft-deleted category (sets active = true, deleted = false)")
    public ResponseEntity<ApiResponse<MedicalCategoryResponseDto>> restore(@PathVariable("id") Long id) {
        log.info("[MEDICAL-CATEGORIES] PATCH /api/medical-categories/{}/restore", id);
        MedicalCategoryResponseDto result = categoryService.restore(id);
        return ResponseEntity.ok(ApiResponse.success("تم استعادة التصنيف بنجاح", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE
    // ═══════════════════════════════════════════════════════════════════════════

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Toggle category active state", description = "Enable or disable a category. Blocked if active specialties exist.")
    public ResponseEntity<ApiResponse<MedicalCategoryResponseDto>> toggle(@PathVariable("id") Long id) {
        log.info("[MEDICAL-CATEGORIES] PATCH /api/medical-categories/{}/toggle", id);
        MedicalCategoryResponseDto result = categoryService.toggle(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
