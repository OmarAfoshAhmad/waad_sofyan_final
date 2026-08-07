package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Phase 8 (performance) — recalculateBalance previously called
 * claimRepository.findById and transactionService.existsForReference once PER
 * CLAIM_APPROVAL credit, inside the loop. Both were batched into one query each
 * before the loop. This test fixes the result the batched version must produce;
 * it existed before the batching change and passed against the original code
 * (verified manually before batching), so it protects the refactor rather than
 * documenting it after the fact.
 *
 * Scenarios needing a genuinely still-active claim are out of scope here — that
 * would require a full member/visit/provider fixture chain unrelated to what
 * changed. The two paths exercised (claim never existed / hard-deleted, and a
 * reversal that already exists) are exactly the two batched lookups.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderAccountRecalculateBalanceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ProviderAccountService providerAccountService;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderRepository providers;

    private Long providerId;
    private ProviderAccount account;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        providerId = providers.save(Provider.builder().name("Recalc Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("RCB-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        account = accounts.save(ProviderAccount.builder().providerId(providerId)
                .runningBalance(new BigDecimal("500.00"))
                .totalApproved(new BigDecimal("500.00")).totalPaid(BigDecimal.ZERO).build());
    }

    private void approvalCredit(long claimId, String amount) {
        transactions.saveAndFlush(AccountTransaction.createClaimApprovedCredit(
                account.getId(), claimId, new BigDecimal(amount), account.getRunningBalance(), 1L));
    }

    @Test
    void reversesEveryOrphanedCreditForAHardDeletedClaimAndSumsTheTotalCorrectly() {
        // Neither claim ever exists — both are "hard-deleted" from recalculateBalance's
        // point of view (claimRepository.findById returns empty for both).
        approvalCredit(9001L, "120.00");
        approvalCredit(9002L, "80.00");

        Map<String, Object> result = providerAccountService.recalculateBalance(providerId);

        assertThat(result.get("reversedClaimsCount")).isEqualTo(2);
        assertThat((BigDecimal) result.get("reversedTotal")).isEqualByComparingTo("200.00");
        assertThat((BigDecimal) result.get("newBalance")).isEqualByComparingTo("300.00"); // 500 - 200

        assertThat(transactions.existsByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, 9001L)).isTrue();
        assertThat(transactions.existsByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, 9002L)).isTrue();
    }

    @Test
    void aClaimWithAnExistingReversalIsSkippedNotDoubleReversed() {
        approvalCredit(9003L, "150.00");
        // Simulates a prior repair (or a real CLAIM_REVERSAL) already recorded for this claim.
        transactions.saveAndFlush(AccountTransaction.createClaimReversalDebit(
                account.getId(), 9003L, new BigDecimal("150.00"), account.getRunningBalance(), 1L));

        Map<String, Object> result = providerAccountService.recalculateBalance(providerId);

        assertThat(result.get("reversedClaimsCount")).isEqualTo(0);
        assertThat(transactions.countByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, 9003L))
                .isEqualTo(1L); // not 2
    }

    @Test
    void mixedBatchOnlyReversesTheOrphanedOnesAndLeavesAlreadyReversedAlone() {
        approvalCredit(9004L, "100.00"); // orphaned -> reversed
        approvalCredit(9005L, "50.00");  // already reversed -> skipped
        transactions.saveAndFlush(AccountTransaction.createClaimReversalDebit(
                account.getId(), 9005L, new BigDecimal("50.00"), account.getRunningBalance(), 1L));

        Map<String, Object> result = providerAccountService.recalculateBalance(providerId);

        assertThat(result.get("reversedClaimsCount")).isEqualTo(1);
        assertThat((BigDecimal) result.get("reversedTotal")).isEqualByComparingTo("100.00");
        assertThat(transactions.existsByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, 9004L)).isTrue();
        assertThat(transactions.countByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, 9005L))
                .isEqualTo(1L);
    }
}
