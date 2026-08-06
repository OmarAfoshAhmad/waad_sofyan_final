package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BenefitBucketAdjustmentRepository extends JpaRepository<BenefitBucketAdjustment, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    default UsageTotals sumActive(Long memberId, Long bucketId, LocalDate periodStart, LocalDate periodEnd) {
        return periodEnd == null
                ? sumActiveOpenEnded(memberId, bucketId, periodStart)
                : sumActiveBounded(memberId, bucketId, periodStart, periodEnd);
    }

    @Query("""
        select new com.waad.tba.modules.benefitpolicy.repository.BenefitBucketAdjustmentRepository$UsageTotals(
            coalesce(sum(a.amountDelta), 0), coalesce(sum(a.timesDelta), 0), coalesce(sum(a.daysDelta), 0))
        from BenefitBucketAdjustment a
        where a.memberId = :memberId and a.bucket.id = :bucketId
          and a.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketAdjustment.Status.ACTIVE
          and a.periodStart = :periodStart and a.periodEnd = :periodEnd
        """)
    UsageTotals sumActiveBounded(@Param("memberId") Long memberId,
                                 @Param("bucketId") Long bucketId,
                                 @Param("periodStart") LocalDate periodStart,
                                 @Param("periodEnd") LocalDate periodEnd);

    @Query("""
        select new com.waad.tba.modules.benefitpolicy.repository.BenefitBucketAdjustmentRepository$UsageTotals(
            coalesce(sum(a.amountDelta), 0), coalesce(sum(a.timesDelta), 0), coalesce(sum(a.daysDelta), 0))
        from BenefitBucketAdjustment a
        where a.memberId = :memberId and a.bucket.id = :bucketId
          and a.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketAdjustment.Status.ACTIVE
          and a.periodStart = :periodStart and a.periodEnd is null
        """)
    UsageTotals sumActiveOpenEnded(@Param("memberId") Long memberId,
                                   @Param("bucketId") Long bucketId,
                                   @Param("periodStart") LocalDate periodStart);

    record UsageTotals(BigDecimal amount, Long times, Long days) {
        public UsageTotals {
            amount = amount == null ? BigDecimal.ZERO : amount;
            times = times == null ? 0L : times;
            days = days == null ? 0L : days;
        }
    }
}

