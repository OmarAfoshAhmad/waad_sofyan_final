package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Selects the effective source after structural applicability has been resolved. */
@Service
@RequiredArgsConstructor
public class EffectiveLimitResolver {
    private final ApplicableLimitResolver applicableLimitResolver;
    private final MemberRepository memberRepository;
    private final List<LimitSourceProvider> sourceProviders;
    private final com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;

    public record EffectiveLimit(
            ApplicableLimitResolver.ApplicableLimitDefinition definition,
            BigDecimal effectiveLimit,
            SourceType sourceType,
            Long sourceEmployerId,
            Long sourceMemberId) {}

    /**
     * The identity check here is deliberately against the policy that applied
     * to the member ON serviceDate, never against members.benefit_policy_id.
     *
     * This guard used to compare the caller's policyId with the member's
     * CURRENT pointer -- which meant that the moment callers started passing a
     * correctly date-resolved historical policy, every backdated claim would
     * be rejected with MEMBER_POLICY_MISMATCH. The guard was actively
     * preventing the fix it sat next to: it enforced "the member is on this
     * policy today", when the question is "was the member on this policy on
     * the service date".
     *
     * serviceDate is required for the same reason -- defaulting it to today
     * inside a limit calculation would reintroduce the same defect silently.
     */
    @Transactional(readOnly = true)
    public List<EffectiveLimit> resolve(Long policyId, Long benefitRuleId, Long memberId,
                                        LocalDate serviceDate, EncounterType encounterType) {
        if (policyId == null) throw new IllegalArgumentException("policyId is required");
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (serviceDate == null) {
            throw new IllegalArgumentException("serviceDate is required: a limit cannot be resolved "
                    + "without the date it applies to");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("MEMBER_NOT_FOUND: id=" + memberId));

        var policyOnServiceDate = memberPolicyResolver.resolveFor(member, serviceDate)
                .orElseThrow(() -> new IllegalStateException(
                        "MEMBER_POLICY_NOT_RESOLVED: member id=" + memberId
                                + " had no effective benefit policy on " + serviceDate));
        if (!policyId.equals(policyOnServiceDate.getId())) {
            throw new IllegalStateException("MEMBER_POLICY_MISMATCH: member id=" + memberId
                    + " was on policy id=" + policyOnServiceDate.getId() + " on " + serviceDate
                    + ", not policy id=" + policyId);
        }

        List<LimitSourceProvider> ordered = sourceProviders.stream()
                .sorted(Comparator.comparingInt(LimitSourceProvider::priority).reversed())
                .toList();
        List<EffectiveLimit> result = new ArrayList<>();
        for (var definition : applicableLimitResolver.resolve(
                policyId, benefitRuleId, serviceDate, encounterType)) {
            record Candidate(int priority, LimitSourceProvider.ResolvedSource source) {}
            List<Candidate> candidates = ordered.stream()
                    .map(provider -> provider.resolve(definition, memberId, serviceDate)
                            .map(source -> new Candidate(provider.priority(), source)))
                    .flatMap(java.util.Optional::stream).toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("LIMIT_SOURCE_NOT_FOUND: " + definition.semanticKey());
            }
            if (candidates.size() > 1 && candidates.get(0).priority() == candidates.get(1).priority()) {
                throw new IllegalStateException("AMBIGUOUS_LIMIT_SOURCE: " + definition.semanticKey()
                        + " has more than one source at priority " + candidates.get(0).priority());
            }
            var selected = candidates.get(0).source();
            if (selected.effectiveLimit() == null || selected.effectiveLimit().signum() < 0) {
                throw new IllegalStateException("INVALID_EFFECTIVE_LIMIT: " + definition.semanticKey());
            }
            result.add(new EffectiveLimit(definition, selected.effectiveLimit(), selected.sourceType(),
                    selected.sourceEmployerId(), selected.sourceMemberId()));
        }
        return List.copyOf(result);
    }
}
