package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.AuthorizedMemberScope;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for the unified-search tenant boundary. */
@ExtendWith(MockitoExtension.class)
class UnifiedSearchServiceSecurityTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberQueryAccessPolicy queryAccessPolicy;
    @Mock private AuthorizedMemberScope authorizedScope;

    private UnifiedSearchService service;

    @BeforeEach
    void setUp() {
        service = new UnifiedSearchService(memberRepository, queryAccessPolicy);
    }

    @Test
    void omittedEmployerIsResolvedByPolicyAndAppliedInsidePagedSqlQuery() {
        when(queryAccessPolicy.requireListing(MemberOperation.SEARCH, null)).thenReturn(authorizedScope);
        when(memberRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search("Ahmed", null);

        verify(queryAccessPolicy).requireListing(MemberOperation.SEARCH, null);
        verify(memberRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(memberRepository, never()).search(any(String.class));
        verify(memberRepository, never()).searchByEmployerId(any(String.class), any(Long.class));
    }

    @Test
    void requestedEmployerIsNeverUsedWithoutPolicyApproval() {
        when(queryAccessPolicy.requireListing(MemberOperation.SEARCH, 999L)).thenReturn(authorizedScope);
        when(memberRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search("Ahmed", 999L);

        verify(queryAccessPolicy).requireListing(MemberOperation.SEARCH, 999L);
        verify(memberRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shortTextStillRequiresAuthorizationButDoesNotQueryLargeMemberTable() {
        when(queryAccessPolicy.requireListing(MemberOperation.SEARCH, null)).thenReturn(authorizedScope);

        service.search("Ah", null);

        verify(queryAccessPolicy).requireListing(MemberOperation.SEARCH, null);
        verify(memberRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void exactResultOutsideResolvedScopeIsRejectedThroughPolicy() {
        Member member = Member.builder().id(7L).barcode("123e4567-e89b-12d3-a456-426614174000").build();
        when(queryAccessPolicy.requireListing(MemberOperation.SEARCH, null)).thenReturn(authorizedScope);
        when(memberRepository.findByBarcode(member.getBarcode())).thenReturn(java.util.Optional.of(member));
        when(authorizedScope.covers(null)).thenReturn(false);
        when(queryAccessPolicy.requireMember(MemberOperation.SEARCH, null))
                .thenThrow(new IllegalStateException("denied"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.search(member.getBarcode(), null));

        verify(queryAccessPolicy).requireMember(MemberOperation.SEARCH, null);
    }

    @Test
    void cardSearchUsesTheStableSuffixAcrossIssuanceYears() {
        assertThat(UnifiedSearchService.stableCardNumberPart("JFZ202533933")).isEqualTo("33933");
        assertThat(UnifiedSearchService.stableCardNumberPart("JFZ202633933")).isEqualTo("33933");
        assertThat(UnifiedSearchService.stableCardNumberPart("JFZ-2026-33933")).isEqualTo("33933");
        assertThat(UnifiedSearchService.stableCardNumberPart("33933")).isEqualTo("33933");
    }

    @Test
    void numericCardSuffixIsSearchedBeforeAnInternalMemberId() {
        when(queryAccessPolicy.requireListing(MemberOperation.SEARCH, null)).thenReturn(authorizedScope);
        when(memberRepository.findByCardNumberWithDetails("33933")).thenReturn(java.util.Optional.empty());
        when(memberRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(
                        Member.builder().id(7L).cardNumber("JFZ202533933").build())));

        var results = service.search("33933", null);

        assertThat(results).singleElement().extracting(com.waad.tba.modules.member.dto.MemberSearchDto::getCardNumber)
                .isEqualTo("JFZ202533933");
        verify(memberRepository, never()).findById(33933L);
    }
}
