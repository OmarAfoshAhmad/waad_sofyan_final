package com.waad.tba.modules.benefitpolicy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.MemberBenefitUsageDto;
import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.security.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;

import lombok.RequiredArgsConstructor;

/** Read model for the beneficiary coverage drawer. No financial writes. */
@Service
@RequiredArgsConstructor
public class MemberBenefitUsageService {
    private final MemberRepository memberRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketUsageService usageService;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public MemberBenefitUsageDto get(Long memberId, LocalDate asOfDate) {
        var user = authorizationService.requireCurrentUser();
        if (!authorizationService.canAccessMember(user, memberId)) {
            throw new AccessDeniedException("لا تملك صلاحية عرض بيانات هذا المستفيد");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود"));
        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy == null) throw new BusinessRuleException("المستفيد غير مرتبط بوثيقة منافع");
        LocalDate date = asOfDate == null ? LocalDate.now() : asOfDate;
        List<MemberBenefitUsageDto.BucketUsage> rows = new ArrayList<>();
        for (BenefitLimitBucket bucket : bucketRepository.findByPolicyIdOrderByCode(policy.getId())) {
            if (!bucket.isActive()) continue;
            BucketPeriodCalculator.Period period = BucketPeriodCalculator.resolve(bucket, policy, date);
            BenefitBucketUsageService.UsageBreakdown usage = usageService.breakdown(
                    memberId, bucket.getId(), period.start(), period.end(), null);
            BigDecimal limit = bucket.getAmountLimit();
            BigDecimal used = usage.totals().amount();
            BigDecimal remaining = limit == null ? null : limit.subtract(used).max(BigDecimal.ZERO);
            BigDecimal percent = limit == null || limit.signum() <= 0 ? null
                    : used.multiply(new BigDecimal("100")).divide(limit, 2, RoundingMode.HALF_UP)
                            .min(new BigDecimal("100"));
            Integer remainingTimes = bucket.getTimesLimit() == null ? null
                    : Math.max(0, bucket.getTimesLimit() - usage.totals().times());
            Long remainingDays = bucket.getDaysLimit() == null ? null
                    : Math.max(0L, bucket.getDaysLimit() - usage.totals().days());
            String status = status(limit, used, member);
            rows.add(new MemberBenefitUsageDto.BucketUsage(bucket.getId(), bucket.getCode(), bucket.getNameAr(),
                    bucket.getParentBucket() == null ? null : bucket.getParentBucket().getId(),
                    bucket.getBenefitGroup() == null ? null : bucket.getBenefitGroup().getNameAr(),
                    period.start(), period.end(), limit, usage.claims().amount(), usage.adjustments().amount(),
                    used, remaining, percent, bucket.getTimesLimit(), usage.totals().times(), remainingTimes,
                    bucket.getDaysLimit(), usage.totals().days(), remainingDays, status));
        }
        return new MemberBenefitUsageDto(member.getId(), member.getFullName(), member.getCardNumber(),
                member.getStatus() == null ? "UNKNOWN" : member.getStatus().name(), policy.getId(),
                policy.getPolicyCode(), policy.getName(), date, rows);
    }

    private String status(BigDecimal limit, BigDecimal used, Member member) {
        if (member.getStatus() != Member.MemberStatus.ACTIVE) return "MEMBERSHIP_INELIGIBLE";
        if (limit == null) return "UNLIMITED";
        if (used.signum() <= 0) return "UNUSED";
        if (used.compareTo(limit) >= 0) return "EXHAUSTED";
        return "PARTIALLY_USED";
    }
}
