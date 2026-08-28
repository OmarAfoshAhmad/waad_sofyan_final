package com.waad.tba.modules.member.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single definition of "this member has a financial movement" -- extracted
 * from {@link MemberExcelImportService#clearOldMembers} so the import's own
 * cleanup and the rollback feature answer the exact same question the exact
 * same way, instead of two copies drifting apart.
 *
 * A member counts as having activity if THEY have a visit, claim, or
 * pre-authorization row (any status -- a rejected claim still proves the
 * member existed and was used, not a blank slate an import can undo), OR if
 * they are a principal whose dependent does. A principal is never touched
 * while any of their family still has a financial trace, even if the
 * principal's own row is otherwise untouched.
 *
 * The escalation checks EVERY real dependent of a candidate principal, not
 * only whichever dependents happen to also be in the candidate set -- a
 * principal created by one import batch can have a dependent added by a
 * completely different action, and that dependent's activity must still
 * protect the principal.
 */
@Component
@RequiredArgsConstructor
public class MemberFinancialActivityChecker {

    private final VisitRepository visitRepository;
    private final ClaimRepository claimRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @param memberIds the members to check
     * @return the subset of {@code memberIds} that must be KEPT -- either they
     *         have their own movement, or they are the principal of a
     *         dependent (any dependent, not just one in {@code memberIds})
     *         who does
     */
    @Transactional(readOnly = true)
    public Set<Long> membersToKeep(Collection<Long> memberIds) {
        Set<Long> candidateIds = new HashSet<>(memberIds);
        if (candidateIds.isEmpty()) return Set.of();

        List<Member> candidates = memberRepository.findAllById(candidateIds);
        List<Long> principalIds = candidates.stream()
                .filter(m -> m.getParent() == null)
                .map(Member::getId)
                .toList();

        // Every real dependent of a candidate principal, not just the ones
        // also present in candidateIds.
        List<Member> allDependentsOfCandidatePrincipals = principalIds.isEmpty()
                ? List.of() : memberRepository.findByParentIdIn(principalIds);

        Set<Long> idsToCheck = new HashSet<>(candidateIds);
        Map<Long, Long> dependentIdToParentId = new HashMap<>();
        for (Member dependent : allDependentsOfCandidatePrincipals) {
            idsToCheck.add(dependent.getId());
            dependentIdToParentId.put(dependent.getId(), dependent.getParent().getId());
        }

        Set<Long> idsWithMovements = new HashSet<>();
        idsWithMovements.addAll(visitRepository.findMemberIdsWithVisits(idsToCheck));
        idsWithMovements.addAll(claimRepository.findMemberIdsWithClaims(idsToCheck));
        idsWithMovements.addAll(preAuthorizationRepository.findMemberIdsWithPreAuths(idsToCheck));
        idsWithMovements.addAll(findIds("eligibility_checks", idsToCheck));
        idsWithMovements.addAll(findIds("benefit_bucket_consumptions", idsToCheck));

        Set<Long> keepIds = new HashSet<>();
        for (Long id : idsWithMovements) {
            if (candidateIds.contains(id)) {
                keepIds.add(id);
            }
            Long parentId = dependentIdToParentId.get(id);
            if (parentId != null && candidateIds.contains(parentId)) {
                keepIds.add(parentId);
            }
        }
        return keepIds;
    }

    private Set<Long> findIds(String table, Set<Long> memberIds) {
        if (memberIds.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(memberIds.size(), "?"));
        return new HashSet<>(jdbcTemplate.queryForList(
                "select distinct member_id from " + table + " where member_id in (" + placeholders + ")",
                Long.class, memberIds.toArray()));
    }
}
