package com.waad.tba.modules.settlement.controller;

import com.waad.tba.modules.settlement.dto.MonthlySettlementSummaryDto;
import com.waad.tba.modules.settlement.dto.PaymentAuditLogDto;
import com.waad.tba.modules.settlement.dto.PaymentRecordDto;
import com.waad.tba.modules.settlement.dto.PaymentRequestDto;
import com.waad.tba.modules.settlement.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/summaries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    public ResponseEntity<List<MonthlySettlementSummaryDto>> getMonthlySummaries(
            @RequestParam(required = false) Long employerId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String status) {
        
        List<MonthlySettlementSummaryDto> summaries = paymentService.getMonthlySettlementSummaries(
                employerId, providerId, year, month, status);
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    public ResponseEntity<List<PaymentRecordDto>> getPaymentRecords(
            @RequestParam Long employerId,
            @RequestParam Long providerId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        
        List<PaymentRecordDto> records = paymentService.getPaymentsForSettlement(employerId, providerId, year, month);
        return ResponseEntity.ok(records);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<PaymentRecordDto> addPayment(
            @Valid @RequestBody PaymentRequestDto request,
            Authentication authentication) {
        
        PaymentRecordDto record = paymentService.addPayment(request, authentication.getName());
        return ResponseEntity.ok(record);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<PaymentRecordDto> updatePayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequestDto request,
            Authentication authentication) {
        
        PaymentRecordDto record = paymentService.updatePayment(id, request, authentication.getName());
        return ResponseEntity.ok(record);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Void> deletePayment(
            @PathVariable Long id,
            @RequestParam String reason,
            Authentication authentication) {
        
        paymentService.deletePayment(id, reason, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    public ResponseEntity<List<PaymentAuditLogDto>> getPaymentAuditLogs(@PathVariable Long id) {
        List<PaymentAuditLogDto> logs = paymentService.getPaymentAuditLogs(id);
        return ResponseEntity.ok(logs);
    }
}
