package com.waad.tba.modules.preauthorization.domain;

import static com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.waad.tba.common.error.ErrorCode;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;

/**
 * The matrix is the specification; these tests pin the edges that used to be
 * enforced in scattered guards (or not at all) so a later edit cannot quietly
 * reopen one.
 */
class PreAuthStateMachineTest {

    private static PreAuthorization at(PreAuthStatus status) {
        return PreAuthorization.builder().id(1L).status(status).active(true).build();
    }

    // ── the two holes this class was written to close ──────────────────

    @Test
    void shouldRefuseFlippingAnApprovedAuthorizationToRejected() {
        assertThatThrownBy(() -> PreAuthStateMachine.transition(at(APPROVED), REJECTED))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PREAUTH_TRANSITION);
    }

    @Test
    void shouldRefuseReopeningARejectedAuthorizationAsApproved() {
        assertThat(PreAuthStateMachine.canTransition(REJECTED, APPROVED)).isFalse();
        assertThat(PreAuthStateMachine.canTransition(REJECTED, UNDER_REVIEW)).isFalse();
    }

    // ── terminal states ────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = PreAuthStatus.class, names = { "REJECTED", "CANCELLED", "EXPIRED", "USED", "CONSUMED" })
    void terminalStatesHaveNoExits(PreAuthStatus terminal) {
        assertThat(PreAuthStateMachine.isTerminal(terminal)).isTrue();
        assertThat(PreAuthStateMachine.targetsFrom(terminal)).isEmpty();
        for (PreAuthStatus to : PreAuthStatus.values()) {
            if (to != terminal) {
                assertThat(PreAuthStateMachine.canTransition(terminal, to))
                        .as("%s -> %s", terminal, to).isFalse();
            }
        }
    }

    // ── what the existing code paths rely on (must stay open) ──────────

    @Test
    void submissionAndReviewIntakeStillWork() {
        assertThat(PreAuthStateMachine.canTransition(PENDING, UNDER_REVIEW)).isTrue();
        assertThat(PreAuthStateMachine.canTransition(NEEDS_CORRECTION, UNDER_REVIEW)).isTrue();
        assertThat(PreAuthStateMachine.canTransition(SUBMITTED, UNDER_REVIEW)).isTrue();
        assertThat(PreAuthStateMachine.canTransition(RESUBMITTED, UNDER_REVIEW)).isTrue();
    }

    @Test
    void ledgerApprovalPathsStillWork() {
        // approveAndReserve's APPROVABLE set, both outcomes
        for (PreAuthStatus from : EnumSet.of(SUBMITTED, PENDING, UNDER_REVIEW, RESUBMITTED, APPROVAL_IN_PROGRESS)) {
            assertThat(PreAuthStateMachine.canTransition(from, APPROVED)).as("%s -> APPROVED", from).isTrue();
            assertThat(PreAuthStateMachine.canTransition(from, PARTIALLY_APPROVED)).as("%s -> PARTIAL", from).isTrue();
        }
        // async approval
        assertThat(PreAuthStateMachine.canTransition(UNDER_REVIEW, APPROVAL_IN_PROGRESS)).isTrue();
        assertThat(PreAuthStateMachine.canTransition(APPROVAL_IN_PROGRESS, REJECTED)).isTrue();
    }

    @Test
    void ledgerExitsStillWork() {
        for (PreAuthStatus from : EnumSet.of(APPROVED, PARTIALLY_APPROVED, ACKNOWLEDGED)) {
            assertThat(PreAuthStateMachine.canTransition(from, CANCELLED)).as("%s -> CANCELLED", from).isTrue();
            assertThat(PreAuthStateMachine.canTransition(from, EXPIRED)).as("%s -> EXPIRED", from).isTrue();
            assertThat(PreAuthStateMachine.canTransition(from, USED)).as("%s -> USED", from).isTrue();
        }
        // a PENDING approval holds nothing and may be cancelled
        assertThat(PreAuthStateMachine.canTransition(PENDING, CANCELLED)).isTrue();
        // provider acknowledgement, full approvals only (as the endpoint always was)
        assertThat(PreAuthStateMachine.canTransition(APPROVED, ACKNOWLEDGED)).isTrue();
        assertThat(PreAuthStateMachine.canTransition(PARTIALLY_APPROVED, ACKNOWLEDGED)).isFalse();
    }

    @Test
    void reviewerMayRejectOrReturnForCorrectionBeforeADecisionOnly() {
        for (PreAuthStatus from : EnumSet.of(PENDING, UNDER_REVIEW, APPROVAL_IN_PROGRESS)) {
            assertThat(PreAuthStateMachine.canTransition(from, REJECTED)).as("%s -> REJECTED", from).isTrue();
        }
        assertThat(PreAuthStateMachine.canTransition(UNDER_REVIEW, NEEDS_CORRECTION)).isTrue();
        assertThat(PreAuthStateMachine.canTransition(APPROVED, NEEDS_CORRECTION)).isFalse();
        assertThat(PreAuthStateMachine.canTransition(ACKNOWLEDGED, REJECTED)).isFalse();
    }

    // ── mechanics ──────────────────────────────────────────────────────

    @Test
    void sameStatusIsANoOpNotAnError() {
        PreAuthorization preAuth = at(APPROVAL_IN_PROGRESS);
        PreAuthStateMachine.transition(preAuth, APPROVAL_IN_PROGRESS);
        assertThat(preAuth.getStatus()).isEqualTo(APPROVAL_IN_PROGRESS);
    }

    @Test
    void transitionAppliesTheNewStatusWhenLegal() {
        PreAuthorization preAuth = at(PENDING);
        PreAuthStateMachine.transition(preAuth, UNDER_REVIEW);
        assertThat(preAuth.getStatus()).isEqualTo(UNDER_REVIEW);
    }

    @Test
    void refusalMessageIsArabicAndNamesNoInternals() {
        assertThatThrownBy(() -> PreAuthStateMachine.transition(at(CANCELLED), UNDER_REVIEW))
                .hasMessageContaining("حالة نهائية")
                .hasMessageNotContaining("Exception")
                .hasMessageNotContaining("PreAuthStatus");
    }

    /**
     * The matrix is only a rule if nothing walks around it. Production code
     * may set a PreAuthorization's status in exactly one place -- the
     * machine's own transition(). Builders setting the initial status at
     * creation are not writes to an existing record and are not matched.
     */
    @Test
    void onlyTheStateMachineWritesAPreAuthorizationStatus() throws java.io.IOException {
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java/com/waad/tba");
        java.util.regex.Pattern write = java.util.regex.Pattern.compile(
                "\\.setStatus\\(\\s*(PreAuthorization\\.)?PreAuthStatus\\.|"
                        + "\\bpre[aA]uth\\w*\\.setStatus\\(|\\bfailedPreAuth\\.setStatus\\(");
        java.util.List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.endsWith("PreAuthStateMachine.java")) continue;
                int lineNo = 0;
                for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    lineNo++;
                    String code = line.strip();
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) continue;
                    // A DTO carrying the requested status is not a write to the record.
                    if (code.contains("Dto.setStatus(") || code.contains("dto.setStatus(")) continue;
                    if (write.matcher(code).find()) {
                        offenders.add(root.relativize(file) + ":" + lineNo + "  " + code);
                    }
                }
            }
        }
        assertThat(offenders).as("status writes outside PreAuthStateMachine").isEmpty();
    }

    @Test
    void everyStatusHasARowInTheMatrix() {
        // A status added to the enum without a decision here would be
        // unreachable-from (targetsFrom empty) yet not declared terminal.
        for (PreAuthStatus status : PreAuthStatus.values()) {
            boolean hasExits = !PreAuthStateMachine.targetsFrom(status).isEmpty();
            assertThat(hasExits || PreAuthStateMachine.isTerminal(status))
                    .as("%s must either have exits or be declared terminal", status).isTrue();
        }
    }
}
