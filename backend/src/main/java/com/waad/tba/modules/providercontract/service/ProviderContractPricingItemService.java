package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
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
    private final MedicalCategoryRepository medicalCategoryRepository;

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
     * Build a map of pricingItem.medicalCategory.id -> MedicalCategory.
     * Since V229, all pricing items MUST have medical_category_id (NOT NULL).
     * The old junction-table + MedicalService FK path is removed.
     */
    private Map<Long, MedicalCategory> buildCategoryMap(List<ProviderContractPricingItem> items) {
        Set<Long> categoryIds = items.stream()
                .filter(i -> i.getMedicalCategory() != null)
                .map(i -> i.getMedicalCategory().getId())
                .collect(Collectors.toSet());

        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return medicalCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(MedicalCategory::getId, cat -> cat));
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
    public Page<ProviderContractPricingItemResponseDto> searchInContract(
            Long contractId, String query, Long categoryId, Pageable pageable) {
        log.debug("Searching pricing in contract {}: query={}, categoryId={}", contractId, query, categoryId);

        verifyContractExists(contractId);

        if ((query == null || query.isBlank()) && categoryId == null) {
            return findByContract(contractId, pageable);
        }

        return pricingRepository.searchByServiceCodeOrNameAndCategory(contractId, query, categoryId, pageable)
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

        // Resolve medical category (required since V229)
        MedicalCategory medicalCategory = null;
        if (dto.getMedicalCategoryId() != null) {
            medicalCategory = medicalCategoryRepository.findById(dto.getMedicalCategoryId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Medical category not found: " + dto.getMedicalCategoryId()));
        }

        // Prevent duplicate service code within the same contract
        if (dto.getServiceCode() != null && !dto.getServiceCode().isBlank()
                && pricingRepository.existsByContractIdAndServiceCodeAndActiveTrue(contractId, dto.getServiceCode())) {
            throw new BusinessRuleException(
                    "Pricing already exists for service code '" + dto.getServiceCode()
                            + "' in this contract. Update instead.");
        }

        // Validate prices
        BigDecimal basePrice = dto.getBasePrice() != null ? dto.getBasePrice() : BigDecimal.ZERO;
        BigDecimal contractPrice = dto.getContractPrice();
        if (contractPrice == null || contractPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Contract price must be greater than zero");
        }

        // Build entity (MedicalService FK removed in V229)
        ProviderContractPricingItem item = ProviderContractPricingItem.builder()
                .contract(contract)
                .medicalCategory(medicalCategory)
                .categoryName(medicalCategory != null ? medicalCategory.getName() : null)
                .serviceCode(dto.getServiceCode())
                .serviceName(dto.getServiceName())
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

        // Disallow only for TERMINATED/EXPIRED contracts
        if (contract.getStatus() == ContractStatus.TERMINATED || contract.getStatus() == ContractStatus.EXPIRED) {
            throw new BusinessRuleException("لا يمكن مسح بنود التسعير لعقد منتهٍ أو ملغى");
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
     * No-op since V229: MedicalService catalog was removed.
     * All items are now directly linked to MedicalCategory.
     */
    @Transactional
    public int repairUnmappedItems(Long contractId) {
        log.info("repairUnmappedItems: no-op after V229 migration (MedicalService catalog removed)");
        return 0;
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

        return pricingItems.stream()
                .filter(p -> p.getMedicalCategory() != null
                        && Objects.equals(p.getMedicalCategory().getId(), categoryId))
                .map(p -> ContractServiceDto.builder()
                        .id(p.getId())
                        .pricingItemId(p.getId())
                        .code(p.getServiceCode())
                        .name(p.getServiceName())
                        .categoryId(categoryId)
                        .categoryName(p.getMedicalCategory().getName())
                        .contractPrice(p.getContractPrice())
                        .basePrice(p.getBasePrice())
                        .discountPercent(p.getDiscountPercent())
                        .requiresPreAuth(false)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get all services (mapped AND unmapped) available in active contracts for a
     * provider.
     * For unmapped items without a MedicalCategory FK, we resolve categoryId from
     * the categoryName text.
     */
    @Transactional(readOnly = true)
    public List<ContractServiceDto> findAllServicesByProvider(Long providerId) {
        log.debug("Finding all contracted services for provider: {}", providerId);

        var pricingItems = pricingRepository.findAllServicesByProvider(providerId);

        // Cache for resolving categoryName text → categoryId (avoids N+1 for items
        // without a FK)
        Map<String, Long> categoryNameToIdCache = new HashMap<>();

        return pricingItems.stream()
                .map(p -> {
                    Long resolvedCategoryId = p.getMedicalCategory() != null ? p.getMedicalCategory().getId() : null;
                    String resolvedCategoryName = p.getMedicalCategory() != null ? p.getMedicalCategory().getName()
                            : null;

                    // Fall back to categoryName text resolution if no FK
                    if (resolvedCategoryId == null && p.getCategoryName() != null && !p.getCategoryName().isBlank()) {
                        String catName = p.getCategoryName().trim();
                        resolvedCategoryId = categoryNameToIdCache.computeIfAbsent(catName,
                                this::resolveCategoryIdByName);
                        resolvedCategoryName = catName;
                    }

                    return ContractServiceDto.builder()
                            .id(p.getId())
                            .medicalServiceId(null) // deprecated — FK removed in V229
                            .pricingItemId(p.getId())
                            .code(p.getServiceCode())
                            .name(p.getServiceName())
                            .categoryId(resolvedCategoryId)
                            .categoryName(resolvedCategoryName)
                            .contractPrice(p.getContractPrice())
                            .basePrice(p.getBasePrice())
                            .discountPercent(p.getDiscountPercent())
                            .requiresPreAuth(false)
                            .mapped(p.getMedicalCategory() != null)
                            .build();
                })
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

    /**
     * Resolve a MedicalCategory ID from a freetext category name (imported data).
     * Uses a 3-step fuzzy matching approach to handle name variations:
     * 1. Exact match
     * 2. Strip parenthetical suffixes like "(IP)", "(OP)" then exact match
     * 3. LIKE (contains) search on the stripped name
     */
    private Long resolveCategoryIdByName(String rawName) {
        if (rawName == null || rawName.isBlank())
            return null;
        String name = rawName.trim();

        // Step 1: exact match
        Optional<MedicalCategory> found = medicalCategoryRepository.findFirstByName(name);
        if (found.isPresent())
            return found.get().getId();

        // Step 2: strip parenthetical suffix like " (IP)" or " (OP)" then exact
        String stripped = name.replaceAll("\\s*\\(.*?\\)\\s*$", "").trim();
        if (!stripped.equals(name)) {
            found = medicalCategoryRepository.findFirstByName(stripped);
            if (found.isPresent())
                return found.get().getId();
        }

        // Step 3: LIKE search (contains) on the stripped name
        if (!stripped.isBlank()) {
            List<MedicalCategory> candidates = medicalCategoryRepository.searchByName(stripped);
            if (!candidates.isEmpty())
                return candidates.get(0).getId();
        }

        // Step 4: LIKE search on full original name
        List<MedicalCategory> candidates = medicalCategoryRepository.searchByName(name);
        if (!candidates.isEmpty())
            return candidates.get(0).getId();

        log.debug("Could not resolve category ID for name: '{}'", rawName);
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
        private Long medicalServiceId;
        private Long pricingItemId;
        private String code;
        private String name;
        private Long categoryId;
        private String categoryName;
        private BigDecimal contractPrice;
        private BigDecimal basePrice;
        private BigDecimal discountPercent;
        private Boolean requiresPreAuth;
        private Boolean mapped;
    }
}
