package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionRequest;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionSource;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claimcontext.entity.ClaimContextDefinition;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.math.BigDecimal;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverageDecisionServiceTest {
    @Mock BenefitPolicyRepository policyRepository;
    @Mock BenefitPolicyRuleRepository ruleRepository;
    @Mock MedicalCategoryRepository categoryRepository;
    @Mock BenefitBucketLimitService bucketLimitService;
    @Mock ClaimContextDefinitionRepository claimContextRepository;
    @InjectMocks CoverageDecisionService service;

    @Test
    void exactContextualRuleIsSelected() {
        MedicalCategory category = category(55L, null, Set.of(CategoryContext.OUTPATIENT));
        BenefitPolicy policy = BenefitPolicy.builder().id(1L).defaultCoveragePercent(80).build();
        BenefitPolicyRule rule = BenefitPolicyRule.builder().id(10L).benefitPolicy(policy)
                .medicalCategory(category).coveragePercent(70).encounterType(EncounterType.OUTPATIENT)
                .active(true).build();
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(category));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(ruleRepository.findBestRuleForClaimContext(1L, 55L, null, "OUTPATIENT"))
                .thenReturn(Optional.of(rule));

        var decision = service.resolve(request(EncounterType.OUTPATIENT));

        assertThat(decision.covered()).isTrue();
        assertThat(decision.coveragePercent()).isEqualTo(70);
        assertThat(decision.source()).isEqualTo(CoverageDecisionSource.EXACT_CATEGORY_RULE);
        assertThat(decision.appliedRule().getId()).isEqualTo(10L);
    }

    @Test
    void missingRuleFailsClosedInsteadOfUsingPolicyDefault() {
        MedicalCategory category = category(55L, null, Set.of(CategoryContext.ANY));
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(category));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(
                BenefitPolicy.builder().id(1L).defaultCoveragePercent(80).build()));
        when(ruleRepository.findBestRuleForClaimContext(1L, 55L, null, "OUTPATIENT"))
                .thenReturn(Optional.empty());

        var decision = service.resolve(request(EncounterType.OUTPATIENT));

        assertThat(decision.covered()).isFalse();
        assertThat(decision.coveragePercent()).isZero();
        assertThat(decision.reasonCode()).isEqualTo("NO_BENEFIT_RULE");
    }

    @Test
    void categoryContextMismatchIsRejectedBeforeRuleLookup() {
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(
                category(55L, null, Set.of(CategoryContext.OUTPATIENT))));

        var decision = service.resolve(request(EncounterType.INPATIENT));

        assertThat(decision.covered()).isFalse();
        assertThat(decision.source()).isEqualTo(CoverageDecisionSource.CONTEXT_MISMATCH);
    }

    @Test
    void decisionCarriesLedgerBackedAmountTimesAndDaysLimits() {
        MedicalCategory category = category(55L, null, Set.of(CategoryContext.ANY));
        BenefitPolicy policy = BenefitPolicy.builder().id(1L).build();
        BenefitPolicyRule rule = BenefitPolicyRule.builder().id(10L).benefitPolicy(policy)
                .medicalCategory(category).coveragePercent(80).encounterType(EncounterType.OUTPATIENT)
                .active(true).build();
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(category));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(ruleRepository.findBestRuleForClaimContext(1L, 55L, null, "OUTPATIENT"))
                .thenReturn(Optional.of(rule));
        when(bucketLimitService.findApplicable(10L, 7L, null, EncounterType.OUTPATIENT, 99L))
                .thenReturn(List.of(new BenefitBucketLimitService.LimitSnapshot(
                        30L, "العلاج الطبيعي", new BigDecimal("5000"), 20, 10,
                        new BigDecimal("1200"), 6, 4, false,
                        CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE, true)));

        var decision = service.resolve(CoverageDecisionRequest.builder().policyId(1L)
                .serviceCategoryId(55L).memberId(7L).excludeClaimId(99L)
                .encounterType(EncounterType.OUTPATIENT).build());

        assertThat(decision.limitsOrEmpty()).singleElement().satisfies(limit -> {
            assertThat(limit.amountLimit()).isEqualByComparingTo("5000");
            assertThat(limit.usedAmount()).isEqualByComparingTo("1200");
            assertThat(limit.timesLimit()).isEqualTo(20);
            assertThat(limit.usedTimes()).isEqualTo(6);
            assertThat(limit.daysLimit()).isEqualTo(10);
            assertThat(limit.usedDays()).isEqualTo(4);
        });
    }

    @Test
    void maternityNeverFallsBackToTheGenericInpatientRule() {
        MedicalCategory category = category(55L, null, Set.of(CategoryContext.INPATIENT));
        BenefitPolicy policy = BenefitPolicy.builder().id(1L).build();
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(category));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(claimContextRepository.findById("MATERNITY")).thenReturn(Optional.of(
                ClaimContextDefinition.builder().code("MATERNITY").nameAr("ولادة وحمل")
                        .baseEncounterType(EncounterType.INPATIENT).active(true).build()));
        when(ruleRepository.findBestRuleForClaimContext(1L, 55L, null, "MATERNITY"))
                .thenReturn(Optional.empty());

        var decision = service.resolve(CoverageDecisionRequest.builder().policyId(1L)
                .serviceCategoryId(55L).memberId(7L)
                .encounterType(EncounterType.INPATIENT).claimContextCode("MATERNITY").build());

        assertThat(decision.covered()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_BENEFIT_RULE");
    }

    /**
     * Pregnancy complications are a claim-wide decision context, not a service
     * category. A real C-section can remain classified as CAT-COV-INPATIENT,
     * while the exact PREGNANCY_COMPLICATIONS rule decides coverage and points
     * to the complications ceiling.
     */
    @Test
    void pregnancyComplicationsContextCanCoverGenericInpatientCategoryWithExactContextRule() {
        MedicalCategory inpatientGeneral = category(55L, null, Set.of(CategoryContext.INPATIENT));
        inpatientGeneral.setCode("CAT-COV-INPATIENT");
        BenefitPolicy policy = BenefitPolicy.builder().id(1L).build();
        BenefitPolicyRule rule = BenefitPolicyRule.builder().id(41L).benefitPolicy(policy)
                .medicalCategory(inpatientGeneral).coveragePercent(100).encounterType(EncounterType.INPATIENT)
                .claimContextCode("PREGNANCY_COMPLICATIONS").active(true).deleted(false).build();
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(inpatientGeneral));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(claimContextRepository.findById("PREGNANCY_COMPLICATIONS")).thenReturn(Optional.of(
                ClaimContextDefinition.builder().code("PREGNANCY_COMPLICATIONS").nameAr("مضاعفات الحمل")
                        .baseEncounterType(EncounterType.INPATIENT).active(true).build()));
        when(ruleRepository.findBestRuleForClaimContext(1L, 55L, null, "PREGNANCY_COMPLICATIONS"))
                .thenReturn(Optional.of(rule));

        var decision = service.resolve(CoverageDecisionRequest.builder().policyId(1L)
                .serviceCategoryId(55L).memberId(7L)
                .encounterType(EncounterType.INPATIENT).claimContextCode("PREGNANCY_COMPLICATIONS").build());

        assertThat(decision.covered()).isTrue();
        assertThat(decision.coveragePercent()).isEqualTo(100);
        assertThat(decision.source()).isEqualTo(CoverageDecisionSource.EXACT_CATEGORY_RULE);
        assertThat(decision.appliedRule().getClaimContextCode()).isEqualTo("PREGNANCY_COMPLICATIONS");
    }

    @Test
    void explicitClaimContextMustBeActiveAndMatchItsBaseEncounter() {
        MedicalCategory category = category(55L, null, Set.of(CategoryContext.ANY));
        when(categoryRepository.findById(55L)).thenReturn(Optional.of(category));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(BenefitPolicy.builder().id(1L).build()));
        when(claimContextRepository.findById("MATERNITY")).thenReturn(Optional.of(
                ClaimContextDefinition.builder().code("MATERNITY").nameAr("ولادة وحمل")
                        .baseEncounterType(EncounterType.INPATIENT).active(true).build()));

        var decision = service.resolve(CoverageDecisionRequest.builder().policyId(1L)
                .serviceCategoryId(55L).encounterType(EncounterType.OUTPATIENT)
                .claimContextCode("MATERNITY").build());

        assertThat(decision.covered()).isFalse();
        assertThat(decision.source()).isEqualTo(CoverageDecisionSource.CONTEXT_MISMATCH);
        assertThat(decision.reasonCode()).isEqualTo("CLAIM_CONTEXT_MISMATCH");
    }

    private CoverageDecisionRequest request(EncounterType context) {
        return CoverageDecisionRequest.builder().policyId(1L).serviceCategoryId(55L)
                .encounterType(context).build();
    }

    private MedicalCategory category(Long id, Long parentId, Set<CategoryContext> contexts) {
        return MedicalCategory.builder().id(id).parentId(parentId).code("CAT-" + id)
                .name("Category").active(true).deleted(false).contexts(contexts).build();
    }
}
