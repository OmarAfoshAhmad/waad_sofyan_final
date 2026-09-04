package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        boolean directlyLinked,
        // The limit cycle this snapshot was resolved against. Part of the
        // claims usage-accumulator key so two different cycles on the same
        // bucket (e.g. an annual reset) never merge into one running total.
        LocalDate periodStart,
        LocalDate periodEnd) {
}
