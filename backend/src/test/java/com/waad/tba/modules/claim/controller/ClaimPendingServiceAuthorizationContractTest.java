package com.waad.tba.modules.claim.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimPendingServiceAuthorizationContractTest {

    @Test
    void serviceDecisionRequiresClaimApproveCapabilityAndResourceScope() throws Exception {
        Method decision = ClaimPendingServiceController.class.getMethod(
                "decide", Long.class, Long.class,
                com.waad.tba.modules.claim.dto.PendingServiceDecisionRequest.class);

        PreAuthorize rule = decision.getAnnotation(PreAuthorize.class);

        assertThat(rule).isNotNull();
        assertThat(rule.value()).isEqualTo("@claimAccessGuard.canApprove(#claimId)");
    }

    @Test
    void claimFinalApprovalUsesTheSameCapabilityAndResourceGuard() throws Exception {
        Method approval = ClaimController.class.getMethod(
                "approveClaim", Long.class,
                com.waad.tba.modules.claim.api.request.ApproveClaimRequest.class);

        PreAuthorize rule = approval.getAnnotation(PreAuthorize.class);

        assertThat(rule).isNotNull();
        assertThat(rule.value()).isEqualTo("@claimAccessGuard.canApprove(#id)");
    }
}
