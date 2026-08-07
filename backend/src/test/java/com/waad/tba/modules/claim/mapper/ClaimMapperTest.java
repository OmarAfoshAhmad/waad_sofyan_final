package com.waad.tba.modules.claim.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

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

    @InjectMocks
    private ClaimMapper mapper;

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
}
