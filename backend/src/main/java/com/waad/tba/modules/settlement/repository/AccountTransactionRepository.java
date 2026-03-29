package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.AccountTransaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Account Transactions
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ ACCOUNT TRANSACTION REPOSITORY ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ IMMUTABLE AUDIT TRAIL - Records are never modified or deleted. ║
 * ║ Database trigger enforces immutability at the database level. ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

       // ═══════════════════════════════════════════════════════════════════════════
       // BASIC QUERIES - Chronological Order
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all transactions for an account, ordered by creation date (newest first)
        */
       List<AccountTransaction> findByProviderAccountIdOrderByCreatedAtDesc(Long providerAccountId);

       /**
        * Find all transactions for an account with pagination (newest first)
        */
       Page<AccountTransaction> findByProviderAccountIdOrderByCreatedAtDesc(Long providerAccountId, Pageable pageable);

       /**
        * Find all transactions for an account (oldest first - for balance calculation)
        */
       List<AccountTransaction> findByProviderAccountIdOrderByCreatedAtAsc(Long providerAccountId);

       // ═══════════════════════════════════════════════════════════════════════════
       // REFERENCE-BASED QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find transaction by reference (unique)
        */
       Optional<AccountTransaction> findByReferenceTypeAndReferenceId(
                     ReferenceType referenceType, Long referenceId);

       /**
        * Check if a reference already has a transaction
        */
       boolean existsByReferenceTypeAndReferenceId(ReferenceType referenceType, Long referenceId);

       /**
        * Count transactions for a specific reference (cycle-safe idempotency).
        */
       long countByReferenceTypeAndReferenceId(ReferenceType referenceType, Long referenceId);

       /**
        * Find the most recent transaction for a reference (for multi-cycle reversal).
        */
       Optional<AccountTransaction> findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                     ReferenceType referenceType, Long referenceId);

       /**
        * Find all transactions for a reference type
        */
       List<AccountTransaction> findByReferenceType(ReferenceType referenceType);

       /**
        * Find claim-related transactions for an account
        */
       @Query("SELECT at FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "AND at.referenceType = 'CLAIM_APPROVAL' " +
                     "ORDER BY at.createdAt DESC")
       List<AccountTransaction> findClaimTransactionsByAccount(@Param("accountId") Long accountId);

       /**
        * Find batch payment transactions for an account
        */
       @Query("SELECT at FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "AND at.referenceType = 'SETTLEMENT_PAYMENT' " +
                     "ORDER BY at.createdAt DESC")
       List<AccountTransaction> findBatchPaymentTransactionsByAccount(@Param("accountId") Long accountId);

       // ═══════════════════════════════════════════════════════════════════════════
       // DATE RANGE QUERIES - For Account Statements
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find transactions within date range (for account statements)
        */
       @Query("SELECT at FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "AND at.createdAt >= :startDate AND at.createdAt <= :endDate " +
                     "ORDER BY at.createdAt ASC")
       List<AccountTransaction> findByAccountAndDateRange(
                     @Param("accountId") Long accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Find transactions with pagination within date range
        */
       @Query("SELECT at FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "AND at.createdAt >= :startDate AND at.createdAt <= :endDate " +
                     "ORDER BY at.createdAt DESC")
       Page<AccountTransaction> findByAccountAndDateRange(
                     @Param("accountId") Long accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     Pageable pageable);

       // ═══════════════════════════════════════════════════════════════════════════
       // AGGREGATION QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Get total credits for an account
        */
       @Query("SELECT COALESCE(SUM(at.amount), 0) FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId AND at.transactionType = 'CREDIT'")
       BigDecimal getTotalCredits(@Param("accountId") Long accountId);

       /**
        * Get total debits for an account
        */
       @Query("SELECT COALESCE(SUM(at.amount), 0) FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId AND at.transactionType = 'DEBIT'")
       BigDecimal getTotalDebits(@Param("accountId") Long accountId);

       /**
        * Get calculated running balance (credits - debits)
        */
       @Query("SELECT COALESCE(SUM(CASE WHEN at.transactionType = 'CREDIT' THEN at.amount ELSE -at.amount END), 0) " +
                     "FROM AccountTransaction at WHERE at.providerAccountId = :accountId")
       BigDecimal getCalculatedBalance(@Param("accountId") Long accountId);

       /**
        * Count transactions by type for an account
        */
       @Query("SELECT at.transactionType, COUNT(at) FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "GROUP BY at.transactionType")
       List<Object[]> getTransactionCountByType(@Param("accountId") Long accountId);

       /**
        * Get transaction summary for an account
        * Returns: [creditCount, creditAmount, debitCount, debitAmount]
        */
       @Query("SELECT " +
                     "SUM(CASE WHEN at.transactionType = 'CREDIT' THEN 1 ELSE 0 END), " +
                     "SUM(CASE WHEN at.transactionType = 'CREDIT' THEN at.amount ELSE 0 END), " +
                     "SUM(CASE WHEN at.transactionType = 'DEBIT' THEN 1 ELSE 0 END), " +
                     "SUM(CASE WHEN at.transactionType = 'DEBIT' THEN at.amount ELSE 0 END) " +
                     "FROM AccountTransaction at WHERE at.providerAccountId = :accountId")
       List<Object[]> getAccountTransactionSummary(@Param("accountId") Long accountId);

       // ═══════════════════════════════════════════════════════════════════════════
       // FILTERING QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find transactions by type
        */
       List<AccountTransaction> findByProviderAccountIdAndTransactionTypeOrderByCreatedAtDesc(
                     Long providerAccountId, TransactionType transactionType);

       /**
        * Find transactions by reference type
        */
       List<AccountTransaction> findByProviderAccountIdAndReferenceTypeOrderByCreatedAtDesc(
                     Long providerAccountId, ReferenceType referenceType);

       /**
        * Advanced filter query
        */
       @Query("SELECT at FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "AND (:transactionType IS NULL OR at.transactionType = :transactionType) " +
                     "AND (:referenceType IS NULL OR at.referenceType = :referenceType) " +
                     "AND (:startDate IS NULL OR at.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR at.createdAt <= :endDate) " +
                     "ORDER BY at.createdAt DESC")
       Page<AccountTransaction> findWithFilters(
                     @Param("accountId") Long accountId,
                     @Param("transactionType") TransactionType transactionType,
                     @Param("referenceType") ReferenceType referenceType,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     Pageable pageable);

       // ═══════════════════════════════════════════════════════════════════════════
       // COUNT QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Count all transactions for an account
        */
       long countByProviderAccountId(Long providerAccountId);

       /**
        * Count transactions by type
        */
       long countByProviderAccountIdAndTransactionType(Long providerAccountId, TransactionType transactionType);

       /**
        * Count transactions by reference type
        */
       long countByProviderAccountIdAndReferenceType(Long providerAccountId, ReferenceType referenceType);

       // ═══════════════════════════════════════════════════════════════════════════
       // BALANCE AT DATE - For Historical Reports
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Calculate balance at a specific point in time
        */
       @Query("SELECT COALESCE(SUM(CASE WHEN at.transactionType = 'CREDIT' THEN at.amount ELSE -at.amount END), 0) " +
                     "FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId AND at.createdAt <= :asOfDate")
       BigDecimal getBalanceAsOf(@Param("accountId") Long accountId, @Param("asOfDate") LocalDateTime asOfDate);

       /**
        * Get the balance after the last transaction (running balance snapshot)
        */
       @Query("SELECT at.balanceAfter FROM AccountTransaction at " +
                     "WHERE at.providerAccountId = :accountId " +
                     "ORDER BY at.createdAt DESC LIMIT 1")
       Optional<BigDecimal> getLatestBalance(@Param("accountId") Long accountId);
}
