package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.preauthorization.dto.PreAuthDashboardDto;
import com.waad.tba.modules.preauthorization.service.PreAuthDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for PreAuthorization Dashboard and Analytics
 */
@RestController
@RequestMapping("/api/v1/pre-authorizations/dashboard")
@RequiredArgsConstructor
@Slf4j
public class PreAuthDashboardController {

    private final PreAuthDashboardService dashboardService;

    /**
     * Get complete dashboard data
     * GET /api/pre-authorizations/dashboard
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthDashboardDto>> getDashboard(
            @RequestParam(name = "trendDays", defaultValue = "30") int trendDays,
            @RequestParam(name = "topProviders", defaultValue = "10") int topProviders
    ) {
        log.info("[DASHBOARD-API] Fetching complete dashboard (trends: {} days, topProviders: {})", 
                trendDays, topProviders);

        PreAuthDashboardDto dashboard = dashboardService.getDashboardData(trendDays, topProviders);

        return ResponseEntity.ok(ApiResponse.success("Dashboard data retrieved successfully", dashboard));
    }

    /**
     * Get overall statistics only
     * GET /api/pre-authorizations/dashboard/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthDashboardDto.OverallStats>> getOverallStats() {
        log.info("[DASHBOARD-API] Fetching overall statistics");

        PreAuthDashboardDto.OverallStats stats = dashboardService.getOverallStats();

        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", stats));
    }

    /**
     * Get status distribution
     * GET /api/pre-authorizations/dashboard/status-distribution
     */
    @GetMapping("/status-distribution")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthDashboardDto.StatusDistribution>> getStatusDistribution() {
        log.info("[DASHBOARD-API] Fetching status distribution");

        PreAuthDashboardDto.StatusDistribution distribution = dashboardService.getStatusDistribution();

        return ResponseEntity.ok(ApiResponse.success("Status distribution retrieved successfully", distribution));
    }

    /**
     * Get high priority queue
     * GET /api/pre-authorizations/dashboard/high-priority
     */
    @GetMapping("/high-priority")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthDashboardDto.PreAuthSummaryDto>>> getHighPriorityQueue(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        log.info("[DASHBOARD-API] Fetching high priority queue (limit: {})", limit);

        List<PreAuthDashboardDto.PreAuthSummaryDto> queue = dashboardService.getHighPriorityQueue(limit);

        return ResponseEntity.ok(ApiResponse.success("High priority queue retrieved successfully", queue));
    }

    /**
     * Get expiring soon items
     * GET /api/pre-authorizations/dashboard/expiring-soon
     */
    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthDashboardDto.PreAuthSummaryDto>>> getExpiringSoon(
            @RequestParam(name = "withinDays", defaultValue = "7") int withinDays,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        log.info("[DASHBOARD-API] Fetching items expiring within {} days (limit: {})", withinDays, limit);

        List<PreAuthDashboardDto.PreAuthSummaryDto> expiring = dashboardService.getExpiringSoon(withinDays, limit);

        return ResponseEntity.ok(ApiResponse.success("Expiring items retrieved successfully", expiring));
    }

    /**
     * Get trend data
     * GET /api/pre-authorizations/dashboard/trends
     */
    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthDashboardDto.TrendData>>> getTrends(
            @RequestParam(name = "days", defaultValue = "30") int days
    ) {
        log.info("[DASHBOARD-API] Fetching trend data for last {} days", days);

        List<PreAuthDashboardDto.TrendData> trends = dashboardService.getTrends(days);

        return ResponseEntity.ok(ApiResponse.success("Trends retrieved successfully", trends));
    }

    /**
     * Get top providers
     * GET /api/pre-authorizations/dashboard/top-providers
     */
    @GetMapping("/top-providers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthDashboardDto.ProviderSummary>>> getTopProviders(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        log.info("[DASHBOARD-API] Fetching top {} providers", limit);

        List<PreAuthDashboardDto.ProviderSummary> providers = dashboardService.getTopProviders(limit);

        return ResponseEntity.ok(ApiResponse.success("Top providers retrieved successfully", providers));
    }

    /**
     * Get recent activity
     * GET /api/pre-authorizations/dashboard/recent-activity
     */
    @GetMapping("/recent-activity")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthDashboardDto.RecentActivity>>> getRecentActivity(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        log.info("[DASHBOARD-API] Fetching recent {} activities", limit);

        List<PreAuthDashboardDto.RecentActivity> activity = dashboardService.getRecentActivity(limit);

        return ResponseEntity.ok(ApiResponse.success("Recent activity retrieved successfully", activity));
    }
}
