package com.waad.tba.modules.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * FamilyEligibilityService is the single shared orchestrator for "evaluate
 * this family's eligibility" -- used both by this module's own memberId-
 * based endpoint (checkFamilyEligibility) and by
 * UnifiedMemberService.checkFamilyEligibility (barcode-based, see
 * UnifiedMemberServiceFamilyEligibilityTest for the delegation side).
 * Previously each had its own independent copy of this logic; this is the
 * first dedicated test coverage for the shared implementation itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyEligibilityServiceTest {

    @Mock private EligibilityEngineService eligibilityService;
    @Mock private MemberRepository memberRepository;

    @InjectMocks
    private FamilyEligibilityService service;

    @Test
    void resolveFamily_principalId_returnsPrincipalAndDependents() {
        Member dependent = Member.builder().id(2L).build();
        Member principal = Member.builder().id(1L).dependents(List.of(dependent)).build();

        var family = service.resolveFamily(principal);

        assertThat(family.principal().getId()).isEqualTo(1L);
        assertThat(family.dependents()).extracting(Member::getId).containsExactly(2L);
        assertThat(family.all()).extracting(Member::getId).containsExactly(1L, 2L);
    }

    @Test
    void resolveFamily_dependentId_resolvesToPrincipalsFamilyNotItsOwn() {
        Member principal = Member.builder().id(1L).build();
        Member dependent = Member.builder().id(2L).parent(principal).build();
        principal.setDependents(List.of(dependent));

        var family = service.resolveFamily(dependent);

        assertThat(family.principal().getId()).isEqualTo(1L);
        assertThat(family.dependents()).extracting(Member::getId).containsExactly(2L);
    }

    @Test
    void evaluateFamily_callsTheEngineOncePerFamilyMemberWithTheGivenServiceDate() {
        Member principal = Member.builder().id(1L).build();
        Member dependent = Member.builder().id(2L).parent(principal).build();
        LocalDate serviceDate = LocalDate.of(2026, 3, 1);

        when(eligibilityService.checkEligibility(any(EligibilityCheckRequest.class)))
                .thenReturn(EligibilityResult.eligible("req", null, 1L, 1));

        Map<Long, EligibilityResult> results = service.evaluateFamily(principal, List.of(dependent), serviceDate);

        assertThat(results).containsOnlyKeys(1L, 2L);
        org.mockito.ArgumentCaptor<EligibilityCheckRequest> captor =
                org.mockito.ArgumentCaptor.forClass(EligibilityCheckRequest.class);
        org.mockito.Mockito.verify(eligibilityService, org.mockito.Mockito.times(2))
                .checkEligibility(captor.capture());
        assertThat(captor.getAllValues()).extracting(EligibilityCheckRequest::getServiceDate)
                .containsOnly(serviceDate);
        assertThat(captor.getAllValues()).extracting(EligibilityCheckRequest::getMemberId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void evaluateFamily_engineFailureForOneMember_failsClosedForThatMemberOnlyAndContinuesTheRest() {
        Member principal = Member.builder().id(1L).build();
        Member dependent = Member.builder().id(2L).parent(principal).build();

        when(eligibilityService.checkEligibility(any(EligibilityCheckRequest.class)))
                .thenAnswer(invocation -> {
                    EligibilityCheckRequest req = invocation.getArgument(0);
                    if (req.getMemberId().equals(1L)) {
                        throw new RuntimeException("engine down");
                    }
                    return EligibilityResult.eligible("req", null, 1L, 1);
                });

        Map<Long, EligibilityResult> results = service.evaluateFamily(principal, List.of(dependent), LocalDate.now());

        assertThat(results.get(1L).isEligible()).isFalse();
        assertThat(results.get(1L).getReasons().get(0).getCode()).isEqualTo("SYSTEM_ERROR");
        assertThat(results.get(2L).isEligible()).isTrue();
    }

    @Test
    void checkFamilyEligibility_usesTheCallerSuppliedServiceDateNotTodaysDate() {
        Member principal = Member.builder().id(1L).build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(principal));
        LocalDate backdated = LocalDate.of(2020, 6, 1);

        when(eligibilityService.checkEligibility(any(EligibilityCheckRequest.class)))
                .thenReturn(EligibilityResult.eligible("req", null, 1L, 1));

        service.checkFamilyEligibility(1L, backdated);

        org.mockito.ArgumentCaptor<EligibilityCheckRequest> captor =
                org.mockito.ArgumentCaptor.forClass(EligibilityCheckRequest.class);
        org.mockito.Mockito.verify(eligibilityService).checkEligibility(captor.capture());
        assertThat(captor.getValue().getServiceDate()).isEqualTo(backdated);
    }

    @Test
    void checkFamilyEligibility_summaryReflectsPartialEligibility() {
        Member principal = Member.builder().id(1L).build();
        Member dependent = Member.builder().id(2L).parent(principal).build();
        principal.setDependents(List.of(dependent));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(principal));

        when(eligibilityService.checkEligibility(any(EligibilityCheckRequest.class)))
                .thenAnswer(invocation -> {
                    EligibilityCheckRequest req = invocation.getArgument(0);
                    return req.getMemberId().equals(1L)
                            ? EligibilityResult.eligible("req", null, 1L, 1)
                            : EligibilityResult.notEligible("req", null,
                                    List.of(EligibilityResult.ReasonDetail.builder()
                                            .code("LIMIT_EXHAUSTED").messageAr("تم استنفاد السقف").hardFailure(true).build()),
                                    1L, 1);
                });

        FamilyEligibilityResponse response = service.checkFamilyEligibility(1L, LocalDate.now());

        assertThat(response.getSummary().getFamilyStatus()).isEqualTo("PARTIAL");
        assertThat(response.getSummary().getEligibleCount()).isEqualTo(1);
        assertThat(response.getSummary().getIneligibleCount()).isEqualTo(1);
    }
}
