package com.waad.tba.modules.settlement.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.settlement.dto.AvailableClaimDTO;
import com.waad.tba.modules.settlement.dto.BatchItemDetailsDTO;
import com.waad.tba.modules.settlement.dto.BatchSummaryDTO;
import com.waad.tba.modules.settlement.dto.CreateBatchRequest;
import com.waad.tba.modules.settlement.entity.SettlementBatch;
import com.waad.tba.modules.settlement.entity.SettlementBatch.BatchStatus;
import com.waad.tba.modules.settlement.service.SettlementBatchService;
import com.waad.tba.security.AuthorizationService;

// ═══════════════════════════════════════════════════════════════════════════
// API v1 CONTRACTS
// ═══════════════════════════════════════════════════════════════════════════
import com.waad.tba.modules.settlement.api.request.*;
import com.waad.tba.modules.settlement.api.response.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import java.util.List;
import java.util.Map;

/**
 * Settlement Batch Controller - API Version 1
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ SETTLEMENT BATCH CONTROLLER - API v1 ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Full batch lifecycle management with STRICT API CONTRACTS ║
 * ║ ║
 * ║ CREATE (DRAFT) → ADD/REMOVE CLAIMS → CONFIRM → PAY ║
 * ║ ↓ ↓ ║
 * ║ CANCEL CANCEL ║
 * ║ ║
 * ║ API Endpoints (v1): ║
 * ║ POST /api/v1/settlement-batches - Create DRAFT batch ║
 * ║ GET /api/v1/settlement-batches/{id} - Get batch details ║
 * ║ GET /api/v1/settlement-batches - List batches (filtered) ║
 * ║ PUT /api/v1/settlement-batches/{id}/claims - Add claims to DRAFT ║
 * ║ DELETE /api/v1/settlement-batches/{id}/claims - Remove claims from DRAFT ║
 * ║ POST /api/v1/settlement-batches/{id}/confirm - DRAFT → CONFIRMED ║
 * ║ POST /api/v1/settlement-batches/{id}/pay - CONFIRMED → PAID ║
 * ║ POST /api/v1/settlement-batches/{id}/cancel - Cancel batch ║
 * ║ ║
 * ║ FINANCIAL SAFETY GUARANTEES (API v1): ║
 * ║ ✓ Frontend NEVER sends monetary values ║
 * ║ ✓ All amounts calculated by backend from database ║
 * ║ ✓ Request contracts validated with Jakarta Bean Validation ║
 * ║ ✓ Response contracts enforce read-only data exposure ║
 * ║ ✓ Payment amounts = batch.totalNetAmount (immutable after CONFIRMED) ║
 * ║ ✓ No arithmetic manipulation possible by client ║
 * ║ ║
 * ║ Security Permissions: ║
 * ║ - VIEW_SETTLEMENTS: Read operations ║
 * ║ - CREATE_SETTLEMENT_BATCH: Create + modify DRAFT ║
 * ║ - CONFIRM_SETTLEMENT_BATCH: Confirm batch ║
 * ║ - PAY_SETTLEMENT_BATCH: Pay batch (financial impact) ║
 * ║ - CANCEL_SETTLEMENT_BATCH: Cancel batch ║
 * ║ ║
 * ║ @see SETTLEMENT_API_CONTRACT.md ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settlement-batches")
@RequiredArgsConstructor
@Tag(name = "Settlement - Batches (v1)", description = "APIs for managing settlement batches - Version 1")
public class SettlementBatchController {

    private final SettlementBatchService batchService;
    private final AuthorizationService authorizationService;

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH CREATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new settlement batch in DRAFT status.
     * Optionally include claim IDs to add immediately.
     * 
     * API CONTRACT v1: Uses CreateSettlementBatchRequest
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Create settlement batch", description = "Creates a new settlement batch in DRAFT status. Frontend NEVER sends amounts.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Batch created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Missing CREATE_SETTLEMENT_BATCH permission")
    })
    public ResponseEntity<ApiResponse<BatchSummaryDTO>> createBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch creation request (API v1)") @Valid @RequestBody CreateSettlementBatchRequest apiRequest) {

        Long userId = authorizationService.getCurrentUser() != null ? authorizationService.getCurrentUser().getId()
                : null;

        log.info("🆕 [API v1] Creating settlement batch for provider {}. Claims: {}",
                apiRequest.getProviderId(),
                apiRequest.getClaimIds() != null ? apiRequest.getClaimIds().size() : 0);

        // Convert API request to internal DTO
        CreateBatchRequest internalRequest = CreateBatchRequest.builder()
                .providerId(apiRequest.getProviderId())
                .description(apiRequest.getDescription())
                .claimIds(apiRequest.getClaimIds())
                .createdBy(userId)
                .build();

        SettlementBatch batch;
        if (internalRequest.getClaimIds() != null && !internalRequest.getClaimIds().isEmpty()) {
            batch = batchService.createBatchWithClaims(internalRequest);
        } else {
            batch = batchService.createBatch(
                    internalRequest.getProviderId(),
                    internalRequest.getDescription(),
                    userId);
        }

        BatchSummaryDTO summary = batchService.getBatchSummary(batch.getId());

        log.info("✅ Batch created: id={}, number={}, net={}",
                batch.getId(), batch.getBatchNumber(), batch.getTotalNetAmount());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("تم إنشاء الدفعة بنجاح", summary));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get batch details by ID.
     */
    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    @Operation(summary = "Get batch details", description = "Returns detailed batch information including items")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<ApiResponse<BatchSummaryDTO>> getBatch(
            @Parameter(description = "Batch ID", required = true) @PathVariable("batchId") Long batchId) {

        log.info("Getting batch details for ID {}", batchId);

        BatchSummaryDTO summary = batchService.getBatchSummary(batchId);

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * List batches with optional filters.
     * ✅ FIXED: Returns DTO instead of Entity to prevent LazyInitializationException
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    @Operation(summary = "List batches", description = "Returns paginated list of batches with optional status filter")
    public ResponseEntity<ApiResponse<SettlementBatchListResponse>> listBatches(
            @Parameter(description = "Filter by status (DRAFT, CONFIRMED, PAID)") @RequestParam(name = "status", required = false) BatchStatus status,

            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,

            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size) {

        log.info("📋 [API v1] Listing batches. Status: {}, Page: {}", status, page);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<SettlementBatch> batchesPage;
        if (status != null) {
            batchesPage = batchService.getBatchesByStatus(status, pageable);
        } else {
            batchesPage = batchService.getAllBatches(pageable);
        }

        // ✅ Convert Entity Page to DTO List (prevents LazyInitializationException)
        List<SettlementBatchListResponse.BatchSummaryItem> dtos = batchesPage.getContent().stream()
                .map(batch -> {
                    Provider provider = batchService.getProviderForBatch(batch);
                    return SettlementBatchListResponse.BatchSummaryItem.builder()
                            .batchId(batch.getId())
                            .batchNumber(batch.getBatchNumber())
                            .providerName(provider != null ? provider.getName() : "Unknown")
                            .status(batch.getStatus().name())
                            .statusArabic(getStatusArabic(batch.getStatus()))
                            .claimCount(batch.getTotalClaimsCount())
                            .totalNetAmount(batch.getTotalNetAmount())
                            .paymentReference(null)
                            .createdByName("User-" + batch.getCreatedBy())
                            .createdAt(batch.getCreatedAt().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE))
                            .modifiable(batch.isModifiable())
                            .build();
                })
                .toList();

        SettlementBatchListResponse response = SettlementBatchListResponse.builder()
                .batches(dtos)
                .currentPage(batchesPage.getNumber())
                .pageSize(batchesPage.getSize())
                .totalElements(batchesPage.getTotalElements())
                .totalPages(batchesPage.getTotalPages())
                .first(batchesPage.isFirst())
                .last(batchesPage.isLast())
                .build();

        log.info("✅ Returned {} batches (page {} of {})", dtos.size(), page, batchesPage.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Helper: Get status label in Arabic
     */
    private String getStatusArabic(BatchStatus status) {
        return switch (status) {
            case DRAFT -> "مسودة";
            case CONFIRMED -> "مؤكد";
            case PAID -> "مدفوع";
        };
    }

    /**
     * Get batch items (claims in batch).
     * ✅ FIXED: Returns DTO instead of Entity
     */
    @GetMapping("/{batchId}/items")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    @Operation(summary = "Get batch items", description = "Returns claims included in the batch")
    public ResponseEntity<ApiResponse<?>> getBatchItems(
            @Parameter(description = "Batch ID", required = true) @PathVariable("batchId") Long batchId) {

        log.info("📋 Getting items for batch {}", batchId);

        List<BatchItemDetailsDTO> items = batchService.getBatchItemDetails(batchId);

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /**
     * Get available claims for batching (APPROVED + not in any batch).
     */
    @GetMapping("/available-claims/{providerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    @Operation(summary = "Get available claims", description = "Returns APPROVED claims not yet in any batch for the provider")
    public ResponseEntity<ApiResponse<List<AvailableClaimDTO>>> getAvailableClaims(
            @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId) {

        log.info("Getting available claims for provider {}", providerId);

        List<Claim> claims = batchService.getAvailableClaimsForBatching(providerId);

        // Convert to DTOs with member names and claim numbers
        List<AvailableClaimDTO> dtos = claims.stream()
                .map(AvailableClaimDTO::fromClaim)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                dtos,
                "Found " + dtos.size() + " available claims",
                "تم العثور على " + dtos.size() + " مطالبة متاحة للدفع"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLAIM MANAGEMENT (DRAFT BATCHES ONLY)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Add claims to a DRAFT batch.
     * 
     * API CONTRACT v1: Uses AddClaimsToBatchRequest
     */
    @PutMapping("/{batchId}/claims")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Add claims to batch", description = "Adds claims to a DRAFT batch. Batch must be in DRAFT status. Backend validates and calculates amounts.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Claims added"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Batch not in DRAFT status")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> addClaims(
            @Parameter(description = "Batch ID", required = true) @PathVariable("batchId") Long batchId,
            @Valid @RequestBody AddClaimsToBatchRequest apiRequest) {

        log.info("➕ [API v1] Adding {} claims to batch {}", apiRequest.getClaimIds().size(), batchId);

        List<Long> addedIds = batchService.addClaimsToBatch(batchId, apiRequest.getClaimIds());
        BatchSummaryDTO summary = batchService.getBatchSummary(batchId);

        Map<String, Object> result = Map.of(
                "addedClaimIds", addedIds,
                "addedCount", addedIds.size(),
                "requestedCount", apiRequest.getClaimIds().size(),
                "batch", summary,
                "message", "تم إضافة " + addedIds.size() + " مطالبة إلى الدفعة");

        log.info("✅ Added {}/{} claims to batch {}", addedIds.size(), apiRequest.getClaimIds().size(), batchId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Remove claims from a DRAFT batch.
     * 
     * API CONTRACT v1: Uses RemoveClaimsFromBatchRequest
     */
    @DeleteMapping("/{batchId}/claims")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Remove claims from batch", description = "Removes claims from a DRAFT batch. Batch must be in DRAFT status.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeClaims(
            @Parameter(description = "Batch ID", required = true) @PathVariable("batchId") Long batchId,
            @Valid @RequestBody RemoveClaimsFromBatchRequest apiRequest) {

        log.info("➖ [API v1] Removing {} claims from batch {}", apiRequest.getClaimIds().size(), batchId);

        List<Long> removedIds = batchService.removeClaimsFromBatch(batchId, apiRequest.getClaimIds());
        BatchSummaryDTO summary = batchService.getBatchSummary(batchId);

        Map<String, Object> result = Map.of(
                "removedClaimIds", removedIds,
                "removedCount", removedIds.size(),
                "batch", summary,
                "message", "تم إزالة " + removedIds.size() + " مطالبة من الدفعة");

        log.info("✅ Removed {} claims from batch {}", removedIds.size(), batchId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH LIFECYCLE TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Confirm a batch (DRAFT → CONFIRMED).
     * Locks the batch - no more changes allowed.
     * 
     * API CONTRACT v1: Uses ConfirmSettlementBatchRequest
     */
    @PostMapping("/{batchId}/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Confirm batch", description = "Confirms a DRAFT batch, locking it for payment. Batch must have at least one claim.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch confirmed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Cannot confirm (wrong status or empty)")
    })
    public ResponseEntity<ApiResponse<BatchSummaryDTO>> confirmBatch(
            @Parameter(description = "Batch ID", required = true) @PathVariable("batchId") Long batchId,
            @Valid @RequestBody(required = false) ConfirmSettlementBatchRequest apiRequest) {

        Long userId = authorizationService.getCurrentUser() != null ? authorizationService.getCurrentUser().getId()
                : null;

        log.info("🔒 [API v1] Confirming batch {} by user {}", batchId, userId);

        batchService.confirmBatch(batchId, userId);
        BatchSummaryDTO summary = batchService.getBatchSummary(batchId);

        log.info("✅ Batch confirmed: id={}, claims={}, net={}",
                batchId, summary.getClaimCount(), summary.getTotalNetAmount());

        return ResponseEntity.ok(ApiResponse.success(
                summary,
                "Batch confirmed successfully",
                "تم تأكيد الدفعة بنجاح - جاهزة للدفع"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEPRECATED - Inner class DTOs (Kept for backward compatibility)
    // Use API v1 contracts instead
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @deprecated Use {@link AddClaimsToBatchRequest} instead
     */
    @Deprecated
    @lombok.Data
    public static class AddClaimsRequest {
        private List<Long> claimIds;
    }

    /**
     * @deprecated Use {@link RemoveClaimsFromBatchRequest} instead
     */
    @Deprecated
    @lombok.Data
    public static class RemoveClaimsRequest {
        private List<Long> claimIds;
    }

}

