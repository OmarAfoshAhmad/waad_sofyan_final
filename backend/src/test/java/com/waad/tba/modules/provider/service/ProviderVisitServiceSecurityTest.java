package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.provider.dto.ProviderVisitResponse;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for SECTION_02 CRITICAL finding #3: the Provider Portal
 * "get visit by id" and "get visit context" endpoints previously performed no
 * ownership check, letting any authenticated PROVIDER_STAFF user view another
 * provider's visit (member identity + clinical data) by guessing/incrementing
 * the visit id. The fix threads the caller's ProviderContextGuard.getProviderFilter()
 * result through to the service, which now rejects a visit whose providerId
 * does not match.
 */
@ExtendWith(MockitoExtension.class)
class ProviderVisitServiceSecurityTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderVisitService service;

    @BeforeEach
    void setUp() {
        Visit visit = Visit.builder().id(500L).providerId(251L).build();
        when(visitRepository.findById(500L)).thenReturn(Optional.of(visit));
        lenient().when(providerRepository.findById(251L))
                .thenReturn(Optional.of(Provider.builder().id(251L).build()));
    }

    @Test
    void deniesAccessWhenVisitBelongsToAnotherProvider() {
        // Caller's own provider is 999; the visit belongs to provider 251.
        ProviderVisitResponse response = service.getVisitById(500L, 999L);

        assertThat(response.getSuccess()).isFalse();
    }

    @Test
    void allowsAccessWhenVisitBelongsToCallersOwnProvider() {
        ProviderVisitResponse response = service.getVisitById(500L, 251L);

        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    void internalStaffWithNullProviderFilterSeesAnyProvidersVisit() {
        // SUPER_ADMIN/MEDICAL_REVIEWER: ProviderContextGuard.getProviderFilter() returns null
        ProviderVisitResponse response = service.getVisitById(500L, null);

        assertThat(response.getSuccess()).isTrue();
    }
}
