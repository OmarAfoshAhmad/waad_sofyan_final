package com.waad.tba.modules.settlement.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.waad.tba.common.guard.FeatureGuard;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.PaymentAllocationSuggestionDto;
import com.waad.tba.modules.settlement.dto.PostProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.ReverseProviderPaymentRequest;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.service.ProviderPaymentAllocationSuggestionService;
import com.waad.tba.modules.settlement.service.ProviderPaymentDraftService;
import com.waad.tba.modules.settlement.service.ProviderPaymentPostingService;
import com.waad.tba.modules.settlement.service.ProviderPaymentReversalService;
import com.waad.tba.security.AuthorizationService;

/**
 * Every write endpoint must check FeatureGuard.requireProviderPaymentPosting()
 * BEFORE touching the underlying service — read endpoints must never touch it
 * at all. Verified here at the unit level (guard mocked) rather than only via
 * the integration suite, so a future refactor that reorders the check-then-act
 * sequence, or that adds a read endpoint which accidentally calls the gate, is
 * caught immediately.
 */
@ExtendWith(MockitoExtension.class)
class ProviderPaymentControllerGateTest {

    @Mock private ProviderPaymentAllocationSuggestionService suggestionService;
    @Mock private ProviderPaymentDraftService draftService;
    @Mock private ProviderPaymentPostingService postingService;
    @Mock private ProviderPaymentReversalService reversalService;
    @Mock private ProviderPaymentRepository payments;
    @Mock private AuthorizationService authorizationService;
    @Mock private FeatureGuard featureGuard;

    @InjectMocks private ProviderPaymentController controller;

    @Test
    void suggestionReadNeverConsultsTheGate() {
        when(suggestionService.suggest(anyLong(), any(), any()))
                .thenReturn(PaymentAllocationSuggestionDto.builder().providerId(1L).build());

        controller.suggest(1L, new BigDecimal("100.00"), LocalDate.now());

        verifyNoInteractions(featureGuard);
    }

    @Test
    void createDraftIsBlockedWhenGateThrows() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "blocked"))
                .when(featureGuard).requireProviderPaymentPosting();

        assertThatThrownBy(() -> controller.createDraft(new CreateProviderPaymentRequest()))
                .isInstanceOf(ResponseStatusException.class);

        verify(draftService, never()).createDraft(any(), any());
    }

    @Test
    void createDraftProceedsWhenGateAllows() {
        when(authorizationService.getCurrentUser())
                .thenReturn(User.builder().id(1L).username("accountant1").build());
        var request = new CreateProviderPaymentRequest();
        var draft = ProviderPayment.builder().id(1L).providerId(1L)
                .amount(new BigDecimal("100.00")).status(ProviderPayment.Status.DRAFT).build();
        when(draftService.createDraft(eq(request), eq("accountant1"))).thenReturn(draft);

        assertThatCode(() -> controller.createDraft(request)).doesNotThrowAnyException();

        verify(featureGuard, times(1)).requireProviderPaymentPosting();
    }

    @Test
    void postIsBlockedWhenGateThrows() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "blocked"))
                .when(featureGuard).requireProviderPaymentPosting();

        assertThatThrownBy(() -> controller.post(1L, new PostProviderPaymentRequest()))
                .isInstanceOf(ResponseStatusException.class);

        verify(postingService, never()).post(any(), any(), any(), any(), any(), any());
        verify(payments, never()).findById(any());
    }

    @Test
    void reverseIsBlockedWhenGateThrows() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "blocked"))
                .when(featureGuard).requireProviderPaymentPosting();

        assertThatThrownBy(() -> controller.reverse(1L, new ReverseProviderPaymentRequest()))
                .isInstanceOf(ResponseStatusException.class);

        verify(reversalService, never()).reverse(any(), any(), any(), any(), any(), any());
    }
}
