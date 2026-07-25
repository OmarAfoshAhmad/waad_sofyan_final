package com.waad.tba.modules.dashboard.service;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.dashboard.dto.DashboardStatsDto;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for dashboard cross-tenant data leaks found during
 * SECTION_02's audit of remaining modules:
 * - getStats() ("employer filtering disabled") always returned global
 *   member/claim counts to EMPLOYER_ADMIN/PROVIDER_STAFF callers.
 * - getMembersGrowth() never scoped by employer at all.
 * Both now resolve the caller's employer scope the same way getSummary()
 * already did, and fall back to global-scope repository methods only when
 * resolveEmployerScope returns null (internal staff).
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceScopeTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private DashboardService dashboardService;

    private User employerAdmin;

    @BeforeEach
    void setUp() {
        employerAdmin = User.builder().id(3L).username("employer-a-admin")
                .userType("EMPLOYER_ADMIN").employerId(10L).build();
        when(authorizationService.getCurrentUser()).thenReturn(employerAdmin);
    }

    @Test
    void getStatsUsesEmployerScopedCountsForEmployerAdmin() {
        when(authorizationService.resolveEmployerScope(employerAdmin, null)).thenReturn(10L);
        when(memberRepository.countByEmployerId(10L)).thenReturn(5L);
        when(claimRepository.countByMemberEmployerId(10L)).thenReturn(2L);
        when(claimRepository.countOpenClaimsByEmployer(10L)).thenReturn(1L);
        when(claimRepository.countApprovedClaimsByEmployer(10L)).thenReturn(1L);
        when(claimRepository.countByStatusAndEmployerOrgId(ClaimStatus.REJECTED, 10L)).thenReturn(0L);

        DashboardStatsDto stats = dashboardService.getStats(null);

        assertThat(stats.getTotalMembers()).isEqualTo(5L);
        // Must never fall through to the unscoped global counters for a scoped caller.
        verify(memberRepository, never()).count();
        verify(claimRepository, never()).countActive();
    }

    @Test
    void getMembersGrowthUsesEmployerScopedQueryForEmployerAdmin() {
        when(authorizationService.resolveEmployerScope(employerAdmin, null)).thenReturn(10L);
        when(memberRepository.getMonthlyGrowthTrendsByEmployer(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(10L)))
                .thenReturn(Collections.emptyList());

        dashboardService.getMembersGrowth(12);

        verify(memberRepository, never()).getMonthlyGrowthTrends(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
