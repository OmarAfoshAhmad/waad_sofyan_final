package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreAuthOperationalReportScopeArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthorizationController.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationService.java");

    @Test
    void operationalListUsesViewCapability() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");

        assertThat(source).contains(
                "@GetMapping\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")");
    }

    @Test
    void operationalListUsesTheSharedAuthorizedScope() throws Exception {
        String source = Files.readString(SERVICE);
        int start = source.indexOf("public Page<PreAuthorizationResponseDto> getOperationalReport(");
        int end = source.indexOf("private String normalizeReportSearch", start);
        String method = source.substring(start, end);

        assertThat(method)
                .contains("preAuthAccessScopeResolver.requireViewScope()")
                .contains("PreAuthAccessScope.Kind.PROVIDERS")
                .contains("PreAuthAccessScope.Kind.EMPLOYERS")
                .contains("PreAuthAccessScope.Kind.GLOBAL")
                .doesNotContain("authorizationService.isProvider")
                .doesNotContain("authorizationService.isReviewer")
                .doesNotContain("providerContextGuard.enforceProviderId")
                .doesNotContain("reviewerIsolationService.getAllowedProviderIds");
    }
}
