package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreAuthAuditScopeArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthorizationAuditController.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationAuditService.java");

    @Test
    void auditControllerUsesCapabilityAndRecordScopeInsteadOfAuthenticationOrRoles() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertThat(source)
                .contains("@PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")")
                .contains("@PreAuthorize(\"@preAuthAccessGuard.canView(#id)\")")
                .doesNotContain("hasRole(")
                .doesNotContain("hasAnyRole(")
                .doesNotContain("isAuthenticated()");
    }

    @Test
    void aggregateAuditReadsCannotReturnToGlobalRepositoryMethods() throws Exception {
        String source = Files.readString(SERVICE);
        assertThat(source)
                .contains("findByChangedByScoped(")
                .contains("findByActionScoped(")
                .contains("findRecentAuditsScoped(")
                .contains("searchScoped(")
                .contains("countScoped(")
                .doesNotContain("findByChangedByOrderByChangeDateDesc(")
                .doesNotContain("findByActionOrderByChangeDateDesc(")
                .doesNotContain("auditRepository.findRecentAudits(")
                .doesNotContain("auditRepository.search(")
                .doesNotContain("auditRepository.count()")
                .doesNotContain("auditRepository.countByAction(");
    }
}
