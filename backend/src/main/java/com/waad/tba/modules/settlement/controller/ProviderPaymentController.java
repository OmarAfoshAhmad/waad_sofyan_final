package com.waad.tba.modules.settlement.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.guard.FeatureGuard;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.PaymentAllocationSuggestionDto;
import com.waad.tba.modules.settlement.dto.PostProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.ProviderPaymentDto;
import com.waad.tba.modules.settlement.dto.ProviderPaymentPostResultDto;
import com.waad.tba.modules.settlement.dto.ProviderPaymentReversalResultDto;
import com.waad.tba.modules.settlement.dto.ReverseProviderPaymentRequest;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.service.ProviderPaymentAllocationSuggestionService;
import com.waad.tba.modules.settlement.service.ProviderPaymentDraftService;
import com.waad.tba.modules.settlement.service.ProviderPaymentPostingService;
import com.waad.tba.modules.settlement.service.ProviderPaymentReversalService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The new provider-payment model (Phases 2-8): one transfer per provider,
 * allocated across employer/period, posted once, reversible.
 *
 * Reads (suggestion, payment/allocation lookups) are always available — Phase 9
 * builds the UI against them. Every write action ({@link #createDraft},
 * {@link #post}, {@link #reverse}) is gated by
 * {@link FeatureGuard#requireProviderPaymentPosting()} and fails closed with
 * 503 until Phase 11 turns the flag on. Building the UI must not implicitly
 * activate the new financial write path.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-payments")
@RequiredArgsConstructor
@Tag(name = "Settlement - Provider Payments (v2)", description = "New one-transfer-per-provider payment model")
@PreAuthorize("isAuthenticated()")
public class ProviderPaymentController {

    private final ProviderPaymentAllocationSuggestionService suggestionService;
    private final ProviderPaymentDraftService draftService;
    private final ProviderPaymentPostingService postingService;
    private final ProviderPaymentReversalService reversalService;
    private final ProviderPaymentRepository payments;
    private final AuthorizationService authorizationService;
    private final FeatureGuard featureGuard;

    @GetMapping("/by-provider/{providerId}/suggestion")
    @Operation(summary = "Preview FIFO allocation", description = "Read-only; never saves anything")
    public ResponseEntity<ApiResponse<PaymentAllocationSuggestionDto>> suggest(
            @PathVariable Long providerId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.success(suggestionService.suggest(providerId, amount, effectiveDate)));
    }

    @GetMapping("/by-provider/{providerId}")
    public ResponseEntity<ApiResponse<List<ProviderPaymentDto>>> listByProvider(@PathVariable Long providerId) {
        List<ProviderPaymentDto> dtos = payments.findByProviderIdWithAllocationsOrderByPaymentDateDesc(providerId)
                .stream().map(ProviderPaymentDto::from).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderPaymentDto>> get(@PathVariable Long id) {
        ProviderPayment payment = payments.findByIdWithAllocations(id)
                .orElseThrow(() -> new BusinessRuleException("الدفعة غير موجودة: " + id));
        return ResponseEntity.ok(ApiResponse.success(ProviderPaymentDto.from(payment)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Create a DRAFT payment", description = "Gated by PROVIDER_PAYMENT_POSTING_ENABLED")
    public ResponseEntity<ApiResponse<ProviderPaymentDto>> createDraft(
            @RequestBody CreateProviderPaymentRequest request) {
        featureGuard.requireProviderPaymentPosting();
        ProviderPayment draft = draftService.createDraft(request, currentUsername());
        return ResponseEntity.ok(ApiResponse.success(ProviderPaymentDto.from(draft)));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Post a DRAFT payment", description = "Gated by PROVIDER_PAYMENT_POSTING_ENABLED")
    public ResponseEntity<ApiResponse<ProviderPaymentPostResultDto>> post(
            @PathVariable Long id, @RequestBody PostProviderPaymentRequest request) {
        featureGuard.requireProviderPaymentPosting();
        ProviderPayment draft = payments.findById(id)
                .orElseThrow(() -> new BusinessRuleException("الدفعة غير موجودة: " + id));
        ProviderPaymentPostResultDto result = postingService.post(id, draft.getIdempotencyKey(),
                request.getExpectedPaymentVersion(), request.getExpectedAccountVersion(),
                currentUsername(), currentUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Reverse a POSTED payment", description = "Gated by PROVIDER_PAYMENT_POSTING_ENABLED")
    public ResponseEntity<ApiResponse<ProviderPaymentReversalResultDto>> reverse(
            @PathVariable Long id, @RequestBody ReverseProviderPaymentRequest request) {
        featureGuard.requireProviderPaymentPosting();
        ProviderPaymentReversalResultDto result = reversalService.reverse(id, request.getReason(),
                request.getExpectedPaymentVersion(), request.getExpectedAccountVersion(),
                currentUsername(), currentUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String currentUsername() {
        var user = authorizationService.getCurrentUser();
        return user != null ? user.getUsername() : "system";
    }

    private Long currentUserId() {
        var user = authorizationService.getCurrentUser();
        return user != null ? user.getId() : null;
    }
}
