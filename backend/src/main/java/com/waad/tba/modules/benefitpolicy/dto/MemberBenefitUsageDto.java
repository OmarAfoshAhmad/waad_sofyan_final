package com.waad.tba.modules.benefitpolicy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MemberBenefitUsageDto(
        Long memberId, String memberName, String cardNumber, String membershipStatus,
        Long policyId, String policyCode, String policyName, LocalDate asOfDate,
        List<BucketUsage> buckets) {

    public record BucketUsage(Long bucketId, String bucketCode, String bucketName,
            Long parentBucketId, String groupName, LocalDate periodStart, LocalDate periodEnd,
            BigDecimal amountLimit, BigDecimal claimUsedAmount, BigDecimal openingUsedAmount,
            BigDecimal usedAmount, BigDecimal remainingAmount, BigDecimal usagePercent,
            Integer timesLimit, Integer usedTimes, Integer remainingTimes,
            Integer daysLimit, Long usedDays, Long remainingDays, String status) {}
}

