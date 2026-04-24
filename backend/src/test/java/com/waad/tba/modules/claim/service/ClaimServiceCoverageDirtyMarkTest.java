package com.waad.tba.modules.claim.service;

import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.dto.ClaimDataUpdateDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderNetworkService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceCoverageDirtyMarkTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimMapper claimMapper;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ProviderContextGuard providerContextGuard;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private VisitRepository visitRepository;
    @Mock
    private PreAuthorizationRepository preAuthorizationRepository;
    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock
    private ClaimStateMachine claimStateMachine;
    @Mock
    private ProviderNetworkService providerNetworkService;
    @Mock
    private AttachmentRulesService attachmentRulesService;
    @Mock
    private CostCalculationService costCalculationService;
    @Mock
    private ClaimAuditService claimAuditService;
    @Mock
    private com.waad.tba.common.service.BusinessDaysCalculatorService businessDaysCalculator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ArchitecturalGuardService architecturalGuard;
    @Mock
    private AtomicFinancialService atomicFinancialService;
    @Mock
    private PreAuthorizationService preAuthorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock
    private ProviderAllowedEmployerRepository providerAllowedEmployerRepository;
    @Mock
    private ClaimBatchService claimBatchService;
    @Mock
    private ClaimBatchRepository claimBatchRepository;
    @Mock
    private ClaimReviewService claimReviewService;
    @Mock
    private EntityManager em;

    @InjectMocks
    private ClaimService claimService;

    @Test
    @DisplayName("updateClaimData should mark claim as pending recalculation when fullCoverage changes")
    void updateClaimData_should_mark_pending_recalculation_when_fullCoverage_changes() {
        Claim claim = Claim.builder()
                .id(100L)
                .status(ClaimStatus.DRAFT)
                .fullCoverage(false)
                .pendingRecalculation(false)
                .coverageVersion(1)
                .build();

        ClaimDataUpdateDto dto = ClaimDataUpdateDto.builder()
                .fullCoverage(true)
                .build();

        User currentUser = User.builder()
                .id(5L)
                .username("provider.user")
                .userType("PROVIDER_STAFF")
                .build();

        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(currentUser);
        when(authorizationService.canModifyClaim(currentUser, 100L)).thenReturn(true);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimService.updateClaimData(100L, dto);

        assertTrue(claim.getFullCoverage());
        assertTrue(claim.getPendingRecalculation());
        assertEquals(2, claim.getCoverageVersion());

        verify(claimAuditService).recordStatusChange(claim, ClaimStatus.DRAFT, currentUser, "Data updated");
    }

    @Test
    @DisplayName("updateClaimData should not mark claim as pending recalculation when fullCoverage is unchanged")
    void updateClaimData_should_not_mark_pending_recalculation_when_fullCoverage_unchanged() {
        Claim claim = Claim.builder()
                .id(101L)
                .status(ClaimStatus.DRAFT)
                .fullCoverage(true)
                .pendingRecalculation(false)
                .coverageVersion(7)
                .build();

        ClaimDataUpdateDto dto = ClaimDataUpdateDto.builder()
                .fullCoverage(true)
                .build();

        User currentUser = User.builder()
                .id(6L)
                .username("provider.user.2")
                .userType("PROVIDER_STAFF")
                .build();

        when(claimRepository.findById(101L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(currentUser);
        when(authorizationService.canModifyClaim(currentUser, 101L)).thenReturn(true);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimService.updateClaimData(101L, dto);

        assertTrue(claim.getFullCoverage());
        assertFalse(claim.getPendingRecalculation());
        assertEquals(7, claim.getCoverageVersion());

        verify(claimAuditService).recordStatusChange(claim, ClaimStatus.DRAFT, currentUser, "Data updated");
    }
}
