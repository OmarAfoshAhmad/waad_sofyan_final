package com.waad.tba.modules.employer.controller;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.waad.tba.modules.employer.dto.EmployerCreateDto;
import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.dto.EmployerSelectorDto;
import com.waad.tba.modules.employer.dto.EmployerUpdateDto;
import com.waad.tba.modules.employer.service.EmployerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/employers")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class EmployerController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "code", "name", "email", "active", "createdAt", "updatedAt");

    private final EmployerService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    public ResponseEntity<ApiResponse<Page<EmployerResponseDto>>> getAll(
            @RequestParam(name = "includeArchived", required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(name = "archivedOnly", required = false, defaultValue = "false") boolean archivedOnly,
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            @RequestParam(name = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(name = "sortDir", required = false, defaultValue = "ASC") String sortDir) {

        String safeSort = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "name";
        Sort.Direction direction;
        try { direction = Sort.Direction.fromString(sortDir); }
        catch (IllegalArgumentException ex) { direction = Sort.Direction.ASC; }
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by(direction, safeSort));

        Boolean activeFilter = archivedOnly ? Boolean.FALSE : includeArchived ? null : Boolean.TRUE;
        Page<EmployerResponseDto> employers = service.getPage(activeFilter, query, pageable);

        return ResponseEntity.ok(ApiResponse.success(employers));
    }

    @GetMapping({ "selectors", "/selector" })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'FINANCE_VIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<EmployerSelectorDto>>> selectors() {
        List<EmployerSelectorDto> selectors = service.getSelectors();
        return ResponseEntity.ok(ApiResponse.success(selectors));
    }

    @GetMapping("selectors/with-members")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'FINANCE_VIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<EmployerSelectorDto>>> selectorsWithMembers() {
        List<EmployerSelectorDto> selectors = service.getSelectorsWithMembers();
        return ResponseEntity.ok(ApiResponse.success(selectors));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EmployerResponseDto>> getById(@PathVariable("id") Long id) {
        EmployerResponseDto employer = service.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Employer retrieved successfully", employer));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EmployerResponseDto>> create(@Valid @RequestBody EmployerCreateDto dto) {
        try {
            EmployerResponseDto created = service.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Employer created successfully", created));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EmployerResponseDto>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody EmployerUpdateDto dto) {
        EmployerResponseDto updated = service.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Employer updated successfully", updated));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Employer deleted successfully", null));
    }

    /**
     * Archive employer (safe alternative to delete)
     * Sets archived=true, hiding from default lists while preserving all data
     */
    @PostMapping("/{id:\\d+}/archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EmployerResponseDto>> archive(@PathVariable("id") Long id) {
        EmployerResponseDto archived = service.archive(id);
        return ResponseEntity.ok(ApiResponse.success("Employer archived successfully", archived));
    }

    /**
     * Restore archived employer
     * Sets archived=false, making employer visible again
     */
    @PostMapping("/{id:\\d+}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EmployerResponseDto>> restore(@PathVariable("id") Long id) {
        EmployerResponseDto restored = service.restore(id);
        return ResponseEntity.ok(ApiResponse.success("Employer restored successfully", restored));
    }

    @PostMapping("/bulk-archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<com.waad.tba.modules.employer.dto.BulkEmployerResultDto>> bulkArchive(
            @RequestBody List<Long> ids) {
        var result = service.bulkArchive(ids);
        return ResponseEntity.ok(ApiResponse.success(
                String.format("تم حذف %d من %d جهة عمل بنجاح", result.getSuccessCount(), result.getTotalCount()),
                result));
    }

    @PostMapping("/bulk-restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<com.waad.tba.modules.employer.dto.BulkEmployerResultDto>> bulkRestore(
            @RequestBody List<Long> ids) {
        var result = service.bulkRestore(ids);
        return ResponseEntity.ok(ApiResponse.success(
                String.format("تمت استعادة %d من %d جهة عمل بنجاح", result.getSuccessCount(), result.getTotalCount()),
                result));
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> count() {
        long total = service.count();
        return ResponseEntity.ok(ApiResponse.success(total));
    }

    /**
     * Live-check field availability (used for instant feedback while typing).
     * field = "code" | "name"
     * excludeId is optional (omit on create, pass self-id on edit)
     */
    @GetMapping("/check")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> check(
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam(required = false) Long excludeId) {
        boolean available = switch (field) {
            case "code" -> service.isCodeAvailable(value, excludeId);
            case "name" -> service.isNameAvailable(value, excludeId);
            default -> true;
        };
        return ResponseEntity.ok(ApiResponse.success(available));
    }
}
