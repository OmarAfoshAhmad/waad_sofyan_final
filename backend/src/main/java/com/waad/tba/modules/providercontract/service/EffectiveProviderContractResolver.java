package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** Single canonical resolver for contract scope and financial terms at service date. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EffectiveProviderContractResolver {
    private final ProviderContractRepository contracts;
    private final ProviderContractTermRepository terms;

    public ResolvedContract resolve(Long providerId, Long employerId, LocalDate serviceDate) {
        if (providerId == null || serviceDate == null)
            throw new BusinessRuleException("مقدم الخدمة وتاريخ الخدمة مطلوبان لاختيار العقد");
        Optional<ProviderContract> employer = employerId == null ? Optional.empty()
                : contracts.findEffectiveEmployerContract(providerId, employerId, serviceDate);
        ProviderContract contract = employer
                .or(() -> contracts.findEffectiveGlobalContract(providerId, serviceDate))
                .orElseThrow(() -> new BusinessRuleException(
                        "لا يوجد عقد ساري لمقدم الخدمة في تاريخ الخدمة " + serviceDate));
        ProviderContractTerm term = terms.findEffective(contract.getId(), serviceDate)
                .orElseThrow(() -> new BusinessRuleException(
                        "لا توجد شروط مالية سارية للعقد " + contract.getContractCode() + " في " + serviceDate));
        return new ResolvedContract(contract, term);
    }

    public record ResolvedContract(ProviderContract contract, ProviderContractTerm terms) {}
}
