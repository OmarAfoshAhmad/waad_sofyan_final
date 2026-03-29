package com.waad.tba.modules.settlement.service;

import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.AccountTransaction.TransactionType;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Account Transaction Service
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ ACCOUNT TRANSACTION SERVICE ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ IMMUTABLE AUDIT TRAIL - Transactions are NEVER modified or deleted. ║
 * ║ This service only CREATES transactions and READS them. ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * IMPORTANT: This service is called INTERNALLY by ProviderAccountService.
 * It should NOT be called directly from controllers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTransactionService {

    private final AccountTransactionRepository transactionRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION CREATION (Internal use only - called by ProviderAccountService)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a CREDIT transaction when a claim is approved.
     * NOTE: Must run in the SAME transaction as the balance update (in
     * ProviderAccountService)
     * to preserve atomicity — if saving the record fails, the balance update rolls
     * back too.
     */
    @Transactional
    public AccountTransaction createClaimApprovedCredit(
            ProviderAccount account,
            Long claimId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        // Validate: no duplicate transaction for same claim
        if (transactionRepository.existsByReferenceTypeAndReferenceId(ReferenceType.CLAIM_APPROVAL, claimId)) {
            throw new IllegalStateException(
                    "Transaction already exists for claim " + claimId + ". Cannot credit twice.");
        }

        AccountTransaction transaction = AccountTransaction.createClaimApprovedCredit(
                account.getId(),
                claimId,
                amount,
                balanceBefore,
                userId);

        transaction = transactionRepository.save(transaction);

        log.info("CREDIT transaction created: account={}, claim={}, amount={}, balanceAfter={}",
                account.getId(), claimId, amount, transaction.getBalanceAfter());

        return transaction;
    }

    /**
     * Create a DEBIT transaction when a batch is paid.
     * NOTE: Must run in the SAME transaction as the balance update to preserve
     * atomicity.
     */
    @Transactional
    public AccountTransaction createBatchPaidDebit(
            ProviderAccount account,
            Long batchId,
            String batchNumber,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        // Validate: no duplicate transaction for same batch
        if (transactionRepository.existsByReferenceTypeAndReferenceId(ReferenceType.SETTLEMENT_PAYMENT, batchId)) {
            throw new IllegalStateException(
                    "Transaction already exists for batch " + batchId + ". Cannot debit twice.");
        }

        AccountTransaction transaction = AccountTransaction.createBatchPaidDebit(
                account.getId(),
                batchId,
                batchNumber,
                amount,
                balanceBefore,
                userId);

        transaction = transactionRepository.save(transaction);

        log.info("DEBIT transaction created: account={}, batch={}, amount={}, balanceAfter={}",
                account.getId(), batchId, amount, transaction.getBalanceAfter());

        return transaction;
    }

    /**
     * Create a DEBIT transaction when a claim is individually settled (not via
     * batch).
     * Idempotent: throws if a CLAIM_SETTLEMENT tx already exists for this claim.
     */
    @Transactional
    public AccountTransaction createClaimSettlementDebit(
            ProviderAccount account,
            Long claimId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        if (transactionRepository.existsByReferenceTypeAndReferenceId(ReferenceType.CLAIM_SETTLEMENT, claimId)) {
            throw new IllegalStateException(
                    "Transaction already exists for settlement of claim " + claimId + ". Cannot debit twice.");
        }

        AccountTransaction transaction = AccountTransaction.createClaimSettlementDebit(
                account.getId(),
                claimId,
                amount,
                balanceBefore,
                userId);

        transaction = transactionRepository.save(transaction);

        log.info("CLAIM SETTLEMENT DEBIT transaction created: account={}, claim={}, amount={}, balanceAfter={}",
                account.getId(), claimId, amount, transaction.getBalanceAfter());

        return transaction;
    }

    /**
     * Create an ADJUSTMENT transaction (manual correction).
     */
    @Transactional
    public AccountTransaction createAdjustment(
            ProviderAccount account,
            BigDecimal amount,
            boolean isCredit,
            BigDecimal balanceBefore,
            String reason,
            Long userId) {

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Adjustment transactions require a reason");
        }

        AccountTransaction transaction = AccountTransaction.createAdjustment(
                account.getId(),
                amount,
                isCredit,
                balanceBefore,
                reason,
                userId);

        transaction = transactionRepository.save(transaction);

        log.warn("ADJUSTMENT transaction created: account={}, type={}, amount={}, reason={}",
                account.getId(), isCredit ? "CREDIT" : "DEBIT", amount, reason);

        return transaction;
    }

    /**
     * FIX #12: Create a CLAIM_REVERSAL DEBIT transaction when an approved claim is
     * reversed.
     * Idempotent: throws if a CLAIM_REVERSAL tx already exists for this claimId.
     */
    @Transactional
    public AccountTransaction createClaimReversalDebit(
            ProviderAccount account,
            Long claimId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Long userId) {

        if (transactionRepository.existsByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, claimId)) {
            throw new IllegalStateException(
                    "Reversal transaction already exists for claim " + claimId + ". Cannot reverse twice.");
        }

        AccountTransaction transaction = AccountTransaction.createClaimReversalDebit(
                account.getId(),
                claimId,
                amount,
                balanceBefore,
                userId);

        transaction = transactionRepository.save(transaction);

        log.info("CLAIM REVERSAL DEBIT transaction created: account={}, claim={}, amount={}, balanceAfter={}",
                account.getId(), claimId, amount, transaction.getBalanceAfter());

        return transaction;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AccountTransaction> getTransactionsByAccount(Long providerAccountId) {
        return transactionRepository.findByProviderAccountIdOrderByCreatedAtDesc(providerAccountId);
    }

    @Transactional(readOnly = true)
    public Page<AccountTransaction> getTransactionsByAccount(Long providerAccountId, Pageable pageable) {
        return transactionRepository.findByProviderAccountIdOrderByCreatedAtDesc(providerAccountId, pageable);
    }

    @Transactional(readOnly = true)
    public List<AccountTransaction> getTransactionsInDateRange(
            Long providerAccountId,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        return transactionRepository.findByAccountAndDateRange(providerAccountId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public Page<AccountTransaction> getTransactionsWithFilters(
            Long providerAccountId,
            TransactionType transactionType,
            ReferenceType referenceType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return transactionRepository.findWithFilters(
                providerAccountId, transactionType, referenceType, startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public AccountTransaction findByReference(ReferenceType referenceType, Long referenceId) {
        return transactionRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean existsForReference(ReferenceType referenceType, Long referenceId) {
        return transactionRepository.existsByReferenceTypeAndReferenceId(referenceType, referenceId);
    }

    @Transactional(readOnly = true)
    public long countForReference(ReferenceType referenceType, Long referenceId) {
        return transactionRepository.countByReferenceTypeAndReferenceId(referenceType, referenceId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGREGATION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public BigDecimal getTotalCredits(Long providerAccountId) {
        return transactionRepository.getTotalCredits(providerAccountId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalDebits(Long providerAccountId) {
        return transactionRepository.getTotalDebits(providerAccountId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCalculatedBalance(Long providerAccountId) {
        return transactionRepository.getCalculatedBalance(providerAccountId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalanceAsOf(Long providerAccountId, LocalDateTime asOfDate) {
        return transactionRepository.getBalanceAsOf(providerAccountId, asOfDate);
    }

    @Transactional(readOnly = true)
    public long countTransactions(Long providerAccountId) {
        return transactionRepository.countByProviderAccountId(providerAccountId);
    }
}
