package com.waad.tba.modules.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.projection.MonthlyTrendProjection;
import com.waad.tba.modules.claim.projection.CostsByProviderProjection;
import com.waad.tba.modules.claim.projection.ServiceDistributionProjection;
import com.waad.tba.modules.claim.projection.RecentClaimProjection;
import com.waad.tba.modules.dashboard.dto.ClaimsPerDayDto;
import com.waad.tba.modules.dashboard.dto.CostByProviderDto;
import com.waad.tba.modules.dashboard.dto.DashboardStatsDto;
import com.waad.tba.modules.dashboard.dto.DashboardSummaryDto;
import com.waad.tba.modules.dashboard.dto.MonthlyTrendDto;
import com.waad.tba.modules.dashboard.dto.RecentActivityDto;
import com.waad.tba.modules.dashboard.dto.ServiceDistributionDto;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;
    private final ProviderRepository providerRepository;
    private final ProviderContractRepository contractRepository;
    private final AuthorizationService authorizationService;

    /**
     * Get dashboard summary statistics
     * All calculations done server-side using JPQL aggregations
     * 
     * @param employerId Optional employer ID filter for partner-specific reports
     *                   (null = all employers)
     * @return DashboardSummaryDto with aggregated statistics
     */
    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(Long employerId) {
        // Check for PROVIDER context
        User currentUser = authorizationService.getCurrentUser();
        if (authorizationService.isProvider(currentUser)) {
            Long providerId = currentUser.getProviderId();
            if (providerId != null) {
                log.debug("📊 Fetching dashboard summary for PROVIDER {}", providerId);
                long totalClaims = claimRepository.countByProviderIdAndActiveTrue(providerId);
                long openClaims = claimRepository.countOpenClaimsByProvider(providerId);
                long approvedClaims = claimRepository.countApprovedClaimsByProvider(providerId);
                long rejectedClaims = claimRepository.countByStatusAndProviderIdAndActiveTrue(
                        com.waad.tba.modules.claim.entity.ClaimStatus.REJECTED, providerId);
                BigDecimal totalMedicalCost = claimRepository.sumApprovedAmountsByProvider(providerId);

                return DashboardSummaryDto.builder()
                        .totalMembers(0L)
                        .activeMembers(0L)
                        .totalClaims(totalClaims)
                        .openClaims(openClaims)
                        .approvedClaims(approvedClaims)
                        .rejectedClaims(rejectedClaims)
                        .totalProviders(1L)
                        .activeProviders(1L)
                        .totalContracts(1L)
                        .activeContracts(1L)
                        .totalMedicalCost(totalMedicalCost)
                        .monthlyGrowth(BigDecimal.ZERO)
                        .build();
            }
        }

        log.debug("📊 Fetching dashboard summary statistics"
                + (employerId != null ? " for employerId=" + employerId : " (all employers)"));

        // Count members - filter by employer if provided
        long totalMembers = employerId != null
                ? memberRepository.countByEmployerId(employerId)
                : memberRepository.count();
        long activeMembers = employerId != null
                ? memberRepository.countByEmployerIdAndActiveTrue(employerId)
                : memberRepository.countActiveMembers();

        // Count claims - filter by employer via member relationship if provided
        long totalClaims = employerId != null
                ? claimRepository.countByMemberEmployerId(employerId)
                : claimRepository.countActive();
        long openClaims = employerId != null
                ? claimRepository.countOpenClaimsByEmployer(employerId)
                : claimRepository.countOpenClaims();
        long approvedClaims = employerId != null
                ? claimRepository.countApprovedClaimsByEmployer(employerId)
                : claimRepository.countApprovedClaims();
        long rejectedClaims = employerId != null
                ? claimRepository.countByStatusAndEmployerOrgId(
                        com.waad.tba.modules.claim.entity.ClaimStatus.REJECTED, employerId)
                : claimRepository.countByStatusAndActiveTrue(com.waad.tba.modules.claim.entity.ClaimStatus.REJECTED);

        // Count providers (not filtered by employer - providers serve all employers)
        long totalProviders = providerRepository.count();
        long activeProviders = providerRepository.countActiveProviders();

        // Count contracts (not filtered by employer - contracts are between TPA and
        // providers)
        long totalContracts = contractRepository.countByActiveTrue();
        long activeContracts = contractRepository.countByStatusAndActiveTrue(
                com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus.ACTIVE);

        // Sum medical costs - filter by employer if provided
        BigDecimal totalMedicalCost = employerId != null
                ? claimRepository.sumApprovedAmountsByEmployer(employerId)
                : claimRepository.sumApprovedAmounts();

        // Calculate monthly growth (simplified - compare current month vs previous
        // month)
        BigDecimal monthlyGrowth = calculateMonthlyGrowth(employerId);

        log.debug(
                "📊 Dashboard Summary" + (employerId != null ? " (Employer " + employerId + ")" : " (All)")
                        + " - Members: {}/{}, Providers: {}/{}",
                totalMembers, activeMembers, totalProviders, activeProviders);

        return DashboardSummaryDto.builder()
                .totalMembers(totalMembers)
                .activeMembers(activeMembers)
                .totalClaims(totalClaims)
                .openClaims(openClaims)
                .approvedClaims(approvedClaims)
                .rejectedClaims(rejectedClaims)
                .totalProviders(totalProviders)
                .activeProviders(activeProviders)
                .totalContracts(totalContracts)
                .activeContracts(activeContracts)
                .totalMedicalCost(totalMedicalCost)
                .monthlyGrowth(monthlyGrowth)
                .build();
    }

    /**
     * Get monthly trends for claims
     * 
     * @param months     Number of months to retrieve (default: 12)
     * @param employerId Optional employer ID filter for partner-specific reports
     *                   (null = all employers)
     * @return List of MonthlyTrendDto
     */
    @Transactional(readOnly = true)
    public List<MonthlyTrendDto> getMonthlyTrends(int months, Long employerId) {
        log.debug("📊 Fetching monthly trends for last {} months"
                + (employerId != null ? " for employerId=" + employerId : ""), months);

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusMonths(months);

        // ✅ TYPE-SAFE: Using interface projection instead of Object[]
        List<MonthlyTrendProjection> results = employerId != null
                ? claimRepository.getMonthlyTrendsByEmployer(startDate, endDate, employerId)
                : claimRepository.getMonthlyTrends(startDate, endDate);

        return results.stream()
                .map(projection -> MonthlyTrendDto.builder()
                        .month(formatYearMonth(
                                projection.getYear() != null ? ((Number) projection.getYear()).intValue() : 0,
                                projection.getMonth() != null ? ((Number) projection.getMonth()).intValue() : 0))
                        .count(projection.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get members monthly growth trends
     * 
     * @param months Number of months to retrieve (default: 12)
     * @return List of MonthlyTrendDto
     */
    @Transactional(readOnly = true)
    public List<MonthlyTrendDto> getMembersGrowth(int months) {
        log.debug("📊 Fetching members growth for last {} months", months);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);

        List<Object[]> results = memberRepository.getMonthlyGrowthTrends(startDate, endDate);

        return results.stream()
                .map(row -> {
                    Integer year = row[0] != null ? ((Number) row[0]).intValue() : 0;
                    Integer month = row[1] != null ? ((Number) row[1]).intValue() : 0;
                    Long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;

                    return MonthlyTrendDto.builder()
                            .month(formatYearMonth(year, month))
                            .count(count)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Format year and month as ISO string "YYYY-MM"
     */
    private String formatYearMonth(Integer year, Integer month) {
        return String.format("%d-%02d", year, month);
    }

    /**
     * Get costs by provider (top N)
     * 
     * @param limit      Maximum number of providers to return
     * @param employerId Optional employer ID filter
     * @return List of CostByProviderDto
     */
    @Transactional(readOnly = true)
    public List<CostByProviderDto> getCostsByProvider(int limit, Long employerId) {
        log.debug("📊 Fetching costs by provider (limit: {})"
                + (employerId != null ? " for employerId=" + employerId : ""), limit);

        // ✅ TYPE-SAFE: Using interface projection instead of Object[]
        List<CostsByProviderProjection> results = employerId != null
                ? claimRepository.getCostsByProviderByEmployer(employerId)
                : claimRepository.getCostsByProvider();

        return results.stream()
                .limit(limit)
                .map(projection -> CostByProviderDto.builder()
                        .providerId(projection.getProviderId())
                        .providerName(projection.getProviderName() != null ? projection.getProviderName() : "غير محدد")
                        .totalCost(projection.getTotalCost())
                        .claimCount(projection.getClaimCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get service distribution
     * Aggregated by medical service name from ClaimLines
     * 
     * @param employerId Optional employer ID filter
     * @return List of ServiceDistributionDto
     */
    @Transactional(readOnly = true)
    public List<ServiceDistributionDto> getServiceDistribution(Long employerId) {
        log.debug("📊 Fetching service distribution" + (employerId != null ? " for employerId=" + employerId : ""));

        // ✅ TYPE-SAFE: Using interface projection instead of Object[]
        List<ServiceDistributionProjection> results = employerId != null
                ? claimRepository.getServiceDistributionByEmployer(employerId)
                : claimRepository.getServiceDistribution();

        // Calculate total for percentage
        long total = results.stream()
                .mapToLong(ServiceDistributionProjection::getCount)
                .sum();

        return results.stream()
                .map(projection -> {
                    String serviceName = projection.getServiceName();
                    Long count = projection.getCount();
                    double percentage = total > 0 ? (count.doubleValue() / total) * 100.0 : 0.0;

                    return ServiceDistributionDto.builder()
                            .serviceType(serviceName) // Now using service name as type/name for better accuracy
                            .serviceName(serviceName)
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get recent activities
     * Combines recent members, claims, and contracts
     * 
     * @param limit Maximum number of activities to return
     * @return List of RecentActivityDto
     */
    @Transactional(readOnly = true)
    public List<RecentActivityDto> getRecentActivities(int limit) {
        // Check for PROVIDER context
        User currentUser = authorizationService.getCurrentUser();
        if (authorizationService.isProvider(currentUser)) {
            Long providerId = currentUser.getProviderId();
            if (providerId != null) {
                log.debug("📊 Fetching recent activities for PROVIDER {} (limit: {})", providerId, limit);
                Pageable pageable = PageRequest.of(0, limit);
                // ✅ TYPE-SAFE: Using interface projection instead of Object[]
                List<RecentClaimProjection> recentClaims = claimRepository.getRecentClaimsByProvider(providerId,
                        pageable);

                return recentClaims.stream().map(projection -> {
                    Long id = projection.getId();
                    String memberName = projection.getMemberName();
                    String diagnosis = projection.getDiagnosisDescription();
                    Object statusObj = projection.getStatus();
                    LocalDateTime createdAt = projection.getCreatedAt();

                    String statusLabel = statusObj != null ? statusObj.toString() : "";
                    String description = memberName + (diagnosis != null ? " - " + diagnosis : "");

                    return RecentActivityDto.builder()
                            .id(id)
                            .type("CLAIM_SUBMITTED")
                            .title("مطالبة " + statusLabel)
                            .description(description)
                            .entityName(memberName)
                            .entityId(id)
                            .createdAt(createdAt)
                            .build();
                }).collect(Collectors.toList());
            }
        }

        log.debug("📊 Fetching recent activities (limit: {})", limit);

        List<RecentActivityDto> activities = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, limit / 3 + 1); // Get a few from each category

        // Get recent members
        List<Object[]> recentMembers = memberRepository.getRecentMembers(pageable);
        for (Object[] row : recentMembers) {
            Long id = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            String name = (String) row[1];
            LocalDateTime createdAt = null;
            if (row[2] instanceof LocalDateTime) {
                createdAt = (LocalDateTime) row[2];
            } else if (row[2] instanceof java.sql.Timestamp) {
                createdAt = ((java.sql.Timestamp) row[2]).toLocalDateTime();
            }

            activities.add(RecentActivityDto.builder()
                    .id(id)
                    .type("MEMBER_ADDED")
                    .title("تمت إضافة عضو جديد")
                    .description(name)
                    .entityName(name)
                    .entityId(id)
                    .createdAt(createdAt)
                    .build());
        }

        // Get recent claims
        // ✅ TYPE-SAFE: Using interface projection instead of Object[]
        List<RecentClaimProjection> recentClaims = claimRepository.getRecentClaims(pageable);
        for (RecentClaimProjection projection : recentClaims) {
            Long id = projection.getId();
            String memberName = projection.getMemberName();
            String diagnosis = projection.getDiagnosisDescription();
            // ClaimStatus status = projection.getStatus(); // Status available but not used
            // in display
            LocalDateTime createdAt = projection.getCreatedAt();

            String description = memberName + (diagnosis != null ? " - " + diagnosis : "");

            activities.add(RecentActivityDto.builder()
                    .id(id)
                    .type("CLAIM_SUBMITTED")
                    .title("مطالبة جديدة")
                    .description(description)
                    .entityName(memberName)
                    .entityId(id)
                    .createdAt(createdAt)
                    .build());
        }

        try {
            // Get recent contracts
            List<Object[]> recentContracts = contractRepository.getRecentContracts(pageable);
            for (Object[] row : recentContracts) {
                Long id = row[0] != null ? ((Number) row[0]).longValue() : 0L;
                String contractCode = (String) row[1];
                String providerName = (String) row[2];
                LocalDateTime createdAt = null;
                if (row[4] instanceof LocalDateTime) {
                    createdAt = (LocalDateTime) row[4];
                } else if (row[4] instanceof java.sql.Timestamp) {
                    createdAt = ((java.sql.Timestamp) row[4]).toLocalDateTime();
                }

                String description = "عقد " + contractCode + " - " + providerName;

                activities.add(RecentActivityDto.builder()
                        .id(id)
                        .type("CONTRACT_UPDATED")
                        .title("تحديث عقد")
                        .description(description)
                        .entityName(providerName)
                        .entityId(id)
                        .createdAt(createdAt)
                        .build());
            }
        } catch (Exception e) {
            // Gracefully handle missing table or SQL errors during migration
            log.warn("Could not fetch recent contracts for dashboard: {}", e.getMessage());
        }

        // Sort all activities by date (most recent first) and limit
        return activities.stream()
                .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Calculate monthly growth percentage
     * Compares current month vs previous month
     * 
     * Uses member count growth as the primary metric
     */
    private BigDecimal calculateMonthlyGrowth(Long employerId) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Current month range
            LocalDateTime currentMonthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime currentMonthEnd = now;

            // Previous month range
            LocalDateTime previousMonthStart = currentMonthStart.minusMonths(1);
            LocalDateTime previousMonthEnd = currentMonthStart.minusSeconds(1);

            // Count members in each period (with optional employer filtering)
            long currentMonthCount = employerId != null
                    ? memberRepository.countMembersInDateRangeByEmployer(currentMonthStart, currentMonthEnd, employerId)
                    : memberRepository.countMembersInDateRange(currentMonthStart, currentMonthEnd);

            long previousMonthCount = employerId != null
                    ? memberRepository.countMembersInDateRangeByEmployer(previousMonthStart, previousMonthEnd,
                            employerId)
                    : memberRepository.countMembersInDateRange(previousMonthStart, previousMonthEnd);

            // Calculate percentage growth
            if (previousMonthCount == 0) {
                return currentMonthCount > 0 ? BigDecimal.valueOf(100.0) : BigDecimal.ZERO;
            }

            double growth = ((double) (currentMonthCount - previousMonthCount) / previousMonthCount) * 100.0;
            return BigDecimal.valueOf(growth).setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.warn("⚠️ Error calculating monthly growth: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ============================================================================
    // Legacy methods (kept for backward compatibility)
    // ============================================================================

    /**
     * Get dashboard statistics (legacy method)
     * 
     * @param requestedEmployerId IGNORED - kept for API compatibility
     * @return Dashboard statistics (global)
     */
    @Transactional(readOnly = true)
    public DashboardStatsDto getStats(Long requestedEmployerId) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user for dashboard stats");
            return createEmptyStats();
        }

        log.debug("📊 Dashboard showing global stats (employer filtering disabled)");
        return getGlobalStats();
    }

    /**
     * Get global stats (all data).
     */
    private DashboardStatsDto getGlobalStats() {
        long totalMembers = memberRepository.count();
        long totalClaims = claimRepository.countActive();

        long pendingClaims = claimRepository.countOpenClaims();
        long approvedClaims = claimRepository.countApprovedClaims();
        long rejectedClaims = claimRepository.countByStatus(
                com.waad.tba.modules.claim.entity.ClaimStatus.REJECTED);

        return DashboardStatsDto.builder()
                .totalMembers(totalMembers)
                .totalClaims(totalClaims)
                .pendingClaims(pendingClaims)
                .approvedClaims(approvedClaims)
                .rejectedClaims(rejectedClaims)
                .totalEmployers(0L) // Add employer count if needed
                .totalInsuranceCompanies(0L) // Add if needed
                .build();
    }

    /**
     * Create empty stats when user is not authenticated.
     */
    private DashboardStatsDto createEmptyStats() {
        return DashboardStatsDto.builder()
                .totalMembers(0L)
                .totalClaims(0L)
                .pendingClaims(0L)
                .approvedClaims(0L)
                .rejectedClaims(0L)
                .totalEmployers(0L)
                .totalInsuranceCompanies(0L)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ClaimsPerDayDto> getClaimsPerDay(Long requestedEmployerId, LocalDate startDate, LocalDate endDate) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user for claims per day");
            return new ArrayList<>();
        }

        log.debug("📊 Fetching claims per day from {} to {} (employer filtering disabled)",
                startDate, endDate);

        // Add daily statistics query methods to ClaimRepository
        return new ArrayList<>();
    }
}
