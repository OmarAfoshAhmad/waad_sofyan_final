package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.ProviderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
}
