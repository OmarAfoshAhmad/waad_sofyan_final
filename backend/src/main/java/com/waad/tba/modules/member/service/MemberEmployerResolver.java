package com.waad.tba.modules.member.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberEmployerAssignment;
import com.waad.tba.modules.member.repository.MemberEmployerAssignmentRepository;
import com.waad.tba.modules.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** Single dated source of truth for a member's employer. */
@Service
@RequiredArgsConstructor
public class MemberEmployerResolver {

    private static final String OVERLAP_CONSTRAINT = "uk_member_employer_assignment_no_overlap";

    private final MemberEmployerAssignmentRepository assignmentRepository;
    private final EmployerRepository employerRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public Optional<MemberEmployerAssignment> resolveAssignmentFor(Member member, LocalDate serviceDate) {
        requireServiceDate(serviceDate);
        if (member == null || member.getId() == null) {
            return Optional.empty();
        }
        return assignmentRepository.findCovering(member.getId(), serviceDate);
    }

    @Transactional(readOnly = true)
    public Optional<Employer> resolveFor(Member member, LocalDate serviceDate) {
        return resolveAssignmentFor(member, serviceDate)
                .flatMap(a -> employerRepository.findById(a.getEmployerId()));
    }

    @Transactional(readOnly = true)
    public Employer resolveForOrFail(Member member, LocalDate serviceDate) {
        requireServiceDate(serviceDate);
        return resolveFor(member, serviceDate).orElseThrow(() -> new BusinessRuleException(
                "لا توجد جهة عمل معيّنة للمستفيد في تاريخ الخدمة " + serviceDate));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MemberEmployerAssignment assignEmployer(Member member, Employer employer, LocalDate effectiveFrom,
            String reason, EmployerAssignmentSource source, Long actingUserId) {
        if (member == null || member.getId() == null) {
            throw new BusinessRuleException("يجب حفظ المستفيد قبل تعيين جهة العمل");
        }
        if (employer == null || employer.getId() == null) {
            throw new BusinessRuleException("جهة العمل مطلوبة");
        }
        if (effectiveFrom == null) {
            throw new BusinessRuleException("تاريخ سريان جهة العمل إلزامي");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب تعيين جهة العمل إلزامي");
        }

        Member lockedMember = memberRepository.findByIdWithLock(member.getId())
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود"));

        // Locked with the same query EmployerService.archive() takes on this
        // row, so the two serialise on the employer rather than racing.
        // Without this, a concurrent archive can read "nobody belongs to this
        // employer" a moment before this assignment commits: the count is
        // taken before the new row lands, archive proceeds believing the
        // employer is empty, and the row that would have blocked it lands
        // under an employer that is now archived.
        Employer lockedEmployer = employerRepository.findByIdForLifecycleTransition(employer.getId())
                .orElseThrow(() -> new BusinessRuleException("جهة العمل غير موجودة"));
        if (!Boolean.TRUE.equals(lockedEmployer.getActive())) {
            throw new BusinessRuleException("لا يمكن تعيين مستفيد لجهة عمل مؤرشفة");
        }

        Optional<MemberEmployerAssignment> open =
                assignmentRepository.findByMemberIdAndAssignmentEndDateIsNull(member.getId());
        if (open.isPresent()) {
            MemberEmployerAssignment current = open.get();
            if (current.getEmployerId().equals(employer.getId())) {
                lockedMember.setEmployer(employer);
                memberRepository.saveAndFlush(lockedMember);
                synchronizeCaller(member, lockedMember);
                return current;
            }
            if (!effectiveFrom.isAfter(current.getAssignmentStartDate())) {
                throw new BusinessRuleException(
                        "تاريخ سريان جهة العمل الجديدة يجب أن يكون بعد بداية التعيين الحالي ("
                                + current.getAssignmentStartDate() + ")");
            }
            current.setAssignmentEndDate(effectiveFrom);
            assignmentRepository.saveAndFlush(current);
        }

        try {
            MemberEmployerAssignment created = assignmentRepository.saveAndFlush(MemberEmployerAssignment.builder()
                    .memberId(member.getId())
                    .employerId(employer.getId())
                    .assignmentStartDate(effectiveFrom)
                    .assignmentReason(reason.trim())
                    .assignmentSource(source == null ? EmployerAssignmentSource.MANUAL : source)
                    .assignedBy(actingUserId)
                    .memberFullName(lockedMember.getFullName())
                    .memberCardNumber(lockedMember.getCardNumber())
                    .employerName(employer.getName())
                    .employerCode(employer.getCode())
                    .createdAt(LocalDateTime.now())
                    .build());
            lockedMember.setEmployer(employer); // display-only current pointer
            memberRepository.saveAndFlush(lockedMember);
            synchronizeCaller(member, lockedMember);
            return created;
        } catch (DataIntegrityViolationException ex) {
            if (mentionsConstraint(ex, OVERLAP_CONSTRAINT)) {
                throw new BusinessRuleException(
                        "يوجد تعيين جهة عمل متداخل للمستفيد في تاريخ السريان المحدد (" + effectiveFrom + ")");
            }
            throw ex;
        }
    }

    private static void synchronizeCaller(Member caller, Member persisted) {
        if (caller != persisted) {
            caller.setEmployer(persisted.getEmployer());
            caller.setVersion(persisted.getVersion());
        }
    }

    /**
     * Restores only the denormalized current pointer after an import rollback.
     * The importer never rewrites dated employer assignments for an existing
     * member, so rollback must not manufacture a new historical assignment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreCurrentPointerAfterImport(Member member, Employer employer) {
        member.setEmployer(employer);
        memberRepository.save(member);
    }

    private static boolean mentionsConstraint(Throwable error, String constraintName) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static void requireServiceDate(LocalDate serviceDate) {
        if (serviceDate == null) {
            throw new BusinessRuleException(
                    "تاريخ الخدمة إلزامي لحل جهة العمل، ولا يجوز استبداله بتاريخ اليوم");
        }
    }
}
