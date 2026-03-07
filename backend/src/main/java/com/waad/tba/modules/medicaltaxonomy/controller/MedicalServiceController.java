package com.waad.tba.modules.medicaltaxonomy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceResponseDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.medicaltaxonomy.service.MedicalServiceService;
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

import java.math.BigDecimal;

/**
 * REST Controller for Medical Service management (Reference Data).
 * 
 * Endpoints (9 total):
 * 1. POST /api/medical-services - Create service
 * 2. GET /api/medical-services/{id} - Get by ID
 * 3. GET /api/medical-services - List all (paginated)
 * 4. PUT /api/medical-services/{id} - Update service
 * 5. DELETE /api/medical-services/{id} - Soft delete
 * 6. GET /api/medical-services/code/{code} - Get by code
 * 7. GET /api/medical-services/category/{categoryId} - Get by category
 * 8. GET /api/medical-services/requires-pa - Get services requiring PA
 * 9. GET /api/medical-services/search - Advanced search
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/medical-services")
@RequiredArgsConstructor
@Tag(name = "Medical Service", description = "Medical service reference data management")
public class MedicalServiceController {

    private final MedicalServiceService serviceService;
    private final com.waad.tba.modules.medicaltaxonomy.service.MedicalServiceBulkImportService importService;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create medical service", description = "Create a new medical service")
    public ResponseEntity<ApiResponse<MedicalServiceResponseDto>> create(
            @Valid @RequestBody MedicalServiceCreateDto dto) {
        log.info("[MEDICAL-SERVICES] POST /api/medical-services - code={}", dto.getCode());

        MedicalServiceResponseDto result = serviceService.create(dto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Medical service created successfully", result));
    }

    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Bulk Import Medical Services", description = "Import services from Excel with high reliability and performance.")
    public ResponseEntity<ApiResponse<ExcelImportResultDto>> importServices(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {

        log.info("[MEDICAL-SERVICES] POST /api/medical-services/import - Bulk Mode");

        ExcelImportResultDto result = importService.importFromExcel(file);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get service by ID", description = "Retrieve a medical service by its ID")
    public ResponseEntity<ApiResponse<MedicalServiceResponseDto>> findById(@PathVariable Long id) {
        log.info("[MEDICAL-SERVICES] GET /api/medical-services/{}", id);

        MedicalServiceResponseDto result = serviceService.findById(id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "List all services", description = "Get paginated list of medical services with optional status filter")
    public ResponseEntity<ApiResponse<Page<MedicalServiceResponseDto>>> findAll(
            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(name = "sortBy", defaultValue = "code") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "ASC") String sortDir,
            @Parameter(description = "Filter by active status: true=active only, false=inactive only, null=all") @RequestParam(name = "active", required = false) Boolean active) {

        log.info("[MEDICAL-SERVICES] GET /api/medical-services - page={}, size={}, active={}", page, size, active);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<MedicalServiceResponseDto> result = serviceService.findAll(pageable, active);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get service statistics", description = "Get counts of active and inactive services")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStats() {
        log.info("[MEDICAL-SERVICES] GET /api/medical-services/stats");

        java.util.Map<String, Long> stats = serviceService.getStats();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get all services", description = "Get all active medical services (for dropdowns)")
    public ResponseEntity<ApiResponse<java.util.List<MedicalServiceResponseDto>>> findAllForDropdown() {
        log.info("[MEDICAL-SERVICES] GET /api/medical-services/all - For dropdown selectors");

        java.util.List<MedicalServiceResponseDto> result = serviceService.findAllActive();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOOKUP (For MedicalServiceSelector Component)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Lookup medical services", description = """
            Unified lookup endpoint for medical service selection.

            ARCHITECTURAL LAW: MedicalService MUST always be represented as:
              CODE + NAME + CATEGORY

            Features:
            - Search by: code, nameAr, nameEn, categoryNameAr, categoryNameEn
            - Optional filter by categoryId
            - Returns full context for display

            Display format: [SVC-001] أشعة مقطعية CT Scan - التصنيف: الأشعة التشخيصية

            Used in:
            - Provider Contract form (Pricing Item selector)
            - Benefit Policy Rule form (Service selector)
            - Provider Portal (Claim / PreAuth service lines)
            """)
    public ResponseEntity<ApiResponse<java.util.List<com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceLookupDto>>> lookup(
            @Parameter(description = "Search term (code, name, or category)") @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Filter by category ID") @RequestParam(name = "categoryId", required = false) Long categoryId) {

        log.info("[MEDICAL-SERVICES] GET /api/medical-services/lookup - q={}, categoryId={}", q, categoryId);

        var result = serviceService.lookup(q, categoryId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get service by code", description = "Retrieve a medical service by its unique code")
    public ResponseEntity<ApiResponse<MedicalServiceResponseDto>> findByCode(@PathVariable String code) {
        log.info("[MEDICAL-SERVICES] GET /api/medical-services/code/{}", code);

        MedicalServiceResponseDto result = serviceService.findByCode(code);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get services by category", description = "Get all services in a specific category")
    public ResponseEntity<ApiResponse<Page<MedicalServiceResponseDto>>> findByCategory(
            @PathVariable Long categoryId,
            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size) {

        log.info("[MEDICAL-SERVICES] GET /api/medical-services/category/{}", categoryId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
        Page<MedicalServiceResponseDto> result = serviceService.findByCategory(categoryId, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/requires-pa")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get services requiring PA", description = "Get all services that require pre-authorization")
    public ResponseEntity<ApiResponse<Page<MedicalServiceResponseDto>>> findServicesRequiringPA(
            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size) {

        log.info("[MEDICAL-SERVICES] GET /api/medical-services/requires-pa");

        Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
        Page<MedicalServiceResponseDto> result = serviceService.findServicesRequiringPA(pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Advanced search", description = "Search services with multiple filters")
    public ResponseEntity<ApiResponse<Page<MedicalServiceResponseDto>>> search(
            @Parameter(description = "Search term (name/nameEn)") @RequestParam(name = "searchTerm", required = false) String searchTerm,
            @Parameter(description = "Category ID filter") @RequestParam(name = "categoryId", required = false) Long categoryId,
            @Parameter(description = "Requires PA filter") @RequestParam(name = "requiresPA", required = false) Boolean requiresPA,
            @Parameter(description = "Minimum base price") @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum base price") @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size) {

        log.info(
                "[MEDICAL-SERVICES] GET /api/medical-services/search - term={}, category={}, requiresPA={}, price=[{}, {}]",
                searchTerm, categoryId, requiresPA, minPrice, maxPrice);

        Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
        Page<MedicalServiceResponseDto> result = serviceService.search(
                searchTerm, categoryId, requiresPA, minPrice, maxPrice, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update service", description = "Update an existing medical service (code is immutable)")
    public ResponseEntity<ApiResponse<MedicalServiceResponseDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody MedicalServiceUpdateDto dto) {

        log.info("[MEDICAL-SERVICES] PUT /api/medical-services/{}", id);

        MedicalServiceResponseDto result = serviceService.update(id, dto);

        return ResponseEntity.ok(ApiResponse.success("Medical service updated successfully", result));
    }

    @PatchMapping("/{id}/category")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Quick update category", description = "Update only the category of a medical service (inline edit)")
    public ResponseEntity<ApiResponse<MedicalServiceResponseDto>> updateCategory(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Long> payload) {

        Long categoryId = payload.get("categoryId");
        log.info("[MEDICAL-SERVICES] PATCH /api/medical-services/{}/category - newCategoryId={}", id, categoryId);

        MedicalServiceResponseDto result = serviceService.updateCategory(id, categoryId);

        return ResponseEntity.ok(ApiResponse.success("تم تحديث التصنيف بنجاح", result));
    }

    @PatchMapping("/bulk/category")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Bulk update category", description = "Update category for multiple services at once")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> bulkUpdateCategory(
            @RequestBody java.util.Map<String, Object> payload) {

        @SuppressWarnings("unchecked")
        java.util.List<Integer> serviceIdInts = (java.util.List<Integer>) payload.get("serviceIds");
        java.util.List<Long> serviceIds = serviceIdInts.stream()
                .map(Integer::longValue)
                .toList();
        Long categoryId = ((Number) payload.get("categoryId")).longValue();

        log.info("[MEDICAL-SERVICES] PATCH /api/medical-services/bulk/category - count={}, categoryId={}",
                serviceIds.size(), categoryId);

        java.util.Map<String, Object> result = serviceService.bulkUpdateCategory(serviceIds, categoryId);

        return ResponseEntity.ok(ApiResponse.success(
                "تم تحديث التصنيف لـ " + result.get("updated") + " خدمة",
                result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE
    // ═══════════════════════════════════════════════════════════════════════════

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Toggle service active state", description = "Flip active/inactive state for a medical service (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<MedicalServiceResponseDto>> toggle(@PathVariable Long id) {
        log.info("[MEDICAL-SERVICES] PATCH /api/v1/medical-services/{}/toggle", id);
        MedicalServiceResponseDto result = serviceService.toggle(id);
        String msg = result.isActive() ? "تم تفعيل الخدمة الطبية" : "تم إلغاء تفعيل الخدمة الطبية";
        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete service", description = "Soft delete a medical service (sets active = false)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("[MEDICAL-SERVICES] DELETE /api/medical-services/{}", id);

        serviceService.delete(id);

        return ResponseEntity.ok(ApiResponse.success("Medical service deleted successfully", null));
    }

    @PutMapping("/bulk/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Deactivate all services", description = "Set active = false for ALL medical services.")
    public ResponseEntity<ApiResponse<Integer>> deactivateAll() {
        log.warn("[MEDICAL-SERVICES] ⚠️ PUT /api/medical-services/bulk/deactivate - Bulk deactivate requested!");

        int count = serviceService.deactivateAll();

        return ResponseEntity.ok(ApiResponse.success(
                "تم إلغاء تنشيط " + count + " خدمة طبية بنجاح",
                count));
    }

    @PutMapping("/bulk/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activate all services", description = "Set active = true for ALL medical services.")
    public ResponseEntity<ApiResponse<Integer>> activateAll() {
        log.warn("[MEDICAL-SERVICES] ✅ PUT /api/medical-services/bulk/activate - Bulk activate requested!");

        int count = serviceService.activateAll();

        return ResponseEntity.ok(ApiResponse.success(
                "تم تنشيط " + count + " خدمة طبية بنجاح",
                count));
    }

    @DeleteMapping("/bulk/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Permanently delete all services", description = "⚠️ DANGER: Permanently delete ALL medical services. This is IRREVERSIBLE!")
    public ResponseEntity<ApiResponse<Integer>> deleteAll(
            @RequestParam(name = "confirm", defaultValue = "false") boolean confirm) {

        if (!confirm) {
            log.warn("[MEDICAL-SERVICES] Permanent delete attempted without confirmation");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("يجب تأكيد الحذف الدائم بإضافة ?confirm=true"));
        }

        log.warn("[MEDICAL-SERVICES] ⚠️🚨 DELETE /api/medical-services/bulk/all - PERMANENT delete requested!");

        int count = serviceService.permanentDeleteAll();

        return ResponseEntity.ok(ApiResponse.success(
                "⚠️ تم حذف " + count + " خدمة طبية نهائياً",
                count));
    }
}
