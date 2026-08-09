package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BenefitBucketConsumptionRepository extends JpaRepository<BenefitBucketConsumption, Long> {
    boolean existsByClaimId(Long claimId);

    interface BucketAmountBalanceProjection {
        Long getBucketId();
        LocalDate getPeriodStart();
        LocalDate getPeriodEnd();
        String getStatus();
        BigDecimal getAmount();
    }

    @Query(value = """
        select c.bucket_id as bucketId,
               c.period_start as periodStart,
               c.period_end as periodEnd,
               c.status as status,
               coalesce(sum(c.approved_amount), 0) as amount
          from benefit_bucket_consumptions c
         where c.member_id = :memberId
           and c.bucket_id in (:bucketIds)
           and c.status in ('COMMITTED', 'RESERVED')
           and (:excludeClaimId is null or c.claim_id <> :excludeClaimId)
         group by c.bucket_id, c.period_start, c.period_end, c.status
        """, nativeQuery = true)
    List<BucketAmountBalanceProjection> aggregateAmountBalances(
            @Param("memberId") Long memberId,
            @Param("bucketIds") java.util.Collection<Long> bucketIds,
            @Param("excludeClaimId") Long excludeClaimId);

    /**
     * Fail-closed guard for legacy/partially deployed claims. An approved claim
     * with bucket-backed lines but without a committed ledger entry must never be
     * silently ignored when approving a later claim for the same member.
     *
     * Only lines with a positive company_share are considered a financial risk: a
     * claim/line that was rejected for exhausting the benefit limit (or has no
     * qualifying amount) never actually consumed the bucket, so it must not
     * permanently block every later claim for the same member just because it
     * has no COMMITTED ledger row — there was never anything to commit.
     */
    @Query(value = """
        select exists (
            select 1
              from claims c
              join claim_lines cl on cl.claim_id = c.id
             where c.member_id = :memberId
               and c.id <> :currentClaimId
               and c.active = true
               and c.status in ('APPROVED', 'BATCHED', 'SETTLED')
               and cl.applied_rule_id is not null
               and coalesce(cl.company_share, 0) > 0
               and exists (
                   select 1
                     from benefit_rule_buckets brb
                     join benefit_limit_buckets b on b.id = brb.bucket_id and b.active = true
                     left join benefit_groups g on g.id = b.benefit_group_id
                    where brb.rule_id = cl.applied_rule_id
                      and not (
                          :annualLimit is not null
                          and b.period_type = 'ANNUAL'
                          and b.amount_limit = :annualLimit
                          and (upper(b.code) = 'B-GENERAL' or upper(g.code) = 'G-GENERAL')
                      )
               )
               and not exists (
                   select 1 from benefit_bucket_consumptions bc
                    where bc.claim_line_id = cl.id and bc.status = 'COMMITTED'
               )
        )
        """, nativeQuery = true)
    boolean existsUnledgeredApprovedBucketClaim(@Param("memberId") Long memberId,
                                                  @Param("currentClaimId") Long currentClaimId,
                                                  @Param("annualLimit") BigDecimal annualLimit);

    default BigDecimal sumCommittedAmount(Long memberId, Long bucketId, LocalDate periodStart,
                                          LocalDate periodEnd, Long excludeClaimId) {
        return periodEnd == null
                ? sumCommittedAmountOpenEnded(memberId, bucketId, periodStart, excludeClaimId)
                : sumCommittedAmountBounded(memberId, bucketId, periodStart, periodEnd, excludeClaimId);
    }

    @Query("""
        select coalesce(sum(c.approvedAmount), 0) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and c.periodEnd = :periodEnd
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    BigDecimal sumCommittedAmountBounded(@Param("memberId") Long memberId,
                                         @Param("bucketId") Long bucketId,
                                         @Param("periodStart") LocalDate periodStart,
                                         @Param("periodEnd") LocalDate periodEnd,
                                         @Param("excludeClaimId") Long excludeClaimId);

    @Query("""
        select coalesce(sum(c.approvedAmount), 0) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and c.periodEnd is null
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    BigDecimal sumCommittedAmountOpenEnded(@Param("memberId") Long memberId,
                                           @Param("bucketId") Long bucketId,
                                           @Param("periodStart") LocalDate periodStart,
                                           @Param("excludeClaimId") Long excludeClaimId);

    default Integer sumCommittedTimes(Long memberId, Long bucketId, LocalDate periodStart,
                                      LocalDate periodEnd, Long excludeClaimId) {
        return periodEnd == null
                ? sumCommittedTimesOpenEnded(memberId, bucketId, periodStart, excludeClaimId)
                : sumCommittedTimesBounded(memberId, bucketId, periodStart, periodEnd, excludeClaimId);
    }

    @Query("""
        select coalesce(sum(c.timesConsumed), 0) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and c.periodEnd = :periodEnd
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    Integer sumCommittedTimesBounded(@Param("memberId") Long memberId,
                                     @Param("bucketId") Long bucketId,
                                     @Param("periodStart") LocalDate periodStart,
                                     @Param("periodEnd") LocalDate periodEnd,
                                     @Param("excludeClaimId") Long excludeClaimId);

    @Query("""
        select coalesce(sum(c.timesConsumed), 0) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and c.periodEnd is null
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    Integer sumCommittedTimesOpenEnded(@Param("memberId") Long memberId,
                                       @Param("bucketId") Long bucketId,
                                       @Param("periodStart") LocalDate periodStart,
                                       @Param("excludeClaimId") Long excludeClaimId);

    default Long countCommittedServiceDays(Long memberId, Long bucketId, LocalDate periodStart,
                                           LocalDate periodEnd, Long excludeClaimId) {
        return periodEnd == null
                ? countCommittedServiceDaysOpenEnded(memberId, bucketId, periodStart, excludeClaimId)
                : countCommittedServiceDaysBounded(memberId, bucketId, periodStart, periodEnd, excludeClaimId);
    }

    @Query("""
        select count(distinct c.claim.serviceDate) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and c.periodEnd = :periodEnd
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    Long countCommittedServiceDaysBounded(@Param("memberId") Long memberId,
                                          @Param("bucketId") Long bucketId,
                                          @Param("periodStart") LocalDate periodStart,
                                          @Param("periodEnd") LocalDate periodEnd,
                                          @Param("excludeClaimId") Long excludeClaimId);

    @Query("""
        select count(distinct c.claim.serviceDate) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and c.periodEnd is null
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    Long countCommittedServiceDaysOpenEnded(@Param("memberId") Long memberId,
                                            @Param("bucketId") Long bucketId,
                                            @Param("periodStart") LocalDate periodStart,
                                            @Param("excludeClaimId") Long excludeClaimId);

    @Query("""
        select count(c) > 0 from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.claim.serviceDate = :serviceDate
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    boolean existsCommittedForServiceDay(@Param("memberId") Long memberId,
                                         @Param("bucketId") Long bucketId,
                                         @Param("serviceDate") LocalDate serviceDate,
                                         @Param("excludeClaimId") Long excludeClaimId);

    List<BenefitBucketConsumption> findByClaimIdAndStatus(Long claimId, BenefitBucketConsumption.Status status);
    boolean existsByBucketId(Long bucketId);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
