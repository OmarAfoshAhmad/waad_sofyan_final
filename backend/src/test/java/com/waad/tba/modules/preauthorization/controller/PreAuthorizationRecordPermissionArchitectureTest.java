package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreAuthorizationRecordPermissionArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthorizationController.java");
    private static final Path REVIEW_CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthReviewController.java");

    @Test
    void recordCommandsUseTheirCapabilityAndOwnershipGuard() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source).contains(
                "@PostMapping\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_CREATE')\")",
                "@PutMapping(\"/{id:\\\\d+}\")\n    @PreAuthorize(\"@preAuthAccessGuard.canCreate(#id)\")",
                "@PutMapping(\"/{id:\\\\d+}/data\")\n    @PreAuthorize(\"@preAuthAccessGuard.canCreate(#id)\")",
                "@PutMapping(\"/{id:\\\\d+}/review\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")",
                "@PostMapping(\"/{id:\\\\d+}/submit\")\n    @PreAuthorize(\"@preAuthAccessGuard.canCreate(#id)\")",
                "@PostMapping(\"/{id:\\\\d+}/approve\")\n    @PreAuthorize(\"@preAuthAccessGuard.canApprove(#id)\")",
                "@PostMapping(\"/{id:\\\\d+}/reject\")\n    @PreAuthorize(\"@preAuthAccessGuard.canApprove(#id)\")",
                "@PostMapping(\"/{id:\\\\d+}/cancel\")\n    @PreAuthorize(\"@preAuthAccessGuard.canCancel(#id)\")",
                "@PostMapping(\"/{id:\\\\d+}/acknowledge\")\n    @PreAuthorize(\"@preAuthAccessGuard.canCreate(#id)\")",
                "@DeleteMapping(\"/{id:\\\\d+}\")\n    @PreAuthorize(\"@preAuthAccessGuard.canDelete(#id)\")",
                "@PostMapping(\"/{id:\\\\d+}/start-review\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")");

        String reviewSource = Files.readString(REVIEW_CONTROLLER).replace("\r\n", "\n");
        assertThat(reviewSource).contains(
                "@PostMapping(\"/{id}/start-review\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")",
                "@PostMapping(\"/{id}/lines/{lineId}/decision\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")",
                "@PostMapping(\"/{id}/finalize\")\n    @PreAuthorize(\"@preAuthAccessGuard.canApprove(#id)\")",
                "@PostMapping(\"/{id}/reject\")\n    @PreAuthorize(\"@preAuthAccessGuard.canApprove(#id)\")",
                "@PostMapping(\"/{id}/request-info\")\n    @PreAuthorize(\"@preAuthAccessGuard.canReview(#id)\")");
    }

    @Test
    void recordReadsAndAttachmentsUseRecordScope() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source).contains(
                "@GetMapping(\"/{id:\\\\d+}\")\n    @PreAuthorize(\"@preAuthAccessGuard.canView(#id)\")",
                "@GetMapping(\"/reference/{referenceNumber}\")\n    @PreAuthorize(\"@preAuthAccessGuard.canViewReference(#referenceNumber)\")",
                "@GetMapping(\"/{id:\\\\d+}/attachments\")\n    @PreAuthorize(\"@preAuthAccessGuard.canView(#id)\")",
                "@GetMapping(\"/{id:\\\\d+}/attachments/{attachmentId}\")\n    @PreAuthorize(\"@preAuthAccessGuard.canView(#id)\")",
                "@PostMapping(value = \"/{id:\\\\d+}/attachments\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)\n    @PreAuthorize(\"@preAuthAccessGuard.canCreate(#id)\")",
                "@DeleteMapping(\"/{id:\\\\d+}/attachments/{attachmentId}\")\n    @PreAuthorize(\"@preAuthAccessGuard.canCreate(#id)\")");
        assertThat(source).contains(
                "@GetMapping(\"/valid\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")",
                "@GetMapping(\"/check-validity\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")");
    }

    @Test
    void noAuthorizationExpressionReferencesAnUndefinedId() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source)
                .doesNotContain("@GetMapping\n    @PreAuthorize(\"@preAuthAccessGuard.canView(#id)\")")
                .doesNotContain("@GetMapping(\"/check-validity\")\n    @PreAuthorize(\"@preAuthAccessGuard.canView(#id)\")");
    }
}
