package com.waad.tba.modules.claim.controller;

import com.waad.tba.modules.claim.dto.ClaimBatchResponse;
import com.waad.tba.modules.claim.entity.ClaimBatch;
import com.waad.tba.modules.claim.service.ClaimBatchService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.common.guard.FeatureGuard;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for SECTION_02 CRITICAL finding #2: ClaimBatchController
 * previously trusted caller-supplied providerId/employerId with no ownership
 * check, letting any authorized role open/read another provider's or
 * employer's batches. The fix resolves the effective scope server-side via
 * AuthorizationService.resolveProviderScope/resolveEmployerScope — the same
 * pattern already proven for beneficiary search — before touching the service.
 */
@ExtendWith(MockitoExtension.class)
class ClaimBatchControllerSecurityTest {

    @Mock
    private ClaimBatchService claimBatchService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private FeatureGuard featureGuard;

    @InjectMocks
    private ClaimBatchController controller;

    private User providerStaffUser;

    @BeforeEach
    void setUp() {
        providerStaffUser = User.builder().id(9L).username("provider-user")
                .userType("PROVIDER_STAFF").providerId(251L).build();
        when(authorizationService.getCurrentUser()).thenReturn(providerStaffUser);
    }

    @Test
    void getCurrentBatchIgnoresRequestedProviderIdForProviderStaffUser() {
        // Attacker-controlled providerId=999 must be overridden to the caller's own 251
        when(authorizationService.resolveProviderScope(providerStaffUser, 999L)).thenReturn(251L);
        when(authorizationService.resolveEmployerScope(providerStaffUser, 10L)).thenReturn(10L);
        when(claimBatchService.getExistingBatch(251L, 10L, 2026, 7)).thenReturn(null);

        ResponseEntity<ClaimBatchResponse> response = controller.getCurrentBatch(999L, 10L, 2026, 7);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        // The service must never be called with the attacker-supplied providerId
        verify(featureGuard).requireBatchClaims();
        verify(claimBatchService).getExistingBatch(251L, 10L, 2026, 7);
    }

    @Test
    void openOrGetBatchResolvesScopeBeforeCreating() {
        when(authorizationService.resolveProviderScope(providerStaffUser, 999L)).thenReturn(251L);
        when(authorizationService.resolveEmployerScope(providerStaffUser, 10L)).thenReturn(10L);
        when(claimBatchService.getExistingBatch(251L, 10L, 2026, 7)).thenReturn(null);
        ClaimBatch created = ClaimBatch.builder().id(1L).providerId(251L).employerId(10L)
                .batchYear(2026).batchMonth(7).build();
        when(claimBatchService.createBatch(251L, 10L, 2026, 7)).thenReturn(created);

        controller.openOrGetBatch(999L, 10L, 2026, 7);

        verify(featureGuard).requireBatchClaims();
        verify(claimBatchService).createBatch(251L, 10L, 2026, 7);
    }

    @Test
    void searchBatchesResolvesScopeForBothFilters() {
        ArgumentCaptor<Long> providerCaptor = ArgumentCaptor.forClass(Long.class);
        when(authorizationService.resolveProviderScope(providerStaffUser, null)).thenReturn(251L);
        when(authorizationService.resolveEmployerScope(providerStaffUser, null)).thenReturn(null);
        when(claimBatchService.findBatches(providerCaptor.capture(), org.mockito.ArgumentMatchers.isNull(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());

        controller.getBatches(null, null, 2026, 7);

        verify(featureGuard).requireBatchClaims();
        assertThat(providerCaptor.getValue())
                .as("a PROVIDER_STAFF caller must never be able to list ALL providers' batches")
                .isEqualTo(251L);
    }
}
