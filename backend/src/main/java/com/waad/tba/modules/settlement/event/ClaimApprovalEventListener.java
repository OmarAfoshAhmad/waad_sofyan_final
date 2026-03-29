package com.waad.tba.modules.settlement.event;

import com.waad.tba.modules.settlement.service.ClaimFinancialSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event Listener for Claim Approval → Provider Account Credit
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ CLAIM APPROVAL EVENT LISTENER ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Automatically credits provider account when a claim is approved. ║
 * ║ ║
 * ║ TRIGGERS: ║
 * ║ When: ClaimApprovedEvent is published (after claim saved with APPROVED) ║
 * ║ Phase: AFTER_COMMIT (ensures claim save is committed first) ║
 * ║ ║
 * ║ ACTIONS: ║
 * ║ 1. Get the approved claim ║
 * ║ 2. Credit provider account with net provider amount ║
 * ║ 3. Create CREDIT transaction record ║
 * ║ ║
 * ║ PROTECTIONS: ║
 * ║ - Double Credit Prevention (checked in ProviderAccountService) ║
 * ║ - Runs in separate transaction (REQUIRES_NEW) ║
 * ║ - Async execution (does not block approval response) ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * @since Phase 3A - Backend Integration
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimApprovalEventListener {

    private final ClaimFinancialSyncService claimFinancialSyncService;

    /**
     * Handle claim approval event - delegates to ClaimFinancialSyncService.
     * Synchronous (no @Async) → account updated before HTTP response returns.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClaimApproved(ClaimApprovedEvent event) {
        if (event.getProviderId() == null) {
            log.warn("⚠️ [EVENT] Skipping credit - provider ID is null for claim {}", event.getClaimId());
            return;
        }
        log.info("🎯 [EVENT] ClaimApprovedEvent → sync: claimId={}", event.getClaimId());
        claimFinancialSyncService.creditForClaim(event.getClaimId(), event.getUserId());
    }
}
