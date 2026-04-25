package com.waad.tba.modules.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.dto.AccountSummaryDTO;
import com.waad.tba.modules.settlement.dto.ProviderAccountListDTO;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderAccount.AccountStatus;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Account Service
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ PROVIDER ACCOUNT SERVICE ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Central service for managing provider financial accounts. ║
 * ║ ║
 * ║ FINANCIAL INTEGRITY INVARIANT: ║
 * ║ running_balance = total_approved - total_paid ║
 * ║ ║
 * ║ ❌ NEVER modify balance directly - always through transactions ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderAccountService {

        private final ProviderAccountRepository accountRepository;
        private final AccountTransactionRepository transactionRepository;
        private final AccountTransactionService transactionService;
        private final ClaimRepository claimRepository;
        private final ProviderRepository providerRepository;
        private final ProviderContractRepository contractRepository;

        // ═══════════════════════════════════════════════════════════════════════════
        // ACCOUNT MANAGEMENT
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Get or create a provider account.
         * If no account exists for the provider, creates one with zero balance.
         */
        @Transactional
        public ProviderAccount getOrCreateAccount(Long providerId) {
                return accountRepository.findByProviderId(providerId)
                                .orElseGet(() -> {
                                        log.info("Creating new provider account for provider {}", providerId);
                                        ProviderAccount account = ProviderAccount.builder()
                                                        .providerId(providerId)
                                                        .runningBalance(BigDecimal.ZERO)
                                                        .totalApproved(BigDecimal.ZERO)
                                                        .totalPaid(BigDecimal.ZERO)
                                                        .status(AccountStatus.ACTIVE)
                                                        .build();
                                        try {
                                                return accountRepository.save(account);
                                        } catch (DataIntegrityViolationException e) {
                                                // Another thread created the account concurrently — fetch and return it
                                                log.info("Race condition on account creation for provider {} — fetching existing",
                                                                providerId);
                                                return accountRepository.findByProviderId(providerId)
                                                                .orElseThrow(() -> new EntityNotFoundException(
                                                                                "Provider account not found for provider: "
                                                                                                + providerId));
                                        }
                                });
        }

        /**
         * Get account by provider ID (with pessimistic lock for updates)
         */
        @Transactional
        public ProviderAccount getAccountForUpdate(Long providerId) {
                return accountRepository.findByProviderIdForUpdate(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));
        }

        /**
         * Get account by ID (read-only)
         */
        @Transactional(readOnly = true)
        public ProviderAccount getAccountById(Long accountId) {
                return accountRepository.findById(accountId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found: " + accountId));
        }

        /**
         * Get account by provider ID (read-only)
         */
        @Transactional(readOnly = true)
        public ProviderAccount getAccountByProviderId(Long providerId) {
                return accountRepository.findByProviderId(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // CLAIM APPROVAL - CREDIT OPERATION
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Credit the provider account when a claim is approved.
         * 
         * This is the ONLY way to increase a provider's balance.
         * 
         * @param claimId The approved claim ID
         * @param userId  User performing the action
         * @return The created credit transaction
         * @throws IllegalStateException if claim is not APPROVED or already credited
         */
        @Transactional
        public AccountTransaction creditOnClaimApproval(Long claimId, Long userId) {
                // 1. Get claim with exclusive lock to prevent concurrent settlement/re-approval
                // races.
                // Lock order (claim before account) must match createPayment to avoid
                // deadlocks.
                Claim claim = claimRepository.findByIdForUpdate(claimId)
                                .orElseThrow(() -> new EntityNotFoundException("Claim not found: " + claimId));

                // 2. Validate claim status — APPROVED (normal) or NEEDS_CORRECTION
                // (restore-after-delete)
                if (claim.getStatus() != ClaimStatus.APPROVED
                                && claim.getStatus() != ClaimStatus.NEEDS_CORRECTION) {
                        throw new IllegalStateException(
                                        "Cannot credit for claim " + claimId
                                                        + ". Status must be APPROVED or NEEDS_CORRECTION, but is: "
                                                        + claim.getStatus());
                }

                // 3. Cycle-safe idempotency: allow re-credit only when every prior credit
                // has a matching reversal (delete→restore scenario).
                long approvalCount = transactionService.countForReference(ReferenceType.CLAIM_APPROVAL, claimId);
                long reversalCount = transactionService.countForReference(ReferenceType.CLAIM_REVERSAL, claimId);
                if (approvalCount > reversalCount) {
                        throw new IllegalStateException(
                                        "Claim " + claimId
                                                        + " has an active (unreversed) credit. Cannot credit twice.");
                }

                // 4. Get net amount to credit = نصيب المرفق بعد خصم العقد
                BigDecimal companyApprovedShare = claim.getNetPayableAmount();
                if (companyApprovedShare == null || companyApprovedShare.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalStateException(
                                        "Invalid net payable amount for claim " + claimId + ": "
                                                        + companyApprovedShare);
                }

                // Apply provider contract discount (نسبة الخصم) to get actual provider share.
                // A missing active contract is a configuration error — fail loudly.
                BigDecimal discountPercent = contractRepository.findActiveContractByProvider(claim.getProviderId())
                                .map(c -> c.getDiscountPercent() != null ? c.getDiscountPercent() : BigDecimal.ZERO)
                                .orElseThrow(() -> new IllegalStateException(
                                                "لا يوجد عقد نشط لمقدم الخدمة " + claim.getProviderId()
                                                                + ". تعذّر حساب نصيب المرفق — تأكد من إعداد العقد أولاً."));

                // Validate discount is within [0, 100]
                if (discountPercent.compareTo(BigDecimal.ZERO) < 0
                                || discountPercent.compareTo(new BigDecimal("100")) > 0) {
                        throw new IllegalStateException(
                                        "نسبة الخصم غير صالحة لمقدم الخدمة " + claim.getProviderId()
                                                        + ": " + discountPercent + ". يجب أن تكون بين 0 و 100.");
                }

                // One-step calculation with a single HALF_EVEN rounding to avoid cascading
                // precision errors across thousands of claims.
                // CALC_SCALE=4 for intermediate ratio; final amount rounded to scale=2.
                BigDecimal amount;
                if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal providerRatio = BigDecimal.ONE
                                        .subtract(discountPercent.divide(new BigDecimal("100"), 4,
                                                        java.math.RoundingMode.HALF_EVEN));
                        amount = companyApprovedShare.multiply(providerRatio).setScale(2,
                                        java.math.RoundingMode.HALF_EVEN);
                } else {
                        amount = companyApprovedShare;
                }

                log.info("Credit calculation: claim={}, companyApprovedShare={}, discountPercent={}, providerShare={}",
                                claimId, companyApprovedShare, discountPercent, amount);

                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalStateException(
                                        "Invalid credit amount after discount for claim " + claimId + ": " + amount);
                }

                // Persist the discount snapshot on the claim for audit trail.
                // Recorded once – future contract changes will NOT alter this.
                if (claim.getAppliedDiscountPercent() == null) {
                        claim.setAppliedDiscountPercent(discountPercent);
                        claimRepository.save(claim);
                }

                // 5. Get account with lock (create if not exists)
                ProviderAccount account = accountRepository.findByProviderIdForUpdate(claim.getProviderId())
                                .orElseGet(() -> getOrCreateAccount(claim.getProviderId()));

                // 5a. Re-check AFTER acquiring the lock — closes the TOCTOU window.
                long approvalCountLocked = transactionService.countForReference(ReferenceType.CLAIM_APPROVAL, claimId);
                long reversalCountLocked = transactionService.countForReference(ReferenceType.CLAIM_REVERSAL, claimId);
                if (approvalCountLocked > reversalCountLocked) {
                        throw new IllegalStateException(
                                        "Claim " + claimId
                                                        + " has an active credit (concurrent request). Cannot credit twice.");
                }

                // 6. Validate account is active
                if (!account.isActive()) {
                        throw new IllegalStateException(
                                        "Cannot credit inactive account for provider " + claim.getProviderId());
                }

                // 7. Get balance before
                BigDecimal balanceBefore = account.getRunningBalance();

                // 8. Credit the account
                account.credit(amount);
                accountRepository.save(account);

                // 9. Create transaction record
                AccountTransaction transaction = transactionService.createClaimApprovedCredit(
                                account,
                                claimId,
                                amount,
                                balanceBefore,
                                userId);

                log.info("CREDIT SUCCESS: claim={}, provider={}, amount={}, newBalance={}",
                                claimId, claim.getProviderId(), amount, account.getRunningBalance());

                return transaction;
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // BATCH PAYMENT - DEBIT OPERATION
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Debit the provider account when a batch is paid.
         * 
         * This is the ONLY way to decrease a provider's balance.
         * Called internally by SettlementBatchService.
         */
        @Transactional
        public AccountTransaction debitOnBatchPayment(Long accountId, Long batchId, String batchNumber,
                        BigDecimal amount, Long userId) {
                // 1. Get account with lock
                ProviderAccount account = accountRepository.findByIdForUpdate(accountId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found: " + accountId));

                // 2. Validate account is active
                if (!account.isActive()) {
                        throw new IllegalStateException(
                                        "Cannot debit inactive account: " + accountId);
                }

                // 3. Validate sufficient balance
                if (account.getRunningBalance().compareTo(amount) < 0) {
                        throw new IllegalStateException(
                                        "Insufficient balance. Account: " + accountId +
                                                        ", Balance: " + account.getRunningBalance() +
                                                        ", Required: " + amount);
                }

                // 4. Get balance before
                BigDecimal balanceBefore = account.getRunningBalance();

                // 5. Debit the account
                account.debit(amount);
                accountRepository.save(account);

                // 6. Create transaction record
                AccountTransaction transaction = transactionService.createBatchPaidDebit(
                                account,
                                batchId,
                                batchNumber,
                                amount,
                                balanceBefore,
                                userId);

                log.info("DEBIT SUCCESS: batch={}, account={}, amount={}, newBalance={}",
                                batchId, accountId, amount, account.getRunningBalance());

                return transaction;
        }

        /**
         * Debit the provider account for an installment (partial) payment.
         * Validates balance, persists the account, and creates the transaction record.
         *
         * @param providerId Provider ID
         * @param amount     Payment amount (must be <= running balance)
         * @param note       Description / reference note
         * @param userId     User performing the action
         * @return The created debit transaction
         */
        @Transactional
        public AccountTransaction debitOnInstallmentPayment(Long providerId, BigDecimal amount,
                        String note, Long userId) {
                ProviderAccount account = accountRepository.findByProviderIdForUpdate(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));

                if (!account.isActive()) {
                        throw new IllegalStateException(
                                        "Cannot debit inactive account for provider " + providerId);
                }

                BigDecimal balance = account.getRunningBalance();
                if (balance == null) {
                        throw new IllegalStateException(
                                        "CRITICAL: Running balance is null for provider " + providerId
                                                        + ". Possible data corruption — investigate immediately.");
                }
                if (balance.compareTo(amount) < 0) {
                        throw new IllegalStateException(
                                        "Insufficient balance for installment. Provider: " + providerId
                                                        + ", Balance: " + balance + ", Requested: " + amount);
                }

                BigDecimal balanceBefore = balance;
                account.debit(amount);
                accountRepository.save(account);

                AccountTransaction transaction = transactionService.createAdjustment(
                                account,
                                amount,
                                false, // DEBIT
                                balanceBefore,
                                note != null && !note.isBlank() ? note : "دفعة قسطية",
                                userId);

                log.info("INSTALLMENT DEBIT: provider={}, amount={}, newBalance={}",
                                providerId, amount, account.getRunningBalance());

                return transaction;
        }

        /**
         * Settle the full remaining balance using a manual adjustment debit.
         * Used for legacy outstanding balances when no claim-level settlement
         * candidates exist.
         */
        @Transactional
        public AccountTransaction settleRemainingBalanceByProvider(Long providerId, String reason, Long userId) {
                ProviderAccount account = accountRepository.findByProviderIdForUpdate(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));

                if (!account.isActive()) {
                        throw new IllegalStateException("Cannot settle inactive account for provider " + providerId);
                }

                BigDecimal remainingBalance = account.getRunningBalance();
                if (remainingBalance == null || remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalStateException("No outstanding balance to settle for provider " + providerId);
                }

                BigDecimal balanceBefore = account.getRunningBalance();
                account.debit(remainingBalance);
                accountRepository.save(account);

                String adjustmentReason = (reason == null || reason.trim().isEmpty())
                                ? "Manual settlement of remaining legacy balance"
                                : reason.trim();

                AccountTransaction transaction = transactionService.createAdjustment(
                                account,
                                remainingBalance,
                                false,
                                balanceBefore,
                                adjustmentReason,
                                userId);

                log.warn("MANUAL SETTLEMENT: provider={}, account={}, amount={}, reason={}",
                                providerId, account.getId(), remainingBalance, adjustmentReason);

                return transaction;
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // CLAIM REVERSAL - DEBIT OPERATION
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Debit the provider account when an approved claim is reversed to REJECTED.
         * Looks up the original CREDIT transaction for this claim and debits the same
         * amount.
         *
         * @param claimId The claim that was reversed
         * @param userId  User performing the action
         * @return The created debit transaction, or null if no prior credit existed
         */
        @Transactional
        public AccountTransaction debitOnClaimReversal(Long claimId, Long userId) {
                // Cycle-safe idempotency: skip if every credit already has a matching reversal.
                long creditCount = transactionService.countForReference(ReferenceType.CLAIM_APPROVAL, claimId);
                long reversalCount = transactionService.countForReference(ReferenceType.CLAIM_REVERSAL, claimId);
                if (reversalCount >= creditCount) {
                        log.warn("⚠️ All credits already reversed for claim {} (credits={}, reversals={}) — skipping",
                                        claimId, creditCount, reversalCount);
                        return null;
                }

                // Find the most recent CREDIT transaction for this claim (handles multi-cycle)
                AccountTransaction creditTx = transactionRepository
                                .findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                                                ReferenceType.CLAIM_APPROVAL, claimId)
                                .orElse(null);

                if (creditTx == null) {
                        // No credit was ever recorded — nothing to reverse.
                        // This can legitimately happen if the claim was rejected before credit,
                        // so log a warning but return null gracefully.
                        log.warn("No credit transaction found for claim {} — no reversal debit needed", claimId);
                        return null;
                }

                BigDecimal amount = creditTx.getAmount();

                Long providerId = claimRepository.findById(claimId)
                                .map(Claim::getProviderId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Claim not found for reversal: " + claimId));

                if (providerId == null) {
                        throw new IllegalStateException(
                                        "Claim " + claimId + " has no provider — cannot process reversal debit");
                }

                // FIX #12: Lock account directly and use dedicated CLAIM_REVERSAL transaction
                // (previously used debitOnInstallmentPayment which filed it as ADJUSTMENT)
                ProviderAccount account = accountRepository.findByProviderIdForUpdate(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));

                if (!account.isActive()) {
                        throw new IllegalStateException("Cannot debit inactive account for provider " + providerId);
                }

                if (account.getRunningBalance().compareTo(amount) < 0) {
                        log.error("REVERSAL: Insufficient balance for claim {}. Balance={}, ReversalAmount={}",
                                        claimId, account.getRunningBalance(), amount);
                        throw new IllegalStateException(
                                        "Insufficient balance to reverse approved claim " + claimId
                                                        + ". Balance: " + account.getRunningBalance()
                                                        + ", Required: " + amount);
                }

                BigDecimal balanceBefore = account.getRunningBalance();
                account.reverseCredit(amount);
                accountRepository.save(account);

                AccountTransaction tx = transactionService.createClaimReversalDebit(
                                account, claimId, amount, balanceBefore, userId);

                log.info("REVERSAL DEBIT: claim={}, provider={}, amount={}, newBalance={}",
                                claimId, providerId, amount, account.getRunningBalance());
                return tx;
        }

        /**
         * Debit the provider account when a claim is individually settled (paid
         * directly).
         * Uses claim.paidAmount (set by settleClaim) as the authoritative amount.
         * Falls back to the original CREDIT transaction amount if paidAmount is null.
         * Idempotent: no-op if the claim was already debited (prevents double-debit
         * in case batch payment later tries to process the same claim).
         *
         * @param claimId The settled claim ID
         * @param userId  User performing the action
         * @return The created debit transaction, or null if no prior credit / already
         *         settled
         */
        @Transactional
        public AccountTransaction debitOnClaimSettlement(Long claimId, Long userId) {
                // Idempotency guard: if we already recorded a CLAIM_SETTLEMENT tx, skip.
                if (transactionService.existsForReference(ReferenceType.CLAIM_SETTLEMENT, claimId)) {
                        log.warn("⚠️ CLAIM_SETTLEMENT debit already exists for claim {} — skipping duplicate", claimId);
                        return null;
                }

                Claim claim = claimRepository.findById(claimId).orElse(null);
                if (claim == null) {
                        log.warn("⚠️ Claim {} not found — skipping settlement debit", claimId);
                        return null;
                }

                Long providerId = claim.getProviderId();
                if (providerId == null) {
                        log.warn("⚠️ Provider not found for claim {} — skipping settlement debit", claimId);
                        return null;
                }

                // Use the recorded paidAmount if available (exact amount given to provider),
                // otherwise fall back to the original CREDIT amount
                BigDecimal amount = claim.getPaidAmount();
                AccountTransaction creditTx = transactionRepository
                                .findByReferenceTypeAndReferenceId(ReferenceType.CLAIM_APPROVAL, claimId)
                                .orElse(null);

                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        if (creditTx == null) {
                                log.warn("⚠️ No credit transaction found for claim {} — skipping settlement debit",
                                                claimId);
                                return null;
                        }
                        amount = creditTx.getAmount();
                }

                // B-05 FIX: Settlement debit must never exceed original credit.
                // Without this guard, a custom paidAmount could create a negative balance.
                if (creditTx != null && amount.compareTo(creditTx.getAmount()) > 0) {
                        log.warn("⚠️ Settlement amount {} exceeds original credit {} for claim {} — capping to credit",
                                        amount, creditTx.getAmount(), claimId);
                        amount = creditTx.getAmount();
                }

                ProviderAccount account = accountRepository.findByProviderIdForUpdate(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));

                if (!account.isActive()) {
                        throw new IllegalStateException(
                                        "Cannot debit inactive account for provider " + providerId);
                }

                BigDecimal balance = account.getRunningBalance();
                if (balance == null) {
                        throw new IllegalStateException(
                                        "CRITICAL: Running balance is null for provider " + providerId
                                                        + ". Possible data corruption — investigate immediately.");
                }
                if (balance.compareTo(amount) < 0) {
                        throw new IllegalStateException(
                                        "Insufficient balance for claim settlement. Provider: " + providerId
                                                        + ", Balance: " + balance + ", Requested: " + amount);
                }

                BigDecimal balanceBefore = balance;
                account.debit(amount);
                accountRepository.save(account);

                AccountTransaction tx = transactionService.createClaimSettlementDebit(
                                account, claimId, amount, balanceBefore, userId);

                log.info("SETTLEMENT DEBIT: claim={}, provider={}, amount={}", claimId, providerId, amount);
                return tx;
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // ACCOUNT SUMMARY & REPORTING
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Get comprehensive account summary for a provider.
         */
        @Transactional(readOnly = true)
        public AccountSummaryDTO getAccountSummary(Long providerId) {
                ProviderAccount account = getAccountByProviderId(providerId);

                // Get provider name
                String providerName = providerRepository.findById(providerId)
                                .map(Provider::getName)
                                .orElse("مقدم خدمة #" + providerId);

                // Verify balance integrity (transactions check)
                BigDecimal calculatedBalance = transactionRepository.getCalculatedBalance(account.getId());
                if (calculatedBalance == null) {
                        calculatedBalance = BigDecimal.ZERO;
                }

                boolean balanceVerified = account.getRunningBalance().compareTo(calculatedBalance) == 0;
                if (!balanceVerified) {
                        log.error("BALANCE MISMATCH! Account {}: stored={}, calculated from transactions={}",
                                        account.getId(), account.getRunningBalance(), calculatedBalance);
                }

                // Verify formula invariant: runningBalance == totalApproved - totalPaid
                BigDecimal formulaBalance = account.getTotalApproved().subtract(account.getTotalPaid());
                boolean formulaVerified = account.getRunningBalance().compareTo(formulaBalance) == 0;
                if (!formulaVerified) {
                        log.error("FORMULA MISMATCH! Account {}: stored={}, formula (totalApproved - totalPaid)={}",
                                        account.getId(), account.getRunningBalance(), formulaBalance);
                }

                long transactionCount = transactionRepository.countByProviderAccountId(account.getId());

                return AccountSummaryDTO.builder()
                                .accountId(account.getId())
                                .providerId(providerId)
                                .providerName(providerName)
                                .runningBalance(account.getRunningBalance())
                                .totalApproved(account.getTotalApproved())
                                .totalPaid(account.getTotalPaid())
                                .status(account.getStatus().name())
                                .statusArabic(account.getStatus().getArabicLabel())
                                .transactionCount(transactionCount)
                                .balanceVerified(balanceVerified)
                                .formulaVerified(formulaVerified)
                                .createdAt(account.getCreatedAt())
                                .updatedAt(account.getUpdatedAt())
                                .build();
        }

        /**
         * Get transactions for a provider account.
         */
        @Transactional(readOnly = true)
        public Page<AccountTransaction> getTransactions(Long providerId, Pageable pageable) {
                ProviderAccount account = getAccountByProviderId(providerId);
                return transactionRepository.findByProviderAccountIdOrderByCreatedAtDesc(account.getId(), pageable);
        }

        /**
         * Get transactions in date range (for account statements).
         */
        @Transactional(readOnly = true)
        public List<AccountTransaction> getTransactionsInDateRange(
                        Long providerId,
                        LocalDateTime startDate,
                        LocalDateTime endDate) {
                ProviderAccount account = getAccountByProviderId(providerId);
                return transactionRepository.findByAccountAndDateRange(account.getId(), startDate, endDate);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // ACCOUNT STATUS MANAGEMENT
        // ═══════════════════════════════════════════════════════════════════════════

        @Transactional
        public ProviderAccount suspendAccount(Long providerId, String reason) {
                ProviderAccount account = getAccountForUpdate(providerId);
                account.suspend();
                log.warn("Account suspended: provider={}, reason={}", providerId, reason);
                return accountRepository.save(account);
        }

        @Transactional
        public ProviderAccount reactivateAccount(Long providerId) {
                ProviderAccount account = getAccountForUpdate(providerId);
                account.reactivate();
                log.info("Account reactivated: provider={}", providerId);
                return accountRepository.save(account);
        }

        @Transactional
        public ProviderAccount closeAccount(Long providerId, String reason) {
                ProviderAccount account = getAccountForUpdate(providerId);
                account.close();
                log.warn("Account closed: provider={}, reason={}", providerId, reason);
                return accountRepository.save(account);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // BALANCE VERIFICATION (FOR AUDITING)
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Verify account balance matches transaction history.
         * INVARIANT: running_balance = SUM(credits) - SUM(debits)
         */
        @Transactional(readOnly = true)
        public boolean verifyAccountBalance(Long accountId) {
                ProviderAccount account = getAccountById(accountId);

                // Check 1: running_balance == SUM(credits) - SUM(debits)
                BigDecimal calculatedBalance = transactionRepository.getCalculatedBalance(accountId);
                if (calculatedBalance == null) {
                        calculatedBalance = BigDecimal.ZERO;
                }

                boolean txMatch = account.getRunningBalance().compareTo(calculatedBalance) == 0;

                if (!txMatch) {
                        log.error("CRITICAL: Balance verification FAILED for account {}. " +
                                        "Stored: {}, Calculated from transactions: {}",
                                        accountId, account.getRunningBalance(), calculatedBalance);
                }

                // Check 2: running_balance == totalApproved - totalPaid (formula invariant)
                BigDecimal formulaBalance = account.getTotalApproved().subtract(account.getTotalPaid());
                boolean formulaMatch = account.getRunningBalance().compareTo(formulaBalance) == 0;

                if (!formulaMatch) {
                        log.error("CRITICAL: Formula verification FAILED for account {}. " +
                                        "Stored: {}, Formula (totalApproved - totalPaid): {}",
                                        accountId, account.getRunningBalance(), formulaBalance);
                }

                return txMatch && formulaMatch;
        }

        /**
         * Get all providers with outstanding balance.
         */
        @Transactional(readOnly = true)
        public List<ProviderAccount> getAccountsWithOutstandingBalance() {
                return accountRepository.findWithOutstandingBalance(AccountStatus.ACTIVE);
        }

        /**
         * Get all provider accounts as DTOs with provider names.
         * Shows ALL active accounts (not just those with outstanding balance).
         * Use hasBalance=true to filter for accounts with balance > 0 only.
         */
        @Transactional(readOnly = true)
        public List<ProviderAccountListDTO> getAccountsWithProviderNames() {
                return getAccountsWithProviderNames(null, false);
        }

        /**
         * Get provider accounts as DTOs with provider names, with optional status and
         * balance filters.
         *
         * @param statusFilter   optional account status filter (ACTIVE, SUSPENDED,
         *                       CLOSED); null = ACTIVE only
         * @param hasBalanceOnly if true, only return accounts where running_balance > 0
         */
        @Transactional // NOT read-only - may create accounts for new providers
        public List<ProviderAccountListDTO> getAccountsWithProviderNames(String statusFilter, boolean hasBalanceOnly) {
                // Get all active providers to ensure every provider shows in the list
                List<Provider> allProviders = providerRepository.findAll();

                // Ensure every active provider has an account (lazy creation)
                for (Provider provider : allProviders) {
                        if (!accountRepository.existsByProviderId(provider.getId())) {
                                log.info("Auto-creating provider account for provider {}", provider.getId());
                                ProviderAccount account = ProviderAccount.builder()
                                                .providerId(provider.getId())
                                                .runningBalance(BigDecimal.ZERO)
                                                .totalApproved(BigDecimal.ZERO)
                                                .totalPaid(BigDecimal.ZERO)
                                                .status(AccountStatus.ACTIVE)
                                                .build();
                                accountRepository.save(account);
                        }
                }

                // Resolve target status (null = ALL statuses)
                AccountStatus targetStatus = null;
                if (statusFilter != null && !statusFilter.isBlank()) {
                        try {
                                targetStatus = AccountStatus.valueOf(statusFilter.toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                                // Invalid status value — return all statuses
                                log.warn("Invalid account status filter '{}', returning all statuses", statusFilter);
                        }
                }

                // Now fetch accounts by status (null = all statuses)
                List<ProviderAccount> accounts;
                if (hasBalanceOnly) {
                        accounts = targetStatus != null
                                        ? accountRepository.findWithOutstandingBalance(targetStatus)
                                        : accountRepository.findAllWithOutstandingBalance();
                } else {
                        accounts = targetStatus != null
                                        ? accountRepository.findByStatus(targetStatus)
                                        : accountRepository.findAll();
                }

                // Batch-load all providers in one query to avoid N+1 individual lookups
                java.util.Set<Long> accountProviderIds = accounts.stream()
                                .map(ProviderAccount::getProviderId)
                                .collect(java.util.stream.Collectors.toSet());
                java.util.Map<Long, com.waad.tba.modules.provider.entity.Provider> providerMap = providerRepository
                                .findAllById(accountProviderIds).stream()
                                .collect(java.util.stream.Collectors.toMap(
                                                com.waad.tba.modules.provider.entity.Provider::getId, p -> p));

                return accounts.stream().map(account -> {
                        Provider provider = providerMap.get(account.getProviderId());
                        String providerName = provider != null ? provider.getName()
                                        : "مقدم خدمة #" + account.getProviderId();
                        String providerType = provider != null && provider.getProviderType() != null
                                        ? provider.getProviderType().name()
                                        : null;

                        return ProviderAccountListDTO.builder()
                                        .id(account.getId())
                                        .providerId(account.getProviderId())
                                        .providerName(providerName)
                                        .providerType(providerType)
                                        .runningBalance(account.getRunningBalance())
                                        .totalApproved(account.getTotalApproved())
                                        .totalPaid(account.getTotalPaid())
                                        .status(account.getStatus().name())
                                        .statusArabic(account.getStatus().getArabicLabel())
                                        .pendingClaimsCount((int) claimRepository
                                                        .countOutstandingClaimsByProvider(account.getProviderId()))
                                        .createdAt(account.getCreatedAt())
                                        .updatedAt(account.getUpdatedAt())
                                        .build();
                }).toList();
        }

        /**
         * Get total outstanding balance across all providers.
         */
        @Transactional(readOnly = true)
        public BigDecimal getTotalOutstandingBalance() {
                return accountRepository.getTotalOutstandingBalance();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // BALANCE REPAIR (for data inconsistency caused by claim deletion without
        // reversal)
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Return all provider IDs that have an account (for bulk repair operations).
         */
        public java.util.List<Long> getAllProviderIds() {
                return accountRepository.findAll().stream()
                                .map(ProviderAccount::getProviderId)
                                .filter(java.util.Objects::nonNull)
                                .collect(java.util.stream.Collectors.toList());
        }

        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Repair orphaned CLAIM_APPROVAL credits: for every CLAIM_APPROVAL transaction
         * on this provider's account whose claim no longer exists (hard-deleted) or is
         * soft-deleted (active=false), create the missing CLAIM_REVERSAL debit so the
         * running_balance reflects reality.
         *
         * This is the correct repair path. Simply recalculating from transactions would
         * yield the same stale result because the orphaned credits are still in the
         * transaction log. We must create the matching debit entries.
         *
         * @param providerId Provider ID
         * @param userId     User performing the repair (for audit trail)
         */
        @Transactional
        public java.util.Map<String, Object> recalculateBalance(Long providerId) {
                ProviderAccount account = accountRepository.findByProviderIdForUpdate(providerId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Provider account not found for provider: " + providerId));

                // Find all CLAIM_APPROVAL credits for this account
                List<AccountTransaction> approvalCredits = transactionRepository
                                .findClaimTransactionsByAccount(account.getId());

                BigDecimal oldBalance = account.getRunningBalance();
                int reversedCount = 0;
                BigDecimal reversedTotal = BigDecimal.ZERO;

                for (AccountTransaction credit : approvalCredits) {
                        Long claimId = credit.getReferenceId();
                        if (claimId == null)
                                continue;

                        // Skip if a CLAIM_REVERSAL already exists for this claim (idempotent)
                        if (transactionService.existsForReference(
                                        AccountTransaction.ReferenceType.CLAIM_REVERSAL, claimId)) {
                                continue;
                        }

                        // Check whether the claim is active with a status that legitimately holds
                        // credit
                        final Set<ClaimStatus> CREDIT_BEARING_STATUSES = EnumSet.of(
                                        ClaimStatus.APPROVED, ClaimStatus.NEEDS_CORRECTION,
                                        ClaimStatus.BATCHED, ClaimStatus.SETTLED);
                        boolean claimIsActive = claimRepository.findById(claimId)
                                        .map(c -> Boolean.TRUE.equals(c.getActive())
                                                        && CREDIT_BEARING_STATUSES.contains(c.getStatus()))
                                        .orElse(false); // hard-deleted → not found → false

                        if (claimIsActive) {
                                // Claim is still live — credit is legitimate, skip
                                continue;
                        }

                        // Orphaned credit — create the missing reversal debit
                        BigDecimal amount = credit.getAmount();
                        if (account.getRunningBalance().compareTo(amount) < 0) {
                                // Safety guard: clamp to available balance to avoid negative balance
                                log.warn("REPAIR: clamping reversal for claim {} to available balance {}",
                                                claimId, account.getRunningBalance());
                                amount = account.getRunningBalance();
                        }
                        if (amount.compareTo(BigDecimal.ZERO) <= 0)
                                continue;

                        BigDecimal balanceBefore = account.getRunningBalance();
                        account.reverseCredit(amount);
                        accountRepository.save(account);

                        transactionService.createClaimReversalDebit(account, claimId, amount, balanceBefore, null);

                        reversedCount++;
                        reversedTotal = reversedTotal.add(amount);
                        log.warn("REPAIR REVERSAL: claim={}, amount={}, newBalance={}",
                                        claimId, amount, account.getRunningBalance());
                }

                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("providerId", providerId);
                result.put("accountId", account.getId());
                result.put("oldBalance", oldBalance);
                result.put("newBalance", account.getRunningBalance());
                result.put("reversedClaimsCount", reversedCount);
                result.put("reversedTotal", reversedTotal);
                result.put("message", reversedCount > 0
                                ? "تم إصلاح " + reversedCount + " قيد يتيم، المبلغ المُصحَّح: " + reversedTotal
                                : "لا توجد قيود يتيمة — الرصيد سليم");
                return result;
        }
}
