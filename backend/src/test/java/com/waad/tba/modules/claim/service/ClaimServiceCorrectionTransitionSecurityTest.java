package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.dto.ClaimUpdateDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a HIGH finding from the claims/coverage-rules
 * closure pass: the generic updateClaim() status-change path allowed
 * APPROVED -> NEEDS_CORRECTION (a legal ClaimStateMachine transition) without
 * reversing the benefit-bucket consumption / provider credit the way the
 * dedicated requestCorrection() endpoint does. Re-approving afterward would
 * double-count both the bucket consumption and the provider payment. This
 * transition must now be rejected from the generic path.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceCorrectionTransitionSecurityTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private com.waad.tba.modules.claim.mapper.ClaimMapper claimMapper;
    @Mock private AuthorizationService authorizationService;
    @Mock private ProviderContextGuard providerContextGuard;
    @Mock private com.waad.tba.modules.audit.service.MedicalAuditLogService medicalAuditLogService;
    @Mock private MemberRepository memberRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private PreAuthorizationRepository preAuthorizationRepository;
    @Mock private com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock private ClaimStateMachine claimStateMachine;
    @Mock private com.waad.tba.modules.provider.service.ProviderNetworkService providerNetworkService;
    @Mock private AttachmentRulesService attachmentRulesService;
    @Mock private CostCalculationService costCalculationService;
    @Mock private ClaimAuditService claimAuditService;
    @Mock private com.waad.tba.common.service.BusinessDaysCalculatorService businessDaysCalculator;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.waad.tba.common.service.ArchitecturalGuardService architecturalGuard;
    @Mock private AtomicFinancialService atomicFinancialService;
    @Mock private PreAuthorizationService preAuthorizationService;
    @Mock private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock private com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository providerAllowedEmployerRepository;
    @Mock private ClaimBatchService claimBatchService;
    @Mock private ClaimBatchRepository claimBatchRepository;
    @Mock private ClaimReviewService claimReviewService;
    @Mock private com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService benefitBucketLedgerService;
    @Mock private ClaimFinancialSnapshotService financialSnapshotService;
    @Mock private jakarta.persistence.EntityManager em;

    @InjectMocks
    private ClaimService claimService;

    private User currentUser;
    private Claim approvedClaim;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("reviewer1").userType("MEDICAL_REVIEWER").build();
        approvedClaim = Claim.builder().id(900L).providerId(251L).status(ClaimStatus.APPROVED).build();

        lenient().when(authorizationService.getCurrentUser()).thenReturn(currentUser);
        lenient().when(claimRepository.findByIdForUpdate(900L)).thenReturn(Optional.of(approvedClaim));
    }

    @Test
    void genericUpdateCannotReopenApprovedClaimForCorrection() {
        ClaimUpdateDto dto = ClaimUpdateDto.builder().status(ClaimStatus.NEEDS_CORRECTION).build();

        // The guard now rejects ANY update to a finalized claim (APPROVED/BATCHED/
        // SETTLED/REJECTED) up front, rather than only blocking the correction
        // transition -- so the message changed from naming the request-correction
        // endpoint to naming the reversal/corrective-settlement paths. The intent
        // this test protects is unchanged: a generic update must never reopen a
        // finalized claim, and must not touch the state machine or the ledger.
        assertThatThrownBy(() -> claimService.updateClaim(900L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يمكن إعادة كتابة مطالبة نهائية");

        verify(claimStateMachine, never()).transition(
                org.mockito.ArgumentMatchers.eq(approvedClaim),
                org.mockito.ArgumentMatchers.eq(ClaimStatus.NEEDS_CORRECTION),
                org.mockito.ArgumentMatchers.any());
        verify(benefitBucketLedgerService, never()).reverseClaim(org.mockito.ArgumentMatchers.anyLong());
    }
}
