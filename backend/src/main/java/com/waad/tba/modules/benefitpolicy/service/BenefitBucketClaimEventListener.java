package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.settlement.event.ClaimReversalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BenefitBucketClaimEventListener {
    private final BenefitBucketLedgerService ledgerService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onReversed(ClaimReversalEvent event) {
        ledgerService.reverseClaim(event.getClaimId());
    }
}
