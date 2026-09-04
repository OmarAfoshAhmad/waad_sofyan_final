package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import java.math.BigDecimal;
import java.util.List;

/**
 * P1.3: the output of a limit decision -- how much may be consumed, and
 * why. Deliberately knows nothing about money ownership: no coverage
 * percent, no company share, no copay, no non-covered. Those belong to
 * {@code ClaimLineDecision}, built by combining this with
 * {@code WaadFinancialEngine.Result} -- never here.
 *
 * {@code amount}/{@code times}/{@code days} share the exact same four-field
 * shape on purpose (P1.3 §1.2): any test or fix written for one axis
 * applies to the others verbatim.
 */
public record UnifiedLimitDecision(

        // ── identity (§1.4) ──────────────────────────────────────────
        Long ruleId,
        List<Long> appliedBucketIds,
        List<String> decisionReasons,

        // ── quantity (§1.3) ──────────────────────────────────────────
        int requestedQuantity,
        int approvedQuantity,
        int refusedQuantity,

        // ── days (§1.3) ──────────────────────────────────────────────
        int requestedDays,
        int approvedDays,
        int refusedDays,

        // ── per-axis limit evaluation (§1.2) ─────────────────────────
        LimitAxis amount,
        LimitAxis times,
        LimitAxis days,

        // ── binding decision (§1.3) ───────────────────────────────────
        BindingConstraintType bindingConstraintType,
        Long bindingBucketId,
        BigDecimal bindingAvailableAmount,
        UnifiedLimitStatus status) {

    /**
     * Same four fields, same meaning, for AMOUNT/TIMES/DAYS alike.
     * {@code configured == null} means "no limit of this kind was
     * configured on any bucket in the chain" -- never confused with a
     * configured-and-exhausted limit ({@code configured} non-null,
     * {@code remaining == 0}). See P1.3 §1.2 and the invariant in §3(5).
     */
    public record LimitAxis(
            BigDecimal configured,
            BigDecimal committed,
            BigDecimal reserved,
            BigDecimal remaining) {

        public static LimitAxis unconfigured() {
            return new LimitAxis(null, null, null, null);
        }
    }

    /**
     * A BLOCKED decision: no numeric field below is meaningful, per P1.3
     * §2 -- {@code bindingAvailableAmount} is null, not zero, and callers
     * must check {@code status} before reading any other field.
     */
    public static UnifiedLimitDecision blocked(Long ruleId, List<String> reasons) {
        return new UnifiedLimitDecision(
                ruleId, List.of(), reasons,
                0, 0, 0,
                0, 0, 0,
                LimitAxis.unconfigured(), LimitAxis.unconfigured(), LimitAxis.unconfigured(),
                BindingConstraintType.NONE, null, null,
                UnifiedLimitStatus.BLOCKED);
    }
}
