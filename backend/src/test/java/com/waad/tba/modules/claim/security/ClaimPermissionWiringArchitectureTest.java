package com.waad.tba.modules.claim.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ClaimPermissionWiringArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java");

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
}
