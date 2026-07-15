package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BenefitBucketConsumptionRepository extends JpaRepository<BenefitBucketConsumption, Long> {
    @Query("""
        select coalesce(sum(c.approvedAmount), 0) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and ((:periodEnd is null and c.periodEnd is null) or c.periodEnd = :periodEnd)
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    BigDecimal sumCommittedAmount(@Param("memberId") Long memberId,
                                  @Param("bucketId") Long bucketId,
                                  @Param("periodStart") LocalDate periodStart,
                                  @Param("periodEnd") LocalDate periodEnd,
                                  @Param("excludeClaimId") Long excludeClaimId);

    @Query("""
        select coalesce(sum(c.timesConsumed), 0) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and ((:periodEnd is null and c.periodEnd is null) or c.periodEnd = :periodEnd)
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    Integer sumCommittedTimes(@Param("memberId") Long memberId,
                              @Param("bucketId") Long bucketId,
                              @Param("periodStart") LocalDate periodStart,
                              @Param("periodEnd") LocalDate periodEnd,
                              @Param("excludeClaimId") Long excludeClaimId);

    @Query("""
        select count(distinct c.claim.serviceDate) from BenefitBucketConsumption c
        where c.memberId = :memberId and c.bucket.id = :bucketId and c.status = com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED
          and c.periodStart = :periodStart
          and ((:periodEnd is null and c.periodEnd is null) or c.periodEnd = :periodEnd)
          and (:excludeClaimId is null or c.claim.id <> :excludeClaimId)
        """)
    Long countCommittedServiceDays(@Param("memberId") Long memberId,
                                   @Param("bucketId") Long bucketId,
                                   @Param("periodStart") LocalDate periodStart,
                                   @Param("periodEnd") LocalDate periodEnd,
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
    boolean existsByIdempotencyKey(String idempotencyKey);
}
