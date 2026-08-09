package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.ClaimLineLimitSnapshotRepository;
import com.waad.tba.modules.claim.repository.FinancialOutboxEventRepository;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

/** Prevents physical deletion once a claim has any immutable financial history. */
@Service
@RequiredArgsConstructor
public class ClaimFinancialHistoryGuard {
    private static final EnumSet<ReferenceType> CLAIM_REFERENCES = EnumSet.of(
            ReferenceType.CLAIM_APPROVAL,
            ReferenceType.CLAIM_REVERSAL,
            ReferenceType.CLAIM_SETTLEMENT);

    private final BenefitBucketConsumptionRepository consumptions;
    private final ClaimLineLimitSnapshotRepository snapshots;
    private final FinancialOutboxEventRepository outbox;
    private final AccountTransactionRepository accountTransactions;

    public void assertHardDeleteAllowed(Long claimId) {
        boolean hasHistory = consumptions.existsByClaimId(claimId)
                || snapshots.existsByClaimId(claimId)
                || outbox.existsByAggregateTypeAndAggregateId("CLAIM", claimId)
                || accountTransactions.existsByReferenceIdAndReferenceTypeIn(claimId, CLAIM_REFERENCES);
        if (hasHistory) {
            throw new BusinessRuleException(
                    "لا يمكن حذف المطالبة نهائياً لأنها مرتبطة بتاريخ مالي دائم. "
                            + "الإلغاء والعكس يحافظان على السقوف وحساب مقدم الخدمة وسجل التدقيق.");
        }
    }
}
