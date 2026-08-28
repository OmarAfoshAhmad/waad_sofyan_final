package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.preauthorization.service.PreAuthConversionFinalizer;
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
    private final PreAuthConversionFinalizer preAuthConversionFinalizer;
    private final ClaimFinancialSyncService claimFinancialSyncService;
    private final ClaimApprovalOutboxService claimApprovalOutboxService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitApprovedClaim(Long claimId, Long actorId) {
        // Stable lock/effect order: policy+buckets, provider account, outbox.
        // The member was already locked by ClaimFinancialSnapshotService.
        benefitBucketLedgerService.commitClaim(claimId);

        // Immediately after the consumption it replaces, and inside the same
        // transaction: a hold released without its claim posting would hand
        // the limit back to everyone, and a claim posted without its hold
        // released would charge the member twice for one service.
        //
        // Here rather than on a second listener of ClaimApprovedEvent, so the
        // two steps cannot be separated or reordered by listener precedence.
        // This gate is itself reached from that event, which is published on
        // five occasions -- including restoring a soft-deleted claim and
        // re-approving a rejected one -- and two of those sites can fire in a
        // single transaction. So being called more than once for one claim is
        // NORMAL, and nothing about this position prevents it. What makes the
        // conversion happen once is the finalizer finding no hold left to
        // release, which is a fact about the ledger rather than a count of
        // anything.
        preAuthConversionFinalizer.finalizeConvertedClaim(claimId, actorId);

        claimFinancialSyncService.creditForClaim(claimId, actorId);
        claimApprovalOutboxService.record(claimId, actorId);
    }
}
