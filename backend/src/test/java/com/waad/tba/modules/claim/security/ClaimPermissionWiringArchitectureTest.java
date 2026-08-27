package com.waad.tba.modules.claim.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ClaimPermissionWiringArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java");
    private static final Path ATTACHMENTS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimAttachmentController.java");
    private static final Path DRAFTS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimDraftController.java");

    @Test
    void resourceMutationsUseClaimAccessGuardInsteadOfRoles() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source).contains("@claimAccessGuard.canCreateFromVisit(#apiRequest.visitId)")
                .contains("@claimAccessGuard.canEdit(#id)")
                .contains("@claimAccessGuard.canReview(#id)")
                .contains("@claimAccessGuard.canApprove(#id)")
                .contains("@claimAccessGuard.canReverse(#id)")
                .contains("@claimAccessGuard.canHardDelete(#id)");
    }

    @Test
    void deprecatedGenericUpdateIsDeniedBeforeExecution() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source).contains("@PutMapping(\"/{id:\\\\d+}\")\n    @PreAuthorize(\"denyAll()\")");
    }

    @Test
    void controllerContainsNoRoleBasedFallbacks() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
    }

    @Test
    void attachmentsAreBoundToClaimScopeAndDraftsRequireCreateCapability() throws Exception {
        String attachments = Files.readString(ATTACHMENTS);
        String drafts = Files.readString(DRAFTS);
        assertThat(attachments)
                .contains("@claimAccessGuard.canRead(#claimId)")
                .contains("@claimAccessGuard.canEdit(#claimId)")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
        assertThat(drafts)
                .contains("@permissionGuard.has('CLAIM_CREATE')")
                .doesNotContain("isAuthenticated()");
    }
}
