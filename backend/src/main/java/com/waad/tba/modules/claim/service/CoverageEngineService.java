package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coverage Engine
 *
 * محرك التغطية المالي المركزي — المصدر الوحيد للحسابات المالية في النظام.
 *
 * خطوات المعالجة لكل سطر:
 * 1) Price Guard : تطبيق سقف سعر العقد
 * 2) Coverage Lookup : جلب قاعدة التغطية ونسبة التحمل
 * 3) Usage Limits : فحص سقوف الاستخدام (times/amount)
 * 4) Financial Split : حساب company/patient/refused بـ BigDecimal
 * 5) Result Build : بناء CoverageResult قابل للتدقيق
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageEngineService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final BenefitPolicyRuleService benefitPolicyRuleService;

    /**
     * حساب bulk لجميع الأسطر بالترتيب مع الحفاظ على سياق التراكم داخل نفس الطلب.
     */
    public List<CoverageResult> calculateBulk(BulkCoverageEngineRequest request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            return List.of();
        }

        Map<Long, BatchUsageAccumulator> batchUsageContext = new HashMap<>();
        List<CoverageResult> results = new ArrayList<>(request.getLines().size());

        for (ClaimLineInput line : request.getLines()) {
            CoverageResult result = calculateSingleInternal(request, line, batchUsageContext);
            results.add(result);
        }

        return results;
    }

    /**
     * حساب سطر واحد داخل سياق bulk.
     */
    public CoverageResult calculateSingle(BulkCoverageEngineRequest request, ClaimLineInput line) {
        return calculateSingleInternal(request, line, new HashMap<>());
    }

    private CoverageResult calculateSingleInternal(
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
        String refusalReason = usageComputation.refusalReason();

        BigDecimal approvedTotal = maxZero(scale2(effectiveTotal.subtract(limitRefused)));

        // 4) Financial Split
        BigDecimal grossCompanyShare = request.isFullCoverage()
                ? approvedTotal
                : scale2(approvedTotal.multiply(BigDecimal.valueOf(coveragePercent)).divide(HUNDRED, 2,
                        RoundingMode.HALF_UP));

        BigDecimal patientShareBase = maxZero(scale2(approvedTotal.subtract(grossCompanyShare)));

        // مهم: manualRefusedInput لا يُعاد اشتقاقه أو تعديله هنا.
        // يُستخدم كما أدخله المستخدم، مع تطبيقه محاسبياً على حصة الشركة فقط.
        BigDecimal effectiveManualOnCompany = min(grossCompanyShare, manualRefusedInput);
        BigDecimal companyShare = maxZero(scale2(grossCompanyShare.subtract(effectiveManualOnCompany)));

        BigDecimal patientShare = line.isRejected()
                ? approvedTotal
                : patientShareBase;

        BigDecimal systemRefusedAmount = maxZero(scale2(priceRefused.add(limitRefused)));
        BigDecimal finalRefusedAmount = maxZero(scale2(systemRefusedAmount.add(manualRefusedInput)));

        validateRefusedWithinRequested(finalRefusedAmount, requestedTotal, line.getLineId());

        if (line.isRejected() && (refusalReason == null || refusalReason.isBlank())) {
            refusalReason = "MANUAL_LINE_REJECTED";
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

    private UsageComputation computeUsage(
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

        if (timesExceeded) {
            limitRefused = effectiveTotal;
        }

        limitRefused = maxZero(limitRefused);
        BigDecimal approvedForUsage = maxZero(scale2(effectiveTotal.subtract(limitRefused)));

        acc.addedCount += 1;
        acc.addedAmount = scale2(acc.addedAmount.add(approvedForUsage));

        BigDecimal remainingAmount = amountLimit == null
                ? null
                : maxZero(scale2(amountLimit.subtract(scale2(usedAmount.add(approvedForUsage)))));

        CoverageResult.UsageDetails usageDetails = CoverageResult.UsageDetails.builder()
                .ruleId(ruleId)
                .hasLimit(true)
                .timesLimit(timesLimit)
                .amountLimit(amountLimit)
                .usedCount((int) Math.min(Integer.MAX_VALUE, usedCount))
                .usedAmount(usedAmount)
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
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.min(b);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static long asLongValue(Object value) {
        Long parsed = asLong(value);
        return parsed == null ? 0L : parsed;
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return scale2(bd);
        }
        if (value instanceof Number n) {
            return scale2(BigDecimal.valueOf(n.doubleValue()));
        }
        return scale2(new BigDecimal(String.valueOf(value)));
    }

    private static BigDecimal asBigDecimalOrZero(Object value) {
        BigDecimal parsed = asBigDecimal(value);
        return parsed == null ? ZERO : parsed;
    }

    private void validateRefusedWithinRequested(BigDecimal finalRefusedAmount, BigDecimal requestedTotal,
            String lineId) {
        BigDecimal safeRefused = maxZero(finalRefusedAmount);
        BigDecimal safeRequested = maxZero(requestedTotal);
        if (safeRefused.compareTo(safeRequested) > 0) {
            throw new IllegalArgumentException(
                    String.format("Total refused exceeds claim amount for line %s", lineId));
        }
    }

    private record UsageComputation(
            BigDecimal limitRefused,
            String refusalReason,
            CoverageResult.UsageDetails usageDetails) {
    }

    private static final class BatchUsageAccumulator {
        private long addedCount = 0L;
        private BigDecimal addedAmount = ZERO;
    }
}
