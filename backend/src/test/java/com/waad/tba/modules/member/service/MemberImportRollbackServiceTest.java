package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportFieldSnapshot;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberImportBatchRow;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackRepository;
import com.waad.tba.modules.member.repository.MemberImportRollbackSkipRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberImportAccessPolicy;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberImportRollbackServiceTest {
    @Mock MemberImportLogRepository importLogRepository;
    @Mock MemberImportBatchRowRepository batchRowRepository;
    @Mock MemberImportRollbackRepository rollbackRepository;
    @Mock MemberImportRollbackSkipRepository rollbackSkipRepository;
    @Mock MemberRepository memberRepository;
    @Mock EmployerRepository employerRepository;
    @Mock BenefitPolicyRepository benefitPolicyRepository;
    @Mock MemberFinancialActivityChecker activityChecker;
    @Mock MemberEmployerResolver employerResolver;
    @Mock MemberPolicyResolver policyResolver;
    @Mock MemberFamilyService familyService;
    @Mock MemberStatusTransitionService statusTransitionService;
    @Mock MemberImportAccessPolicy importAccessPolicy;
    @Mock MemberImportRollbackAuditRecorder auditRecorder;
    @Mock AuthorizationService authorizationService;
    @Spy ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @InjectMocks MemberImportRollbackService service;

    @BeforeEach
    void fixture() {
        when(importLogRepository.findById(1L)).thenReturn(Optional.of(
                MemberImportLog.builder().id(1L).importBatchId("B-1").build()));
        when(rollbackRepository.findByImportLogIdAndStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldRejectBlankReasonBeforeAnyWrite() {
        assertThatThrownBy(() -> service.execute(1L, " ")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(authorizationService, rollbackSkipRepository);
    }

    @Test
    void shouldSkipMemberEditedAfterImportInsteadOfOverwritingIt() throws Exception {
        Member imported = member(10L, "Imported", "091");
        Member current = member(10L, "Edited later", "092");
        stubUpdated(imported, current);

        var preview = service.preview(1L);

        assertThat(preview.getWouldRevertUpdatedCount()).isZero();
        assertThat(preview.getSkips()).singleElement()
                .satisfies(skip -> assertThat(skip.getReason()).isEqualTo("MODIFIED_AFTER_IMPORT"));
    }

    @Test
    void shouldRestoreOnlyAnUnchangedImportedVersion() throws Exception {
        Member current = member(10L, "Imported", "091");
        stubUpdated(current, current);

        var preview = service.preview(1L);

        assertThat(preview.getWouldRevertUpdatedCount()).isOne();
        assertThat(preview.getWouldSkipCount()).isZero();
    }

    private void stubUpdated(Member imported, Member current) throws Exception {
        String previous = objectMapper.writeValueAsString(MemberImportFieldSnapshot.of(member(10L, "Before", "090")));
        String after = objectMapper.writeValueAsString(MemberImportFieldSnapshot.of(imported));
        when(batchRowRepository.findByImportLogId(1L)).thenReturn(List.of(MemberImportBatchRow.builder()
                .importLogId(1L).memberId(10L).action(MemberImportBatchRow.Action.UPDATED)
                .previousSnapshot(previous).importedSnapshot(after).build()));
        when(memberRepository.findById(10L)).thenReturn(Optional.of(current));
        when(memberRepository.findAllById(any())).thenReturn(List.of(current));
    }

    private static Member member(Long id, String name, String phone) {
        return Member.builder().id(id).fullName(name).phone(phone)
                .employer(com.waad.tba.modules.employer.entity.Employer.builder().id(7L).build())
                .status(Member.MemberStatus.PENDING).active(false).build();
    }
}
