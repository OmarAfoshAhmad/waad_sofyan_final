package com.waad.tba.modules.providercontract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;

@ExtendWith(MockitoExtension.class)
class EffectiveProviderContractResolverTest {
    @Mock ProviderContractRepository contracts;
    @Mock ProviderContractTermRepository terms;
    @InjectMocks EffectiveProviderContractResolver resolver;

    @Test
    void employerSpecificContractWinsAndTermsAreSelectedByServiceDate() {
        LocalDate serviceDate = LocalDate.of(2026, 7, 1);
        ProviderContract employerContract = ProviderContract.builder().id(22L).contractCode("EMP-22").build();
        ProviderContractTerm effectiveTerms = ProviderContractTerm.builder().id(220L)
                .contract(employerContract).effectiveFrom(serviceDate)
                .discountPercent(new BigDecimal("15.00")).discountBeforeRejection(false).build();
        when(contracts.findEffectiveEmployerContract(7L, 9L, serviceDate))
                .thenReturn(Optional.of(employerContract));
        when(terms.findEffective(22L, serviceDate)).thenReturn(Optional.of(effectiveTerms));

        var resolved = resolver.resolve(7L, 9L, serviceDate);

        assertThat(resolved.contract()).isSameAs(employerContract);
        assertThat(resolved.terms()).isSameAs(effectiveTerms);
        verify(contracts, never()).findEffectiveGlobalContract(7L, serviceDate);
    }

    @Test
    void fallsBackToGlobalContractForTheSameServiceDate() {
        LocalDate serviceDate = LocalDate.of(2026, 6, 30);
        ProviderContract global = ProviderContract.builder().id(11L).contractCode("GLOBAL-11").build();
        ProviderContractTerm oldTerms = ProviderContractTerm.builder().id(110L)
                .contract(global).effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 7, 1))
                .discountPercent(new BigDecimal("10.00")).discountBeforeRejection(true).build();
        when(contracts.findEffectiveEmployerContract(7L, 9L, serviceDate)).thenReturn(Optional.empty());
        when(contracts.findEffectiveGlobalContract(7L, serviceDate)).thenReturn(Optional.of(global));
        when(terms.findEffective(11L, serviceDate)).thenReturn(Optional.of(oldTerms));

        var resolved = resolver.resolve(7L, 9L, serviceDate);

        assertThat(resolved.terms().getDiscountPercent()).isEqualByComparingTo("10.00");
        verify(terms).findEffective(11L, serviceDate);
    }
}
