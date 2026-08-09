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

    public record EffectiveLimit(
            ApplicableLimitResolver.ApplicableLimitDefinition definition,
            BigDecimal effectiveLimit,
            SourceType sourceType,
            Long sourceEmployerId,
            Long sourceMemberId) {}

    @Transactional(readOnly = true)
    public List<EffectiveLimit> resolve(Long policyId, Long benefitRuleId, Long memberId,
                                        LocalDate serviceDate, EncounterType encounterType) {
        if (policyId == null) throw new IllegalArgumentException("policyId is required");
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("MEMBER_NOT_FOUND: id=" + memberId));
        if (member.getBenefitPolicy() == null || !policyId.equals(member.getBenefitPolicy().getId())) {
            throw new IllegalStateException("MEMBER_POLICY_MISMATCH: member id=" + memberId
                    + " is not assigned to policy id=" + policyId);
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
