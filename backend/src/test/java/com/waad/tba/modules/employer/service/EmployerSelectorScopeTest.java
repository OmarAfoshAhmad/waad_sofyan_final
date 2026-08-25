package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.dto.EmployerSelectorDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.mapper.EmployerMapper;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

class EmployerSelectorScopeTest {

    private final EmployerRepository employerRepository = mock(EmployerRepository.class);
    private final EmployerMapper mapper = mock(EmployerMapper.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final EmployerService service = new EmployerService(
            employerRepository,
            mapper,
            mock(ProviderRepository.class),
            mock(MemberRepository.class),
            mock(BenefitPolicyRepository.class),
            authorizationService);

    @Test
    void dataEntryReceivesOnlyItsConfiguredActiveEmployer() {
        User user = user("DATA_ENTRY", 41L);
        Employer employer = Employer.builder().id(41L).name("جهة الاختبار").active(true).build();
        EmployerSelectorDto selector = EmployerSelectorDto.builder()
                .id(41L).code("EMP-41").label("جهة الاختبار").build();
        authenticate(user);
        when(employerRepository.findById(41L)).thenReturn(Optional.of(employer));
        when(mapper.toSelector(employer)).thenReturn(selector);

        assertThat(service.getSelectors()).containsExactly(selector);
        verify(employerRepository, never()).findByActiveTrue();
    }

    @Test
    void employerAdminReceivesOnlyItsConfiguredActiveEmployer() {
        User user = user("EMPLOYER_ADMIN", 52L);
        Employer employer = Employer.builder().id(52L).name("جهة الإدارة").active(true).build();
        EmployerSelectorDto selector = EmployerSelectorDto.builder()
                .id(52L).code("EMP-52").label("جهة الإدارة").build();
        authenticate(user);
        when(employerRepository.findById(52L)).thenReturn(Optional.of(employer));
        when(mapper.toSelector(employer)).thenReturn(selector);

        assertThat(service.getSelectors()).containsExactly(selector);
        verify(employerRepository, never()).findByActiveTrue();
    }

    @Test
    void dataEntryWithoutEmployerFailsWithConfigurationMessage() {
        authenticate(user("DATA_ENTRY", null));

        assertThatThrownBy(service::getSelectors)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("غير مرتبط بجهة عمل");
        verify(employerRepository, never()).findByActiveTrue();
    }

    @Test
    void inactiveConfiguredEmployerFailsClosed() {
        authenticate(user("DATA_ENTRY", 61L));
        Employer inactive = Employer.builder().id(61L).name("جهة موقوفة").active(false).build();
        when(employerRepository.findById(61L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(service::getSelectors)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("غير موجودة أو غير نشطة");
    }

    private static User user(String role, Long employerId) {
        return User.builder()
                .id(900L)
                .username("tester")
                .password("not-used")
                .fullName("Tester")
                .email("tester@example.test")
                .userType(role)
                .employerId(employerId)
                .active(true)
                .build();
    }

    private void authenticate(User user) {
        when(authorizationService.requireCurrentUser()).thenReturn(user);
    }
}
