package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionRequest;
import com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.CoverageDecisionService;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ClaimLimitEvaluationContext;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BindingConstraintType;
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
import java.time.LocalDate;
import java.util.*;

/**
 * 🛡️ CENTRAL FINANCIAL COVERAGE ENGINE (SINGLE SOURCE OF TRUTH)
 *
 * Provides Unified Financial Calculations for both:
 * 1. UI Live Preview (BatchEntry / BatchGrid)
 * 2. Backend Entity Mapping (ClaimMapper)
 *
 * LAW: All financial calculations MUST flow through evaluateLine().
 *
 * P1.5.1 live wiring: the quantity/times/days DECISION itself
 * (previously {@code computeBucketUsage}'s own occurrence-split and
 * amount-ceiling arithmetic) is now made by {@code UnifiedLimitResolver},
 * fed by {@link BucketLimitSnapshotAdapter} (reusing the SAME applicable
 * buckets {@code coverageDecisionService.resolve} already resolved -- rule
 * and bucket selection happen exactly once per line, never twice) and kept
 * batch-aware across the lines of one claim by
 * {@link ClaimLimitEvaluationContext} (this evaluateLine's replacement for
 * the old {@code BatchUsageAccumulator}). {@code CoverageDecisionService}
 * remains responsible for rule resolution ONLY -- it never decided
 * quantity, and still does not.
 *
 * Two behavior changes are intended by this wiring, not regressions: a
 * RESERVED amount from another in-flight decision is now actually
 * subtracted (never checked before), and a bucket owned by a different
 * policy now blocks the line instead of being silently evaluated.
 *
 * {@code ClaimFinancialAdjudicationService} ("Engine B") still runs after
 * this on the SAVE path and still recomputes money from scratch --
 * unchanged by this step (tracked as P1.6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageEngineService {

    private final CoverageDecisionService coverageDecisionService;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final BucketLimitSnapshotAdapter bucketLimitSnapshotAdapter;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Batch calculation (used by /analyze endpoint)
     */
    public List<CoverageResult> calculateBulk(BulkCoverageEngineRequest request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            return List.of();
        }

        ClaimLimitEvaluationContext context = new ClaimLimitEvaluationContext();
        List<CoverageResult> results = new ArrayList<>(request.getLines().size());

        for (ClaimLineInput line : request.getLines()) {
            try {
                CoverageResult result = evaluateLine(request, line, context);
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
            return evaluateLine(request, line, new ClaimLimitEvaluationContext());
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
            ClaimLimitEvaluationContext context) {

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
        // FULL_COVERAGE changes the co-pay split only. Its limits still come from
        // the underlying OUTPATIENT/INPATIENT rule; looking up a synthetic
        // FULL_COVERAGE rule would turn a missing configuration into a limit bypass.
        String decisionContextCode = request.isFullCoverage()
                ? request.getEncounterType().name()
                : (request.getClaimContextCode() != null && !request.getClaimContextCode().isBlank()
                        ? request.getClaimContextCode()
                        : request.getEncounterType().name());
        var coverageDecision = coverageDecisionService.resolve(CoverageDecisionRequest.builder()
                .policyId(request.getPolicyId()).memberId(request.getMemberId())
                .serviceId(line.getServiceId())
                .serviceCategoryId(line.getServiceCategoryId() != null
                        ? line.getServiceCategoryId() : line.getCategoryId())
                .serviceDate(request.getServiceDate()).encounterType(request.getEncounterType())
                .claimContextCode(decisionContextCode)
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
                context,
                effectiveTotal,
                effectiveUnitPrice);

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
            } else if ("USAGE_BUCKET_CONFIGURATION_ERROR".equals(usageComputation.refusalReason())) {
                reasons.add("خطأ في إعداد سقف المنفعة لهذه الخدمة");
            } else {
                reasons.add("تجاوز سقف المبلغ المسموح به");
            }
        }
        String refusalReason = reasons.isEmpty() ? usageComputation.refusalReason() : String.join(" و ", reasons);
        if (notCovered && (refusalReason == null || refusalReason.isBlank())) {
            // The context the rule was actually looked up under, not the encounter
            // type behind it. Those differ for every context that is not
            // OUTPATIENT or INPATIENT: a claim entered under MATERNITY was refused
            // with the word INPATIENT, which sends whoever reads it looking for a
            // rule that was never searched for.
            refusalReason = "لا توجد قاعدة تغطية فعالة للتصنيف في سياق المطالبة "
                    + decisionContextCode;
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
            ClaimLimitEvaluationContext context,
            BigDecimal effectiveTotal,
            BigDecimal effectiveUnitPrice) {

        if (request.getMemberId() == null) {
            return new UsageComputation(ZERO, null, null);
        }

        if (!bucketLimits.isEmpty()) {
            return computeUsageViaUnifiedLimitResolver(request, line, ruleOpt, bucketLimits, context,
                    effectiveTotal, effectiveUnitPrice);
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

    /**
     * P1.5.1: replaces the old {@code computeBucketUsage}'s own
     * occurrence-split/amount-ceiling arithmetic with
     * {@code UnifiedLimitResolver}. Bucket selection is NOT repeated here --
     * {@code limits} is the exact list {@code coverageDecisionService.resolve}
     * already produced for this line, handed straight to the adapter.
     */
    private UsageComputation computeUsageViaUnifiedLimitResolver(
            BulkCoverageEngineRequest request,
            ClaimLineInput line,
            Optional<BenefitPolicyRuleResponseDto> ruleOpt,
            List<CoverageLimitSnapshot> limits,
            ClaimLimitEvaluationContext context,
            BigDecimal effectiveTotal,
            BigDecimal effectiveUnitPrice) {

        int coveragePercent = ruleOpt.map(BenefitPolicyRuleResponseDto::getEffectiveCoveragePercent).orElse(0);
        Long ruleId = ruleOpt.map(BenefitPolicyRuleResponseDto::getId).orElse(null);

        BucketLimitSnapshotAdapter.Result adapterResult = bucketLimitSnapshotAdapter
                .buildForNormalClaimFromResolvedLimits(request.getPolicyId(), request.getMemberId(), limits,
                        request.getExcludeClaimId());

        if (adapterResult.blocked()) {
            // P1.3 §2 (intended correction, not a regression): a structural
            // bucket/policy mismatch now halts the line instead of being
            // silently evaluated -- computeBucketUsage never checked this at all.
            log.error("[COVERAGE-ENGINE] {} for lineId={}", adapterResult.blockReason(), line.getLineId());
            UsageDetails blockedDetails = UsageDetails.builder()
                    .ruleId(ruleId).hasLimit(true).exceeded(true).build();
            return new UsageComputation(effectiveTotal, "USAGE_BUCKET_CONFIGURATION_ERROR", blockedDetails);
        }

        List<BucketLimitSnapshot> baseSnapshots = adapterResult.snapshots();
        List<BucketLimitSnapshot> beforeLine = context.adjustForNextLine(baseSnapshots);

        // ── requestedDays: a day is newly requested only if neither the DB
        // nor an earlier line in this batch already accounted for today's
        // date on this bucket -- mirrors `!serviceDayAlreadyUsed && !addedDay`. ──
        boolean dbDayAlreadyUsed = limits.stream().anyMatch(CoverageLimitSnapshot::serviceDayAlreadyUsed);
        boolean batchDayAlreadyConsumed = beforeLine.stream()
                .filter(s -> s.limitType() == LimitAxisType.DAYS)
                .anyMatch(s -> context.dayAlreadyConsumedThisBatch(
                        s.bucketId(), s.periodStart(), s.periodEnd(), request.getServiceDate()));
        boolean hasDaysAxis = beforeLine.stream().anyMatch(s -> s.limitType() == LimitAxisType.DAYS);
        int requestedDays = hasDaysAxis && !dbDayAlreadyUsed && !batchDayAlreadyConsumed ? 1 : 0;

        // ── requestedQuantity: mirrors the old per-bucket requestedTimes()
        // rule, applied using the first TIMES-configuring bucket's own
        // countingMethod (P1.5.2 moved countingMethod to the bucket; a line
        // touching two TIMES buckets with two different methods is a
        // confirmed-but-deferred edge case, see P1_5_1A doc §5 item 1). ──
        Optional<BucketLimitSnapshot> primaryTimesSnapshot = beforeLine.stream()
                .filter(s -> s.limitType() == LimitAxisType.TIMES).findFirst();
        int requestedQuantity = requestedQuantity(primaryTimesSnapshot, line, context);

        UnifiedLimitInput input = new UnifiedLimitInput(
                request.getPolicyId(), ruleId, request.getMemberId(), request.getServiceDate(),
                request.getEncounterType(), requestedQuantity, requestedDays, effectiveUnitPrice, effectiveTotal,
                request.getExcludeClaimId(), ReservationEvaluationMode.NORMAL, null, null);

        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, beforeLine);

        if (decision.status() == com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitStatus.BLOCKED) {
            // P1.3 §2: BLOCKED must never surface as an ordinary approval --
            // bindingAvailableAmount is null here (not computed at all, not
            // zero), so it must be handled explicitly, not defaulted to
            // "unlimited money" below. This is the resolver's OWN
            // independent guard (defense-in-depth beyond the adapter's own
            // check above), so reaching it here is not expected in
            // practice, but must still refuse safely if it ever fires.
            log.error("[COVERAGE-ENGINE] {} for lineId={}", decision.decisionReasons(), line.getLineId());
            UsageDetails blockedDetails = UsageDetails.builder()
                    .ruleId(ruleId).hasLimit(true).exceeded(true).build();
            return new UsageComputation(effectiveTotal, "USAGE_BUCKET_CONFIGURATION_ERROR", blockedDetails);
        }

        // UnifiedLimitResolver/ClaimLimitEvaluationContext are deliberately
        // money-ownership-blind (P1.3 §0) -- they only ever see gross
        // amounts. Converting into what each bucket's OWN consumption is
        // actually measured in (COMPANY_SHARE vs ELIGIBLE_AMOUNT) is this
        // caller's job, since only it knows each bucket's consumptionBasis.
        Map<Long, ConsumptionBasis> basisByBucketId = new HashMap<>();
        for (CoverageLimitSnapshot l : limits) {
            if (l.bucketId() != null) basisByBucketId.put(l.bucketId(), l.consumptionBasis());
        }
        context.recordLineConsumption(beforeLine, decision, request.getServiceDate(),
                bucketId -> {
                    ConsumptionBasis basis = basisByBucketId.getOrDefault(bucketId, ConsumptionBasis.ELIGIBLE_AMOUNT);
                    BigDecimal gross = decision.bindingAvailableAmount() == null ? ZERO : decision.bindingAvailableAmount();
                    return basisAmount(basis, gross, coveragePercent);
                });
        List<BucketLimitSnapshot> afterLine = context.adjustForNextLine(baseSnapshots);

        BigDecimal bindingAvailable = decision.bindingAvailableAmount() == null ? effectiveTotal
                : decision.bindingAvailableAmount();
        BigDecimal limitRefused = maxZero(scale2(effectiveTotal.subtract(bindingAvailable)));

        boolean timesExceeded = decision.bindingConstraintType() == BindingConstraintType.TIMES;
        boolean amountExceeded = decision.bindingConstraintType() == BindingConstraintType.AMOUNT;
        boolean daysExceeded = decision.bindingConstraintType() == BindingConstraintType.DAYS;

        // Display: only directly-linked limits are shown as "the" benefit
        // limit for this service -- a general/parent ceiling still enforces
        // (via UnifiedLimitResolver above, over every applicable bucket) but
        // is never shown as if it were this service's own limit.
        CoverageLimitSnapshot amountDisplay = limits.stream().filter(CoverageLimitSnapshot::directlyLinked)
                .filter(l -> l.amountLimit() != null).min(Comparator.comparing(CoverageLimitSnapshot::amountLimit))
                .orElse(null);
        CoverageLimitSnapshot timesDisplay = limits.stream().filter(CoverageLimitSnapshot::directlyLinked)
                .filter(l -> l.timesLimit() != null).min(Comparator.comparing(CoverageLimitSnapshot::timesLimit))
                .orElse(null);
        CoverageLimitSnapshot daysDisplay = limits.stream().filter(CoverageLimitSnapshot::directlyLinked)
                .filter(l -> l.daysLimit() != null).min(Comparator.comparing(CoverageLimitSnapshot::daysLimit))
                .orElse(null);
        CoverageLimitSnapshot constraining = amountDisplay != null ? amountDisplay
                : timesDisplay != null ? timesDisplay
                : daysDisplay != null ? daysDisplay
                : limits.get(0);

        BigDecimal usedAmountBeforeLine = amountDisplay == null ? ZERO
                : findAxis(beforeLine, amountDisplay.bucketId(), LimitAxisType.AMOUNT)
                        .map(BucketLimitSnapshot::committed).orElse(scale2(defaultIfNull(amountDisplay.usedAmount(), ZERO)));
        BigDecimal finalAmount = amountDisplay == null ? ZERO
                : findAxis(afterLine, amountDisplay.bucketId(), LimitAxisType.AMOUNT)
                        .map(BucketLimitSnapshot::committed).orElse(usedAmountBeforeLine);
        long finalTimes = timesDisplay == null ? 0
                : findAxis(afterLine, timesDisplay.bucketId(), LimitAxisType.TIMES)
                        .map(s -> s.committed().longValue())
                        .orElse((long) (timesDisplay.usedTimes() == null ? 0 : timesDisplay.usedTimes()));
        long usedDaysAfter = daysDisplay == null ? 0
                : findAxis(afterLine, daysDisplay.bucketId(), LimitAxisType.DAYS)
                        .map(s -> s.committed().longValue())
                        .orElse((long) (daysDisplay.usedDays() == null ? 0 : daysDisplay.usedDays()));

        BigDecimal requestedBasis = basisAmount(constraining.consumptionBasis(), effectiveTotal, coveragePercent);
        BigDecimal approvedGross = maxZero(scale2(effectiveTotal.subtract(limitRefused)));

        Integer approvedUnits = decision.approvedQuantity() != requestedQuantity ? decision.approvedQuantity() : null;
        Integer refusedUnits = decision.refusedQuantity() > 0 ? decision.refusedQuantity() : null;

        UsageDetails details = UsageDetails.builder()
                .ruleId(ruleId)
                .bucketId(constraining.bucketId()).bucketName(constraining.bucketName())
                .hasLimit(true)
                .timesLimit(timesDisplay == null ? null : timesDisplay.timesLimit())
                .amountLimit(amountDisplay == null ? null : amountDisplay.amountLimit())
                .daysLimit(daysDisplay == null ? null : daysDisplay.daysLimit())
                .usedCount((int) Math.min(Integer.MAX_VALUE, finalTimes)).usedAmount(finalAmount)
                .consumptionBasis(constraining.consumptionBasis() == null ? null : constraining.consumptionBasis().name())
                .usedAmountBeforeLine(usedAmountBeforeLine)
                .requestedAmountForLimit(requestedBasis)
                .approvedAmountForLimit(basisAmount(constraining.consumptionBasis(), approvedGross, coveragePercent))
                .usedDays((int) usedDaysAfter)
                .remainingAmount(amountDisplay == null ? null
                        : maxZero(scale2(amountDisplay.amountLimit().subtract(finalAmount))))
                .timesExceeded(timesExceeded).amountExceeded(amountExceeded).daysExceeded(daysExceeded)
                .exceeded(timesExceeded || amountExceeded || daysExceeded)
                .approvedUnits(approvedUnits).refusedUnits(refusedUnits)
                .build();

        String reason = timesExceeded ? "USAGE_TIMES_LIMIT_EXCEEDED"
                : daysExceeded ? "USAGE_DAYS_LIMIT_EXCEEDED"
                : amountExceeded ? "USAGE_AMOUNT_LIMIT_EXCEEDED" : null;

        return new UsageComputation(limitRefused, reason, details);
    }

    private static Optional<BucketLimitSnapshot> findAxis(List<BucketLimitSnapshot> snapshots, Long bucketId,
            LimitAxisType axisType) {
        if (bucketId == null) return Optional.empty();
        return snapshots.stream()
                .filter(s -> axisType == s.limitType() && bucketId.equals(s.bucketId()))
                .findFirst();
    }

    /**
     * Mirrors the old per-bucket {@code requestedTimes()} rule exactly:
     * EACH_UNIT asks for the line's own quantity; PER_VISIT/PER_DAY ask for
     * one occurrence only the first time this bucket is touched in the
     * batch (the whole claim shares one visit/day), zero afterward; every
     * other method (EACH_LINE, or no TIMES axis at all) always asks for one.
     */
    private int requestedQuantity(Optional<BucketLimitSnapshot> primaryTimesSnapshot, ClaimLineInput line,
            ClaimLimitEvaluationContext context) {
        if (primaryTimesSnapshot.isEmpty()) {
            return Math.max(1, line.getQuantity() == null ? 1 : line.getQuantity());
        }
        BucketLimitSnapshot snapshot = primaryTimesSnapshot.get();
        CountingMethod method = snapshot.countingMethod() != null ? snapshot.countingMethod() : CountingMethod.EACH_LINE;
        if (method == CountingMethod.EACH_UNIT) {
            return Math.max(1, line.getQuantity() == null ? 1 : line.getQuantity());
        }
        if (method == CountingMethod.PER_VISIT || method == CountingMethod.PER_DAY) {
            boolean alreadyThisBatch = context.timesAlreadyConsumedThisBatch(
                    snapshot.bucketId(), snapshot.periodStart(), snapshot.periodEnd());
            return alreadyThisBatch ? 0 : 1;
        }
        return 1;
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

    private BigDecimal basisAmount(ConsumptionBasis basis, BigDecimal gross, int coveragePercent) {
        if (basis == ConsumptionBasis.ELIGIBLE_AMOUNT) return scale2(gross);
        return scale2(gross.multiply(BigDecimal.valueOf(coveragePercent)).divide(HUNDRED, 2, RoundingMode.HALF_UP));
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
}
