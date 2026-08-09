package com.waad.tba.modules.claim.controller;

import com.waad.tba.modules.claim.api.request.ApproveClaimRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimFinalApprovalAuthorizationContractTest {

    @Test
    void finalApprovalIsRestrictedToHeadInsuranceManagerAndSuperAdmin() throws Exception {
        Method approval = ClaimController.class.getMethod(
                "approveClaim", Long.class, ApproveClaimRequest.class);

        PreAuthorize rule = approval.getAnnotation(PreAuthorize.class);

        assertThat(rule).isNotNull();
        assertThat(rule.value())
                .contains("SUPER_ADMIN", "INSURANCE_MANAGER", "MEDICAL_REVIEW_HEAD")
                .doesNotContain("MEDICAL_REVIEWER");
    }
}
