package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.mapper.EmployerMapper;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

/**
 * The employer closure gate: E-01 (no delete erases history), E-02 (archive is
 * a guarded transition, not a flag), and E-04 (a user scoped to one employer
 * cannot read or write another).
 *
 * Written before any fix, and expected to fail where the system does not yet
 * hold. A gate that is written to match what the code already does is not a
 * gate.
 *
 * E-04 is the one that matters most. EmployerSelectorScopeTest already proves
 * `getSelectors()` narrows to the caller's employer -- but the selector is one
 * of eight reads, and it is not the one a browser calls to list employers.
 */
class EmployerScopeClosureGateTest {

    private final EmployerRepository employerRepository = mock(EmployerRepository.class);
    private final EmployerMapper mapper = mock(EmployerMapper.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final BenefitPolicyRepository benefitPolicyRepository = mock(BenefitPolicyRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final com.waad.tba.modules.member.security.MemberAccessScopeResolver scopeResolver =
            mock(com.waad.tba.modules.member.security.MemberAccessScopeResolver.class);
    private final com.waad.tba.modules.member.repository.MemberEmployerAssignmentRepository assignmentRepository =
            mock(com.waad.tba.modules.member.repository.MemberEmployerAssignmentRepository.class);

    private final EmployerService service = new EmployerService(
            employerRepository,
            mapper,
            mock(ProviderRepository.class),
            memberRepository,
            benefitPolicyRepository,
            authorizationService,
            scopeResolver,
            assignmentRepository,
            mock(com.waad.tba.modules.systemadmin.service.AuditLogService.class));

    private User user(String type, Long employerId) {
        User user = new User();
        user.setId(900L);
        user.setUsername("scoped-" + type);
        user.setUserType(type);
        user.setEmployerId(employerId);
        user.setActive(true);
        return user;
    }

    /** What the resolver answers for this caller. */
    private void scopedTo(Long... employerIds) {
        when(scopeResolver.resolve()).thenReturn(
                com.waad.tba.modules.member.security.MemberAccessScope.employers(
                        new java.util.LinkedHashSet<>(List.of(employerIds))));
    }

    private void globalScope() {
        when(scopeResolver.resolve()).thenReturn(
                com.waad.tba.modules.member.security.MemberAccessScope.global());
    }

    private Employer employer(Long id, String code) {
        Employer employer = new Employer();
        employer.setId(id);
        employer.setCode(code);
        employer.setName("جهة " + code);
        employer.setActive(true);
        return employer;
    }

    /** The response DTO is an all-args record-style class; only the id is read here. */
    private com.waad.tba.modules.employer.dto.EmployerResponseDto response(Employer source) {
        return com.waad.tba.modules.employer.dto.EmployerResponseDto.builder()
                .id(source.getId())
                .code(source.getCode())
                .build();
    }

    // ── E-01: no delete erases history ─────────────────────────────────────

    @Test
    @DisplayName("E-01: deleting an employer is refused outright, whoever asks")
    void deleteIsAlwaysRefused() {
        globalScope();
        when(employerRepository.findById(7L)).thenReturn(Optional.of(employer(7L, "A")));

        assertThatThrownBy(() -> service.delete(7L))
                .as("an employer is named by member assignments, policies, claims and ledger rows; "
                        + "removing the row would leave every one of them pointing at nothing")
                .isInstanceOf(BusinessRuleException.class);

        verify(employerRepository, never()).delete(any());
        verify(employerRepository, never()).deleteById(anyLong());
    }

    // ── E-02: archive is guarded ───────────────────────────────────────────

    @Test
    @DisplayName("E-02: an employer with active members cannot be archived")
    void archiveIsBlockedByActiveMembers() {
        globalScope();
        when(employerRepository.findById(7L)).thenReturn(Optional.of(employer(7L, "A")));
        when(assignmentRepository.countActiveMembersAssignedOn(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(3L);
        when(benefitPolicyRepository.countByEmployerIdAndActiveTrue(7L)).thenReturn(0L);

        assertThatThrownBy(() -> service.archive(7L)).isInstanceOf(BusinessRuleException.class);
        verify(employerRepository, never()).save(any());
    }

    @Test
    @DisplayName("E-02: an employer with an active policy cannot be archived")
    void archiveIsBlockedByActivePolicies() {
        globalScope();
        when(employerRepository.findById(7L)).thenReturn(Optional.of(employer(7L, "A")));
        when(assignmentRepository.countActiveMembersAssignedOn(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(0L);
        when(benefitPolicyRepository.countByEmployerIdAndActiveTrue(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.archive(7L)).isInstanceOf(BusinessRuleException.class);
        verify(employerRepository, never()).save(any());
    }

    // ── E-04: scope, on every read and every write ─────────────────────────

    @Test
    @DisplayName("E-04: a scoped user listing employers sees only their own")
    void listingIsScopedToTheCallersEmployer() {
        scopedTo(41L);
        Page<Employer> everyEmployer = new PageImpl<>(List.of(
                employer(41L, "MINE"), employer(42L, "SOMEONE-ELSE")));
        when(employerRepository.searchPage(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
                .thenReturn(everyEmployer);
        when(mapper.toResponse(any())).thenAnswer(invocation -> response(invocation.getArgument(0)));

        service.getPage(Boolean.TRUE, "", PageRequest.of(0, 20));

        // Asserted on the QUERY, not on the page it returned. Filtering a page
        // after the fact leaves its total count describing rows the caller may
        // not see -- "1 of 47 employers" is still a disclosure -- so the
        // narrowing has to reach the database.
        var unscoped = org.mockito.ArgumentCaptor.forClass(Boolean.class);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Collection<Long>> ids =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(employerRepository).searchPage(any(), any(), unscoped.capture(), ids.capture(), any());

        assertThat(unscoped.getValue())
                .as("GET /api/v1/employers is guarded by EMPLOYER_VIEW alone, and DATA_ENTRY holds "
                        + "EMPLOYER_VIEW by default while being scoped to one employer")
                .isFalse();
        assertThat(ids.getValue()).containsExactly(41L);
    }

    @Test
    @DisplayName("E-04: a scoped user cannot read another employer by id")
    void readingAnotherEmployerByIdIsRefused() {
        scopedTo(41L);
        when(employerRepository.findById(42L)).thenReturn(Optional.of(employer(42L, "SOMEONE-ELSE")));

        assertThatThrownBy(() -> service.getById(42L))
                .as("an id in a URL is not authorisation to read what it names")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("E-04: a scoped user cannot archive another employer")
    void archivingAnotherEmployerIsRefused() {
        scopedTo(41L);
        when(employerRepository.findById(42L)).thenReturn(Optional.of(employer(42L, "SOMEONE-ELSE")));
        when(assignmentRepository.countActiveMembersAssignedOn(org.mockito.ArgumentMatchers.eq(42L), any()))
                .thenReturn(0L);
        when(benefitPolicyRepository.countByEmployerIdAndActiveTrue(42L)).thenReturn(0L);

        assertThatThrownBy(() -> service.archive(42L))
                .as("archiving hides an employer from every list in the system; a tenant may not do "
                        + "that to another tenant")
                .isInstanceOf(RuntimeException.class);

        verify(employerRepository, never()).save(any());
    }

    @Test
    @DisplayName("E-04: a super admin is not scoped, and sees everything")
    void aSuperAdminIsNotNarrowed() {
        globalScope();
        Page<Employer> everyEmployer = new PageImpl<>(List.of(
                employer(41L, "A"), employer(42L, "B")));
        when(employerRepository.searchPage(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
                .thenReturn(everyEmployer);
        when(mapper.toResponse(any())).thenAnswer(invocation -> response(invocation.getArgument(0)));

        assertThat(service.getPage(Boolean.TRUE, "", PageRequest.of(0, 20)).getContent())
                .as("narrowing must not become a rule that also narrows the people who administer "
                        + "the whole book")
                .hasSize(2);
    }
}
