package com.waad.tba.modules.provider.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProviderImportPermissionWiringArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/waad/tba/modules");

    @Test
    void providerImportUsesEffectiveProviderManagePermissionWithoutRoleFallback() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve(
                "provider/controller/ProviderExcelTemplateController.java"));

        assertThat(source).contains("@permissionGuard.has('PROVIDER_MANAGE')");
        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
    }

    @Test
    void contractImportUsesEffectiveContractManagePermissionOnEveryOperation() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve(
                "providercontract/controller/ProviderContractImportController.java"));

        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
        assertThat(count(source, "@permissionGuard.has('CONTRACT_MANAGE')"))
                .as("class guard plus four explicit endpoint guards")
                .isEqualTo(5);
    }

    @Test
    void priceListImportSeparatesReadFromImportAndKeepsLegacyDirectImportClosed() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve(
                "providercontract/controller/ProviderContractPricingExcelController.java"));

        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
        assertThat(source).contains("@permissionGuard.has('CONTRACT_VIEW')");
        assertThat(source).contains("@permissionGuard.has('PRICE_LIST_IMPORT')");

        int legacyMethod = source.indexOf("public ResponseEntity<ApiResponse<ExcelImportResultDto>> importPriceList(");
        assertThat(legacyMethod).isPositive();
        String legacyPrefix = source.substring(Math.max(0, legacyMethod - 500), legacyMethod);
        assertThat(legacyPrefix).contains("@PreAuthorize(\"denyAll()\")");
    }

    private long count(String source, String token) {
        return source.lines().filter(line -> line.contains(token)).count();
    }
}
