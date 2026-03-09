package com.waad.tba.modules.medical.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medical.dto.ManualMapRequest;
import com.waad.tba.modules.medical.dto.RawServiceDto;
import com.waad.tba.modules.medical.enums.MappingStatus;
import com.waad.tba.modules.medical.service.ProviderMappingService;
import com.waad.tba.modules.provider.dto.ProviderSelectorDto;
import com.waad.tba.security.AuthorizationService;
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
 * Provider Mapping Center — REST API.
 *
 * <p>
 * Base path: {@code /api/v1/provider-mapping}
 *
 * <p>
 * Access: SUPER_ADMIN and DATA_ENTRY only (no new roles introduced).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-mapping")
@RequiredArgsConstructor
@Tag(name = "Provider Mapping Center", description = "Map raw provider service names to canonical medical_services")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ENTRY')")
public class ProviderMappingController {

    private final ProviderMappingService mappingService;
    private final AuthorizationService authorizationService;

    // ═══════════════════════════════════════════════════════════════════════
    // GET /providers-active
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/providers-active")
    @Operation(summary = "List providers with active contracts", description = "Returns only providers that have at least one ACTIVE contract. Used to populate the provider selector in the Mapping Center.")
    public ResponseEntity<ApiResponse<List<ProviderSelectorDto>>> getProvidersWithActiveContracts() {
        log.info("[MAPPING] GET /provider-mapping/providers-active");
        return ResponseEntity.ok(ApiResponse.success(
                mappingService.getProvidersWithActiveContracts()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /raw?providerId=X&status=PENDING
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/raw")
    @Operation(summary = "List raw services for a provider", description = "Returns raw service names optionally filtered by mapping status")
    public ResponseEntity<ApiResponse<List<RawServiceDto>>> getRawServices(
            @RequestParam(name = "providerId") Long providerId,
            @RequestParam(name = "status", required = false, defaultValue = "PENDING") String status) {

        MappingStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = MappingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // invalid enum value — return all
            }
        }

        List<RawServiceDto> result = mappingService.getRawServices(providerId, statusEnum);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /auto-match/{rawId}
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/auto-match/{rawId}")
    @Operation(summary = "Auto-match a raw service", description = "Performs exact-match lookup against medical_services code/name/alias")
    public ResponseEntity<ApiResponse<RawServiceDto>> autoMatch(
            @PathVariable("rawId") Long rawId) {

        RawServiceDto result = mappingService.autoMatch(rawId);
        return ResponseEntity.ok(ApiResponse.success("Auto-match completed", result));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /manual-map
    // Body: { "rawId": 1, "medicalServiceId": 55 }
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/manual-map")
    @Operation(summary = "Manually map a raw service to a medical service", description = "Sets status to MANUAL_CONFIRMED and writes an audit entry")
    public ResponseEntity<ApiResponse<RawServiceDto>> manualMap(
            @Valid @RequestBody ManualMapRequest request) {

        Long userId = currentUserId();
        RawServiceDto result = mappingService.manualMap(
                request.getRawId(), request.getMedicalServiceId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Manual mapping confirmed", result));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /reject/{rawId}
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/reject/{rawId}")
    @Operation(summary = "Reject a raw service mapping", description = "Marks the service as REJECTED; writes an audit entry")
    public ResponseEntity<ApiResponse<RawServiceDto>> reject(
            @PathVariable("rawId") Long rawId) {

        Long userId = currentUserId();
        RawServiceDto result = mappingService.rejectMapping(rawId, userId);
        return ResponseEntity.ok(ApiResponse.success("Service mapping rejected", result));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════════════

    /** Resolve operator ID from JWT principal via existing AuthorizationService */
    private Long currentUserId() {
        var user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return user.getId();
    }
}

