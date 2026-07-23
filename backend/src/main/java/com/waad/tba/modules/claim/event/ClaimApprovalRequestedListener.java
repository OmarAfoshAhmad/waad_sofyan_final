package com.waad.tba.modules.claim.event;

import com.waad.tba.modules.claim.service.ClaimReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs financial approval outside the request transaction and only after commit. */
@Component
@RequiredArgsConstructor
public class ClaimApprovalRequestedListener {

    private final ClaimReviewService claimReviewService;

    @Async("approvalTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(ClaimApprovalRequestedEvent event) {
        claimReviewService.processApproval(
                event.claimId(),
                event.request(),
                event.actorId(),
                event.actorUsername(),
                event.actorType());
    }
}
