package com.waad.tba.modules.provider.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.dto.PaginationResponse;
import com.waad.tba.modules.provider.dto.*;
import com.waad.tba.modules.provider.service.ProviderService;
import com.waad.tba.modules.provider.service.ProviderServiceService;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.provider.service.ProviderAdminDocumentService;
import com.waad.tba.security.AuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderServiceService providerServiceService;
    private final ProviderContractService providerContractService;
    private final ProviderAdminDocumentService providerAdminDocumentService;
    private final AuthorizationService authorizationService;
    private final com.waad.tba.modules.claim.service.ReviewerProviderIsolationService reviewerIsolationService;

    /**
     * Get provider selector options with pagination
     * 
     * PHASE 3 REVIEW (Issue D): Added pagination to prevent technical debt.
     * Defaults to 1000 items per page to maintain backward compatibility,
     * but allows pagination for larger datasets.
     * 
     * @param page Page number (default: 1)
     * @param size Items per page (default: 1000, max: 1000)
     * @return Paginated list of provider selector options
     */
    @GetMapping("/selector")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    public ResponseEntity<ApiResponse<PaginationResponse<ProviderSelectorDto>>> getSelectorOptions(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "1000") int size) {

        // Cap maximum size at 1000
        size = Math.min(size, 1000);

        Page<ProviderSelectorDto> options = providerService.getSelectorOptions(Math.max(0, page - 1), size);

        var currentUser = authorizationService.getCurrentUser();

        // Filter for Provider Users
        if (currentUser != null && authorizationService.isProvider(currentUser)) {
            Long providerId = authorizationService.getProviderFilterForUser(currentUser);
            if (providerId != null) {
                List<ProviderSelectorDto> filtered = options.getContent().stream()
                        .filter(p -> p.getId().equals(providerId))
                        .collect(Collectors.toList());

                PaginationResponse<ProviderSelectorDto> response = PaginationResponse.<ProviderSelectorDto>builder()
                        .items(filtered)
                        .total((long) filtered.size())
                        .page(page)
                        .size(size)
                        .build();

                return ResponseEntity.ok(ApiResponse.success(response));
            }
        }

        // Filter for Medical Reviewers (Phase 11 Isolation)
        if (currentUser != null && "MEDICAL_REVIEWER".equals(currentUser.getUserType())) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            List<ProviderSelectorDto> filtered = options.getContent().stream()
                    .filter(p -> allowedProviderIds.contains(p.getId()))
                    .collect(Collectors.toList());

            PaginationResponse<ProviderSelectorDto> response = PaginationResponse.<ProviderSelectorDto>builder()
                    .items(filtered)
                    .total((long) filtered.size())
                    .page(page)
                    .size(size)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        }

        PaginationResponse<ProviderSelectorDto> response = PaginationResponse.<ProviderSelectorDto>builder()
                .items(options.getContent())
                .total(options.getTotalElements())
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderViewDto>> createProvider(@Valid @RequestBody ProviderCreateDto dto) {
        ProviderViewDto provider = providerService.createProvider(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Provider created successfully", provider));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderViewDto>> updateProvider(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProviderUpdateDto dto) {
        ProviderViewDto provider = providerService.updateProvider(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Provider updated successfully", provider));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'DATA_ENTRY', 'PROVIDER_STAFF', 'EMPLOYER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderViewDto>> getProvider(@PathVariable("id") Long id) {
        var currentUser = authorizationService.getCurrentUser();
        if (authorizationService.isProvider(currentUser) && !authorizationService.canAccessProvider(currentUser, id)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Provider users can only view their own provider record");
        }
        ProviderViewDto provider = providerService.getProvider(id);
        return ResponseEntity.ok(ApiResponse.success("Provider retrieved successfully", provider));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginationResponse<ProviderViewDto>>> listProviders(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "providerType", required = false) String providerType,
            @RequestParam(name = "networkStatus", required = false) String networkStatus,
            @RequestParam(name = "status", required = false) String status) {
        Page<ProviderViewDto> providers = providerService.listProviders(Math.max(0, page - 1), size, search, active,
                providerType, networkStatus, status);

        PaginationResponse<ProviderViewDto> response = PaginationResponse.<ProviderViewDto>builder()
                .items(providers.getContent())
                .total(providers.getTotalElements())
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Deactivate a provider (soft delete only)
     * 
     * PHASE 3 REVIEW: Hard delete removed due to FK RESTRICT constraints.
     * Providers with claims, accounts, or legacy contracts cannot be deleted.
     * Use soft delete (active=false) instead to preserve data integrity.
     */
    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateProvider(@PathVariable("id") Long id) {
        providerService.deactivateProvider(id);
        return ResponseEntity.ok(ApiResponse.success("Provider deactivated successfully", null));
    }

    @PutMapping("/{id:\\d+}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderViewDto>> restoreProvider(@PathVariable("id") Long id) {
        ProviderViewDto provider = providerService.restoreProvider(id);
        return ResponseEntity.ok(ApiResponse.success("Provider restored successfully", provider));
    }

    @DeleteMapping("/{id:\\d+}/hard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> hardDeleteProvider(@PathVariable("id") Long id) {
        providerService.hardDeleteProvider(id);
        return ResponseEntity.ok(ApiResponse.success("Provider permanently deleted", null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/bulk-deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkDeactivateProviders(@RequestBody List<Long> ids) {
        providerService.bulkDeactivateProviders(ids);
        return ResponseEntity.ok(ApiResponse.success("Providers and empty contracts deactivated successfully", null));
    }

    @PostMapping("/bulk-hard-delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkHardDeleteProviders(@RequestBody List<Long> ids) {
        providerService.bulkHardDeleteProviders(ids);
        return ResponseEntity.ok(ApiResponse.success("Providers and empty contracts permanently deleted", null));
    }

    @PostMapping("/bulk-restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkRestoreProviders(@RequestBody List<Long> ids) {
        providerService.bulkRestoreProviders(ids);
        return ResponseEntity.ok(ApiResponse.success("Providers restored successfully", null));
    }

    @PostMapping("/bulk-allow-all-employers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkAllowAllEmployers(@RequestBody List<Long> ids) {
        providerService.bulkAllowAllEmployers(ids);
        return ResponseEntity.ok(ApiResponse.success("Employers allowed successfully for selected providers", null));
    }

    @PostMapping("/bulk-remove-employers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkRemoveEmployers(@RequestBody List<Long> ids) {
        providerService.bulkRemoveEmployers(ids);
        return ResponseEntity.ok(ApiResponse.success("Employers removed successfully for selected providers", null));
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProviderViewDto>>> getAllActiveProviders() {
        List<ProviderViewDto> providers = providerService.getAllActiveProviders();
        return ResponseEntity.ok(ApiResponse.success("Active providers retrieved successfully", providers));
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> countProviders() {
        long count = providerService.countProviders();
        return ResponseEntity.ok(ApiResponse.success("Provider count retrieved successfully", count));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProviderViewDto>>> search(@RequestParam(name = "query") String query) {
        List<ProviderViewDto> results = providerService.search(query);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SERVICE ASSIGNMENT ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Assign a medical service to a provider
     */
    @PostMapping("/{id}/services")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderServiceResponseDto>> assignService(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProviderServiceAssignDto dto) {

        log.info("[PROVIDER-SERVICES] POST /api/providers/{}/services - serviceCode={}",
                id, dto.getServiceCode());

        ProviderServiceResponseDto result = providerServiceService.assignService(id, dto);

        return ResponseEntity.ok(ApiResponse.success("Service assigned successfully", result));
    }

    /**
     * Remove a service from a provider
     */
    @DeleteMapping("/{id}/services/{serviceCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeService(
            @PathVariable("id") Long id,
            @PathVariable("serviceCode") String serviceCode) {

        log.info("[PROVIDER-SERVICES] DELETE /api/providers/{}/services/{}", id, serviceCode);

        providerServiceService.removeService(id, serviceCode);

        return ResponseEntity.ok(ApiResponse.success("Service removed successfully", null));
    }

    /**
     * Get all services offered by a provider
     */
    @GetMapping("/{id}/services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<List<ProviderServiceResponseDto>>> getProviderServices(
            @PathVariable("id") Long id) {

        log.info("[PROVIDER-SERVICES] GET /api/providers/{}/services", id);

        List<ProviderServiceResponseDto> services = providerServiceService.getProviderServices(id);

        return ResponseEntity.ok(ApiResponse.success(services));
    }

    /**
     * Get service codes for a provider (lightweight)
     */
    @GetMapping("/{id}/service-codes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<List<String>>> getProviderServiceCodes(@PathVariable("id") Long id) {
        log.info("[PROVIDER-SERVICES] GET /api/providers/{}/service-codes", id);

        List<String> serviceCodes = providerServiceService.getProviderServiceCodes(id);

        return ResponseEntity.ok(ApiResponse.success(serviceCodes));
    }

    /**
     * Check if provider offers a specific service
     */
    @GetMapping("/{id}/services/{serviceCode}/check")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Boolean>> checkProviderService(
            @PathVariable("id") Long id,
            @PathVariable("serviceCode") String serviceCode) {

        log.info("[PROVIDER-SERVICES] GET /api/providers/{}/services/{}/check", id, serviceCode);

        boolean offers = providerServiceService.providerOffersService(id, serviceCode);

        return ResponseEntity.ok(ApiResponse.success(offers));
    }

    // ==================== PROVIDER CONTRACT ENDPOINTS ====================
    //
    // NOTE (2026-07-27): the legacy_provider_contracts-backed CRUD and list
    // endpoints were removed entirely — confirmed zero frontend callers and
    // zero live data (0 rows) before deletion. All contract management now
    // goes through /api/v1/provider-contracts (the modern module). What
    // remains here (getEffectivePrice, getServicesRequiringPreAuth) reads
    // exclusively from the modern provider_contract_pricing_items table.

    /**
     * Get effective price for a service on a specific date
     */
    @GetMapping("/{id}/services/{serviceCode}/price")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<EffectivePriceResponseDto>> getEffectivePrice(
            @PathVariable("id") Long id,
            @PathVariable("serviceCode") String serviceCode,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {

        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/services/{}/price?date={}",
                id, serviceCode, date);

        EffectivePriceResponseDto price = providerContractService.getEffectivePrice(id, serviceCode, date);

        return ResponseEntity.ok(ApiResponse.success(price));
    }

    /**
     * Get services requiring pre-approval for a member from provider's active
     * contract.
     * 
     * This endpoint returns ONLY services that:
     * 1. Are in the provider's active contract (with contract pricing)
     * 2. Require pre-approval based on the MEMBER's benefit policy rules
     * 
     * GET /api/providers/{id}/contract/services/requiring-preauth?memberId=X
     */
    @GetMapping("/{id}/contract/services/requiring-preauth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<java.util.List<ProviderServiceDto>>> getServicesRequiringPreAuth(
            @PathVariable("id") Long id,
            @RequestParam(name = "memberId") Long memberId) {

        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/contract/services/requiring-preauth?memberId={}",
                id, memberId);

        java.util.List<ProviderServiceDto> services = providerContractService.getServicesRequiringPreAuth(id, memberId);

        return ResponseEntity.ok(ApiResponse.success(
                "Services requiring pre-approval retrieved",
                services));
    }

    /**
     * Get allowed employer IDs for a provider
     * Used in provider management to show partner permissions
     * 
     * GET /api/providers/{id}/allowed-employers-ids
     */
    @GetMapping("/{id}/allowed-employers-ids")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Long>>> getAllowedEmployerIds(@PathVariable("id") Long id) {
        log.info("[PROVIDER] GET /api/providers/{}/allowed-employers-ids", id);
        List<Long> employerIds = providerService.getAllowedEmployerIds(id);
        return ResponseEntity.ok(ApiResponse.success("Allowed employers retrieved", employerIds));
    }

    /**
     * Get administrative documents for a provider
     * 
     * GET /api/providers/{id}/documents
     */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProviderAdminDocumentResponseDto>>> getProviderDocuments(
            @PathVariable("id") Long id) {
        log.info("[PROVIDER] GET /api/providers/{}/documents", id);
        List<ProviderAdminDocumentResponseDto> documents = providerAdminDocumentService.getDocumentsByProviderId(id);
        return ResponseEntity.ok(ApiResponse.success("Documents retrieved successfully", documents));
    }

    /**
     * Add administrative document for a provider
     * 
     * POST /api/providers/{id}/documents
     */
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderAdminDocumentResponseDto>> addProviderDocument(
            @PathVariable("id") Long id,
            @RequestPart("data") @Valid ProviderAdminDocumentCreateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        log.info("[PROVIDER] POST /api/providers/{}/documents - type: {}", id, dto.getType());

        ProviderAdminDocumentResponseDto document = providerAdminDocumentService.createDocument(id, dto, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document added successfully", document));
    }

    /**
     * Download/preview an administrative document's raw bytes (authorized —
     * this is the only path that can ever read the underlying stored file).
     *
     * GET /api/providers/{providerId}/documents/{docId}/download
     */
    @GetMapping("/{providerId}/documents/{docId}/download")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadProviderDocument(
            @PathVariable("providerId") Long providerId,
            @PathVariable("docId") Long docId) {

        var download = providerAdminDocumentService.downloadDocument(providerId, docId);
        String encodedFileName = java.net.URLEncoder.encode(download.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(download.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedFileName)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }

    /**
     * Delete administrative document
     *
     * DELETE /api/providers/{providerId}/documents/{docId}
     */
    @DeleteMapping("/{providerId}/documents/{docId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProviderDocument(
            @PathVariable("providerId") Long providerId,
            @PathVariable("docId") Long docId) {

        log.info("[PROVIDER] DELETE /api/providers/{}/documents/{}", providerId, docId);

        providerAdminDocumentService.deleteDocument(providerId, docId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDER-PARTNER ISOLATION ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get allowed employers for a provider
     */
    @GetMapping("/{id}/allowed-employers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<AllowedEmployerDto>>> getAllowedEmployers(@PathVariable("id") Long id) {
        // Security check: if provider user, ensure accessing own provider
        var currentUser = authorizationService.getCurrentUser();
        if (authorizationService.isProvider(currentUser)) {
            Long userProviderId = authorizationService.getProviderFilterForUser(currentUser);
            if (userProviderId != null && !userProviderId.equals(id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied"));
            }
        }

        List<AllowedEmployerDto> employers = providerService.getAllowedEmployers(id);
        return ResponseEntity.ok(ApiResponse.success(employers));
    }

    /**
     * Update allowed employers for a provider
     */
    @PutMapping("/{id}/allowed-employers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateAllowedEmployers(
            @PathVariable("id") Long id,
            @RequestBody List<Long> employerIds) {

        providerService.updateAllowedEmployers(id, employerIds);
        return ResponseEntity.ok(ApiResponse.success("Allowed employers updated successfully", null));
    }

    /**
     * Get all providers allowed for a specific employer.
     * Use case: Claims Batch System (Card view).
     * 
     * GET /api/v1/providers/by-employer/{employerId}
     */
    @GetMapping("/by-employer/{employerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'EMPLOYER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProviderViewDto>>> getProvidersByEmployer(
            @PathVariable("employerId") Long employerId) {
        Long scopedEmployerId = authorizationService.resolveEmployerScope(authorizationService.getCurrentUser(), employerId);
        log.info("[PROVIDER] GET /api/v1/providers/by-employer/{}", scopedEmployerId);
        List<ProviderViewDto> providers = providerService.getProvidersByEmployer(scopedEmployerId);

        // ═══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION (Phase 11)
        // ═══════════════════════════════════════════════════════════════════════════
        // Note: Per user request, reviewers can see all provider cards for an employer
        // to monitor batch availability. However, claim-level isolation remains
        // strictly enforced in ClaimService.
        /* 
        var currentUser = authorizationService.getCurrentUser();
        if (currentUser != null && "MEDICAL_REVIEWER".equals(currentUser.getUserType())) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            log.info("[ISOLATION] Reviewer {} is assigned to {} providers: {}", currentUser.getUsername(), allowedProviderIds.size(), allowedProviderIds);
            
            providers = providers.stream()
                    .filter(p -> allowedProviderIds.contains(p.getId()))
                    .collect(Collectors.toList());
            
            log.info("[ISOLATION] After filtering, reviewer {} can see {} providers for employer {}", currentUser.getUsername(), providers.size(), employerId);
        }
        */
        log.info("[ISOLATION] Medical Reviewer isolation for provider list bypassed per request. Found {} providers.", providers.size());

        return ResponseEntity.ok(ApiResponse.success(providers));
    }
}
