package com.waad.tba.modules.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.provider.entity.ProviderService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;
import com.waad.tba.modules.provider.service.ProviderServiceService.AssignmentOutcome;

/**
 * assignService() always attempts an INSERT and fails on any existing row --
 * active or soft-deleted -- so a bulk provisioner calling it twice, or
 * calling it after a service was removed and re-offered, would throw.
 * assignOrReactivate() is the idempotent replacement this bulk provisioning
 * feature needs: no write when already active, reactivate the same row when
 * soft-deleted, create only when truly absent.
 */
class ProviderServiceServiceAssignOrReactivateTest {

    private final ProviderServiceRepository providerServiceRepository = mock(ProviderServiceRepository.class);
    private final ProviderServiceService service = new ProviderServiceService(
            providerServiceRepository,
            mock(ProviderRepository.class),
            mock(MedicalServiceRepository.class),
            mock(MedicalCategoryRepository.class));

    @Test
    void reportsAlreadyActiveAndWritesNothingWhenTheAssignmentIsAlreadyActive() {
        ProviderService existing = ProviderService.builder()
                .id(1L).providerId(10L).serviceCode("SYS-DRUG-GENERAL").active(true).build();
        when(providerServiceRepository.findByProviderIdAndServiceCode(10L, "SYS-DRUG-GENERAL"))
                .thenReturn(Optional.of(existing));

        AssignmentOutcome outcome = service.assignOrReactivate(10L, "SYS-DRUG-GENERAL");

        assertThat(outcome).isEqualTo(AssignmentOutcome.ALREADY_ACTIVE);
        verify(providerServiceRepository, never()).save(any());
    }

    @Test
    void reactivatesTheSameRowRatherThanInsertingWhenItWasSoftDeleted() {
        ProviderService existing = ProviderService.builder()
                .id(2L).providerId(10L).serviceCode("SYS-DRUG-GENERAL").active(false).build();
        when(providerServiceRepository.findByProviderIdAndServiceCode(10L, "SYS-DRUG-GENERAL"))
                .thenReturn(Optional.of(existing));

        AssignmentOutcome outcome = service.assignOrReactivate(10L, "SYS-DRUG-GENERAL");

        assertThat(outcome).isEqualTo(AssignmentOutcome.REACTIVATED);
        assertThat(existing.getActive()).isTrue();
        verify(providerServiceRepository).save(existing);
    }

    @Test
    void createsANewAssignmentOnlyWhenNoRowExistsAtAll() {
        when(providerServiceRepository.findByProviderIdAndServiceCode(10L, "SYS-DRUG-GENERAL"))
                .thenReturn(Optional.empty());

        AssignmentOutcome outcome = service.assignOrReactivate(10L, "SYS-DRUG-GENERAL");

        assertThat(outcome).isEqualTo(AssignmentOutcome.CREATED);
        verify(providerServiceRepository).save(any(ProviderService.class));
    }
}
