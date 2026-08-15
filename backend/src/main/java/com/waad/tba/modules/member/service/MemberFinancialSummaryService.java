package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.projection.MemberFinancialAggregateProjection;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
    private final MemberPolicyResolver memberPolicyResolver;

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

        LocalDate today = LocalDate.now();
        Map<Long, BenefitPolicy> policyByMember = resolvePolicies(members, today);

        Map<Long, MemberFinancialAggregateProjection> claimStatsByMember = claimRepository
                .findFinancialAggregatesByMemberIds(memberIds).stream()
                .collect(Collectors.toMap(MemberFinancialAggregateProjection::getMemberId, row -> row));

        // WAAD-FIN-1.0 S4: the ceiling's own axis, read once for the whole batch --
        // see BenefitPolicyCoverageService.getLimitConsumedForYear's javadoc for why
        // this must never be totalApproved.
        Map<Long, BigDecimal> consumedByMember = coverageService.getLimitConsumedForYear(
                memberIds, today.getYear(), null);

        Map<Long, MemberFinancialSummaryDto> result = new LinkedHashMap<>();
        for (Member member : members) {
            result.put(member.getId(), buildSummary(
                    member, policyByMember.get(member.getId()),
                    claimStatsByMember.get(member.getId()),
                    consumedByMember.getOrDefault(member.getId(), BigDecimal.ZERO)));
        }
        return result;
    }

    /**
     * Direct policy first, employer-level effective policy as fallback --
     * resolved once per member up front so the per-member build step never
     * touches the database.
     */
    private Map<Long, BenefitPolicy> resolvePolicies(List<Member> members, LocalDate asOfDate) {
        Map<Long, BenefitPolicy> byMember = new LinkedHashMap<>();
        for (Member member : members) {
            BenefitPolicy policy = memberPolicyResolver.resolveFor(member, asOfDate).orElse(null);
            if (policy != null) {
                byMember.put(member.getId(), policy);
            }
        }
        return byMember;
    }

    /**
     * Assembles one member's DTO from already-fetched pieces -- pure, no
     * database access, so it stays trivially testable and identical for the
     * single- and bulk-read paths.
     */
    private MemberFinancialSummaryDto buildSummary(
            Member member, BenefitPolicy policy,
            MemberFinancialAggregateProjection stats, BigDecimal limitConsumed) {

        MemberFinancialSummaryDto.MemberFinancialSummaryDtoBuilder builder = MemberFinancialSummaryDto.builder();

        builder.memberId(member.getId())
                .fullName(member.getFullName())
                .cardNumber(member.getCardNumber())
                .barcode(member.getBarcode())
                .isDependent(member.getParent() != null);

        if (policy != null) {
            builder.policyId(policy.getId())
                    .policyName(policy.getName())
                    .annualLimit(policy.getAnnualLimit())
                    .policyStartDate(policy.getStartDate())
                    .policyEndDate(policy.getEndDate())
                    .policyActive(policy.isActive() && policy.isEffectiveOn(LocalDate.now()));
        } else {
            builder.policyActive(false);
        }

        BigDecimal totalClaimed = stats == null ? BigDecimal.ZERO : nz(stats.getTotalClaimed());
        BigDecimal totalApproved = stats == null ? BigDecimal.ZERO : nz(stats.getTotalApproved());
        BigDecimal totalPaid = stats == null ? BigDecimal.ZERO : nz(stats.getTotalPaid());
        BigDecimal totalPatientCoPay = stats == null ? BigDecimal.ZERO : nz(stats.getTotalPatientCoPay());
        BigDecimal totalDeductible = stats == null ? BigDecimal.ZERO : nz(stats.getTotalDeductibleApplied());

        builder.totalClaimed(totalClaimed)
                .totalApproved(totalApproved)
                .totalPaid(totalPaid)
                .totalPatientCoPay(totalPatientCoPay)
                .totalDeductibleApplied(totalDeductible)
                .limitConsumedAmount(limitConsumed);

        BigDecimal remainingCoverage = null;
        BigDecimal utilizationPercent = BigDecimal.ZERO;
        if (policy != null && policy.getAnnualLimit() != null && policy.getAnnualLimit().compareTo(BigDecimal.ZERO) > 0) {
            remainingCoverage = policy.getAnnualLimit().subtract(limitConsumed).max(BigDecimal.ZERO);
            utilizationPercent = limitConsumed
                    .multiply(BigDecimal.valueOf(100))
                    .divide(policy.getAnnualLimit(), 2, RoundingMode.HALF_UP);
            builder.remainingCoverage(remainingCoverage).utilizationPercent(utilizationPercent);
        }

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

        String warning = null;
        boolean nearingLimit = false;
        boolean expiringSoon = false;

        if (policy != null) {
            if (utilizationPercent.compareTo(BigDecimal.valueOf(80)) >= 0) {
                nearingLimit = true;
                warning = "⚠️ تنبيه: اقتربت من حد التغطية السنوي (" + utilizationPercent.intValue() + "% مستهلك)";
            }

            if (policy.getEndDate() != null) {
                long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), policy.getEndDate());
                if (daysUntilExpiry > 0 && daysUntilExpiry <= 30) {
                    expiringSoon = true;
                    if (warning == null) {
                        warning = "⚠️ تنبيه: الوثيقة ستنتهي خلال " + daysUntilExpiry + " يوم";
                    }
                } else if (daysUntilExpiry <= 0) {
                    warning = "❌ الوثيقة منتهية";
                }
            }
        } else {
            warning = "❌ لا توجد وثيقة تغطية مربوطة بالعضو";
        }

        builder.warningMessage(warning)
                .nearingLimit(nearingLimit)
                .policyExpiringSoon(expiringSoon);

        MemberFinancialSummaryDto summary = builder.build();
        log.debug("✅ Financial summary built: member={}, claimed={}, approved={}, consumed={}, remaining={}",
                member.getId(), totalClaimed, totalApproved, limitConsumed, remainingCoverage);
        return summary;
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
