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
import com.waad.tba.modules.medicaldictionary.service.MedicalDictionaryNormalizer;
import org.springframework.data.domain.PageImpl;
import java.util.ArrayList;
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
    private final ClaimProviderEmployerAccessService employerAccess;
    private final EffectiveProviderContractResolver contractResolver;
    private final ProviderContractPricingItemService pricingItemService;
    private final LimitBalanceReader limitBalanceReader;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final MedicalDictionaryNormalizer searchNormalizer;

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

        // General claim-entry services are shared medical catalog entries, not
        // provider price-list rows. Invoice-style professional standards stay
        // MANUAL_AMOUNT; services added from the claim window use CONTRACT_PRICE
        // with an editable quantity and a direct unit price.
        List<ProviderContractPricingItemResponseDto> generalOptions = pageable.getOffset() == 0
                ? findGeneralServiceOptions(query)
                : List.of();
        if (generalOptions.isEmpty()) {
            return contractItems;
        }

        List<ProviderContractPricingItemResponseDto> merged = new ArrayList<>(generalOptions);
        merged.addAll(contractItems.getContent());
        return new PageImpl<>(merged, pageable, contractItems.getTotalElements() + generalOptions.size());
    }

    private List<ProviderContractPricingItemResponseDto> findGeneralServiceOptions(String query) {
        String normalizedQuery = searchNormalizer.normalize(query);
        List<MedicalService> standardServices = new ArrayList<>();
        standardServices.addAll(medicalServiceRepository.findByPricingModeAndActiveTrue(PricingMode.MANUAL_AMOUNT));
        standardServices.addAll(medicalServiceRepository
                .findByPricingModeAndCodeStartingWithAndActiveTrue(PricingMode.CONTRACT_PRICE, "SYS-CLAIM-"));
        standardServices = standardServices.stream()
                .filter(service -> normalizedQuery.isBlank()
                        || searchNormalizer.normalize(service.getName()).contains(normalizedQuery)
                        || service.getCode().toLowerCase().contains(normalizedQuery))
                .sorted(java.util.Comparator.comparingInt(
                        service -> manualAmountSearchRank(service, normalizedQuery)))
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
                    .pricingMode(service.getPricingMode() == null
                            ? PricingMode.MANUAL_AMOUNT.name()
                            : service.getPricingMode().name())
                    .serviceName(service.getName())
                    .serviceCode(service.getCode())
                    .basePrice(service.getBasePrice())
                    .contractPrice(service.getBasePrice())
                    .maxContractPrice(service.getBasePrice())
                    .claimContextCode(service.getDefaultClaimContextCode())
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

    private int manualAmountSearchRank(MedicalService service, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return 100;
        }

        String normalizedName = searchNormalizer.normalize(service.getName());
        String normalizedCode = service.getCode() == null ? "" : service.getCode().toLowerCase();

        if (normalizedName.equals(normalizedQuery) || normalizedCode.equals(normalizedQuery)) {
            return 0;
        }
        if (normalizedName.startsWith(normalizedQuery) || normalizedCode.startsWith(normalizedQuery)) {
            return 1;
        }
        if (normalizedName.contains(normalizedQuery) || normalizedCode.contains(normalizedQuery)) {
            return 2;
        }
        return 10;
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
