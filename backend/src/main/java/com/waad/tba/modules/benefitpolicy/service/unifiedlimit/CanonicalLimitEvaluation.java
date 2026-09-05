package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import java.util.List;

/**
 * P1.6.x: the envelope one canonical limit resolution pass hands to its two
 * consumers -- never a third resolver, never a new source of truth. Just
 * {@link #decision()} (what is allowed -- feeds {@code WaadFinancialEngine})
 * carried alongside {@link #items()} (what each involved limit IS, for the
 * audit trail -- feeds {@code ClaimLimitSnapshotFactory}), because both were
 * already known at the end of the SAME resolution and neither consumer
 * should have to re-derive or re-query for the other's half.
 *
 * A {@code List}, not a {@code Map}: consumption order is preserved exactly
 * as the resolver saw it, and a non-bucket source (POLICY_GENERAL today,
 * whatever else tomorrow) never has to be shoehorned into a bucketId key.
 */
public record CanonicalLimitEvaluation(UnifiedLimitDecision decision, List<ResolvedLimitItem> items) {
}
