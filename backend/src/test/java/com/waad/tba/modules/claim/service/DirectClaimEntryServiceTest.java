package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.claim.api.ClaimApiMapper;
import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.DirectClaimEntryRequest;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.MemberEmployerAssignment;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.member.service.MemberDatedContext;
import com.waad.tba.modules.visit.dto.VisitCreateDto;
import com.waad.tba.modules.visit.dto.VisitResponseDto;
import com.waad.tba.modules.visit.service.VisitService;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.entity.Claim;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DirectClaimEntryServiceTest {

    @Mock VisitService visitService;
    @Mock ClaimService claimService;
    @Mock ClaimApiMapper claimApiMapper;
    @Mock MemberContextResolver memberContextResolver;
    @Mock ClaimRepository claimRepository;
    @Mock ClaimMapper claimMapper;
    @Mock JdbcTemplate jdbcTemplate;
    // Real, not mocked: the fingerprint is a pure function of the request, and a
    // stubbed one would let a broken fingerprint pass this suite unnoticed.
    @Spy DirectClaimEntryFingerprint fingerprints = new DirectClaimEntryFingerprint();
    // Real too, over a mock repository: the member/employer check it performs
    // here never reaches the provider network table.
    @Spy ClaimProviderEmployerAccessService employerAccess = new ClaimProviderEmployerAccessService(
            org.mockito.Mockito.mock(
                    com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository.class));
    @InjectMocks DirectClaimEntryService service;

    @Test
    void createsVisitThenPassesItsIdToTheCanonicalClaimService() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        DirectClaimEntryRequest request = request(date);
        ClaimCreateDto mapped = ClaimCreateDto.builder()
                .lines(List.of(ClaimLineDto.builder().quantity(1).build())).build();
        ClaimViewDto expected = ClaimViewDto.builder().id(55L).build();
        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(context(date, 9L));
        when(visitService.create(any())).thenReturn(VisitResponseDto.builder().id(44L).build());
        when(claimApiMapper.toCreateDto(request.getClaim())).thenReturn(mapped);
        when(claimService.createClaim(eq(mapped), any())).thenReturn(expected);
        when(claimRepository.findById(55L)).thenReturn(java.util.Optional.of(new com.waad.tba.modules.claim.entity.Claim()));

        assertThat(service.create(request)).isSameAs(expected);

        ArgumentCaptor<VisitCreateDto> visitCaptor = ArgumentCaptor.forClass(VisitCreateDto.class);
        verify(visitService).create(visitCaptor.capture());
        assertThat(visitCaptor.getValue().getVisitDate()).isEqualTo(date);
        assertThat(mapped.getVisitId()).isEqualTo(44L);
        verify(claimService).createClaim(eq(mapped), any());
    }

    @Test
    void rejectsEmployerMismatchBeforeWritingEitherEntity() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        DirectClaimEntryRequest request = request(date);
        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(context(date, 10L));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يتبع جهة عمل الدفعة");

        verify(visitService, never()).create(any());
        verify(claimService, never()).createClaim(any(), any());
    }

    @Test
    void replayingTheSameCommandReturnsTheOriginalClaimWithoutAnotherVisit() {
        LocalDate date = LocalDate.of(2025, 8, 12);
        DirectClaimEntryRequest request = request(date);
        ClaimCreateDto mapped = ClaimCreateDto.builder()
                .lines(List.of(ClaimLineDto.builder().quantity(1).build())).build();
        ClaimViewDto expected = ClaimViewDto.builder().id(55L).build();
        Claim persisted = Claim.builder().id(55L).build();
        AtomicReference<Claim> keyedClaim = new AtomicReference<>();

        when(claimRepository.findByDirectEntryIdempotencyKey("entry-test-key"))
                .thenAnswer(ignored -> java.util.Optional.ofNullable(keyedClaim.get()));
        when(memberContextResolver.resolveForOrFail(7L, date)).thenReturn(context(date, 9L));
        when(visitService.create(any())).thenReturn(VisitResponseDto.builder().id(44L).build());
        when(claimApiMapper.toCreateDto(request.getClaim())).thenReturn(mapped);
        when(claimService.createClaim(eq(mapped), any())).thenReturn(expected);
        when(claimRepository.findById(55L)).thenReturn(java.util.Optional.of(persisted));
        when(claimRepository.save(any())).thenAnswer(invocation -> {
            Claim saved = invocation.getArgument(0);
            keyedClaim.set(saved);
            return saved;
        });
        when(claimMapper.toViewDto(persisted)).thenReturn(expected);

        assertThat(service.create(request)).isSameAs(expected);
        assertThat(service.create(request)).isSameAs(expected);

        verify(visitService, times(1)).create(any());
        verify(claimService, times(1)).createClaim(eq(mapped), any());
        assertThat(persisted.getDirectEntryIdempotencyKey()).isEqualTo("entry-test-key");
        assertThat(persisted.getDirectEntryRequestFingerprint()).hasSize(64);
    }

    private DirectClaimEntryRequest request(LocalDate date) {
        CreateClaimRequest claim = CreateClaimRequest.builder()
                .memberId(7L).providerId(8L).serviceDate(date).doctorName("طبيب")
                .lines(List.of(CreateClaimRequest.ClaimLineRequest.builder().quantity(1).build()))
                .build();
        return DirectClaimEntryRequest.builder().idempotencyKey("entry-test-key").employerId(9L).claim(claim).build();
    }

    private MemberDatedContext context(LocalDate date, Long employerId) {
        return new MemberDatedContext(7L, date,
                MemberEmployerAssignment.builder().id(1L).build(),
                Employer.builder().id(employerId).build(),
                MemberPolicyAssignment.builder().id(2L).build(),
                BenefitPolicy.builder().id(3L).build());
    }
}
