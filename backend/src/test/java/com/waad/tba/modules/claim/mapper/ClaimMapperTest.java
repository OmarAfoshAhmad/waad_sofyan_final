package com.waad.tba.modules.claim.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.service.EffectiveProviderContractResolver;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;

/**
 * Regression test for the double-derivation bug: {@code toViewDto} used to
 * recompute the contract discount amount from requestedAmount/patientCoPay/
 * appliedDiscountPercent instead of reading the persisted, validated
 * Claim.companyDiscountAmount — silently ignoring refusedAmount and the
 * discount-timing flag, and drifting from the value the entity's own
 * @PrePersist/@PreUpdate identity check already enforces.
 */
@ExtendWith(MockitoExtension.class)
class ClaimMapperTest {

    @Mock
    private ProviderContractService providerContractService;
    @Mock
    private BenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private MedicalCategoryRepository medicalCategoryRepository;
    @Mock
    private MedicalServiceRepository medicalServiceRepository;
    @Mock
    private ProviderContractPricingItemRepository pricingItemRepository;
    @Mock
    private EffectiveProviderContractResolver effectiveContractResolver;
    @Mock
    private ClaimBatchRepository claimBatchRepository;
    @Mock
    private com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
    @Mock
    private com.waad.tba.modules.claim.service.CoverageEngineService coverageEngineService;
    @Mock
    private com.waad.tba.security.AuthorizationService authorizationService;
    @Mock
    private com.waad.tba.modules.claim.service.finance.ClaimFinancialAdjudicationService financialAdjudicationService;
    @Mock
    private com.waad.tba.modules.claim.service.finance.ClaimFinancialInvariantGuard claimFinancialInvariantGuard;

    @InjectMocks
    private ClaimMapper mapper;

    /**
     * Regression test for P0 (historical policy snapshot, V216): toEntity used
     * to resolve the member's dated employer/policy context only to authorize
     * the request, then discard it -- ClaimService.createClaim never wrote
     * policy_id/policy_assignment_id/employer_assignment_id onto the Claim.
     * Any later re-resolution of the same dated question could legitimately
     * answer differently after the member's assignment history changed
     * (transfer, mid-term policy change), silently reattributing an old
     * claim to a different policy/employer than the one that actually
     * authorized it.
     */
    @Test
    void toEntityCapturesTheResolvedDatedContextAsAnImmutableSnapshot() {
        com.waad.tba.modules.member.entity.Member member = com.waad.tba.modules.member.entity.Member.builder()
                .id(7L).build();
        com.waad.tba.modules.visit.entity.Visit visit = com.waad.tba.modules.visit.entity.Visit.builder()
                .id(44L)
                .member(member)
                .visitType(com.waad.tba.modules.visit.entity.VisitType.OUTPATIENT)
                .build();
        com.waad.tba.modules.provider.entity.Provider provider = com.waad.tba.modules.provider.entity.Provider.builder()
                .id(8L).name("مزود الاختبار").build();

        com.waad.tba.modules.providercontract.entity.ProviderContract contract =
                com.waad.tba.modules.providercontract.entity.ProviderContract.builder().id(501L).build();
        com.waad.tba.modules.providercontract.entity.ProviderContractTerm terms =
                com.waad.tba.modules.providercontract.entity.ProviderContractTerm.builder()
                        .id(9001L).discountPercent(new BigDecimal("5.00")).discountBeforeRejection(true).build();
        when(effectiveContractResolver.resolve(eq(8L), any(), any()))
                .thenReturn(new com.waad.tba.modules.providercontract.service.EffectiveProviderContractResolver.ResolvedContract(
                        contract, terms));
        when(coverageEngineService.evaluateLine(any(), any(), any()))
                .thenReturn(com.waad.tba.modules.claim.dto.engine.CoverageResult.builder()
                        .serviceCode("GEN-MEDICAL-SERVICE").serviceName("خدمة طبية")
                        .effectiveUnitPrice(new BigDecimal("100.00")).effectiveTotal(new BigDecimal("100.00"))
                        .companyShare(new BigDecimal("100.00")).patientShare(BigDecimal.ZERO)
                        .build());

        LocalDate serviceDate = LocalDate.of(2026, 3, 1);
        com.waad.tba.modules.member.service.MemberDatedContext datedContext =
                new com.waad.tba.modules.member.service.MemberDatedContext(
                        7L, serviceDate,
                        com.waad.tba.modules.member.entity.MemberEmployerAssignment.builder().id(301L).build(),
                        Employer.builder().id(20L).build(),
                        com.waad.tba.modules.member.entity.MemberPolicyAssignment.builder().id(302L).build(),
                        com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.builder().id(303L).build());

        com.waad.tba.modules.claim.dto.ClaimCreateDto dto = com.waad.tba.modules.claim.dto.ClaimCreateDto.builder()
                .serviceDate(serviceDate)
                .claimContextCode("OUTPATIENT")
                .lines(List.of(com.waad.tba.modules.claim.dto.ClaimLineDto.builder()
                        .serviceCode("GEN-MEDICAL-SERVICE").unitPrice(new BigDecimal("100.00")).quantity(1)
                        .build()))
                .build();

        Claim claim = mapper.toEntity(dto, visit, provider, null, null, datedContext);

        assertThat(claim.getPolicyId()).isEqualTo(303L);
        assertThat(claim.getPolicyAssignmentId()).isEqualTo(302L);
        assertThat(claim.getEmployerAssignmentId()).isEqualTo(301L);
    }

    @Test
    void toEntityFailsClosedWhenNoDatedContextWasResolved() {
        // Every real caller resolves this via MemberContextResolver
        // #resolveForOrFail first, which throws rather than returning null.
        // A null datedMemberContext here can only mean a caller regressed --
        // toEntity must refuse immediately, not silently build a claim with
        // historicalContextStatus=RESOLVED and all three snapshot columns
        // null (which V219's CHECK constraint would then reject at INSERT
        // with a far less legible database error).
        com.waad.tba.modules.member.entity.Member member = com.waad.tba.modules.member.entity.Member.builder()
                .id(7L).build();
        com.waad.tba.modules.visit.entity.Visit visit = com.waad.tba.modules.visit.entity.Visit.builder()
                .id(44L)
                .member(member)
                .visitType(com.waad.tba.modules.visit.entity.VisitType.OUTPATIENT)
                .build();
        com.waad.tba.modules.provider.entity.Provider provider = com.waad.tba.modules.provider.entity.Provider.builder()
                .id(8L).name("مزود الاختبار").build();
        com.waad.tba.modules.claim.dto.ClaimCreateDto dto = com.waad.tba.modules.claim.dto.ClaimCreateDto.builder()
                .serviceDate(LocalDate.of(2026, 3, 1))
                .claimContextCode("OUTPATIENT")
                .lines(List.of())
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> mapper.toEntity(dto, visit, provider, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resolved MemberDatedContext");
    }

    @Test
    void toViewDtoReportsThePersistedDiscountAmountNotARecomputedOne() {
        // A claim with a refused portion: the old formula
        // (requested - patientCoPay) * discount% would have ignored the refusal
        // entirely and reported a larger, wrong discount.
        Claim claim = Claim.builder()
                .status(ClaimStatus.APPROVED)
                .requestedAmount(new BigDecimal("1000.00"))
                .patientCoPay(new BigDecimal("100.00"))
                .refusedAmount(new BigDecimal("200.00"))
                .appliedDiscountPercent(new BigDecimal("10.00"))
                .companyDiscountAmount(new BigDecimal("63.00")) // persisted, validated value
                .approvedAmount(new BigDecimal("637.00"))
                .netProviderAmount(new BigDecimal("637.00"))
                .lines(List.of(ClaimLine.builder().id(1L).build()))
                .build();

        ClaimViewDto dto = mapper.toViewDto(claim);

        // Must equal the persisted snapshot, not a recomputed
        // (1000-100)*10/100 = 90.00 that ignores refusedAmount.
        assertThat(dto.getCompanyDiscountAmount()).isEqualByComparingTo(new BigDecimal("63.00"));
        assertThat(dto.getCompanyDiscountAmount()).isNotEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    void toViewDtoPassesThroughZeroDiscountUnchanged() {
        Claim claim = Claim.builder()
                .status(ClaimStatus.APPROVED)
                .requestedAmount(new BigDecimal("500.00"))
                .patientCoPay(BigDecimal.ZERO)
                .refusedAmount(BigDecimal.ZERO)
                .appliedDiscountPercent(BigDecimal.ZERO)
                .companyDiscountAmount(BigDecimal.ZERO)
                .approvedAmount(new BigDecimal("500.00"))
                .netProviderAmount(new BigDecimal("500.00"))
                .lines(List.of(ClaimLine.builder().id(1L).build()))
                .build();

        ClaimViewDto dto = mapper.toViewDto(claim);

        assertThat(dto.getCompanyDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void toViewDtoNeverReconstructsAMissingHistoricalSnapshotFromTodaysContract() {
        Long providerId = 10L;
        Long employerId = 20L;
        Member member = Member.builder()
                .employer(Employer.builder().id(employerId).build())
                .build();
        Claim claim = Claim.builder()
                .status(ClaimStatus.APPROVED)
                .providerId(providerId)
                .member(member)
                .requestedAmount(new BigDecimal("1000.00"))
                .approvedAmount(new BigDecimal("1000.00"))
                .netProviderAmount(new BigDecimal("1000.00"))
                .patientCoPay(BigDecimal.ZERO)
                .refusedAmount(BigDecimal.ZERO)
                .appliedDiscountPercent(null)
                .lines(List.of(ClaimLine.builder().id(1L).build()))
                .build();

        ClaimViewDto dto = mapper.toViewDto(claim);
        assertThat(dto.getProviderDiscountPercent()).isNull();
        verify(effectiveContractResolver, never()).resolve(providerId, employerId, claim.getServiceDate());
    }

    @Test
    void resolvesEffectiveReplacementForAStalePriceVersionInTheSameContract() {
        LocalDate serviceDate = LocalDate.of(2026, 8, 10);
        ProviderContract contract = ProviderContract.builder().id(3051L).build();
        ProviderContractPricingItem stale = ProviderContractPricingItem.builder()
                .id(37261L).contract(contract).serviceName("خزعة بمنظار").active(false).build();
        ProviderContractPricingItem effective = ProviderContractPricingItem.builder()
                .id(37264L).contract(contract).serviceName("خزعة بمنظار").active(true).build();

        when(pricingItemRepository.findEffectiveInContractById(3051L, 37261L, serviceDate))
                .thenReturn(Optional.empty());
        when(pricingItemRepository.findById(37261L)).thenReturn(Optional.of(stale));
        when(pricingItemRepository.findEffectiveInContractByName(3051L, "خزعة بمنظار", serviceDate))
                .thenReturn(Optional.of(effective));

        assertThat(mapper.resolvePricingItemForLine(3051L, serviceDate, 37261L, null, null))
                .isSameAs(effective);
    }

    @Test
    void neverRecoversAStalePriceVersionFromAnotherContract() {
        LocalDate serviceDate = LocalDate.of(2026, 8, 10);
        ProviderContract foreignContract = ProviderContract.builder().id(9999L).build();
        ProviderContractPricingItem foreign = ProviderContractPricingItem.builder()
                .id(88L).contract(foreignContract).serviceName("خزعة بمنظار").active(false).build();

        when(pricingItemRepository.findEffectiveInContractById(3051L, 88L, serviceDate))
                .thenReturn(Optional.empty());
        when(pricingItemRepository.findById(88L)).thenReturn(Optional.of(foreign));

        assertThat(mapper.resolvePricingItemForLine(3051L, serviceDate, 88L, null, "خزعة بمنظار"))
                .isNull();
    }
}
