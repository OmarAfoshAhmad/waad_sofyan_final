package com.waad.tba.modules.member.mapper;

import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for SECTION_02 HIGH finding #9: every member read
 * endpoint returned full nationalNumber and address regardless of caller
 * role, so an external PROVIDER_STAFF user saw the same PII payload as
 * internal staff. UnifiedMemberMapper now masks both fields for provider
 * callers only; internal staff and employer admins are unaffected.
 */
@ExtendWith(MockitoExtension.class)
class UnifiedMemberMapperPiiMaskingTest {

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private UnifiedMemberMapper mapper;

    private Member member() {
        return Member.builder()
                .id(500L)
                .fullName("Ahmed Ali")
                .nationalNumber("1234567890")
                .address("123 Main St, Riyadh")
                .build();
    }

    @Test
    void masksNationalNumberAndAddressForProviderStaff() {
        User providerUser = User.builder().id(9L).userType("PROVIDER_STAFF").providerId(251L).build();
        when(authorizationService.getCurrentUser()).thenReturn(providerUser);
        when(authorizationService.isProvider(providerUser)).thenReturn(true);

        MemberViewDto dto = mapper.toViewDto(member());

        assertThat(dto.getNationalNumber()).isEqualTo("****7890");
        assertThat(dto.getAddress()).isNull();
    }

    @Test
    void doesNotMaskForInternalStaff() {
        User internalUser = User.builder().id(1L).userType("SUPER_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(internalUser);
        when(authorizationService.isProvider(internalUser)).thenReturn(false);

        MemberViewDto dto = mapper.toViewDto(member());

        assertThat(dto.getNationalNumber()).isEqualTo("1234567890");
        assertThat(dto.getAddress()).isEqualTo("123 Main St, Riyadh");
    }

    @Test
    void doesNotMaskForEmployerAdmin() {
        User employerAdmin = User.builder().id(2L).userType("EMPLOYER_ADMIN").employerId(10L).build();
        when(authorizationService.getCurrentUser()).thenReturn(employerAdmin);
        when(authorizationService.isProvider(employerAdmin)).thenReturn(false);

        MemberViewDto dto = mapper.toViewDto(member());

        assertThat(dto.getNationalNumber()).isEqualTo("1234567890");
        assertThat(dto.getAddress()).isEqualTo("123 Main St, Riyadh");
    }
}
