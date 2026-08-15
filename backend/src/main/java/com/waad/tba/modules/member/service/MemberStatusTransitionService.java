package com.waad.tba.modules.member.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberHardDeleteAudit;
import com.waad.tba.modules.member.entity.MemberStatusHistory;
import com.waad.tba.modules.member.entity.StatusSource;
import com.waad.tba.modules.member.repository.MemberHardDeleteAuditRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.repository.MemberStatusHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The single place a Member's status/active pair is ever written.
 *
 * `status` is the source of truth; `active` is always derived from it
 * (activeFor) -- this used to be two independently-writable fields
 * (toggleActive touched only `active`, changeStatus touched both but with
 * its own separate logic, the Excel import row processor set both directly
 * again), and that divergence is exactly what let a member end up
 * status=SUSPENDED with active=true. chk_member_status_active_consistency
 * (V169) is the DB-level backstop; this service is the single application
 * path that keeps the invariant in the first place.
 *
 * Every real transition (not the in-memory-only helper used by the Excel
 * import row builder, which doesn't persist itself) writes an append-only
 * MemberStatusHistory row in the SAME transaction as the Member update --
 * if the transition doesn't commit, its history entry doesn't exist either.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberStatusTransitionService {

    private final MemberRepository memberRepository;
    private final MemberStatusHistoryRepository historyRepository;
    private final MemberHardDeleteAuditRepository hardDeleteAuditRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MemberPolicyResolver memberPolicyResolver;

    public static boolean activeFor(Member.MemberStatus status) {
        return status == Member.MemberStatus.ACTIVE;
    }

    /**
     * Sets status/active/tracking fields on an in-memory Member WITHOUT
     * persisting or writing history -- for the Excel import row processor
     * only, which builds a Member object the caller batches and saves
     * itself (memberRepository.saveAll in large batches, tuned for import
     * performance earlier in this effort). This is still "going through the
     * transition service": the status->active derivation and the tracking
     * fields are computed in exactly one place, just not flushed here.
     */
    public void initializeStatus(Member member, Member.MemberStatus newStatus, StatusSource source, String reason) {
        requireReason(reason, "سبب تهيئة حالة المستفيد إلزامي");
        Member.MemberStatus previous = member.getStatus();
        member.setPreviousStatus(previous);
        member.setStatus(newStatus);
        member.setActive(activeFor(newStatus));
        member.setStatusSource(source);
        member.setStatusReason(reason.trim());
        member.setStatusChangedAt(LocalDateTime.now());
        member.setStatusTransitionId(UUID.randomUUID().toString());
    }

    public void applyStatusFieldsForImport(Member member, Member.MemberStatus newStatus, String reason) {
        initializeStatus(member, newStatus, StatusSource.IMPORT, reason);
    }

    /**
     * The core primitive every real (persisted) transition goes through.
     * saveAndFlush deliberately, not save: forces the @Version optimistic
     * check to fire HERE, inside this method, rather than being deferred to
     * an outer transaction's commit where a caller further up the stack
     * (e.g. a family cascade loop) might not expect it.
     */
    @Transactional
    public Member transitionTo(Member member, Member.MemberStatus newStatus, String reason, StatusSource source,
            String transitionId, Long actingUserId) {
        requireReason(reason, "سبب تغيير حالة المستفيد إلزامي");
        Member.MemberStatus previous = member.getStatus();
        if (previous == Member.MemberStatus.DUPLICATE_MERGED) {
            throw new BusinessRuleException(
                    "لا يمكن تغيير حالة سجل مدموج؛ استخدم السجل الأساسي المرتبط به");
        }
        if (previous == newStatus) {
            throw new BusinessRuleException("العضو بالفعل في هذه الحالة: " + newStatus);
        }
        if (newStatus == Member.MemberStatus.ACTIVE) {
            ensureBenefitPolicyForActivation(member);
        }

        member.setPreviousStatus(previous);
        member.setStatus(newStatus);
        member.setActive(activeFor(newStatus));
        member.setStatusSource(source);
        member.setStatusReason(reason.trim());
        member.setStatusChangedAt(LocalDateTime.now());
        member.setStatusChangedBy(actingUserId);
        member.setStatusTransitionId(transitionId);

        Member saved = memberRepository.saveAndFlush(member);

        historyRepository.save(MemberStatusHistory.builder()
                .memberId(saved.getId())
                .memberFullName(saved.getFullName())
                .memberCardNumber(saved.getCardNumber())
                .fromStatus(previous)
                .toStatus(newStatus)
                .reason(reason.trim())
                .source(source)
                .transitionId(transitionId)
                .changedAt(LocalDateTime.now())
                .changedBy(actingUserId)
                .build());

        return saved;
    }

    @Transactional
    public Member transitionTo(Long memberId, Member.MemberStatus newStatus, String reason, StatusSource source,
            String transitionId, Long actingUserId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        return transitionTo(member, newStatus, reason, source, transitionId, actingUserId);
    }

    /**
     * Suspend (temporary, reversible). Cascades to the principal's currently
     * ACTIVE dependents only -- a dependent already SUSPENDED/TERMINATED for
     * their own reason keeps that history untouched, per the explicit "no
     * silent overwrite of an independent status" rule.
     */
    @Transactional
    public Member suspend(Long memberId, String reason, Long actingUserId) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException("سبب الإيقاف مطلوب عند تعليق المستفيد");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        String transitionId = UUID.randomUUID().toString();
        Member saved = transitionTo(member, Member.MemberStatus.SUSPENDED, reason, StatusSource.MANUAL,
                transitionId, actingUserId);
        cascadeToActiveDependents(saved, Member.MemberStatus.SUSPENDED, reason, transitionId, actingUserId, "إيقاف");
        return saved;
    }

    /**
     * End coverage without deleting history. Financial/medical history is
     * deliberately allowed and remains attached to the terminated member.
     * Only physical hardDelete applies the footprint guard.
     */
    @Transactional
    public Member terminateMembership(Long memberId, String reason, Long actingUserId, StatusSource source) {
        requireReason(reason, "سبب إنهاء العضوية إلزامي");
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        String transitionId = UUID.randomUUID().toString();
        Member saved = transitionTo(member, Member.MemberStatus.TERMINATED, reason, source, transitionId, actingUserId);
        cascadeToActiveDependents(saved, Member.MemberStatus.TERMINATED, reason, transitionId, actingUserId, "إنهاء");
        return saved;
    }

    /** SUSPENDED (or PENDING) -> ACTIVE. Ordinary operational action, no elevated permission. */
    @Transactional
    public Member restoreFromSuspended(Long memberId, String reason, Long actingUserId) {
        requireReason(reason, "سبب استعادة المستفيد إلزامي");
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        if (member.getStatus() == Member.MemberStatus.TERMINATED) {
            throw new BusinessRuleException(
                    "العضو منتهي العضوية -- استخدم إعادة العضوية المنتهية (صلاحية خاصة)، لا الاستعادة العادية");
        }
        if (member.getStatus() == Member.MemberStatus.ACTIVE) {
            throw new BusinessRuleException("العضو نشط بالفعل");
        }
        return transitionTo(member, Member.MemberStatus.ACTIVE, reason, StatusSource.MANUAL,
                UUID.randomUUID().toString(), actingUserId);
    }

    /**
     * TERMINATED -> ACTIVE. Exceptional action: caller must already have
     * verified elevated permission (isSuperAdmin) before calling -- this
     * method re-checks it too (defense in depth, not just a controller
     * @PreAuthorize) and requires a reason. Re-validates the employer's
     * benefit policy is currently active before reinstating; does NOT touch
     * any claim/visit/preauth history -- reinstating membership is not the
     * same as reversing whatever happened while the member was terminated.
     */
    @Transactional
    public Member reinstateTerminated(Long memberId, String reason, Long actingUserId, boolean callerIsSuperAdmin) {
        if (!callerIsSuperAdmin) {
            throw new AccessDeniedException("إعادة عضوية منتهية تتطلب صلاحية مدير النظام");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException("سبب إعادة العضوية المنتهية إلزامي");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        if (member.getStatus() != Member.MemberStatus.TERMINATED) {
            throw new BusinessRuleException("العضو ليس في حالة إنهاء عضوية");
        }
        return transitionTo(member, Member.MemberStatus.ACTIVE, reason, StatusSource.MANUAL,
                UUID.randomUUID().toString(), actingUserId);
    }

    /**
     * Restores exactly the dependents ONE specific cascade (transitionId)
     * affected -- never every dependent currently sharing the cascaded
     * status, and never a dependent whose status has changed again since
     * (their statusTransitionId would no longer equal transitionId). Skips
     * (does not fail the whole call for) a dependent whose employer/policy
     * can no longer support activation -- that's reported back, not thrown.
     */
    @Transactional
    public FamilyRestoreResult restoreFamily(String transitionId, Long actingUserId) {
        List<MemberStatusHistory> cascaded = historyRepository.findByTransitionId(transitionId).stream()
                .filter(h -> h.getSource() == StatusSource.FAMILY_CASCADE)
                .toList();

        List<Long> restored = new ArrayList<>();
        Map<Long, String> skipped = new LinkedHashMap<>();

        for (MemberStatusHistory h : cascaded) {
            Member dependent = memberRepository.findById(h.getMemberId()).orElse(null);
            if (dependent == null) {
                continue;
            }
            if (!transitionId.equals(dependent.getStatusTransitionId())) {
                skipped.put(dependent.getId(), "تغيّرت حالته بعملية مستقلة بعد التتالي");
                continue;
            }
            if (dependent.getStatus() == Member.MemberStatus.ACTIVE) {
                continue;
            }
            try {
                restored.add(transitionTo(dependent, Member.MemberStatus.ACTIVE,
                        "استعادة عائلية بعد استعادة الموظف الرئيسي", StatusSource.MANUAL,
                        UUID.randomUUID().toString(), actingUserId).getId());
            } catch (BusinessRuleException e) {
                skipped.put(dependent.getId(), e.getMessage());
            }
        }

        return new FamilyRestoreResult(restored, skipped);
    }

    /**
     * Resolves the current employers of every still-existing member affected
     * by one family cascade. The command layer uses this before restore so a
     * bulk operation is authorised as all-or-nothing, never member by member
     * after writes have already started.
     */
    @Transactional(readOnly = true)
    public java.util.List<Long> familyCascadeEmployerIds(String transitionId) {
        if (transitionId == null || transitionId.isBlank()) {
            throw new BusinessRuleException("معرف عملية التتالي إلزامي");
        }
        return historyRepository.findByTransitionId(transitionId).stream()
                .filter(h -> h.getSource() == StatusSource.FAMILY_CASCADE)
                .map(MemberStatusHistory::getMemberId)
                .distinct()
                .map(memberRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(Member::getEmployer)
                .filter(java.util.Objects::nonNull)
                .map(com.waad.tba.modules.employer.entity.Employer::getId)
                .toList();
    }

    public record FamilyRestoreResult(List<Long> restoredMemberIds, Map<Long, String> skippedWithReason) {
    }

    /**
     * Physical delete. Blocked entirely if any financial/medical/audit
     * footprint exists. Writes an independent (non-FK'd) audit row BEFORE
     * deleting, in the SAME transaction -- if the delete is blocked or fails,
     * the audit row never commits either; this is a record of an ACTUAL
     * deletion, not an attempt.
     */
    @Transactional
    public void hardDelete(Long memberId, String reason, Long actingUserId, String actingUsername,
            boolean callerIsSuperAdmin) {
        if (!callerIsSuperAdmin) {
            throw new AccessDeniedException("الحذف النهائي يتطلب صلاحية مدير النظام");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException("سبب الحذف النهائي إلزامي");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        List<Long> allIds = new ArrayList<>();
        allIds.add(memberId);
        boolean wasPrincipal = member.isPrincipal();
        if (wasPrincipal) {
            memberRepository.findByParentId(memberId).forEach(d -> allIds.add(d.getId()));
        }
        assertNoFinancialOrAuditFootprint(allIds, "لا يمكن حذف المستفيد نهائياً");

        hardDeleteAuditRepository.save(MemberHardDeleteAudit.builder()
                .memberId(memberId)
                .memberFullName(member.getFullName())
                .memberCardNumber(member.getCardNumber())
                .employerId(member.getEmployer() != null ? member.getEmployer().getId() : null)
                .wasPrincipal(wasPrincipal)
                .reason(reason)
                .performedBy(actingUserId)
                .performedByUsername(actingUsername)
                .performedAt(LocalDateTime.now())
                .build());

        String idList = allIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        // member_policy_assignments is deliberately NOT deleted here: like
        // member_status_history (V170), it is an append-only record that must
        // survive the member row it describes -- it carries its own identity
        // snapshot and has no member FK, so leaving it does not block the
        // delete. Deleting it would also fail outright against V171's
        // append-only trigger.
        jdbcTemplate.update("DELETE FROM member_deductibles WHERE member_id IN (" + idList + ")");

        if (wasPrincipal) {
            memberRepository.deleteByParentId(memberId);
        }
        memberRepository.delete(member);
        memberRepository.flush();
    }

    private void cascadeToActiveDependents(Member principal, Member.MemberStatus newStatus, String principalReason,
            String transitionId, Long actingUserId, String actionAr) {
        if (!principal.isPrincipal()) {
            return;
        }
        List<Member> dependents = memberRepository.findByParentId(principal.getId());
        for (Member dependent : dependents) {
            if (dependent.getStatus() != Member.MemberStatus.ACTIVE) {
                // Not currently "working" -- already suspended/terminated/pending for
                // their own, independent reason. Cascading here would silently
                // overwrite that history, which is exactly what's forbidden.
                continue;
            }
            String cascadeReason = "تتالٍ تلقائي بسبب " + actionAr + " الموظف الرئيسي: " + principalReason;
            transitionTo(dependent, newStatus, cascadeReason, StatusSource.FAMILY_CASCADE, transitionId, actingUserId);
        }
    }

    private void assertNoFinancialOrAuditFootprint(List<Long> memberIds, String messagePrefix) {
        String idList = memberIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        long claimsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM claims WHERE member_id IN (" + idList + ")", Long.class);
        // pre_authorizations, not preauthorization_requests. The latter is a
        // dead model with no entity and no writer, so counting it always
        // returned zero -- and this guard would have let a member with real
        // approvals be hard-deleted, taking their approval history with them.
        long preAuthCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pre_authorizations WHERE member_id IN (" + idList + ")", Long.class);
        long visitsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE member_id IN (" + idList + ")", Long.class);
        long eligibilityChecksCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eligibility_checks WHERE member_id IN (" + idList + ")", Long.class);
        long bucketConsumptionsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE member_id IN (" + idList + ")", Long.class);
        if (claimsCount > 0 || preAuthCount > 0 || visitsCount > 0
                || eligibilityChecksCount > 0 || bucketConsumptionsCount > 0) {
            throw new BusinessRuleException(messagePrefix + String.format(
                    " لوجود أثر مالي أو طبي أو تدقيقي مرتبط به (مطالبات: %d، موافقات مسبقة: %d، زيارات: %d، "
                            + "فحوص أهلية: %d، استهلاك سقوف: %d). استخدم الإيقاف/الإنهاء للحفاظ على سلامة السجل.",
                    claimsCount, preAuthCount, visitsCount, eligibilityChecksCount, bucketConsumptionsCount));
        }
    }

    /**
     * chk_active_member_requires_policy forbids saving an active member
     * with no benefit policy. Try to auto-assign the employer's current
     * active policy before failing with a clear message.
     */
    private void ensureBenefitPolicyForActivation(Member member) {
        if (member.getBenefitPolicy() != null && member.getBenefitPolicy().isEffective()) {
            return;
        }
        Long employerId = member.getEmployer() != null ? member.getEmployer().getId() : null;
        BenefitPolicy autoPolicy = employerId != null
                ? benefitPolicyRepository.findActiveEffectivePolicyForEmployer(employerId, java.time.LocalDate.now()).orElse(null)
                : null;
        if (autoPolicy == null) {
            throw new BusinessRuleException(
                    "لا يمكن تفعيل المستفيد لعدم وجود وثيقة تأمين سارية لجهة العمل. يرجى ربط وثيقة تأمين أولاً.");
        }
        // Goes through the resolver, not a bare setter: auto-assigning here used
        // to write only the denormalized pointer, leaving the member's dated
        // assignment history claiming a different policy than the pointer did --
        // reintroducing exactly the parallel source of truth the assignment
        // record exists to remove. Caught by
        // MemberSensitiveFieldWriteArchitectureTest.
        memberPolicyResolver.assignPolicy(member, autoPolicy, java.time.LocalDate.now(),
                "إسناد تلقائي لوثيقة جهة العمل السارية عند تفعيل المستفيد",
                com.waad.tba.modules.member.entity.PolicyAssignmentSource.EMPLOYER_DEFAULT, null);
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException(message);
        }
    }
}
