package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.PolicySummaryRow;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.projection.MemberFinancialAggregateProjection;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.dto.CoverageLimitsDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Member Financial Summary Service
 *
 * Provides comprehensive financial overview for members by aggregating:
 * - Benefit policy information
 * - Claim statistics and amounts (via one grouped database query -- see
 *   {@link #getFinancialSummaries(Collection)})
 * - Utilization metrics, on the WAAD-FIN-1.0 limit-consumption axis
 * - Alerts and warnings
 *
 * REUSES existing services (no duplicate logic):
 * - BenefitPolicyCoverageService for limit-consumption reads (single AND
 *   bulk) and remaining coverage
 * - ClaimRepository.findFinancialAggregatesByMemberIds for claim statistics
 *   (single AND bulk) -- no claim entity is ever loaded into memory here
 *
 * @version 2026.2
 * @since Phase 1 - Financial Lifecycle Completion; rebuilt member-closure
 *        2026-08 to fix two defects: the annual-ceiling axis mismatch
 *        against the WAAD-FIN-1.0 engine, and unbounded per-member claim
 *        loading (a family eligibility check cost one full claim-history
 *        load per family member before this rewrite).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberFinancialSummaryService {

    private final MemberRepository memberRepository;
    private final BenefitPolicyCoverageService coverageService;
    private final ClaimRepository claimRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MemberQueryAccessPolicy queryAccessPolicy;
    private final MemberLimitOverviewService limitOverviewService;
    private final BenefitPolicyRepository policyRepository;

    private static final String UNAVAILABLE_MESSAGE = "تعذّر عرض الرصيد حالياً";
    private static final String NO_POLICY_MESSAGE = "لا توجد وثيقة تغطية سارية للمستفيد";
    private static final String EXCEEDED_MESSAGE = "تم تجاوز حد التغطية السنوي";
    private static final String EXHAUSTED_MESSAGE = "لا يوجد رصيد متاح لالتزام جديد";
    private static final String NEARING_MESSAGE = "اقترب المستفيد من حد التغطية السنوي";
    private static final String POLICY_EXPIRED_MESSAGE = "الوثيقة منتهية";
    private static final String POLICY_EXPIRING_MESSAGE = "الوثيقة ستنتهي خلال %d يوم";

    /**
     * HTTP/read-model entry point. Internal eligibility batching deliberately uses
     * {@link #getFinancialSummaries(Collection)} after its own member authorization;
     * controllers must use this method so an id cannot bypass tenant scope.
     */
    public MemberFinancialSummaryDto getAuthorizedFinancialSummary(Long memberId, MemberOperation operation) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        Long employerId = member.getEmployer() == null ? null : member.getEmployer().getId();
        queryAccessPolicy.requireMember(operation, employerId);
        return getFinancialSummary(memberId);
    }

    /**
     * Financial summary for exactly one member. Thin wrapper over
     * {@link #getFinancialSummaries(Collection)} so the single- and
     * bulk-member code paths can never drift apart -- there is only one
     * implementation of "how a financial summary is built".
     *
     * @param memberId Member ID
     * @return Financial summary DTO with all metrics
     * @throws ResourceNotFoundException if member not found
     */
    public MemberFinancialSummaryDto getFinancialSummary(Long memberId) {
        Map<Long, MemberFinancialSummaryDto> result = getFinancialSummaries(List.of(memberId));
        MemberFinancialSummaryDto summary = result.get(memberId);
        if (summary == null) {
            throw new ResourceNotFoundException("Member", "id", memberId);
        }
        return summary;
    }

    /**
     * Financial summary for every member in {@code memberIds}, in a fixed
     * small number of database round trips regardless of how many members are
     * requested (one member lookup, one claim-statistics aggregate, one
     * limit-consumption aggregate) -- this is what lets a family-eligibility
     * check (principal + N dependents) cost O(1) queries instead of O(N).
     *
     * Members that do not exist are silently omitted from the result map
     * (not present as a null value) -- callers that need to distinguish
     * "not found" from "found with zero claims" must check
     * {@code result.containsKey(id)}.
     *
     * @param memberIds every member whose summary is being read together
     * @return map from member id to its financial summary, one entry per
     *         member id that actually exists
     */
    public Map<Long, MemberFinancialSummaryDto> getFinancialSummaries(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        log.info("📊 Generating financial summaries for {} member(s)", memberIds.size());

        List<Member> members = memberRepository.findAllById(memberIds);
        if (members.isEmpty()) {
            return Map.of();
        }

        // One reader, one axis, one transaction snapshot. The ceiling block
        // comes from MemberLimitOverviewService rather than being recomputed
        // here, so this screen and the members list cannot disagree about the
        // same member on the same date -- which they did, on two counts: this
        // service read committed only, and it clamped the result at zero.
        Map<Long, CurrentGeneralLimitSummary> ceilings = limitOverviewService.summariesFor(memberIds);

        Set<Long> policyIds = ceilings.values().stream()
                .map(CurrentGeneralLimitSummary::policyId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<Long, PolicySummaryRow> policyById = policyIds.isEmpty() ? Map.of()
                : policyRepository.findSummaryRows(policyIds).stream()
                        .collect(Collectors.toMap(PolicySummaryRow::policyId, row -> row));

        Map<Long, MemberFinancialAggregateProjection> claimStatsByMember = claimRepository
                .findFinancialAggregatesByMemberIds(memberIds).stream()
                .collect(Collectors.toMap(MemberFinancialAggregateProjection::getMemberId, row -> row));

        Map<Long, MemberFinancialSummaryDto> result = new LinkedHashMap<>();
        for (Member member : members) {
            CurrentGeneralLimitSummary ceiling = ceilings.get(member.getId());
            PolicySummaryRow policy = ceiling == null || ceiling.policyId() == null
                    ? null : policyById.get(ceiling.policyId());
            result.put(member.getId(), buildSummary(
                    member, policy, ceiling, claimStatsByMember.get(member.getId())));
        }
        return result;
    }

    /**
     * Assembles one member's DTO from already-fetched pieces -- pure, no
     * database access, so it stays trivially testable and identical for the
     * single- and bulk-read paths.
     */
    private MemberFinancialSummaryDto buildSummary(
            Member member, PolicySummaryRow policy, CurrentGeneralLimitSummary ceiling,
            MemberFinancialAggregateProjection stats) {

        MemberFinancialSummaryDto.MemberFinancialSummaryDtoBuilder builder = MemberFinancialSummaryDto.builder();

        builder.memberId(member.getId())
                .fullName(member.getFullName())
                .cardNumber(member.getCardNumber())
                .barcode(member.getBarcode())
                .isDependent(member.getParent() != null);

        LocalDate asOfDate = ceiling == null ? LocalDate.now() : ceiling.asOfDate();
        builder.asOfDate(asOfDate)
                .readAt(ceiling == null ? null : ceiling.readAt())
                .ceilingMode(ceiling == null ? CurrentGeneralLimitSummary.Mode.UNAVAILABLE : ceiling.mode());

        if (policy != null) {
            // annualLimit is what applies to THIS member, which is the policy
            // figure only when no exception has been granted. Taking it from
            // the policy while the balances below come from the effective
            // ceiling reported a remaining balance larger than the limit.
            boolean ceilingHasFigures = ceiling != null
                    && ceiling.mode() == CurrentGeneralLimitSummary.Mode.FOUND;
            builder.policyId(policy.policyId())
                    .policyName(policy.name())
                    .annualLimit(ceilingHasFigures ? ceiling.limit() : policy.annualLimit())
                    .policyLimit(ceilingHasFigures ? ceiling.policyLimit() : policy.annualLimit())
                    .upliftAmount(ceilingHasFigures ? ceiling.uplift() : null)
                    .policyStartDate(policy.startDate())
                    .policyEndDate(policy.endDate())
                    .policyActive(policy.active() && policy.isInForceOn(asOfDate));
        } else {
            builder.policyActive(false);
        }

        // Signed on purpose, both of them. A member who has overspent shows a
        // negative figure rather than a zero, because zero is the one answer a
        // reconciler cannot tell apart from "exactly spent".
        builder.limitConsumedAmount(ceiling == null ? null : ceiling.committed())
                .reservedAmount(ceiling == null ? null : ceiling.reserved())
                .actualRemaining(ceiling == null ? null : ceiling.actualRemaining())
                .reservableAvailable(ceiling == null ? null : ceiling.reservableAvailable())
                .remainingCoverage(ceiling == null ? null : ceiling.actualRemaining())
                .utilizationPercent(ceiling == null ? null : ceiling.utilizationPercent());

        // Nulled, never zeroed: see the field docs. A zero here reads as
        // "nothing has been paid", which is a claim this system cannot make.
        builder.totalClaimed(null).totalApproved(null).totalPaid(null)
                .claimPaymentAttribution(
                        MemberFinancialSummaryDto.ClaimPaymentAttribution.NOT_SUPPORTED);

        builder.totalPatientCoPay(stats == null ? BigDecimal.ZERO : nz(stats.getTotalPatientCoPay()))
                .totalDeductibleApplied(stats == null ? BigDecimal.ZERO : nz(stats.getTotalDeductibleApplied()));

        int totalCount = stats == null ? 0 : stats.getClaimsCount().intValue();
        int pendingCount = stats == null ? 0 : stats.getPendingClaimsCount().intValue();
        int approvedCount = stats == null ? 0 : stats.getApprovedClaimsCount().intValue();
        int rejectedCount = stats == null ? 0 : stats.getRejectedClaimsCount().intValue();
        LocalDate lastClaimDate = stats == null || stats.getLastClaimAt() == null
                ? null : stats.getLastClaimAt().toLocalDate();

        builder.claimsCount(totalCount)
                .pendingClaimsCount(pendingCount)
                .approvedClaimsCount(approvedCount)
                .rejectedClaimsCount(rejectedCount)
                .lastClaimDate(lastClaimDate);

        builder.warningMessage(warningFor(policy, ceiling, asOfDate))
                .nearingLimit(nearingLimit(ceiling))
                .policyExpiringSoon(expiringSoon(policy, asOfDate));

        return builder.build();
    }

    /**
     * Judged on what may still be committed, not on what has been spent: a
     * member holding a large approved pre-authorization is near their ceiling
     * for every decision that matters, before any of it is spent.
     */
    private boolean nearingLimit(CurrentGeneralLimitSummary ceiling) {
        if (ceiling == null) {
            return false;
        }
        return switch (ceiling.alertStatus()) {
            case WARNING, CRITICAL, EXHAUSTED, EXCEEDED -> true;
            default -> false;
        };
    }

    private boolean expiringSoon(PolicySummaryRow policy, LocalDate asOfDate) {
        if (policy == null || policy.endDate() == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(asOfDate, policy.endDate());
        return days > 0 && days <= 30;
    }

    private String warningFor(PolicySummaryRow policy, CurrentGeneralLimitSummary ceiling,
            LocalDate asOfDate) {
        if (ceiling != null && ceiling.mode() == CurrentGeneralLimitSummary.Mode.UNAVAILABLE) {
            return UNAVAILABLE_MESSAGE;
        }
        if (policy == null) {
            return NO_POLICY_MESSAGE;
        }
        if (ceiling != null) {
            switch (ceiling.alertStatus()) {
                case EXCEEDED -> {
                    return EXCEEDED_MESSAGE;
                }
                case EXHAUSTED -> {
                    return EXHAUSTED_MESSAGE;
                }
                case CRITICAL, WARNING -> {
                    return NEARING_MESSAGE;
                }
                default -> { }
            }
        }
        if (policy.endDate() != null) {
            long days = ChronoUnit.DAYS.between(asOfDate, policy.endDate());
            if (days <= 0) {
                return POLICY_EXPIRED_MESSAGE;
            }
            if (days <= 30) {
                return String.format(POLICY_EXPIRING_MESSAGE, days);
            }
        }
        return null;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Get Coverage Limits (times and amounts) for a specific service based on
     * member's active policy. Times-used is read via
     * {@link ClaimRepository#countServiceUsageForMemberAndYear} -- a single
     * COUNT query -- not by loading every claim for the member (the pattern
     * {@link #getFinancialSummaries(Collection)} was rewritten to avoid;
     * this method carried the same defect until member-closure Phase 4).
     *
     * @param memberId    Member ID
     * @param serviceCode Medical Service Code
     * @return CoverageLimitsDto
     */
    public CoverageLimitsDto getServiceCoverageLimits(Long memberId, String serviceCode) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        MedicalService service = medicalServiceRepository.findByCode(serviceCode)
                .orElseThrow(() -> new BusinessRuleException("Service not found: " + serviceCode));

        // Get limits from policy
        var coverageInfoOpt = coverageService.getCoverageForService(member, service.getId());
        if (coverageInfoOpt.isEmpty()) {
            return CoverageLimitsDto.builder()
                    .covered(false)
                    .warningMessage("الخدمة غير مغطاة تحت هذه الوثيقة")
                    .build();
        }

        var coverageInfo = coverageInfoOpt.get();
        if (!coverageInfo.isCovered()) {
            return CoverageLimitsDto.builder()
                    .covered(false)
                    .warningMessage("الخدمة غير مغطاة تحت هذه الوثيقة")
                    .build();
        }

        int coveragePercent = coverageInfo.getCoveragePercent();
        BigDecimal amountLimit = coverageInfo.getAmountLimit();
        Integer timesLimit = coverageInfo.getTimesLimit();
        int timesUsed = 0;
        int remainingTimes = timesLimit != null ? timesLimit : 999;
        boolean timesLimitExceeded = false;
        String warningMessage = null;

        // If times limit exists, we must calculate historical usage
        if (timesLimit != null) {
            int currentYear = LocalDate.now().getYear();
            timesUsed = (int) Math.min(Integer.MAX_VALUE,
                    claimRepository.countServiceUsageForMemberAndYear(memberId, serviceCode, currentYear));

            remainingTimes = timesLimit - timesUsed;
            if (remainingTimes <= 0) {
                remainingTimes = 0;
                timesLimitExceeded = true;
                warningMessage = "تم تجاوز الحد الأقصى لعدد المرات المسموح بها (" + timesLimit
                        + " مرات). المرات المتبقية: صفر.";
            }
        }

        return CoverageLimitsDto.builder()
                .covered(true)
                .coveragePercent(coveragePercent)
                .amountLimit(amountLimit)
                .timesLimit(timesLimit)
                .timesUsed(timesUsed)
                .remainingTimes(remainingTimes)
                .timesLimitExceeded(timesLimitExceeded)
                .warningMessage(warningMessage)
                .build();
    }

}
