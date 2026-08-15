package com.waad.tba.modules.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberAccessDeniedException;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
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
    @Mock private MemberFinancialSummaryService financialSummaryService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuditLogService auditLogService;
    @Mock private com.waad.tba.modules.eligibility.service.FamilyEligibilityService familyEligibilityService;
    @Mock private com.waad.tba.modules.member.service.MemberStatusTransitionService statusTransitionService;
    @Mock private com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
    @Mock private MemberEmployerResolver memberEmployerResolver;
    @Mock private MemberQueryAccessPolicy memberQueryAccessPolicy;
    @Mock private com.waad.tba.modules.member.security.MemberCommandAccessPolicy memberCommandAccessPolicy;

    private UnifiedMemberService service;
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
                financialSummaryService,
                jdbcTemplate,
                auditLogService,
                familyEligibilityService,
                statusTransitionService,
                memberPolicyResolver,
                memberEmployerResolver,
                memberQueryAccessPolicy,
                memberCommandAccessPolicy);
    }

    @Test
    void employerAdminCannotReadPhotoPathForAnotherEmployerMember() {
        Member member = member(10L, 2L, "members/photos/secret.jpg");
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));
        doThrow(new MemberAccessDeniedException(MemberOperation.VIEW_DETAILS, "outside scope"))
                .when(memberQueryAccessPolicy).requireMember(MemberOperation.VIEW_DETAILS, 2L);

        assertThrows(MemberAccessDeniedException.class, () -> service.getMemberPhotoPath(10L));
    }

    @Test
    void unrelatedProviderCannotModifyMemberPhoto() {
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member(10L, 2L, "old.jpg")));
        doThrow(new MemberAccessDeniedException(MemberOperation.EDIT_DEMOGRAPHICS, "read only"))
                .when(memberCommandAccessPolicy).require(MemberOperation.EDIT_DEMOGRAPHICS, 2L);

        assertThrows(MemberAccessDeniedException.class,
                () -> service.updateMemberPhoto(10L, "members/photos/replacement.jpg"));

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providerCanReadPhotoForMemberOfAllowedEmployer() {
        when(memberRepository.findById(10L))
                .thenReturn(Optional.of(member(10L, 2L, "members/photos/allowed.jpg")));

        assertEquals("members/photos/allowed.jpg", service.getMemberPhotoPath(10L));
        verify(memberQueryAccessPolicy).requireMember(MemberOperation.VIEW_DETAILS, 2L);
    }

    private Member member(Long id, Long employerId, String photoPath) {
        return Member.builder()
                .id(id)
                .employer(Employer.builder().id(employerId).build())
                .profilePhotoPath(photoPath)
                .build();
    }
}
