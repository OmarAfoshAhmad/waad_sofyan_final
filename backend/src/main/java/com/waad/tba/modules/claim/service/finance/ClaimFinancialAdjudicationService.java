package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalLimitEvaluation;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ClaimLimitEvaluationContext;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitStatus;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.waad.tba.modules.preauthorization.entity.PreauthDecisionSnapshot;

/**
 * The only live adapter from claim entities to the canonical financial engine.
 *
 * P1.6: no longer resolves the limit a second time. Save-A
 * ({@code CoverageEngineService.evaluateLine}, via {@code UnifiedLimitResolver})
 * already decided how much of each line may be consumed; that decision
 * rides along on {@code ClaimLine.unifiedLimitDecision} (transient, set in
 * the same request by {@code ClaimMapper}) and is used AS-IS here --
 * {@code EffectiveLimitResolver}/{@code ApplicableLimitResolver}/
 * {@code LimitBalanceReader} are never called on this path anymore.
 *
 * A line with no rider (the approval-time re-verification path in
 * {@code ClaimFinancialSnapshotService.finalizeSnapshot}, which explicitly
 * clears every line's rider right after acquiring the member lock -- a
 * rider computed before that lock reflects a pre-lock balance and must
 * never be reused for the locked re-check) is resolved FRESH through the
 * exact same canonical resolver
 * ({@link BucketLimitSnapshotAdapter} + {@link UnifiedLimitResolver}) --
 * the one "resolve again" the architecture still allows, since it exists
 * only to catch a balance that changed after Save-A ran (concurrency), not
 * to run an independent, parallel adjudication.
 *
 * {@code MultiLineMultiBucketEngine} is no longer called from here: its own
 * job -- deriving a binding available limit from a balance set, batch-aware
 * across the claim's own lines -- is now {@code UnifiedLimitDecision}'s
 * ({@code bindingAvailableAmount}, already batch-aware via
 * {@link ClaimLimitEvaluationContext}). Calling both would be exactly the
 * "resolve limits again" this phase exists to remove. The class itself is
 * left in place (P1.6 does not delete B) and its own characterization
 * tests still exercise it directly; it has simply lost its only production
 * caller.
 */
@Service
@RequiredArgsConstructor
public class ClaimFinancialAdjudicationService {
    private final BenefitPolicyRepository policyRepository;
    private final MemberPolicyResolver memberPolicyResolver;
    private final com.waad.tba.modules.preauthorization.repository.PreauthDecisionSnapshotRepository
            decisionSnapshotRepository;
    private final BucketLimitSnapshotAdapter bucketLimitSnapshotAdapter;
    private final WaadFinancialEngine financialEngine;

    public record AdjudicationResult(List<WaadFinancialEngine.Result> lineResults) {}

    @Transactional(readOnly = true)
    public AdjudicationResult adjudicate(Claim claim) {
        if (claim == null || claim.getMember() == null || claim.getMember().getId() == null) {
            throw new IllegalArgumentException("claim with a persisted member is required");
        }
        if (claim.getLines() == null || claim.getLines().isEmpty()) {
            throw new IllegalArgumentException("claim requires at least one line");
        }
        LocalDate serviceDate = Optional.ofNullable(claim.getServiceDate()).orElseThrow(
                () -> new IllegalArgumentException("serviceDate is required"));
        BenefitPolicy policy = resolvePolicy(claim, serviceDate);
        Long memberId = claim.getMember().getId();

        // READ from the approval's own decision, never re-derived here.
        // See the historical note this replaced: re-deriving "which
        // enrollment period holds this reservation" from the claim's ACTUAL
        // service date, instead of the decision snapshot's recorded answer,
        // silently caps a claim as though its own approval were somebody
        // else's spending.
        Long assignmentId = claim.getPreAuthorization() == null ? null
                : decisionSnapshotRepository
                        .findFirstByPreauthIdOrderByCalculationVersionDesc(
                                claim.getPreAuthorization().getId())
                        .map(PreauthDecisionSnapshot::getMemberPolicyAssignmentId)
                        .orElse(null);

        // Batch-aware across this claim's own lines, exactly like Save-A's
        // own ClaimLimitEvaluationContext -- only exercised for lines that
        // need a fresh resolve (no rider from Save-A); a line that already
        // carries its decision needs no further adjustment here.
        ClaimLimitEvaluationContext freshResolveContext = new ClaimLimitEvaluationContext();

        List<WaadFinancialEngine.Result> lineResults = new ArrayList<>();
        for (int index = 0; index < claim.getLines().size(); index++) {
            ClaimLine line = claim.getLines().get(index);
            if (line.getAppliedRuleId() == null) {
                throw new IllegalStateException("COVERED_LINE_RULE_MISSING: line index=" + index);
            }
            if (line.getCoveragePercentSnapshot() == null || line.getCoveragePercentSnapshot() < 1) {
                throw new IllegalStateException("COVERAGE_ZERO_IS_NOT_A_VALID_BENEFIT: line index=" + index);
            }
            BigDecimal requested = requiredPositive(line.getRequestedTotal(), "requestedTotal", index);
            BigDecimal contractualUnit = requiredPositive(line.getContractUnitPrice(), "contractUnitPrice", index);
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            BigDecimal contractualTotal = contractualUnit.multiply(BigDecimal.valueOf(quantity));

            UnifiedLimitDecision decision;
            if (line.getUnifiedLimitDecision() != null) {
                decision = line.getUnifiedLimitDecision();
            } else {
                CanonicalLimitEvaluation evaluation = resolveCanonically(claim, line, policy, serviceDate, memberId,
                        assignmentId, contractualUnit, contractualTotal, quantity, freshResolveContext);
                decision = evaluation.decision();
                line.setResolvedLimitItems(evaluation.items());
            }

            if (decision.status() == UnifiedLimitStatus.BLOCKED) {
                // P1.3 §2: BLOCKED halts before WaadFinancialEngine ever runs --
                // never a zero ceiling, never an ordinary exhausted result.
                throw new IllegalStateException("LIMIT_DECISION_BLOCKED: line index=" + index
                        + " reasons=" + decision.decisionReasons());
            }

            WaadFinancialEngine.LimitMode limitMode = decision.status() == UnifiedLimitStatus.UNLIMITED
                    ? WaadFinancialEngine.LimitMode.UNLIMITED : WaadFinancialEngine.LimitMode.LIMITED;
            BigDecimal bindingAvailableLimit = limitMode == WaadFinancialEngine.LimitMode.LIMITED
                    ? decision.bindingAvailableAmount().max(BigDecimal.ZERO) : null;

            WaadFinancialEngine.Result financial = financialEngine.evaluate(new WaadFinancialEngine.Input(
                    requested, contractualTotal, limitMode, bindingAvailableLimit,
                    line.getCoveragePercentSnapshot(), zero(claim.getAppliedDiscountPercent()),
                    Boolean.TRUE.equals(claim.getDiscountBeforeRejection()),
                    zero(line.getManualRefusedAmount()), Boolean.TRUE.equals(line.getRejected()), quantity));

            // Whichever path produced it (Save-A's rider or a fresh
            // canonical resolve), the SAME decision now rides on the line
            // for ClaimLimitSnapshotFactory -- it must never re-derive "what
            // was decided" from a different source than the money did.
            line.setUnifiedLimitDecision(decision);
            apply(line, financial, decision);
            lineResults.add(financial);
        }
        return new AdjudicationResult(List.copyOf(lineResults));
    }

    /**
     * The one "resolve again" this architecture still allows: a fresh read
     * through the SAME canonical resolver Save-A used, never the retired
     * EffectiveLimitResolver/LimitBalanceReader pair. Reached only when a
     * line carries no rider from Save-A (the approval-time re-verification
     * path, where a freshly loaded ClaimLine naturally has no transient
     * state) -- exists to catch a balance that changed after Save-A ran,
     * not to run an independent adjudication.
     *
     * requestedDays is fixed at 0 here on purpose: the retired
     * EffectiveLimitResolver/LimitBalanceReader pair had no days dimension
     * at all (AMOUNT and TIMES only), so this preserves that exact scope
     * rather than inventing a new one B never had.
     */
    private CanonicalLimitEvaluation resolveCanonically(Claim claim, ClaimLine line, BenefitPolicy policy,
            LocalDate serviceDate, Long memberId, Long assignmentId, BigDecimal effectiveUnitPrice,
            BigDecimal eligibleAmount, int quantity, ClaimLimitEvaluationContext context) {
        boolean isPreauthorized = claim.getPreAuthorization() != null;
        BucketLimitSnapshotAdapter.Result adapterResult = isPreauthorized
                ? bucketLimitSnapshotAdapter.buildForPreauthorizedClaim(policy.getId(), line.getAppliedRuleId(),
                        memberId, serviceDate, claim.getEncounterType(), claim.getId(),
                        claim.getPreAuthorization().getId(), assignmentId)
                : bucketLimitSnapshotAdapter.buildForNormalClaim(policy.getId(), line.getAppliedRuleId(), memberId,
                        serviceDate, claim.getEncounterType(), claim.getId());

        if (adapterResult.blocked()) {
            return new CanonicalLimitEvaluation(
                    UnifiedLimitDecision.blocked(line.getAppliedRuleId(), List.of(adapterResult.blockReason())),
                    adapterResult.items());
        }

        List<BucketLimitSnapshot> beforeLine = context.adjustForNextLine(adapterResult.snapshots());
        UnifiedLimitInput input = new UnifiedLimitInput(policy.getId(), line.getAppliedRuleId(), memberId,
                serviceDate, claim.getEncounterType(), quantity, 0, effectiveUnitPrice, eligibleAmount,
                claim.getId(),
                isPreauthorized ? ReservationEvaluationMode.PREAUTHORIZED_CLAIM : ReservationEvaluationMode.NORMAL,
                isPreauthorized ? claim.getPreAuthorization().getId() : null,
                isPreauthorized ? assignmentId : null);

        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, beforeLine);
        context.recordLineConsumption(beforeLine, decision, serviceDate);
        return new CanonicalLimitEvaluation(decision, adapterResult.items());
    }

    private void apply(ClaimLine line, WaadFinancialEngine.Result r, UnifiedLimitDecision decision) {
        line.setContractualPrice(r.contractualPrice());
        line.setContractualPriceExcess(r.contractualPriceExcess());
        line.setSettlementBase(r.settlementBase());
        line.setLimitMode(r.limitMode().name());
        line.setBindingAvailableLimit(r.bindingAvailableLimit());
        line.setInsideLimit(r.insideLimit());
        line.setPatientLimitExcess(r.patientLimitExcess());
        line.setLimitConsumption(r.limitConsumption());
        line.setBindingRemainingLimit(r.bindingRemainingLimit());
        line.setPatientCoverageShare(r.patientCoverageShare());
        line.setPatientTotalResponsibility(r.patientTotalResponsibility());
        line.setInsurerGrossShare(r.insurerGrossShare());
        line.setProviderDiscountPercent(r.providerDiscountPercent());
        line.setProviderContractDiscount(r.providerContractDiscount());
        line.setProviderNetBeforeRejection(r.providerNetBeforeRejection());
        line.setProviderRejectedAmountV2(r.providerRejectedAmount());
        line.setInsurerFinalPayment(r.insurerFinalPayment());

        // Compatibility/reporting columns now mirror the canonical result; no
        // second formula is allowed here.
        line.setApprovedAmount(r.insurerFinalPayment());
        line.setCompanyShare(r.insurerFinalPayment());
        line.setPatientShare(r.patientTotalResponsibility());
        line.setPriceExcessRefused(r.contractualPriceExcess());
        line.setLimitRefused(r.patientLimitExcess());
        line.setRefusedAmount(r.contractualPriceExcess().add(r.providerRejectedAmount()));

        // P1.6: derivable again -- UnifiedLimitDecision is the SAME decision
        // Save-A already made (or, on the fresh-resolve path, its exact
        // canonical equivalent), unlike the monetary-only result this used
        // to be set to null against. ClaimLine's own @PrePersist hook only
        // fills approvedQuantity when it is still null, so this explicit
        // value -- set before persist -- always wins.
        line.setApprovedQuantity(decision.approvedQuantity());

        if (decision.amount().configured() != null) {
            line.setAmountLimitSnapshot(decision.amount().configured());
            line.setUsedAmountSnapshot(decision.amount().committed().add(decision.amount().reserved()));
            line.setRemainingAmountSnapshot(decision.amount().remaining());
        }
    }

    /**
     * The policy that applied ON THE SERVICE DATE. This used to return the
     * member's current pointer whenever one existed, consulting the date only
     * as a fallback -- so a backdated claim was adjudicated against today's
     * limits and coverage percentages. Fails closed: an unresolvable policy
     * stops adjudication rather than flowing on as null.
     */
    private BenefitPolicy resolvePolicy(Claim claim, LocalDate date) {
        return memberPolicyResolver.resolveForOrFail(claim.getMember(), date);
    }

    private BigDecimal requiredPositive(BigDecimal value, String field, int index) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(field + " must be positive for line index=" + index);
        }
        return value;
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
