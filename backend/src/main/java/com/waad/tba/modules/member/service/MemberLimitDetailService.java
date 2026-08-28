package com.waad.tba.modules.member.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.dto.MemberLimitDetail;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;

import lombok.RequiredArgsConstructor;

/**
 * The ceiling drawer: one member, the general ceiling and every bucket
 * underneath it.
 *
 * Opened on demand, so it reads one member rather than a page. It still
 * reaches the general figures through MemberLimitOverviewService, which is
 * what keeps the drawer and the column from disagreeing -- they are the same
 * read, not two implementations that happen to agree today.
 *
 * The buckets are read separately and are never added to the general ceiling.
 * One claim line can map to several buckets, so summing them would count the
 * same money once per category it fell into.
 */
@Service
@RequiredArgsConstructor
public class MemberLimitDetailService {

    private final MemberRepository memberRepository;
    private final MemberQueryAccessPolicy queryAccessPolicy;
    private final MemberLimitOverviewService limitOverviewService;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;

    @Transactional(readOnly = true)
    public MemberLimitDetail authorizedDetailFor(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        Long employerId = member.getEmployer() == null ? null : member.getEmployer().getId();
        queryAccessPolicy.requireMember(MemberOperation.VIEW_LIMITS, employerId);

        CurrentGeneralLimitSummary general = limitOverviewService
                .summariesFor(List.of(memberId))
                .get(memberId);

        List<MemberLimitDetail.BucketBalance> buckets = general == null || general.policyId() == null
                ? List.of()
                : bucketsFor(memberId, general.policyId());

        return new MemberLimitDetail(memberId,
                general == null ? LocalDate.now() : general.asOfDate(),
                general == null ? null : general.readAt(),
                general, buckets);
    }

    /**
     * Every bucket the member's policy defines, with this member's balance in
     * each. A bucket with no activity is still listed, at zero consumed --
     * omitting it would make "nothing spent here" look the same as "this
     * benefit does not exist for you".
     */
    private List<MemberLimitDetail.BucketBalance> bucketsFor(Long memberId, Long policyId) {
        List<BenefitLimitBucket> defined = bucketRepository.findByPolicyIdOrderByCode(policyId).stream()
                .filter(BenefitLimitBucket::isActive)
                // The mirror exists to make the general ceiling addressable as a
                // bucket internally. Listing it here would show the same money
                // twice, once as the ceiling and once as a bucket under it.
                .filter(bucket -> bucket.getLimitRole() != BenefitLimitBucket.LimitRole.POLICY_GENERAL_MIRROR)
                .toList();
        if (defined.isEmpty()) {
            return List.of();
        }

        List<Long> bucketIds = defined.stream().map(BenefitLimitBucket::getId).toList();
        Map<Long, BigDecimal> committedByBucket = new HashMap<>();
        Map<Long, BigDecimal> reservedByBucket = new HashMap<>();
        Map<Long, LocalDate[]> periodByBucket = new LinkedHashMap<>();

        for (var row : consumptionRepository.aggregateAmountBalances(memberId, bucketIds, null)) {
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            if ("COMMITTED".equals(row.getStatus())) {
                committedByBucket.merge(row.getBucketId(), amount, BigDecimal::add);
            } else if ("RESERVED".equals(row.getStatus())) {
                reservedByBucket.merge(row.getBucketId(), amount, BigDecimal::add);
            }
            periodByBucket.putIfAbsent(row.getBucketId(),
                    new LocalDate[] { row.getPeriodStart(), row.getPeriodEnd() });
        }

        List<MemberLimitDetail.BucketBalance> result = new ArrayList<>();
        for (BenefitLimitBucket bucket : defined) {
            BigDecimal committed = committedByBucket.getOrDefault(bucket.getId(), BigDecimal.ZERO);
            BigDecimal reserved = reservedByBucket.getOrDefault(bucket.getId(), BigDecimal.ZERO);
            BigDecimal limit = bucket.getAmountLimit();

            // A count-only bucket constrains occurrences and not money, so it
            // has no monetary balance to report. Null means "this ceiling does
            // not measure money", never "no money left".
            BigDecimal actualRemaining = limit == null ? null : limit.subtract(committed);
            BigDecimal reservableAvailable = actualRemaining == null
                    ? null : actualRemaining.subtract(reserved);

            LocalDate[] period = periodByBucket.get(bucket.getId());
            result.add(new MemberLimitDetail.BucketBalance(
                    bucket.getId(), bucket.getCode(), bucket.getNameAr(),
                    bucket.getPeriodType() == null ? null : bucket.getPeriodType().name(),
                    period == null ? null : period[0],
                    period == null ? null : period[1],
                    limit, committed, reserved, actualRemaining, reservableAvailable,
                    bucket.getTimesLimit()));
        }
        return result;
    }
}
