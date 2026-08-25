package com.waad.tba.modules.member.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberEmployerTransferPreviewDto;
import com.waad.tba.modules.member.dto.MemberEmployerTransferPreviewDto.FamilyMemberSnapshot;
import com.waad.tba.modules.member.dto.MemberEmployerTransferRequest;
import com.waad.tba.modules.member.dto.MemberFamilyTransferRequest;
import com.waad.tba.modules.member.dto.MemberFamilyPolicyChangeRequest;
import com.waad.tba.modules.member.dto.MemberFamilyReorderRequest;
import com.waad.tba.modules.member.dto.MemberRelationshipCorrectionRequest;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberCommandAccessPolicy;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Atomic, audited operations that are allowed to change family structure. */
@Service
@RequiredArgsConstructor
public class MemberFamilyService {

    private final MemberRepository memberRepository;
    private final MemberEmployerResolver employerResolver;
    private final MemberPolicyResolver policyResolver;
    private final MemberCommandAccessPolicy commandAccessPolicy;
    private final AuthorizationService authorizationService;
    private final UnifiedMemberMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final BenefitPolicyRepository policyRepository;
    private final EmployerRepository employerRepository;

    @Transactional
    public MemberViewDto transferDependent(Long memberId, MemberFamilyTransferRequest request) {
        requireReason(request.reason());
        if (request.effectiveDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("لا يجوز نقل تابع بتاريخ مستقبلي لأن مؤشر الأسرة الحالي سيتغير فوراً");
        }
        if (Objects.equals(memberId, request.newPrincipalId())) {
            throw new BusinessRuleException("لا يمكن أن يكون المستفيد تابعاً لنفسه");
        }

        // Compass reads identify every row that must be locked. Truth is read
        // again after the deterministic locks below.
        Member compassMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود"));
        Member compassPrincipal = memberRepository.findById(request.newPrincipalId())
                .orElseThrow(() -> new BusinessRuleException("رئيس الأسرة الجديد غير موجود"));
        Long oldParentId = compassMember.getParent() != null ? compassMember.getParent().getId() : null;

        List<Long> lockIds = java.util.stream.Stream.of(memberId, oldParentId, request.newPrincipalId())
                .filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder()).toList();
        java.util.Map<Long, Member> locked = new java.util.HashMap<>();
        for (Long id : lockIds) {
            locked.put(id, memberRepository.findByIdWithLock(id)
                    .orElseThrow(() -> new BusinessRuleException("تغيرت الأسرة أثناء العملية؛ أعد المحاولة")));
        }

        Member member = locked.get(memberId);
        Member newPrincipal = locked.get(request.newPrincipalId());
        if (!member.isDependent()) {
            throw new BusinessRuleException("نقل الأسرة متاح للتابع فقط؛ لا يمكن نقل رئيس أسرة بهذه العملية");
        }
        if (!newPrincipal.isPrincipal()) {
            throw new BusinessRuleException("المستفيد المحدد ليس رئيس أسرة");
        }
        requireVersion(member, request.expectedVersion());
        if (Objects.equals(member.getParent().getId(), newPrincipal.getId())
                && member.getRelationship() == request.relationship()) {
            throw new BusinessRuleException("لم يتغير رئيس الأسرة ولا صلة القرابة");
        }

        commandAccessPolicy.require(MemberOperation.TRANSFER_DEPENDENT, member.getEmployer().getId());
        commandAccessPolicy.require(MemberOperation.TRANSFER_DEPENDENT, newPrincipal.getEmployer().getId());

        Long previousParentId = member.getParent().getId();
        Member.Relationship previousRelationship = member.getRelationship();
        User actor = authorizationService.requireCurrentUser();
        String reason = request.reason().trim();

        // Detach before a cross-employer dated assignment: the database
        // deliberately forbids a dependent and principal from different
        // employers at every persisted point inside the transaction.
        member.setParent(null);
        member.setRelationship(null);
        member.setFamilyOrder(null);
        memberRepository.saveAndFlush(member);

        var targetEmployer = employerResolver.resolveForOrFail(newPrincipal, request.effectiveDate());
        if (!Objects.equals(member.getEmployer().getId(), targetEmployer.getId())) {
            employerResolver.assignEmployer(member, targetEmployer, request.effectiveDate(), reason,
                    EmployerAssignmentSource.FAMILY_CASCADE, actor.getId());
        }
        BenefitPolicy targetPolicy = policyResolver.resolveForOrFail(newPrincipal, request.effectiveDate());
        policyResolver.assignPolicy(member, targetPolicy, request.effectiveDate(), reason,
                PolicyAssignmentSource.MANUAL, actor.getId());

        member.setParent(newPrincipal);
        member.setRelationship(request.relationship());
        member.setFamilyOrder(nextFamilyOrder(newPrincipal.getId()));
        memberRepository.saveAndFlush(member);
        appendHistory(member, previousParentId, newPrincipal.getId(), previousRelationship,
                request.relationship(), request.effectiveDate(), reason, "TRANSFER", actor.getId());
        return mapper.toViewDto(member);
    }

    @Transactional
    public List<MemberViewDto> changeFamilyPolicy(Long principalId, MemberFamilyPolicyChangeRequest request) {
        requireReason(request.reason());
        Member compass = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessRuleException("رئيس الأسرة غير موجود"));
        if (!compass.isPrincipal()) throw new BusinessRuleException("العملية تبدأ من رئيس الأسرة فقط");
        List<Long> ids = java.util.stream.Stream.concat(java.util.stream.Stream.of(principalId),
                memberRepository.findByParentId(principalId).stream().map(Member::getId))
                .distinct().sorted().toList();
        if (!request.expectedVersions().keySet().equals(new java.util.HashSet<>(ids))) {
            throw new BusinessRuleException("يجب إرسال نسخة كل فرد في الأسرة؛ لا يسمح بتغيير جزئي صامت");
        }
        List<Member> locked = ids.stream().map(id -> memberRepository.findByIdWithLock(id)
                .orElseThrow(() -> new BusinessRuleException("تغيرت الأسرة أثناء العملية؛ أعد المحاولة"))).toList();
        BenefitPolicy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new BusinessRuleException("وثيقة المنافع غير موجودة"));
        User actor = authorizationService.requireCurrentUser();
        for (Member member : locked) {
            requireVersion(member, request.expectedVersions().get(member.getId()));
            commandAccessPolicy.require(MemberOperation.CHANGE_POLICY, member.getEmployer().getId());
            Long policyEmployer = policy.getEmployer() != null ? policy.getEmployer().getId() : null;
            Long memberEmployer = employerResolver.resolveForOrFail(member, request.effectiveDate()).getId();
            if (!Objects.equals(policyEmployer, memberEmployer)) {
                throw new BusinessRuleException("الوثيقة لا تتبع جهة عمل الأسرة في تاريخ السريان");
            }
        }
        for (Member member : locked) {
            policyResolver.assignPolicy(member, policy, request.effectiveDate(), request.reason().trim(),
                    PolicyAssignmentSource.MANUAL, actor.getId());
        }
        return locked.stream().map(mapper::toViewDto).toList();
    }

    @Transactional
    public List<MemberViewDto> reorderFamily(Long principalId, MemberFamilyReorderRequest request) {
        Member principal = memberRepository.findByIdWithLock(principalId)
                .orElseThrow(() -> new BusinessRuleException("رئيس الأسرة غير موجود"));
        if (!principal.isPrincipal()) throw new BusinessRuleException("إعادة الترتيب تبدأ من رئيس الأسرة فقط");
        commandAccessPolicy.require(MemberOperation.REORDER_FAMILY, principal.getEmployer().getId());
        List<Member> dependents = memberRepository.findByParentId(principalId).stream()
                .sorted(Comparator.comparing(Member::getId)).toList();
        java.util.Set<Long> actual = dependents.stream().map(Member::getId).collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(new java.util.LinkedHashSet<>(request.dependentIds()))
                || request.dependentIds().size() != actual.size()) {
            throw new BusinessRuleException("قائمة الترتيب يجب أن تحتوي كل تابعي الأسرة مرة واحدة");
        }
        for (Member member : dependents) requireVersion(member, request.expectedVersions().get(member.getId()));
        int offset = dependents.size() + 1000000;
        for (Member member : dependents) member.setFamilyOrder(offset++);
        memberRepository.saveAllAndFlush(dependents);
        java.util.Map<Long, Member> byId = dependents.stream().collect(java.util.stream.Collectors.toMap(Member::getId, m -> m));
        for (int i = 0; i < request.dependentIds().size(); i++) byId.get(request.dependentIds().get(i)).setFamilyOrder(i + 1);
        memberRepository.saveAllAndFlush(dependents);
        return request.dependentIds().stream().map(byId::get).map(mapper::toViewDto).toList();
    }

    @Transactional
    public MemberViewDto correctRelationship(Long memberId, MemberRelationshipCorrectionRequest request) {
        requireReason(request.reason());
        Member member = memberRepository.findByIdWithLock(memberId)
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود"));
        if (!member.isDependent()) {
            throw new BusinessRuleException("صلة القرابة تخص التابعين فقط");
        }
        requireVersion(member, request.expectedVersion());
        if (member.getRelationship() == request.relationship()) {
            throw new BusinessRuleException("صلة القرابة الجديدة مطابقة للقيمة الحالية");
        }
        commandAccessPolicy.require(MemberOperation.CORRECT_RELATIONSHIP, member.getEmployer().getId());
        User actor = authorizationService.requireCurrentUser();
        Member.Relationship previous = member.getRelationship();
        member.setRelationship(request.relationship());
        memberRepository.saveAndFlush(member);
        appendHistory(member, member.getParent().getId(), member.getParent().getId(), previous,
                request.relationship(), LocalDate.now(), request.reason().trim(),
                "RELATIONSHIP_CORRECTION", actor.getId());
        return mapper.toViewDto(member);
    }

    /**
     * Read-only impact preview: current -> new employer, and every family
     * member (principal + dependents) with the version the caller must echo
     * back in the execute request. No write of any kind happens here.
     */
    @Transactional(readOnly = true)
    public MemberEmployerTransferPreviewDto previewTransferPrincipalToEmployer(Long principalId, Long newEmployerId) {
        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessRuleException("رئيس الأسرة غير موجود"));
        if (!principal.isPrincipal()) {
            throw new BusinessRuleException("نقل جهة العمل يبدأ من رئيس الأسرة فقط");
        }
        Employer newEmployer = employerRepository.findById(newEmployerId)
                .orElseThrow(() -> new BusinessRuleException("جهة العمل الجديدة غير موجودة"));

        List<Member> dependents = memberRepository.findByParentId(principalId);
        List<FamilyMemberSnapshot> family = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(principal), dependents.stream())
                .map(m -> new FamilyMemberSnapshot(m.getId(), m.getFullName(), m.isPrincipal(),
                        m.getRelationship() != null ? m.getRelationship().name() : null, m.getVersion()))
                .toList();

        Employer currentEmployer = principal.getEmployer();
        return new MemberEmployerTransferPreviewDto(principalId,
                currentEmployer != null ? currentEmployer.getId() : null,
                currentEmployer != null ? currentEmployer.getName() : null,
                newEmployer.getId(), newEmployer.getName(), family);
    }

    /**
     * Moves the principal and every one of their dependents to a new employer
     * (and, unless explicitly declined, a new policy) as of one effective
     * date, inside one transaction: every family member moves together or
     * none does. Reuses the same dated, append-only assignment writers
     * {@link MemberEmployerResolver#assignEmployer} and
     * {@link MemberPolicyResolver#assignPolicy} that transferDependent
     * already relies on for a single member -- this is family-wide
     * orchestration on top of them, not new write machinery.
     */
    @Transactional
    public List<MemberViewDto> transferPrincipalToEmployer(Long principalId, MemberEmployerTransferRequest request) {
        requireReason(request.reason());
        if (request.effectiveDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "لا يجوز نقل جهة العمل بتاريخ مستقبلي لأن مؤشر الجهة/الوثيقة الحالي سيتغير فوراً");
        }
        if (!request.noPolicy() && request.newPolicyId() == null) {
            throw new BusinessRuleException(
                    "يجب تحديد وثيقة المنافع الجديدة صراحة، أو تأكيد عدم وجود وثيقة لهذه الأسرة");
        }

        Member compassPrincipal = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessRuleException("رئيس الأسرة غير موجود"));
        if (!compassPrincipal.isPrincipal()) {
            throw new BusinessRuleException("نقل جهة العمل يبدأ من رئيس الأسرة فقط");
        }
        Employer newEmployer = employerRepository.findById(request.newEmployerId())
                .orElseThrow(() -> new BusinessRuleException("جهة العمل الجديدة غير موجودة"));

        List<Long> ids = java.util.stream.Stream.concat(java.util.stream.Stream.of(principalId),
                memberRepository.findByParentId(principalId).stream().map(Member::getId))
                .distinct().sorted().toList();
        if (!request.expectedVersions().keySet().equals(new java.util.HashSet<>(ids))) {
            throw new BusinessRuleException("يجب إرسال نسخة كل فرد في الأسرة؛ لا يسمح بنقل جزئي صامت");
        }
        List<Member> locked = ids.stream().map(id -> memberRepository.findByIdWithLock(id)
                .orElseThrow(() -> new BusinessRuleException("تغيرت الأسرة أثناء العملية؛ أعد المحاولة"))).toList();

        for (Member member : locked) {
            requireVersion(member, request.expectedVersions().get(member.getId()));
            commandAccessPolicy.require(MemberOperation.TRANSFER_EMPLOYER, member.getEmployer().getId());
        }
        commandAccessPolicy.require(MemberOperation.TRANSFER_EMPLOYER, newEmployer.getId());

        BenefitPolicy newPolicy = null;
        if (!request.noPolicy()) {
            newPolicy = policyRepository.findById(request.newPolicyId())
                    .orElseThrow(() -> new BusinessRuleException("وثيقة المنافع غير موجودة"));
            if (newPolicy.getStatus() != BenefitPolicy.BenefitPolicyStatus.ACTIVE) {
                throw new BusinessRuleException("لا يمكن تعيين وثيقة غير فعّالة (" + newPolicy.getStatus() + ")");
            }
            Long policyEmployerId = newPolicy.getEmployer() != null ? newPolicy.getEmployer().getId() : null;
            if (!Objects.equals(policyEmployerId, newEmployer.getId())) {
                throw new BusinessRuleException("الوثيقة المحددة لا تتبع جهة العمل الجديدة");
            }
        }

        User actor = authorizationService.requireCurrentUser();
        String reason = request.reason().trim();
        for (Member member : locked) {
            employerResolver.assignEmployer(member, newEmployer, request.effectiveDate(), reason,
                    EmployerAssignmentSource.MANUAL, actor.getId());
            if (newPolicy != null) {
                policyResolver.assignPolicy(member, newPolicy, request.effectiveDate(), reason,
                        PolicyAssignmentSource.MANUAL, actor.getId());
            }
        }
        return locked.stream().map(mapper::toViewDto).toList();
    }

    private void appendHistory(Member member, Long oldParent, Long newParent,
            Member.Relationship oldRelationship, Member.Relationship newRelationship,
            LocalDate effectiveDate, String reason, String type, Long actorId) {
        jdbcTemplate.update("""
                INSERT INTO member_family_transitions(
                    transition_id, member_id, previous_parent_id, new_parent_id,
                    previous_relationship, new_relationship, effective_date, reason,
                    transition_type, changed_by, member_name, member_card_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), member.getId(), oldParent, newParent,
                oldRelationship != null ? oldRelationship.name() : null,
                newRelationship != null ? newRelationship.name() : null,
                Date.valueOf(effectiveDate), reason, type, actorId,
                member.getFullName(), member.getCardNumber());
    }

    private static void requireVersion(Member member, Long expectedVersion) {
        if (!Objects.equals(member.getVersion(), expectedVersion)) {
            throw new BusinessRuleException("تم تعديل سجل المستفيد بواسطة مستخدم آخر؛ حدّث الصفحة وأعد المحاولة");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب العملية إلزامي");
        }
    }

    private int nextFamilyOrder(Long principalId) {
        Integer current = jdbcTemplate.queryForObject(
                "select coalesce(max(family_order),0) from members where parent_id=?", Integer.class, principalId);
        return (current == null ? 0 : current) + 1;
    }
}
