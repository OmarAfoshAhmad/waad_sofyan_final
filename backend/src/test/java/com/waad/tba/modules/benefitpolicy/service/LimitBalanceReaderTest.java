package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository.GeneralCeilingBulkProjection;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * finance-08 follow-up: a member who switched benefit policies mid-year has
 * committed POLICY_GENERAL rows under BOTH policy ids in the same calendar
 * window. Each policy's own annual limit must only ever be measured against
 * its OWN rows -- summing both into one figure understates (or overstates)
 * whichever policy's remaining balance a caller is asking about.
 */
@ExtendWith(MockitoExtension.class)
class LimitBalanceReaderTest {

    @Mock private BenefitBucketConsumptionRepository consumptionRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private BenefitLimitBucketRepository bucketRepository;

    private LimitBalanceReader reader;

    private final LocalDate periodStart = LocalDate.of(2026, 1, 1);
    private final LocalDate periodEnd = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        // The uplift repository is mocked to return nothing: these cases are
        // about committed and reserved against a policy ceiling, and an
        // exceptional uplift is asserted where it belongs, in
        // MemberLimitUpliftIntegrationTest.
        var upliftRepository = org.mockito.Mockito.mock(
                com.waad.tba.modules.member.repository.MemberGeneralLimitUpliftRepository.class);
        // lenient: the empty-input case returns before any query is issued, so
        // a strict stub here would be reported as unused by the very test that
        // proves the early return works.
        org.mockito.Mockito.lenient().when(upliftRepository.sumInForceByMember(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        reader = new LimitBalanceReader(consumptionRepository, upliftRepository,
                claimRepository, bucketRepository);
    }

    private record Row(Long memberId, Long policyId, BigDecimal amount) implements GeneralCeilingBulkProjection {
        @Override public Long getMemberId() { return memberId; }
        @Override public Long getPolicyId() { return policyId; }
        @Override public BigDecimal getAmount() { return amount; }
    }

    @Test
    @DisplayName("A member with rows under two policies (mid-year switch) counts only the CURRENT policy's row")
    void bulk_MidYearPolicySwitch_CountsOnlyTheCurrentPolicysRow() {
        // Member 1 spent 400 under the OLD policy (55) before switching, then
        // 150 under the NEW policy (77) -- both rows exist in the same
        // calendar-year window. The caller says policy 77 is current.
        when(consumptionRepository.sumGeneralScopeCommittedBulk(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new Row(1L, 55L, new BigDecimal("400.00")),
                        new Row(1L, 77L, new BigDecimal("150.00"))));

        Map<Long, BigDecimal> result = reader.readGeneralCeilingCommittedBulk(
                Map.of(1L, 77L), periodStart, periodEnd, null);

        assertThat(result.get(1L)).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("A member with no rows under their current policy id gets zero, not a missing key")
    void bulk_NoRowsUnderCurrentPolicy_YieldsZeroNotAbsent() {
        when(consumptionRepository.sumGeneralScopeCommittedBulk(any(), any(), any(), any()))
                .thenReturn(List.of(new Row(2L, 55L, new BigDecimal("400.00"))));

        Map<Long, BigDecimal> result = reader.readGeneralCeilingCommittedBulk(
                Map.of(2L, 77L), periodStart, periodEnd, null);

        assertThat(result).containsEntry(2L, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("An empty member map short-circuits without querying the repository")
    void bulk_EmptyInput_SkipsTheQuery() {
        Map<Long, BigDecimal> result = reader.readGeneralCeilingCommittedBulk(
                Map.of(), periodStart, periodEnd, null);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(consumptionRepository);
    }
}
