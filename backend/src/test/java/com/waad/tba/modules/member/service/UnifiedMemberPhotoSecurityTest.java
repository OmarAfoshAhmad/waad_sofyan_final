package com.waad.tba.modules.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.service.ProviderService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class UnifiedMemberPhotoSecurityTest {

    @Mock private MemberRepository memberRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private BenefitPolicyRepository benefitPolicyRepository;
    @Mock private BarcodeGeneratorService barcodeGenerator;
    @Mock private CardNumberGeneratorService cardNumberGenerator;
    @Mock private UnifiedMemberMapper mapper;
    @Mock private AuthorizationService authorizationService;
    @Mock private ProviderService providerService;
    @Mock private MemberFinancialSummaryService financialSummaryService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuditLogService auditLogService;

    private UnifiedMemberService service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new UnifiedMemberService(
                memberRepository,
                employerRepository,
                benefitPolicyRepository,
                barcodeGenerator,
                cardNumberGenerator,
                mapper,
                authorizationService,
                providerService,
                financialSummaryService,
                jdbcTemplate,
                auditLogService);
        currentUser = mock(User.class);
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void employerAdminCannotReadPhotoPathForAnotherEmployerMember() {
        Member member = member(10L, 2L, "members/photos/secret.jpg");
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));
        when(authorizationService.canAccessMember(currentUser, 10L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getMemberPhotoPath(10L));
    }

    @Test
    void unrelatedProviderCannotModifyMemberPhoto() {
        User providerUser = User.builder().userType("PROVIDER_STAFF").providerId(20L).build();
        when(authorizationService.requireCurrentUser()).thenReturn(providerUser);
        when(authorizationService.isProvider(providerUser)).thenReturn(true);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member(10L, 2L, "old.jpg")));
        when(providerService.getAllowedEmployerIds(20L)).thenReturn(List.of(3L));

        assertThrows(AccessDeniedException.class,
                () -> service.updateMemberPhoto(10L, "members/photos/replacement.jpg"));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providerCanReadPhotoForMemberOfAllowedEmployer() {
        User providerUser = User.builder().userType("PROVIDER_STAFF").providerId(20L).build();
        when(authorizationService.requireCurrentUser()).thenReturn(providerUser);
        when(authorizationService.isProvider(providerUser)).thenReturn(true);
        when(memberRepository.findById(10L))
                .thenReturn(Optional.of(member(10L, 2L, "members/photos/allowed.jpg")));
        when(providerService.getAllowedEmployerIds(20L)).thenReturn(List.of(2L));

        assertEquals("members/photos/allowed.jpg", service.getMemberPhotoPath(10L));
    }

    private Member member(Long id, Long employerId, String photoPath) {
        return Member.builder()
                .id(id)
                .employer(Employer.builder().id(employerId).build())
                .profilePhotoPath(photoPath)
                .build();
    }
}
