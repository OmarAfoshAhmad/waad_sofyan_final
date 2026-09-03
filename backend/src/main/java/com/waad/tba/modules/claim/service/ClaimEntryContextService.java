package com.waad.tba.modules.claim.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.waad.tba.modules.claim.dto.ClaimEntryContextDto;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.providercontract.service.EffectiveProviderContractResolver;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemResponseDto;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.claim.dto.EligiblePreAuthorizationDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;
import org.springframework.data.domain.PageImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private final ClaimProviderEmployerAccessService employerAccess;
    private final EffectiveProviderContractResolver contractResolver;
    private final ProviderContractPricingItemService pricingItemService;
    private final LimitBalanceReader limitBalanceReader;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;

    @Transactional(readOnly = true)
    public ClaimEntryContextDto resolve(Long memberId, Long providerId,
            Long requestedEmployerId, LocalDate serviceDate) {
        var memberContext = memberContextResolver.resolveForOrFail(memberId, serviceDate);

        employerAccess.requireMemberBelongsToEmployer(
                requestedEmployerId, memberContext.employer(), serviceDate);

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
                ceiling == null ? null : ceiling.annualLimit(),
                ceiling == null ? null : ceiling.committed(),
                ceiling == null ? null : ceiling.reserved(),
                ceiling == null ? null : ceiling.actualRemaining(),
                ceiling == null ? null : ceiling.reservableAvailable(),
                LocalDateTime.now(),
                eligiblePreAuthorizations(memberId, providerId, serviceDate, policy.getId()));
    }

    @Transactional(readOnly = true)
    public Page<ProviderContractPricingItemResponseDto> findEffectiveServices(
            Long memberId, Long providerId, Long requestedEmployerId,
            LocalDate serviceDate, String query, Pageable pageable) {
        ClaimEntryContextDto context = resolve(memberId, providerId, requestedEmployerId, serviceDate);
        Page<ProviderContractPricingItemResponseDto> contractItems = pricingItemService
                .findEffectiveInContract(context.contractId(), serviceDate, query, pageable);

        // Standard services (pharmacy/optics-style invoices, MANUAL_AMOUNT):
        // there is no ProviderContractPricingItem to page through, and the
        // set is always small (a handful of catalog entries), so it is
        // folded into the first page only rather than paginated separately.
        List<ProviderContractPricingItemResponseDto> manualAmountOptions = pageable.getOffset() == 0
                ? findManualAmountServiceOptions(providerId, query)
                : List.of();
        if (manualAmountOptions.isEmpty()) {
            return contractItems;
        }

        List<ProviderContractPricingItemResponseDto> merged = new ArrayList<>(contractItems.getContent());
        merged.addAll(manualAmountOptions);
        return new PageImpl<>(merged, pageable, contractItems.getTotalElements() + manualAmountOptions.size());
    }

    private List<ProviderContractPricingItemResponseDto> findManualAmountServiceOptions(
            Long providerId, String query) {
        Set<String> providerServiceCodes = Set.copyOf(
                providerServiceRepository.findServiceCodesByProviderId(providerId));
        if (providerServiceCodes.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<MedicalService> standardServices = medicalServiceRepository
                .findByPricingModeAndActiveTrue(PricingMode.MANUAL_AMOUNT).stream()
                .filter(service -> providerServiceCodes.contains(service.getCode()))
                .filter(service -> normalizedQuery.isBlank()
                        || service.getName().toLowerCase().contains(normalizedQuery)
                        || service.getCode().toLowerCase().contains(normalizedQuery))
                .toList();
        if (standardServices.isEmpty()) {
            return List.of();
        }

        java.util.Map<Long, MedicalCategory> categoriesById = medicalCategoryRepository
                .findAllById(standardServices.stream().map(MedicalService::getCategoryId)
                        .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(MedicalCategory::getId, c -> c));

        return standardServices.stream().map(service -> {
            MedicalCategory category = categoriesById.get(service.getCategoryId());
            ProviderContractPricingItemResponseDto.CategorySummaryDto categoryDto = category == null ? null
                    : ProviderContractPricingItemResponseDto.CategorySummaryDto.builder()
                            .id(category.getId()).code(category.getCode())
                            .name(category.getName()).nameAr(category.getNameAr()).build();
            return ProviderContractPricingItemResponseDto.builder()
                    .medicalServiceId(service.getId())
                    .pricingMode(PricingMode.MANUAL_AMOUNT.name())
                    .serviceName(service.getName())
                    .serviceCode(service.getCode())
                    .categoryName(category != null
                            ? (category.getNameAr() != null ? category.getNameAr() : category.getName())
                            : null)
                    .medicalCategory(categoryDto)
                    .effectiveCategory(categoryDto)
                    .quantity(1)
                    .isCurrentlyEffective(true)
                    .build();
        }).toList();
    }

    public Page<ProviderContractPricingItemResponseDto> findEffectiveServices(
            Long memberId, Long providerId, Long requestedEmployerId,
            LocalDate serviceDate, Pageable pageable) {
        return findEffectiveServices(memberId, providerId, requestedEmployerId, serviceDate, null, pageable);
    }

    @Transactional(readOnly = true)
    public List<EligiblePreAuthorizationDto> findEligiblePreAuthorizations(
            Long memberId, Long providerId, Long requestedEmployerId, LocalDate serviceDate) {
        ClaimEntryContextDto context = resolve(memberId, providerId, requestedEmployerId, serviceDate);
        return context.eligiblePreAuthorizations();
    }

    private List<EligiblePreAuthorizationDto> eligiblePreAuthorizations(
            Long memberId, Long providerId, LocalDate serviceDate, Long policyId) {
        return preAuthorizationRepository.findEligibleForClaim(memberId, providerId, serviceDate).stream()
                .filter(pa -> pa.getPolicyId() == null || pa.getPolicyId().equals(policyId))
                .map(pa -> new EligiblePreAuthorizationDto(pa.getId(),
                        pa.getReferenceNumber() != null ? pa.getReferenceNumber() : pa.getPreAuthNumber(),
                        pa.getStatus().name(), pa.getServiceName(), pa.getExpectedServiceDate(),
                        pa.getExpiryDate(), pa.getApprovedAmount()))
                .toList();
    }
}
