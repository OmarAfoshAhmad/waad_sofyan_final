package com.waad.tba.modules.claim.repository;

import java.math.BigDecimal;

/**
 * Projection interface for financial summary by provider query.
 * Moved to repository package to solve classloading issues in some
 * environments.
 */
public interface FinancialSummaryByProviderProjection {

    /**
     * Provider ID
     */
    Long getProviderId();

    /**
     * Provider name
     */
    String getProviderName();

    /**
     * Total claims count
     */
    Long getClaimsCount();

    /**
     * Total requested amount
     */
    BigDecimal getRequestedAmount();

    /**
     * Total approved amount
     */
    BigDecimal getApprovedAmount();

    /**
     * Total patient co-pay
     */
    BigDecimal getPatientCoPay();

    /**
     * Net amount payable to provider
     */
    BigDecimal getNetProviderAmount();
}
