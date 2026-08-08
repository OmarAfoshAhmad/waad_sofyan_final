package com.waad.tba.modules.settlement.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.waad.tba.common.guard.FeatureGuard;
import com.waad.tba.modules.settlement.dto.AdjustProviderAccountRequest;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.service.ProviderAccountAdjustmentService;
import com.waad.tba.modules.settlement.service.ProviderAccountReconciliationService;
import com.waad.tba.security.AuthorizationService;

/** Mirrors ProviderPaymentControllerGateTest: reconcile never gates, adjust always does. */
@ExtendWith(MockitoExtension.class)
class ProviderAccountReconciliationControllerGateTest {

    @Mock private ProviderAccountReconciliationService reconciliation;
    @Mock private ProviderAccountAdjustmentService adjustment;
    @Mock private AuthorizationService authorizationService;
    @Mock private FeatureGuard featureGuard;

    @InjectMocks private ProviderAccountReconciliationController controller;

    @Test
    void reconcileReadNeverConsultsTheGate() {
        when(reconciliation.reconcile(anyLong())).thenReturn(ProviderReconciliationDto.builder().build());

        controller.reconcile(1L);

        verifyNoInteractions(featureGuard);
    }

    @Test
    void discrepanciesReadNeverConsultsTheGate() {
        when(reconciliation.findDiscrepancies()).thenReturn(java.util.List.of());

        controller.discrepancies();

        verifyNoInteractions(featureGuard);
    }

    @Test
    void adjustIsBlockedWhenGateThrows() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "blocked"))
                .when(featureGuard).requireProviderPaymentPosting();

        assertThatThrownBy(() -> controller.adjust(1L, new AdjustProviderAccountRequest()))
                .isInstanceOf(ResponseStatusException.class);

        verify(adjustment, never()).alignPaidTotalWithLedger(anyLong(), any(), anyLong(), any(), anyLong());
    }
}
