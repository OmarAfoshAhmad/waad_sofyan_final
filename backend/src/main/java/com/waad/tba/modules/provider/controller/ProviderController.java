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
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderServiceService providerServiceService;
    private final ProviderContractService providerContractService;
    private final ProviderAdminDocumentService providerAdminDocumentService;
    private final AuthorizationService authorizationService;

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
        
        // Filter for Provider Users
        var currentUser = authorizationService.getCurrentUser();
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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderViewDto>> getProvider(@PathVariable("id") Long id) {
        ProviderViewDto provider = providerService.getProvider(id);
        return ResponseEntity.ok(ApiResponse.success("Provider retrieved successfully", provider));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginationResponse<ProviderViewDto>>> listProviders(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "search", required = false) String search) {
        Page<ProviderViewDto> providers = providerService.listProviders(Math.max(0, page - 1), size, search);

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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getProviderServiceCodes(@PathVariable("id") Long id) {
        log.info("[PROVIDER-SERVICES] GET /api/providers/{}/service-codes", id);
        
        List<String> serviceCodes = providerServiceService.getProviderServiceCodes(id);
        
        return ResponseEntity.ok(ApiResponse.success(serviceCodes));
    }

    /**
     * Check if provider offers a specific service
     */
    @GetMapping("/{id}/services/{serviceCode}/check")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> checkProviderService(
            @PathVariable("id") Long id,
            @PathVariable("serviceCode") String serviceCode) {
        
        log.info("[PROVIDER-SERVICES] GET /api/providers/{}/services/{}/check", id, serviceCode);
        
        boolean offers = providerServiceService.providerOffersService(id, serviceCode);
        
        return ResponseEntity.ok(ApiResponse.success(offers));
    }

    // ==================== PROVIDER CONTRACT ENDPOINTS ====================

    /**
     * Create a new provider contract
     */
    @PostMapping("/{id}/contracts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> createContract(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProviderContractCreateDto dto) {
        
        log.info("[PROVIDER-CONTRACTS] POST /api/providers/{}/contracts", id);
        
        ProviderContractResponseDto contract = providerContractService.createContract(id, dto);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Provider contract created successfully", contract));
    }

    /**
     * Update an existing provider contract
     */
    @PutMapping("/{id}/contracts/{contractId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> updateContract(
            @PathVariable("id") Long id,
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody ProviderContractUpdateDto dto) {
        
        log.info("[PROVIDER-CONTRACTS] PUT /api/providers/{}/contracts/{}", id, contractId);
        
        ProviderContractResponseDto contract = providerContractService.updateContract(id, contractId, dto);
        
        return ResponseEntity.ok(ApiResponse.success("Provider contract updated successfully", contract));
    }

    /**
     * Delete a provider contract (soft delete)
     */
    @DeleteMapping("/{id}/contracts/{contractId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteContract(
            @PathVariable("id") Long id,
            @PathVariable("contractId") Long contractId) {
        
        log.info("[PROVIDER-CONTRACTS] DELETE /api/providers/{}/contracts/{}", id, contractId);
        
        providerContractService.deleteContract(id, contractId);
        
        return ResponseEntity.ok(ApiResponse.<Void>success("Provider contract deleted successfully", null));
    }

    /**
     * Get all contracts for a provider (paginated)
     */
    @GetMapping("/{id}/contracts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PaginationResponse<ProviderContractResponseDto>> getProviderContracts(
            @PathVariable("id") Long id,
            @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "effectiveFrom") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir) {
        
        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/contracts?activeOnly={}&page={}&size={}", 
                id, activeOnly, page, size);
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, 
                sortDir.equalsIgnoreCase("ASC") 
                    ? org.springframework.data.domain.Sort.by(sortBy).ascending()
                    : org.springframework.data.domain.Sort.by(sortBy).descending()
        );
        
        Page<ProviderContractResponseDto> contracts = providerContractService.getProviderContracts(
                id, activeOnly, pageable);
        
        return ResponseEntity.ok(PaginationResponse.of(contracts));
    }

    /**
     * Get currently effective contracts for a provider
     */
    @GetMapping("/{id}/contracts/current")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProviderContractResponseDto>>> getCurrentContracts(
            @PathVariable("id") Long id) {
        
        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/contracts/current", id);
        
        List<ProviderContractResponseDto> contracts = providerContractService.getCurrentlyEffectiveContracts(id);
        
        return ResponseEntity.ok(ApiResponse.success(contracts));
    }

    /**
     * Get contract by ID
     */
    @GetMapping("/{id}/contracts/{contractId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> getContractById(
            @PathVariable("id") Long id,
            @PathVariable("contractId") Long contractId) {
        
        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/contracts/{}", id, contractId);
        
        ProviderContractResponseDto contract = providerContractService.getContractById(id, contractId);
        
        return ResponseEntity.ok(ApiResponse.success(contract));
    }

    /**
     * Get effective price for a service on a specific date
     */
    @GetMapping("/{id}/services/{serviceCode}/price")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EffectivePriceResponseDto>> getEffectivePrice(
            @PathVariable("id") Long id,
            @PathVariable("serviceCode") String serviceCode,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) 
            java.time.LocalDate date) {
        
        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/services/{}/price?date={}", 
                id, serviceCode, date);
        
        EffectivePriceResponseDto price = providerContractService.getEffectivePrice(id, serviceCode, date);
        
        return ResponseEntity.ok(ApiResponse.success(price));
    }

    /**
     * Get count of active contracts
     */
    @GetMapping("/{id}/contracts/count")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getContractCount(@PathVariable("id") Long id) {
        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/contracts/count", id);
        
        long count = providerContractService.countActiveContracts(id);
        
        return ResponseEntity.ok(ApiResponse.success(count));
    }
    
    /**
     * Get services requiring pre-approval for a member from provider's active contract.
     * 
     * This endpoint returns ONLY services that:
     * 1. Are in the provider's active contract (with contract pricing)
     * 2. Require pre-approval based on the MEMBER's benefit policy rules
     * 
     * GET /api/providers/{id}/contract/services/requiring-preauth?memberId=X
     */
    @GetMapping("/{id}/contract/services/requiring-preauth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<java.util.List<ProviderPortalController.ProviderServiceDto>>> getServicesRequiringPreAuth(
            @PathVariable("id") Long id,
            @RequestParam(name = "memberId") Long memberId) {
        
        log.info("[PROVIDER-CONTRACTS] GET /api/providers/{}/contract/services/requiring-preauth?memberId={}", 
                id, memberId);
        
        java.util.List<ProviderPortalController.ProviderServiceDto> services = providerContractService.getServicesRequiringPreAuth(id, memberId);
        
        return ResponseEntity.ok(ApiResponse.success(
            "Services requiring pre-approval retrieved", 
            services
        ));
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<List<ProviderViewDto>>> getProvidersByEmployer(
            @PathVariable("employerId") Long employerId) {
        log.info("[PROVIDER] GET /api/v1/providers/by-employer/{}", employerId);
        List<ProviderViewDto> providers = providerService.getProvidersByEmployer(employerId);
        return ResponseEntity.ok(ApiResponse.success(providers));
    }
}
