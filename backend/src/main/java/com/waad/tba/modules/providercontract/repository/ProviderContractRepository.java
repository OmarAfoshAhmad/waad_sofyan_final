package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Provider Contract entity (New standalone module).
 * 
 * Provides:
 * - Standard CRUD operations
 * - Search by provider, status, code
 * - Active contract lookups
 * - Date range queries
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Repository("providerContractModuleRepository")
public interface ProviderContractRepository extends JpaRepository<ProviderContract, Long> {

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND BY CODE / NUMBER
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find contract by unique contract code
        */
       Optional<ProviderContract> findByContractCodeAndActiveTrue(String contractCode);

       /**
        * Find contract by legacy contract number
        */
       Optional<ProviderContract> findByContractNumberAndActiveTrue(String contractNumber);

       /**
        * Check if contract code exists
        */
       boolean existsByContractCode(String contractCode);

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND BY PROVIDER
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all contracts for a provider
        */
       List<ProviderContract> findByProviderIdAndActiveTrue(Long providerId);

       /**
        * Find all contracts for a provider (paginated)
        */
       Page<ProviderContract> findByProviderIdAndActiveTrue(Long providerId, Pageable pageable);

       /**
        * Find active contract for a provider (only one should exist)
        */
       @Query("SELECT c FROM ModernProviderContract c " +
                     "WHERE c.provider.id = :providerId " +
                     "AND c.status = 'ACTIVE' " +
                     "AND c.active = true")
       Optional<ProviderContract> findActiveContractByProvider(@Param("providerId") Long providerId);

       /**
        * Return distinct provider IDs that have at least one currently ACTIVE
        * contract.
        * Used by Provider Mapping Center to show only eligible providers.
        */
       @Query("SELECT DISTINCT c.provider.id FROM ModernProviderContract c " +
                     "WHERE c.status = 'ACTIVE' AND c.active = true")
       List<Long> findDistinctProviderIdsWithActiveContracts();

       /**
        * Find contracts by provider and status
        */
       List<ProviderContract> findByProviderIdAndStatusAndActiveTrue(Long providerId, ContractStatus status);

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND BY STATUS
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all contracts with a specific status
        */
       List<ProviderContract> findByStatusAndActiveTrue(ContractStatus status);

       /**
        * Find all contracts with a specific status (paginated)
        */
       Page<ProviderContract> findByStatusAndActiveTrue(ContractStatus status, Pageable pageable);

       /**
        * Count contracts by status
        */
       long countByStatusAndActiveTrue(ContractStatus status);

       // ═══════════════════════════════════════════════════════════════════════════
       // FIND ALL ACTIVE
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all active (not soft-deleted) contracts
        */
       List<ProviderContract> findByActiveTrue();

       /**
        * Find all active (not soft-deleted) contracts (paginated)
        */
       Page<ProviderContract> findByActiveTrue(Pageable pageable);

       /**
        * Check if contract exists and is active
        */
       boolean existsByIdAndActiveTrue(Long id);

       // ═══════════════════════════════════════════════════════════════════════════
       // DATE-BASED QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find contracts effective on a specific date
        */
       @Query("SELECT c FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "AND c.startDate <= :date " +
                     "AND (c.endDate IS NULL OR c.endDate >= :date)")
       List<ProviderContract> findEffectiveOnDate(@Param("date") LocalDate date);

       /**
        * Find contracts expiring within N days
        */
       @Query("SELECT c FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "AND c.status = 'ACTIVE' " +
                     "AND c.endDate IS NOT NULL " +
                     "AND c.endDate BETWEEN :startDate AND :endDate")
       List<ProviderContract> findExpiringBetween(
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

       /**
        * Find expired contracts that are still marked as ACTIVE
        */
       @Query("SELECT c FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "AND c.status = 'ACTIVE' " +
                     "AND c.endDate IS NOT NULL " +
                     "AND c.endDate < :today")
       List<ProviderContract> findExpiredButStillActive(@Param("today") LocalDate today);

       /**
        * Check for overlapping contracts for same provider
        */
       @Query("SELECT COUNT(c) > 0 FROM ModernProviderContract c " +
                     "WHERE c.provider.id = :providerId " +
                     "AND c.id != :excludeId " +
                     "AND c.active = true " +
                     "AND c.status IN ('ACTIVE', 'SUSPENDED') " +
                     "AND c.startDate <= :endDate " +
                     "AND (c.endDate IS NULL OR c.endDate >= :startDate)")
       boolean hasOverlappingContract(
                     @Param("providerId") Long providerId,
                     @Param("excludeId") Long excludeId,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

       // ═══════════════════════════════════════════════════════════════════════════
       // SEARCH QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Search contracts by provider name or contract code
        */
       @Query("SELECT c FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "AND (LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
                     "     OR LOWER(c.provider.name) LIKE LOWER(CONCAT('%', :search, '%')))")
       Page<ProviderContract> searchByCodeOrProviderName(@Param("search") String search, Pageable pageable);

       /**
        * Search with status filter
        */
       @Query("SELECT c FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "AND (:status IS NULL OR c.status = :status) " +
                     "AND (LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
                     "     OR LOWER(c.provider.name) LIKE LOWER(CONCAT('%', :search, '%')))")
       Page<ProviderContract> searchByCodeOrProviderNameWithStatus(
                     @Param("search") String search,
                     @Param("status") ContractStatus status,
                     Pageable pageable);

       // ═══════════════════════════════════════════════════════════════════════════
       // STATISTICS QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Get total contract value by status
        */
       @Query("SELECT COALESCE(SUM(c.totalValue), 0) FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "AND c.status = :status")
       java.math.BigDecimal getTotalValueByStatus(@Param("status") ContractStatus status);

       /**
        * Count active contracts
        */
       long countByActiveTrue();

       /**
        * Get recent contracts (for dashboard recent activities)
        * Returns: [id, contractCode, provider.name, status, createdAt]
        */
       @Query("SELECT c.id, " +
                     "c.contractCode, " +
                     "c.provider.name as providerName, " +
                     "c.status, " +
                     "c.createdAt " +
                     "FROM ModernProviderContract c " +
                     "WHERE c.active = true " +
                     "ORDER BY c.createdAt DESC")
       List<Object[]> getRecentContracts(Pageable pageable);
}
