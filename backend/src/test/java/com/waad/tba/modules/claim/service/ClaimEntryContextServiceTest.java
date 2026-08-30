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

@ExtendWith(MockitoExtension.class)
class ClaimEntryContextServiceTest {

    @Mock MemberContextResolver memberContextResolver;
    @Mock EffectiveProviderContractResolver contractResolver;
    @Mock ProviderContractPricingItemService pricingItemService;
    @Mock LimitBalanceReader limitBalanceReader;
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
                .thenReturn(new LimitBalanceReader.GeneralCeilingBalance(
                        new BigDecimal("60000"), new BigDecimal("10000"), new BigDecimal("5000"),
                        new BigDecimal("50000"), new BigDecimal("45000")));

        var result = service.resolve(7L, 8L, 9L, date);

        assertThat(result.policyId()).isEqualTo(31L);
        assertThat(result.policyAssignmentId()).isEqualTo(202L);
        assertThat(result.contractId()).isEqualTo(41L);
        assertThat(result.contractTermsId()).isEqualTo(42L);
        assertThat(result.serviceDate()).isEqualTo(date);
        assertThat(result.actualRemaining()).isEqualByComparingTo("50000");
        assertThat(result.reservableAvailable()).isEqualByComparingTo("45000");
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
        when(pricingItemService.findEffectiveInContract(41L, date, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        service.findEffectiveServices(7L, 8L, 9L, date, pageable);

        verify(pricingItemService).findEffectiveInContract(41L, date, pageable);
    }
}
