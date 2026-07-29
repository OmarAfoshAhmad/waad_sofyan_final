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

    @InjectMocks
    private UnifiedMemberService service;

    private com.waad.tba.modules.rbac.entity.User currentUser;
    private Member member;

    @BeforeEach
    void setUp() {
        currentUser = com.waad.tba.modules.rbac.entity.User.builder()
                .id(1L).username("employer-a-admin").userType("EMPLOYER_ADMIN").employerId(10L).build();
        member = Member.builder().id(500L).build();

        when(authorizationService.getCurrentUser()).thenReturn(currentUser);
        lenient().when(memberRepository.findById(500L)).thenReturn(Optional.of(member));
    }

    @Test
    void updateMemberDeniedWhenCallerCannotAccessMember() {
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.updateMember(500L, new MemberUpdateDto()));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void toggleActiveDeniedWhenCallerCannotAccessMember() {
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.toggleActive(500L, false));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteMemberDeniedWhenCallerCannotAccessMember() {
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.deleteMember(500L));

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
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getMember(500L));
    }

    @Test
    void getDependentsDeniedWhenCallerCannotAccessPrincipal() {
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getDependents(500L));
    }

    @Test
    void countDependentsDeniedWhenCallerCannotAccessPrincipal() {
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.countDependents(500L));
    }

    @Test
    void restoreMemberDeniedWhenCallerCannotAccessMember() {
        when(authorizationService.canAccessMember(currentUser, 500L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.restoreMember(500L));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hardDeleteDeniedWhenCallerIsNotSuperAdminEvenIfServiceIsCalledDirectly() {
        assertThrows(AccessDeniedException.class, () -> service.hardDeleteMember(500L));

        verify(memberRepository, never()).delete(org.mockito.ArgumentMatchers.any(Member.class));
    }

    @Test
    void hardDeleteBlockedWhenMemberHasFinancialOrMedicalFootprint() {
        currentUser.setUserType("SUPER_ADMIN");
        when(memberRepository.findByParentId(500L)).thenReturn(java.util.List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(1L, 0L, 0L, 0L, 0L);

        assertThrows(BusinessRuleException.class, () -> service.hardDeleteMember(500L));

        verify(memberRepository, never()).delete(org.mockito.ArgumentMatchers.any(Member.class));
    }
}
