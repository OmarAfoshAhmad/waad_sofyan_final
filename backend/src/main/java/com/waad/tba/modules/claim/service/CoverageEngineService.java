package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
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
                        line.getCategoryId(),
                        line.getServiceCategoryId());

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

        Map<String, Object> usage = benefitPolicyRuleService.checkUsageLimit(
                request.getPolicyId(),
                line.getServiceId(),
                line.getCategoryId(),
                line.getServiceCategoryId(),
                request.getMemberId(),
                request.getServiceYear(),
                request.getExcludeClaimId());

        if (usage == null || !Boolean.TRUE.equals(usage.get("hasLimit"))) {
            return new UsageComputation(ZERO, null, null);
        }

        Long ruleId = asLong(usage.get("ruleId"));
        Integer timesLimit = asInteger(usage.get("timesLimit"));
        BigDecimal amountLimit = asBigDecimal(usage.get("amountLimit"));
        long usedCountDb = asLongValue(usage.get("usedCount"));
        BigDecimal usedAmountDb = scale2(asBigDecimalOrZero(usage.get("usedAmount")));

        BatchUsageAccumulator acc = batchUsageContext.computeIfAbsent(
                ruleId != null ? ruleId : (ruleOpt.map(BenefitPolicyRuleResponseDto::getId).orElse(-1L)),
                key -> new BatchUsageAccumulator());

        long usedCount = usedCountDb + acc.addedCount;
        BigDecimal usedAmount = scale2(usedAmountDb.add(acc.addedAmount));

        boolean timesExceeded = timesLimit != null && usedCount >= timesLimit;

        if (timesExceeded) {
            CoverageResult.UsageDetails usageDetails = CoverageResult.UsageDetails.builder()
                    .ruleId(ruleId)
                    .hasLimit(true)
                    .timesLimit(timesLimit)
                    .amountLimit(amountLimit)
                    .usedCount((int) Math.min(Integer.MAX_VALUE, usedCount))
                    .usedAmount(usedAmount)
                    .remainingAmount(amountLimit == null ? null : maxZero(scale2(amountLimit.subtract(usedAmount))))
                    .timesExceeded(true)
                    .amountExceeded(false)
                    .exceeded(true)
                    .build();

            return new UsageComputation(effectiveTotal, "USAGE_TIMES_LIMIT_EXCEEDED", usageDetails);
        }

        BigDecimal limitRefused = ZERO;
        boolean amountExceeded = false;

        if (amountLimit != null) {
            BigDecimal remaining = scale2(amountLimit.subtract(usedAmount));
            if (remaining.compareTo(ZERO) <= 0) {
                amountExceeded = true;
                limitRefused = effectiveTotal;
            } else if (effectiveTotal.compareTo(remaining) > 0) {
                amountExceeded = true;
                limitRefused = scale2(effectiveTotal.subtract(remaining));
            }
        }

        limitRefused = maxZero(limitRefused);
        BigDecimal approvedForUsage = maxZero(scale2(effectiveTotal.subtract(limitRefused)));

        Long finalUsedCount = usedCount;
        BigDecimal finalUsedAmount = usedAmount;
        long quantity = line.getQuantity() != null && line.getQuantity() > 0 ? line.getQuantity() : 1L;

        if (approvedForUsage.compareTo(ZERO) > 0) {
            finalUsedCount += quantity;
            finalUsedAmount = scale2(usedAmount.add(approvedForUsage));

            acc.addedCount += quantity;
            acc.addedAmount = scale2(acc.addedAmount.add(approvedForUsage));
        }

        BigDecimal remainingAmount = amountLimit == null
                ? null
                : maxZero(scale2(amountLimit.subtract(finalUsedAmount)));

        CoverageResult.UsageDetails usageDetails = CoverageResult.UsageDetails.builder()
                .ruleId(ruleId)
                .hasLimit(true)
                .timesLimit(timesLimit)
                .amountLimit(amountLimit)
                .usedCount((int) Math.min(Integer.MAX_VALUE, finalUsedCount))
                .usedAmount(finalUsedAmount)
                .remainingAmount(remainingAmount)
                .timesExceeded(timesExceeded)
                .amountExceeded(amountExceeded)
                .exceeded(timesExceeded || amountExceeded)
                .build();

        String reason = null;
        if (timesExceeded) {
            reason = "USAGE_TIMES_LIMIT_EXCEEDED";
        } else if (amountExceeded) {
            reason = "USAGE_AMOUNT_LIMIT_EXCEEDED";
        }

        return new UsageComputation(limitRefused, reason, usageDetails);
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

    private static Long asLong(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number n)
            return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static long asLongValue(Object value) {
        Long parsed = asLong(value);
        return parsed == null ? 0L : parsed;
    }

    private static Integer asInteger(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number n)
            return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null)
            return null;
        if (value instanceof BigDecimal bd)
            return scale2(bd);
        if (value instanceof Number n)
            return scale2(BigDecimal.valueOf(n.doubleValue()));
        return scale2(new BigDecimal(String.valueOf(value)));
    }

    private static BigDecimal asBigDecimalOrZero(Object value) {
        BigDecimal parsed = asBigDecimal(value);
        return parsed == null ? ZERO : parsed;
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
    }
}
