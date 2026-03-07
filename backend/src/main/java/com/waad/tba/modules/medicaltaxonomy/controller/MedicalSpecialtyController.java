package com.waad.tba.modules.medicaltaxonomy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalSpecialtyCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalSpecialtyDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalSpecialtyUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.service.MedicalSpecialtyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Medical Specialties management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/v1/medical-specialties                — list all active</li>
 *   <li>GET    /api/v1/medical-specialties?categoryId=X  — filter by category</li>
 *   <li>POST   /api/v1/medical-specialties                — create (SUPER_ADMIN)</li>
 *   <li>PUT    /api/v1/medical-specialties/{id}           — update (SUPER_ADMIN)</li>
 *   <li>PATCH  /api/v1/medical-specialties/{id}/toggle    — soft toggle (SUPER_ADMIN)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/medical-specialties")
@RequiredArgsConstructor
public class MedicalSpecialtyController {

    private final MedicalSpecialtyService specialtyService;

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ENTRY','MEDICAL_REVIEWER'," +
                  "'ACCOUNTANT','PROVIDER_STAFF','EMPLOYER_ADMIN','FINANCE_VIEWER')")
    public ResponseEntity<ApiResponse<List<MedicalSpecialtyDto>>> listAll(
            @RequestParam(name = "categoryId", required = false) Long categoryId) {

        List<MedicalSpecialtyDto> data = categoryId != null
                ? specialtyService.listByCategory(categoryId)
                : specialtyService.listActive();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MedicalSpecialtyDto>> create(
            @Valid @RequestBody MedicalSpecialtyCreateDto dto) {

        MedicalSpecialtyDto result = specialtyService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MedicalSpecialtyDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody MedicalSpecialtyUpdateDto dto) {

        MedicalSpecialtyDto result = specialtyService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── TOGGLE ────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MedicalSpecialtyDto>> toggle(@PathVariable Long id) {
        MedicalSpecialtyDto result = specialtyService.toggle(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
