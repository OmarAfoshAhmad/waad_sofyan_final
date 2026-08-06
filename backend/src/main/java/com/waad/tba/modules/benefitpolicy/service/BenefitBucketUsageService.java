package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketAdjustmentRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Single read path for claim-ledger usage plus non-financial opening usage. */
@Service
@RequiredArgsConstructor
public class BenefitBucketUsageService {
    private final BenefitBucketConsumptionRepository consumptionRepository;
    private final BenefitBucketAdjustmentRepository adjustmentRepository;

    @Transactional(readOnly = true)
    public UsageTotals totals(Long memberId, Long bucketId, LocalDate periodStart,
                              LocalDate periodEnd, Long excludeClaimId) {
        return breakdown(memberId, bucketId, periodStart, periodEnd, excludeClaimId).totals();
    }

    @Transactional(readOnly = true)
    public UsageBreakdown breakdown(Long memberId, Long bucketId, LocalDate periodStart,
                                    LocalDate periodEnd, Long excludeClaimId) {
        BigDecimal claimAmount = consumptionRepository.sumCommittedAmount(
                memberId, bucketId, periodStart, periodEnd, excludeClaimId);
        Integer claimTimes = consumptionRepository.sumCommittedTimes(
                memberId, bucketId, periodStart, periodEnd, excludeClaimId);
        Long claimDays = consumptionRepository.countCommittedServiceDays(
                memberId, bucketId, periodStart, periodEnd, excludeClaimId);
        BenefitBucketAdjustmentRepository.UsageTotals opening = adjustmentRepository.sumActive(
                memberId, bucketId, periodStart, periodEnd);
        UsageTotals claims = new UsageTotals(safe(claimAmount), safe(claimTimes), safe(claimDays));
        UsageTotals adjustments = new UsageTotals(opening.amount(), Math.toIntExact(opening.times()), opening.days());
        return new UsageBreakdown(claims, adjustments, new UsageTotals(
                claims.amount().add(adjustments.amount()),
                claims.times() + adjustments.times(), claims.days() + adjustments.days()));
    }

    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private int safe(Integer value) { return value == null ? 0 : value; }
    private long safe(Long value) { return value == null ? 0L : value; }

    public record UsageTotals(BigDecimal amount, Integer times, Long days) {}
    public record UsageBreakdown(UsageTotals claims, UsageTotals adjustments, UsageTotals totals) {}
}
