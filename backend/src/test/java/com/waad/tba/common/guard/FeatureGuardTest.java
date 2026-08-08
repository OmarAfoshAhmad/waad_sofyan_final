package com.waad.tba.common.guard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.waad.tba.common.config.FeatureFlagsConfig;
import com.waad.tba.modules.systemadmin.service.FeatureFlagService;
import com.waad.tba.security.AuthorizationService;

/**
 * Phase 9 — requireProviderPaymentPosting must fail closed for everyone,
 * including internal staff, unlike the provider-portal guards it sits beside.
 * Building the new payment UI must not implicitly activate its write path.
 */
@ExtendWith(MockitoExtension.class)
class FeatureGuardTest {

    @Mock private FeatureFlagsConfig flags;
    @Mock private FeatureFlagService featureFlagService;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks private FeatureGuard guard;

    @Test
    void blocksProviderPaymentPostingWhenFlagDisabled() {
        when(featureFlagService.isFlagEnabled(eq(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING), anyBoolean()))
                .thenReturn(false);

        assertThatThrownBy(guard::requireProviderPaymentPosting)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("مسار دفعات مقدم الخدمة الجديد");
    }

    @Test
    void allowsProviderPaymentPostingWhenFlagEnabled() {
        when(featureFlagService.isFlagEnabled(eq(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING), anyBoolean()))
                .thenReturn(true);

        assertThatCode(guard::requireProviderPaymentPosting).doesNotThrowAnyException();
    }

    @Test
    void unlikeThePortalGuardsInternalStaffAreNotExemptFromThisOne() {
        // No stubbing of authorizationService.getCurrentUser() at all: if the guard
        // consulted staff-bypass logic here, Mockito's strict stubbing would still
        // let it through silently. The real proof is the flag-disabled case above
        // throwing regardless of who is calling — this test documents the intent
        // so a future "isStaff() return" shortcut added to this method is a visible
        // diff, not a silent behavior change.
        when(featureFlagService.isFlagEnabled(eq(FeatureGuard.FLAG_PROVIDER_PAYMENT_POSTING), anyBoolean()))
                .thenReturn(false);

        assertThatThrownBy(guard::requireProviderPaymentPosting)
                .isInstanceOf(ResponseStatusException.class);
    }
}
