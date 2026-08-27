package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreAuthReviewPermissionWiringArchitectureTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthReviewController.java");

    @Test
    void reviewControllerDoesNotAuthorizeByRoleName() throws Exception {
        String source = source();

        assertThat(source)
                .doesNotContain("hasRole(")
                .doesNotContain("hasAnyRole(");
    }

    @Test
    void reviewWorkUsesReviewCapability() throws Exception {
        String source = source();

        assertThat(source).contains(
                "@GetMapping(\"/inbox\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_REVIEW')\")",
                "@PostMapping(\"/{id}/start-review\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")",
                "@GetMapping(\"/{id}/lines\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")",
                "@PostMapping(\"/{id}/lines/{lineId}/decision\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")",
                "@PostMapping(\"/{id}/request-info\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")");
    }

    @Test
    void finalDecisionUsesApproveCapability() throws Exception {
        String source = source();

        assertThat(source).contains(
                "@PostMapping(\"/{id}/finalize\")\n    @PreAuthorize(\"@preAuthAccessGuard.canApprove(#id)\")",
                "@PostMapping(\"/{id}/reject\")\n    @PreAuthorize(\"@preAuthAccessGuard.canApprove(#id)\")");
    }

    private String source() throws Exception {
        return Files.readString(CONTROLLER).replace("\r\n", "\n");
    }
}
