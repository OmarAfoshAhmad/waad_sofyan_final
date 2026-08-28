package com.waad.tba.modules.member.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.PolicyInForceRow;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The single place "which benefit policy applied to this member on this
 * date" is answered.
 *
 * Before this existed, the two consumers disagreed:
 *   - EligibilityEngineServiceImpl read member.getBenefitPolicy() and never
 *     looked at the request's serviceDate at all.
 *   - BenefitPolicyCoverageService read the same pointer, only consulting
 *     serviceDate when the pointer was null -- and then silently persisted
 *     that result back onto the member, so a read path rewrote state.
 * Both therefore evaluated a backdated decision against TODAY's policy.
 *
 * resolveFor() is PURE: it never writes. Changing which policy a member is
 * on is an explicit, audited operation (assignPolicy), never a side effect
 * of reading.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberPolicyResolver {

    private static final String OVERLAP_CONSTRAINT = "uk_member_policy_assignment_no_overlap";

    private final MemberPolicyAssignmentRepository assignmentRepository;
    private final BenefitPolicyRepository policyRepository;
    private final com.waad.tba.modules.member.repository.MemberRepository memberRepository;
    private final MemberEmployerResolver memberEmployerResolver;
    private final com.waad.tba.modules.member.repository.MemberEmployerAssignmentRepository
            employerAssignmentRepository;

    /**
     * Walks the cause chain looking for the constraint name. Postgres reports
     * it in the message; matching by name is what keeps this translation
     * specific instead of swallowing every integrity failure.
     */
    private static boolean mentionsConstraint(Throwable error, String constraintName) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(constraintName)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * The policy in force for this member on this date, or empty if none can
     * be established. Never falls back to "today's policy" for a past date --
     * that silent substitution is the defect this class exists to remove.
     */
    @Transactional(readOnly = true)
    public Optional<BenefitPolicy> resolveFor(Member member, LocalDate serviceDate) {
        requireServiceDate(serviceDate);
        if (member == null || member.getId() == null) {
            return Optional.empty();
        }
        LocalDate effectiveDate = serviceDate;

        Optional<MemberPolicyAssignment> covering = assignmentRepository.findCovering(member.getId(), effectiveDate);
        if (covering.isPresent()) {
            Optional<BenefitPolicy> policy = policyRepository.findById(covering.get().getPolicyId());
            if (policy.isEmpty()) {
                return Optional.empty();
            }
            BenefitPolicy resolved = policy.get();

            // An assignment covering the date is not sufficient: the POLICY
            // must itself have been in force then. An assignment left open --
            // which is the normal state, since assignments are only closed when
            // a new one starts -- would otherwise keep answering with a policy
            // that expired years ago, silently extending coverage nobody
            // granted.
            if (!resolved.isEffectiveOn(effectiveDate)) {
                log.debug("[MemberPolicy] Assignment covers memberId={} on {} but policy {} was not in force "
                        + "(status={}, {}..{})", member.getId(), effectiveDate, resolved.getId(),
                        resolved.getStatus(), resolved.getStartDate(), resolved.getEndDate());
                return Optional.empty();
            }

            // Validate against the employer that owned the member ON THE SAME
            // DATE. Comparing with members.employer_id would reject valid
            // backdated claims after an employer transfer, or accept a policy
            // from the wrong historical employer.
            Long memberEmployerId = memberEmployerResolver.resolveFor(member, effectiveDate)
                    .map(e -> e.getId()).orElse(null);
            Long policyEmployerId = resolved.getEmployer() != null ? resolved.getEmployer().getId() : null;
            if (memberEmployerId == null || policyEmployerId == null
                    || !memberEmployerId.equals(policyEmployerId)) {
                log.warn("[MemberPolicy][EMPLOYER_MISMATCH] memberId={} employer={} but assigned policy {} belongs "
                        + "to employer {} on {}", member.getId(), memberEmployerId, resolved.getId(),
                        policyEmployerId, effectiveDate);
                return Optional.empty();
            }

            return Optional.of(resolved);
        }

        // Distinguish two very different "no match" cases.
        //
        // (a) The member has assignment rows, but none covers this date: a
        //     real answer -- they had no policy then. Returning today's policy
        //     instead would fabricate coverage that never existed, which is
        //     exactly the defect this class removes. Fail closed.
        //
        // (b) The member has NO assignment rows at all: not an answer, a data
        //     integrity gap. V171 backfilled existing rows and every live
        //     creation path must record an assignment. The convenience pointer
        //     is deliberately NOT a fallback: using it would grant coverage
        //     without knowing when that policy started applying.
        boolean hasAnyAssignment = !assignmentRepository
                .findByMemberIdOrderByAssignmentStartDateDesc(member.getId()).isEmpty();
        if (hasAnyAssignment) {
            log.debug("[MemberPolicy] No assignment covers memberId={} on {}", member.getId(), effectiveDate);
            return Optional.empty();
        }

        if (member.getBenefitPolicy() != null) {
            log.error("[MemberPolicy][MISSING_ASSIGNMENT] memberId={} has benefit_policy_id={} but no assignment "
                    + "covering {}. Refusing the dated decision; the pointer is display-only.",
                    member.getId(), member.getBenefitPolicy().getId(), effectiveDate);
        }
        return Optional.empty();
    }

    /**
     * The assignment row itself, for callers that must record WHICH coverage
     * period a decision was made under -- policyId alone cannot distinguish
     * two separate assignment periods that use the same logical policy.
     */
    @Transactional(readOnly = true)
    public Optional<MemberPolicyAssignment> resolveAssignmentFor(Member member, LocalDate serviceDate) {
        requireServiceDate(serviceDate);
        if (member == null || member.getId() == null) {
            return Optional.empty();
        }
        return assignmentRepository.findCovering(member.getId(), serviceDate);
    }

    /**
     * Financial callers must use this, not resolveFor: a missing policy has to
     * stop the operation with a clear reason, never flow onward as a null that
     * some downstream branch reads as "no limit applies". Fail closed.
     */
    /**
     * The dated policy for a whole set of members, in one query.
     *
     * Exists because the members list needs this for every row it renders, and
     * resolveAssignmentFor once per row makes the cost of a page a function of
     * its size. The list is the only reason this is bulk; the rule it applies
     * is identical to the single-member path.
     *
     * Every requested id appears in the result. A member the query returned
     * nothing for is NOT_ASSIGNED, not absent -- a caller iterating the map
     * must not be able to skip someone by accident, and a missing key is far
     * easier to overlook than a stated outcome.
     *
     * Two covering assignments produce AMBIGUOUS rather than a choice. V171's
     * exclusion constraint should make that impossible; if it ever happens,
     * deciding which policy priced someone's care by iteration order is worse
     * than admitting we do not know.
     *
     * @param asOfDate mandatory, and never defaulted to today: the whole point
     *                 is to answer for a stated date, and quietly substituting
     *                 the current one prices a past or future question with
     *                 today's configuration
     */
    @Transactional(readOnly = true)
    public Map<Long, ResolvedMemberPolicy> resolveForMembers(
            java.util.Collection<Long> memberIds, LocalDate asOfDate) {
        requireServiceDate(asOfDate);

        Map<Long, ResolvedMemberPolicy> result = new java.util.LinkedHashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return result;
        }
        java.util.Set<Long> requested = new java.util.LinkedHashSet<>(memberIds);
        requested.remove(null);
        if (requested.isEmpty()) {
            return result;
        }

        java.util.Map<Long, java.util.List<MemberPolicyAssignment>> covering = new java.util.HashMap<>();
        try {
            for (MemberPolicyAssignment assignment
                    : assignmentRepository.findCoveringForMembers(requested, asOfDate)) {
                covering.computeIfAbsent(assignment.getMemberId(), ignored -> new java.util.ArrayList<>())
                        .add(assignment);
            }
        } catch (RuntimeException ex) {
            // One failed read must not become a page of zeroes. Every member
            // is reported as unavailable so the screen can say so.
            log.error("Bulk policy resolution failed for {} members asOf {}", requested.size(), asOfDate, ex);
            for (Long memberId : requested) {
                result.put(memberId, ResolvedMemberPolicy.unavailable("تعذّرت قراءة تعيينات الوثائق"));
            }
            return result;
        }

        // The same two checks resolveFor applies after finding an assignment,
        // in bulk. Leaving them out here would have made the list a laxer
        // authority than the single-member path it must agree with: an
        // assignment left open -- the normal state -- would keep answering
        // with a policy that expired years ago, and a policy belonging to an
        // employer the member has since left would price their care.
        java.util.Set<Long> policyIds = new java.util.LinkedHashSet<>();
        for (var matches : covering.values()) {
            if (matches.size() == 1) {
                policyIds.add(matches.get(0).getPolicyId());
            }
        }
        java.util.Map<Long, PolicyInForceRow> policyRows = new java.util.HashMap<>();
        java.util.Map<Long, Long> employerByMember = new java.util.HashMap<>();
        try {
            if (!policyIds.isEmpty()) {
                for (PolicyInForceRow row : policyRepository.findInForceRows(policyIds)) {
                    policyRows.put(row.policyId(), row);
                }
            }
            for (var assignment : employerAssignmentRepository
                    .findCoveringForMembers(requested, asOfDate)) {
                // A second covering employer assignment is impossible under
                // V183's exclusion constraint; if one ever appears, the
                // mismatch branch below refuses rather than picks.
                employerByMember.merge(assignment.getMemberId(), assignment.getEmployerId(),
                        (first, second) -> first.equals(second) ? first : null);
            }
        } catch (RuntimeException ex) {
            log.error("Bulk policy validation failed for {} members asOf {}", requested.size(), asOfDate, ex);
            for (Long memberId : requested) {
                result.put(memberId, ResolvedMemberPolicy.unavailable("تعذّرت قراءة تعيينات الوثائق"));
            }
            return result;
        }

        for (Long memberId : requested) {
            java.util.List<MemberPolicyAssignment> matches =
                    covering.getOrDefault(memberId, java.util.List.of());
            if (matches.isEmpty()) {
                result.put(memberId, ResolvedMemberPolicy.notAssigned());
                continue;
            }
            if (matches.size() > 1) {
                result.put(memberId, ResolvedMemberPolicy.ambiguous(
                        "أكثر من تعيين وثيقة يغطي التاريخ " + asOfDate));
                continue;
            }
            MemberPolicyAssignment assignment = matches.get(0);
            PolicyInForceRow policy = policyRows.get(assignment.getPolicyId());
            if (policy == null) {
                // The assignment names a policy that no longer exists. Unknown,
                // not "no coverage": something deleted a row a decision points at.
                result.put(memberId, ResolvedMemberPolicy.unavailable(
                        "الوثيقة المعيّنة غير موجودة"));
                continue;
            }
            if (!policy.isInForceOn(asOfDate)) {
                result.put(memberId, ResolvedMemberPolicy.policyNotInForce(
                        "الوثيقة غير سارية بتاريخ " + asOfDate));
                continue;
            }
            Long memberEmployerId = employerByMember.get(memberId);
            if (memberEmployerId == null || !memberEmployerId.equals(policy.employerId())) {
                log.warn("[MemberPolicy][EMPLOYER_MISMATCH] memberId={} employer={} but assigned policy {} "
                        + "belongs to employer {} on {}", memberId, memberEmployerId,
                        policy.policyId(), policy.employerId(), asOfDate);
                result.put(memberId, ResolvedMemberPolicy.employerMismatch(
                        "الوثيقة المعيّنة تتبع جهة عمل أخرى"));
                continue;
            }
            result.put(memberId,
                    ResolvedMemberPolicy.found(assignment.getPolicyId(), assignment.getId()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BenefitPolicy resolveForOrFail(Member member, LocalDate serviceDate) {
        requireServiceDate(serviceDate);
        return resolveFor(member, serviceDate).orElseThrow(() -> new BusinessRuleException(
                "لا توجد وثيقة منافع سارية للمستفيد "
                        + (member != null ? member.getFullName() : "")
                        + " بتاريخ الخدمة " + serviceDate
                        + ". لا يمكن اتخاذ قرار مالي بدون وثيقة سارية في ذلك التاريخ."));
    }

    /**
     * Explicitly assign a policy to a member from a given date. Closes the
     * currently-open assignment at that same date (half-open ranges meet
     * exactly, so there is no uncovered day and no overlapping day), then
     * opens the new one and syncs the member's convenience pointer.
     *
     * Takes a row lock on the member first, so two concurrent assignments for
     * the same member serialize instead of both reading "the current open
     * assignment" and each closing it. The gist exclusion constraint remains
     * the last line of defence underneath -- the lock makes the common case a
     * clean wait rather than a constraint violation, it does not replace the
     * constraint.
     *
     * REQUIRES_NEW is deliberately NOT used: an assignment must commit or
     * roll back together with whatever business operation requested it.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public MemberPolicyAssignment assignPolicy(Member member, BenefitPolicy policy, LocalDate effectiveFrom,
            String reason, PolicyAssignmentSource source, Long actingUserId) {
        if (member == null || member.getId() == null) {
            throw new BusinessRuleException("يجب حفظ المستفيد قبل تعيين وثيقة المنافع");
        }
        if (policy == null) {
            throw new BusinessRuleException("لا يمكن تعيين وثيقة غير موجودة");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException("سبب تعيين وثيقة المنافع إلزامي");
        }
        if (effectiveFrom == null) {
            throw new BusinessRuleException("تاريخ سريان تعيين وثيقة المنافع إلزامي");
        }
        LocalDate from = effectiveFrom;

        // Serialize concurrent assignments for this member: without the lock,
        // two requests both read "no open assignment" (or both read the same
        // one) and both insert an open-ended row. The exclusion constraint
        // would still catch it, but as a raw constraint violation rather than
        // an orderly wait.
        Member lockedMember = memberRepository.findByIdWithLock(member.getId())
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود"));

        Optional<MemberPolicyAssignment> open =
                assignmentRepository.findByMemberIdAndAssignmentEndDateIsNull(member.getId());
        if (open.isPresent()) {
            MemberPolicyAssignment current = open.get();
            if (current.getPolicyId().equals(policy.getId())) {
                lockedMember.setBenefitPolicy(policy);
                memberRepository.saveAndFlush(lockedMember);
                synchronizeCaller(member, lockedMember);
                return current;
            }
            if (!from.isAfter(current.getAssignmentStartDate())) {
                throw new BusinessRuleException(
                        "تاريخ سريان التعيين الجديد يجب أن يكون بعد بداية التعيين الحالي ("
                                + current.getAssignmentStartDate() + ")");
            }
            current.setAssignmentEndDate(from);
            // saveAndFlush, not save: Hibernate orders INSERTs before UPDATEs
            // within a flush, so without forcing the close to hit the database
            // first, the new open-ended row is inserted while the old one is
            // still open -- two assignments covering the same day, which the
            // exclusion constraint (correctly) rejects.
            assignmentRepository.saveAndFlush(current);
        }

        MemberPolicyAssignment created;
        try {
            created = assignmentRepository.saveAndFlush(MemberPolicyAssignment.builder()
                    .memberId(member.getId())
                    .policyId(policy.getId())
                    .assignmentStartDate(from)
                    .assignmentEndDate(null)
                    .assignmentReason(reason.trim())
                    .assignmentSource(source != null ? source : PolicyAssignmentSource.MANUAL)
                    .assignedBy(actingUserId)
                    .memberFullName(lockedMember.getFullName())
                    .memberCardNumber(lockedMember.getCardNumber())
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Translate ONLY the overlap constraint, matched by name. Treating
            // every DataIntegrityViolationException as an overlap would put a
            // confident, wrong Arabic sentence on top of an unrelated failure
            // (a not-null violation, a bad FK) and hide the real cause.
            if (mentionsConstraint(e, OVERLAP_CONSTRAINT)) {
                throw new BusinessRuleException(
                        "يوجد تعيين وثيقة متداخل للمستفيد في تاريخ السريان المحدد (" + from + ")");
            }
            throw e;
        }

        // members.benefit_policy_id stays as a denormalized "current policy"
        // convenience pointer for list/detail screens. It is no longer the
        // source of truth for any dated decision -- resolveFor is.
        lockedMember.setBenefitPolicy(policy);
        memberRepository.saveAndFlush(lockedMember);
        synchronizeCaller(member, lockedMember);

        log.info("[MemberPolicy] Assigned policy {} to member {} from {} (source={})",
                policy.getId(), member.getId(), from, created.getAssignmentSource());
        return created;
    }

    private static void synchronizeCaller(Member caller, Member persisted) {
        if (caller != persisted) {
            caller.setBenefitPolicy(persisted.getBenefitPolicy());
            caller.setVersion(persisted.getVersion());
        }
    }

    /** See MemberEmployerResolver.restoreCurrentPointerAfterImport. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreCurrentPointerAfterImport(Member member, BenefitPolicy policy) {
        member.setBenefitPolicy(policy);
        memberRepository.save(member);
    }

    private static void requireServiceDate(LocalDate serviceDate) {
        if (serviceDate == null) {
            throw new BusinessRuleException(
                    "تاريخ الخدمة إلزامي لحل وثيقة المنافع، ولا يجوز استبداله بتاريخ اليوم");
        }
    }
}
