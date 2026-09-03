package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.MemberEmployerAssignment;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.member.service.MemberDatedContext;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.service.EffectiveProviderContractResolver;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;

@ExtendWith(MockitoExtension.class)
class ClaimEntryContextServiceTest {

    @Mock MemberContextResolver memberContextResolver;
    @Mock EffectiveProviderContractResolver contractResolver;
    @Mock ProviderContractPricingItemService pricingItemService;
    @Mock LimitBalanceReader limitBalanceReader;
    @Mock PreAuthorizationRepository preAuthorizationRepository;
    @Mock com.waad.tba.modules.provider.repository.ProviderServiceRepository providerServiceRepository;
    @Mock com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository medicalServiceRepository;
    @Mock com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository medicalCategoryRepository;
    // Real, over a mock repository: this screen only asks the member/employer
    // question, which never reaches the provider network table. A stub here
    // would let the check pass without actually checking.
    @Spy ClaimProviderEmployerAccessService employerAccess = new ClaimProviderEmployerAccessService(
            org.mockito.Mockito.mock(
                    com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository.class));
    @InjectMocks ClaimEntryContextService service;

    @Test
    void policyAndContractComeFromTheSameExplicitServiceDate() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        Employer employer = Employer.builder().id(9L).name("جهة أ").build();
        BenefitPolicy policy = BenefitPolicy.builder().id(31L).policyCode("POL-A")
                .name("وثيقة أ").status(BenefitPolicyStatus.ACTIVE)
                .annualLimit(new BigDecimal("60000"))
                .startDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 12, 31)).build();
        MemberEmployerAssignment employerAssignment = MemberEmployerAssignment.builder().id(101L).build();
        MemberPolicyAssignment policyAssignment = MemberPolicyAssignment.builder().id(202L).build();
        ProviderContract contract = ProviderContract.builder().id(41L).contractCode("CON-A")
                .contractNumber("2025-A").startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31)).build();
        ProviderContractTerm terms = ProviderContractTerm.builder().id(42L).contract(contract).build();

        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(new MemberDatedContext(
                7L, date, employerAssignment, employer, policyAssignment, policy));
        when(contractResolver.resolve(8L, 9L, date))
                .thenReturn(new EffectiveProviderContractResolver.ResolvedContract(contract, terms));
        when(limitBalanceReader.readGeneralCeiling(7L, 31L, new BigDecimal("60000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null))
                // No exceptional uplift for this member, so the effective
                // ceiling equals the policy's own: annualLimit == policyLimit
                // and uplift is zero. The same five figures this asserted
                // before the record split limit into those three components.
                .thenReturn(new LimitBalanceReader.GeneralCeilingBalance(
                        new BigDecimal("60000"), new BigDecimal("60000"), BigDecimal.ZERO,
                        new BigDecimal("10000"), new BigDecimal("5000"),
                        new BigDecimal("50000"), new BigDecimal("45000")));
        when(preAuthorizationRepository.findEligibleForClaim(7L, 8L, date)).thenReturn(java.util.List.of(
                PreAuthorization.builder().id(501L).policyId(31L)
                        .referenceNumber("PA-501").status(PreAuthorization.PreAuthStatus.APPROVED)
                        .serviceName("خدمة معتمدة").expectedServiceDate(date)
                        .expiryDate(date.plusDays(10)).approvedAmount(new BigDecimal("800")).build(),
                PreAuthorization.builder().id(502L).policyId(99L)
                        .referenceNumber("PA-OTHER").status(PreAuthorization.PreAuthStatus.APPROVED)
                        .expectedServiceDate(date).build()));

        var result = service.resolve(7L, 8L, 9L, date);

        assertThat(result.policyId()).isEqualTo(31L);
        assertThat(result.policyAssignmentId()).isEqualTo(202L);
        assertThat(result.contractId()).isEqualTo(41L);
        assertThat(result.contractTermsId()).isEqualTo(42L);
        assertThat(result.serviceDate()).isEqualTo(date);
        assertThat(result.actualRemaining()).isEqualByComparingTo("50000");
        assertThat(result.reservableAvailable()).isEqualByComparingTo("45000");
        assertThat(result.eligiblePreAuthorizations())
                .extracting(com.waad.tba.modules.claim.dto.EligiblePreAuthorizationDto::id)
                .containsExactly(501L);
        verify(contractResolver).resolve(8L, 9L, date);
    }

    @Test
    void memberOutsideTheBatchEmployerIsRejectedBeforeContractResolution() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        Employer actualEmployer = Employer.builder().id(10L).name("جهة أخرى").build();
        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(new MemberDatedContext(
                7L, date,
                MemberEmployerAssignment.builder().id(101L).build(), actualEmployer,
                MemberPolicyAssignment.builder().id(202L).build(),
                BenefitPolicy.builder().id(31L).build()));

        assertThatThrownBy(() -> service.resolve(7L, 8L, 9L, date))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يتبع جهة عمل الدفعة");
    }

    @Test
    void entryServicesUseTheResolvedContractAndTheSameServiceDate() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        Employer employer = Employer.builder().id(9L).name("جهة أ").build();
        ProviderContract contract = ProviderContract.builder().id(41L).contractCode("CON-A")
                .contractNumber("2025-A").startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31)).build();
        ProviderContractTerm terms = ProviderContractTerm.builder().id(42L).contract(contract).build();
        var pageable = PageRequest.of(0, 50);

        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(new MemberDatedContext(
                7L, date,
                MemberEmployerAssignment.builder().id(101L).build(), employer,
                MemberPolicyAssignment.builder().id(202L).build(),
                BenefitPolicy.builder().id(31L).policyCode("POL-A").name("وثيقة أ")
                        .status(BenefitPolicyStatus.ACTIVE).build()));
        when(contractResolver.resolve(8L, 9L, date))
                .thenReturn(new EffectiveProviderContractResolver.ResolvedContract(contract, terms));
        when(pricingItemService.findEffectiveInContract(41L, date, null, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        service.findEffectiveServices(7L, 8L, 9L, date, pageable);

        verify(pricingItemService).findEffectiveInContract(41L, date, null, pageable);
    }

    @Test
    void includesTheProvidersAssignedStandardServicesAlongsideContractPricedItems() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        Employer employer = Employer.builder().id(9L).name("جهة أ").build();
        ProviderContract contract = ProviderContract.builder().id(41L).contractCode("CON-A")
                .contractNumber("2025-A").startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31)).build();
        ProviderContractTerm terms = ProviderContractTerm.builder().id(42L).contract(contract).build();
        var pageable = PageRequest.of(0, 50);

        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(new MemberDatedContext(
                7L, date,
                MemberEmployerAssignment.builder().id(101L).build(), employer,
                MemberPolicyAssignment.builder().id(202L).build(),
                BenefitPolicy.builder().id(31L).policyCode("POL-A").name("وثيقة أ")
                        .status(BenefitPolicyStatus.ACTIVE).build()));
        when(contractResolver.resolve(8L, 9L, date))
                .thenReturn(new EffectiveProviderContractResolver.ResolvedContract(contract, terms));
        when(pricingItemService.findEffectiveInContract(41L, date, null, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        when(providerServiceRepository.findServiceCodesByProviderId(8L))
                .thenReturn(java.util.List.of("SYS-DRUG-GENERAL"));
        var drugCategory = com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory.builder()
                .id(77L).code("CAT-DRUG-GENERAL").name("Drugs").nameAr("أدوية").build();
        when(medicalServiceRepository.findByPricingModeAndActiveTrue(
                com.waad.tba.modules.medicaltaxonomy.enums.PricingMode.MANUAL_AMOUNT))
                .thenReturn(java.util.List.of(
                        com.waad.tba.modules.medicaltaxonomy.entity.MedicalService.builder()
                                .id(501L).code("SYS-DRUG-GENERAL").name("فاتورة أدوية روتينية")
                                .categoryId(77L)
                                .pricingMode(com.waad.tba.modules.medicaltaxonomy.enums.PricingMode.MANUAL_AMOUNT)
                                .build()));
        when(medicalCategoryRepository.findAllById(java.util.Set.of(77L)))
                .thenReturn(java.util.List.of(drugCategory));

        var result = service.findEffectiveServices(7L, 8L, 9L, date, pageable);

        assertThat(result.getContent()).hasSize(1);
        var option = result.getContent().get(0);
        assertThat(option.getMedicalServiceId()).isEqualTo(501L);
        assertThat(option.getPricingMode()).isEqualTo("MANUAL_AMOUNT");
        assertThat(option.getServiceCode()).isEqualTo("SYS-DRUG-GENERAL");
        assertThat(option.getContractPrice()).isNull();
        assertThat(option.getId()).isNull();
    }

    @Test
    void excludesAStandardServiceTheProviderIsNotAssigned() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        Employer employer = Employer.builder().id(9L).name("جهة أ").build();
        ProviderContract contract = ProviderContract.builder().id(41L).contractCode("CON-A")
                .contractNumber("2025-A").startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31)).build();
        ProviderContractTerm terms = ProviderContractTerm.builder().id(42L).contract(contract).build();
        var pageable = PageRequest.of(0, 50);

        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(new MemberDatedContext(
                7L, date,
                MemberEmployerAssignment.builder().id(101L).build(), employer,
                MemberPolicyAssignment.builder().id(202L).build(),
                BenefitPolicy.builder().id(31L).policyCode("POL-A").name("وثيقة أ")
                        .status(BenefitPolicyStatus.ACTIVE).build()));
        when(contractResolver.resolve(8L, 9L, date))
                .thenReturn(new EffectiveProviderContractResolver.ResolvedContract(contract, terms));
        when(pricingItemService.findEffectiveInContract(41L, date, null, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        when(providerServiceRepository.findServiceCodesByProviderId(8L)).thenReturn(java.util.List.of());

        var result = service.findEffectiveServices(7L, 8L, 9L, date, pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
