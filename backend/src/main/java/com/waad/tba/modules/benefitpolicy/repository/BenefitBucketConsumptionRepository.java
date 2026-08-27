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

    /**
     * The same fail-closed guard as {@link #existsUnledgeredApprovedBucketClaim},
     * for the POLICY_GENERAL ceiling instead of a bucket. Reading the ceiling
     * from the ledger (V189) instead of {@code claim_lines} is only safe once
     * every APPROVED/BATCHED/SETTLED claim that ever spent it actually posted a
     * COMMITTED POLICY_GENERAL row -- {@code postGeneralCeiling} writes one row
     * per claim (via its claim_id), never per bucket, so this checks existence
     * at the claim level directly, with no bucket join at all.
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
               and coalesce(cl.limit_consumption, 0) > 0
               and :annualLimit is not null
               and not exists (
                   select 1 from benefit_bucket_consumptions bc
                    where bc.claim_id = c.id and bc.limit_scope = 'POLICY_GENERAL' and bc.status = 'COMMITTED'
               )
        )
        """, nativeQuery = true)
    boolean existsUnledgeredApprovedGeneralClaim(@Param("memberId") Long memberId,
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
        select coalesce(sum(c.times_consumed - coalesce(r.released_times, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(times_consumed) as released_times
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
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
        select coalesce(sum(c.times_consumed - coalesce(r.released_times, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(times_consumed) as released_times
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
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

    @Query(value = """
        select count(distinct cl.service_date)
          from benefit_bucket_consumptions c
          join claims cl on cl.id = c.claim_id
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end = :periodEnd
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
           -- A day counts only while SOME consumption on it is still
           -- outstanding. Counting a day whose claims were all reversed
           -- fails twice over: it rejects a legitimate new day, and callers
           -- that skip the day check when the day "already exists" then let a
           -- claim onto a day that is really free.
           -- The subquery mirrors EVERY filter of the outer query. Without
           -- the period bound it would keep a day alive from another year;
           -- without excludeClaimId the claim being re-adjudicated would
           -- hold its own day open and count against itself.
           --
           -- And it tests BOTH dimensions. Money and occurrences are released
           -- independently, so a day whose money was fully returned may still
           -- have visits outstanding, and the reverse. A day is free only when
           -- nothing at all is left on it.
           and exists (
                select 1 from benefit_bucket_consumptions n
                 left join (
                       select reversal_of_id,
                              sum(approved_amount) as released,
                              sum(times_consumed) as released_times
                         from benefit_bucket_consumptions
                        where status = 'REVERSED' and reversal_of_id is not null
                        group by reversal_of_id
                 ) nr on nr.reversal_of_id = n.id
                 join claims nc on nc.id = n.claim_id
                where n.member_id = c.member_id
                  and n.bucket_id = c.bucket_id
                  and n.status = 'COMMITTED'
                  and n.period_start = c.period_start
                  and n.period_end is not distinct from c.period_end
                  and nc.service_date = cl.service_date
                  and (:excludeClaimId is null or n.claim_id is distinct from :excludeClaimId)
                  and ((n.approved_amount - coalesce(nr.released, 0)) > 0
                       or (coalesce(n.times_consumed, 0)
                           - coalesce(nr.released_times, 0)) > 0))
        """, nativeQuery = true)
    Long countCommittedServiceDaysBounded(@Param("memberId") Long memberId,
                                          @Param("bucketId") Long bucketId,
                                          @Param("periodStart") LocalDate periodStart,
                                          @Param("periodEnd") LocalDate periodEnd,
                                          @Param("excludeClaimId") Long excludeClaimId);

    @Query(value = """
        select count(distinct cl.service_date)
          from benefit_bucket_consumptions c
          join claims cl on cl.id = c.claim_id
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end is null
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
           -- The subquery mirrors EVERY filter of the outer query. Without
           -- the period bound it would keep a day alive from another year;
           -- without excludeClaimId the claim being re-adjudicated would
           -- hold its own day open and count against itself.
           --
           -- And it tests BOTH dimensions. Money and occurrences are released
           -- independently, so a day whose money was fully returned may still
           -- have visits outstanding, and the reverse. A day is free only when
           -- nothing at all is left on it.
           and exists (
                select 1 from benefit_bucket_consumptions n
                 left join (
                       select reversal_of_id,
                              sum(approved_amount) as released,
                              sum(times_consumed) as released_times
                         from benefit_bucket_consumptions
                        where status = 'REVERSED' and reversal_of_id is not null
                        group by reversal_of_id
                 ) nr on nr.reversal_of_id = n.id
                 join claims nc on nc.id = n.claim_id
                where n.member_id = c.member_id
                  and n.bucket_id = c.bucket_id
                  and n.status = 'COMMITTED'
                  and n.period_start = c.period_start
                  and n.period_end is not distinct from c.period_end
                  and nc.service_date = cl.service_date
                  and (:excludeClaimId is null or n.claim_id is distinct from :excludeClaimId)
                  and ((n.approved_amount - coalesce(nr.released, 0)) > 0
                       or (coalesce(n.times_consumed, 0)
                           - coalesce(nr.released_times, 0)) > 0))
        """, nativeQuery = true)
    Long countCommittedServiceDaysOpenEnded(@Param("memberId") Long memberId,
                                            @Param("bucketId") Long bucketId,
                                            @Param("periodStart") LocalDate periodStart,
                                            @Param("excludeClaimId") Long excludeClaimId);

    @Query(value = """
        select count(*) > 0
          from benefit_bucket_consumptions c
          join claims cl on cl.id = c.claim_id
          left join (
                select reversal_of_id,
                       sum(approved_amount) as released,
                       sum(times_consumed) as released_times
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'COMMITTED'
           and cl.service_date = :serviceDate
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
           -- Outstanding, not merely posted. Answering "yes" for a fully
           -- reversed day makes callers skip the day limit entirely for that
           -- date, which is how a claim gets onto a day past the ceiling.
           --
           -- Both dimensions, because they are released independently: a day
           -- whose money came back may still hold visits, and the reverse.
           and ((c.approved_amount - coalesce(r.released, 0)) > 0
                or (coalesce(c.times_consumed, 0)
                    - coalesce(r.released_times, 0)) > 0)
        """, nativeQuery = true)
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
     * What ONE pre-authorization still holds against one bucket and period --
     * net of anything already released.
     *
     * Scoped by preauth_id AND member AND bucket AND period on purpose. A
     * looser scope would return a neighbouring approval's hold, letting one
     * approval spend limit promised to another; a scope that ignored prior
     * releases would hand back money that has already been given back.
     */
    @Query(value = """
        select coalesce(sum(c.approved_amount - coalesce(r.released, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(approved_amount) as released
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId
           and c.preauth_id = :preauthId
           and c.member_policy_assignment_id = :assignmentId
           and c.bucket_id is not distinct from :bucketId
           -- Explicit even though bucket_id already separates the two scopes
           -- (V174 forbids a general row from carrying a bucket): a rule that
           -- holds by implication is one refactor away from not holding.
           and c.limit_scope = :limitScope
           and c.status = 'RESERVED'
           and c.period_start = :periodStart
           and c.period_end is not distinct from cast(:periodEnd as date)
        """, nativeQuery = true)
    BigDecimal sumOwnActiveReservation(@Param("memberId") Long memberId,
                                       @Param("preauthId") Long preauthId,
                                       @Param("assignmentId") Long assignmentId,
                                       @Param("bucketId") Long bucketId,
                                       @Param("limitScope") String limitScope,
                                       @Param("periodStart") LocalDate periodStart,
                                       @Param("periodEnd") LocalDate periodEnd);

    /**
     * The occurrence half of the same ownership question, on exactly the same
     * dimensions. A claim converting its own approval must see its own held
     * VISITS returned too -- otherwise an approval that took the last visit
     * blocks the very claim it was granted for.
     */
    @Query(value = """
        select coalesce(sum(c.times_consumed - coalesce(r.released_times, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(times_consumed) as released_times
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId
           and c.preauth_id = :preauthId
           and c.member_policy_assignment_id = :assignmentId
           and c.bucket_id is not distinct from :bucketId
           and c.limit_scope = :limitScope
           and c.status = 'RESERVED'
           and c.period_start = :periodStart
           and c.period_end is not distinct from cast(:periodEnd as date)
        """, nativeQuery = true)
    Integer sumOwnActiveReservationTimes(@Param("memberId") Long memberId,
                                         @Param("preauthId") Long preauthId,
                                         @Param("assignmentId") Long assignmentId,
                                         @Param("bucketId") Long bucketId,
                                         @Param("limitScope") String limitScope,
                                         @Param("periodStart") LocalDate periodStart,
                                         @Param("periodEnd") LocalDate periodEnd);

    /**
     * Net RESERVED times held against a bucket. A separate dimension from the
     * amount: a visit count and a currency figure are not comparable, and a
     * decision can be constrained by either independently.
     */
    @Query(value = """
        select coalesce(sum(c.times_consumed - coalesce(r.released_times, 0)), 0)
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(times_consumed) as released_times
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id = :memberId and c.bucket_id = :bucketId and c.status = 'RESERVED'
           and c.period_start = :periodStart
           and c.period_end is not distinct from cast(:periodEnd as date)
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

    /**
     * Net COMMITTED amount spent against the POLICY_GENERAL ceiling, from
     * whatever spent it.
     *
     * This is the half that used to be summed out of claim_lines while its
     * reserved counterpart was read here, so one ceiling's arithmetic came
     * from two places and only one of them was the ledger. Anything general
     * that is not a claim -- an imported opening balance above all -- could
     * not be represented in the claim-table half at all, short of fabricating
     * a claim for it.
     *
     * Source-agnostic on purpose: a claim, an opening import and a manual
     * adjustment all spend the same ceiling, and a filter on source_type here
     * would silently exclude whichever kind was added last.
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
           and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end is not distinct from cast(:periodEnd as date)
           -- The claim being adjudicated must not count against itself. An
           -- opening balance carries no claim_id, and `is distinct from`
           -- keeps a null on the left TRUE here -- so it stays counted, which
           -- is the whole reason it was imported.
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
        """, nativeQuery = true)
    BigDecimal sumGeneralScopeCommitted(@Param("memberId") Long memberId,
                                        @Param("policyId") Long policyId,
                                        @Param("periodStart") LocalDate periodStart,
                                        @Param("periodEnd") LocalDate periodEnd,
                                        @Param("excludeClaimId") Long excludeClaimId);

    interface GeneralCeilingBulkProjection {
        Long getMemberId();
        Long getPolicyId();
        BigDecimal getAmount();
    }

    /**
     * Bulk counterpart of {@link #sumGeneralScopeCommitted} -- one query for an
     * entire batch of members instead of one per member, the same O(1) shape
     * {@code MemberFinancialSummaryService.getFinancialSummaries} already
     * guarantees for its other reads.
     *
     * Grouped by (member_id, policy_id) rather than pre-filtered to one policy
     * per member: a member who changed policies mid-period has committed rows
     * under BOTH policy ids in the same calendar window, and each policy's own
     * annual limit must only ever be measured against ITS OWN rows. The caller
     * is the one who knows which policy_id is the member's CURRENT one and
     * picks that row; a row for a policy the member has since left is not
     * silently added to a limit it was never spent against.
     */
    @Query(value = """
        select c.member_id as memberId, c.policy_id as policyId,
               coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0) as amount
          from benefit_bucket_consumptions c
          left join (
                select reversal_of_id, sum(approved_amount) as reversed_amount
                  from benefit_bucket_consumptions
                 where status = 'REVERSED' and reversal_of_id is not null
                 group by reversal_of_id
          ) r on r.reversal_of_id = c.id
         where c.member_id in (:memberIds)
           and c.limit_scope = 'POLICY_GENERAL'
           and c.status = 'COMMITTED'
           and c.period_start = :periodStart
           and c.period_end is not distinct from cast(:periodEnd as date)
           and (:excludeClaimId is null or c.claim_id is distinct from :excludeClaimId)
         group by c.member_id, c.policy_id
        """, nativeQuery = true)
    List<GeneralCeilingBulkProjection> sumGeneralScopeCommittedBulk(
            @Param("memberIds") java.util.Collection<Long> memberIds,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("excludeClaimId") Long excludeClaimId);
}
