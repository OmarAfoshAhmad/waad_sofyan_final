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
    List<OutstandingPeriod> findOutstandingPeriodsForSuggestion(
            @Param("providerId") Long providerId, @Param("asOfDate") LocalDate asOfDate);

    /**
     * Posting-time liability is deliberately not an as-of report. Every POSTED
     * allocation must be subtracted regardless of its bank/payment date, otherwise
     * a backdated draft can allocate an already-settled period a second time.
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
                  AND p.id <> :excludedPaymentId
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
    List<OutstandingPeriod> findOutstandingPeriodsForPosting(
            @Param("providerId") Long providerId,
            @Param("excludedPaymentId") Long excludedPaymentId);

    interface OutstandingPeriod {
        Long getEmployerId();
        Integer getTargetYear();
        Integer getTargetMonth();
        BigDecimal getOutstandingAmount();
    }

    /**
     * Puts the four figures that must agree side by side for one provider:
     * payment documents, the ledger, the stored account totals, and allocations.
     *
     * Everything is aggregated in PostgreSQL. Doing this in Java would mean
     * loading every payment, allocation and ledger row per provider — the exact
     * shape of the defect in the old monthly-summary path, which loaded all
     * active payments and filtered them in memory once per row.
     *
     * Reversed payments are excluded from documentsTotal and allocatedTotal
     * because their money was given back; the ledger nets them out through the
     * matching PROVIDER_PAYMENT_REVERSAL credit, so both sides stay comparable.
     */
    @Query(value = """
            SELECT pr.id                                  AS "providerId",
                   pr.name                                AS "providerName",
                   pa.id                                  AS "providerAccountId",
                   COALESCE(pa.total_paid, 0)             AS "accountTotalPaid",
                   COALESCE(pa.total_approved, 0)         AS "accountTotalApproved",
                   COALESCE(pa.running_balance, 0)        AS "accountRunningBalance",
                   COALESCE(doc.posted_total, 0)          AS "documentsTotal",
                   COALESCE(doc.posted_count, 0)          AS "documentsCount",
                   COALESCE(doc.draft_total, 0)           AS "draftTotal",
                   COALESCE(doc.draft_count, 0)           AS "draftCount",
                   COALESCE(led.ledger_net, 0)            AS "ledgerNet",
                   COALESCE(led.entry_count, 0)           AS "ledgerEntryCount",
                   COALESCE(alloc.allocated_total, 0)     AS "allocatedTotal"
            FROM providers pr
            LEFT JOIN provider_accounts pa ON pa.provider_id = pr.id
            LEFT JOIN (
                SELECT p.provider_id,
                       SUM(p.amount) FILTER (WHERE p.status = 'POSTED')  AS posted_total,
                       COUNT(*)      FILTER (WHERE p.status = 'POSTED')  AS posted_count,
                       SUM(p.amount) FILTER (WHERE p.status = 'DRAFT')   AS draft_total,
                       COUNT(*)      FILTER (WHERE p.status = 'DRAFT')   AS draft_count
                FROM provider_payments p
                GROUP BY p.provider_id
            ) doc ON doc.provider_id = pr.id
            LEFT JOIN (
                SELECT pa2.provider_id,
                       SUM(CASE WHEN at.reference_type = 'PROVIDER_PAYMENT'          THEN at.amount
                                WHEN at.reference_type = 'PROVIDER_PAYMENT_REVERSAL' THEN -at.amount
                                ELSE 0 END)                              AS ledger_net,
                       COUNT(*)                                          AS entry_count
                FROM account_transactions at
                JOIN provider_accounts pa2 ON pa2.id = at.provider_account_id
                WHERE at.reference_type IN ('PROVIDER_PAYMENT', 'PROVIDER_PAYMENT_REVERSAL')
                GROUP BY pa2.provider_id
            ) led ON led.provider_id = pr.id
            LEFT JOIN (
                SELECT p3.provider_id, SUM(a.amount) AS allocated_total
                FROM provider_payment_allocations a
                JOIN provider_payments p3 ON p3.id = a.payment_id
                WHERE p3.status = 'POSTED'
                GROUP BY p3.provider_id
            ) alloc ON alloc.provider_id = pr.id
            WHERE (:providerId IS NULL OR pr.id = :providerId)
              AND (pa.id IS NOT NULL OR doc.provider_id IS NOT NULL)
            ORDER BY pr.id
            """, nativeQuery = true)
    List<ReconciliationRow> findReconciliationRows(@Param("providerId") Long providerId);

    interface ReconciliationRow {
        Long getProviderId();
        String getProviderName();
        Long getProviderAccountId();
        BigDecimal getAccountTotalPaid();
        BigDecimal getAccountTotalApproved();
        BigDecimal getAccountRunningBalance();
        BigDecimal getDocumentsTotal();
        Long getDocumentsCount();
        BigDecimal getDraftTotal();
        Long getDraftCount();
        BigDecimal getLedgerNet();
        Long getLedgerEntryCount();
        BigDecimal getAllocatedTotal();
    }
}
