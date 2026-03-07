package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalServiceCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.dto.*;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing Provider Contract Pricing Items.
 * 
 * Handles pricing negotiation between providers and TPA.
 * Each item links a contract to a medical service with negotiated prices.
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ProviderContractPricingItemService {

    private final ProviderContractPricingItemRepository pricingRepository;
    private final ProviderContractRepository contractRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final MedicalServiceCategoryRepository medicalServiceCategoryRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all pricing items for a contract (with category resolution)
     */
    @Transactional(readOnly = true)
    public List<ProviderContractPricingItemResponseDto> findByContract(Long contractId) {
        log.debug("Finding pricing items for contract: {}", contractId);

        // Verify contract exists
        verifyContractExists(contractId);

        List<ProviderContractPricingItem> items = pricingRepository.findByContractIdAndActiveTrue(contractId);

        // Build category map for resolving service categories
        Map<Long, MedicalCategory> categoryMap = buildCategoryMap(items);

        return items.stream()
                .map(item -> ProviderContractPricingItemResponseDto.fromEntity(item, categoryMap))
                .collect(Collectors.toList());
    }

    /**
     * Get all pricing items for a contract (paginated, with category resolution)
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractPricingItemResponseDto> findByContract(Long contractId, Pageable pageable) {
        log.debug("Finding pricing items for contract: {}, page: {}", contractId, pageable.getPageNumber());

        // Verify contract exists
        verifyContractExists(contractId);

        Page<ProviderContractPricingItem> page = pricingRepository.findByContractIdAndActiveTrue(contractId, pageable);

        // Build category map for resolving service categories
        Map<Long, MedicalCategory> categoryMap = buildCategoryMap(page.getContent());

        return page.map(item -> ProviderContractPricingItemResponseDto.fromEntity(item, categoryMap));
    }

    /**
     * Build a map of serviceId -> MedicalCategory for resolving effective service
     * categories.
     * Priority:
     * 1) Primary active mapping from medical_service_categories
     * 2) Legacy medical_services.category_id fallback
     */
    private Map<Long, MedicalCategory> buildCategoryMap(List<ProviderContractPricingItem> items) {
        Set<Long> serviceIds = items.stream()
                .map(ProviderContractPricingItem::getMedicalService)
                .filter(Objects::nonNull)
                .map(MedicalService::getId)
                .collect(Collectors.toSet());

        if (serviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> serviceToCategoryId = new HashMap<>();

        List<MedicalServiceCategory> mappings = medicalServiceCategoryRepository
                .findPrimaryActiveByServiceIds(serviceIds);
        for (MedicalServiceCategory mapping : mappings) {
            serviceToCategoryId.put(mapping.getServiceId(), mapping.getCategoryId());
        }

        for (ProviderContractPricingItem item : items) {
            MedicalService medicalService = item.getMedicalService();
            if (medicalService == null || medicalService.getId() == null) {
                continue;
            }
            serviceToCategoryId.putIfAbsent(medicalService.getId(), medicalService.getCategoryId());
        }

        Set<Long> categoryIds = serviceToCategoryId.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, MedicalCategory> categoryById = medicalCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(MedicalCategory::getId, cat -> cat));

        Map<Long, MedicalCategory> serviceCategoryMap = new HashMap<>();
        for (Map.Entry<Long, Long> entry : serviceToCategoryId.entrySet()) {
            MedicalCategory category = categoryById.get(entry.getValue());
            if (category != null) {
                serviceCategoryMap.put(entry.getKey(), category);
            }
        }

        return serviceCategoryMap;
    }

    /**
     * Get pricing item by ID
     */
    @Transactional(readOnly = true)
    public ProviderContractPricingItemResponseDto findById(Long id) {
        log.debug("Finding pricing item by ID: {}", id);

        ProviderContractPricingItem item = pricingRepository.findById(id)
                .filter(i -> Boolean.TRUE.equals(i.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Pricing item not found: " + id));

        return ProviderContractPricingItemResponseDto.fromEntity(item);
    }

    /**
     * Search pricing items within a contract
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractPricingItemResponseDto> searchInContract(Long contractId, String query,
            Pageable pageable) {
        log.debug("Searching pricing items in contract: {}, query: {}", contractId, query);

        verifyContractExists(contractId);

        if (query == null || query.trim().isEmpty()) {
            return findByContract(contractId, pageable);
        }

        return pricingRepository.searchByServiceCodeOrName(contractId, query, pageable)
                .map(ProviderContractPricingItemResponseDto::fromEntity);
    }

    /**
     * Get effective pricing for a provider/service combination
     */
    @Transactional(readOnly = true)
    public ProviderContractPricingItemResponseDto findEffectivePricing(Long providerId, Long serviceId) {
        log.debug("Finding effective pricing for provider: {}, service: {}", providerId, serviceId);

        return pricingRepository.findEffectivePricing(providerId, serviceId, java.time.LocalDate.now())
                .map(ProviderContractPricingItemResponseDto::fromEntity)
                .orElse(null);
    }

    /**
     * Get contract pricing statistics
     */
    @Transactional(readOnly = true)
    public PricingStatsDto getPricingStats(Long contractId) {
        log.debug("Getting pricing stats for contract: {}", contractId);

        verifyContractExists(contractId);

        long itemCount = pricingRepository.countByContractIdAndActiveTrue(contractId);
        BigDecimal avgDiscount = pricingRepository.getAverageDiscount(contractId);
        BigDecimal totalSavings = pricingRepository.getTotalSavings(contractId);
        BigDecimal totalStandardPrice = pricingRepository.getTotalStandardPrice(contractId);
        BigDecimal totalContractedPrice = pricingRepository.getTotalContractedPrice(contractId);

        return PricingStatsDto.builder()
                .totalItems(itemCount)
                .averageDiscountPercent(
                        avgDiscount != null ? avgDiscount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .totalSavings(totalSavings != null ? totalSavings : BigDecimal.ZERO)
                .totalStandardPrice(totalStandardPrice != null ? totalStandardPrice : BigDecimal.ZERO)
                .totalContractedPrice(totalContractedPrice != null ? totalContractedPrice : BigDecimal.ZERO)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Add pricing item to contract
     */
    @Transactional
    public ProviderContractPricingItemResponseDto create(Long contractId, ProviderContractPricingItemCreateDto dto) {
        log.info("Adding pricing item to contract: {}", contractId);

        // Get contract
        ProviderContract contract = contractRepository.findById(contractId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + contractId));

        // Validate contract allows pricing modifications
        if (!contract.canModifyPricing()) {
            throw new BusinessRuleException("Cannot modify pricing for contract with status: " + contract.getStatus());
        }

        // Get medical service
        MedicalService service = medicalServiceRepository.findById(dto.getMedicalServiceId())
                .orElseThrow(
                        () -> new BusinessRuleException("Medical service not found: " + dto.getMedicalServiceId()));

        MedicalCategory categoryOverride = null;
        if (dto.getMedicalCategoryId() != null) {
            categoryOverride = medicalCategoryRepository.findById(dto.getMedicalCategoryId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Medical category not found: " + dto.getMedicalCategoryId()));
        }

        // Check if pricing already exists for this service
        if (pricingRepository.existsByContractIdAndMedicalServiceIdAndActiveTrue(contractId,
                dto.getMedicalServiceId())) {
            throw new BusinessRuleException("Pricing already exists for this service in contract. Update instead.");
        }

        // Validate prices
        BigDecimal basePrice = dto.getBasePrice();
        if (basePrice == null) {
            throw new BusinessRuleException("Base price is required");
        }

        BigDecimal contractPrice = dto.getContractPrice();
        if (contractPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Contract price must be greater than zero");
        }

        // Build entity
        ProviderContractPricingItem item = ProviderContractPricingItem.builder()
                .contract(contract)
                .medicalService(service)
                .medicalCategory(categoryOverride)
                .categoryName(
                        categoryOverride != null ? categoryOverride.getName() : resolveCategoryNameForService(service))
                .basePrice(basePrice)
                .contractPrice(contractPrice)
                .effectiveFrom(dto.getEffectiveFrom() != null ? dto.getEffectiveFrom() : java.time.LocalDate.now())
                .effectiveTo(dto.getEffectiveTo())
                .notes(dto.getNotes())
                .active(true)
                .build();

        // Discount is calculated in @PrePersist
        item = pricingRepository.save(item);

        log.info("Added pricing item {} to contract: {}", item.getId(), contract.getContractCode());
        return ProviderContractPricingItemResponseDto.fromEntity(item);
    }

    /**
     * Bulk add pricing items to contract
     */
    @Transactional
    public List<ProviderContractPricingItemResponseDto> createBulk(Long contractId,
            List<ProviderContractPricingItemCreateDto> dtos) {
        log.info("Bulk adding {} pricing items to contract: {}", dtos.size(), contractId);

        return dtos.stream()
                .map(dto -> create(contractId, dto))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update pricing item
     */
    @Transactional
    public ProviderContractPricingItemResponseDto update(Long id, ProviderContractPricingItemUpdateDto dto) {
        log.info("Updating pricing item: {}", id);

        ProviderContractPricingItem item = pricingRepository.findById(id)
                .filter(i -> Boolean.TRUE.equals(i.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Pricing item not found: " + id));

        ProviderContract contract = item.getContract();

        // Validate contract allows pricing modifications
        if (!contract.canModifyPricing()) {
            throw new BusinessRuleException("Cannot modify pricing for contract with status: " + contract.getStatus());
        }

        if (dto.getMedicalServiceId() != null) {
            MedicalService service = medicalServiceRepository.findById(dto.getMedicalServiceId())
                    .orElseThrow(
                            () -> new BusinessRuleException("Medical service not found: " + dto.getMedicalServiceId()));

            boolean duplicateInContract = pricingRepository.existsByContractIdAndMedicalServiceIdAndActiveTrue(
                    contract.getId(), service.getId());
            if (duplicateInContract && (item.getMedicalService() == null
                    || !Objects.equals(item.getMedicalService().getId(), service.getId()))) {
                throw new BusinessRuleException("Pricing already exists for this service in contract");
            }

            item.setMedicalService(service);
            item.setServiceName(service.getName());
            item.setServiceCode(service.getCode());
            item.setCategoryName(resolveCategoryNameForService(service));
        }

        if (dto.getMedicalCategoryId() != null) {
            MedicalCategory categoryOverride = medicalCategoryRepository.findById(dto.getMedicalCategoryId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Medical category not found: " + dto.getMedicalCategoryId()));
            item.setMedicalCategory(categoryOverride);
            item.setCategoryName(categoryOverride.getName());
        }

        // Apply updates
        if (dto.getBasePrice() != null) {
            item.setBasePrice(dto.getBasePrice());
        }
        if (dto.getContractPrice() != null) {
            if (dto.getContractPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Contract price must be greater than zero");
            }
            item.setContractPrice(dto.getContractPrice());
        }
        if (dto.getEffectiveFrom() != null) {
            item.setEffectiveFrom(dto.getEffectiveFrom());
        }
        if (dto.getEffectiveTo() != null) {
            item.setEffectiveTo(dto.getEffectiveTo());
        }
        if (dto.getNotes() != null) {
            item.setNotes(dto.getNotes());
        }

        // Discount is recalculated in @PreUpdate
        item = pricingRepository.save(item);

        log.info("Updated pricing item: {}", id);
        return ProviderContractPricingItemResponseDto.fromEntity(item);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Delete pricing item (soft delete)
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting pricing item: {}", id);

        ProviderContractPricingItem item = pricingRepository.findById(id)
                .filter(i -> Boolean.TRUE.equals(i.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Pricing item not found: " + id));

        ProviderContract contract = item.getContract();

        // Validate contract allows pricing modifications
        if (!contract.canModifyPricing()) {
            throw new BusinessRuleException("Cannot modify pricing for contract with status: " + contract.getStatus());
        }

        item.setActive(false);
        pricingRepository.save(item);

        log.info("Soft deleted pricing item: {}", id);
    }

    /**
     * Delete all pricing items for a contract
     */
    @Transactional
    public int deleteByContract(Long contractId) {
        log.info("Deleting all pricing items for contract: {}", contractId);

        ProviderContract contract = contractRepository.findById(contractId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + contractId));

        // Only allow for DRAFT contracts
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessRuleException("Can only bulk delete pricing for DRAFT contracts");
        }

        List<ProviderContractPricingItem> items = pricingRepository.findByContractIdAndActiveTrue(contractId);
        int count = 0;

        for (ProviderContractPricingItem item : items) {
            item.setActive(false);
            pricingRepository.save(item);
            count++;
        }

        log.info("Soft deleted {} pricing items for contract: {}", count, contractId);
        return count;
    }

    /**
     * Repair unmapped pricing items by trying to link them to MedicalService
     * based on code or name.
     */
    @Transactional
    public int repairUnmappedItems(Long contractId) {
        log.info("Repairing unmapped items for contract: {}", contractId);

        List<ProviderContractPricingItem> unmappedItems = pricingRepository.findAllUnmappedInContract(contractId);
        int fixedCount = 0;

        for (ProviderContractPricingItem item : unmappedItems) {
            MedicalService service = null;

            // Try lookup by Code
            if (item.getServiceCode() != null && !item.getServiceCode().isEmpty()) {
                service = medicalServiceRepository.findByCode(item.getServiceCode()).orElse(null);
            }

            // Try lookup by Name
            if (service == null && item.getServiceName() != null) {
                // Try exact Arabic name match first
                service = medicalServiceRepository.findFirstByName(item.getServiceName()).orElse(null);
            }

            if (service != null) {
                item.setMedicalService(service);
                pricingRepository.save(item);
                fixedCount++;
            }
        }

        log.info("Repaired {} items for contract: {}", fixedCount, contractId);
        return fixedCount;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CATEGORY AND SERVICE LOOKUPS BY PROVIDER (for claims/preauth creation)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get distinct categories available in active contracts for a provider
     * Used when creating claims/preauth to show only contracted categories
     */
    @Transactional(readOnly = true)
    public List<ContractCategoryDto> findCategoriesByProvider(Long providerId) {
        log.debug("Finding contracted categories for provider: {}", providerId);

        var pricingItems = pricingRepository.findAllServicesByProvider(providerId);
        Map<Long, MedicalCategory> categoryMap = buildCategoryMap(pricingItems);

        return categoryMap.values().stream()
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(MedicalCategory::getId, cat -> cat, (left, right) -> left, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(MedicalCategory::getId))
                .map(cat -> ContractCategoryDto.builder()
                        .id(cat.getId())
                        .code(cat.getCode())
                        .name(cat.getName())
                        .parentId(cat.getParentId())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get services available in active contracts for a provider filtered by
     * category
     * Used when creating claims/preauth to show only contracted services
     */
    @Transactional(readOnly = true)
    public List<ContractServiceDto> findServicesByProviderAndCategory(Long providerId, Long categoryId) {
        log.debug("Finding contracted services for provider: {}, category: {}", providerId, categoryId);

        var pricingItems = pricingRepository.findAllServicesByProvider(providerId);
        Map<Long, MedicalCategory> categoryMap = buildCategoryMap(pricingItems);

        return pricingItems.stream()
                .filter(p -> p.getMedicalService() != null)
                .filter(p -> {
                    MedicalCategory resolvedCategory = categoryMap.get(p.getMedicalService().getId());
                    return resolvedCategory != null && Objects.equals(resolvedCategory.getId(), categoryId);
                })
                .map(p -> ContractServiceDto.builder()
                        .id(p.getMedicalService().getId())
                        .code(p.getMedicalService().getCode())
                        .name(p.getMedicalService().getName())
                        .categoryId(categoryId)
                        .categoryName(categoryMap.get(p.getMedicalService().getId()).getName())
                        .contractPrice(p.getContractPrice())
                        .basePrice(p.getBasePrice())
                        .discountPercent(p.getDiscountPercent())
                        .requiresPreAuth(p.getMedicalService().isRequiresPA())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get all services available in active contracts for a provider
     */
    @Transactional(readOnly = true)
    public List<ContractServiceDto> findAllServicesByProvider(Long providerId) {
        log.debug("Finding all contracted services for provider: {}", providerId);

        var pricingItems = pricingRepository.findAllServicesByProvider(providerId);
        Map<Long, MedicalCategory> categoryMap = buildCategoryMap(pricingItems);

        return pricingItems.stream()
                .filter(p -> p.getMedicalService() != null)
                .map(p -> ContractServiceDto.builder()
                        .id(p.getMedicalService().getId())
                        .code(p.getMedicalService().getCode())
                        .name(p.getMedicalService().getName())
                        .categoryId(categoryMap.get(p.getMedicalService().getId()) != null
                                ? categoryMap.get(p.getMedicalService().getId()).getId()
                                : null)
                        .categoryName(categoryMap.get(p.getMedicalService().getId()) != null
                                ? categoryMap.get(p.getMedicalService().getId()).getName()
                                : p.getCategoryName())
                        .contractPrice(p.getContractPrice())
                        .basePrice(p.getBasePrice())
                        .discountPercent(p.getDiscountPercent())
                        .requiresPreAuth(p.getMedicalService().isRequiresPA())
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Verify contract exists and is active
     */
    private void verifyContractExists(Long contractId) {
        if (!contractRepository.existsByIdAndActiveTrue(contractId)) {
            throw new BusinessRuleException("Provider contract not found: " + contractId);
        }
    }

    private String resolveCategoryNameForService(MedicalService service) {
        if (service == null || service.getId() == null) {
            return null;
        }

        Optional<MedicalServiceCategory> mapped = medicalServiceCategoryRepository
                .findFirstByServiceIdAndActiveTrueOrderByIsPrimaryDescIdAsc(service.getId());

        if (mapped.isPresent()) {
            return medicalCategoryRepository.findById(mapped.get().getCategoryId())
                    .map(MedicalCategory::getName)
                    .orElse(null);
        }

        if (service.getCategoryId() != null) {
            return medicalCategoryRepository.findById(service.getCategoryId())
                    .map(MedicalCategory::getName)
                    .orElse(null);
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INNER DTOs (Consider moving to dto package if needed elsewhere)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * DTO for pricing statistics
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PricingStatsDto {
        private long totalItems;
        private BigDecimal averageDiscountPercent;
        private BigDecimal totalSavings;
        private BigDecimal totalStandardPrice;
        private BigDecimal totalContractedPrice;
    }

    /**
     * DTO for contracted categories (used in claims/preauth creation)
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContractCategoryDto {
        private Long id;
        private String code;
        private String name;
        private Long parentId;
    }

    /**
     * DTO for contracted services with pricing info (used in claims/preauth
     * creation)
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContractServiceDto {
        private Long id;
        private String code;
        private String name;
        private Long categoryId;
        private String categoryName;
        private BigDecimal contractPrice;
        private BigDecimal basePrice;
        private BigDecimal discountPercent;
        private Boolean requiresPreAuth; // Flag to filter services that require pre-authorization
    }
}
