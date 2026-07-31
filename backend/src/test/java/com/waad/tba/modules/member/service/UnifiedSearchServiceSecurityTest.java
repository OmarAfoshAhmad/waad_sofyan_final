package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.QueryFilterService;
import com.waad.tba.security.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a CRITICAL IDOR found while closing the employer
 * module: GET /unified-search allows EMPLOYER_ADMIN, and the employerId
 * query param was optional with no scope resolution — omitting it (or
 * sending another employer's id) returned name/barcode/card matches across
 * every employer. resolveEmployerScope() now forces it to the caller's own
 * employer.
 */
@ExtendWith(MockitoExtension.class)
class UnifiedSearchServiceSecurityTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private NameSearchService nameSearchService;
    @Mock
    private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Mock
    private RoleService roleService;

    private UnifiedSearchService service;
    private User employerAdmin;

    @BeforeEach
    void setUp() {
        employerAdmin = User.builder().id(1L).username("employer-a-admin")
                .userType("EMPLOYER_ADMIN").employerId(10L).build();

        QueryFilterService queryFilterService = new QueryFilterService(roleService);
        lenient().when(roleService.isEmployerAdmin(employerAdmin)).thenReturn(true);
        AuthorizationService authorizationService = new AuthorizationService(userRepository, roleService,
                null, queryFilterService, null);

        service = new UnifiedSearchService(memberRepository, nameSearchService, authorizationService);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        employerAdmin.getUsername(), null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        lenient().when(userRepository.findByUsername(employerAdmin.getUsername()))
                .thenReturn(Optional.of(employerAdmin));
    }

    @Test
    void employerAdminSearchIgnoresOmittedEmployerIdAndUsesOwnEmployer() {
        when(memberRepository.searchByEmployerId("Ahmed", 10L)).thenReturn(java.util.List.of());

        service.search("Ahmed", null);

        verify(memberRepository).searchByEmployerId("Ahmed", 10L);
    }

    @Test
    void employerAdminSearchIgnoresForeignEmployerIdAndUsesOwnEmployer() {
        when(memberRepository.searchByEmployerId("Ahmed", 10L)).thenReturn(java.util.List.of());

        service.search("Ahmed", 999L);

        verify(memberRepository).searchByEmployerId("Ahmed", 10L);
    }

    @Test
    void shortTextSearchDoesNotHitRepositoryOnLargeMemberTables() {
        service.search("Ah", null);

        verify(memberRepository, never()).searchByEmployerId("Ah", 10L);
        verify(memberRepository, never()).search("Ah");
    }
}
