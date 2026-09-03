package com.waad.tba.modules.provider.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.provider.dto.ProviderServiceAssignDto;
import com.waad.tba.modules.provider.dto.ProviderServiceResponseDto;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.ProviderService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provider Service Assignment Service
 * 
 * Manages many-to-many relationship between Provider and MedicalService.
 * 
 * Key Responsibilities:
 * - Assign services to providers
 * - Remove service assignments
 * - Validate service codes exist in MedicalService
 * - Prevent duplicate assignments
 * - Return provider service catalog
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceService {

    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderRepository providerRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // ASSIGN SERVICE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Assign a medical service to a provider
     * 
     * Validations:
     * 1. Provider must exist and be active
     * 2. Service code must exist in MedicalService table
     * 3. Service must be active
     * 4. No duplicate assignments allowed
     */
    @Transactional
    public ProviderServiceResponseDto assignService(Long providerId, ProviderServiceAssignDto dto) {
        log.info("Assigning service {} to provider {}", dto.getServiceCode(), providerId);

        // 1. Validate provider exists and is active
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessRuleException("Provider not found with ID: " + providerId));

        if (!provider.getActive()) {
            throw new BusinessRuleException("Cannot assign service to inactive provider");
        }

        // 2. Validate service exists and is active
        MedicalService medicalService = medicalServiceRepository
                .findByCode(dto.getServiceCode())
                .orElseThrow(() -> new BusinessRuleException(
                        "Medical service not found with code: " + dto.getServiceCode()));

        if (!medicalService.isActive()) {
            throw new BusinessRuleException(
                    "Cannot assign inactive service: " + dto.getServiceCode());
        }

        // 3. Check for duplicate assignment
        boolean exists = providerServiceRepository.existsByProviderIdAndServiceCode(
                providerId, dto.getServiceCode());
        if (exists) {
            throw new BusinessRuleException(
                    "Service already assigned to provider: " + dto.getServiceCode());
        }

        // 4. Create assignment
        ProviderService providerService = ProviderService.builder()
                .providerId(providerId)
                .serviceCode(dto.getServiceCode())
                .active(true)
                .build();

        ProviderService saved = providerServiceRepository.save(providerService);

        log.info("✅ Service {} assigned to provider {} (assignment ID: {})",
                dto.getServiceCode(), providerId, saved.getId());

        return mapToResponseDto(saved, medicalService);
    }

    /**
     * Outcome of {@link #assignOrReactivate(Long, String)}: distinguishes a
     * true no-op from an actual write, so a bulk caller can report an
     * accurate created/reactivated/already-active summary.
     */
    public enum AssignmentOutcome {
        CREATED, REACTIVATED, ALREADY_ACTIVE
    }

    /**
     * Idempotent assign: unlike {@link #assignService}, which always attempts
     * an INSERT and fails on any existing row (active or not), this looks at
     * the existing row first and does the smallest correct thing -- no write
     * at all if already active, reactivate the same row if it was soft-
     * deleted, or create it if it never existed. Safe to call repeatedly with
     * the same (providerId, serviceCode): a second call never creates a
     * duplicate or throws.
     */
    @Transactional
    public AssignmentOutcome assignOrReactivate(Long providerId, String serviceCode) {
        return providerServiceRepository.findByProviderIdAndServiceCode(providerId, serviceCode)
                .map(existing -> {
                    if (Boolean.TRUE.equals(existing.getActive())) {
                        return AssignmentOutcome.ALREADY_ACTIVE;
                    }
                    existing.setActive(true);
                    providerServiceRepository.save(existing);
                    return AssignmentOutcome.REACTIVATED;
                })
                .orElseGet(() -> {
                    providerServiceRepository.save(ProviderService.builder()
                            .providerId(providerId).serviceCode(serviceCode).active(true).build());
                    return AssignmentOutcome.CREATED;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOVE SERVICE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Remove service assignment from provider (soft delete)
     */
    @Transactional
    public void removeService(Long providerId, String serviceCode) {
        log.info("Removing service {} from provider {}", serviceCode, providerId);

        ProviderService assignment = providerServiceRepository
                .findActiveByProviderIdAndServiceCode(providerId, serviceCode)
                .orElseThrow(() -> new BusinessRuleException(
                        "Service assignment not found for provider " + providerId +
                        " and service " + serviceCode));

        assignment.setActive(false);
        providerServiceRepository.save(assignment);

        log.info("✅ Service {} removed from provider {}", serviceCode, providerId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RETRIEVE SERVICES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all active services for a provider
     */
    @Transactional(readOnly = true)
    public List<ProviderServiceResponseDto> getProviderServices(Long providerId) {
        log.info("Retrieving services for provider {}", providerId);

        List<ProviderService> assignments = providerServiceRepository
                .findActiveByProviderId(providerId);

        return assignments.stream()
                .map(this::mapToResponseDtoWithServiceLookup)
                .collect(Collectors.toList());
    }

    /**
     * Get service codes for a provider (lightweight)
     */
    @Transactional(readOnly = true)
    public List<String> getProviderServiceCodes(Long providerId) {
        return providerServiceRepository.findServiceCodesByProviderId(providerId);
    }

    /**
     * Check if provider offers a specific service
     */
    @Transactional(readOnly = true)
    public boolean providerOffersService(Long providerId, String serviceCode) {
        return providerServiceRepository.existsByProviderIdAndServiceCode(providerId, serviceCode);
    }

    /**
     * Count active services for provider
     */
    @Transactional(readOnly = true)
    public long countProviderServices(Long providerId) {
        return providerServiceRepository.countActiveByProviderId(providerId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIND PROVIDERS BY SERVICE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all providers offering a specific service
     */
    @Transactional(readOnly = true)
    public List<Long> findProvidersByServiceCode(String serviceCode) {
        log.info("Finding providers offering service {}", serviceCode);
        return providerServiceRepository.findProviderIdsByServiceCode(serviceCode);
    }

    /**
     * Count providers offering a specific service
     */
    @Transactional(readOnly = true)
    public long countProvidersByService(String serviceCode) {
        return providerServiceRepository.countProvidersByServiceCode(serviceCode);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Assign multiple services to a provider (batch operation)
     */
    @Transactional
    public List<ProviderServiceResponseDto> assignMultipleServices(
            Long providerId, List<String> serviceCodes) {
        
        log.info("Assigning {} services to provider {}", serviceCodes.size(), providerId);

        return serviceCodes.stream()
                .map(code -> {
                    ProviderServiceAssignDto dto = new ProviderServiceAssignDto(code);
                    try {
                        return assignService(providerId, dto);
                    } catch (BusinessRuleException e) {
                        log.warn("Skipping service {}: {}", code, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAPPING
    // ═══════════════════════════════════════════════════════════════════════════

    private ProviderServiceResponseDto mapToResponseDto(
            ProviderService entity, MedicalService medicalService) {
        
        // Lookup category for category code and name
        String categoryCode = null;
        String categoryName = null;
        if (medicalService.getCategoryId() != null) {
            MedicalCategory category = medicalCategoryRepository
                    .findById(medicalService.getCategoryId())
                    .orElse(null);
            if (category != null) {
                categoryCode = category.getCode();
                categoryName = category.getName(); // Arabic name
            }
        }
        
        return ProviderServiceResponseDto.builder()
                .id(medicalService.getId())  // FIXED: Return medical_service_id, not provider_service assignment id
                .providerId(entity.getProviderId())
                .serviceCode(entity.getServiceCode())
                .serviceName(medicalService.getName())
                .categoryCode(categoryCode)
                .categoryName(categoryName)
                .requiresPreAuth(false) // PA requirement comes from BenefitPolicyRule, not MedicalService
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProviderServiceResponseDto mapToResponseDtoWithServiceLookup(ProviderService entity) {
        MedicalService medicalService = medicalServiceRepository
                .findByCode(entity.getServiceCode())
                .orElse(null);

        if (medicalService == null) {
            log.warn("Medical service not found for code: {}", entity.getServiceCode());
            return ProviderServiceResponseDto.builder()
                    .id(entity.getId())
                    .providerId(entity.getProviderId())
                    .serviceCode(entity.getServiceCode())
                    .serviceName("خدمة غير موجودة")
                    .categoryCode(null)
                    .categoryName(null)
                    .requiresPreAuth(true) // Default to true for safety
                    .active(entity.getActive())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        }

        return mapToResponseDto(entity, medicalService);
    }
}
