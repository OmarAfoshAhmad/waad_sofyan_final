package com.waad.tba.modules.settlement.event;

import com.waad.tba.modules.claim.service.ClaimReversalOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event Listener for Claim Reversal → Provider Account Debit
 *
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ CLAIM REVERSAL EVENT LISTENER ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Debits the provider account when an approved claim is reversed to REJECTED.
 * ║
 * ║ ║
 * ║ TRIGGERS: ║
 * ║ When: ClaimReversalEvent is published (APPROVED → REJECTED transition) ║
 * ║ Phase: BEFORE_COMMIT (all reversal effects are atomic) ║
 * ║ ║
 * ║ FAILURE HANDLING: ║
 * ║ - Any failure rolls back the status change and every financial effect. ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimReversalEventListener {

    private final ClaimReversalOrchestrator claimReversalOrchestrator;

    /**
     * Handle claim reversal event - delegates to ClaimFinancialSyncService.
     * Synchronous (no @Async) → account updated before HTTP response returns.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleClaimReversal(ClaimReversalEvent event) {
        if (event.getProviderId() == null) {
            log.warn("⚠️ [EVENT] Skipping reversal - provider ID is null for claim {}", event.getClaimId());
            return;
        }
        log.info("🔄 [EVENT] ClaimReversalEvent → sync: claimId={}", event.getClaimId());
        claimReversalOrchestrator.reverseClaim(event.getClaimId(), event.getUserId());
    }
}
