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
               coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0) as amount
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(approved_amount) as reversed_amount
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId
           and c.bucket_id in (:bucketIds)
           and c.status in ('COMMITTED', 'RESERVED')
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
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

    @Query(value = """
        select coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(approved_amount) as reversed_amount
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end = :periodEnd
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
        """, nativeQuery = true)
    BigDecimal sumCommittedAmountBounded(@Param("memberId") Long memberId,
                                         @Param("bucketId") Long bucketId,
                                         @Param("periodStart") LocalDate periodStart,
                                         @Param("periodEnd") LocalDate periodEnd,
                                         @Param("excludeClaimId") Long excludeClaimId);

    @Query(value = """
        select coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(approved_amount) as reversed_amount
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end is null
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
        """, nativeQuery = true)
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

    @Query(value = """
        select coalesce(sum(c.times_consumed), 0)
          from benefit_bucket_consumptions c
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end = :periodEnd
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
        """, nativeQuery = true)
    Integer sumCommittedTimesBounded(@Param("memberId") Long memberId,
                                     @Param("bucketId") Long bucketId,
                                     @Param("periodStart") LocalDate periodStart,
                                     @Param("periodEnd") LocalDate periodEnd,
                                     @Param("excludeClaimId") Long excludeClaimId);

    @Query(value = """
        select coalesce(sum(c.times_consumed), 0)
          from benefit_bucket_consumptions c
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end is null
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
        """, nativeQuery = true)
    Integer sumCommittedTimesOpenEnded(@Param("memberId") Long memberId,
                                       @Param("bucketId") Long bucketId,
                                       @Param("periodStart") LocalDate periodStart,
                                       @Param("excludeClaimId") Long excludeClaimId);

    /**
     * CLAIM-SCOPED BY DESIGN. A "service day" is a fact of the claim, so these
     * read c.claim.serviceDate and therefore INNER JOIN through claim --
     * deliberately excluding claim-less movements (a PREAUTH hold, an
     * OPENING_IMPORT balance), which carry no service date to count.
     *
     * Do NOT reuse them as "all committed consumption": since V174 that is a
     * strictly larger set. A read that means the whole ledger must filter on
     * sourceType/limitScope, never on the presence of a claim.
     */
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

    /**
     * Every hold this pre-authorization still has outstanding. Ordered by id
     * so a release walks them deterministically.
     */
    @Query(value = """
        select * from benefit_bucket_consumptions c
         where c.preauth_id = :preauthId
           and c.status = 'RESERVED'
         order by c.id
        """, nativeQuery = true)
    List<BenefitBucketConsumption> findActiveReservationsForPreauth(@Param("preauthId") Long preauthId);

    /**
     * How much of an original has already been given back. A release must
     * return what is OUTSTANDING: releasing the original amount blindly would
     * give back more than is held whenever part of it was already returned.
     */
    @Query(value = """
        select coalesce(sum(approved_amount), 0) from benefit_bucket_consumptions
         where reversal_of_id = :originalId and status = 'REVERSED'
        """, nativeQuery = true)
    BigDecimal sumReleasedAmount(@Param("originalId") Long originalId);

    @Query(value = """
        select coalesce(sum(times_consumed), 0) from benefit_bucket_consumptions
         where reversal_of_id = :originalId and status = 'REVERSED'
        """, nativeQuery = true)
    Integer sumReleasedTimes(@Param("originalId") Long originalId);

    /**
     * Net RESERVED times held against a bucket. A separate dimension from the
     * amount: a visit count and a currency figure are not comparable, and a
     * decision can be constrained by either independently.
     */
    @Query(value = """
        select coalesce(sum(c.times_consumed), 0)
          from benefit_bucket_consumptions c
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'RESERVED'
           and c.period_start = :periodStart
           and c.period_end is not distinct from cast(:periodEnd as date)
           -- A released hold no longer counts: its compensating movement
           -- carries the times it gave back.
           and not exists (
                select 1 from benefit_bucket_consumptions r
                 where r.reversal_of_id = c.id and r.status = 'REVERSED')
        """, nativeQuery = true)
    Integer sumReservedTimes(@Param("memberId") Long memberId,
                             @Param("bucketId") Long bucketId,
                             @Param("periodStart") LocalDate periodStart,
                             @Param("periodEnd") LocalDate periodEnd);

    /**
     * Net RESERVED amount held against the POLICY_GENERAL ceiling. Read from
     * its OWN rows -- never derived by summing bucket rows, since one line can
     * map to several buckets and that sum would count the same money more than
     * once (LimitBalanceReader carried exactly this warning as a comment while
     * the ledger could not yet store such rows).
     */
    @Query(value = """
        select coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(approved_amount) as reversed_amount
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId
           and c.policy_id = :policyId
           and c.limit_scope = 'POLICY_GENERAL'
           and c.status = 'RESERVED'
           and c.period_start = :periodStart
           -- Null-safe EQUALITY: an open-ended period matches an open-ended
           -- row, not every row. Treating a null bound as "any period" would
           -- sum holds from other periods into this one and understate what
           -- the member may spend.
           and c.period_end is not distinct from cast(:periodEnd as date)
        """, nativeQuery = true)
    BigDecimal sumGeneralScopeReserved(@Param("memberId") Long memberId,
                                       @Param("policyId") Long policyId,
                                       @Param("periodStart") LocalDate periodStart,
                                       @Param("periodEnd") LocalDate periodEnd);
}
