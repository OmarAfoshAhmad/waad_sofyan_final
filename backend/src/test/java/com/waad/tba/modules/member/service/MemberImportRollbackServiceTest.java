package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportFieldSnapshot;
import com.waad.tba.modules.member.dto.MemberImportRollbackPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportRollbackResultDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberImportBatchRow;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.entity.MemberImportRollback;
import com.waad.tba.modules.member.repository.MemberAttributeRepository;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackSkipRepository;
import com.waad.tba.modules.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberImportRollbackServiceTest {

    @Mock private MemberImportLogRepository importLogRepository;
    @Mock private MemberImportBatchRowRepository batchRowRepository;
    @Mock private MemberImportRollbackRepository rollbackRepository;
    @Mock private MemberImportRollbackSkipRepository rollbackSkipRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberAttributeRepository memberAttributeRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private BenefitPolicyRepository benefitPolicyRepository;
    @Mock private MemberFinancialActivityChecker financialActivityChecker;

    @InjectMocks
    private MemberImportRollbackService service;

    private static final Long LOG_ID = 1L;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper (not injected via @Mock) so JSON round-trips genuinely work.
        service = new MemberImportRollbackService(
                importLogRepository, batchRowRepository, rollbackRepository, rollbackSkipRepository,
                memberRepository, memberAttributeRepository, employerRepository, benefitPolicyRepository,
                financialActivityChecker, new ObjectMapper().findAndRegisterModules());

        when(importLogRepository.findById(LOG_ID)).thenReturn(Optional.of(
                MemberImportLog.builder().id(LOG_ID).importBatchId("BATCH-1").build()));
        when(rollbackRepository.findByImportLogIdAndStatus(eq(LOG_ID), eq(MemberImportRollback.Status.COMPLETED)))
                .thenReturn(Optional.empty());
    }

    private MemberImportBatchRow createdRow(Long memberId) {
        return MemberImportBatchRow.builder().importLogId(LOG_ID).memberId(memberId)
                .action(MemberImportBatchRow.Action.CREATED).build();
    }

    private MemberImportBatchRow updatedRow(Long memberId, String snapshotJson) {
        return MemberImportBatchRow.builder().importLogId(LOG_ID).memberId(memberId)
                .action(MemberImportBatchRow.Action.UPDATED).previousSnapshot(snapshotJson).build();
    }

    @Test
    @DisplayName("Reason is mandatory")
    void execute_RequiresReason() {
        assertThatThrownBy(() -> service.execute(LOG_ID, "  ", "admin"))
                .isInstanceOf(BusinessRuleException.class);
        verify(batchRowRepository, never()).findByImportLogId(anyLong());
    }

    @Test
    @DisplayName("A batch already rolled back successfully cannot be rolled back again")
    void execute_RefusesDoubleRollback() {
        when(rollbackRepository.findByImportLogIdAndStatus(eq(LOG_ID), eq(MemberImportRollback.Status.COMPLETED)))
                .thenReturn(Optional.of(MemberImportRollback.builder().id(9L).build()));

        assertThatThrownBy(() -> service.execute(LOG_ID, "خطأ في الملف", "admin"))
                .isInstanceOf(BusinessRuleException.class);
        verify(batchRowRepository, never()).findByImportLogId(anyLong());
    }

    @Test
    @DisplayName("A created member with no financial activity is deleted")
    void execute_DeletesCreatedMemberWithNoActivity() {
        Member created = Member.builder().id(101L).fullName("Ali").build(); // no parent -> principal
        when(batchRowRepository.findByImportLogId(LOG_ID)).thenReturn(List.of(createdRow(101L)));
        when(financialActivityChecker.membersToKeep(List.of(101L))).thenReturn(Set.of());
        when(memberRepository.findAllById(Set.of(101L))).thenReturn(List.of(created));
        when(rollbackRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MemberImportRollback r = inv.getArgument(0);
            r.setId(55L);
            return r;
        });

        MemberImportRollbackResultDto result = service.execute(LOG_ID, "خطأ في الملف", "admin");

        assertThat(result.getRevertedCreatedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        verify(memberAttributeRepository).deleteByMemberIdIn(Set.of(101L));
        verify(memberRepository).deleteMembersByIds(List.of(101L)); // principal path
        verify(rollbackSkipRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("A created member WITH financial activity is skipped, not deleted, and recorded")
    void execute_SkipsCreatedMemberWithActivity() {
        Member created = Member.builder().id(202L).fullName("Sara").build();
        when(batchRowRepository.findByImportLogId(LOG_ID)).thenReturn(List.of(createdRow(202L)));
        when(financialActivityChecker.membersToKeep(List.of(202L))).thenReturn(Set.of(202L));
        when(rollbackRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MemberImportRollback r = inv.getArgument(0);
            r.setId(56L);
            return r;
        });

        MemberImportRollbackResultDto result = service.execute(LOG_ID, "خطأ في الملف", "admin");

        assertThat(result.getRevertedCreatedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(memberRepository, never()).deleteMembersByIds(any());
        verify(rollbackSkipRepository).saveAll(any());
    }

    @Test
    @DisplayName("An updated member is restored to exactly its previous snapshot")
    void execute_RestoresUpdatedMemberFromSnapshot() throws Exception {
        Member member = Member.builder().id(303L).fullName("Changed Name").phone("999").build();
        MemberImportFieldSnapshot snapshot = MemberImportFieldSnapshot.builder()
                .fullName("Original Name").phone("555").build();
        String snapshotJson = new ObjectMapper().writeValueAsString(snapshot);

        when(batchRowRepository.findByImportLogId(LOG_ID)).thenReturn(List.of(updatedRow(303L, snapshotJson)));
        when(financialActivityChecker.membersToKeep(List.of())).thenReturn(Set.of());
        when(memberRepository.findById(303L)).thenReturn(Optional.of(member));
        when(rollbackRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MemberImportRollback r = inv.getArgument(0);
            r.setId(57L);
            return r;
        });

        MemberImportRollbackResultDto result = service.execute(LOG_ID, "خطأ في الملف", "admin");

        assertThat(result.getRevertedUpdatedCount()).isEqualTo(1);
        assertThat(member.getFullName()).isEqualTo("Original Name");
        assertThat(member.getPhone()).isEqualTo("555");
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("Preview reports the same counts execute would produce, with no writes")
    void preview_ComputesCountsWithoutWriting() {
        Member kept = Member.builder().id(404L).fullName("Kept").build();
        when(batchRowRepository.findByImportLogId(LOG_ID))
                .thenReturn(List.of(createdRow(303L), createdRow(404L), updatedRow(505L, "{}")));
        when(financialActivityChecker.membersToKeep(List.of(303L, 404L))).thenReturn(Set.of(404L));
        when(memberRepository.findAllById(Set.of(404L))).thenReturn(List.of(kept));

        MemberImportRollbackPreviewDto preview = service.preview(LOG_ID);

        assertThat(preview.getCreatedCount()).isEqualTo(2);
        assertThat(preview.getUpdatedCount()).isEqualTo(1);
        assertThat(preview.getWouldRevertCreatedCount()).isEqualTo(1);
        assertThat(preview.getWouldSkipCount()).isEqualTo(1);
        assertThat(preview.getSkips()).hasSize(1);
        assertThat(preview.getSkips().get(0).getMemberId()).isEqualTo(404L);
        verify(memberRepository, never()).deleteMembersByIds(any());
        verify(rollbackRepository, never()).saveAndFlush(any());
    }
}
