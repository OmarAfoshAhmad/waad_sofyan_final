package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.error.ErrorCode;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.dto.PreAuthReviewDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

/**
 * PUT /pre-authorizations/{id}/review used to copy whatever status the body
 * named straight onto the record. Two consequences, both fixed here:
 *
 *  1. No source check: an APPROVED authorization could be re-decided as
 *     REJECTED (or a REJECTED one revived) by a reviewer.
 *  2. No ledger: status=APPROVED set the status without reserving a single
 *     dinar of the member's ceiling. Approval has exactly two canonical paths,
 *     POST /{id}/approve and the line-review /finalize, and both go through
 *     PreAuthReservationLedgerService. This endpoint is for sending a request
 *     back or refusing it.
 */
@ExtendWith(MockitoExtension.class)
class PreAuthReviewEndpointCannotBypassTheStateMachineTest {

    @Mock PreAuthorizationRepository preAuthorizationRepository;
    @Mock AuthorizationService authorizationService;
    @Mock ReviewerProviderIsolationService reviewerIsolationService;
    @Mock PreAuthorizationAuditService auditService;
    @Mock PreAuthReservationLedgerService reservationLedgerService;
    @Mock MemberRepository memberRepository;
    @Mock ProviderRepository providerRepository;

    @InjectMocks PreAuthorizationService service;

    private User reviewer;

    @BeforeEach
    void reviewerIsAuthorized() {
        reviewer = User.builder().id(5L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        lenient().when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        lenient().when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        lenient().when(authorizationService.isSuperAdmin(reviewer)).thenReturn(false);
    }

    private PreAuthorization stored(PreAuthStatus status) {
        PreAuthorization preAuth = PreAuthorization.builder()
                .id(77L).providerId(3L).memberId(9L).referenceNumber("PA-77")
                .status(status).active(true).build();
        when(preAuthorizationRepository.findById(77L)).thenReturn(Optional.of(preAuth));
        return preAuth;
    }

    private static PreAuthReviewDto decision(PreAuthStatus status, String comment) {
        PreAuthReviewDto dto = new PreAuthReviewDto();
        dto.setStatus(status);
        dto.setReviewerComment(comment);
        return dto;
    }

    @Test
    void shouldRefuseToRejectAnAlreadyApprovedAuthorization() {
        PreAuthorization preAuth = stored(PreAuthStatus.APPROVED);

        assertThatThrownBy(() -> service.reviewPreAuth(77L, decision(PreAuthStatus.REJECTED, "changed my mind"), "reviewer"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PREAUTH_TRANSITION);

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.APPROVED);
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void shouldRefuseToReviveARejectedAuthorization() {
        PreAuthorization preAuth = stored(PreAuthStatus.REJECTED);

        assertThatThrownBy(() -> service.reviewPreAuth(77L, decision(PreAuthStatus.NEEDS_CORRECTION, "try again"), "reviewer"))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.REJECTED);
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void shouldNotGrantApprovalOutsideTheLedger() {
        PreAuthorization preAuth = stored(PreAuthStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> service.reviewPreAuth(77L, decision(PreAuthStatus.APPROVED, "ok"), "reviewer"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("الاعتماد");

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.UNDER_REVIEW);
        verify(preAuthorizationRepository, never()).save(any());
        verify(reservationLedgerService, never()).approveAndReserve(any(), any(), any());
    }

    @Test
    void shouldNotGrantPartialApprovalOutsideTheLedgerEither() {
        stored(PreAuthStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> service.reviewPreAuth(77L, decision(PreAuthStatus.PARTIALLY_APPROVED, "half"), "reviewer"))
                .isInstanceOf(BusinessRuleException.class);

        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void shouldStillReturnARequestForCorrectionFromReview() {
        PreAuthorization preAuth = stored(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewPreAuth(77L, decision(PreAuthStatus.NEEDS_CORRECTION, "missing the lab report"), "reviewer");

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.NEEDS_CORRECTION);
        assertThat(preAuth.getNotes()).isEqualTo("missing the lab report");
    }

    @Test
    void shouldStillRejectFromReview() {
        PreAuthorization preAuth = stored(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewPreAuth(77L, decision(PreAuthStatus.REJECTED, "not covered"), "reviewer");

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.REJECTED);
        assertThat(preAuth.getRejectionReason()).isEqualTo("not covered");
    }
}
