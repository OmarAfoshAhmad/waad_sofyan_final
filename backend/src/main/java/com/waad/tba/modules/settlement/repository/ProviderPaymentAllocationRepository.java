package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProviderPaymentAllocationRepository
        extends JpaRepository<ProviderPaymentAllocation, Long> {

    /**
     * Money actually disbursed for a provider/employer over a period.
     *
     * <p>Only POSTED payments count, which is the definition the settlement
     * module already states: a draft has no ledger effect and a reversal is
     * neutralised. The claim financial summary used to call a claim "paid" the
     * moment it reached APPROVED or BATCHED, so it reported money as disbursed
     * before a single dinar had left the account.
     *
     * <p>Allocations are recorded against a target month, not a service date,
     * because that is how the accountant settles: one transfer, distributed over
     * the months it covers. A period is therefore matched on (year, month)
     * ordinals, which is exact for the month-at-a-time screens that ask this
     * question and inclusive at the edges for any wider range.
     */
    @Query("""
           SELECT COALESCE(SUM(a.amount), 0)
           FROM ProviderPaymentAllocation a
           WHERE a.payment.status = com.waad.tba.modules.settlement.entity.ProviderPayment.Status.POSTED
             AND (:employerId IS NULL OR a.employerId = :employerId)
             AND (:providerId IS NULL OR a.payment.providerId = :providerId)
             AND (:fromOrdinal IS NULL OR (a.targetYear * 100 + a.targetMonth) >= :fromOrdinal)
             AND (:toOrdinal IS NULL OR (a.targetYear * 100 + a.targetMonth) <= :toOrdinal)
           """)
    BigDecimal sumPostedAllocations(@Param("employerId") Long employerId,
                                    @Param("providerId") Long providerId,
                                    @Param("fromOrdinal") Integer fromOrdinal,
                                    @Param("toOrdinal") Integer toOrdinal);

    /** The same figure for every provider at once, so a per-provider list never asks per row. */
    @Query("""
           SELECT a.payment.providerId AS providerId, COALESCE(SUM(a.amount), 0) AS paidAmount
           FROM ProviderPaymentAllocation a
           WHERE a.payment.status = com.waad.tba.modules.settlement.entity.ProviderPayment.Status.POSTED
             AND (:employerId IS NULL OR a.employerId = :employerId)
             AND (:fromOrdinal IS NULL OR (a.targetYear * 100 + a.targetMonth) >= :fromOrdinal)
             AND (:toOrdinal IS NULL OR (a.targetYear * 100 + a.targetMonth) <= :toOrdinal)
           GROUP BY a.payment.providerId
           """)
    java.util.List<PaidByProvider> sumPostedAllocationsGroupedByProvider(
            @Param("employerId") Long employerId,
            @Param("fromOrdinal") Integer fromOrdinal,
            @Param("toOrdinal") Integer toOrdinal);

    interface PaidByProvider {
        Long getProviderId();
        BigDecimal getPaidAmount();
    }
}
