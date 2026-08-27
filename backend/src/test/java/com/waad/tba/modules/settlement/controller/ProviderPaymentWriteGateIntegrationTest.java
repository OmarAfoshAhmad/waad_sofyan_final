package com.waad.tba.modules.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.guard.FeatureGuard;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.modules.settlement.dto.AdjustProviderAccountRequest;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.PostProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.ReverseProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.ProviderPaymentDto;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.systemadmin.service.FeatureFlagService;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the real V143 seed + FeatureGuard wiring end to end — not just the
 * mocked gate checks in ProviderPaymentControllerGateTest. Confirms the flag
 * is genuinely OFF by default straight from the database, and that toggling it
 * through the same service the admin UI uses genuinely flips the endpoint.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderPaymentWriteGateIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ProviderPaymentController controller;
    @Autowired ProviderAccountReconciliationController reconciliationController;
    @Autowired FeatureFlagService featureFlagService;
    @Autowired ProviderRepository providers;
    @MockitoBean PermissionGuard permissionGuard;

    @BeforeEach
    void allowSettlementCapabilitiesUnlessATestExplicitlyRevokesWrite() {
        when(permissionGuard.has("SETTLEMENT_VIEW")).thenReturn(true);
        when(permissionGuard.has("SETTLEMENT_MANAGE")).thenReturn(true);
    }

    @AfterEach
    void resetFlag() {
        featureFlagService.toggleFeatureFlag(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING, false, "test-cleanup");
    }

    @Test
    void v143SeedsTheFlagDisabledByDefault() {
        assertThat(featureFlagService.isFlagEnabled(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING, true)).isFalse();
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTLEMENT_MANAGE")
    void createDraftFailsClosedByDefault() {
        var request = new CreateProviderPaymentRequest();
        request.setProviderId(1L);
        request.setAmount(new BigDecimal("50.00"));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

        assertThatThrownBy(() -> controller.createDraft(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTLEMENT_MANAGE")
    void createDraftSucceedsOnceTheFlagIsExplicitlyEnabled() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long providerId = providers.save(Provider.builder().name("Gate Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("GATE-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();

        featureFlagService.toggleFeatureFlag(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING, true, "test");

        var request = new CreateProviderPaymentRequest();
        request.setProviderId(providerId);
        request.setAmount(new BigDecimal("50.00"));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

        assertThatCode(() -> controller.createDraft(request)).doesNotThrowAnyException();
    }

    // ── تعطيل العلم أثناء جلسة مفتوحة ────────────────────────────────────────

    /**
     * A draft created while the flag was ON must not become postable just
     * because the client's screen was opened before an admin turned the flag
     * back off — the gate must be re-checked at every write, not just at the
     * moment a session began.
     */
    @Test
    @WithMockUser(authorities = "PERM_SETTLEMENT_MANAGE")
    void aDraftOpenedWhileEnabledCannotBePostedAfterTheFlagIsDisabledMidSession() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long providerId = providers.save(Provider.builder().name("MidSession Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("MID-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();

        featureFlagService.toggleFeatureFlag(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING, true, "test");

        var createRequest = new CreateProviderPaymentRequest();
        createRequest.setProviderId(providerId);
        createRequest.setAmount(new BigDecimal("50.00"));
        createRequest.setPaymentDate(LocalDate.now());
        createRequest.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        ProviderPaymentDto draft = controller.createDraft(createRequest).getBody().getData();
        assertThat(draft).isNotNull();

        // The flag flips off mid-session, as if an admin disabled it while this
        // draft's tab stayed open.
        featureFlagService.toggleFeatureFlag(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING, false, "test");

        var postRequest = new PostProviderPaymentRequest();
        postRequest.setExpectedPaymentVersion(draft.getVersion());
        postRequest.setExpectedAccountVersion(0L);

        assertThatThrownBy(() -> controller.post(draft.getId(), postRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    // ── صلاحيات المستخدمين المختلفة ──────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_SETTLEMENT_VIEW")
    void aUserWithViewButWithoutManageCapabilityIsDeniedOnEveryWriteEndpoint() {
        when(permissionGuard.has("SETTLEMENT_MANAGE")).thenReturn(false);
        featureFlagService.toggleFeatureFlag(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING, true, "test");

        var createRequest = new CreateProviderPaymentRequest();
        createRequest.setProviderId(1L);
        createRequest.setAmount(new BigDecimal("10.00"));
        createRequest.setPaymentDate(LocalDate.now());
        createRequest.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        assertThatThrownBy(() -> controller.createDraft(createRequest))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> controller.post(1L, new PostProviderPaymentRequest()))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> controller.reverse(1L, new ReverseProviderPaymentRequest()))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> reconciliationController.adjust(1L, new AdjustProviderAccountRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTLEMENT_VIEW")
    void aUserWithViewButWithoutManageCapabilityCanStillReadEverything() {
        when(permissionGuard.has("SETTLEMENT_MANAGE")).thenReturn(false);
        assertThatCode(() -> controller.listByProvider(1L)).doesNotThrowAnyException();
        assertThatCode(() -> reconciliationController.reconcileAll()).doesNotThrowAnyException();
    }
}
