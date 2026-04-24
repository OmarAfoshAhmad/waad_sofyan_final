package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.ClaimStateTransitionException;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.rbac.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClaimStateMachineTest {

    @Mock
    private MedicalAuditLogService medicalAuditLogService;

    @InjectMocks
    private ClaimStateMachine claimStateMachine;

    @Test
    @DisplayName("Should allow valid transition and write audit")
    void should_allow_valid_transition_and_write_audit() {
        Claim claim = baseClaim(ClaimStatus.DRAFT);
        User actor = user("EMPLOYER_ADMIN", "employer1");

        claimStateMachine.transition(claim, ClaimStatus.SUBMITTED, actor);

        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());
        verify(medicalAuditLogService, times(1)).record(any());
    }

    @Test
    @DisplayName("Should reject invalid transition path")
    void should_reject_invalid_transition_path() {
        Claim claim = baseClaim(ClaimStatus.DRAFT);
        User actor = user("EMPLOYER_ADMIN", "employer1");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.APPROVED, actor));
    }

    @Test
    @DisplayName("Should reject transition when role is not allowed")
    void should_reject_role_denied_transition() {
        Claim claim = baseClaim(ClaimStatus.DRAFT);
        User actor = user("MEDICAL_REVIEWER", "reviewer1");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.SUBMITTED, actor));
    }

    @Test
    @DisplayName("Should lock terminal states REJECTED and SETTLED")
    void should_lock_terminal_states() {
        Claim claim = baseClaim(ClaimStatus.REJECTED);
        User actor = user("ACCOUNTANT", "acc1");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.APPROVED, actor));
    }

    @Test
    @DisplayName("Should enforce no lines guard before approval")
    void should_reject_approval_without_lines() {
        Claim claim = baseClaim(ClaimStatus.UNDER_REVIEW);
        claim.setLines(List.of());
        claim.setApprovedAmount(new BigDecimal("100.00"));
        User actor = user("MEDICAL_REVIEWER", "reviewer1");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.APPROVED, actor));
    }

    @Test
    @DisplayName("Should enforce totalApproved > 0 before approval")
    void should_reject_approval_when_total_approved_is_zero() {
        Claim claim = baseClaim(ClaimStatus.UNDER_REVIEW);
        claim.setApprovedAmount(BigDecimal.ZERO);
        User actor = user("MEDICAL_REVIEWER", "reviewer1");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.APPROVED, actor));
    }

    @Test
    @DisplayName("Should enforce precondition completeness for SUBMITTED to UNDER_REVIEW")
    void should_reject_under_review_when_claim_incomplete() {
        Claim claim = baseClaim(ClaimStatus.SUBMITTED);
        claim.setServiceDate(null);
        User actor = user("MEDICAL_REVIEWER", "reviewer1");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.UNDER_REVIEW, actor));
    }

    @Test
    @DisplayName("Should enforce context guards for pending recalculation")
    void should_reject_approval_when_recalculation_pending() {
        Claim claim = baseClaim(ClaimStatus.UNDER_REVIEW);
        User actor = user("MEDICAL_REVIEWER", "reviewer1");

        ClaimStateMachine.TransitionContext context = new ClaimStateMachine.TransitionContext(
                new BigDecimal("120.00"),
                true,
                true,
                true,
                true,
                "pending recalculation");

        assertThrows(ClaimStateTransitionException.class,
                () -> claimStateMachine.transition(claim, ClaimStatus.APPROVED, actor, context));
    }

    @Test
    @DisplayName("Should be idempotent with no side effects for same state")
    void should_be_idempotent_for_same_target_status() {
        Claim claim = baseClaim(ClaimStatus.SUBMITTED);
        User actor = user("ACCOUNTANT", "acc1");

        claimStateMachine.transition(claim, ClaimStatus.SUBMITTED, actor);

        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());
        verify(medicalAuditLogService, never()).record(any());
    }

    private Claim baseClaim(ClaimStatus status) {
        ClaimLine line = ClaimLine.builder()
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .totalPrice(new BigDecimal("100.00"))
                .coveragePercentSnapshot(80)
                .serviceCode("SRV-1")
                .build();

        Member member = Member.builder().id(10L).build();

        return Claim.builder()
                .id(1L)
                .status(status)
                .member(member)
                .providerId(20L)
                .serviceDate(LocalDate.now())
                .approvedAmount(new BigDecimal("100.00"))
                .lines(List.of(line))
                .build();
    }

    private User user(String role, String username) {
        return User.builder()
                .userType(role)
                .username(username)
                .build();
    }
}
