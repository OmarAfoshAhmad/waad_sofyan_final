package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProviderContractTermsService {
    private final ProviderContractTermRepository repository;

    @Transactional
    public ProviderContractTerm createInitial(ProviderContract contract, String actor) {
        return repository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(scale(contract.getDiscountPercent()))
                .discountBeforeRejection(Boolean.TRUE.equals(contract.getDiscountBeforeRejection()))
                .changeReason("Initial contract terms").approvedBy(actor).approvedAt(LocalDateTime.now()).build());
    }

    /**
     * Idempotently guarantees the contract has effective terms matching its own
     * financial fields. Needed by bulk paths that build or mutate a contract
     * directly through the repository (bypassing {@code create()}/{@code update()}):
     * without terms the effective-contract resolver fails closed and no claim can
     * be written for that provider at all, and a contract whose discountPercent
     * was changed without a matching amendment would silently keep charging the
     * old rate because resolution reads terms, not the contract row.
     */
    @Transactional
    public ProviderContractTerm ensureEffectiveTerms(ProviderContract contract, String actor) {
        BigDecimal target = scale(contract.getDiscountPercent());
        boolean timing = Boolean.TRUE.equals(contract.getDiscountBeforeRejection());

        ProviderContractTerm current = repository.findOpenForUpdate(contract.getId()).orElse(null);
        if (current == null) {
            return createInitial(contract, actor);
        }
        if (current.getDiscountPercent().compareTo(target) == 0
                && Boolean.TRUE.equals(current.getDiscountBeforeRejection()) == timing) {
            return current;
        }
        // Never back-date an amendment before the open period starts; for terms that
        // have not taken effect yet this collapses into an in-place correction.
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = today.isBefore(current.getEffectiveFrom())
                ? current.getEffectiveFrom()
                : today;
        return amend(contract, effectiveFrom, target, timing,
                "مزامنة شروط العقد مع بيانات العقد", actor);
    }

    @Transactional
    public ProviderContractTerm amend(ProviderContract contract, LocalDate effectiveFrom,
                                      BigDecimal discountPercent, Boolean beforeRejection,
                                      String reason, String actor) {
        if (effectiveFrom == null) throw new BusinessRuleException("تاريخ سريان تعديل شروط العقد مطلوب");
        ProviderContractTerm current = repository.findOpenForUpdate(contract.getId())
                .orElseThrow(() -> new BusinessRuleException("لا توجد شروط مفتوحة للعقد " + contract.getContractCode()));
        if (effectiveFrom.isBefore(current.getEffectiveFrom())) {
            throw new BusinessRuleException("تاريخ التعديل لا يمكن أن يسبق بداية الشروط الحالية");
        }
        BigDecimal normalized = scale(discountPercent);
        boolean timing = Boolean.TRUE.equals(beforeRejection);
        if (effectiveFrom.equals(current.getEffectiveFrom())) {
            current.setDiscountPercent(normalized);
            current.setDiscountBeforeRejection(timing);
            current.setChangeReason(reason);
            current.setApprovedBy(actor);
            current.setApprovedAt(LocalDateTime.now());
            return repository.save(current);
        }
        current.setEffectiveTo(effectiveFrom);
        repository.saveAndFlush(current);
        return repository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(effectiveFrom).discountPercent(normalized)
                .discountBeforeRejection(timing).changeReason(reason)
                .approvedBy(actor).approvedAt(LocalDateTime.now()).build());
    }

    private BigDecimal scale(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0 || result.compareTo(new BigDecimal("100")) > 0)
            throw new BusinessRuleException("نسبة الخصم يجب أن تكون بين 0 و100");
        return result.setScale(2, RoundingMode.HALF_UP);
    }
}
