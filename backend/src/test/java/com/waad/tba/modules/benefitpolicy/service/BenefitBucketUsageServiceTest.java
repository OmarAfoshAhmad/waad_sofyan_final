package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketAdjustmentRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;

@ExtendWith(MockitoExtension.class)
class BenefitBucketUsageServiceTest {
    @Mock BenefitBucketConsumptionRepository consumptionRepository;
    @Mock BenefitBucketAdjustmentRepository adjustmentRepository;
    @InjectMocks BenefitBucketUsageService service;

    @Test
    void combinesOpeningUsageWithClaimsWithoutChangingClaimAmounts() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(consumptionRepository.sumCommittedAmount(10L, 20L, start, end, null))
                .thenReturn(new BigDecimal("150.00"));
        when(consumptionRepository.sumCommittedTimes(10L, 20L, start, end, null)).thenReturn(2);
        when(consumptionRepository.countCommittedServiceDays(10L, 20L, start, end, null)).thenReturn(1L);
        when(adjustmentRepository.sumActive(10L, 20L, start, end))
                .thenReturn(new BenefitBucketAdjustmentRepository.UsageTotals(
                        new BigDecimal("450.00"), 3L, 2L));

        BenefitBucketUsageService.UsageTotals totals = service.totals(10L, 20L, start, end, null);

        assertThat(totals.amount()).isEqualByComparingTo("600.00");
        assertThat(totals.times()).isEqualTo(5);
        assertThat(totals.days()).isEqualTo(3L);
    }
}
