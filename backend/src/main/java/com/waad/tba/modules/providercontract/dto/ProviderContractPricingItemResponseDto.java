package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for returning Provider Contract Pricing Item data in API responses.
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderContractPricingItemResponseDto {

    private Long id;
    private Long contractId;

    // Service info (from medicalService relation)
    private ServiceSummaryDto medicalService;

    // Service name (for imported items without medical service link)
    private String serviceName;

    // Service code (for imported items - reference/lookup)
    private String serviceCode;

    // Category name (for imported items - display/grouping)
    private String categoryName;

    // Quantity (for imported items)
    private Integer quantity;

    // Category info (optional override)
    private CategorySummaryDto medicalCategory;

    // Effective category (from item or service)
    private CategorySummaryDto effectiveCategory;

    // Pricing
    private BigDecimal basePrice;
    private BigDecimal contractPrice;
    private BigDecimal discountPercent;
    private BigDecimal savingsAmount;

    // Unit and currency
    private String unit;
    private String currency;

    // Dates
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    // Computed
    private Boolean isCurrentlyEffective;

    // Metadata
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert entity to response DTO
     */
    public static ProviderContractPricingItemResponseDto fromEntity(ProviderContractPricingItem entity) {
        return fromEntity(entity, null);
    }

    /**
     * Convert entity to response DTO with category lookup
     * 
     * @param entity      The pricing item entity
     * @param categoryMap Map of categoryId -> MedicalCategory (for resolving
     *                    service categories)
     */
    public static ProviderContractPricingItemResponseDto fromEntity(
            ProviderContractPricingItem entity,
            Map<Long, MedicalCategory> categoryMap) {
        if (entity == null) {
            return null;
        }

        ServiceSummaryDto serviceDto = null;
        Long serviceId = null;
        // MedicalService FK removed (V229): service info comes from
        // serviceName/serviceCode fields

        CategorySummaryDto categoryDto = null;
        if (entity.getMedicalCategory() != null) {
            categoryDto = CategorySummaryDto.builder()
                    .id(entity.getMedicalCategory().getId())
                    .code(entity.getMedicalCategory().getCode())
                    .name(entity.getMedicalCategory().getName())
                    .nameAr(entity.getMedicalCategory().getNameAr())
                    .build();
        }

        // Determine effective category:
        // 1. Item's medicalCategory (override)
        // 2. Service's category (from categoryMap lookup)
        // 3. Item's categoryName field (for imported items)
        CategorySummaryDto effectiveCategoryDto = null;
        String effectiveCategoryName = entity.getCategoryName(); // Fallback for imported items

        if (entity.getMedicalCategory() != null) {
            // Use item's override category
            effectiveCategoryDto = categoryDto;
            effectiveCategoryName = entity.getMedicalCategory().getNameAr() != null
                    ? entity.getMedicalCategory().getNameAr()
                    : entity.getMedicalCategory().getName();
        }

        // Get display name from serviceName field (MedicalService FK removed in V229)
        String displayServiceName = entity.getServiceName();

        return ProviderContractPricingItemResponseDto.builder()
                .id(entity.getId())
                .contractId(entity.getContract() != null ? entity.getContract().getId() : null)
                .medicalService(serviceDto)
                .serviceName(displayServiceName)
                .serviceCode(entity.getServiceCode())
                .categoryName(effectiveCategoryName)
                .quantity(entity.getQuantity())
                .medicalCategory(categoryDto)
                .effectiveCategory(effectiveCategoryDto)
                .basePrice(entity.getBasePrice())
                .contractPrice(entity.getContractPrice())
                .discountPercent(entity.getDiscountPercent())
                .savingsAmount(entity.getSavingsAmount())
                .unit(entity.getUnit())
                .currency(entity.getCurrency())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .isCurrentlyEffective(entity.isCurrentlyEffective())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Service Summary DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSummaryDto {
        private Long id;
        private String code;
        private String name;
        private String nameAr;
        private String nameEn;
    }

    /**
     * Category Summary DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummaryDto {
        private Long id;
        private String code;
        private String name;
        private String nameAr;
    }
}
