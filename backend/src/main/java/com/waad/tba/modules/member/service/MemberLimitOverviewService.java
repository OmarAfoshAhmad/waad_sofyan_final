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
import com.waad.tba.modules.benefitpolicy.repository.PolicyAnnualLimit;
import com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.AlertStatus;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.Mode;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the ceiling figures the members list shows, for a whole page at a
 * time.
 *
 * The single reason this class exists is to keep the number of queries a
 * function of the page rather than of its rows. It performs four, whatever the
 * page holds:
 *
 *   1. the dated policy assignment for every member
 *   2. the annual limit of each distinct policy those resolve to
 *   3. committed against the general ceiling
 *   4. reserved against it
 *
 * It computes nothing financial of its own. Every figure comes from
 * LimitBalanceReader, which is the same reader the approval engine consults,
 * so a row cannot disagree with the decision that will be made about it.
 */
@Service
@RequiredArgsConstructor
public class MemberLimitOverviewService {

    private final MemberPolicyResolver policyResolver;
    private final BenefitPolicyRepository policyRepository;
    private final LimitBalanceReader limitBalanceReader;

    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.20");
    private static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("0.10");

    /**
     * @param memberIds the page being rendered; the caller has already applied
     *                  whatever access scope governs which members it may see,
     *                  and this method adds none of its own
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
            result.put(memberId, toSummary(asOfDate, readAt, policy, reading));
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
        for (PolicyAnnualLimit policy : policyRepository.findAnnualLimits(policyIds)) {
            BigDecimal annualLimit = policy.annualLimit();
            if (annualLimit != null && annualLimit.compareTo(BigDecimal.ZERO) > 0) {
                limits.put(policy.policyId(), annualLimit);
            }
        }
        return limits;
    }

    private CurrentGeneralLimitSummary toSummary(LocalDate asOfDate, LocalDateTime readAt,
            ResolvedMemberPolicy policy, GeneralCeilingReading reading) {

        // A policy that could not be read is unavailable regardless of what
        // the balance query then returned for it; an ambiguous assignment is
        // the same, because there is no single ceiling to report.
        if (policy != null && (policy.outcome() == ResolvedMemberPolicy.Outcome.UNAVAILABLE
                || policy.outcome() == ResolvedMemberPolicy.Outcome.AMBIGUOUS)) {
            return empty(asOfDate, readAt, Mode.UNAVAILABLE, AlertStatus.UNAVAILABLE);
        }
        if (reading == null) {
            return empty(asOfDate, readAt, Mode.UNAVAILABLE, AlertStatus.UNAVAILABLE);
        }

        return switch (reading.mode()) {
            case UNAVAILABLE -> empty(asOfDate, readAt, Mode.UNAVAILABLE, AlertStatus.UNAVAILABLE);
            case NOT_CONFIGURED -> empty(asOfDate, readAt, Mode.NOT_CONFIGURED, AlertStatus.UNAVAILABLE);
            case UNLIMITED -> new CurrentGeneralLimitSummary(asOfDate, readAt, Mode.UNLIMITED,
                    null, reading.committed(), reading.reserved(), null, null, null,
                    AlertStatus.UNLIMITED);
            case FOUND -> found(asOfDate, readAt, reading);
        };
    }

    private CurrentGeneralLimitSummary found(LocalDate asOfDate, LocalDateTime readAt,
            GeneralCeilingReading reading) {
        BigDecimal limit = reading.limit();
        BigDecimal utilization = reading.committed()
                .multiply(BigDecimal.valueOf(100))
                .divide(limit, 1, RoundingMode.HALF_UP);

        return new CurrentGeneralLimitSummary(asOfDate, readAt, Mode.FOUND,
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
                null, null, null, null, null, null, alertStatus);
    }
}
