package com.waad.tba.modules.medicalpackage;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.dto.PaginationResponse;
import com.waad.tba.modules.medicalpackage.dto.MedicalPackageSelectorDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/medical-packages")
@RequiredArgsConstructor
@Tag(name = "Medical Packages", description = "APIs for managing medical packages")
public class MedicalPackageController {

    private final MedicalPackageService service;

    @GetMapping("/selector")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Get medical package selector options", description = "Returns active medical packages for dropdown/selector")
    public ResponseEntity<ApiResponse<List<MedicalPackageSelectorDto>>> getSelectorOptions() {
        List<MedicalPackageSelectorDto> options = service.getSelectorOptions();
        return ResponseEntity.ok(ApiResponse.success(options));
    }

    /**
     * GET /api/medical-packages
     * Get all medical packages with pagination
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "List medical packages with pagination", description = "Returns paginated list of medical packages with optional search")
    public ResponseEntity<ApiResponse<PaginationResponse<MedicalPackage>>> list(
            @Parameter(description = "Page number (1-based)") @RequestParam(name = "page", defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "10") int size,
            @Parameter(description = "Search query") @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "Sort by field") @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), size, sort);

        Page<MedicalPackage> pageResult = service.findAllPaginated(pageRequest, search);

        PaginationResponse<MedicalPackage> response = PaginationResponse.<MedicalPackage>builder()
                .items(pageResult.getContent())
                .total(pageResult.getTotalElements())
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/medical-packages/{id}
     * Get medical package by ID
     */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalPackage>> getById(@PathVariable Long id) {
        try {
            MedicalPackage medicalPackage = service.findById(id);
            return ResponseEntity.ok(
                    ApiResponse.success("Medical package retrieved successfully", medicalPackage));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Medical package not found: " + e.getMessage()));
        }
    }

    /**
     * GET /api/medical-packages/code/{code}
     * Get medical package by code
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalPackage>> getByCode(@PathVariable String code) {
        try {
            MedicalPackage medicalPackage = service.findByCode(code);
            return ResponseEntity.ok(
                    ApiResponse.success("Medical package retrieved successfully", medicalPackage));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Medical package not found: " + e.getMessage()));
        }
    }

    /**
     * GET /api/medical-packages/active
     * Get active medical packages only
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<List<MedicalPackage>>> getActive() {
        try {
            List<MedicalPackage> packages = service.findActive();
            return ResponseEntity.ok(
                    ApiResponse.success("Active medical packages retrieved successfully", packages));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve active medical packages: " + e.getMessage()));
        }
    }

    /**
     * POST /api/medical-packages
     * Create new medical package
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create medical package")
    public ResponseEntity<ApiResponse<MedicalPackage>> create(@Valid @RequestBody MedicalPackageDTO dto) {
        MedicalPackage created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Medical package created successfully", created));
    }

    /**
     * PUT /api/medical-packages/{id}
     * Update existing medical package
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update medical package")
    public ResponseEntity<ApiResponse<MedicalPackage>> update(@PathVariable Long id,
            @Valid @RequestBody MedicalPackageDTO dto) {
        MedicalPackage updated = service.update(id, dto);
        return ResponseEntity.ok(
                ApiResponse.success("Medical package updated successfully", updated));
    }

    /**
     * DELETE /api/medical-packages/{id}
     * Delete medical package
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete medical package")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success("Medical package deleted successfully", null));
    }

    /**
     * GET /api/medical-packages/count
     * Get total count of medical packages
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Count medical packages")
    public ResponseEntity<ApiResponse<Long>> count() {
        Long count = service.count();
        return ResponseEntity.ok(
                ApiResponse.success("Package count retrieved successfully", count));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Search medical packages")
    public ResponseEntity<ApiResponse<List<MedicalPackage>>> search(@RequestParam(name = "query") String query) {
        List<MedicalPackage> results = service.search(query);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
