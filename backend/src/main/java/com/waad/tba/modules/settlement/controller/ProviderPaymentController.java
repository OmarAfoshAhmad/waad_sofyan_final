package com.waad.tba.modules.settlement.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.settlement.api.request.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.api.response.SettlementBatchListResponse;
import com.waad.tba.modules.settlement.dto.ProviderPaymentDTO;
import com.waad.tba.modules.settlement.entity.SettlementBatch;
import com.waad.tba.modules.settlement.service.ProviderPaymentService;
import com.waad.tba.modules.settlement.service.SettlementBatchService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/settlements/payments")
@RequiredArgsConstructor
public class ProviderPaymentController {

    private final ProviderPaymentService providerPaymentService;
    private final SettlementBatchService settlementBatchService;
    private final AuthorizationService authorizationService;

    @GetMapping("/confirmed-batches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    @Operation(summary = "Get confirmed batches for payment center")
    public ResponseEntity<ApiResponse<SettlementBatchListResponse>> getConfirmedBatches(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Page<SettlementBatch> batchesPage = providerPaymentService.getConfirmedBatches(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "confirmedAt"))
        );

        List<SettlementBatchListResponse.BatchSummaryItem> rows = batchesPage.getContent().stream()
                .map(batch -> {
                    Provider provider = settlementBatchService.getProviderForBatch(batch);
                    return SettlementBatchListResponse.BatchSummaryItem.builder()
                            .batchId(batch.getId())
                            .batchNumber(batch.getBatchNumber())
                            .providerName(provider != null ? provider.getName() : "Unknown")
                            .status(batch.getStatus().name())
                            .statusArabic(batch.getStatus().getArabicLabel())
                            .claimCount(batch.getTotalClaimsCount())
                            .totalNetAmount(batch.getTotalNetAmount())
                            .paymentReference(null)
                            .createdByName("User-" + batch.getCreatedBy())
                            .createdAt(batch.getCreatedAt() != null ? batch.getCreatedAt().toString() : null)
                            .modifiable(batch.isModifiable())
                            .build();
                })
                .toList();

        SettlementBatchListResponse response = SettlementBatchListResponse.builder()
                .batches(rows)
                .currentPage(batchesPage.getNumber())
                .pageSize(batchesPage.getSize())
                .totalElements(batchesPage.getTotalElements())
                .totalPages(batchesPage.getTotalPages())
                .first(batchesPage.isFirst())
                .last(batchesPage.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

        @GetMapping("/reports/outstanding-balance")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Outstanding balance report", description = "Sum of CONFIRMED batches that are not yet recorded in provider_payments")
        public ResponseEntity<ApiResponse<BigDecimal>> getOutstandingBalanceReport() {
                BigDecimal total = providerPaymentService.getOutstandingConfirmedUnpaidTotal();
                return ResponseEntity.ok(ApiResponse.success(total));
        }

    @PostMapping("/batches/{batchId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Record payment for confirmed batch")
    public ResponseEntity<ApiResponse<ProviderPaymentDTO>> createPayment(
            @PathVariable Long batchId,
            @Valid @RequestBody CreateProviderPaymentRequest request) {

        Long userId = authorizationService.getCurrentUser() != null ? authorizationService.getCurrentUser().getId() : null;
        ProviderPaymentDTO payment = providerPaymentService.createPayment(batchId, request, userId);

        log.info("Payment recorded in Payment Center for batch {}", batchId);
        return ResponseEntity.ok(ApiResponse.success("تم تسجيل الدفع بنجاح", payment));
    }
}
