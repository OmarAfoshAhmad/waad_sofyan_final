package com.waad.tba.modules.admin.system;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.lenient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression coverage for a MEDIUM finding from SECTION_02's remaining-module
 * audit: resetTestData() performed an unconditional deleteAll() on core
 * business tables with no environment safeguard — only a SUPER_ADMIN role
 * check. A single misdirected request against a production database would
 * have been irreversible. requireNonProductionProfile() now fails closed
 * when spring.profiles.active is "prod"/"production".
 */
@ExtendWith(MockitoExtension.class)
class SystemAdminServiceProductionGuardTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private VisitRepository visitRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private SystemAdminService service;

    @Test
    void resetTestDataBlockedInProdProfile() {
        ReflectionTestUtils.setField(service, "activeProfile", "prod");

        assertThatThrownBy(service::resetTestData).isInstanceOf(BusinessRuleException.class);

        verify(claimRepository, never()).deleteAll();
        verify(memberRepository, never()).deleteAll();
    }

    @Test
    void resetTestDataBlockedForCommaSeparatedProfileList() {
        // spring.profiles.active can be "prod,metrics" — the whole raw
        // string must not be compared literally against "prod", or a
        // multi-profile activation silently bypasses the guard.
        ReflectionTestUtils.setField(service, "activeProfile", "prod,metrics");

        assertThatThrownBy(service::resetTestData).isInstanceOf(BusinessRuleException.class);

        verify(claimRepository, never()).deleteAll();
    }

    @Test
    void resetTestDataAllowedInDevProfile() {
        ReflectionTestUtils.setField(service, "activeProfile", "dev");
        lenient().when(securityAuditService.logSecurityEvent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        service.resetTestData();

        verify(claimRepository).deleteAll();
        verify(memberRepository).deleteAll();
    }
}
