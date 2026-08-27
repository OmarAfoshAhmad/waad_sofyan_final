package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.MemberUpdateDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for SECTION_02 CRITICAL finding #4: updateMember,
 * toggleActive and deleteMember were gated only by role
 * (SUPER_ADMIN/EMPLOYER_ADMIN), not by employer ownership — an EMPLOYER_ADMIN
 * for Employer A could mutate/deactivate/delete Employer B's members by ID.
 * The fix adds the same canAccessMember(currentUser, id) check already used
 * for member search/photo access.
 */
@ExtendWith(MockitoExtension.class)
class UnifiedMemberServiceSecurityTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private com.waad.tba.modules.member.security.MemberCommandAccessPolicy commandAccessPolicy;

    @Mock
    private com.waad.tba.modules.member.security.MemberQueryAccessPolicy queryAccessPolicy;

    private UnifiedMemberService service;

    private com.waad.tba.modules.rbac.entity.User currentUser;
    private Member member;

    @BeforeEach
    void setUp() {
        currentUser = com.waad.tba.modules.rbac.entity.User.builder()
                .id(1L).username("employer-a-admin").userType("EMPLOYER_ADMIN").employerId(10L).build();
        member = Member.builder().id(500L).build();

        lenient().when(authorizationService.getCurrentUser()).thenReturn(currentUser);
        lenient().when(memberRepository.findById(500L)).thenReturn(Optional.of(member));

        // Real (not mocked) MemberStatusTransitionService, backed by mocked
        // repositories: the SUPER_ADMIN and financial-footprint checks this
        // test class exists to cover now live there, not in
        // UnifiedMemberService itself -- a mock would make these tests
        // exercise nothing.
        com.waad.tba.modules.member.service.MemberStatusTransitionService statusTransitionService =
                new com.waad.tba.modules.member.service.MemberStatusTransitionService(
                        memberRepository,
                        org.mockito.Mockito.mock(com.waad.tba.modules.member.repository.MemberStatusHistoryRepository.class),
                        org.mockito.Mockito.mock(com.waad.tba.modules.member.repository.MemberHardDeleteAuditRepository.class),
                        org.mockito.Mockito.mock(com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository.class),
                        jdbcTemplate,
                        new com.waad.tba.modules.member.service.MemberPolicyResolver(
                                org.mockito.Mockito.mock(com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository.class),
                                org.mockito.Mockito.mock(com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository.class),
                                memberRepository,
                                org.mockito.Mockito.mock(MemberEmployerResolver.class)));

        service = new UnifiedMemberService(
                memberRepository,
                org.mockito.Mockito.mock(com.waad.tba.modules.employer.repository.EmployerRepository.class),
                org.mockito.Mockito.mock(com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository.class),
                org.mockito.Mockito.mock(BarcodeGeneratorService.class),
                org.mockito.Mockito.mock(CardNumberGeneratorService.class),
                org.mockito.Mockito.mock(com.waad.tba.modules.member.mapper.UnifiedMemberMapper.class),
                authorizationService,
                org.mockito.Mockito.mock(com.waad.tba.modules.rbac.permission.EffectivePermissionService.class),
                org.mockito.Mockito.mock(MemberFinancialSummaryService.class),
                jdbcTemplate,
                org.mockito.Mockito.mock(com.waad.tba.modules.systemadmin.service.AuditLogService.class),
                org.mockito.Mockito.mock(com.waad.tba.modules.eligibility.service.FamilyEligibilityService.class),
                statusTransitionService,
                new com.waad.tba.modules.member.service.MemberPolicyResolver(
                        org.mockito.Mockito.mock(com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository.class),
                        org.mockito.Mockito.mock(com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository.class),
                        memberRepository,
                        org.mockito.Mockito.mock(MemberEmployerResolver.class)),
                org.mockito.Mockito.mock(MemberEmployerResolver.class),
                queryAccessPolicy,
                commandAccessPolicy);
    }

    @Test
    void updateMemberDeniedWhenCallerCannotAccessMember() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(commandAccessPolicy)
                .require(com.waad.tba.modules.member.security.MemberOperation.EDIT_DEMOGRAPHICS, null);

        assertThrows(AccessDeniedException.class,
                () -> service.updateMember(500L, new MemberUpdateDto()));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void toggleActiveDeniedWhenCallerCannotAccessMember() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(commandAccessPolicy)
                .require(com.waad.tba.modules.member.security.MemberOperation.CHANGE_STATUS, null);

        assertThrows(AccessDeniedException.class, () -> service.toggleActive(500L, false, "reason"));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteMemberDeniedWhenCallerCannotAccessMember() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(commandAccessPolicy)
                .require(com.waad.tba.modules.member.security.MemberOperation.TERMINATE, null);

        assertThrows(AccessDeniedException.class, () -> service.deleteMember(500L));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bulkTerminationAuthorizesTheWholeSelectionBeforeAnyWrite() {
        Member second = Member.builder().id(501L).build();
        when(memberRepository.findById(501L)).thenReturn(Optional.of(second));
        org.mockito.Mockito.doThrow(new AccessDeniedException("one member is outside scope"))
                .when(commandAccessPolicy)
                .requireBulk(eq(com.waad.tba.modules.member.security.MemberOperation.BULK_OPERATION),
                        anyCollection());

        assertThrows(AccessDeniedException.class,
                () -> service.bulkTerminateMemberships(java.util.List.of(500L, 501L)));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Follow-up closure round: getMember, getDependents, countDependents and
    // restoreMember had no ownership check at all (unlike their siblings
    // above), letting any EMPLOYER_ADMIN read/restore another employer's
    // member by ID. Same canAccessMember() pattern applied.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void getMemberDeniedWhenCallerCannotAccessMember() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(queryAccessPolicy)
                .requireMember(com.waad.tba.modules.member.security.MemberOperation.VIEW_DETAILS, null);

        assertThrows(AccessDeniedException.class, () -> service.getMember(500L));
    }

    @Test
    void getDependentsDeniedWhenCallerCannotAccessPrincipal() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(queryAccessPolicy)
                .requireMember(com.waad.tba.modules.member.security.MemberOperation.VIEW_DETAILS, null);

        assertThrows(AccessDeniedException.class, () -> service.getDependents(500L));
    }

    @Test
    void countDependentsDeniedWhenCallerCannotAccessPrincipal() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(queryAccessPolicy)
                .requireMember(com.waad.tba.modules.member.security.MemberOperation.VIEW_DETAILS, null);

        assertThrows(AccessDeniedException.class, () -> service.countDependents(500L));
    }

    @Test
    void restoreMemberDeniedWhenCallerCannotAccessMember() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied")).when(commandAccessPolicy)
                .require(com.waad.tba.modules.member.security.MemberOperation.REINSTATE, null);

        assertThrows(AccessDeniedException.class, () -> service.restoreMember(500L, "سبب الاستعادة"));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hardDeleteDeniedWhenCallerIsNotSuperAdminEvenIfServiceIsCalledDirectly() {
        assertThrows(AccessDeniedException.class, () -> service.hardDeleteMember(500L, "reason"));

        verify(memberRepository, never()).delete(org.mockito.ArgumentMatchers.any(Member.class));
    }

    @Test
    void hardDeleteBlockedWhenMemberHasFinancialOrMedicalFootprint() {
        currentUser.setUserType("SUPER_ADMIN");
        when(memberRepository.findByParentId(500L)).thenReturn(java.util.List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(1L, 0L, 0L, 0L, 0L);

        assertThrows(BusinessRuleException.class, () -> service.hardDeleteMember(500L, "reason"));

        verify(memberRepository, never()).delete(org.mockito.ArgumentMatchers.any(Member.class));
    }
}
