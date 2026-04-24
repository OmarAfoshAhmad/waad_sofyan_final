package com.waad.tba.modules.settlement.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for ClaimSettledEvent (fired AFTER_COMMIT) to react to settlements.
 *
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ CLAIM SETTLED EVENT LISTENER ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Fires after the settlement transaction commits, so: ║
 * ║ • The provider-account debit is already durable ║
 * ║ • Downstream services can safely read the settled state ║
 * ║ ║
 * ║ Extend this listener to add: ║
 * ║ • Settlement batch total refresh ║
 * ║ • Email / SMS notifications ║
 * ║ • BI pre-aggregation hooks ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimSettledEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClaimSettled(ClaimSettledEvent event) {
        log.info("✅ [EVENT] ClaimSettledEvent: claimId={}, providerId={}, amount={}",
                event.getClaimId(), event.getProviderId(), event.getSettledAmount());
        // Extension point: add batch-total refresh, notification triggers, etc.
    }
}
