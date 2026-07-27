package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionRequest;
import com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.CoverageDecisionService;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.claim.dto.engine.CoverageResult.UsageDetails;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
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

    private final CoverageDecisionService coverageDecisionService;
    private final ProviderContractPricingItemRepository pricingItemRepository;

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
            try {
                CoverageResult result = evaluateLine(request, line, batchUsageContext);
                results.add(result);
            } catch (Exception e) {
                log.error("[COVERAGE-ENGINE] Failed to calculate lineId={}, pricingItemId={}, serviceId={}, categoryId={}: {}",
                        line != null ? line.getLineId() : null,
                        line != null ? line.getPricingItemId() : null,
                        line != null ? line.getServiceId() : null,
                        line != null ? line.getServiceCategoryId() : null,
                        e.getMessage(), e);
                results.add(fallbackFailedResult(line, e));
            }
        }

        recordRecalculationAudit(request, results);

        return results;
    }

    public CoverageResult calculateSingle(BulkCoverageEngineRequest request, ClaimLineInput line) {
        try {
            return evaluateLine(request, line, new HashMap<>());
        } catch (Exception e) {
            log.error("[COVERAGE-ENGINE] Failed to calculate single lineId={}, pricingItemId={}: {}",
                    line != null ? line.getLineId() : null,
                    line != null ? line.getPricingItemId() : null,
                    e.getMessage(), e);
            return fallbackFailedResult(line, e);
        }
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

        applyPricingItemSnapshot(line);

        BigDecimal contractPrice = scale2(defaultIfNull(line.getContractPrice(), ZERO));
        BigDecimal manualRefusedInput = maxZero(scale2(defaultIfNull(line.getManualRefusedAmount(), ZERO)));

        // 1) Contract Price Guard
        BigDecimal effectiveUnitPrice = resolveEffectiveUnitPrice(enteredUnitPrice, contractPrice);
        BigDecimal requestedTotal = scale2(enteredUnitPrice.multiply(quantity));
        BigDecimal effectiveTotal = scale2(effectiveUnitPrice.multiply(quantity));
        BigDecimal priceRefused = maxZero(scale2(requestedTotal.subtract(effectiveTotal)));

        // 2) Coverage Lookup
        // Full coverage is a co-pay exception, not a benefit-limit bypass. Keep
        // resolving the rule so amount/times/day buckets remain enforceable.
        var coverageDecision = coverageDecisionService.resolve(CoverageDecisionRequest.builder()
                .policyId(request.getPolicyId()).memberId(request.getMemberId())
                .serviceId(line.getServiceId())
                .serviceCategoryId(line.getServiceCategoryId() != null
                        ? line.getServiceCategoryId() : line.getCategoryId())
                .serviceDate(request.getServiceDate()).encounterType(request.getEncounterType())
                .excludeClaimId(request.getExcludeClaimId())
                .requestedAmount(effectiveTotal).build());
        Optional<BenefitPolicyRuleResponseDto> ruleOpt = coverageDecision.appliedRuleOptional();

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
                coverageDecision.limitsOrEmpty(),
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
        if (notCovered && (refusalReason == null || refusalReason.isBlank())) {
            refusalReason = "لا توجد قاعدة تغطية فعالة للتصنيف في سياق المطالبة "
                    + request.getEncounterType();
        }

        // 4) Financial Split (Strict sequence)
        // First cap the gross allowed amount by the benefit ceiling, then split that
        // allowed amount between company and patient. Calculating the patient share
        // before the ceiling would incorrectly charge the member for a non-covered
        // excess (the exact defect reported for MRI, dental and annual limits).
        BigDecimal allowedGross = maxZero(scale2(effectiveTotal.subtract(limitRefused)));
        BigDecimal patientRate = request.isFullCoverage()
                ? ZERO
                : maxZero(scale2(BigDecimal.valueOf(100 - coveragePercent)));
        BigDecimal patientShare = scale2(allowedGross.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));

        BigDecimal providerShareBeforeRejection = maxZero(scale2(allowedGross.subtract(patientShare)));

        BigDecimal systemRefusedAmount = maxZero(scale2(limitRefused));
        // The ceiling has already reduced allowedGross above. Do not subtract the
        // same limit refusal again from the company share. Only a reviewer refusal
        // is applied at this stage.
        BigDecimal rejectionCandidate = line.isRejected()
                ? providerShareBeforeRejection
                : manualRefusedInput;

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
            List<CoverageLimitSnapshot> bucketLimits,
            Long resolvedCategoryId,
            Map<Long, BatchUsageAccumulator> batchUsageContext,
            BigDecimal effectiveTotal) {

        if (request.getMemberId() == null) {
            return new UsageComputation(ZERO, null, null);
        }

        if (!bucketLimits.isEmpty()) {
            return computeBucketUsage(line, ruleOpt, bucketLimits, batchUsageContext, effectiveTotal);
        }

        // Full bucket cutover: an unlinked rule has no usage ceiling. Never fall back
        // to the retired amount_limit/times_limit columns on benefit_policy_rules.
        return new UsageComputation(ZERO, null, null);
    }

    /**
     * Pricing item is the canonical bridge between provider price lists and
     * insurance classifications. If the frontend omits categoryId/serviceCategoryId
     * or contractPrice, recover them from the provider contract pricing item so
     * coverage is not falsely marked as "not covered" for free-text services.
     */
    private void applyPricingItemSnapshot(ClaimLineInput line) {
        if (line == null || line.getPricingItemId() == null) {
            return;
        }

        pricingItemRepository.findById(line.getPricingItemId())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .ifPresent(item -> {
                    if (item.getMedicalCategory() != null) {
                        Long categoryId = item.getMedicalCategory().getId();
                        if (line.getServiceCategoryId() == null) {
                            line.setServiceCategoryId(categoryId);
                        }
                        if (line.getCategoryId() == null) {
                            line.setCategoryId(categoryId);
                        }
                    }
                    if ((line.getContractPrice() == null || line.getContractPrice().compareTo(ZERO) <= 0)
                            && item.getContractPrice() != null) {
                        line.setContractPrice(item.getContractPrice());
                    }
                });
    }

    private UsageComputation computeBucketUsage(
            ClaimLineInput line,
            Optional<BenefitPolicyRuleResponseDto> ruleOpt,
            List<CoverageLimitSnapshot> limits,
            Map<Long, BatchUsageAccumulator> batchUsageContext,
            BigDecimal effectiveTotal) {

        int coveragePercent = ruleOpt.map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent).orElse(0);
        BigDecimal greatestRefusal = ZERO;
        boolean timesExceeded = false;
        boolean amountExceeded = false;
        boolean daysExceeded = false;
        CoverageLimitSnapshot constraining = limits.get(0);

        for (CoverageLimitSnapshot limit : limits) {
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
        for (CoverageLimitSnapshot limit : limits) {
            BatchUsageAccumulator acc = batchUsageContext.get(bucketAccumulatorKey(limit.bucketId()));
            if (approvedGross.signum() > 0) {
                acc.addedCount += requestedTimes(limit.countingMethod(), line, acc);
                acc.addedAmount = scale2(acc.addedAmount.add(
                        basisAmount(limit.consumptionBasis(), approvedGross, coveragePercent)));
                if (!limit.serviceDayAlreadyUsed()) acc.addedDay = true;
            }
        }

        // Every applicable bucket (including the annual/general parent) has
        // already participated in enforcement above. The line-level display,
        // however, must expose only limits directly linked to this benefit.
        // General/parent ceilings belong in the policy summary, not in the
        // "benefit limit" column.
        CoverageLimitSnapshot amountDisplay = limits.stream()
                .filter(CoverageLimitSnapshot::directlyLinked)
                .filter(limit -> limit.amountLimit() != null)
                .min(Comparator.comparing(CoverageLimitSnapshot::amountLimit))
                .orElse(null);
        CoverageLimitSnapshot timesDisplay = limits.stream()
                .filter(CoverageLimitSnapshot::directlyLinked)
                .filter(limit -> limit.timesLimit() != null)
                .min(Comparator.comparing(CoverageLimitSnapshot::timesLimit))
                .orElse(null);
        CoverageLimitSnapshot daysDisplay = limits.stream()
                .filter(CoverageLimitSnapshot::directlyLinked)
                .filter(limit -> limit.daysLimit() != null)
                .min(Comparator.comparing(CoverageLimitSnapshot::daysLimit))
                .orElse(null);

        BatchUsageAccumulator amountAcc = amountDisplay == null ? null
                : batchUsageContext.get(bucketAccumulatorKey(amountDisplay.bucketId()));
        BatchUsageAccumulator timesAcc = timesDisplay == null ? null
                : batchUsageContext.get(bucketAccumulatorKey(timesDisplay.bucketId()));
        BigDecimal finalAmount = amountDisplay == null ? ZERO
                : scale2(defaultIfNull(amountDisplay.usedAmount(), ZERO).add(amountAcc.addedAmount));
        long finalTimes = timesDisplay == null ? 0
                : (timesDisplay.usedTimes() == null ? 0 : timesDisplay.usedTimes()) + timesAcc.addedCount;
        UsageDetails details = UsageDetails.builder()
                .ruleId(ruleOpt.map(BenefitPolicyRuleResponseDto::getId).orElse(null))
                .bucketId(constraining.bucketId()).bucketName(constraining.bucketName())
                .hasLimit(true)
                .timesLimit(timesDisplay == null ? null : timesDisplay.timesLimit())
                .amountLimit(amountDisplay == null ? null : amountDisplay.amountLimit())
                .daysLimit(daysDisplay == null ? null : daysDisplay.daysLimit())
                .usedCount((int) Math.min(Integer.MAX_VALUE, finalTimes)).usedAmount(finalAmount)
                .usedDays(daysDisplay == null ? 0 : daysDisplay.usedDays()
                        + (batchUsageContext.get(bucketAccumulatorKey(daysDisplay.bucketId())).addedDay ? 1 : 0))
                .remainingAmount(amountDisplay == null ? null
                        : maxZero(scale2(amountDisplay.amountLimit().subtract(finalAmount))))
                .timesExceeded(timesExceeded).amountExceeded(amountExceeded).daysExceeded(daysExceeded)
                .exceeded(timesExceeded || amountExceeded || daysExceeded).build();
        String reason = timesExceeded ? "USAGE_TIMES_LIMIT_EXCEEDED"
                : daysExceeded ? "USAGE_DAYS_LIMIT_EXCEEDED"
                : amountExceeded ? "USAGE_AMOUNT_LIMIT_EXCEEDED" : null;
        return new UsageComputation(greatestRefusal, reason, details);
    }

    private CoverageResult fallbackFailedResult(ClaimLineInput line, Exception e) {
        BigDecimal quantity = bd(line != null ? line.getQuantity() : null);
        BigDecimal enteredUnitPrice = scale2(defaultIfNull(line != null ? line.getEnteredUnitPrice() : null, ZERO));
        BigDecimal requestedTotal = scale2(enteredUnitPrice.multiply(quantity));
        return CoverageResult.builder()
                .lineId(line != null ? line.getLineId() : null)
                .effectiveUnitPrice(enteredUnitPrice)
                .effectiveTotal(requestedTotal)
                .requestedTotal(requestedTotal)
                .coveragePercent(0)
                .notCovered(true)
                .requiresPreApproval(false)
                .approvedTotal(ZERO)
                .companyShare(ZERO)
                .patientShare(requestedTotal)
                .refusalReason("تعذر حساب التغطية لهذا البند: " + safeMessage(e))
                .priceRefused(ZERO)
                .limitRefused(ZERO)
                .systemRefusedAmount(requestedTotal)
                .manualRefusedAmount(ZERO)
                .resolvedCategoryId(line != null
                        ? (line.getServiceCategoryId() != null ? line.getServiceCategoryId() : line.getCategoryId())
                        : null)
                .build();
    }

    private String safeMessage(Exception e) {
        String message = e == null ? null : e.getMessage();
        return message == null || message.isBlank() ? "خطأ داخلي في محرك التغطية" : message;
    }

    private long requestedTimes(CountingMethod method, ClaimLineInput line, BatchUsageAccumulator acc) {
        CountingMethod effectiveMethod = method != null ? method : CountingMethod.EACH_LINE;
        if (effectiveMethod == CountingMethod.EACH_UNIT) {
            return Math.max(1, line.getQuantity() == null ? 1 : line.getQuantity());
        }
        if (effectiveMethod == CountingMethod.PER_VISIT || effectiveMethod == CountingMethod.PER_DAY) {
            return acc.addedCount == 0 ? 1 : 0;
        }
        return 1;
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
