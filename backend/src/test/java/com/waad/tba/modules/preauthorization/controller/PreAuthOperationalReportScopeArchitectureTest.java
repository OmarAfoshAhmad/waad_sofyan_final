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
                "@GetMapping\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")",
                "@GetMapping(\"/member/{memberId}\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")",
                "@GetMapping(\"/status/{status}\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")",
                "@GetMapping(\"/search\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")",
                "@GetMapping(\"/inbox/pending\")\n    @PreAuthorize(\"@permissionGuard.has('PREAUTH_REVIEW')\")");
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

    @Test
    void statusAndSearchCannotBypassTheAuthorizedScope() throws Exception {
        String source = Files.readString(SERVICE);

        assertThat(source)
                .contains("return getOperationalReport(status, null, null, null, null, null, pageable)")
                .contains("preAuthorizationRepository.searchScoped(")
                .doesNotContain("preAuthorizationRepository.search(query, pageable)")
                .doesNotContain("preAuthorizationRepository.findByStatusAndActiveTrue(status, pageable)");
    }
}
