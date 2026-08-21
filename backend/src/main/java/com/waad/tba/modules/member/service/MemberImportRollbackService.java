package com.waad.tba.modules.member.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import com.waad.tba.modules.member.entity.MemberImportBatchRow;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.entity.MemberImportRollback;
import com.waad.tba.modules.member.entity.MemberImportRollbackSkip;
import com.waad.tba.modules.member.repository.MemberAttributeRepository;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackSkipRepository;
import com.waad.tba.modules.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Undoes an import batch, safely.
 *
 * CREATED members are deleted the same way {@link MemberExcelImportService
 * #clearOldMembers} already deletes members -- via
 * {@link MemberFinancialActivityChecker}, the single shared definition of
 * "has this member (or their dependent) ever moved money" -- and by the same
 * bulk-delete path, dependents first. A member with activity is never
 * touched; it is recorded as a skip instead.
 *
 * UPDATED members are restored from the {@link MemberImportFieldSnapshot}
 * captured immediately before the import overwrote them -- exactly the
 * fields the import can change, nothing else.
 *
 * A batch may be rolled back successfully at most once: enforced first here
 * for a clean error, and again by a partial unique index in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberImportRollbackService {

    private final MemberImportLogRepository importLogRepository;
    private final MemberImportBatchRowRepository batchRowRepository;
    private final MemberImportRollbackRepository rollbackRepository;
    private final MemberImportRollbackSkipRepository rollbackSkipRepository;
    private final MemberRepository memberRepository;
    private final MemberAttributeRepository memberAttributeRepository;
    private final EmployerRepository employerRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final MemberFinancialActivityChecker financialActivityChecker;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MemberImportRollbackPreviewDto preview(Long importLogId) {
        MemberImportLog importLog = importLogRepository.findById(importLogId)
                .orElseThrow(() -> new BusinessRuleException("سجل الاستيراد غير موجود"));
        List<MemberImportBatchRow> rows = batchRowRepository.findByImportLogId(importLogId);

        boolean alreadyRolledBack = rollbackRepository
                .findByImportLogIdAndStatus(importLogId, MemberImportRollback.Status.COMPLETED).isPresent();

        List<Long> createdIds = rows.stream()
                .filter(r -> r.getAction() == MemberImportBatchRow.Action.CREATED)
                .map(MemberImportBatchRow::getMemberId).toList();
        int updatedCount = (int) rows.stream()
                .filter(r -> r.getAction() == MemberImportBatchRow.Action.UPDATED).count();

        Set<Long> toKeep = financialActivityChecker.membersToKeep(createdIds);
        Map<Long, Member> membersById = new HashMap<>();
        if (!toKeep.isEmpty()) {
            for (Member m : memberRepository.findAllById(toKeep)) {
                membersById.put(m.getId(), m);
            }
        }

        List<SkipPreview> skips = new ArrayList<>();
        for (Long id : createdIds) {
            if (toKeep.contains(id)) {
                Member m = membersById.get(id);
                skips.add(SkipPreview.builder().memberId(id)
                        .memberName(m != null ? m.getFullName() : null)
                        .reason("HAS_FINANCIAL_ACTIVITY").build());
            }
        }

        return MemberImportRollbackPreviewDto.builder()
                .importLogId(importLogId).batchId(importLog.getImportBatchId())
                .alreadyRolledBack(alreadyRolledBack)
                .createdCount(createdIds.size()).updatedCount(updatedCount)
                .wouldRevertCreatedCount(createdIds.size() - skips.size())
                .wouldSkipCount(skips.size())
                .skips(skips)
                .build();
    }

    @Transactional
    public MemberImportRollbackResultDto execute(Long importLogId, String reason, String performedBy) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب التراجع إلزامي");
        }
        importLogRepository.findById(importLogId)
                .orElseThrow(() -> new BusinessRuleException("سجل الاستيراد غير موجود"));
        if (rollbackRepository.findByImportLogIdAndStatus(importLogId, MemberImportRollback.Status.COMPLETED)
                .isPresent()) {
            throw new BusinessRuleException("سبق التراجع عن هذه الدفعة");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        try {
            List<MemberImportBatchRow> rows = batchRowRepository.findByImportLogId(importLogId);

            List<Long> createdIds = rows.stream()
                    .filter(r -> r.getAction() == MemberImportBatchRow.Action.CREATED)
                    .map(MemberImportBatchRow::getMemberId).toList();
            List<MemberImportBatchRow> updatedRows = rows.stream()
                    .filter(r -> r.getAction() == MemberImportBatchRow.Action.UPDATED).toList();

            Set<Long> toKeep = financialActivityChecker.membersToKeep(createdIds);
            Set<Long> toDelete = new HashSet<>(createdIds);
            toDelete.removeAll(toKeep);

            int revertedUpdatedCount = restoreUpdatedMembers(updatedRows);
            int revertedCreatedCount = deleteCreatedMembers(toDelete);

            MemberImportRollback rollback = MemberImportRollback.builder()
                    .importLogId(importLogId).reason(reason)
                    .performedBy(performedBy != null ? performedBy : "system")
                    .status(MemberImportRollback.Status.COMPLETED)
                    .revertedCreatedCount(revertedCreatedCount)
                    .revertedUpdatedCount(revertedUpdatedCount)
                    .skippedCount(toKeep.size())
                    .startedAt(startedAt).completedAt(LocalDateTime.now())
                    .build();
            // saveAndFlush: a concurrent completed rollback for the same batch
            // fails HERE on the partial unique index, rolling back every
            // delete/restore this transaction just made together with it.
            rollback = rollbackRepository.saveAndFlush(rollback);

            if (!toKeep.isEmpty()) {
                List<MemberImportRollbackSkip> skips = new ArrayList<>();
                for (Long id : toKeep) {
                    skips.add(MemberImportRollbackSkip.builder()
                            .rollbackId(rollback.getId()).memberId(id)
                            .reason("HAS_FINANCIAL_ACTIVITY").build());
                }
                rollbackSkipRepository.saveAll(skips);
            }

            return MemberImportRollbackResultDto.builder()
                    .rollbackId(rollback.getId()).importLogId(importLogId).status("COMPLETED")
                    .revertedCreatedCount(revertedCreatedCount).revertedUpdatedCount(revertedUpdatedCount)
                    .skippedCount(toKeep.size()).completedAt(rollback.getCompletedAt())
                    .message(String.format("تم التراجع: حُذف %d عضو جديد، أُعيد %d عضو معدَّل، استُثني %d بسبب حركة مالية",
                            revertedCreatedCount, revertedUpdatedCount, toKeep.size()))
                    .build();
        } catch (Exception e) {
            log.error("[MemberImportRollback] فشل التراجع عن الدفعة {}: {}", importLogId, e.getMessage(), e);
            recordFailure(importLogId, reason, performedBy, startedAt, e.getMessage());
            throw e;
        }
    }

    private int restoreUpdatedMembers(List<MemberImportBatchRow> updatedRows) {
        int count = 0;
        for (MemberImportBatchRow row : updatedRows) {
            Member member = memberRepository.findById(row.getMemberId()).orElse(null);
            if (member == null) continue; // already gone by some other means -- nothing to restore
            MemberImportFieldSnapshot snapshot;
            try {
                snapshot = objectMapper.readValue(row.getPreviousSnapshot(), MemberImportFieldSnapshot.class);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "تعذّر قراءة نسخة العضو " + row.getMemberId() + " قبل التعديل", e);
            }
            applySnapshot(member, snapshot);
            memberRepository.save(member);
            count++;
        }
        return count;
    }

    private void applySnapshot(Member member, MemberImportFieldSnapshot s) {
        member.setFullName(s.getFullName());
        Employer employer = s.getEmployerId() == null ? null
                : employerRepository.findById(s.getEmployerId()).orElse(null);
        member.setEmployer(employer);
        BenefitPolicy policy = s.getBenefitPolicyId() == null ? null
                : benefitPolicyRepository.findById(s.getBenefitPolicyId()).orElse(null);
        member.setBenefitPolicy(policy);
        Member parent = s.getParentId() == null ? null
                : memberRepository.findById(s.getParentId()).orElse(null);
        member.setParent(parent);
        member.setRelationship(s.getRelationship() == null ? null
                : Member.Relationship.valueOf(s.getRelationship()));
        member.setCardStatus(s.getCardStatus() == null ? null
                : Member.CardStatus.valueOf(s.getCardStatus()));
        member.setCardNumber(s.getCardNumber());
        member.setBarcode(s.getBarcode());
        member.setNationalNumber(s.getNationalNumber());
        member.setBirthDate(s.getBirthDate());
        member.setGender(s.getGender() == null ? null : Member.Gender.valueOf(s.getGender()));
        member.setPhone(s.getPhone());
        member.setEmail(s.getEmail());
        member.setEmployeeNumber(s.getEmployeeNumber());
        member.setPolicyNumber(s.getPolicyNumber());
        member.setStartDate(s.getStartDate());
    }

    /** Same bulk-delete path as {@code clearOldMembers}: attributes, dependents, then principals. */
    private int deleteCreatedMembers(Set<Long> memberIdsToDelete) {
        if (memberIdsToDelete.isEmpty()) return 0;

        Map<Long, Member> memberMap = new HashMap<>();
        for (Member m : memberRepository.findAllById(memberIdsToDelete)) {
            memberMap.put(m.getId(), m);
        }

        List<Long> dependentsToDelete = new ArrayList<>();
        List<Long> principalsToDelete = new ArrayList<>();
        for (Long id : memberIdsToDelete) {
            Member m = memberMap.get(id);
            if (m == null) continue; // already gone
            if (m.isDependent()) {
                dependentsToDelete.add(id);
            } else {
                principalsToDelete.add(id);
            }
        }

        memberAttributeRepository.deleteByMemberIdIn(memberIdsToDelete);
        if (!dependentsToDelete.isEmpty()) memberRepository.deleteMembersByIds(dependentsToDelete);
        if (!principalsToDelete.isEmpty()) memberRepository.deleteMembersByIds(principalsToDelete);
        return dependentsToDelete.size() + principalsToDelete.size();
    }

    /**
     * Same reasoning as {@link MemberImportAuditRecorder#markFailed}: a
     * durable record of a failed rollback attempt must survive even though
     * this method's own transaction is about to roll back everything it did.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long importLogId, String reason, String performedBy, LocalDateTime startedAt,
            String errorMessage) {
        try {
            MemberImportRollback failed = MemberImportRollback.builder()
                    .importLogId(importLogId).reason(reason)
                    .performedBy(performedBy != null ? performedBy : "system")
                    .status(MemberImportRollback.Status.FAILED)
                    .errorMessage(errorMessage)
                    .startedAt(startedAt).completedAt(LocalDateTime.now())
                    .build();
            rollbackRepository.saveAndFlush(failed);
        } catch (Exception e) {
            log.error("[MemberImportRollback][AUDIT_LOG_FAILURE] Failed to record rollback failure for importLogId={}: {}",
                    importLogId, e.getMessage(), e);
        }
    }
}
