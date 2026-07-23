package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureDtos.GroupRequest;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureDtos.IndividualLimitRequest;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;

@ExtendWith(MockitoExtension.class)
class BenefitStructureServiceTest {
    @Mock BenefitPolicyRepository policyRepository;
    @Mock BenefitPolicyRuleRepository ruleRepository;
    @Mock BenefitGroupRepository groupRepository;
    @Mock BenefitLimitBucketRepository bucketRepository;
    @Mock BenefitRuleBucketRepository linkRepository;
    @Mock BenefitBucketConsumptionRepository consumptionRepository;
    @InjectMocks BenefitStructureService service;

    private BenefitPolicy policy;

    @BeforeEach
    void setUp() {
        policy = BenefitPolicy.builder().id(10L).build();
    }

    @Test
    void createGroup_withoutLimit_keepsMembershipAndCountsEachUnit() {
        when(policyRepository.findById(10L)).thenReturn(Optional.of(policy));
        BenefitPolicyRule first = BenefitPolicyRule.builder().id(101L).benefitPolicy(policy)
                .encounterType(EncounterType.OUTPATIENT).build();
        BenefitPolicyRule second = BenefitPolicyRule.builder().id(102L).benefitPolicy(policy)
                .encounterType(EncounterType.OUTPATIENT).build();
        when(groupRepository.existsByPolicyIdAndCodeIgnoreCase(10L, "GRP-TEST")).thenReturn(false);
        when(groupRepository.findByPolicyIdAndNameArIgnoreCase(10L, "مجموعة اختبار")).thenReturn(Optional.empty());
        when(groupRepository.save(any(BenefitGroup.class))).thenAnswer(invocation -> {
            BenefitGroup value = invocation.getArgument(0); value.setId(20L); return value;
        });
        when(bucketRepository.save(any(BenefitLimitBucket.class))).thenAnswer(invocation -> {
            BenefitLimitBucket value = invocation.getArgument(0); value.setId(30L); return value;
        });
        when(ruleRepository.findById(101L)).thenReturn(Optional.of(first));
        when(ruleRepository.findById(102L)).thenReturn(Optional.of(second));

        service.createGroup(10L, new GroupRequest("GRP-TEST", "مجموعة اختبار", EncounterType.OUTPATIENT,
                AggregationMode.SHARED, true, null, null, null, LimitPeriodType.POLICY_PERIOD, null,
                List.of(101L, 102L)));

        ArgumentCaptor<BenefitLimitBucket> bucket = ArgumentCaptor.forClass(BenefitLimitBucket.class);
        verify(bucketRepository).save(bucket.capture());
        assertThat(bucket.getValue().getCountingMethod()).isEqualTo(CountingMethod.EACH_UNIT);
        assertThat(bucket.getValue().getAmountLimit()).isNull();
        verify(linkRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void upsertIndividualLimit_createsInternalLimitAndCountsQuantity() {
        BenefitPolicyRule rule = BenefitPolicyRule.builder().id(101L).benefitPolicy(policy)
                .encounterType(EncounterType.OUTPATIENT).build();
        when(ruleRepository.findById(101L)).thenReturn(Optional.of(rule));
        when(linkRepository.findByRuleIdOrderByConsumptionOrder(101L)).thenReturn(List.of());
        when(groupRepository.save(any(BenefitGroup.class))).thenAnswer(invocation -> {
            BenefitGroup value = invocation.getArgument(0); value.setId(21L); return value;
        });
        when(bucketRepository.save(any(BenefitLimitBucket.class))).thenAnswer(invocation -> {
            BenefitLimitBucket value = invocation.getArgument(0); value.setId(31L); return value;
        });

        service.upsertIndividualLimit(10L, 101L, new IndividualLimitRequest(
                new BigDecimal("5000"), 20, null, LimitPeriodType.POLICY_PERIOD, null));

        ArgumentCaptor<BenefitLimitBucket> bucket = ArgumentCaptor.forClass(BenefitLimitBucket.class);
        verify(bucketRepository).save(bucket.capture());
        assertThat(bucket.getValue().getCountingMethod()).isEqualTo(CountingMethod.EACH_UNIT);
        assertThat(bucket.getValue().getTimesLimit()).isEqualTo(20);
        verify(linkRepository).save(any());
    }

    @Test
    void deleteBucket_withFinancialHistory_failsClosed() {
        BenefitGroup group = BenefitGroup.builder().id(20L).policy(policy).build();
        BenefitLimitBucket bucket = BenefitLimitBucket.builder().id(30L).policy(policy)
                .benefitGroup(group).nameAr("علاج طبيعي").build();
        when(bucketRepository.findById(30L)).thenReturn(Optional.of(bucket));
        when(bucketRepository.findByParentBucketId(30L)).thenReturn(List.of());
        when(linkRepository.existsByBucketId(30L)).thenReturn(false);
        when(consumptionRepository.existsByBucketId(30L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteBucket(10L, 30L))
                .hasMessageContaining("سجل استهلاك مالي");
        verify(bucketRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteGeneratedGroup_withConsumedBucket_doesNotRemoveLinksOrHistory() {
        BenefitGroup group = BenefitGroup.builder().id(20L).policy(policy).build();
        BenefitLimitBucket bucket = BenefitLimitBucket.builder().id(30L).policy(policy)
                .benefitGroup(group).code("AUTO-GRP-TEST").nameAr("مجموعة مستهلكة").build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(bucketRepository.findByBenefitGroupId(20L)).thenReturn(List.of(bucket));
        when(consumptionRepository.existsByBucketId(30L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteGroup(10L, 20L))
                .hasMessageContaining("سجل استهلاك مالي");
        verify(linkRepository, org.mockito.Mockito.never()).deleteAll(any());
        verify(groupRepository, org.mockito.Mockito.never()).delete(any());
    }
}
