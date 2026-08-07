package com.waad.tba.modules.providercontract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;

@ExtendWith(MockitoExtension.class)
class ProviderContractTermsServiceTest {
    @Mock ProviderContractTermRepository repository;

    @Test
    void amendmentClosesOldPeriodAndStartsNewHalfOpenPeriod() {
        ProviderContract contract = ProviderContract.builder().id(5L).contractCode("C-5").build();
        ProviderContractTerm old = ProviderContractTerm.builder().id(50L).contract(contract)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .discountPercent(new BigDecimal("10.00")).discountBeforeRejection(true).build();
        when(repository.findOpenForUpdate(5L)).thenReturn(Optional.of(old));
        when(repository.saveAndFlush(old)).thenReturn(old);
        when(repository.save(any(ProviderContractTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProviderContractTermsService service = new ProviderContractTermsService(repository);

        ProviderContractTerm created = service.amend(contract, LocalDate.of(2026, 7, 1),
                new BigDecimal("15"), false, "ملحق تعاقدي", "auditor");

        assertThat(old.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(old.isEffectiveOn(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(old.isEffectiveOn(LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(created.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(created.getEffectiveTo()).isNull();
        assertThat(created.getDiscountPercent()).isEqualByComparingTo("15.00");
        assertThat(created.getDiscountBeforeRejection()).isFalse();
    }
}
