package com.waad.tba.modules.dashboard.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.dashboard.dto.ClaimsPerDayDto;
import com.waad.tba.modules.dashboard.dto.CostByProviderDto;
import com.waad.tba.modules.dashboard.dto.DashboardStatsDto;
import com.waad.tba.modules.dashboard.dto.DashboardSummaryDto;
import com.waad.tba.modules.dashboard.dto.MonthlyTrendDto;
import com.waad.tba.modules.dashboard.dto.RecentActivityDto;
import com.waad.tba.modules.dashboard.dto.ServiceDistributionDto;
import com.waad.tba.modules.dashboard.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard Controller
 * 
 * Provides dedicated endpoints for dashboard statistics and analytics.
 * All calculations are done server-side using JPQL aggregations.
 * No entities returned - only DTOs with aggregated data.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "APIs for dashboard statistics and charts")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService service;

    /**
     * GET /api/dashboard/summary
     * Get dashboard summary statistics (KPIs)
     * 
     * Returns aggregated statistics for dashboard overview:
     * - Total/Active members
     * - Total/Open/Approved claims
     * - Total/Active providers
     * - Total/Active contracts
     * - Total medical cost
     * - Monthly growth percentage
     * 
     * @param employerId Optional employer ID filter for partner-specific reports
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get dashboard summary", description = "Returns aggregated statistics for dashboard KPIs. All calculations done server-side. Optionally filter by employer.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Summary retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getSummary(
            @Parameter(description = "Employer ID for partner-specific filtering") @RequestParam(name = "employerId", required = false) Long employerId) {
        DashboardSummaryDto summary = service.getSummary(employerId);
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary retrieved successfully", summary));
    }

    /**
     * GET /api/dashboard/monthly-trends
     * Get monthly trends for claims
     * 
     * Returns monthly aggregated data for charts.
     * Default: last 12 months
     */
    @GetMapping("/monthly-trends")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get monthly trends", description = "Returns monthly aggregated data for claims. Used for line charts. Optionally filter by employer.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trends retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<MonthlyTrendDto>>> getMonthlyTrends(
            @Parameter(description = "Number of months to retrieve (max 60)", example = "12") @RequestParam(name = "months", defaultValue = "12") int months,
            @Parameter(description = "Employer ID for partner-specific filtering") @RequestParam(name = "employerId", required = false) Long employerId) {
        int cappedMonths = Math.min(Math.max(1, months), 60);
        List<MonthlyTrendDto> trends = service.getMonthlyTrends(cappedMonths, employerId);
        return ResponseEntity.ok(ApiResponse.success("Monthly trends retrieved successfully", trends));
    }

    /**
     * GET /api/dashboard/members-growth
     * Get monthly growth trends for members
     * 
     * Returns monthly aggregated member data for area charts.
     * Default: last 12 months
     */
    @GetMapping("/members-growth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get members monthly growth", description = "Returns monthly aggregated member data for area charts.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Members growth retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<MonthlyTrendDto>>> getMembersGrowth(
            @Parameter(description = "Number of months to retrieve (max 60)", example = "12") @RequestParam(name = "months", defaultValue = "12") int months) {
        int cappedMonths = Math.min(Math.max(1, months), 60);
        List<MonthlyTrendDto> growth = service.getMembersGrowth(cappedMonths);
        return ResponseEntity.ok(ApiResponse.success("Members growth retrieved successfully", growth));
    }

    /**
     * GET /api/dashboard/cost-by-provider
     * Get costs aggregated by provider
     * 
     * Returns top N providers by total cost.
     * Used for bar charts.
     */
    @GetMapping("/cost-by-provider")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get costs by provider", description = "Returns aggregated costs grouped by provider. Used for bar charts.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Costs retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<CostByProviderDto>>> getCostsByProvider(
            @Parameter(description = "Maximum number of providers to return (max 100)", example = "10") @RequestParam(name = "limit", defaultValue = "10") int limit,
            @Parameter(description = "Employer ID for partner-specific filtering") @RequestParam(name = "employerId", required = false) Long employerId) {
        int cappedLimit = Math.min(Math.max(1, limit), 100);
        List<CostByProviderDto> costs = service.getCostsByProvider(cappedLimit, employerId);
        return ResponseEntity.ok(ApiResponse.success("Costs by provider retrieved successfully", costs));
    }

    /**
     * GET /api/dashboard/service-distribution
     * Get service distribution
     * 
     * Returns aggregated data grouped by service type.
     * Used for donut charts.
     */
    @GetMapping("/service-distribution")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get service distribution", description = "Returns aggregated data grouped by service type. Used for donut charts.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Service distribution retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<ServiceDistributionDto>>> getServiceDistribution(
            @Parameter(description = "Employer ID for partner-specific filtering") @RequestParam(name = "employerId", required = false) Long employerId) {
        List<ServiceDistributionDto> distribution = service.getServiceDistribution(employerId);
        return ResponseEntity.ok(ApiResponse.success("Service distribution retrieved successfully", distribution));
    }

    /**
     * GET /api/dashboard/recent-activities
     * Get recent activities
     * 
     * Returns recent system activities for timeline display.
     */
    @GetMapping("/recent-activities")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get recent activities", description = "Returns recent system activities for timeline display.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recent activities retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<RecentActivityDto>>> getRecentActivities(
            @Parameter(description = "Maximum number of activities to return (max 100)", example = "10") @RequestParam(name = "limit", defaultValue = "10") int limit) {
        int cappedLimit = Math.min(Math.max(1, limit), 100);
        List<RecentActivityDto> activities = service.getRecentActivities(cappedLimit);
        return ResponseEntity.ok(ApiResponse.success("Recent activities retrieved successfully", activities));
    }

    // ============================================================================
    // Legacy endpoints (kept for backward compatibility)
    // ============================================================================

    /**
     * GET /api/dashboard/stats
     * Legacy endpoint - kept for backward compatibility
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get dashboard stats (legacy)", description = "Legacy endpoint. Use /summary instead.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stats retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getStats(
            @Parameter(name = "X-Employer-ID", description = "Optional employer ID for filtering stats", required = false) @RequestHeader(value = "X-Employer-ID", required = false) Long employerId) {
        DashboardStatsDto stats = service.getStats(employerId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * GET /api/dashboard/claims-per-day
     * Legacy endpoint - kept for backward compatibility
     */
    @GetMapping("/claims-per-day")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Get claims per day (legacy)", description = "Legacy endpoint. Use /monthly-trends instead.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Data retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<List<ClaimsPerDayDto>>> getClaimsPerDay(
            @Parameter(name = "X-Employer-ID", description = "Optional employer ID for filtering claims", required = false) @RequestHeader(value = "X-Employer-ID", required = false) Long employerId,
            @Parameter(name = "startDate", description = "Start date (YYYY-MM-DD)", required = true) @RequestParam(name = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(name = "endDate", description = "End date (YYYY-MM-DD)", required = true) @RequestParam(name = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ClaimsPerDayDto> data = service.getClaimsPerDay(employerId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
