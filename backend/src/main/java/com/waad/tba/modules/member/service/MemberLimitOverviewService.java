package com.waad.tba.modules.member.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.PolicySummaryRow;
import com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.security.AuthorizedMemberScope;
import com.waad.tba.modules.member.security.MemberAccessDeniedException;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.AlertStatus;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.Mode;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the ceiling figures the members list shows, for a whole page at a
 * time.
 *
 * The single reason this class exists is to keep the number of queries a
 * function of the page rather than of its rows. It performs six, whatever the
 * page holds:
 *
 *   1. the dated policy assignment for every member
 *   2. whether those policies were in force, and whose they are
 *   3. the dated employer of every member, to check 2 against
 *   4. the annual limit of each distinct policy that survived
 *   5. committed against the general ceiling
 *   6. reserved against it
 *
 * The first three belong to MemberPolicyResolver and are the same checks its
 * single-member path makes; the split keeps dated resolution unable to see
 * money, and this service unable to re-derive resolution.
 *
 * It computes nothing financial of its own. Every figure comes from
 * LimitBalanceReader, which is the same reader the approval engine consults,
 * so a row cannot disagree with the decision that will be made about it.
 */
@Service
@RequiredArgsConstructor
public class MemberLimitOverviewService {

    private final MemberPolicyResolver policyResolver;
    private final com.waad.tba.modules.member.repository.MemberRepository memberRepository;
    private final MemberQueryAccessPolicy queryAccessPolicy;
    private final BenefitPolicyRepository policyRepository;
    private final LimitBalanceReader limitBalanceReader;

    /**
     * Matches the list's own maximum page size. A page is the only legitimate
     * caller, so anything larger is either a mistake or an extraction.
     */
    private static final int MAX_BATCH_SIZE = 200;

    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.20");
    private static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("0.10");

    /**
     * The entry point an HTTP caller must use: authorises the ids first, then
     * reads.
     *
     * Fails the whole request if any id falls outside the caller's scope
     * rather than dropping it from the result. A legitimate page never
     * contains one, and silently returning fewer rows than were asked for is
     * how an id-probing loop learns which members exist -- the caller cannot
     * tell "outside your scope" from "no ceiling configured" if both come back
     * as an absent key.
     */
    @Transactional(readOnly = true)
    public Map<Long, CurrentGeneralLimitSummary> authorizedSummariesFor(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (memberIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessRuleException(
                    "عدد المستفيدين في الطلب يتجاوز الحد المسموح (" + MAX_BATCH_SIZE + ")");
        }

        AuthorizedMemberScope scope = queryAccessPolicy.requireListing(
                MemberOperation.VIEW_FINANCIALS, null);

        for (Member member : memberRepository.findAllById(memberIds)) {
            Long employerId = member.getEmployer() == null ? null : member.getEmployer().getId();
            if (!scope.covers(employerId)) {
                throw new MemberAccessDeniedException(MemberOperation.VIEW_FINANCIALS,
                        "أحد المستفيدين المطلوبين خارج نطاق المستخدم");
            }
        }
        return summariesFor(memberIds);
    }

    /**
     * @param memberIds the page being rendered; the caller has already applied
     *                  whatever access scope governs which members it may see,
     *                  and this method adds none of its own. HTTP callers must
     *                  use {@link #authorizedSummariesFor(Collection)}.
     */
    @Transactional(readOnly = true)
    public Map<Long, CurrentGeneralLimitSummary> summariesFor(Collection<Long> memberIds) {
        return summariesFor(memberIds, Clock.systemDefaultZone());
    }

    /**
     * @param clock injected only so tests can pin the day; no caller supplies
     *              a date. Letting the request choose one would turn a
     *              current-balance screen into a historical-balance query --
     *              a different question, with different authorization, asked
     *              through a parameter nobody guards.
     */
    @Transactional(readOnly = true)
    public Map<Long, CurrentGeneralLimitSummary> summariesFor(Collection<Long> memberIds, Clock clock) {
        LocalDate asOfDate = LocalDate.now(clock);
        LocalDateTime readAt = LocalDateTime.now(clock);

        Map<Long, CurrentGeneralLimitSummary> result = new LinkedHashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return result;
        }

        Map<Long, ResolvedMemberPolicy> resolved = policyResolver.resolveForMembers(memberIds, asOfDate);

        Map<Long, Long> policyIdByMemberId = new LinkedHashMap<>();
        Set<Long> policyIds = new LinkedHashSet<>();
        for (var entry : resolved.entrySet()) {
            ResolvedMemberPolicy policy = entry.getValue();
            policyIdByMemberId.put(entry.getKey(), policy.isFound() ? policy.policyId() : null);
            if (policy.isFound()) {
                policyIds.add(policy.policyId());
            }
        }

        Map<Long, BigDecimal> annualLimitByPolicyId = annualLimits(policyIds);

        Map<Long, GeneralCeilingReading> readings = limitBalanceReader.readGeneralCeilingBulk(
                policyIdByMemberId, annualLimitByPolicyId,
                LocalDate.of(asOfDate.getYear(), 1, 1),
                LocalDate.of(asOfDate.getYear(), 12, 31));

        for (Long memberId : policyIdByMemberId.keySet()) {
            ResolvedMemberPolicy policy = resolved.get(memberId);
            GeneralCeilingReading reading = readings.get(memberId);
            result.put(memberId, toSummary(asOfDate, readAt, policyIdByMemberId.get(memberId),
                    policy, reading));
        }
        return result;
    }

    /**
     * One query for every policy on the page. Members share policies, so this
     * is a handful of rows however long the page is.
     *
     * Reads a projection rather than the entities: BenefitPolicy has an EAGER
     * element collection, so findAllById would issue an extra select per
     * distinct policy and quietly restore the per-row cost this whole class
     * exists to remove.
     *
     * A limit of zero or less is treated as no ceiling, matching
     * BenefitPolicyCoverageService -- and the column is NOT NULL since V33, so
     * zero is how an uncapped policy is actually written. That equivalence is
     * the existing convention rather than a choice made here; what matters is
     * that both surfaces answer the same way, because a policy that reads as
     * unlimited in one screen and fully spent in another is worse than either
     * answer.
     */
    private Map<Long, BigDecimal> annualLimits(Set<Long> policyIds) {
        Map<Long, BigDecimal> limits = new HashMap<>();
        if (policyIds.isEmpty()) {
            return limits;
        }
        for (PolicySummaryRow policy : policyRepository.findSummaryRows(policyIds)) {
            BigDecimal annualLimit = policy.annualLimit();
            if (annualLimit != null && annualLimit.compareTo(BigDecimal.ZERO) > 0) {
                limits.put(policy.policyId(), annualLimit);
            }
        }
        return limits;
    }

    private CurrentGeneralLimitSummary toSummary(LocalDate asOfDate, LocalDateTime readAt,
            Long policyId, ResolvedMemberPolicy policy, GeneralCeilingReading reading) {

        // A policy that could not be read is unavailable regardless of what
        // the balance query then returned for it. So is an ambiguous
        // assignment -- there is no single ceiling to report -- and so is an
        // employer mismatch, which says the data is wrong rather than that
        // the member has no cover.
        //
        // NOT_ASSIGNED and POLICY_NOT_IN_FORCE deliberately fall through: both
        // are real answers meaning no ceiling applied, and the reading below
        // reports them as NOT_CONFIGURED.
        if (policy != null && (policy.outcome() == ResolvedMemberPolicy.Outcome.UNAVAILABLE
                || policy.outcome() == ResolvedMemberPolicy.Outcome.AMBIGUOUS
                || policy.outcome() == ResolvedMemberPolicy.Outcome.EMPLOYER_MISMATCH)) {
            return empty(asOfDate, readAt, Mode.UNAVAILABLE, AlertStatus.UNAVAILABLE);
        }
        if (reading == null) {
            return empty(asOfDate, readAt, Mode.UNAVAILABLE, AlertStatus.UNAVAILABLE);
        }

        return switch (reading.mode()) {
            case UNAVAILABLE -> empty(asOfDate, readAt, Mode.UNAVAILABLE, AlertStatus.UNAVAILABLE);
            case NOT_CONFIGURED -> empty(asOfDate, readAt, Mode.NOT_CONFIGURED, AlertStatus.UNAVAILABLE);
            case UNLIMITED -> new CurrentGeneralLimitSummary(asOfDate, readAt, Mode.UNLIMITED,
                    policyId, null, reading.committed(), reading.reserved(), null, null, null,
                    AlertStatus.UNLIMITED);
            case FOUND -> found(asOfDate, readAt, policyId, reading);
        };
    }

    private CurrentGeneralLimitSummary found(LocalDate asOfDate, LocalDateTime readAt,
            Long policyId, GeneralCeilingReading reading) {
        BigDecimal limit = reading.limit();
        BigDecimal utilization = reading.committed()
                .multiply(BigDecimal.valueOf(100))
                .divide(limit, 1, RoundingMode.HALF_UP);

        return new CurrentGeneralLimitSummary(asOfDate, readAt, Mode.FOUND, policyId,
                limit, reading.committed(), reading.reserved(),
                reading.actualRemaining(), reading.reservableAvailable(),
                utilization, alertFor(reading));
    }

    /**
     * Severity is judged on what may still be committed, not on what has been
     * spent.
     *
     * The two measures cannot disagree in the dangerous direction --
     * reservableAvailable is never above actualRemaining, so a member close to
     * their ceiling by consumption is at least as close by availability. Using
     * availability therefore reports the more severe of the two by
     * construction, and a large hold correctly raises the alarm before any of
     * it has been spent.
     */
    private AlertStatus alertFor(GeneralCeilingReading reading) {
        if (reading.actualRemaining().signum() < 0) {
            return AlertStatus.EXCEEDED;
        }
        BigDecimal reservable = reading.reservableAvailable();
        if (reservable.signum() <= 0) {
            return AlertStatus.EXHAUSTED;
        }
        BigDecimal shareLeft = reservable.divide(reading.limit(), 4, RoundingMode.HALF_UP);
        if (shareLeft.compareTo(CRITICAL_THRESHOLD) <= 0) {
            return AlertStatus.CRITICAL;
        }
        if (shareLeft.compareTo(WARNING_THRESHOLD) <= 0) {
            return AlertStatus.WARNING;
        }
        return AlertStatus.NORMAL;
    }

    private CurrentGeneralLimitSummary empty(LocalDate asOfDate, LocalDateTime readAt,
            Mode mode, AlertStatus alertStatus) {
        return new CurrentGeneralLimitSummary(asOfDate, readAt, mode,
                null, null, null, null, null, null, null, alertStatus);
    }
}
