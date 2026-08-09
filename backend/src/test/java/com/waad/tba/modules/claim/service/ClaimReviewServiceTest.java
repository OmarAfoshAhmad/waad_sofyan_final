package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimRejectDto;
import com.waad.tba.modules.claim.dto.ClaimSettleDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.ClaimSubmissionSource;
import com.waad.tba.modules.claim.dto.ClaimLineReviewDecision;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.settlement.service.ProviderAccountService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;

@ExtendWith(MockitoExtension.class)
class ClaimReviewServiceTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimMapper claimMapper;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock
    private ClaimStateMachine claimStateMachine;
    @Mock
    private AtomicFinancialService atomicFinancialService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock
    private ClaimReversalOrchestrator claimReversalOrchestrator;
    @Mock
    private com.waad.tba.common.service.BusinessDaysCalculatorService businessDaysCalculator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ClaimAuditService claimAuditService;
    @Mock
    private ProviderAccountService providerAccountService;
    @Mock
    private MedicalAuditLogService medicalAuditLogService;
    @Mock
    private ClaimApprovalRecoveryWorker approvalRecoveryWorker;
    @Mock
    private ClaimFinancialSnapshotService financialSnapshotService;

    @InjectMocks
    private ClaimReviewService claimReviewService;

    private User reviewer;
    private Claim claim;
    private Member member;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(1L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        member = Member.builder().id(10L).fullName("Test Member").build();
        claim = Claim.builder()
                .id(100L)
                .status(ClaimStatus.SUBMITTED)
                .member(member)
                .providerId(50L)
                .requestedAmount(new BigDecimal("1000"))
                .build();
    }

    @Test
    void startReview_shouldTransitionStatus() {
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.startReview(100L);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.UNDER_REVIEW), eq(reviewer), any());
        verify(claimRepository).save(claim);
    }

    @Test
    void startReview_invalidStatus_shouldThrowException() {
        claim.setStatus(ClaimStatus.APPROVED);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimReviewService.startReview(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يمكن بدء المراجعة");
    }

    @Test
    void pauseAndResumeReview_shouldKeepUnderReviewStatus() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.pauseReview(100L, "بانتظار مراجعة التقرير الطبي");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getReviewPaused()).isTrue();
        assertThat(claim.getReviewPauseReason()).contains("التقرير الطبي");

        claimReviewService.resumeReview(100L);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getReviewPaused()).isFalse();
        assertThat(claim.getReviewPauseReason()).isNull();
    }

    @Test
    void requestApproval_pausedReview_shouldFailClosed() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claim.setReviewPaused(true);
        claim.setLines(List.of(ClaimLine.builder().id(501L).claim(claim).build()));
        ClaimApproveDto dto = ClaimApproveDto.builder()
                .lineDecisions(List.of(ClaimApproveDto.LineDecision.builder()
                        .lineId(501L).decision(ClaimLineReviewDecision.APPROVE).build()))
                .build();
        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);

        assertThatThrownBy(() -> claimReviewService.requestApproval(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("استئناف المراجعة");
    }

    @Test
    void requestCorrection_shouldReverseBenefitLedgerAndProviderCredit() {
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setSubmissionSource(ClaimSubmissionSource.INTERNAL_DIRECT);
        claim.setApprovedAmount(new BigDecimal("800"));
        claim.setNetProviderAmount(new BigDecimal("800"));
        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.requestCorrection(100L, "تصحيح البنود");

        verify(claimReversalOrchestrator).reverseClaim(100L, 1L);
        verify(claimStateMachine).transition(claim, ClaimStatus.NEEDS_CORRECTION, reviewer);
        assertThat(claim.getApprovedAmount()).isNull();
        assertThat(claim.getNetProviderAmount()).isNull();
        assertThat(claim.getReviewerComment()).isEqualTo("تصحيح البنود");
    }

    @Test
    void requestCorrection_portalClaim_shouldReverseAndOpenForResubmission() {
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setSubmissionSource(ClaimSubmissionSource.PROVIDER_PORTAL);
        claim.setApprovedAmount(new BigDecimal("800"));
        claim.setNetProviderAmount(new BigDecimal("800"));
        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.requestCorrection(100L, "تصحيح");

        verify(claimReversalOrchestrator).reverseClaim(100L, 1L);
        verify(claimStateMachine).transition(claim, ClaimStatus.NEEDS_CORRECTION, reviewer);
        assertThat(claim.getApprovedAmount()).isNull();
        assertThat(claim.getNetProviderAmount()).isNull();
    }

    @Test
    void rejectClaim_shouldTransitionToRejected() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimRejectDto dto = new ClaimRejectDto();
        dto.setRejectionReason("Medical necessity not proven");

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.rejectClaim(100L, dto);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 50L);
        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.REJECTED), eq(reviewer), any());
        assertThat(claim.getReviewerComment()).isEqualTo("Medical necessity not proven");
    }

    @Test
    void rejectClaim_missingReason_shouldThrowException() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimRejectDto dto = new ClaimRejectDto(); // No reason

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);

        assertThatThrownBy(() -> claimReviewService.rejectClaim(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("سبب الرفض مطلوب");
    }

    @Test
    void settleClaim_shouldTransitionToSettled() {
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAmount(new BigDecimal("800"));
        claim.setNetProviderAmount(new BigDecimal("800"));

        ClaimSettleDto dto = new ClaimSettleDto();
        dto.setPaymentReference("PAY-123");
        dto.setSettlementAmount(new BigDecimal("800"));

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.settleClaim(100L, dto);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.SETTLED), eq(reviewer), any());
        assertThat(claim.getPaymentReference()).isEqualTo("PAY-123");
        assertThat(claim.getSettledAt()).isNotNull();
    }

    @Test
    void settleClaim_excessiveAmount_shouldThrowException() {
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setNetProviderAmount(new BigDecimal("800"));

        ClaimSettleDto dto = new ClaimSettleDto();
        dto.setPaymentReference("PAY-123");
        dto.setSettlementAmount(new BigDecimal("900")); // Exceeds approved

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimReviewService.settleClaim(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("يتجاوز المبلغ المستحق");
    }

    @Test
    void requestApproval_shouldInitiateAsyncPhase() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimLine line = ClaimLine.builder().id(501L).claim(claim).build();
        claim.setLines(List.of(line));
        ClaimApproveDto dto = new ClaimApproveDto();
        dto.setNotes("Approving this");
        dto.setLineDecisions(List.of(ClaimApproveDto.LineDecision.builder()
                .lineId(501L)
                .decision(ClaimLineReviewDecision.APPROVE)
                .build()));

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.requestApproval(100L, dto);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.APPROVAL_IN_PROGRESS), eq(reviewer), any());
        verify(eventPublisher).publishEvent(any(com.waad.tba.modules.claim.event.ClaimApprovalRequestedEvent.class));
    }

    @Test
    void requestApproval_missingLineDecision_shouldFailClosed() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claim.setLines(List.of(ClaimLine.builder().id(501L).claim(claim).build()));
        ClaimApproveDto dto = ClaimApproveDto.builder().lineDecisions(List.of()).build();

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);

        assertThatThrownBy(() -> claimReviewService.requestApproval(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("قرار واضح لكل بند");
    }

    @Test
    void requestApproval_rejectedLine_shouldPersistReviewerReasonBeforeCalculation() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimLine line = ClaimLine.builder().id(501L).claim(claim).rejected(false).build();
        claim.setLines(List.of(line));
        ClaimApproveDto dto = ClaimApproveDto.builder()
                .lineDecisions(List.of(ClaimApproveDto.LineDecision.builder()
                        .lineId(501L)
                        .decision(ClaimLineReviewDecision.REJECT)
                        .reason("غير ضروري طبياً")
                        .build()))
                .build();

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.requestApproval(100L, dto);

        assertThat(line.getRejected()).isTrue();
        assertThat(line.getRejectionReason()).isEqualTo("غير ضروري طبياً");
    }
}
