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
import com.waad.tba.modules.provider.dto.RevokeStandardServicesSummaryDto;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.projection.ProviderServiceClaimUsageProjection;

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
    private final ClaimRepository claimRepository = mock(ClaimRepository.class);

    private final ProviderStandardServiceProvisioner provisioner = new ProviderStandardServiceProvisioner(
            providerRepository, providerServiceRepository, providerServiceDefaultRepository,
            medicalServiceRepository, medicalCategoryRepository, claimRepository);

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

    // ── Revoke: the exact inverse of apply, but never when money already moved ──

    private record UsagePair(Long providerId, String serviceCode) implements ProviderServiceClaimUsageProjection {
        @Override
        public Long getProviderId() { return providerId; }
        @Override
        public String getServiceCode() { return serviceCode; }
    }

    @Test
    void revokePreviewNeverWrites() {
        Provider p1 = provider(1, true);
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(p1));
        ProviderService active = ProviderService.builder()
                .id(9L).providerId(1L).serviceCode("SYS-DRUG-GENERAL").active(true).build();
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of(active));
        when(claimRepository.findProviderServiceCodePairsWithClaimHistory(anyList(), anyList()))
                .thenReturn(List.of());

        RevokeStandardServicesSummaryDto summary = provisioner.previewRevoke(allActiveRequest());

        assertThat(summary.getAssignmentsToRevoke()).isEqualTo(1);
        verify(providerServiceRepository, never()).saveAll(any());
        verify(providerServiceRepository, never()).save(any());
    }

    @Test
    void revokeDeactivatesExactlyWhatPreviewReportedWhenNoClaimHistoryExists() {
        Provider p1 = provider(1, true);
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(p1));
        ProviderService active = ProviderService.builder()
                .id(9L).providerId(1L).serviceCode("SYS-DRUG-GENERAL").active(true).build();
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of(active));
        when(claimRepository.findProviderServiceCodePairsWithClaimHistory(anyList(), anyList()))
                .thenReturn(List.of());

        RevokeStandardServicesSummaryDto summary = provisioner.revoke(allActiveRequest());

        assertThat(summary.getAssignmentsToRevoke()).isEqualTo(1);
        assertThat(summary.getAssignmentsBlockedByClaimHistory()).isZero();
        assertThat(active.getActive()).isFalse();
        verify(providerServiceRepository).saveAll(List.of(active));
    }

    @Test
    void refusesToRevokeAnAssignmentWithClaimHistoryAndNamesExactlyWhichOne() {
        Provider p1 = provider(1, true);
        p1.setName("صيدلية الاختبار");
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(p1));
        ProviderService active = ProviderService.builder()
                .id(9L).providerId(1L).serviceCode("SYS-DRUG-GENERAL").active(true).build();
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of(active));
        when(claimRepository.findProviderServiceCodePairsWithClaimHistory(anyList(), anyList()))
                .thenReturn(List.of(new UsagePair(1L, "SYS-DRUG-GENERAL")));

        RevokeStandardServicesSummaryDto summary = provisioner.revoke(allActiveRequest());

        assertThat(summary.getAssignmentsToRevoke()).isZero();
        assertThat(summary.getAssignmentsBlockedByClaimHistory()).isEqualTo(1);
        assertThat(summary.getBlockedAssignments()).hasSize(1);
        var blocked = summary.getBlockedAssignments().get(0);
        assertThat(blocked.getProviderId()).isEqualTo(1L);
        assertThat(blocked.getProviderName()).isEqualTo("صيدلية الاختبار");
        assertThat(blocked.getServiceCode()).isEqualTo("SYS-DRUG-GENERAL");
        assertThat(blocked.getReason()).contains("أثر مالي");
        assertThat(active.getActive()).isTrue();
        verify(providerServiceRepository, never()).saveAll(any());
    }

    @Test
    void revokeReportsAlreadyInactiveSeparatelyFromRevoked() {
        Provider p1 = provider(1, true);
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(p1));
        ProviderService inactive = ProviderService.builder()
                .id(9L).providerId(1L).serviceCode("SYS-DRUG-GENERAL").active(false).build();
        when(providerServiceRepository.findAllByProviderIdInAndServiceCodeIn(anyList(), anyList()))
                .thenReturn(List.of(inactive));
        when(claimRepository.findProviderServiceCodePairsWithClaimHistory(anyList(), anyList()))
                .thenReturn(List.of());

        RevokeStandardServicesSummaryDto summary = provisioner.revoke(allActiveRequest());

        assertThat(summary.getAssignmentsAlreadyInactive()).isEqualTo(1);
        assertThat(summary.getAssignmentsToRevoke()).isZero();
        verify(providerServiceRepository, never()).saveAll(any());
    }
}
