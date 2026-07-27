package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BenefitPolicyRuleServiceLifecycleTest {

    @Mock BenefitPolicyRuleRepository ruleRepository;
    @Mock BenefitPolicyRepository policyRepository;
    @Mock MedicalCategoryRepository categoryRepository;
    @Mock CoverageDecisionService coverageDecisionService;
    @Mock EntityManager em;
    @Mock Query query;

    @Test
    void deleteSoftDeletesRuleWhenNoFinancialReferenceExists() {
        BenefitPolicyRule rule = rule(10L, true, false);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        financialReferenceCount(0L);

        service().delete(10L);

        ArgumentCaptor<BenefitPolicyRule> captor = ArgumentCaptor.forClass(BenefitPolicyRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().isDeleted()).isTrue();
    }

    @Test
    void deleteDisablesRuleInsteadOfSoftDeletingWhenFinancialReferenceExists() {
        BenefitPolicyRule rule = rule(11L, true, false);
        when(ruleRepository.findById(11L)).thenReturn(Optional.of(rule));
        financialReferenceCount(1L);

        service().delete(11L);

        ArgumentCaptor<BenefitPolicyRule> captor = ArgumentCaptor.forClass(BenefitPolicyRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().isDeleted()).isFalse();
    }

    @Test
    void financialReferenceCheckCountsOnlyActiveApprovedBatchedOrSettledClaims() {
        BenefitPolicyRule rule = rule(15L, true, false);
        when(ruleRepository.findById(15L)).thenReturn(Optional.of(rule));
        financialReferenceCount(0L);

        service().delete(15L);

        verify(em).createNativeQuery(contains("c.status IN ('APPROVED', 'BATCHED', 'SETTLED')"));
        verify(em).createNativeQuery(contains("COALESCE(c.active, true) = true"));
        verify(em).createNativeQuery(contains("c.deleted_at IS NULL"));

        ArgumentCaptor<BenefitPolicyRule> captor = ArgumentCaptor.forClass(BenefitPolicyRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
    }

    @Test
    void hardDeleteRejectsDisabledRuleBeforePhysicalDelete() {
        BenefitPolicyRule disabled = rule(12L, false, false);
        when(ruleRepository.findById(12L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service().hardDelete(12L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("المنافع المعطلة مالياً");

        verify(ruleRepository, never()).delete(disabled);
    }

    @Test
    void hardDeleteRejectsDeletedRuleWhenFinancialReferenceExists() {
        BenefitPolicyRule deleted = rule(13L, false, true);
        when(ruleRepository.findById(13L)).thenReturn(Optional.of(deleted));
        financialReferenceCount(1L);

        assertThatThrownBy(() -> service().hardDelete(13L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("سجلات مالية");

        verify(ruleRepository, never()).delete(deleted);
    }

    @Test
    void hardDeleteDetachesNonFinancialClaimLineReferencesBeforePhysicalDelete() {
        BenefitPolicyRule deleted = rule(14L, false, true);
        when(ruleRepository.findById(14L)).thenReturn(Optional.of(deleted));
        financialReferenceCount(0L);
        when(em.createNativeQuery(contains("UPDATE claim_lines cl"))).thenReturn(query);
        when(query.setParameter(eq("ruleId"), eq(14L))).thenReturn(query);
        when(query.executeUpdate()).thenReturn(2);

        service().hardDelete(14L);

        verify(em).createNativeQuery(contains("UPDATE claim_lines cl"));
        verify(ruleRepository).delete(deleted);
        verify(ruleRepository).flush();
    }

    @Test
    void responseDtoExposesLifecycleStatusForActiveDisabledAndDeletedRules() {
        assertThat(BenefitPolicyRuleResponseDto.fromEntity(rule(20L, true, false)).getLifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(BenefitPolicyRuleResponseDto.fromEntity(rule(21L, false, false)).getLifecycleStatus()).isEqualTo("DISABLED");
        BenefitPolicyRuleResponseDto deleted = BenefitPolicyRuleResponseDto.fromEntity(rule(22L, false, true));
        assertThat(deleted.getLifecycleStatus()).isEqualTo("DELETED");
        assertThat(deleted.isHardDeleteAllowed()).isTrue();
    }

    private BenefitPolicyRuleService service() {
        return new BenefitPolicyRuleService(ruleRepository, policyRepository, categoryRepository, coverageDecisionService, em);
    }

    private void financialReferenceCount(Long count) {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(eq("ruleId"), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(count);
    }

    private BenefitPolicyRule rule(Long id, boolean active, boolean deleted) {
        return BenefitPolicyRule.builder()
                .id(id)
                .benefitPolicy(BenefitPolicy.builder().id(1L).defaultCoveragePercent(80).name("Policy").build())
                .coveragePercent(100)
                .active(active)
                .deleted(deleted)
                .build();
    }
}
