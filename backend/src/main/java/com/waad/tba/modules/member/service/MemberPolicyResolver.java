package com.waad.tba.modules.member.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
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
        if (member == null || member.getId() == null) {
            return Optional.empty();
        }
        LocalDate effectiveDate = serviceDate != null ? serviceDate : LocalDate.now();

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

            // The policy must belong to the member's employer. NOTE: this
            // compares against the member's CURRENT employer, because employer
            // history is not recorded anywhere -- there is no member_employer
            // assignment table the way there is now for policies. For a member
            // who has changed employer, a backdated question therefore checks
            // the wrong employer. That gap is real and deliberate to leave
            // visible rather than paper over with a guess; closing it needs the
            // same temporal treatment policies just received.
            Long memberEmployerId = member.getEmployer() != null ? member.getEmployer().getId() : null;
            Long policyEmployerId = resolved.getEmployer() != null ? resolved.getEmployer().getId() : null;
            if (memberEmployerId != null && policyEmployerId != null
                    && !memberEmployerId.equals(policyEmployerId)) {
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
        //     gap. V171 backfilled one row for every member holding a policy,
        //     so this should be unreachable; if it happens, some path created
        //     a member without recording an assignment. Falling back to the
        //     pointer keeps that member serviceable rather than silently
        //     denying real coverage, but it is logged at WARN with a distinct
        //     marker so the missing path is findable instead of invisible.
        boolean hasAnyAssignment = !assignmentRepository
                .findByMemberIdOrderByAssignmentStartDateDesc(member.getId()).isEmpty();
        if (hasAnyAssignment) {
            log.debug("[MemberPolicy] No assignment covers memberId={} on {}", member.getId(), effectiveDate);
            return Optional.empty();
        }

        if (member.getBenefitPolicy() != null) {
            log.warn("[MemberPolicy][MISSING_ASSIGNMENT] memberId={} has benefit_policy_id={} but no assignment row; "
                    + "falling back to the pointer for {}. Some creation path is not recording assignments.",
                    member.getId(), member.getBenefitPolicy().getId(), effectiveDate);
            return Optional.of(member.getBenefitPolicy());
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
        if (member == null || member.getId() == null) {
            return Optional.empty();
        }
        return assignmentRepository.findCovering(member.getId(),
                serviceDate != null ? serviceDate : LocalDate.now());
    }

    /**
     * Financial callers must use this, not resolveFor: a missing policy has to
     * stop the operation with a clear reason, never flow onward as a null that
     * some downstream branch reads as "no limit applies". Fail closed.
     */
    @Transactional(readOnly = true)
    public BenefitPolicy resolveForOrFail(Member member, LocalDate serviceDate) {
        return resolveFor(member, serviceDate).orElseThrow(() -> new BusinessRuleException(
                "لا توجد وثيقة منافع سارية للمستفيد "
                        + (member != null ? member.getFullName() : "")
                        + " بتاريخ الخدمة " + (serviceDate != null ? serviceDate : LocalDate.now())
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
        if (policy == null) {
            throw new BusinessRuleException("لا يمكن تعيين وثيقة غير موجودة");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException("سبب تعيين وثيقة المنافع إلزامي");
        }
        LocalDate from = effectiveFrom != null ? effectiveFrom : LocalDate.now();

        // Serialize concurrent assignments for this member: without the lock,
        // two requests both read "no open assignment" (or both read the same
        // one) and both insert an open-ended row. The exclusion constraint
        // would still catch it, but as a raw constraint violation rather than
        // an orderly wait.
        if (member.getId() != null) {
            memberRepository.findByIdWithLock(member.getId());
        }

        Optional<MemberPolicyAssignment> open =
                assignmentRepository.findByMemberIdAndAssignmentEndDateIsNull(member.getId());
        if (open.isPresent()) {
            MemberPolicyAssignment current = open.get();
            if (current.getPolicyId().equals(policy.getId())) {
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
                    .memberFullName(member.getFullName())
                    .memberCardNumber(member.getCardNumber())
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
        member.setBenefitPolicy(policy);

        log.info("[MemberPolicy] Assigned policy {} to member {} from {} (source={})",
                policy.getId(), member.getId(), from, created.getAssignmentSource());
        return created;
    }
}
