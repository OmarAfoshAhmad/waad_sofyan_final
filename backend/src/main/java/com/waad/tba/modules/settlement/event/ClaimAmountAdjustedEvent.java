package com.waad.tba.modules.settlement.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Event published when an already approved/settled claim's amount is adjusted.
 * Signals the ProviderAccountService to record an adjustment entry (delta).
 */
public class ClaimAmountAdjustedEvent extends ApplicationEvent {

    private final Long claimId;
    private final Long providerId;
    private final BigDecimal oldApprovedAmount;
    private final BigDecimal newApprovedAmount;
    private final BigDecimal deltaAmount;
    private final Long userId;
    private final Long claimVersion;

    /**
     * @param claimVersion the claim's @Version AFTER this amount change was saved.
     *                     Forms the idempotency key (claimId, claimVersion) for the
     *                     listener — a claim can legitimately be adjusted more than
     *                     once, so claimId alone cannot tell two adjustments apart
     *                     or a redelivered one from a fresh one.
     */
    public ClaimAmountAdjustedEvent(Object source, Long claimId, Long providerId,
                                    BigDecimal oldApprovedAmount, BigDecimal newApprovedAmount,
                                    Long userId, Long claimVersion) {
        super(source);
        this.claimId = claimId;
        this.providerId = providerId;
        this.oldApprovedAmount = oldApprovedAmount;
        this.newApprovedAmount = newApprovedAmount;
        this.deltaAmount = newApprovedAmount.subtract(oldApprovedAmount);
        this.userId = userId;
        this.claimVersion = claimVersion;
    }

    public Long getClaimId() {
        return claimId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public BigDecimal getOldApprovedAmount() {
        return oldApprovedAmount;
    }

    public BigDecimal getNewApprovedAmount() {
        return newApprovedAmount;
    }

    public BigDecimal getDeltaAmount() {
        return deltaAmount;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getClaimVersion() {
        return claimVersion;
    }
}
