package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService.LimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.claim.dto.engine.CoverageResult.UsageDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 🛡️ CENTRAL FINANCIAL COVERAGE ENGINE (SINGLE SOURCE OF TRUTH)
 * 
 * Provides Unified Financial Calculations for both:
 * 1. UI Live Preview (BatchEntry / BatchGrid)
 * 2. Backend Entity Mapping (ClaimMapper)
 * 
 * LAW: All financial calculations MUST flow through evaluateLine().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageEngineService {

    private final BenefitPolicyRuleService benefitPolicyRuleService;
    private final BenefitBucketLimitService benefitBucketLimitService;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Batch calculation (used by /analyze endpoint)
     */
    public List<CoverageResult> calculateBulk(BulkCoverageEngineRequest request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            return List.of();
        }

        Map<Long, BatchUsageAccumulator> batchUsageContext = new HashMap<>();
        List<CoverageResult> results = new ArrayList<>(request.getLines().size());

        for (ClaimLineInput line : request.getLines()) {
            CoverageResult result = evaluateLine(request, line, batchUsageContext);
            results.add(result);
        }

        recordRecalculationAudit(request, results);

        return results;
    }

    public CoverageResult calculateSingle(BulkCoverageEngineRequest request, ClaimLineInput line) {
        return evaluateLine(request, line, new HashMap<>());
    }

    /**
     * CORE CALCULATION ENGINE: Evaluate a single line within a batch context.
     * Shared by both Live Preview (FE) and Final Mapping (BE).
     */
    public CoverageResult evaluateLine(
            BulkCoverageEngineRequest request,
            ClaimLineInput line,
            Map<Long, BatchUsageAccumulator> batchUsageContext) {

        BigDecimal quantity = bd(line.getQuantity());
        BigDecimal enteredUnitPrice = scale2(defaultIfNull(line.getEnteredUnitPrice(), ZERO));
        BigDecimal contractPrice = scale2(defaultIfNull(line.getContractPrice(), ZERO));
        BigDecimal manualRefusedInput = maxZero(scale2(defaultIfNull(line.getManualRefusedAmount(), ZERO)));

        // 1) Contract Price Guard
        BigDecimal effectiveUnitPrice = resolveEffectiveUnitPrice(enteredUnitPrice, contractPrice);
        BigDecimal requestedTotal = scale2(enteredUnitPrice.multiply(quantity));
        BigDecimal effectiveTotal = scale2(effectiveUnitPrice.multiply(quantity));
        BigDecimal priceRefused = maxZero(scale2(requestedTotal.subtract(effectiveTotal)));

        // 2) Coverage Lookup
        Optional<BenefitPolicyRuleResponseDto> ruleOpt = request.isFullCoverage()
                ? Optional.empty()
                : benefitPolicyRuleService.findCoverageForService(
                        request.getPolicyId(),
                        line.getServiceId(),
                        line.getServiceCategoryId() != null ? line.getServiceCategoryId() : line.getCategoryId(),
                        request.getEncounterType());

        int coveragePercent = request.isFullCoverage()
                ? 100
                : ruleOpt.map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent).orElse(0);

        boolean notCovered = !request.isFullCoverage() && coveragePercent <= 0;
        boolean requiresPreApproval = ruleOpt.map(BenefitPolicyRuleResponseDto::isRequiresPreApproval).orElse(false);
        Long appliedRuleId = ruleOpt.map(BenefitPolicyRuleResponseDto::getId).orElse(null);
        Long resolvedCategoryId = ruleOpt.map(BenefitPolicyRuleResponseDto::getMedicalCategoryId)
                .orElse(line.getCategoryId());

        // 3) Usage Limits
        UsageComputation usageComputation = computeUsage(
                request,
                line,
                ruleOpt,
                resolvedCategoryId,
                batchUsageContext,
                effectiveTotal);

        BigDecimal limitRefused = usageComputation.limitRefused();

        // Build precise Arabic refusal reason
        List<String> reasons = new ArrayList<>();
        if (priceRefused.compareTo(ZERO) > 0) {
            reasons.add("خصم فارق السعر التعاقدي");
        }
        if (limitRefused.compareTo(ZERO) > 0) {
            if ("USAGE_TIMES_LIMIT_EXCEEDED".equals(usageComputation.refusalReason())) {
                reasons.add("تجاوز عدد المرات المسموح بها");
            } else if ("USAGE_DAYS_LIMIT_EXCEEDED".equals(usageComputation.refusalReason())) {
                reasons.add("تجاوز عدد أيام الاستفادة المسموح بها");
            } else {
                reasons.add("تجاوز سقف المبلغ المسموح به");
            }
        }
        String refusalReason = reasons.isEmpty() ? usageComputation.refusalReason() : String.join(" و ", reasons);

        // 4) Financial Split (Strict sequence, no patient impact from rejection)
        // Patient share is calculated first from gross and never changed by later
        // adjustments.
        BigDecimal patientRate = request.isFullCoverage()
                ? ZERO
                : maxZero(scale2(BigDecimal.valueOf(100 - coveragePercent)));
        BigDecimal patientShare = scale2(requestedTotal.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));

        BigDecimal providerShareBeforeRejection = maxZero(scale2(requestedTotal.subtract(patientShare)));

        BigDecimal systemRefusedAmount = maxZero(scale2(priceRefused.add(limitRefused)));
        // Option 2: If rejected, the patient still pays their share, and the provider share is fully refused
        BigDecimal rejectionCandidate = line.isRejected()
                ? providerShareBeforeRejection
                : maxZero(scale2(systemRefusedAmount.add(manualRefusedInput)));

        BigDecimal finalRefusedAmount = min(providerShareBeforeRejection, rejectionCandidate);
        finalRefusedAmount = validateRefusedWithinRequested(finalRefusedAmount, providerShareBeforeRejection,
                line.getLineId());

        BigDecimal approvedTotal = maxZero(scale2(providerShareBeforeRejection.subtract(finalRefusedAmount)));
        BigDecimal companyShare = approvedTotal;

        if (line.isRejected() && (refusalReason == null || refusalReason.isBlank())) {
            refusalReason = "مرفوض كلياً من قبل المراجع";
        }

        // 5) Build Result
        return CoverageResult.builder()
                .lineId(line.getLineId())
                .effectiveUnitPrice(effectiveUnitPrice)
                .effectiveTotal(effectiveTotal)
                .requestedTotal(requestedTotal)
                .coveragePercent(coveragePercent)
                .notCovered(notCovered)
                .requiresPreApproval(requiresPreApproval)
                .usageDetails(usageComputation.usageDetails())
                .approvedTotal(approvedTotal)
                .companyShare(companyShare)
                .patientShare(patientShare)
                .refusalReason(refusalReason)
                .priceRefused(priceRefused)
                .limitRefused(limitRefused)
                .systemRefusedAmount(systemRefusedAmount)
                .manualRefusedAmount(manualRefusedInput)
                .manualRefusalReason(line.getManualRefusalReason())
                .appliedRuleId(appliedRuleId)
                .resolvedCategoryId(resolvedCategoryId)
                .build();
    }

    public UsageComputation computeUsage(
            BulkCoverageEngineRequest request,
            ClaimLineInput line,
            Optional<BenefitPolicyRuleResponseDto> ruleOpt,
            Long resolvedCategoryId,
            Map<Long, BatchUsageAccumulator> batchUsageContext,
            BigDecimal effectiveTotal) {

        if (request.isFullCoverage() || request.getMemberId() == null) {
            return new UsageComputation(ZERO, null, null);
        }

        Long appliedRuleId = ruleOpt.map(BenefitPolicyRuleResponseDto::getId).orElse(null);
        List<LimitSnapshot> bucketLimits = benefitBucketLimitService.findApplicable(
                appliedRuleId, request.getMemberId(), request.getServiceDate(), request.getEncounterType(),
                request.getExcludeClaimId());
        if (!bucketLimits.isEmpty()) {
            return computeBucketUsage(line, ruleOpt, bucketLimits, batchUsageContext, effectiveTotal);
        }

        // Full bucket cutover: an unlinked rule has no usage ceiling. Never fall back
        // to the retired amount_limit/times_limit columns on benefit_policy_rules.
        return new UsageComputation(ZERO, null, null);
    }

    private UsageComputation computeBucketUsage(
            ClaimLineInput line,
            Optional<BenefitPolicyRuleResponseDto> ruleOpt,
            List<LimitSnapshot> limits,
            Map<Long, BatchUsageAccumulator> batchUsageContext,
            BigDecimal effectiveTotal) {

        int coveragePercent = ruleOpt.map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent).orElse(0);
        BigDecimal greatestRefusal = ZERO;
        boolean timesExceeded = false;
        boolean amountExceeded = false;
        boolean daysExceeded = false;
        LimitSnapshot constraining = limits.get(0);

        for (LimitSnapshot limit : limits) {
            BatchUsageAccumulator acc = batchUsageContext.computeIfAbsent(bucketAccumulatorKey(limit.bucketId()),
                    ignored -> new BatchUsageAccumulator());
            long usedTimes = (limit.usedTimes() == null ? 0 : limit.usedTimes()) + acc.addedCount;
            long requestedTimes = requestedTimes(limit.countingMethod(), line, acc);
            boolean thisTimesExceeded = limit.timesLimit() != null
                    && usedTimes + requestedTimes > limit.timesLimit();
            boolean thisDaysExceeded = limit.daysLimit() != null
                    && !limit.serviceDayAlreadyUsed() && !acc.addedDay
                    && limit.usedDays() + 1 > limit.daysLimit();
            BigDecimal refusal = (thisTimesExceeded || thisDaysExceeded) ? effectiveTotal : ZERO;
            boolean thisAmountExceeded = false;

            BigDecimal usedAmount = scale2(defaultIfNull(limit.usedAmount(), ZERO).add(acc.addedAmount));
            BigDecimal requestedBasis = basisAmount(limit.consumptionBasis(), effectiveTotal, coveragePercent);
        if (!thisTimesExceeded && !thisDaysExceeded && limit.amountLimit() != null) {
                BigDecimal available = maxZero(scale2(limit.amountLimit().subtract(usedAmount)));
                if (requestedBasis.compareTo(available) > 0) {
                    thisAmountExceeded = true;
                    BigDecimal refusedBasis = scale2(requestedBasis.subtract(available));
                    refusal = toGrossRefusal(limit.consumptionBasis(), refusedBasis, coveragePercent);
                }
            }
            if (refusal.compareTo(greatestRefusal) > 0) {
                greatestRefusal = min(effectiveTotal, refusal);
                constraining = limit;
                timesExceeded = thisTimesExceeded;
                amountExceeded = thisAmountExceeded;
                daysExceeded = thisDaysExceeded;
            }
        }

        BigDecimal approvedGross = maxZero(scale2(effectiveTotal.subtract(greatestRefusal)));
        for (LimitSnapshot limit : limits) {
            BatchUsageAccumulator acc = batchUsageContext.get(bucketAccumulatorKey(limit.bucketId()));
            if (approvedGross.signum() > 0) {
                acc.addedCount += requestedTimes(limit.countingMethod(), line, acc);
                acc.addedAmount = scale2(acc.addedAmount.add(
                        basisAmount(limit.consumptionBasis(), approvedGross, coveragePercent)));
                if (!limit.serviceDayAlreadyUsed()) acc.addedDay = true;
            }
        }

        BatchUsageAccumulator selectedAcc = batchUsageContext.get(bucketAccumulatorKey(constraining.bucketId()));
        long finalTimes = (constraining.usedTimes() == null ? 0 : constraining.usedTimes()) + selectedAcc.addedCount;
        BigDecimal finalAmount = scale2(defaultIfNull(constraining.usedAmount(), ZERO).add(selectedAcc.addedAmount));
        UsageDetails details = UsageDetails.builder()
                .ruleId(ruleOpt.map(BenefitPolicyRuleResponseDto::getId).orElse(null))
                .bucketId(constraining.bucketId()).bucketName(constraining.bucketName())
                .hasLimit(true).timesLimit(constraining.timesLimit()).amountLimit(constraining.amountLimit())
                .daysLimit(constraining.daysLimit())
                .usedCount((int) Math.min(Integer.MAX_VALUE, finalTimes)).usedAmount(finalAmount)
                .usedDays(constraining.usedDays() + (selectedAcc.addedDay ? 1 : 0))
                .remainingAmount(constraining.amountLimit() == null ? null
                        : maxZero(scale2(constraining.amountLimit().subtract(finalAmount))))
                .timesExceeded(timesExceeded).amountExceeded(amountExceeded).daysExceeded(daysExceeded)
                .exceeded(timesExceeded || amountExceeded || daysExceeded).build();
        String reason = timesExceeded ? "USAGE_TIMES_LIMIT_EXCEEDED"
                : daysExceeded ? "USAGE_DAYS_LIMIT_EXCEEDED"
                : amountExceeded ? "USAGE_AMOUNT_LIMIT_EXCEEDED" : null;
        return new UsageComputation(greatestRefusal, reason, details);
    }

    private long requestedTimes(CountingMethod method, ClaimLineInput line, BatchUsageAccumulator acc) {
        return switch (method) {
            case EACH_UNIT -> Math.max(1, line.getQuantity() == null ? 1 : line.getQuantity());
            case PER_VISIT, PER_DAY -> acc.addedCount == 0 ? 1 : 0;
            case EACH_LINE -> 1;
        };
    }

    private BigDecimal basisAmount(ConsumptionBasis basis, BigDecimal gross, int coveragePercent) {
        if (basis == ConsumptionBasis.ELIGIBLE_AMOUNT) return scale2(gross);
        return scale2(gross.multiply(BigDecimal.valueOf(coveragePercent)).divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal toGrossRefusal(ConsumptionBasis basis, BigDecimal refusedBasis, int coveragePercent) {
        if (basis == ConsumptionBasis.ELIGIBLE_AMOUNT || coveragePercent <= 0) return scale2(refusedBasis);
        return scale2(refusedBasis.multiply(HUNDRED)
                .divide(BigDecimal.valueOf(coveragePercent), 2, RoundingMode.HALF_UP));
    }

    private long bucketAccumulatorKey(Long bucketId) {
        return Long.MIN_VALUE + bucketId;
    }

    private BigDecimal resolveEffectiveUnitPrice(BigDecimal enteredUnitPrice, BigDecimal contractPrice) {
        if (contractPrice == null || contractPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return scale2(enteredUnitPrice);
        }
        return scale2(enteredUnitPrice.min(contractPrice));
    }

    private static BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(Integer value) {
        return value == null ? BigDecimal.ONE : BigDecimal.valueOf(value.longValue());
    }

    private static BigDecimal maxZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return scale2(value);
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        return a.min(b);
    }

    private BigDecimal validateRefusedWithinRequested(BigDecimal finalRefusedAmount, BigDecimal requestedTotal,
            String lineId) {
        BigDecimal safeRefused = maxZero(finalRefusedAmount);
        BigDecimal safeRequested = maxZero(requestedTotal);
        if (safeRefused.compareTo(safeRequested) > 0) {
            log.warn(
                    "⚠️ [ENGINE] Refused amount ({}) exceeded requested amount ({}) for line {}. Capping to requested.",
                    safeRefused, safeRequested, lineId);
            return safeRequested;
        }
        return safeRefused;
    }

    private void recordRecalculationAudit(BulkCoverageEngineRequest request, List<CoverageResult> results) {
        // Implementation for auditing if needed
    }

    public record UsageComputation(
            BigDecimal limitRefused,
            String refusalReason,
            CoverageResult.UsageDetails usageDetails) {
    }

    public static class BatchUsageAccumulator {
        public long addedCount = 0;
        public BigDecimal addedAmount = BigDecimal.ZERO;
        public boolean addedDay = false;
    }
}
