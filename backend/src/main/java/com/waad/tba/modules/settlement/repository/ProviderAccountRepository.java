package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderAccount.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Provider Accounts
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ PROVIDER ACCOUNT REPOSITORY ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ IMPORTANT: Use findByIdForUpdate() for ALL financial operations ║
 * ║ to prevent race conditions with concurrent balance modifications. ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Repository
public interface ProviderAccountRepository extends JpaRepository<ProviderAccount, Long> {

       // ═══════════════════════════════════════════════════════════════════════════
       // LOCKING QUERIES (for financial operations)
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find account by ID with pessimistic lock for financial updates
        * MANDATORY for credit/debit operations to prevent race conditions
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.id = :id")
       Optional<ProviderAccount> findByIdForUpdate(@Param("id") Long id);

       /**
        * Find account by provider ID with pessimistic lock
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.providerId = :providerId")
       Optional<ProviderAccount> findByProviderIdForUpdate(@Param("providerId") Long providerId);

       // ═══════════════════════════════════════════════════════════════════════════
       // BASIC QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find account by provider ID
        */
       Optional<ProviderAccount> findByProviderId(Long providerId);

       /**
        * Check if account exists for provider
        */
       boolean existsByProviderId(Long providerId);

       /**
        * Find all accounts by status
        */
       List<ProviderAccount> findByStatus(AccountStatus status);

       /**
        * Find all active accounts with pagination
        */
       Page<ProviderAccount> findByStatus(AccountStatus status, Pageable pageable);

       // ═══════════════════════════════════════════════════════════════════════════
       // BALANCE QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find accounts with outstanding balance (balance > 0)
        */
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.runningBalance > 0 AND pa.status = :status")
       List<ProviderAccount> findWithOutstandingBalance(@Param("status") AccountStatus status);

       /**
        * Find accounts with outstanding balance regardless of status
        */
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.runningBalance > 0")
       List<ProviderAccount> findAllWithOutstandingBalance();

       /**
        * Find accounts with outstanding balance with pagination
        */
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.runningBalance > 0 AND pa.status = :status")
       Page<ProviderAccount> findWithOutstandingBalance(@Param("status") AccountStatus status, Pageable pageable);

       /**
        * Find accounts with balance greater than threshold
        */
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.runningBalance >= :minBalance AND pa.status = 'ACTIVE'")
       List<ProviderAccount> findByMinBalance(@Param("minBalance") BigDecimal minBalance);

       // ═══════════════════════════════════════════════════════════════════════════
       // AGGREGATION QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Get total outstanding balance across all active accounts
        */
       @Query("SELECT COALESCE(SUM(pa.runningBalance), 0) FROM ProviderAccount pa WHERE pa.status = 'ACTIVE'")
       BigDecimal getTotalOutstandingBalance();

       /**
        * Get total approved amount across all accounts
        */
       @Query("SELECT COALESCE(SUM(pa.totalApproved), 0) FROM ProviderAccount pa")
       BigDecimal getTotalApprovedAmount();

       /**
        * Get total paid amount across all accounts
        */
       @Query("SELECT COALESCE(SUM(pa.totalPaid), 0) FROM ProviderAccount pa")
       BigDecimal getTotalPaidAmount();

       /**
        * Count accounts by status
        */
       long countByStatus(AccountStatus status);

       /**
        * Count accounts with outstanding balance
        */
       @Query("SELECT COUNT(pa) FROM ProviderAccount pa WHERE pa.runningBalance > 0 AND pa.status = 'ACTIVE'")
       long countWithOutstandingBalance();

       // ═══════════════════════════════════════════════════════════════════════════
       // SEARCH QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find accounts by provider IDs
        */
       @Query("SELECT pa FROM ProviderAccount pa WHERE pa.providerId IN :providerIds")
       List<ProviderAccount> findByProviderIds(@Param("providerIds") List<Long> providerIds);

       /**
        * Search accounts with filters
        */
       @Query("SELECT pa FROM ProviderAccount pa WHERE " +
                     "(:status IS NULL OR pa.status = :status) " +
                     "AND (:hasBalance IS NULL OR (:hasBalance = true AND pa.runningBalance > 0) OR (:hasBalance = false AND pa.runningBalance = 0))")
       Page<ProviderAccount> findWithFilters(
                     @Param("status") AccountStatus status,
                     @Param("hasBalance") Boolean hasBalance,
                     Pageable pageable);
}
