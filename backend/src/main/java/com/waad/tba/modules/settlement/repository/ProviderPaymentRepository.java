package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.ProviderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ProviderPaymentRepository extends JpaRepository<ProviderPayment, Long> {

    /** Idempotent replay: the same request key must return the original payment. */
    Optional<ProviderPayment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Loads a payment together with its allocations in one query. Needed wherever
     * the allocation breakdown is read outside an open session (detail drawer,
     * posting validation), and avoids an N+1 over allocations.
     */
    @Query("SELECT p FROM ProviderPayment p LEFT JOIN FETCH p.allocations WHERE p.id = :id")
    Optional<ProviderPayment> findByIdWithAllocations(@Param("id") Long id);

    /** Lock order during posting: provider account first, then this payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT p FROM ProviderPayment p LEFT JOIN FETCH p.allocations WHERE p.id = :id")
    Optional<ProviderPayment> findByIdWithAllocationsForUpdate(@Param("id") Long id);

    List<ProviderPayment> findByProviderIdOrderByPaymentDateDesc(Long providerId);

    /**
     * What the provider has actually been paid, per the payment documents.
     * Only POSTED counts: drafts have no ledger effect and reversals are
     * neutralised. This is one side of the reconciliation invariant
     * (the other being the ledger).
     */
    @Query("""
           SELECT COALESCE(SUM(p.amount), 0) FROM ProviderPayment p
           WHERE p.providerId = :providerId AND p.status = com.waad.tba.modules.settlement.entity.ProviderPayment.Status.POSTED
           """)
    BigDecimal sumPostedAmountByProvider(@Param("providerId") Long providerId);

    /**
     * Aggregated in one query per provider rather than loading every payment and
     * filtering in memory — the defect in the old monthly-summary path.
     */
    @Query("""
           SELECT p.providerId AS providerId, COALESCE(SUM(p.amount), 0) AS postedAmount, COUNT(p) AS postedCount
           FROM ProviderPayment p
           WHERE p.status = com.waad.tba.modules.settlement.entity.ProviderPayment.Status.POSTED
           GROUP BY p.providerId
           """)
    List<PostedTotalByProvider> sumPostedAmountGroupedByProvider();

    interface PostedTotalByProvider {
        Long getProviderId();
        BigDecimal getPostedAmount();
        Long getPostedCount();
    }

    /**
     * Provider liability by employer/service month, net of allocations already
     * attached to POSTED provider payments. DRAFT and REVERSED payments have no
     * allocation effect. The query is deliberately aggregated in PostgreSQL: this
     * is the hot input to FIFO and must not load all claims/payments into memory.
     */
    @Query(value = """
            WITH due AS (
                SELECT m.employer_id,
                       EXTRACT(YEAR FROM c.service_date)::int AS target_year,
                       EXTRACT(MONTH FROM c.service_date)::int AS target_month,
                       SUM(COALESCE(c.net_provider_amount, c.approved_amount, 0)) AS due_amount
                FROM claims c
                JOIN members m ON m.id = c.member_id
                WHERE c.active = true
                  AND c.provider_id = :providerId
                  AND c.service_date <= :asOfDate
                  AND c.status IN ('APPROVED', 'BATCHED', 'SETTLED')
                GROUP BY m.employer_id,
                         EXTRACT(YEAR FROM c.service_date),
                         EXTRACT(MONTH FROM c.service_date)
            ), allocated AS (
                SELECT a.employer_id, a.target_year, a.target_month,
                       SUM(a.amount) AS allocated_amount
                FROM provider_payment_allocations a
                JOIN provider_payments p ON p.id = a.payment_id
                WHERE p.provider_id = :providerId
                  AND p.status = 'POSTED'
                  AND p.payment_date <= :asOfDate
                GROUP BY a.employer_id, a.target_year, a.target_month
            )
            SELECT d.employer_id AS "employerId",
                   d.target_year AS "targetYear",
                   d.target_month AS "targetMonth",
                   GREATEST(d.due_amount - COALESCE(a.allocated_amount, 0), 0) AS "outstandingAmount"
            FROM due d
            LEFT JOIN allocated a
              ON a.employer_id = d.employer_id
             AND a.target_year = d.target_year
             AND a.target_month = d.target_month
            WHERE d.due_amount - COALESCE(a.allocated_amount, 0) > 0
            ORDER BY d.target_year, d.target_month, d.employer_id
            """, nativeQuery = true)
    List<OutstandingPeriod> findOutstandingPeriodsForAllocation(
            @Param("providerId") Long providerId, @Param("asOfDate") LocalDate asOfDate);

    interface OutstandingPeriod {
        Long getEmployerId();
        Integer getTargetYear();
        Integer getTargetMonth();
        BigDecimal getOutstandingAmount();
    }
}
