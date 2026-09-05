package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;

/**
 * P1.12.2: pairs one resolved limit's identity with the MEASURE its own
 * bucket was configured to reserve against -- {@code COMPANY_SHARE} or
 * {@code ELIGIBLE_AMOUNT}. Deliberately separate from
 * {@link ResolvedLimitDescriptor} and never added to
 * {@link UnifiedLimitDecision}: only a pre-authorization's hold amount
 * depends on this per-bucket choice. A claim's own consumption always uses
 * the line's single {@code limitConsumption} figure uniformly for every
 * AMOUNT-axis target regardless of basis (see
 * {@code CanonicalConsumptionTargetBuilder}), so every other consumer of the
 * canonical decision stays basis-blind.
 *
 * Absent (no entry) for the synthetic {@code POLICY_GENERAL} ceiling, which
 * has no bucket and therefore no configured basis of its own -- its measure
 * is always the insurer's company share, a PreAuth-specific mapping rule,
 * not a resolved fact.
 */
public record ResolvedLimitMeasure(String limitKey, ConsumptionBasis consumptionBasis) {}
