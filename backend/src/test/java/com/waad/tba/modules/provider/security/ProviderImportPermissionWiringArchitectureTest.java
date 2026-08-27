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
    void providerApiSeparatesReadManageAndDangerZoneWithoutRoleFallback() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve(
                "provider/controller/ProviderController.java"));

        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
        assertThat(source).contains("@permissionGuard.has('PROVIDER_VIEW')");
        assertThat(source).contains("@permissionGuard.has('PROVIDER_MANAGE')");
        assertThat(count(source,
                "@permissionGuard.has('PROVIDER_MANAGE') and @permissionGuard.has('DANGER_ZONE_EXECUTE')"))
                .as("single and bulk permanent deletion require the danger-zone capability")
                .isEqualTo(2);
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
    void contractApiUsesCapabilityAndResourceGuardsWithoutRoleFallback() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve(
                "providercontract/controller/ProviderContractController.java"));

        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
        assertThat(source).contains("@providerContractAccessGuard.canReadGlobal()");
        assertThat(source).contains("@providerContractAccessGuard.canReadProvider(#providerId)");
        assertThat(source).contains("@providerContractAccessGuard.canReadContract(#id)");
        assertThat(source).contains("@providerContractAccessGuard.canReadPricingItem(#pricingId)");
        assertThat(source).contains("@providerContractAccessGuard.canManageProvider(#dto.providerId)");
        assertThat(source).contains("@providerContractAccessGuard.canManageContract(#id)");
        assertThat(source).contains("@providerContractAccessGuard.canManagePricingItem(#pricingId)");
        assertThat(source).contains("@permissionGuard.has('DANGER_ZONE_EXECUTE')");
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
