package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads committed/reserved GENERAL-CEILING balances for display and
 * reporting -- never for a live limit decision.
 *
 * P1.12.4: {@code read(...)} and {@code readForPreauthorizedClaim(...)}
 * (the per-bucket balance-set methods a live decision used to read) are
 * retired along with their sole caller, {@code EffectiveLimitResolver}/
 * {@code PreAuthorizationDecisionBuilder}'s legacy path -- the canonical
 * {@code BucketLimitSnapshotAdapter} (Claims since P1.5, PreAuth since
 * P1.12.3) reads the identical ledger data through its own bulk queries
 * instead. What remains here -- {@code readGeneralCeiling}/
 * {@code readGeneralCeilingBulk}/{@code readGeneralCeilingCommittedBulk} --
 * is read-only reporting (member balance screens, provider portal,
 * financial summaries), never part of any decision path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LimitBalanceReader {

    private final BenefitBucketConsumptionRepository consumptionRepository;
    private final com.waad.tba.modules.member.repository.MemberGeneralLimitUpliftRepository upliftRepository;

    /**
     * The POLICY_GENERAL ceiling read in isolation, for callers that have no
     * {@code benefitRuleId} to hand {@link #read} -- {@code validateAmountLimits}
     * checks a member's annual ceiling independent of any one claim line, so it
     * cannot go through the rule-bucket resolution path {@link #read} requires.
     * Same source, same two figures, same meaning as the POLICY_GENERAL branch
     * of {@link #read}: committed and reserved both come from the ledger.
     */
    public record GeneralCeilingBalance(
            /** What actually applies: the policy's limit plus any uplift for this member. */
            BigDecimal annualLimit,
            BigDecimal policyLimit,
            BigDecimal uplift,
            BigDecimal committed, BigDecimal reserved,
            BigDecimal actualRemaining, BigDecimal reservableAvailable) {}

    /**
     * @param policyAnnualLimit what the benefit policy grants everyone on it.
     *                          The ceiling this returns is that PLUS any
     *                          exceptional uplift granted to this member, and
     *                          {@code annualLimit} on the result is the sum
     * @return null when the policy carries no positive annual limit -- there is
     *         no ceiling to report, not a ceiling of zero. An uplift cannot
     *         create one: raising nothing would produce a limit where the
     *         policy deliberately set none
     */
    @Transactional(readOnly = true)
    public GeneralCeilingBalance readGeneralCeiling(Long memberId, Long policyId, BigDecimal policyAnnualLimit,
            LocalDate periodStart, LocalDate periodEnd, Long excludeClaimId) {
        if (policyAnnualLimit == null || policyAnnualLimit.signum() <= 0) return null;

        // Resolved here for the same reason the bulk read resolves it here:
        // every caller of this method is a DECISION -- what a claim may
        // consume, what a pre-authorization may hold, whether an approval
        // still fits. A caller that forgot the uplift would not report a
        // slightly low figure, it would refuse the member the money an
        // administrator deliberately granted them, with nothing on screen to
        // say why.
        BigDecimal uplift = upliftInForce(java.util.Set.of(memberId), periodStart, periodEnd)
                .getOrDefault(memberId, BigDecimal.ZERO);
        BigDecimal annualLimit = policyAnnualLimit.add(uplift);

        BigDecimal committed = consumptionRepository.sumGeneralScopeCommitted(
                memberId, policyId, periodStart, periodEnd, excludeClaimId);
        BigDecimal reserved = consumptionRepository.sumGeneralScopeReserved(
                memberId, policyId, periodStart, periodEnd);
        BigDecimal actualRemaining = annualLimit.subtract(committed);
        BigDecimal reservableAvailable = actualRemaining.subtract(reserved);
        return new GeneralCeilingBalance(annualLimit, policyAnnualLimit, uplift,
                committed, reserved, actualRemaining, reservableAvailable);
    }

    /**
     * Both halves of the general ceiling for a whole page, in two queries.
     *
     * Committed and reserved are read inside one read-only transaction on
     * purpose. Read separately they can straddle a claim approval, and the
     * column would then show a remaining balance from one instant beside a
     * hold from another -- a state that never existed, presented as though it
     * did.
     *
     * The mode is per member and explicit. A policy with no annual limit is
     * UNLIMITED, a member the caller could not resolve a policy for is
     * NOT_CONFIGURED, and a failed read is UNAVAILABLE; none of them is zero,
     * because zero is a balance and these are the absence of one.
     *
     * @param annualLimitByPolicyId the ceiling for each policy in play, looked
     *                              up once by the caller rather than per
     *                              member -- members share policies, and this
     *                              keeps the query count on the page rather
     *                              than on its rows
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, GeneralCeilingReading> readGeneralCeilingBulk(
            java.util.Map<Long, Long> policyIdByMemberId,
            java.util.Map<Long, BigDecimal> annualLimitByPolicyId,
            LocalDate periodStart, LocalDate periodEnd) {

        java.util.Map<Long, GeneralCeilingReading> result = new java.util.LinkedHashMap<>();
        if (policyIdByMemberId == null || policyIdByMemberId.isEmpty()) {
            return result;
        }

        // Resolved here rather than passed in. An exceptional uplift is part of
        // what a member's ceiling IS, so a caller that forgot to supply it
        // would not get a slightly wrong figure -- it would get a different
        // member's ceiling and no sign of it. One query per page, keyed by
        // member because the exception belongs to a person and not to the
        // policy their colleagues share.
        java.util.Map<Long, BigDecimal> upliftByMemberId =
                upliftInForce(policyIdByMemberId.keySet(), periodStart, periodEnd);

        java.util.Map<Long, BigDecimal> committedByMember;
        java.util.Map<Long, BigDecimal> reservedByMember;
        try {
            committedByMember = sumBulk(
                    consumptionRepository.sumGeneralScopeCommittedBulk(
                            policyIdByMemberId.keySet(), periodStart, periodEnd, null),
                    policyIdByMemberId);
            reservedByMember = sumBulk(
                    consumptionRepository.sumGeneralScopeReservedBulk(
                            policyIdByMemberId.keySet(), periodStart, periodEnd),
                    policyIdByMemberId);
        } catch (RuntimeException ex) {
            log.error("Bulk general-ceiling read failed for {} members [{} .. {}]",
                    policyIdByMemberId.size(), periodStart, periodEnd, ex);
            for (Long memberId : policyIdByMemberId.keySet()) {
                result.put(memberId, GeneralCeilingReading.unavailable("تعذّرت قراءة الرصيد"));
            }
            return result;
        }

        for (var entry : policyIdByMemberId.entrySet()) {
            Long memberId = entry.getKey();
            Long policyId = entry.getValue();
            BigDecimal committed = committedByMember.getOrDefault(memberId, BigDecimal.ZERO);
            BigDecimal reserved = reservedByMember.getOrDefault(memberId, BigDecimal.ZERO);

            if (policyId == null) {
                result.put(memberId, GeneralCeilingReading.notConfigured("لا توجد وثيقة سارية"));
                continue;
            }
            BigDecimal annualLimit = annualLimitByPolicyId == null
                    ? null
                    : annualLimitByPolicyId.get(policyId);
            if (annualLimit == null) {
                // The policy exists but sets no monetary ceiling. Consumption
                // is still real; there is simply nothing to measure it against.
                result.put(memberId, GeneralCeilingReading.unlimited(committed, reserved));
                continue;
            }
            result.put(memberId, GeneralCeilingReading.found(annualLimit,
                    upliftByMemberId.getOrDefault(memberId, BigDecimal.ZERO), committed, reserved));
        }
        return result;
    }

    /**
     * The exceptional increases in force for these members.
     *
     * The date asked about is the one the rest of the read answers for. This
     * method is handed the ceiling period rather than an as-of date, so it
     * uses today when today falls inside that period and the period's last day
     * otherwise -- reading a closed year answers with the uplifts that applied
     * at its end, not with whatever is in force now.
     */
    private java.util.Map<Long, BigDecimal> upliftInForce(java.util.Set<Long> memberIds,
            LocalDate periodStart, LocalDate periodEnd) {
        java.util.Map<Long, BigDecimal> byMember = new java.util.HashMap<>();
        if (memberIds.isEmpty()) {
            return byMember;
        }
        LocalDate today = LocalDate.now();
        LocalDate asOfDate = today.isBefore(periodStart) ? periodStart
                : today.isAfter(periodEnd) ? periodEnd
                : today;
        try {
            for (Object[] row : upliftRepository.sumInForceByMember(memberIds, asOfDate)) {
                byMember.put((Long) row[0], (BigDecimal) row[1]);
            }
        } catch (RuntimeException ex) {
            // A ceiling read that silently drops an uplift reports a lower
            // ceiling than the one that applies, which is the direction that
            // wrongly refuses treatment. Fail the read instead.
            log.error("Uplift read failed for {} members as of {}", memberIds.size(), asOfDate, ex);
            throw ex;
        }
        return byMember;
    }

    /** Keeps only the rows belonging to each member's own policy. */
    private java.util.Map<Long, BigDecimal> sumBulk(
            java.util.List<? extends BenefitBucketConsumptionRepository.GeneralCeilingBulkProjection> rows,
            java.util.Map<Long, Long> policyIdByMemberId) {
        java.util.Map<Long, BigDecimal> byMember = new HashMap<>();
        for (var row : rows) {
            Long currentPolicyId = policyIdByMemberId.get(row.getMemberId());
            // A row under a policy the member has since left belongs to that
            // policy's ceiling, never to this one.
            if (currentPolicyId != null && currentPolicyId.equals(row.getPolicyId())) {
                byMember.merge(row.getMemberId(),
                        row.getAmount() == null ? BigDecimal.ZERO : row.getAmount(),
                        BigDecimal::add);
            }
        }
        return byMember;
    }

    /**
     * Bulk counterpart of {@link #readGeneralCeiling}'s committed figure --
     * one query for the whole batch, same O(1) shape
     * {@code MemberFinancialSummaryService.getFinancialSummaries} already
     * guarantees. Committed only: bulk display reads (a member-financial-summary
     * screen, a family eligibility check) show what has actually been spent,
     * never a reservation.
     *
     * A member absent from {@code policyIdByMemberId} or with no committed rows
     * under THEIR OWN policy id is present in the result with ZERO, never
     * absent -- a caller that skips a missing key while trusting a present
     * zero is exactly the bug this method exists to prevent for members who
     * changed policies mid-period (whose historical rows sit under a DIFFERENT
     * policy id and must not be added to the current policy's ceiling).
     *
     * @param policyIdByMemberId each member's policy AS OF THE DATE that produced
     *                           periodStart/periodEnd -- never the member's
     *                           current pointer, for the same reason
     *                           {@link #readGeneralCeiling} takes an explicit
     *                           policyId rather than resolving one itself
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, BigDecimal> readGeneralCeilingCommittedBulk(
            java.util.Map<Long, Long> policyIdByMemberId, LocalDate periodStart, LocalDate periodEnd,
            Long excludeClaimId) {
        java.util.Map<Long, BigDecimal> result = new HashMap<>();
        for (Long memberId : policyIdByMemberId.keySet()) {
            result.put(memberId, BigDecimal.ZERO);
        }
        if (policyIdByMemberId.isEmpty()) return result;

        for (var row : consumptionRepository.sumGeneralScopeCommittedBulk(
                policyIdByMemberId.keySet(), periodStart, periodEnd, excludeClaimId)) {
            Long currentPolicyId = policyIdByMemberId.get(row.getMemberId());
            if (currentPolicyId != null && currentPolicyId.equals(row.getPolicyId())) {
                result.put(row.getMemberId(), row.getAmount());
            }
        }
        return result;
    }

}
