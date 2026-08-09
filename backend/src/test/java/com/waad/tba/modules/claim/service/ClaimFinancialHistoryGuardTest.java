package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.ClaimLineLimitSnapshotRepository;
import com.waad.tba.modules.claim.repository.FinancialOutboxEventRepository;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimFinancialHistoryGuardTest {
    @Mock BenefitBucketConsumptionRepository consumptions;
    @Mock ClaimLineLimitSnapshotRepository snapshots;
    @Mock FinancialOutboxEventRepository outbox;
    @Mock AccountTransactionRepository transactions;
    @InjectMocks ClaimFinancialHistoryGuard guard;

    @Test
    void allowsHardDeleteOnlyWhenEveryFinancialSourceIsEmpty() {
        guard.assertHardDeleteAllowed(10L);
        verify(transactions).existsByReferenceIdAndReferenceTypeIn(
                org.mockito.ArgumentMatchers.eq(10L), anyCollection());
    }

    @Test
    void bucketHistoryFailsClosedWithoutCheckingLaterSources() {
        when(consumptions.existsByClaimId(11L)).thenReturn(true);
        assertBlocked(11L);
        verify(snapshots, never()).existsByClaimId(11L);
    }

    @Test
    void snapshotHistoryBlocksDeletionEvenWithoutConsumption() {
        when(snapshots.existsByClaimId(12L)).thenReturn(true);
        assertBlocked(12L);
    }

    @Test
    void providerLedgerHistoryBlocksLegacyClaimDeletion() {
        when(transactions.existsByReferenceIdAndReferenceTypeIn(
                org.mockito.ArgumentMatchers.eq(13L), anyCollection())).thenReturn(true);
        assertBlocked(13L);
    }

    private void assertBlocked(Long claimId) {
        assertThatThrownBy(() -> guard.assertHardDeleteAllowed(claimId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("تاريخ مالي دائم");
    }
}
