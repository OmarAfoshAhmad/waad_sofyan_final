package com.waad.tba.modules.claim.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.dto.ClaimEntryContextDto;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.providercontract.service.EffectiveProviderContractResolver;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemResponseDto;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.claim.dto.EligiblePreAuthorizationDto;
import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * One dated source for the policy and provider contract displayed by claim
 * entry. The UI must never combine today's employer policy with a historical
 * service date or a provider-wide contract lookup.
 */
@Service
@RequiredArgsConstructor
public class ClaimEntryContextService {

    private final MemberContextResolver memberContextResolver;
    private final EffectiveProviderContractResolver contractResolver;
    private final ProviderContractPricingItemService pricingItemService;
    private final LimitBalanceReader limitBalanceReader;
    private final PreAuthorizationRepository preAuthorizationRepository;

    @Transactional(readOnly = true)
    public ClaimEntryContextDto resolve(Long memberId, Long providerId,
            Long requestedEmployerId, LocalDate serviceDate) {
        var memberContext = memberContextResolver.resolveForOrFail(memberId, serviceDate);

        if (requestedEmployerId == null
                || !requestedEmployerId.equals(memberContext.employer().getId())) {
            throw new BusinessRuleException(
                    "المستفيد لا يتبع جهة عمل الدفعة في تاريخ الخدمة " + serviceDate);
        }

        var resolvedContract = contractResolver.resolve(
                providerId, memberContext.employer().getId(), serviceDate);
        var policy = memberContext.policy();
        var contract = resolvedContract.contract();
        var annualLimit = policy.getAnnualLimit();
        var ceiling = limitBalanceReader.readGeneralCeiling(
                memberId, policy.getId(), annualLimit,
                LocalDate.of(serviceDate.getYear(), 1, 1),
                LocalDate.of(serviceDate.getYear(), 12, 31), null);
        String ceilingMode = annualLimit == null || annualLimit.signum() <= 0
                ? "UNLIMITED" : "LIMITED";

        return new ClaimEntryContextDto(
                memberContext.memberId(),
                serviceDate,
                memberContext.employerAssignment().getId(),
                memberContext.employer().getId(),
                memberContext.employer().getName(),
                memberContext.policyAssignment().getId(),
                policy.getId(),
                policy.getPolicyCode(),
                policy.getName(),
                policy.getStatus() == null ? null : policy.getStatus().name(),
                policy.getStartDate(),
                policy.getEndDate(),
                contract.getId(),
                resolvedContract.terms().getId(),
                contract.getContractCode(),
                contract.getContractNumber(),
                contract.getStartDate(),
                contract.getEndDate(),
                ceilingMode,
                ceiling == null ? null : ceiling.limit(),
                ceiling == null ? null : ceiling.committed(),
                ceiling == null ? null : ceiling.reserved(),
                ceiling == null ? null : ceiling.actualRemaining(),
                ceiling == null ? null : ceiling.reservableAvailable(),
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Page<ProviderContractPricingItemResponseDto> findEffectiveServices(
            Long memberId, Long providerId, Long requestedEmployerId,
            LocalDate serviceDate, Pageable pageable) {
        ClaimEntryContextDto context = resolve(memberId, providerId, requestedEmployerId, serviceDate);
        return pricingItemService.findEffectiveInContract(context.contractId(), serviceDate, pageable);
    }

    @Transactional(readOnly = true)
    public List<EligiblePreAuthorizationDto> findEligiblePreAuthorizations(
            Long memberId, Long providerId, Long requestedEmployerId, LocalDate serviceDate) {
        ClaimEntryContextDto context = resolve(memberId, providerId, requestedEmployerId, serviceDate);
        return preAuthorizationRepository.findEligibleForClaim(memberId, providerId, serviceDate).stream()
                .filter(pa -> pa.getPolicyId() == null || pa.getPolicyId().equals(context.policyId()))
                .map(pa -> new EligiblePreAuthorizationDto(pa.getId(),
                        pa.getReferenceNumber() != null ? pa.getReferenceNumber() : pa.getPreAuthNumber(),
                        pa.getStatus().name(), pa.getServiceName(), pa.getExpectedServiceDate(),
                        pa.getExpiryDate(), pa.getApprovedAmount()))
                .toList();
    }
}
