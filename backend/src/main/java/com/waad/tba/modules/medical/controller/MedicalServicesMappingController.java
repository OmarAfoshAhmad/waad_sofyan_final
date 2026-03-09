package com.waad.tba.modules.medical.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medical.dto.CreateAndMapRequest;
import com.waad.tba.modules.medical.dto.LinkAndMapRequest;
import com.waad.tba.modules.medical.dto.MappingStatsDto;
import com.waad.tba.modules.medical.dto.RawServiceDto;
import com.waad.tba.modules.medical.service.MedicalServicesMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Medical Services Mapping REST API.
 *
 * <p>
 * Base path: {@code /api/v1/medical-services-mapping}
 *
 * <p>
 * Access: SUPER_ADMIN and DATA_ENTRY only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/medical-services-mapping")
@RequiredArgsConstructor
@Tag(name = "Medical Services Mapping", description = "Create and link unified medical services from provider raw data")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ENTRY')")
public class MedicalServicesMappingController {

    private final MedicalServicesMappingService mappingService;

    // ── Stats ──────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "Mapping statistics", description = "Aggregate counts: total, pending, mapped, rejected raw services")
    public ResponseEntity<ApiResponse<MappingStatsDto>> getStats() {
        log.info("[SERVICES-MAPPING] GET /stats");
        return ResponseEntity.ok(ApiResponse.success(mappingService.getStats()));
    }

    // ── Create new service + map ───────────────────────────────────────────────

    @PostMapping("/create-and-map")
    @Operation(summary = "Create medical service and map raw services", description = "Creates a new ACTIVE medical service and maps the selected raw provider services to it")
    public ResponseEntity<ApiResponse<List<RawServiceDto>>> createAndMap(
            @Valid @RequestBody CreateAndMapRequest req) {

        log.info("[SERVICES-MAPPING] POST /create-and-map code={} rawIds={}",
                req.getCode(), req.getRawServiceIds());

        List<RawServiceDto> result = mappingService.createAndMap(req);
        return ResponseEntity.ok(ApiResponse.success(
                "تم إنشاء الخدمة وتعيين الخدمات الخام بنجاح", result));
    }

    // ── Link to existing service + map ─────────────────────────────────────────

    @PostMapping("/link-and-map")
    @Operation(summary = "Link raw services to an existing medical service", description = "Maps the selected raw provider services to an already-existing unified medical service")
    public ResponseEntity<ApiResponse<List<RawServiceDto>>> linkAndMap(
            @Valid @RequestBody LinkAndMapRequest req) {

        log.info("[SERVICES-MAPPING] POST /link-and-map medicalServiceId={} rawIds={}",
                req.getMedicalServiceId(), req.getRawServiceIds());

        List<RawServiceDto> result = mappingService.linkAndMap(req);
        return ResponseEntity.ok(ApiResponse.success(
                "تم تعيين الخدمات الخام بنجاح", result));
    }
}
