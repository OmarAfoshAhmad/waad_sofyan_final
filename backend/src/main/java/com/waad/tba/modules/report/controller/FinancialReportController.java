package com.waad.tba.modules.report.controller;

import com.waad.tba.modules.report.dto.FinancialConsolidationDto;
import com.waad.tba.modules.report.service.FinancialConsolidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialConsolidationService financialConsolidationService;

    /** تقرير الخلاصة المالية المجمعة (Multi-Entity Financial Consolidation) */
    @GetMapping("/financial-consolidation")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
    public ResponseEntity<List<FinancialConsolidationDto>> getFinancialConsolidation(
            @RequestParam(required = false, defaultValue = "2026") int year) {
        List<FinancialConsolidationDto> report = financialConsolidationService.getMonthlyFinancialConsolidation(year);
        return ResponseEntity.ok(report);
    }
}
