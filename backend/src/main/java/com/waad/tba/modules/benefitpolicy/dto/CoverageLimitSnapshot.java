package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import lombok.Builder;

import java.math.BigDecimal;

/** Immutable, transport-safe view of an applicable benefit limit. */
@Builder
public record CoverageLimitSnapshot(
        Long bucketId,
        String bucketName,
        BigDecimal amountLimit,
        Integer timesLimit,
        Integer daysLimit,
        BigDecimal usedAmount,
        Integer usedTimes,
        Integer usedDays,
        boolean serviceDayAlreadyUsed,
        CountingMethod countingMethod,
        ConsumptionBasis consumptionBasis,
        boolean directlyLinked) {
}
