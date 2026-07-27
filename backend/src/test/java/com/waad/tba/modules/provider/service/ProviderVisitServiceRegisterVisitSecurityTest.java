package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.dto.ProviderVisitRegisterRequest;
import com.waad.tba.modules.provider.dto.ProviderVisitResponse;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a CRITICAL IDOR found while closing the provider &
 * contracts module: registerVisit() fell back to the client-supplied
 * request.providerId whenever the caller had no providerId bound, regardless
 * of role. A misconfigured/unlinked PROVIDER_STAFF account (or any other
 * authenticated user reaching this code path) could register a visit — and
 * the claims that follow it — against an arbitrary provider. The fallback is
 * now restricted to a genuine SUPER_ADMIN override.
 */
@ExtendWith(MockitoExtension.class)
class ProviderVisitServiceRegisterVisitSecurityTest {

    @Mock
    private VisitRepository visitRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProviderVisitService service;

    @BeforeEach
    void setUp() {
        Member member = Member.builder().id(10L).status(Member.MemberStatus.ACTIVE).build();
        lenient().when(memberRepository.findById(10L)).thenReturn(Optional.of(member));
    }

    @Test
    void unlinkedProviderStaffCannotFallBackToClientSuppliedProviderId() {
        User unlinkedProviderStaff = User.builder().id(1L).username("unlinked-provider-staff")
                .userType("PROVIDER_STAFF").providerId(null).build();
        when(userRepository.findByUsername("unlinked-provider-staff"))
                .thenReturn(Optional.of(unlinkedProviderStaff));

        ProviderVisitRegisterRequest request = new ProviderVisitRegisterRequest();
        request.setMemberId(10L);
        request.setProviderId(999L); // attacker-supplied target provider

        ProviderVisitResponse response = service.registerVisit(request, "unlinked-provider-staff");

        assertThat(response.getSuccess()).isFalse();
    }

    @Test
    void superAdminOverrideMayStillSpecifyProviderId() {
        User superAdmin = User.builder().id(2L).username("superadmin")
                .userType("SUPER_ADMIN").providerId(null).build();
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdmin));
        when(providerRepository.findById(999L))
                .thenReturn(Optional.of(com.waad.tba.modules.provider.entity.Provider.builder().id(999L).build()));
        lenient().when(visitRepository.findByMemberIdAndProviderIdAndStatusIn(
                        org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(999L),
                        org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Collections.emptyList());
        lenient().when(visitRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ProviderVisitRegisterRequest request = new ProviderVisitRegisterRequest();
        request.setMemberId(10L);
        request.setProviderId(999L);

        ProviderVisitResponse response = service.registerVisit(request, "superadmin");

        assertThat(response.getSuccess()).isTrue();
    }
}
