package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.settlement.service.ClaimFinancialSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single ordered financial commit gate after a claim becomes APPROVED.
 * It deliberately owns no independent transaction: all effects join and can
 * roll back the caller's approval transaction.
 */
@Service
@RequiredArgsConstructor
public class ClaimApprovalOrchestrator {
    private final BenefitBucketLedgerService benefitBucketLedgerService;
    private final ClaimFinancialSyncService claimFinancialSyncService;
    private final ClaimApprovalOutboxService claimApprovalOutboxService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitApprovedClaim(Long claimId, Long actorId) {
        // Stable lock/effect order: policy+buckets, provider account, outbox.
        // The member was already locked by ClaimFinancialSnapshotService.
        benefitBucketLedgerService.commitClaim(claimId);
        claimFinancialSyncService.creditForClaim(claimId, actorId);
        claimApprovalOutboxService.record(claimId, actorId);
    }
}
