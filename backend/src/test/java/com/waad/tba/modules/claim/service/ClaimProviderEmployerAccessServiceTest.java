package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The two employer questions, kept apart.
 *
 * <p>They were being confused: claim entry asked the temporal one twice with
 * two different messages, and the network one lived inline inside claim
 * creation. Answering them in one place is only safe if they keep answering
 * differently, which is what these tests hold in place.
 */
@ExtendWith(MockitoExtension.class)
class ClaimProviderEmployerAccessServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 9, 1);

    @Mock private ProviderAllowedEmployerRepository allowedEmployers;
    @InjectMocks private ClaimProviderEmployerAccessService access;

    // ── the member's employer on the service date ────────────────────────────

    @Test
    void acceptsAMemberWhoBelongsToTheBatchEmployer() {
        assertThatCode(() -> access.requireMemberBelongsToEmployer(5L, employer(5L), SERVICE_DATE))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAMemberWhoBelongsToAnotherEmployerOnThatDate() {
        assertThatThrownBy(() -> access.requireMemberBelongsToEmployer(5L, employer(9L), SERVICE_DATE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2026-09-01");
    }

    /**
     * A missing employer is a refusal, not a pass. Reading "no employer" as
     * "no objection" is how a fail-open slips into a tenant boundary.
     */
    @Test
    void refusesWhenEitherSideIsMissing() {
        assertThatThrownBy(() -> access.requireMemberBelongsToEmployer(null, employer(5L), SERVICE_DATE))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> access.requireMemberBelongsToEmployer(5L, null, SERVICE_DATE))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── the provider's network ───────────────────────────────────────────────

    @Test
    void acceptsAGlobalProviderWithoutConsultingTheNetworkTable() {
        Provider global = provider(3L, true);

        assertThatCode(() -> access.requireProviderServesEmployer(global, employer(5L), 7L))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAProviderHoldingAnActiveLinkToTheEmployer() {
        when(allowedEmployers.hasActiveAccessToEmployer(3L, 5L)).thenReturn(true);

        assertThatCode(() -> access.requireProviderServesEmployer(provider(3L, false), employer(5L), 7L))
                .doesNotThrowAnyException();
    }

    /**
     * 403 rather than a business error: a provider reaching an employer it has
     * no relationship with is crossing a tenant boundary, not mistyping a form.
     */
    @Test
    void refusesAProviderOutsideTheEmployerNetworkWithForbidden() {
        when(allowedEmployers.hasActiveAccessToEmployer(3L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> access.requireProviderServesEmployer(provider(3L, false), employer(5L), 7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("جهة الاختبار");
    }

    private Employer employer(Long id) {
        Employer employer = new Employer();
        employer.setId(id);
        employer.setName("جهة الاختبار");
        return employer;
    }

    private Provider provider(Long id, boolean global) {
        Provider provider = new Provider();
        provider.setId(id);
        provider.setAllowAllEmployers(global);
        return provider;
    }
}
