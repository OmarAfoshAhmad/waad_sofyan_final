package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Provider Contract Pricing Item entity.
 * 
 * Provides:
 * - CRUD operations for pricing items
 * - Queries by contract, service, category
 * - Effective pricing lookups
 * - Price comparison queries
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Repository
public interface ProviderContractPricingItemRepository extends JpaRepository<ProviderContractPricingItem, Long> {

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND BY CONTRACT
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all pricing items for a contract
        */
       List<ProviderContractPricingItem> findByContractIdAndActiveTrue(Long contractId);

       /**
        * Find all pricing items for a contract (paginated)
        */
       Page<ProviderContractPricingItem> findByContractIdAndActiveTrue(Long contractId, Pageable pageable);

       /**
        * Count pricing items for a contract
        */
       long countByContractIdAndActiveTrue(Long contractId);

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND BY SERVICE
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find pricing items by service code (replaces old medicalServiceId lookup)
        */
       List<ProviderContractPricingItem> findByServiceCodeAndActiveTrue(String serviceCode);

       /**
        * Find active pricing item by service name in a contract (case-insensitive).
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true " +
                     "AND LOWER(p.serviceName) = LOWER(:serviceName)")
       Optional<ProviderContractPricingItem> findActiveUnmappedByContractAndServiceName(
                     @Param("contractId") Long contractId,
                     @Param("serviceName") String serviceName);

       /**
        * Find active pricing item by service code in a contract.
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true " +
                     "AND p.serviceCode = :serviceCode")
       Optional<ProviderContractPricingItem> findActiveUnmappedByContractAndServiceCode(
                     @Param("contractId") Long contractId,
                     @Param("serviceCode") String serviceCode);

       /** Find active pricing item by service code (any mapping status). */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId AND p.active = true AND p.serviceCode = :serviceCode")
       Optional<ProviderContractPricingItem> findByContractIdAndServiceCodeActiveTrue(
                     @Param("contractId") Long contractId,
                     @Param("serviceCode") String serviceCode);

       /**
        * Find active pricing item by service name (any mapping status). Uses native
        * SQL with explicit CAST to avoid bytea type inference.
        */
       @Query(value = "SELECT * FROM provider_contract_pricing_items WHERE contract_id = :contractId AND active = true AND lower(service_name::text) = lower(cast(:serviceName as text))", nativeQuery = true)
       Optional<ProviderContractPricingItem> findByContractIdAndServiceNameActiveTrue(
                     @Param("contractId") Long contractId,
                     @Param("serviceName") String serviceName);

       /**
        * Check if pricing exists for a service code in a contract (replaces
        * medicalServiceId check)
        */
       boolean existsByContractIdAndServiceCodeAndActiveTrue(Long contractId, String serviceCode);

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND BY CATEGORY
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find pricing items by category within a contract
        */
       List<ProviderContractPricingItem> findByContractIdAndMedicalCategoryIdAndActiveTrue(
                     Long contractId, Long categoryId);

       /**
        * Find pricing items by medical category within a contract
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true " +
                     "AND p.medicalCategory.id = :categoryId")
       List<ProviderContractPricingItem> findByContractIdAndServiceCategoryId(
                     @Param("contractId") Long contractId,
                     @Param("categoryId") Long categoryId);

       // ═══════════════════════════════════════════════════════════════════════════
       // EFFECTIVE PRICING LOOKUPS
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find effective pricing for a pricing item at a provider on a specific date
        * (by pricingItemId).
        * Use findEffectivePricingByCode for service-code based lookups.
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.provider.id = :providerId " +
                     "AND p.id = :pricingItemId " +
                     "AND p.active = true " +
                     "AND p.contract.active = true " +
                     "AND p.contract.status = 'ACTIVE' " +
                     "AND p.contract.startDate <= :date " +
                     "AND (p.contract.endDate IS NULL OR p.contract.endDate >= :date) " +
                     "AND (p.effectiveFrom IS NULL OR p.effectiveFrom <= :date) " +
                     "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)")
       Optional<ProviderContractPricingItem> findEffectivePricing(
                     @Param("providerId") Long providerId,
                     @Param("pricingItemId") Long pricingItemId,
                     @Param("date") LocalDate date);

       /**
        * Find effective pricing for a service code at a provider on a specific date.
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.provider.id = :providerId " +
                     "AND p.active = true " +
                     "AND p.contract.active = true " +
                     "AND p.contract.status = 'ACTIVE' " +
                     "AND p.serviceCode = :serviceCode " +
                     "AND p.contract.startDate <= :date " +
                     "AND (p.contract.endDate IS NULL OR p.contract.endDate >= :date) " +
                     "AND (p.effectiveFrom IS NULL OR p.effectiveFrom <= :date) " +
                     "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)")
       Optional<ProviderContractPricingItem> findEffectivePricingByCode(
                     @Param("providerId") Long providerId,
                     @Param("serviceCode") String serviceCode,
                     @Param("date") LocalDate date);

       /**
        * Find effective pricing for a pricing item at a provider (today)
        */
       default Optional<ProviderContractPricingItem> findEffectivePricingToday(Long providerId, Long pricingItemId) {
              return findEffectivePricing(providerId, pricingItemId, LocalDate.now());
       }

       // ═══════════════════════════════════════════════════════════════════════════
       // SEARCH QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Search pricing items by service code or name
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "LEFT JOIN p.medicalCategory mc " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true " +
                     "AND (:q IS NULL OR :q = '' " +
                     "     OR LOWER(p.serviceCode) LIKE LOWER(CONCAT('%', :q, '%')) " +
                     "     OR LOWER(p.serviceName) LIKE LOWER(CONCAT('%', :q, '%')) " +
                     "     OR LOWER(p.categoryName) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                     "AND (:categoryId IS NULL OR mc.id = :categoryId)")
       Page<ProviderContractPricingItem> searchByServiceCodeOrNameAndCategory(
                     @Param("contractId") Long contractId,
                     @Param("q") String q,
                     @Param("categoryId") Long categoryId,
                     Pageable pageable);

       // ═══════════════════════════════════════════════════════════════════════════
       // PRICE COMPARISON QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find pricing items with discount above threshold
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true " +
                     "AND p.discountPercent >= :minDiscount")
       List<ProviderContractPricingItem> findByDiscountAbove(
                     @Param("contractId") Long contractId,
                     @Param("minDiscount") BigDecimal minDiscount);

       /**
        * Find pricing items with contract price below threshold
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true " +
                     "AND p.contractPrice <= :maxPrice")
       List<ProviderContractPricingItem> findByContractPriceBelow(
                     @Param("contractId") Long contractId,
                     @Param("maxPrice") BigDecimal maxPrice);

       // ═══════════════════════════════════════════════════════════════════════════
       // STATISTICS QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Calculate average discount for a contract
        */
       @Query("SELECT AVG(p.discountPercent) FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true")
       BigDecimal getAverageDiscount(@Param("contractId") Long contractId);

       /**
        * Calculate total potential savings for a contract
        */
       @Query("SELECT COALESCE(SUM(p.basePrice - p.contractPrice), 0) " +
                     "FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true")
       BigDecimal getTotalSavings(@Param("contractId") Long contractId);

       /**
        * Calculate total base (standard) price for a contract
        */
       @Query("SELECT COALESCE(SUM(p.basePrice), 0) " +
                     "FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true")
       BigDecimal getTotalStandardPrice(@Param("contractId") Long contractId);

       /**
        * Calculate total contracted price for a contract
        */
       @Query("SELECT COALESCE(SUM(p.contractPrice), 0) " +
                     "FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true")
       BigDecimal getTotalContractedPrice(@Param("contractId") Long contractId);

       /**
        * Get min and max prices for a contract
        */
       @Query("SELECT MIN(p.contractPrice), MAX(p.contractPrice) " +
                     "FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.id = :contractId " +
                     "AND p.active = true")
       Object[] getPriceRange(@Param("contractId") Long contractId);

       // ═══════════════════════════════════════════════════════════════════════════
       // BULK OPERATIONS
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Soft delete all pricing items for a contract
        */
       @org.springframework.data.jpa.repository.Modifying
       @Query("UPDATE ProviderContractPricingItem p SET p.active = false " +
                     "WHERE p.contract.id = :contractId")
       int softDeleteByContractId(@Param("contractId") Long contractId);

       /**
        * Hard delete all pricing items for a contract.
        */
       @org.springframework.data.jpa.repository.Modifying
       @Query("DELETE FROM ProviderContractPricingItem p WHERE p.contract.id = :contractId")
       int hardDeleteByContractId(@Param("contractId") Long contractId);

       /**
        * Find all active pricing items in a contract that have no medical category
        * assigned
        */
       @Query("SELECT p FROM ProviderContractPricingItem p WHERE p.contract.id = :contractId AND p.active = true AND p.medicalCategory IS NULL")
       List<ProviderContractPricingItem> findAllUnmappedInContract(@Param("contractId") Long contractId);

       /**
        * Find all effective pricing items for a provider on a specific date
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.provider.id = :providerId " +
                     "AND p.active = true " +
                     "AND p.contract.active = true " +
                     "AND p.contract.status = 'ACTIVE' " +
                     "AND p.contract.startDate <= :date " +
                     "AND (p.contract.endDate IS NULL OR p.contract.endDate >= :date) " +
                     "AND (p.effectiveFrom IS NULL OR p.effectiveFrom <= :date) " +
                     "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)")
       List<ProviderContractPricingItem> findEffectivePricingByProvider(
                     @Param("providerId") Long providerId,
                     @Param("date") LocalDate date);

       // ═══════════════════════════════════════════════════════════════════════════
       // CATEGORY AND SERVICE LOOKUPS BY PROVIDER (for claims/preauth creation)
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Get distinct categories available in active contracts for a provider
        */
       @Query("SELECT DISTINCT p.medicalCategory FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.provider.id = :providerId " +
                     "AND p.active = true " +
                     "AND p.contract.active = true " +
                     "AND p.contract.status = 'ACTIVE' " +
                     "AND p.medicalCategory IS NOT NULL " +
                     "AND p.contract.startDate <= CURRENT_DATE " +
                     "AND (p.contract.endDate IS NULL OR p.contract.endDate >= CURRENT_DATE)")
       List<com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory> findDistinctCategoriesByProvider(
                     @Param("providerId") Long providerId);

       /**
        * Get services available in active contracts for a provider filtered by
        * category
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.provider.id = :providerId " +
                     "AND p.active = true " +
                     "AND p.contract.active = true " +
                     "AND p.contract.status = 'ACTIVE' " +
                     "AND p.medicalCategory.id = :categoryId " +
                     "AND p.contract.startDate <= CURRENT_DATE " +
                     "AND (p.contract.endDate IS NULL OR p.contract.endDate >= CURRENT_DATE)")
       List<ProviderContractPricingItem> findServicesByProviderAndCategory(
                     @Param("providerId") Long providerId,
                     @Param("categoryId") Long categoryId);

       /**
        * Get ALL pricing items (mapped AND unmapped) in active contracts for a
        * provider.
        * This is used for the service dropdown in claim/preauth entry.
        */
       @Query("SELECT p FROM ProviderContractPricingItem p " +
                     "WHERE p.contract.provider.id = :providerId " +
                     "AND p.active = true " +
                     "AND p.contract.active = true " +
                     "AND p.contract.status = 'ACTIVE' " +
                     "AND p.contract.startDate <= CURRENT_DATE " +
                     "AND (p.contract.endDate IS NULL OR p.contract.endDate >= CURRENT_DATE)")
       List<ProviderContractPricingItem> findAllServicesByProvider(
                     @Param("providerId") Long providerId);
}
