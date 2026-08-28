package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreAuthDashboardScopeArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthDashboardController.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/waad/tba/modules/preauthorization/service/PreAuthDashboardService.java");

    @Test
    void dashboardUsesCapabilityInsteadOfRoleNames() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertThat(source)
                .contains("@PreAuthorize(\"@permissionGuard.has('PREAUTH_VIEW')\")")
                .doesNotContain("hasRole(")
                .doesNotContain("hasAnyRole(")
                .doesNotContain("isAuthenticated()");
    }

    @Test
    void everyDashboardReadRequiresAnAuthorizedScope() throws Exception {
        String source = Files.readString(SERVICE);

        assertThat(source)
                .contains("accessScopeResolver.requireViewScope()")
                .contains("getActiveSummaryScoped(")
                .contains("countByStatusScoped(")
                .contains("sumAmountsByStatusScoped(")
                .contains("findHighPriorityPendingScoped(")
                .contains("findPreAuthsExpiringWithinDaysScoped(")
                .contains("findActiveFromDateScoped(")
                .contains("getActiveProviderStatsScoped(")
                .contains("findRecentAuditsScoped(")
                .doesNotContain("preAuthRepository.getActiveSummary()")
                .doesNotContain("preAuthRepository.countByStatus()")
                .doesNotContain("preAuthRepository.sumAmountsByStatus()")
                .doesNotContain("preAuthRepository.findHighPriorityPending()")
                .doesNotContain("preAuthRepository.findPreAuthsExpiringWithinDays(")
                .doesNotContain("preAuthRepository.findActiveFromDate(")
                .doesNotContain("preAuthRepository.getActiveProviderStats()")
                .doesNotContain("auditRepository.findRecentAudits(");
    }
}
