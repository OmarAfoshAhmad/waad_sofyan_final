package com.waad.tba.modules.member.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportFieldSnapshot;
import com.waad.tba.modules.member.dto.MemberImportRollbackPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportRollbackPreviewDto.SkipPreview;
import com.waad.tba.modules.member.dto.MemberImportRollbackResultDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberAttribute;
import com.waad.tba.modules.member.entity.MemberImportBatchRow;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.entity.MemberImportRollback;
import com.waad.tba.modules.member.entity.MemberImportRollbackSkip;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackSkipRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberImportAccessPolicy;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Atomic, conflict-aware rollback of one completed member import batch. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberImportRollbackService {
    private static final String HAS_PROTECTED_HISTORY = "HAS_PROTECTED_HISTORY";
    private static final String MODIFIED_AFTER_IMPORT = "MODIFIED_AFTER_IMPORT";
    private static final String MEMBER_MISSING = "MEMBER_MISSING";
    private static final String FAMILY_STILL_REFERENCES_MEMBER = "FAMILY_STILL_REFERENCES_MEMBER";

    private final MemberImportLogRepository importLogRepository;
    private final MemberImportBatchRowRepository batchRowRepository;
    private final MemberImportRollbackRepository rollbackRepository;
    private final MemberImportRollbackSkipRepository rollbackSkipRepository;
    private final MemberRepository memberRepository;
    private final EmployerRepository employerRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final MemberFinancialActivityChecker activityChecker;
    private final MemberEmployerResolver employerResolver;
    private final MemberPolicyResolver policyResolver;
    private final MemberFamilyService familyService;
    private final MemberStatusTransitionService statusTransitionService;
    private final MemberImportAccessPolicy importAccessPolicy;
    private final MemberImportRollbackAuditRecorder auditRecorder;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MemberImportRollbackPreviewDto preview(Long importLogId) {
        MemberImportLog logRow = importLogRepository.findById(importLogId)
                .orElseThrow(() -> new BusinessRuleException("سجل الاستيراد غير موجود"));
        List<TrackedRow> rows = readRows(importLogId);
        importAccessPolicy.requireRollback(employerIds(rows));
        RollbackPlan plan = plan(rows, loadMembers(rows, false));
        return MemberImportRollbackPreviewDto.builder()
                .importLogId(importLogId).batchId(logRow.getImportBatchId())
                .alreadyRolledBack(completedExists(importLogId))
                .createdCount((int) rows.stream().filter(TrackedRow::created).count())
                .updatedCount((int) rows.stream().filter(r -> !r.created()).count())
                .wouldRevertCreatedCount(plan.createdToDelete().size())
                .wouldRevertUpdatedCount(plan.updatedToRestore().size())
                .wouldSkipCount(plan.skips().size()).skips(toPreviewSkips(plan.skips())).build();
    }

    @Transactional
    public MemberImportRollbackResultDto execute(Long importLogId, String reason) {
        requireReason(reason);
        LocalDateTime startedAt = LocalDateTime.now();
        User actor = authorizationService.requireCurrentUser();
        try {
            MemberImportLog logRow = importLogRepository.findByIdForRollback(importLogId)
                    .orElseThrow(() -> new BusinessRuleException("سجل الاستيراد غير موجود"));
            if (completedExists(importLogId)) throw new BusinessRuleException("سبق التراجع عن هذه الدفعة");
            List<TrackedRow> rows = readRows(importLogId);
            importAccessPolicy.requireRollback(employerIds(rows));

            Map<Long, Member> lockedMembers = loadMembers(rows, true);
            RollbackPlan plan = plan(rows, lockedMembers);
            int restored = 0;
            for (TrackedRow row : plan.updatedToRestore()) {
                restoreUpdated(row, reason.trim(), actor.getId());
                restored++;
            }

            int deleted = 0;
            List<TrackedRow> deletionOrder = plan.createdToDelete().stream()
                    .sorted(Comparator.comparing((TrackedRow row) -> lockedMembers.get(row.memberId()).isDependent())
                            .reversed()).toList();
            for (TrackedRow row : deletionOrder) {
                statusTransitionService.hardDeleteAfterAuthorizedImportRollback(row.memberId(),
                        "تراجع عن استيراد " + logRow.getImportBatchId() + ": " + reason.trim(),
                        actor.getId(), actor.getUsername());
                deleted++;
            }

            MemberImportRollback rollback = rollbackRepository.saveAndFlush(MemberImportRollback.builder()
                    .importLogId(importLogId).reason(reason.trim()).performedBy(actor.getUsername())
                    .status(MemberImportRollback.Status.COMPLETED)
                    .revertedCreatedCount(deleted).revertedUpdatedCount(restored)
                    .skippedCount(plan.skips().size()).startedAt(startedAt)
                    .completedAt(LocalDateTime.now()).build());
            if (!plan.skips().isEmpty()) {
                rollbackSkipRepository.saveAll(plan.skips().entrySet().stream()
                        .map(e -> MemberImportRollbackSkip.builder().rollbackId(rollback.getId())
                                .memberId(e.getKey()).reason(e.getValue()).build()).toList());
            }
            return MemberImportRollbackResultDto.builder().rollbackId(rollback.getId())
                    .importLogId(importLogId).status("COMPLETED")
                    .revertedCreatedCount(deleted).revertedUpdatedCount(restored)
                    .skippedCount(plan.skips().size()).completedAt(rollback.getCompletedAt())
                    .message(String.format("تم التراجع بأمان: حُذف %d، أُعيد %d، واستُثني %d دون الكتابة فوق تعديلات لاحقة",
                            deleted, restored, plan.skips().size())).build();
        } catch (Exception error) {
            log.error("[MemberImportRollback] importLogId={} failed", importLogId, error);
            auditRecorder.recordFailure(importLogId, reason.trim(), actor.getUsername(), startedAt, safeError(error));
            throw error;
        }
    }

    private RollbackPlan plan(List<TrackedRow> rows, Map<Long, Member> currentMembers) {
        Map<Long, String> skips = new LinkedHashMap<>();
        List<TrackedRow> created = rows.stream().filter(TrackedRow::created).toList();
        List<TrackedRow> updated = rows.stream().filter(r -> !r.created()).toList();
        Set<Long> protectedIds = activityChecker.membersToKeep(created.stream().map(TrackedRow::memberId).toList());
        for (TrackedRow row : created) {
            Member member = currentMembers.get(row.memberId());
            if (member == null) skips.put(row.memberId(), MEMBER_MISSING);
            else if (!row.imported().matches(member)) skips.put(row.memberId(), MODIFIED_AFTER_IMPORT);
            else if (protectedIds.contains(row.memberId())) skips.put(row.memberId(), HAS_PROTECTED_HISTORY);
        }
        Set<Long> createdIds = created.stream().map(TrackedRow::memberId).collect(java.util.stream.Collectors.toSet());
        List<Long> principalIds = created.stream().map(TrackedRow::memberId)
                .map(currentMembers::get).filter(Objects::nonNull).filter(Member::isPrincipal)
                .map(Member::getId).toList();
        Map<Long, List<Member>> dependentsByPrincipal = principalIds.isEmpty() ? Map.of()
                : memberRepository.findByParentIdIn(principalIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(d -> d.getParent().getId()));
        for (TrackedRow row : created) {
            if (skips.containsKey(row.memberId())) continue;
            Member member = currentMembers.get(row.memberId());
            if (member != null && member.isPrincipal()) {
                boolean surviving = dependentsByPrincipal.getOrDefault(member.getId(), List.of()).stream()
                        .anyMatch(dep -> !createdIds.contains(dep.getId()) || skips.containsKey(dep.getId()));
                if (surviving) skips.put(row.memberId(), FAMILY_STILL_REFERENCES_MEMBER);
            }
        }
        for (TrackedRow row : updated) {
            Member member = currentMembers.get(row.memberId());
            if (member == null) skips.put(row.memberId(), MEMBER_MISSING);
            else if (!row.imported().matches(member)) skips.put(row.memberId(), MODIFIED_AFTER_IMPORT);
        }
        return new RollbackPlan(created.stream().filter(r -> !skips.containsKey(r.memberId())).toList(),
                updated.stream().filter(r -> !skips.containsKey(r.memberId())).toList(), skips);
    }

    private void restoreUpdated(TrackedRow row, String reason, Long actorId) {
        Member member = memberRepository.findByIdWithLock(row.memberId())
                .orElseThrow(() -> new BusinessRuleException("اختفى المستفيد أثناء التراجع: " + row.memberId()));
        MemberImportFieldSnapshot snapshot = Objects.requireNonNull(row.previous());
        Employer employer = snapshot.getEmployerId() == null ? null : employerRepository.findById(snapshot.getEmployerId())
                .orElseThrow(() -> new BusinessRuleException("جهة العمل السابقة لم تعد موجودة"));
        BenefitPolicy policy = snapshot.getBenefitPolicyId() == null ? null
                : benefitPolicyRepository.findById(snapshot.getBenefitPolicyId())
                        .orElseThrow(() -> new BusinessRuleException("وثيقة المنافع السابقة لم تعد موجودة"));
        Member parent = snapshot.getParentId() == null ? null : memberRepository.findById(snapshot.getParentId())
                .orElseThrow(() -> new BusinessRuleException("رئيس الأسرة السابق لم يعد موجوداً"));

        member.setFullName(snapshot.getFullName());
        member.setCardStatus(enumValue(Member.CardStatus.class, snapshot.getCardStatus()));
        member.setCardNumber(snapshot.getCardNumber()); member.setBarcode(snapshot.getBarcode());
        member.setNationalNumber(snapshot.getNationalNumber()); member.setBirthDate(snapshot.getBirthDate());
        member.setGender(enumValue(Member.Gender.class, snapshot.getGender())); member.setPhone(snapshot.getPhone());
        member.setEmail(snapshot.getEmail()); member.setEmployeeNumber(snapshot.getEmployeeNumber());
        member.setPolicyNumber(snapshot.getPolicyNumber()); member.setStartDate(snapshot.getStartDate());
        member.getAttributes().clear();
        if (snapshot.getAttributes() != null) {
            snapshot.getAttributes().forEach(attribute -> member.getAttributes().add(MemberAttribute.builder()
                    .member(member).attributeCode(attribute.getAttributeCode())
                    .attributeValue(attribute.getAttributeValue())
                    .source(enumValue(MemberAttribute.AttributeSource.class, attribute.getSource()))
                    .sourceReference(attribute.getSourceReference())
                    .createdBy(attribute.getCreatedBy()).updatedBy(attribute.getUpdatedBy()).build()));
        }
        employerResolver.restoreCurrentPointerAfterImport(member, employer);
        policyResolver.restoreCurrentPointerAfterImport(member, policy);
        familyService.restoreFamilyLinkAfterImport(member, parent,
                enumValue(Member.Relationship.class, snapshot.getRelationship()), reason, actorId);
        memberRepository.save(member);
        statusTransitionService.restoreStatusAfterImport(member,
                enumValue(Member.MemberStatus.class, snapshot.getStatus()), reason, actorId);
    }

    private List<TrackedRow> readRows(Long importLogId) {
        List<MemberImportBatchRow> stored = batchRowRepository.findByImportLogId(importLogId);
        if (stored.isEmpty()) throw new BusinessRuleException("هذه الدفعة لا تحتوي سجلاً تفصيلياً قابلاً للتراجع");
        return stored.stream().map(row -> new TrackedRow(row.getMemberId(),
                row.getAction() == MemberImportBatchRow.Action.CREATED, parse(row.getPreviousSnapshot()),
                parseRequired(row.getImportedSnapshot(), row.getMemberId()))).toList();
    }

    private Set<Long> employerIds(List<TrackedRow> rows) {
        Set<Long> ids = rows.stream().map(r -> r.imported().getEmployerId()).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) throw new BusinessRuleException("تعذر تحديد نطاق جهة العمل لدفعة الاستيراد");
        return ids;
    }

    private List<SkipPreview> toPreviewSkips(Map<Long, String> skips) {
        Map<Long, Member> members = new HashMap<>();
        memberRepository.findAllById(skips.keySet()).forEach(m -> members.put(m.getId(), m));
        return skips.entrySet().stream().map(e -> SkipPreview.builder().memberId(e.getKey())
                .memberName(members.containsKey(e.getKey()) ? members.get(e.getKey()).getFullName() : null)
                .reason(e.getValue()).build()).toList();
    }

    private Map<Long, Member> loadMembers(List<TrackedRow> rows, boolean lock) {
        List<Long> ids = rows.stream().map(TrackedRow::memberId).distinct().sorted().toList();
        List<Member> members = lock ? memberRepository.findAllByIdWithLock(ids) : memberRepository.findAllById(ids);
        return members.stream().collect(java.util.stream.Collectors.toMap(Member::getId, m -> m));
    }

    private MemberImportFieldSnapshot parse(String json) { return json == null ? null : parseRequired(json, null); }
    private MemberImportFieldSnapshot parseRequired(String json, Long id) {
        try { return objectMapper.readValue(json, MemberImportFieldSnapshot.class); }
        catch (Exception e) { throw new BusinessRuleException("تعذر قراءة لقطة الاستيراد للمستفيد " + id); }
    }
    private boolean completedExists(Long id) {
        return rollbackRepository.findByImportLogIdAndStatus(id, MemberImportRollback.Status.COMPLETED).isPresent();
    }
    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("سبب التراجع إلزامي");
    }
    private static String safeError(Throwable e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
    private record TrackedRow(Long memberId, boolean created, MemberImportFieldSnapshot previous,
            MemberImportFieldSnapshot imported) {}
    private record RollbackPlan(List<TrackedRow> createdToDelete, List<TrackedRow> updatedToRestore,
            Map<Long, String> skips) {}
}
