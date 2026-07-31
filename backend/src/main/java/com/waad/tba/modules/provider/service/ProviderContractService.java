package com.waad.tba.modules.provider.service;

import com.waad.tba.common.exception.ResourceNotFoundException;

import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.provider.dto.*;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Provider contract pricing lookups.
 *
 * All contract CRUD/list operations were retired on 2026-07-27 — they used
 * to write/read a separate legacy_provider_contracts table that the pricing
 * engine below never consulted (see SECTION_02 audit for the incident).
 * Contract management now lives entirely in
 * com.waad.tba.modules.providercontract; this class only resolves effective
 * prices and pre-auth requirements from that module's normalized
 * provider_contract_pricing_items table, for callers (ClaimMapper,
 * PreAuthorizationService, the provider portal) that need a price lookup
 * without depending on the contracts module's DTOs.
 */
@Service("legacyProviderContractService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProviderContractService {

        private final ProviderRepository providerRepository;
        private final ProviderContractPricingItemRepository pricingItemRepository;
        private final MemberRepository memberRepository;
        private final BenefitPolicyRuleService benefitPolicyRuleService;

        // ==================== PRICE LOOKUP ====================

        /**
         * Get effective price for a service on a specific date
         * 
         * Uses the NORMALIZED provider_contract_pricing_items table ONLY.
         * Legacy table (legacy_provider_contracts) is DEPRECATED and should be removed.
         * 
         * @param providerId  Provider ID
         * @param serviceCode Service code
         * @param date        Date to check (default: today)
         * @return Effective price
         */
        @Transactional(readOnly = true)
        public EffectivePriceResponseDto getEffectivePrice(
                        Long providerId,
                        String serviceCode,
                        LocalDate date) {
                return getEffectivePrice(providerId, null, serviceCode, date);
        }

        /**
         * Get effective price with employer-aware resolution.
         *
         * Resolution order:
         * 1. EMPLOYER_SPECIFIC contract for provider + employer.
         * 2. GLOBAL provider contract fallback.
         */
        @Transactional(readOnly = true)
        public EffectivePriceResponseDto getEffectivePrice(
                        Long providerId,
                        Long employerId,
                        String serviceCode,
                        LocalDate date) {
                log.debug("[PROVIDER-CONTRACT] Getting effective price for provider {}, service {}, date {}",
                                providerId, serviceCode, date);

                LocalDate effectiveDate = date != null ? date : LocalDate.now();

                // Fetch provider
                Provider provider = providerRepository.findById(providerId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Provider not found with ID: " + providerId));

                Optional<ProviderContractPricingItem> employerPricingOpt = employerId != null
                                ? pricingItemRepository.findEffectiveEmployerPricingByCode(
                                                providerId, employerId, serviceCode, effectiveDate)
                                : Optional.empty();

                Optional<ProviderContractPricingItem> pricingOpt = employerPricingOpt.isPresent()
                                ? employerPricingOpt
                                : pricingItemRepository.findEffectiveGlobalPricingByCode(
                                                providerId, serviceCode, effectiveDate);

                if (pricingOpt.isPresent()) {
                        ProviderContractPricingItem pricing = pricingOpt.get();
                        boolean usedGlobalFallback = employerId != null && employerPricingOpt.isEmpty();
                        log.debug("✅ Found effective pricing: provider={}, service={}, price={}",
                                        providerId, serviceCode, pricing.getContractPrice());

                        return EffectivePriceResponseDto.builder()
                                        .providerId(providerId)
                                        .providerName(provider.getName())
                                        .employerId(pricing.getContract().getEmployer() != null
                                                        ? pricing.getContract().getEmployer().getId()
                                                        : employerId)
                                        .pricingScope(pricing.getContract().getPricingScope() != null
                                                        ? pricing.getContract().getPricingScope().name()
                                                        : "GLOBAL")
                                        .usedGlobalFallback(usedGlobalFallback)
                                        .serviceCode(serviceCode)
                                        .serviceName(pricing.getServiceName() != null ? pricing.getServiceName()
                                                        : serviceCode)
                                        .contractPrice(pricing.getContractPrice())
                                        .maxContractPrice(pricing.getMaxContractPrice())
                                        .currency(pricing.getCurrency())
                                        .effectiveDate(effectiveDate)
                                        .contractId(pricing.getContract().getId())
                                        .effectiveFrom(pricing.getEffectiveFrom() != null ? pricing.getEffectiveFrom()
                                                        : pricing.getContract().getStartDate())
                                        .effectiveTo(pricing.getEffectiveTo() != null ? pricing.getEffectiveTo()
                                                        : pricing.getContract().getEndDate())
                                        .pricingItemId(pricing.getId())
                                        .hasContract(true)
                                        .message(usedGlobalFallback
                                                        ? "Global provider contract fallback used"
                                                        : "Contract found")
                                        .build();
                }

                // No contract found in either table
                log.warn("❌ No contract found for provider={}, service={}, date={}",
                                providerId, serviceCode, effectiveDate);

                return EffectivePriceResponseDto.builder()
                                .providerId(providerId)
                                .providerName(provider.getName())
                                .employerId(employerId)
                                .pricingScope(null)
                                .usedGlobalFallback(false)
                                .serviceCode(serviceCode)
                                .serviceName(serviceCode)
                                .contractPrice(null)
                                .currency("LYD")
                                .effectiveDate(effectiveDate)
                                .contractId(null)
                                .effectiveFrom(null)
                                .effectiveTo(null)
                                .hasContract(false)
                                .message("No contract found for this date")
                                .build();
        }

        /**
         * Get services requiring pre-approval for a member from provider's active
         * contract.
         * 
         * This method:
         * 1. Gets the provider's active contract and its pricing items
         * 2. For each service, checks the member's benefit policy rules
         * 3. Returns only services where requiresPreApproval = true
         * 
         * @param providerId Provider ID
         * @param memberId   Member ID to check benefit policy rules
         * @return List of services requiring pre-approval with contract prices
         */
        public java.util.List<ProviderServiceDto> getServicesRequiringPreAuth(Long providerId, Long memberId) {
                log.info("[PROVIDER-CONTRACT] Getting services requiring pre-auth for provider {} and member {}",
                                providerId, memberId);

                // Get pricing items from the NEW modern tables
                java.util.List<com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem> pricingItems = pricingItemRepository
                                .findEffectivePricingByProvider(providerId, java.time.LocalDate.now());

                if (pricingItems.isEmpty()) {
                        log.warn("[PROVIDER-CONTRACT] No pricing items found for provider {}", providerId);
                        return java.util.Collections.emptyList();
                }

                // Get member's benefit policy
                com.waad.tba.modules.member.entity.Member member = memberRepository.findById(memberId).orElse(null);
                if (member == null || member.getBenefitPolicy() == null) {
                        log.warn("[PROVIDER-CONTRACT] Member {} not found or has no benefit policy", memberId);
                        return java.util.Collections.emptyList();
                }

                Long policyId = member.getBenefitPolicy().getId();

                // Filter services that require pre-approval (category-based check since V229)
                return pricingItems.stream()
                                .filter(item -> {
                                        if (item.getMedicalCategory() == null)
                                                return false;
                                        return benefitPolicyRuleService.requiresPreApproval(
                                                        policyId, null, item.getMedicalCategory().getId());
                                })
                                .map(item -> ProviderServiceDto.builder()
                                                .serviceId(item.getId())
                                                .serviceCode(item.getServiceCode())
                                                .serviceName(item.getServiceName())
                                                .serviceNameArabic(item.getServiceName())
                                                .categoryCode(null)
                                                .categoryName(
                                                                item.getMedicalCategory() != null
                                                                                ? item.getMedicalCategory().getName()
                                                                                : null)
                                                .contractPrice(item.getContractPrice())
                                                .maxContractPrice(item.getMaxContractPrice())
                                                .currency(item.getCurrency())
                                                .requiresPA(true)
                                                .build())
                                .collect(java.util.stream.Collectors.toList());
        }
}
