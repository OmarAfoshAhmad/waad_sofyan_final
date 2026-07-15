package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class BenefitStructureDtos {
    private BenefitStructureDtos() {}

    public record GroupRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String nameAr,
            @NotNull EncounterType contextType,
            @NotNull AggregationMode aggregationMode,
            Boolean active) {}

    public record GroupResponse(Long id, String code, String nameAr, EncounterType contextType,
                                AggregationMode aggregationMode, Integer coveragePercent,
                                BigDecimal copayPercentage, boolean requiresPreApproval,
                                String notes, String sourceClause, boolean active) {}

    public record BucketRequest(
            @NotNull Long benefitGroupId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String nameAr,
            @NotNull EncounterType contextType,
            @DecimalMin("0.00") BigDecimal amountLimit,
            @Min(0) Integer timesLimit,
            @Min(0) Integer daysLimit,
            @NotNull LimitPeriodType periodType,
            @Min(1) Integer periodValue,
            @NotNull CountingMethod countingMethod,
            @NotNull ConsumptionBasis consumptionBasis,
            Long parentBucketId,
            Boolean shared,
            Boolean active) {}

    public record BucketResponse(Long id, Long benefitGroupId, String code, String nameAr,
                                 EncounterType contextType, BigDecimal amountLimit, Integer timesLimit,
                                 Integer daysLimit, LimitPeriodType periodType, Integer periodValue, CountingMethod countingMethod,
                                 ConsumptionBasis consumptionBasis, Long parentBucketId, boolean shared,
                                 boolean active) {}

    public record RuleBucketRequest(@NotNull Long bucketId, @Min(1) Integer consumptionOrder,
                                    @NotNull ConsumptionMode consumptionMode, Boolean mandatory) {}

    public record RuleBucketResponse(Long id, Long ruleId, BucketResponse bucket,
                                    Integer consumptionOrder, ConsumptionMode consumptionMode,
                                    boolean mandatory) {}

    public record StructureResponse(List<GroupResponse> groups, List<BucketResponse> buckets,
                                    List<RuleBucketResponse> links) {}
}
