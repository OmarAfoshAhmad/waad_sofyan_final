package com.waad.tba.modules.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class SettlementPermissionWiringArchitectureTest {

    private static final Path CONTROLLERS = Path.of(
            "src/main/java/com/waad/tba/modules/settlement/controller");

    @Test
    void financialAccountAndPaymentControllersUseCapabilitiesWithoutRoleFallback() throws Exception {
        for (String file : List.of(
                "PaymentController.java",
                "ProviderPaymentController.java",
                "ProviderAccountController.java",
                "ProviderAccountReconciliationController.java")) {
            String source = Files.readString(CONTROLLERS.resolve(file));
            assertThat(source).as(file).doesNotContain("hasRole(", "hasAnyRole(");
            assertThat(source).as(file).contains("@permissionGuard.has('SETTLEMENT_VIEW')");
        }
    }

    @Test
    void everyFinancialMutationRequiresSettlementManage() throws Exception {
        for (String file : List.of(
                "PaymentController.java",
                "ProviderPaymentController.java",
                "ProviderAccountController.java",
                "ProviderAccountReconciliationController.java")) {
            String source = Files.readString(CONTROLLERS.resolve(file));
            assertThat(source).as(file).contains("@permissionGuard.has('SETTLEMENT_MANAGE')");
        }
    }

    @Test
    void balanceRepairAlsoRequiresDangerZoneCapability() throws Exception {
        String source = Files.readString(CONTROLLERS.resolve("ProviderAccountController.java"));
        assertThat(source.lines().filter(line -> line.contains(
                "@permissionGuard.has('SETTLEMENT_MANAGE') and @permissionGuard.has('DANGER_ZONE_EXECUTE')")))
                .hasSize(2);
    }
}
