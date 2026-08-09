package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.settlement.service.ClaimFinancialSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Single ordered, atomic gate for every financial reversal of a claim. */
@Service
@RequiredArgsConstructor
public class ClaimReversalOrchestrator {
    private final BenefitBucketLedgerService benefitBucketLedgerService;
    private final ClaimFinancialSyncService claimFinancialSyncService;
    private final ClaimReversalOutboxService claimReversalOutboxService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseClaim(Long claimId, Long actorId) {
        benefitBucketLedgerService.reverseClaim(claimId);
        claimFinancialSyncService.reverseForClaim(claimId, actorId);
        claimReversalOutboxService.record(claimId, actorId);
    }
}
