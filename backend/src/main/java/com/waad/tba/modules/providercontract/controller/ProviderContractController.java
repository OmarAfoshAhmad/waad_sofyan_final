package com.waad.tba.modules.providercontract.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.waad.tba.modules.providercontract.dto.ProviderContractCreateDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemCreateDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemResponseDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemUpdateDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractStatsDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractUpdateDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.providercontract.service.ProviderContractService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for Provider Contracts management.
 * 
 * Provides endpoints for:
 * - CRUD operations on contracts
 * - Contract lifecycle management (activate, suspend, terminate)
 * - Contract pricing management
 * - Contract statistics
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-contracts")
@RequiredArgsConstructor
@Tag(name = "Provider Contracts", description = "Provider contract management API")
public class ProviderContractController {

    private final ProviderContractService contractService;
    private final ProviderContractPricingItemService pricingService;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACT CRUD ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/provider-contracts
     * List all contracts (paginated)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "List all contracts", description = "Get paginated list of all provider contracts")
    public ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("REST request to get all provider contracts");
        Page<ProviderContractResponseDto> result = contractService.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success("Contracts retrieved successfully", result));
    }

    /**
     * GET /api/provider-contracts/search
     * Search contracts
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Search contracts", description = "Search contracts by code or provider name")
    public ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> search(
            @Parameter(description = "Search query") @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Filter by status") @RequestParam(name = "status", required = false) ContractStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("REST request to search contracts: q={}, status={}", q, status);
        Page<ProviderContractResponseDto> result = contractService.search(q, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search completed", result));
    }

    /**
     * GET /api/provider-contracts/stats
     * Get contract statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get statistics", description = "Get contract statistics summary")
    public ResponseEntity<ApiResponse<ProviderContractStatsDto>> getStats() {
        log.debug("REST request to get contract statistics");
        ProviderContractStatsDto stats = contractService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved", stats));
    }

    /**
     * GET /api/provider-contracts/expiring
     * Get contracts expiring within N days
     */
    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get expiring contracts", description = "List contracts expiring within specified days")
    public ResponseEntity<ApiResponse<List<ProviderContractResponseDto>>> getExpiring(
            @Parameter(description = "Days until expiration") @RequestParam(name = "days", defaultValue = "30") int days) {

        log.debug("REST request to get contracts expiring within {} days", days);
        List<ProviderContractResponseDto> result = contractService.findExpiringWithinDays(days);
        return ResponseEntity.ok(ApiResponse.success("Expiring contracts retrieved", result));
    }

    /**
     * GET /api/provider-contracts/status/{status}
     * Get contracts by status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get contracts by status", description = "List contracts filtered by status")
    public ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> getByStatus(
            @Parameter(description = "Contract status") @PathVariable("status") ContractStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("REST request to get contracts with status: {}", status);
        Page<ProviderContractResponseDto> result = contractService.findByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Contracts retrieved", result));
    }

    /**
     * GET /api/provider-contracts/{id}
     * Get contract by ID
     */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get contract by ID", description = "Get detailed contract information")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> getById(
            @Parameter(description = "Contract ID") @PathVariable("id") Long id) {

        log.debug("REST request to get contract: {}", id);
        ProviderContractResponseDto result = contractService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Contract retrieved", result));
    }

    /**
     * GET /api/provider-contracts/code/{code}
     * Get contract by code
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get contract by code", description = "Get contract by contract code")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> getByCode(
            @Parameter(description = "Contract code") @PathVariable("code") String code) {

        log.debug("REST request to get contract by code: {}", code);
        ProviderContractResponseDto result = contractService.findByCode(code);
        return ResponseEntity.ok(ApiResponse.success("Contract retrieved", result));
    }

    /**
     * GET /api/provider-contracts/provider/{providerId}
     * Get contracts for a provider
     */
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Get contracts by provider", description = "List all contracts for a provider")
    public ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> getByProvider(
            @Parameter(description = "Provider ID") @PathVariable("providerId") Long providerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("REST request to get contracts for provider: {}", providerId);
        Page<ProviderContractResponseDto> result = contractService.findByProvider(providerId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Contracts retrieved", result));
    }

    /**
     * GET /api/provider-contracts/provider/{providerId}/active
     * Get active contract for a provider
     */
    @GetMapping("/provider/{providerId}/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get active contract", description = "Get the active contract for a provider")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> getActiveByProvider(
            @Parameter(description = "Provider ID") @PathVariable("providerId") Long providerId) {

        log.debug("REST request to get active contract for provider: {}", providerId);
        ProviderContractResponseDto result = contractService.findActiveByProvider(providerId);
        return ResponseEntity
                .ok(ApiResponse.success(result != null ? "Active contract found" : "No active contract", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACTED CATEGORIES AND SERVICES (for Claims/PreAuth creation)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/provider-contracts/provider/{providerId}/categories
     * Get medical categories available in provider's active contract
     * 
     * CRITICAL: This is the ONLY way to get categories for claims/preauth creation
     * Direct MedicalCategory queries are NOT allowed for this purpose
     */
    @GetMapping("/provider/{providerId}/categories")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'PROVIDER_STAFF')")
    @Operation(summary = "Get contracted categories", description = "Get medical categories available in provider's active contract. Use this for claims/preauth creation.")
    public ResponseEntity<ApiResponse<List<ProviderContractPricingItemService.ContractCategoryDto>>> getContractedCategories(
            @Parameter(description = "Provider ID") @PathVariable("providerId") Long providerId) {

        log.debug("REST request to get contracted categories for provider: {}", providerId);
        List<ProviderContractPricingItemService.ContractCategoryDto> result = pricingService
                .findCategoriesByProvider(providerId);
        return ResponseEntity.ok(ApiResponse.success("Contracted categories retrieved", result));
    }

    /**
     * GET
     * /api/provider-contracts/provider/{providerId}/categories/{categoryId}/services
     * Get medical services for a category in provider's active contract
     * 
     * CRITICAL: This is the ONLY way to get services for claims/preauth creation
     * Direct MedicalService queries are NOT allowed for this purpose
     */
    @GetMapping("/provider/{providerId}/categories/{categoryId}/services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'PROVIDER_STAFF')")
    @Operation(summary = "Get contracted services by category", description = "Get medical services for a category in provider's active contract. Use this for claims/preauth creation.")
    public ResponseEntity<ApiResponse<List<ProviderContractPricingItemService.ContractServiceDto>>> getContractedServicesByCategory(
            @Parameter(description = "Provider ID") @PathVariable("providerId") Long providerId,
            @Parameter(description = "Category ID") @PathVariable("categoryId") Long categoryId) {

        log.debug("REST request to get contracted services for provider: {}, category: {}", providerId, categoryId);
        List<ProviderContractPricingItemService.ContractServiceDto> result = pricingService
                .findServicesByProviderAndCategory(providerId, categoryId);
        return ResponseEntity.ok(ApiResponse.success("Contracted services retrieved", result));
    }

    /**
     * GET /api/provider-contracts/provider/{providerId}/services
     * Get ALL medical services in provider's active contract (without category
     * filter)
     */
    @GetMapping("/provider/{providerId}/services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'ACCOUNTANT')")
    @Operation(summary = "Get all contracted services", description = "Get all medical services in provider's active contract.")
    public ResponseEntity<ApiResponse<List<ProviderContractPricingItemService.ContractServiceDto>>> getAllContractedServices(
            @Parameter(description = "Provider ID") @PathVariable("providerId") Long providerId) {

        log.debug("REST request to get all contracted services for provider: {}", providerId);
        List<ProviderContractPricingItemService.ContractServiceDto> result = pricingService
                .findAllServicesByProvider(providerId);
        return ResponseEntity.ok(ApiResponse.success("All contracted services retrieved", result));
    }

    /**
     * POST /api/provider-contracts
     * Create new contract
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Create contract", description = "Create a new provider contract")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> create(
            @Valid @RequestBody ProviderContractCreateDto dto) {

        log.debug("REST request to create contract for provider: {}", dto.getProviderId());
        ProviderContractResponseDto result = contractService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contract created successfully", result));
    }

    /**
     * PUT /api/provider-contracts/{id}
     * Update contract
     */
    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Update contract", description = "Update an existing contract")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> update(
            @Parameter(description = "Contract ID") @PathVariable("id") Long id,
            @Valid @RequestBody ProviderContractUpdateDto dto) {

        log.debug("REST request to update contract: {}", id);
        ProviderContractResponseDto result = contractService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Contract updated successfully", result));
    }

    /**
     * DELETE /api/provider-contracts/{id}
     * Delete contract (soft delete)
     */
    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Delete contract", description = "Soft delete a contract")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Contract ID") @PathVariable("id") Long id) {

        log.debug("REST request to delete contract: {}", id);
        contractService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Contract deleted successfully", null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACT LIFECYCLE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/provider-contracts/{id}/activate
     * Activate a contract
     */
    @PostMapping("/{id:\\d+}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Activate contract", description = "Activate a draft or suspended contract")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> activate(
            @Parameter(description = "Contract ID") @PathVariable("id") Long id) {

        log.debug("REST request to activate contract: {}", id);
        ProviderContractResponseDto result = contractService.activate(id);
        return ResponseEntity.ok(ApiResponse.success("Contract activated successfully", result));
    }

    /**
     * POST /api/provider-contracts/{id}/suspend
     * Suspend a contract
     */
    @PostMapping("/{id:\\d+}/suspend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Suspend contract", description = "Suspend an active contract")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> suspend(
            @Parameter(description = "Contract ID") @PathVariable("id") Long id,
            @Parameter(description = "Suspension reason") @RequestParam(name = "reason", required = false) String reason) {

        log.debug("REST request to suspend contract: {}", id);
        ProviderContractResponseDto result = contractService.suspend(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Contract suspended successfully", result));
    }

    /**
     * POST /api/provider-contracts/{id}/terminate
     * Terminate a contract
     */
    @PostMapping("/{id:\\d+}/terminate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Terminate contract", description = "Terminate a contract permanently")
    public ResponseEntity<ApiResponse<ProviderContractResponseDto>> terminate(
            @Parameter(description = "Contract ID") @PathVariable("id") Long id,
            @Parameter(description = "Termination reason") @RequestParam(name = "reason", required = false) String reason) {

        log.debug("REST request to terminate contract: {}", id);
        ProviderContractResponseDto result = contractService.terminate(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Contract terminated successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRICING ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/provider-contracts/{contractId}/pricing
     * List pricing items for a contract
     */
    @GetMapping("/{contractId}/pricing")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "List pricing items", description = "Get pricing items for a contract with optional search and filtering")
    public ResponseEntity<ApiResponse<Page<ProviderContractPricingItemResponseDto>>> getPricing(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId,
            @Parameter(description = "Search query") @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Category ID") @RequestParam(name = "categoryId", required = false) Long categoryId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("REST request to get pricing for contract: {}, query: {}, category: {}", contractId, q, categoryId);
        Page<ProviderContractPricingItemResponseDto> result = pricingService.searchInContract(contractId, q, categoryId,
                pageable);
        return ResponseEntity.ok(ApiResponse.success("Pricing items retrieved", result));
    }

    /**
     * GET /api/provider-contracts/{contractId}/pricing/search
     * Search pricing items
     */
    @GetMapping("/{contractId}/pricing/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Search pricing items", description = "Search pricing items by service code or name")
    public ResponseEntity<ApiResponse<Page<ProviderContractPricingItemResponseDto>>> searchPricing(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId,
            @Parameter(description = "Search query") @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("REST request to search pricing in contract: {}, query: {}", contractId, q);
        Page<ProviderContractPricingItemResponseDto> result = pricingService.searchInContract(contractId, q, null,
                pageable);
        return ResponseEntity.ok(ApiResponse.success("Search completed", result));
    }

    /**
     * GET /api/provider-contracts/{contractId}/pricing/stats
     * Get pricing statistics
     */
    @GetMapping("/{contractId}/pricing/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get pricing stats", description = "Get pricing statistics for a contract")
    public ResponseEntity<ApiResponse<ProviderContractPricingItemService.PricingStatsDto>> getPricingStats(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId) {

        log.debug("REST request to get pricing stats for contract: {}", contractId);
        ProviderContractPricingItemService.PricingStatsDto stats = pricingService.getPricingStats(contractId);
        return ResponseEntity.ok(ApiResponse.success("Pricing stats retrieved", stats));
    }

    /**
     * GET /api/provider-contracts/pricing/{pricingId}
     * Get pricing item by ID
     */
    @GetMapping("/pricing/{pricingId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get pricing item", description = "Get pricing item by ID")
    public ResponseEntity<ApiResponse<ProviderContractPricingItemResponseDto>> getPricingById(
            @Parameter(description = "Pricing item ID") @PathVariable("pricingId") Long pricingId) {

        log.debug("REST request to get pricing item: {}", pricingId);
        ProviderContractPricingItemResponseDto result = pricingService.findById(pricingId);
        return ResponseEntity.ok(ApiResponse.success("Pricing item retrieved", result));
    }

    /**
     * POST /api/provider-contracts/{contractId}/pricing
     * Add pricing item
     */
    @PostMapping("/{contractId}/pricing")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Add pricing item", description = "Add a pricing item to a contract")
    public ResponseEntity<ApiResponse<ProviderContractPricingItemResponseDto>> addPricing(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId,
            @Valid @RequestBody ProviderContractPricingItemCreateDto dto) {

        log.debug("REST request to add pricing to contract: {}", contractId);
        ProviderContractPricingItemResponseDto result = pricingService.create(contractId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pricing item added successfully", result));
    }

    /**
     * POST /api/provider-contracts/{contractId}/pricing/bulk
     * Bulk add pricing items
     */
    @PostMapping("/{contractId}/pricing/bulk")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Bulk add pricing", description = "Add multiple pricing items to a contract")
    public ResponseEntity<ApiResponse<List<ProviderContractPricingItemResponseDto>>> addBulkPricing(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId,
            @Valid @RequestBody List<ProviderContractPricingItemCreateDto> dtos) {

        log.debug("REST request to bulk add {} pricing items to contract: {}", dtos.size(), contractId);
        List<ProviderContractPricingItemResponseDto> result = pricingService.createBulk(contractId, dtos);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pricing items added successfully", result));
    }

    /**
     * PUT /api/provider-contracts/pricing/{pricingId}
     * Update pricing item
     */
    @PutMapping("/pricing/{pricingId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Update pricing item", description = "Update a pricing item")
    public ResponseEntity<ApiResponse<ProviderContractPricingItemResponseDto>> updatePricing(
            @Parameter(description = "Pricing item ID") @PathVariable("pricingId") Long pricingId,
            @Valid @RequestBody ProviderContractPricingItemUpdateDto dto) {

        log.debug("REST request to update pricing item: {}", pricingId);
        ProviderContractPricingItemResponseDto result = pricingService.update(pricingId, dto);
        return ResponseEntity.ok(ApiResponse.success("Pricing item updated successfully", result));
    }

    /**
     * DELETE /api/provider-contracts/pricing/{pricingId}
     * Delete pricing item
     */
    @DeleteMapping("/pricing/{pricingId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Delete pricing item", description = "Delete a pricing item")
    public ResponseEntity<ApiResponse<Void>> deletePricing(
            @Parameter(description = "Pricing item ID") @PathVariable("pricingId") Long pricingId) {

        log.debug("REST request to delete pricing item: {}", pricingId);
        pricingService.delete(pricingId);
        return ResponseEntity.ok(ApiResponse.success("Pricing item deleted successfully", null));
    }

    /**
     * DELETE /api/provider-contracts/{contractId}/pricing
     * Delete all pricing items for a contract
     */
    @DeleteMapping("/{contractId}/pricing")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Delete all pricing", description = "Delete all pricing items for a draft contract")
    public ResponseEntity<ApiResponse<Integer>> deleteAllPricing(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId) {

        log.debug("REST request to delete all pricing for contract: {}", contractId);
        int count = pricingService.deleteByContract(contractId);
        return ResponseEntity.ok(ApiResponse.success("Deleted " + count + " pricing items", count));
    }

    /**
     * POST /api/provider-contracts/{contractId}/pricing/repair
     * Repair unmapped pricing items
     */
    @PostMapping("/{contractId}/pricing/repair")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Repair unmapped items", description = "Attempts to link unmapped pricing items to system medical services by code or name")
    public ResponseEntity<ApiResponse<Integer>> repairPricing(
            @Parameter(description = "Contract ID") @PathVariable("contractId") Long contractId) {

        log.debug("REST request to repair pricing items for contract: {}", contractId);
        int count = pricingService.repairUnmappedItems(contractId);
        return ResponseEntity.ok(ApiResponse.success("Successfully repaired " + count + " items", count));
    }
}
