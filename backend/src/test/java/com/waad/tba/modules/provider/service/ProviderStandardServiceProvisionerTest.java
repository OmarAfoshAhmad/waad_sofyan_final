package com.waad.tba.modules.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesRequestDto;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesRequestDto.Scope;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesSummaryDto;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.entity.ProviderService;
import com.waad.tba.modules.provider.entity.ProviderServiceDefault;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceDefaultRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;

/**
 * Bulk provisioning across many providers must (a) never write during
 * preview, (b) write exactly the rows apply reports, (c) be safe to run
 * twice -- a second apply over the same scope should report everything
 * already active, not fail or duplicate -- and (d) never touch a provider
 * or service outside the requested scope.
 */
class ProviderStandardServiceProvisionerTest {

    private final ProviderRepository providerRepository = mock(ProviderRepository.class);
    private final ProviderServiceRepository providerServiceRepository = mock(ProviderServiceRepository.class);
    private final ProviderServiceDefaultRepository providerServiceDefaultRepository =
            mock(ProviderServiceDefaultRepository.class);
    private final MedicalServiceRepository medicalServiceRepository = mock(MedicalServiceRepository.class);
    private final MedicalCategoryRepository medicalCategoryRepository = mock(MedicalCategoryRepository.class);

    private final ProviderStandardServiceProvisioner provisioner = new ProviderStandardServiceProvisioner(
            providerRepository, providerServiceRepository, providerServiceDefaultRepository,
            medicalServiceRepository, medicalCategoryRepository);

    private MedicalService standardService;

    @BeforeEach
    void setUp() {
        standardService = MedicalService.builder()
                .id(1L).code("SYS-DRUG-GENERAL").name("test").pricingMode(PricingMode.MANUAL_AMOUNT).build();
        when(medicalServiceRepository.findByCodeIn(anyList())).thenReturn(List.of(standardService));
    }

    private Provider provider(long id, boolean active) {
        return Provider.builder().id(id).name("P" + id).providerType(ProviderType.PHARMACY).active(active).build();
    }

    private ProvisionStandardServicesRequestDto allActiveRequest() {
        return ProvisionStandardServicesRequestDto.builder()
                .serviceCodes(List.of("SYS-DRUG-GENERAL")).scope(Scope.ALL_ACTIVE).build();
    }

    @Test
    void previewNeverWrites() {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(provider(1, true), provider(2, true)));
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of());

        ProvisionStandardServicesSummaryDto summary = provisioner.preview(allActiveRequest());

        assertThat(summary.getProvidersMatched()).isEqualTo(2);
        assertThat(summary.getAssignmentsToCreate()).isEqualTo(2);
        verify(providerServiceRepository, never()).saveAll(any());
        verify(providerServiceRepository, never()).save(any());
    }

    @Test
    void applyCreatesExactlyWhatPreviewReported() {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(provider(1, true), provider(2, true)));
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of());

        ProvisionStandardServicesSummaryDto summary = provisioner.apply(allActiveRequest());

        assertThat(summary.getAssignmentsToCreate()).isEqualTo(2);
        assertThat(summary.getAssignmentsAlreadyActive()).isZero();
        verify(providerServiceRepository, times(1)).saveAll(anyList());
    }

    @Test
    void reactivatesASoftDeletedAssignmentRatherThanFailingOrDuplicating() {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(provider(1, true)));
        ProviderService inactive = ProviderService.builder()
                .id(9L).providerId(1L).serviceCode("SYS-DRUG-GENERAL").active(false).build();
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of(inactive));

        ProvisionStandardServicesSummaryDto summary = provisioner.apply(allActiveRequest());

        assertThat(summary.getAssignmentsToReactivate()).isEqualTo(1);
        assertThat(summary.getAssignmentsToCreate()).isZero();
        assertThat(inactive.getActive()).isTrue();
    }

    @Test
    void aSecondApplyOverTheSameScopeReportsEverythingAlreadyActive() {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(provider(1, true)));
        ProviderService active = ProviderService.builder()
                .id(9L).providerId(1L).serviceCode("SYS-DRUG-GENERAL").active(true).build();
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of(active));

        ProvisionStandardServicesSummaryDto summary = provisioner.apply(allActiveRequest());

        assertThat(summary.getAssignmentsAlreadyActive()).isEqualTo(1);
        assertThat(summary.getAssignmentsToCreate()).isZero();
        assertThat(summary.getAssignmentsToReactivate()).isZero();
        assertThat(summary.getProvidersNeedingChanges()).isZero();
        verify(providerServiceRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsAServiceCodeThatIsNotAStandardManualAmountService() {
        MedicalService contractPriced = MedicalService.builder()
                .id(2L).code("SRV-X").name("x").pricingMode(PricingMode.CONTRACT_PRICE).build();
        when(medicalServiceRepository.findByCodeIn(anyList())).thenReturn(List.of(contractPriced));

        ProvisionStandardServicesRequestDto request = ProvisionStandardServicesRequestDto.builder()
                .serviceCodes(List.of("SRV-X")).scope(Scope.ALL_ACTIVE).build();

        assertThatThrownBy(() -> provisioner.preview(request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsAnUnknownServiceCode() {
        when(medicalServiceRepository.findByCodeIn(anyList())).thenReturn(List.of());

        ProvisionStandardServicesRequestDto request = ProvisionStandardServicesRequestDto.builder()
                .serviceCodes(List.of("NO-SUCH-CODE")).scope(Scope.ALL_ACTIVE).build();

        assertThatThrownBy(() -> provisioner.preview(request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void selectedProvidersScopeOnlyTouchesTheProvidersListed() {
        when(providerRepository.findAllById(List.of(5L))).thenReturn(List.of(provider(5, true)));
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of());

        ProvisionStandardServicesRequestDto request = ProvisionStandardServicesRequestDto.builder()
                .serviceCodes(List.of("SYS-DRUG-GENERAL")).scope(Scope.SELECTED_PROVIDERS)
                .providerIds(List.of(5L)).build();

        ProvisionStandardServicesSummaryDto summary = provisioner.preview(request);

        assertThat(summary.getProvidersMatched()).isEqualTo(1);
    }

    @Test
    void providerTypesScopeRequiresAtLeastOneType() {
        ProvisionStandardServicesRequestDto request = ProvisionStandardServicesRequestDto.builder()
                .serviceCodes(List.of("SYS-DRUG-GENERAL")).scope(Scope.PROVIDER_TYPES)
                .providerTypes(List.of()).build();

        assertThatThrownBy(() -> provisioner.preview(request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void autoApplyCreatesEachDefaultServiceForTheNewProviderInsideTheSameCall() {
        Provider newProvider = provider(42, true);
        when(providerServiceDefaultRepository
                .findByProviderTypeAndActiveTrueAndAutoApplyTrueOrderBySortOrder(ProviderType.PHARMACY))
                .thenReturn(List.of(
                        ProviderServiceDefault.builder().providerType(ProviderType.PHARMACY)
                                .serviceCode("SYS-DRUG-GENERAL").autoApply(true).active(true).sortOrder(1).build(),
                        ProviderServiceDefault.builder().providerType(ProviderType.PHARMACY)
                                .serviceCode("SYS-DRUG-CHRONIC").autoApply(true).active(true).sortOrder(2).build()));

        provisioner.autoApplyOnNewProvider(newProvider);

        verify(providerServiceRepository, times(2)).save(any(ProviderService.class));
    }

    @Test
    void autoApplyDoesNothingWhenNoDefaultsAreConfiguredForTheType() {
        Provider newProvider = provider(43, true);
        when(providerServiceDefaultRepository
                .findByProviderTypeAndActiveTrueAndAutoApplyTrueOrderBySortOrder(ProviderType.PHARMACY))
                .thenReturn(List.of());

        provisioner.autoApplyOnNewProvider(newProvider);

        verify(providerServiceRepository, never()).save(any());
    }
}
