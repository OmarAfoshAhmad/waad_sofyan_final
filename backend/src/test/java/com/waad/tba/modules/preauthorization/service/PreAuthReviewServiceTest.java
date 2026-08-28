package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.preauthorization.dto.PreAuthLineDecisionDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineDecisionStatus;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationLineRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.repository.PreauthLineSnapshotRepository;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * makeLineDecision's translation from the screen's decision (decisionStatus +
 * an optional amount) to the ONLY fields the financial engine
 * ({@link PreAuthorizationDecisionBuilder}) reads: reviewDecision,
 * approvedQuantity, explicitRejectedAmount. Everything else that method
 * writes is a display projection and is not under test here.
 */
@ExtendWith(MockitoExtension.class)
class PreAuthReviewServiceTest {

    @Mock private PreAuthorizationRepository preAuthRepo;
    @Mock private PreAuthorizationLineRepository lineRepo;
    @Mock private PreAuthorizationAuditService auditService;
    @Mock private AuthorizationService authorizationService;
    @Mock private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock private PreAuthReservationLedgerService reservationLedgerService;
    @Mock private PreauthLineSnapshotRepository lineSnapshotRepository;

    @InjectMocks
    private PreAuthReviewService service;

    private PreAuthorization preAuth;
    private PreAuthorizationLine line;

    @BeforeEach
    void setUp() {
        preAuth = PreAuthorization.builder().id(1L).status(PreAuthStatus.UNDER_REVIEW).active(true).build();
        line = PreAuthorizationLine.builder().id(10L)
                .requestedQuantity(1).requestedAmount(new BigDecimal("1000.00")).build();

        User admin = new User();
        admin.setId(99L);
        admin.setUserType("SUPER_ADMIN");

        lenient().when(preAuthRepo.findById(1L)).thenReturn(Optional.of(preAuth));
        lenient().when(lineRepo.findByIdAndPreAuthorizationId(10L, 1L)).thenReturn(Optional.of(line));
        lenient().when(authorizationService.getCurrentUser()).thenReturn(admin);
        lenient().when(authorizationService.isSuperAdmin(admin)).thenReturn(true);
        lenient().when(authorizationService.isReviewer(admin)).thenReturn(false);
    }

    @Test
    @DisplayName("REJECTED maps to REJECT with a zero explicit rejection and quantity 0")
    void rejected_MapsToCanonicalReject() {
        service.makeLineDecision(1L, 10L, PreAuthLineDecisionDto.builder()
                .decisionStatus(LineDecisionStatus.REJECTED).decisionNotes("لا يوجد مبرر طبي").build(), "reviewer");

        assertThat(line.getReviewDecision()).isEqualTo(PreAuthorizationLine.ReviewDecision.REJECT);
        assertThat(line.getApprovedQuantity()).isZero();
        assertThat(line.getExplicitRejectedAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getRejectionReason()).isEqualTo("لا يوجد مبرر طبي");
    }

    @Test
    @DisplayName("APPROVED maps to APPROVE with the full requested quantity and zero rejection")
    void approved_MapsToCanonicalApprove() {
        service.makeLineDecision(1L, 10L, PreAuthLineDecisionDto.builder()
                .decisionStatus(LineDecisionStatus.APPROVED).build(), "reviewer");

        assertThat(line.getReviewDecision()).isEqualTo(PreAuthorizationLine.ReviewDecision.APPROVE);
        assertThat(line.getApprovedQuantity()).isEqualTo(1);
        assertThat(line.getExplicitRejectedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("PARTIALLY_APPROVED derives explicitRejectedAmount as requested minus the approved figure")
    void partiallyApproved_DerivesExplicitRejectedAmount() {
        service.makeLineDecision(1L, 10L, PreAuthLineDecisionDto.builder()
                .decisionStatus(LineDecisionStatus.PARTIALLY_APPROVED)
                .approvedAmount(new BigDecimal("400.00"))
                .decisionNotes("جزء غير مبرر").build(), "reviewer");

        assertThat(line.getReviewDecision()).isEqualTo(PreAuthorizationLine.ReviewDecision.PARTIALLY_APPROVE);
        assertThat(line.getApprovedQuantity()).isEqualTo(1); // amount-based, not quantity-based
        assertThat(line.getExplicitRejectedAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("An approved amount above what was requested is refused rather than producing a negative rejection")
    void partiallyApproved_ApprovedAboveRequested_Throws() {
        assertThatThrownBy(() -> service.makeLineDecision(1L, 10L, PreAuthLineDecisionDto.builder()
                .decisionStatus(LineDecisionStatus.PARTIALLY_APPROVED)
                .approvedAmount(new BigDecimal("1500.00"))
                .decisionNotes("n/a").build(), "reviewer"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("INFO_REQUESTED leaves the canonical fields untouched (still unreviewed)")
    void infoRequested_LeavesCanonicalFieldsAlone() {
        service.makeLineDecision(1L, 10L, PreAuthLineDecisionDto.builder()
                .decisionStatus(LineDecisionStatus.INFO_REQUESTED).build(), "reviewer");

        assertThat(line.getReviewDecision()).isNull();
        assertThat(line.getApprovedQuantity()).isNull();
    }
}
