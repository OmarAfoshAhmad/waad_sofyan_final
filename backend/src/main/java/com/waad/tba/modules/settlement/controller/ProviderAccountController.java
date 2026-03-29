package com.waad.tba.modules.settlement.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.settlement.dto.AccountSummaryDTO;
import com.waad.tba.modules.settlement.dto.ProviderAccountListDTO;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.service.ProviderAccountService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Account Controller - API Version 1
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ PROVIDER ACCOUNT CONTROLLER - API v1 ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ READ-FIRST ENDPOINTS - Focus on viewing financial data ║
 * ║ ║
 * ║ Endpoints: ║
 * ║ GET /api/v1/provider-accounts - List accounts with filters ║
 * ║ GET /api/v1/provider-accounts/{id} - Account details + summary ║
 * ║ GET /api/v1/provider-accounts/{id}/transactions - Transaction history ║
 * ║ GET /api/v1/provider-accounts/summary - Total outstanding across all ║
 * ║ ║
 * ║ Security: ║
 * ║ Required Permission: VIEW_PROVIDER_ACCOUNTS ║
 * ║ ║
 * ║ @see SETTLEMENT_API_CONTRACT.md ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-accounts")
@RequiredArgsConstructor
@Tag(name = "Settlement - Provider Accounts (v1)", description = "APIs for viewing provider financial accounts - Version 1")
@PreAuthorize("isAuthenticated()")
public class ProviderAccountController {

        private final ProviderAccountService providerAccountService;
        private final AuthorizationService authorizationService;

        // ═══════════════════════════════════════════════════════════════════════════
        // LIST & SEARCH
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * List all provider accounts with optional filters.
         * 
         * Filters:
         * - status: ACTIVE, SUSPENDED, CLOSED
         * - hasBalance: true = only accounts with balance > 0
         */
        @GetMapping
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "List provider accounts", description = "Returns provider accounts with provider names and optional filters")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Accounts retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Missing VIEW_PROVIDER_ACCOUNTS permission")
        })
        public ResponseEntity<ApiResponse<List<ProviderAccountListDTO>>> listAccounts(
                        @Parameter(description = "Filter by account status") @RequestParam(name = "status", required = false) String status,

                        @Parameter(description = "Filter accounts with balance > 0") @RequestParam(name = "hasBalance", required = false, defaultValue = "false") boolean hasBalance) {

                log.info("Listing provider accounts. Filters: status={}, hasBalance={}", status, hasBalance);

                // Return accounts with provider names; pass status and hasBalance filters
                List<ProviderAccountListDTO> accounts = providerAccountService.getAccountsWithProviderNames(status,
                                hasBalance);

                return ResponseEntity.ok(ApiResponse.success(accounts));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // ACCOUNT DETAILS
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Get detailed account summary by provider ID.
         * 
         * Returns:
         * - Account info (balance, totals, status)
         * - Balance verification status
         * - Transaction count
         */
        @GetMapping("/by-provider/{providerId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Get account summary by provider ID", description = "Returns comprehensive account summary including balance verification")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account summary retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Provider account not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
        })
        public ResponseEntity<ApiResponse<AccountSummaryDTO>> getAccountByProvider(
                        @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId) {

                log.info("Getting account summary for provider {}", providerId);

                AccountSummaryDTO summary = providerAccountService.getAccountSummary(providerId);

                return ResponseEntity.ok(ApiResponse.success(summary));
        }

        /**
         * Get account details by account ID.
         */
        @GetMapping("/{accountId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Get account by ID", description = "Returns provider account details")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
        })
        public ResponseEntity<ApiResponse<ProviderAccount>> getAccountById(
                        @Parameter(description = "Account ID", required = true) @PathVariable("accountId") Long accountId) {

                log.info("Getting account by ID {}", accountId);

                ProviderAccount account = providerAccountService.getAccountById(accountId);

                return ResponseEntity.ok(ApiResponse.success(account));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // TRANSACTION HISTORY
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Get transaction history for a provider.
         * 
         * Supports:
         * - Pagination
         * - Date range filtering
         */
        @GetMapping("/by-provider/{providerId}/transactions")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Get transaction history", description = "Returns paginated transaction history for a provider")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transactions retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<ApiResponse<Page<AccountTransaction>>> getTransactions(
                        @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId,

                        @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,

                        @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,

                        @Parameter(description = "Start date filter") @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

                        @Parameter(description = "End date filter") @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

                log.info("Getting transactions for provider {}. Page: {}, Size: {}", providerId, page, size);

                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

                Page<AccountTransaction> transactions;

                if (startDate != null && endDate != null) {
                        // If date range provided, get filtered list and convert to page
                        List<AccountTransaction> filteredTransactions = providerAccountService
                                        .getTransactionsInDateRange(providerId, startDate, endDate);

                        // Convert list to page (simple implementation)
                        int start = page * size;
                        int end = Math.min(start + size, filteredTransactions.size());

                        List<AccountTransaction> pageContent = start < filteredTransactions.size()
                                        ? filteredTransactions.subList(start, end)
                                        : List.of();

                        transactions = new org.springframework.data.domain.PageImpl<>(
                                        pageContent, pageable, filteredTransactions.size());
                } else {
                        transactions = providerAccountService.getTransactions(providerId, pageable);
                }

                return ResponseEntity.ok(ApiResponse.success(transactions));
        }

        /**
         * Get recent transactions (last 10) for quick view.
         */
        @GetMapping("/by-provider/{providerId}/transactions/recent")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Get recent transactions", description = "Returns last 10 transactions for quick view")
        public ResponseEntity<ApiResponse<List<AccountTransaction>>> getRecentTransactions(
                        @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId) {

                log.info("Getting recent transactions for provider {}", providerId);

                Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
                Page<AccountTransaction> transactions = providerAccountService.getTransactions(providerId, pageable);

                return ResponseEntity.ok(ApiResponse.success(transactions.getContent()));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SUMMARY & REPORTING
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Get total outstanding balance across all providers.
         * Used for financial dashboard / reporting.
         */
        @GetMapping("/summary/total-outstanding")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Get total outstanding balance", description = "Returns the total outstanding balance across all provider accounts")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getTotalOutstanding() {

                log.info("Getting total outstanding balance");

                BigDecimal totalOutstanding = providerAccountService.getTotalOutstandingBalance();
                List<ProviderAccount> accountsWithBalance = providerAccountService.getAccountsWithOutstandingBalance();

                Map<String, Object> summary = Map.of(
                                "totalOutstandingBalance", totalOutstanding,
                                "totalOutstandingBalanceFormatted", totalOutstanding + " د.ل",
                                "accountsWithBalance", accountsWithBalance.size(),
                                "message", "إجمالي المبالغ المستحقة لمقدمي الخدمات",
                                "messageEn", "Total amount owed to service providers");

                return ResponseEntity.ok(ApiResponse.success(summary));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // BALANCE VERIFICATION (AUDIT)
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Verify balance integrity for an account.
         * Checks: running_balance == SUM(credits) - SUM(debits)
         */
        @GetMapping("/{accountId}/verify-balance")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER')")
        @Operation(summary = "Verify account balance", description = "Verifies that account balance matches transaction history")
        public ResponseEntity<ApiResponse<Map<String, Object>>> verifyBalance(
                        @Parameter(description = "Account ID", required = true) @PathVariable("accountId") Long accountId) {

                log.info("Verifying balance for account {}", accountId);

                boolean isValid = providerAccountService.verifyAccountBalance(accountId);
                ProviderAccount account = providerAccountService.getAccountById(accountId);

                Map<String, Object> result = Map.of(
                                "accountId", accountId,
                                "runningBalance", account.getRunningBalance(),
                                "totalApproved", account.getTotalApproved(),
                                "totalPaid", account.getTotalPaid(),
                                "balanceVerified", isValid,
                                "status", isValid ? "VALID" : "MISMATCH",
                                "message", isValid
                                                ? "تطابق الرصيد: الرصيد صحيح ✓"
                                                : "عدم تطابق: يرجى مراجعة المعاملات ✗");

                return ResponseEntity.ok(ApiResponse.success(result));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // BALANCE REPAIR
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Recalculate running_balance from transaction history for a provider.
         *
         * Use this endpoint to repair a provider account whose balance is out of sync
         * because claims were deleted before the financial reversal fix was applied.
         */
        @PostMapping("/by-provider/{providerId}/recalculate-balance")
        @PreAuthorize("hasRole('SUPER_ADMIN')")
        @Operation(summary = "Recalculate account balance from transactions", description = "Repairs running_balance by recomputing from transaction history. Use after deleting claims without proper reversal.")
        public ResponseEntity<ApiResponse<Map<String, Object>>> recalculateBalance(
                        @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId) {

                log.warn("MANUAL BALANCE RECALC requested for provider {}", providerId);
                Map<String, Object> result = providerAccountService.recalculateBalance(providerId);
                return ResponseEntity.ok(ApiResponse.success(result));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // INSTALLMENT PAYMENTS
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Record an installment (partial) payment for a provider.
         *
         * Debits the provider account by the given amount.
         * Expects: { amount, paymentReference, paymentMethod (ignored), notes
         * (optional) }
         */
        @PostMapping("/by-provider/{providerId}/pay")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
        @Operation(summary = "Record installment payment", description = "Debits the provider account for a partial/installment payment")
        public ResponseEntity<ApiResponse<AccountTransaction>> recordInstallmentPayment(
                        @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId,
                        @RequestBody Map<String, Object> request) {

                Object amountRaw = request.get("amount");
                if (amountRaw == null) {
                        throw new IllegalArgumentException("مبلغ الدفعة مطلوب");
                }
                BigDecimal amount;
                try {
                        amount = new BigDecimal(String.valueOf(amountRaw));
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("مبلغ الدفعة غير صالح");
                }
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("يجب أن يكون مبلغ الدفعة أكبر من الصفر");
                }

                String paymentReference = request.get("paymentReference") != null
                                ? String.valueOf(request.get("paymentReference")).trim()
                                : "";
                if (paymentReference.isEmpty()) {
                        throw new IllegalArgumentException("مرجع الدفع مطلوب");
                }

                String notes = request.get("notes") != null ? String.valueOf(request.get("notes")).trim() : "";
                String description = notes.isEmpty() ? paymentReference : paymentReference + " - " + notes;

                Long userId = authorizationService.getCurrentUser() != null
                                ? authorizationService.getCurrentUser().getId()
                                : null;

                log.info("Recording installment payment: provider={}, amount={}, ref={}", providerId, amount,
                                paymentReference);

                AccountTransaction transaction = providerAccountService.debitOnInstallmentPayment(
                                providerId, amount, description, userId);

                return ResponseEntity.ok(ApiResponse.success(transaction));
        }

        /**
         * Settle full remaining balance for provider account using manual adjustment
         * debit.
         * Intended for legacy balances that are not tied to currently available claims.
         */
        @PostMapping("/by-provider/{providerId}/settle-remaining")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
        @Operation(summary = "Settle remaining balance", description = "Creates manual debit adjustment to settle the full outstanding balance for provider account")
        public ResponseEntity<ApiResponse<Map<String, Object>>> settleRemainingBalance(
                        @Parameter(description = "Provider ID", required = true) @PathVariable("providerId") Long providerId,
                        @RequestBody(required = false) Map<String, String> request) {

                String reason = request != null ? request.get("reason") : null;
                Long userId = authorizationService.getCurrentUser() != null
                                ? authorizationService.getCurrentUser().getId()
                                : null;

                AccountTransaction transaction = providerAccountService.settleRemainingBalanceByProvider(providerId,
                                reason,
                                userId);
                AccountSummaryDTO summary = providerAccountService.getAccountSummary(providerId);

                Map<String, Object> result = Map.of(
                                "providerId", providerId,
                                "transactionId", transaction.getId(),
                                "settledAmount", transaction.getAmount(),
                                "newRunningBalance", summary.getRunningBalance(),
                                "message", "تمت تسوية الرصيد المتبقي بنجاح");

                return ResponseEntity.ok(ApiResponse.success(result));
        }
}
