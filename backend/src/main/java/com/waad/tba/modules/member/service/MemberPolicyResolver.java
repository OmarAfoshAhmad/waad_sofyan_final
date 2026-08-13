package com.waad.tba.modules.member.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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

    private final MemberPolicyAssignmentRepository assignmentRepository;
    private final BenefitPolicyRepository policyRepository;

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
            return policyRepository.findById(covering.get().getPolicyId());
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
     * Explicitly assign a policy to a member from a given date. Closes the
     * currently-open assignment at that same date (half-open ranges meet
     * exactly, so there is no uncovered day and no overlapping day), then
     * opens the new one and syncs the member's convenience pointer.
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

        MemberPolicyAssignment created = assignmentRepository.save(MemberPolicyAssignment.builder()
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

        // members.benefit_policy_id stays as a denormalized "current policy"
        // convenience pointer for list/detail screens. It is no longer the
        // source of truth for any dated decision -- resolveFor is.
        member.setBenefitPolicy(policy);

        log.info("[MemberPolicy] Assigned policy {} to member {} from {} (source={})",
                policy.getId(), member.getId(), from, created.getAssignmentSource());
        return created;
    }
}
