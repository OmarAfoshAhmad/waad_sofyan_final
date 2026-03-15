package com.waad.tba.modules.provider.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.provider.dto.*;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.ProviderContract;
import com.waad.tba.modules.provider.repository.ProviderContractRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for ProviderContract operations (Legacy module - integrated
 * with Provider)
 * 
 * Responsibilities:
 * - Contract CRUD operations
 * - Validation (provider, service, dates, prices)
 * - Price lookups (effective price on date)
 * - Business rules enforcement
 * - Audit trail
 * 
 * Integration:
 * - ProviderRepository: Validate provider exists and is active
 * - MedicalServiceRepository: Validate service exists and is active
 * - ProviderContractRepository: CRUD operations
 * - ProviderContractPricingItemRepository: NEW normalized pricing lookups
 */
@Service("legacyProviderContractService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProviderContractService {

    private final ProviderContractRepository providerContractRepository;
    private final ProviderRepository providerRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final MemberRepository memberRepository;
    private final BenefitPolicyRuleService benefitPolicyRuleService;

    // ==================== CREATE ====================

    /**
     * Create a new provider contract
     * 
     * Validation:
     * 1. Provider exists and is active
     * 2. Service exists and is active
     * 3. Date range is valid
     * 4. Price is valid (>= 0)
     * 5. No overlapping contracts (optional - for strict mode)
     * 
     * @param providerId Provider ID
     * @param dto        Contract data
     * @return Created contract
     * @throws ResourceNotFoundException if provider or service not found
     * @throws BusinessRuleException     if validation fails
     */
    public ProviderContractResponseDto createContract(Long providerId, ProviderContractCreateDto dto) {
        log.info("[PROVIDER-CONTRACT] Creating contract for provider {} and service {}",
                providerId, dto.getServiceCode());

        // Step 1: Validate provider
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider not found with ID: " + providerId));

        if (!provider.getActive()) {
            throw new BusinessRuleException(
                    "Cannot create contract for inactive provider: " + provider.getName());
        }

        // Step 2: Validate service
        MedicalService service = medicalServiceRepository.findByCode(dto.getServiceCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Medical service not found with code: " + dto.getServiceCode()));

        if (!service.isActive()) {
            throw new BusinessRuleException(
                    "Cannot create contract for inactive service: " + service.getName());
        }

        // Step 3: Validate date range
        if (dto.getEffectiveTo() != null && dto.getEffectiveTo().isBefore(dto.getEffectiveFrom())) {
            throw new BusinessRuleException(
                    "Effective to date must be after or equal to effective from date");
        }

        // Step 4: Check for overlapping contracts (warning only, not strict)
        boolean hasOverlap = providerContractRepository.hasOverlappingContract(
                providerId,
                dto.getServiceCode(),
                dto.getEffectiveFrom(),
                dto.getEffectiveTo(),
                0L // No exclusion for new contract
        );

        if (hasOverlap) {
            log.warn("[PROVIDER-CONTRACT] Overlapping contract detected for provider {} and service {}",
                    providerId, dto.getServiceCode());
            // Allow overlapping contracts for price history
            // throw new BusinessRuleException("Overlapping contract exists for this
            // period");
        }

        // Step 5: Create contract
        ProviderContract contract = ProviderContract.builder()
                .providerId(providerId)
                .serviceCode(dto.getServiceCode())
                .contractPrice(dto.getContractPrice())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "LYD")
                .effectiveFrom(dto.getEffectiveFrom())
                .effectiveTo(dto.getEffectiveTo())
                .notes(dto.getNotes())
                .active(true)
                .createdBy(getCurrentUsername())
                .build();

        contract = providerContractRepository.save(contract);

        log.info("[PROVIDER-CONTRACT] Contract created successfully with ID: {}", contract.getId());

        return mapToResponseDto(contract, service);
    }

    // ==================== UPDATE ====================

    /**
     * Update an existing provider contract
     * 
     * Note: serviceCode is NOT updatable
     * To change service, delete old contract and create new one
     * 
     * @param providerId Provider ID
     * @param contractId Contract ID
     * @param dto        Update data
     * @return Updated contract
     * @throws ResourceNotFoundException if contract not found
     * @throws BusinessRuleException     if validation fails
     */
    public ProviderContractResponseDto updateContract(
            Long providerId,
            Long contractId,
            ProviderContractUpdateDto dto) {
        log.info("[PROVIDER-CONTRACT] Updating contract {} for provider {}", contractId, providerId);

        // Find contract
        ProviderContract contract = providerContractRepository.findByIdAndProviderId(contractId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with ID: " + contractId + " for provider: " + providerId));

        // Update fields (only if provided)
        if (dto.getContractPrice() != null) {
            if (dto.getContractPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Contract price must be >= 0");
            }
            contract.setContractPrice(dto.getContractPrice());
        }

        if (dto.getCurrency() != null) {
            contract.setCurrency(dto.getCurrency());
        }

        if (dto.getEffectiveFrom() != null) {
            contract.setEffectiveFrom(dto.getEffectiveFrom());
        }

        if (dto.getEffectiveTo() != null) {
            contract.setEffectiveTo(dto.getEffectiveTo());
        }

        if (dto.getNotes() != null) {
            contract.setNotes(dto.getNotes());
        }

        if (dto.getActive() != null) {
            contract.setActive(dto.getActive());
        }

        // Validate date range
        contract.validateDateRange();

        // Set updated by
        contract.setUpdatedBy(getCurrentUsername());

        contract = providerContractRepository.save(contract);

        log.info("[PROVIDER-CONTRACT] Contract updated successfully: {}", contractId);

        // Fetch service for response
        MedicalService service = medicalServiceRepository.findByCode(contract.getServiceCode())
                .orElse(null);

        return mapToResponseDto(contract, service);
    }

    // ==================== DELETE ====================

    /**
     * Delete (soft delete) a provider contract
     * 
     * @param providerId Provider ID
     * @param contractId Contract ID
     * @throws ResourceNotFoundException if contract not found
     */
    public void deleteContract(Long providerId, Long contractId) {
        log.info("[PROVIDER-CONTRACT] Deleting contract {} for provider {}", contractId, providerId);

        ProviderContract contract = providerContractRepository.findByIdAndProviderId(contractId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with ID: " + contractId + " for provider: " + providerId));

        contract.setActive(false);
        contract.setUpdatedBy(getCurrentUsername());
        providerContractRepository.save(contract);

        log.info("[PROVIDER-CONTRACT] Contract deleted successfully: {}", contractId);
    }

    // ==================== READ ====================

    /**
     * Get all contracts for a provider (paginated)
     * 
     * @param providerId Provider ID
     * @param activeOnly Filter by active status
     * @param pageable   Pagination
     * @return Page of contracts
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> getProviderContracts(
            Long providerId,
            boolean activeOnly,
            Pageable pageable) {
        log.debug("[PROVIDER-CONTRACT] Getting contracts for provider {}, activeOnly={}",
                providerId, activeOnly);

        Page<ProviderContract> contracts = providerContractRepository.findByProviderIdAndActive(
                providerId,
                activeOnly,
                pageable);

        // Fetch all service codes
        List<String> serviceCodes = contracts.getContent().stream()
                .map(ProviderContract::getServiceCode)
                .distinct()
                .collect(Collectors.toList());

        // Fetch services in bulk
        Map<String, MedicalService> serviceMap = medicalServiceRepository.findByCodes(serviceCodes)
                .stream()
                .collect(Collectors.toMap(MedicalService::getCode, s -> s));

        return contracts.map(contract -> mapToResponseDto(contract, serviceMap.get(contract.getServiceCode())));
    }

    /**
     * Get currently effective contracts for a provider
     * 
     * @param providerId Provider ID
     * @return List of currently effective contracts
     */
    @Transactional(readOnly = true)
    public List<ProviderContractResponseDto> getCurrentlyEffectiveContracts(Long providerId) {
        log.debug("[PROVIDER-CONTRACT] Getting currently effective contracts for provider {}", providerId);

        List<ProviderContract> contracts = providerContractRepository.findCurrentlyEffectiveContracts(providerId);

        // Fetch services
        List<String> serviceCodes = contracts.stream()
                .map(ProviderContract::getServiceCode)
                .distinct()
                .collect(Collectors.toList());

        Map<String, MedicalService> serviceMap = medicalServiceRepository.findByCodes(serviceCodes)
                .stream()
                .collect(Collectors.toMap(MedicalService::getCode, s -> s));

        return contracts.stream()
                .map(contract -> mapToResponseDto(contract, serviceMap.get(contract.getServiceCode())))
                .collect(Collectors.toList());
    }

    /**
     * Get contract by ID
     * 
     * @param providerId Provider ID
     * @param contractId Contract ID
     * @return Contract
     * @throws ResourceNotFoundException if contract not found
     */
    @Transactional(readOnly = true)
    public ProviderContractResponseDto getContractById(Long providerId, Long contractId) {
        log.debug("[PROVIDER-CONTRACT] Getting contract {} for provider {}", contractId, providerId);

        ProviderContract contract = providerContractRepository.findByIdAndProviderId(contractId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with ID: " + contractId + " for provider: " + providerId));

        MedicalService service = medicalServiceRepository.findByCode(contract.getServiceCode())
                .orElse(null);

        return mapToResponseDto(contract, service);
    }

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
        log.debug("[PROVIDER-CONTRACT] Getting effective price for provider {}, service {}, date {}",
                providerId, serviceCode, date);

        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        // Fetch provider
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider not found with ID: " + providerId));

        // Attempt to find service (NOT strictly required anymore for price lookup)
        MedicalService service = medicalServiceRepository.findByCode(serviceCode).orElse(null);

        // ═══════════════════════════════════════════════════════════════════════════
        // Use NORMALIZED table (provider_contract_pricing_items)
        // ═══════════════════════════════════════════════════════════════════════════
        Optional<ProviderContractPricingItem> pricingOpt = (service != null)
                ? pricingItemRepository.findEffectivePricing(providerId, service.getId(), effectiveDate)
                : pricingItemRepository.findEffectivePricingByCode(providerId, serviceCode, effectiveDate);

        if (pricingOpt.isPresent()) {
            ProviderContractPricingItem pricing = pricingOpt.get();
            log.debug("✅ Found effective pricing: provider={}, service={}, price={}",
                    providerId, serviceCode, pricing.getContractPrice());

            return EffectivePriceResponseDto.builder()
                    .providerId(providerId)
                    .providerName(provider.getName())
                    .serviceCode(serviceCode)
                    .serviceName(pricing.getServiceName() != null ? pricing.getServiceName()
                            : (service != null ? service.getName() : "Unknown"))
                    .contractPrice(pricing.getContractPrice())
                    .currency(pricing.getCurrency())
                    .effectiveDate(effectiveDate)
                    .contractId(pricing.getContract().getId())
                    .effectiveFrom(pricing.getEffectiveFrom() != null ? pricing.getEffectiveFrom()
                            : pricing.getContract().getStartDate())
                    .effectiveTo(pricing.getEffectiveTo() != null ? pricing.getEffectiveTo()
                            : pricing.getContract().getEndDate())
                    .pricingItemId(pricing.getId())
                    .hasContract(true)
                    .message("Contract found")
                    .build();
        }

        // No contract found in either table
        log.warn("❌ No contract found for provider={}, service={}, date={}",
                providerId, serviceCode, effectiveDate);

        return EffectivePriceResponseDto.builder()
                .providerId(providerId)
                .providerName(provider.getName())
                .serviceCode(serviceCode)
                .serviceName(service != null ? service.getName() : serviceCode)
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

    // ==================== STATISTICS ====================

    /**
     * Count active contracts for a provider
     */
    @Transactional(readOnly = true)
    public long countActiveContracts(Long providerId) {
        return providerContractRepository.countByProviderIdAndActive(providerId, true);
    }

    /**
     * Count currently effective contracts for a provider
     */
    @Transactional(readOnly = true)
    public long countCurrentlyEffectiveContracts(Long providerId) {
        return providerContractRepository.countCurrentlyEffectiveContracts(providerId);
    }

    /**
     * Get service codes with contracts for a provider
     */
    @Transactional(readOnly = true)
    public List<String> getServiceCodesWithContracts(Long providerId) {
        return providerContractRepository.findServiceCodesByProviderId(providerId);
    }

    // ==================== MAINTENANCE ====================

    /**
     * Find expired contracts
     * For maintenance/cleanup
     */
    @Transactional(readOnly = true)
    public List<ProviderContractResponseDto> findExpiredContracts() {
        log.debug("[PROVIDER-CONTRACT] Finding expired contracts");

        List<ProviderContract> contracts = providerContractRepository.findExpiredContracts();

        // Fetch services
        List<String> serviceCodes = contracts.stream()
                .map(ProviderContract::getServiceCode)
                .distinct()
                .collect(Collectors.toList());

        Map<String, MedicalService> serviceMap = medicalServiceRepository.findByCodes(serviceCodes)
                .stream()
                .collect(Collectors.toMap(MedicalService::getCode, s -> s));

        return contracts.stream()
                .map(contract -> mapToResponseDto(contract, serviceMap.get(contract.getServiceCode())))
                .collect(Collectors.toList());
    }

    /**
     * Find contracts expiring within N days
     */
    @Transactional(readOnly = true)
    public List<ProviderContractResponseDto> findContractsExpiringWithinDays(int days) {
        log.debug("[PROVIDER-CONTRACT] Finding contracts expiring within {} days", days);

        LocalDate expiryDate = LocalDate.now().plusDays(days);
        List<ProviderContract> contracts = providerContractRepository.findContractsExpiringBefore(expiryDate);

        // Fetch services
        List<String> serviceCodes = contracts.stream()
                .map(ProviderContract::getServiceCode)
                .distinct()
                .collect(Collectors.toList());

        Map<String, MedicalService> serviceMap = medicalServiceRepository.findByCodes(serviceCodes)
                .stream()
                .collect(Collectors.toMap(MedicalService::getCode, s -> s));

        return contracts.stream()
                .map(contract -> mapToResponseDto(contract, serviceMap.get(contract.getServiceCode())))
                .collect(Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    /**
     * Map ProviderContract entity to ResponseDto
     */
    private ProviderContractResponseDto mapToResponseDto(ProviderContract contract, MedicalService service) {
        return ProviderContractResponseDto.builder()
                .id(contract.getId())
                .providerId(contract.getProviderId())
                .serviceCode(contract.getServiceCode())
                .serviceName(service != null ? service.getName() : null)
                .contractPrice(contract.getContractPrice())
                .currency(contract.getCurrency())
                .effectiveFrom(contract.getEffectiveFrom())
                .effectiveTo(contract.getEffectiveTo())
                .active(contract.isActive())
                .notes(contract.getNotes())
                .isCurrentlyEffective(contract.isCurrentlyEffective())
                .isExpired(contract.isExpired())
                .isOpenEnded(contract.isOpenEnded())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .createdBy(contract.getCreatedBy())
                .updatedBy(contract.getUpdatedBy())
                .build();
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
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

        // Filter services that require pre-approval
        return pricingItems.stream()
                .filter(item -> {
                    MedicalService service = item.getMedicalService();
                    if (service == null)
                        return false;

                    // Check if this service requires pre-approval in the member's policy
                    return benefitPolicyRuleService.requiresPreApproval(policyId, service.getId(), null);
                })
                .map(item -> {
                    MedicalService service = item.getMedicalService();
                    return ProviderServiceDto.builder()
                            .serviceId(service.getId())
                            .serviceCode(service.getCode())
                            .serviceName(service.getName())
                            .serviceNameArabic(service.getName())
                            .categoryCode(null)
                            .categoryName(
                                    item.getMedicalCategory() != null ? item.getMedicalCategory().getName() : null)
                            .contractPrice(item.getContractPrice())
                            .currency(item.getCurrency())
                            .requiresPA(true)
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
