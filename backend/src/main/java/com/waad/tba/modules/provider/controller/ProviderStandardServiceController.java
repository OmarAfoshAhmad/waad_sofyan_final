package com.waad.tba.modules.provider.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesRequestDto;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesSummaryDto;
import com.waad.tba.modules.provider.dto.RevokeStandardServicesSummaryDto;
import com.waad.tba.modules.provider.dto.StandardServiceCreateDto;
import com.waad.tba.modules.provider.dto.StandardServiceDto;
import com.waad.tba.modules.provider.dto.StandardServiceUpdateDto;
import com.waad.tba.modules.provider.service.ProviderStandardServiceProvisioner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Bulk-assigning the standard (invoice-priced) services across many
 * providers is a distinct, high-blast-radius administrative action from
 * managing one provider's own record -- it gets its own permission
 * (PROVIDER_STANDARD_SERVICES_MANAGE) rather than riding on PROVIDER_MANAGE.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-standard-services")
@RequiredArgsConstructor
@PreAuthorize("@permissionGuard.has('PROVIDER_STANDARD_SERVICES_MANAGE')")
public class ProviderStandardServiceController {

    private final ProviderStandardServiceProvisioner provisioner;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StandardServiceDto>>> list() {
        return ResponseEntity.ok(ApiResponse.success(provisioner.listStandardServices()));
    }

    /** Includes inactive services -- the admin catalog-management table, not the assignment picker above. */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<StandardServiceDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(provisioner.listAllStandardServices()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StandardServiceDto>> create(
            @Valid @RequestBody StandardServiceCreateDto request) {
        return ResponseEntity.ok(ApiResponse.success(provisioner.createStandardService(request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<StandardServiceDto>> update(
            @PathVariable Long id, @Valid @RequestBody StandardServiceUpdateDto request) {
        return ResponseEntity.ok(ApiResponse.success(provisioner.updateStandardService(id, request)));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<ProvisionStandardServicesSummaryDto>> preview(
            @Valid @RequestBody ProvisionStandardServicesRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(provisioner.preview(request)));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ProvisionStandardServicesSummaryDto>> apply(
            @Valid @RequestBody ProvisionStandardServicesRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(provisioner.apply(request)));
    }

    @PostMapping("/revoke/preview")
    public ResponseEntity<ApiResponse<RevokeStandardServicesSummaryDto>> previewRevoke(
            @Valid @RequestBody ProvisionStandardServicesRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(provisioner.previewRevoke(request)));
    }

    @PostMapping("/revoke/apply")
    public ResponseEntity<ApiResponse<RevokeStandardServicesSummaryDto>> applyRevoke(
            @Valid @RequestBody ProvisionStandardServicesRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(provisioner.revoke(request)));
    }
}
