package com.waad.tba.modules.settlement.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Event published (AFTER transaction commit) when a claim transitions to
 * SETTLED.
 *
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ CLAIM SETTLED EVENT ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Triggered from ClaimReviewService.settleClaim() once the settlement record
 * ║
 * ║ and provider-account debit are durably committed. ║
 * ║ Listeners may use this to: ║
 * ║ • Refresh batch-level settlement totals ║
 * ║ • Send member/provider email notifications ║
 * ║ • Update BI / reporting aggregates ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Getter
public class ClaimSettledEvent extends ApplicationEvent {

    private final Long claimId;
    private final Long providerId;
    private final Long userId;
    /** The amount that was actually debited from the provider account. */
    private final BigDecimal settledAmount;

    public ClaimSettledEvent(Object source, Long claimId, Long providerId,
            Long userId, BigDecimal settledAmount) {
        super(source);
        this.claimId = claimId;
        this.providerId = providerId;
        this.userId = userId;
        this.settledAmount = settledAmount;
    }
}
