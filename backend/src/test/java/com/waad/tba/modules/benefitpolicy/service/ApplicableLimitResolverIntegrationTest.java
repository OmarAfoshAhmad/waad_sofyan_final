package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket.LimitRole;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.LimitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * WAAD-FIN-1.0: proves ApplicableLimitResolver stays purely structural
 * (bucket hierarchy + POLICY_GENERAL synthesis + POLICY_GENERAL_MIRROR
 * exclusion) against real PostgreSQL, with no consumption/balance reading.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ApplicableLimitResolverIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ApplicableLimitResolver resolver;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private BenefitGroupRepository benefitGroupRepository;
    @Autowired private BenefitLimitBucketRepository benefitLimitBucketRepository;
    @Autowired private BenefitRuleBucketRepository benefitRuleBucketRepository;

    private record Fixture(BenefitPolicy policy, BenefitPolicyRule rule, BenefitGroup group) {}

    private Fixture buildPolicyAndRule(BigDecimal annualLimit) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Employer employer = employerRepository.save(Employer.builder()
                .name("Resolver Test Co " + suffix).code("EMP-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(annualLimit).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("General Services").active(true).build());

        BenefitPolicyRule rule = benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(80).active(true).deleted(false).build());

        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + suffix).nameAr("مجموعة الاختبار")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.SHARED)
                .active(true).build());

        return new Fixture(policy, rule, group);
    }

    private BenefitLimitBucket bucket(Fixture f, String code, BigDecimal amount, LimitPeriodType periodType,
                                       BenefitLimitBucket parent, LimitRole role) {
        return benefitLimitBucketRepository.save(BenefitLimitBucket.builder()
                .policy(f.policy()).benefitGroup(f.group()).code(code + "-" + UUID.randomUUID().toString().substring(0, 6))
                .nameAr(code).contextType(EncounterType.OUTPATIENT).amountLimit(amount)
                .periodType(periodType).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE).parentBucket(parent)
                .limitRole(role).shared(false).active(true).build());
    }

    private void link(Fixture f, BenefitLimitBucket bucket, int order) {
        benefitRuleBucketRepository.save(BenefitRuleBucket.builder()
                .rule(f.rule()).bucket(bucket).consumptionOrder(order).build());
    }

    @Test
    @Transactional
    void resolvesDirectBucketPlusPolicyGeneral() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        BenefitLimitBucket serviceBucket = bucket(f, "SVC", new BigDecimal("500.00"), LimitPeriodType.ANNUAL, null, LimitRole.STANDARD);
        link(f, serviceBucket, 1);

        List<ApplicableLimitResolver.ApplicableLimitDefinition> result =
                resolver.resolve(f.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).scopeType()).isEqualTo(LimitScopeType.SERVICE);
        assertThat(result.get(0).semanticKey()).isEqualTo("BUCKET:" + serviceBucket.getId());
        assertThat(result.get(0).defaultLimit()).isEqualByComparingTo("500.00");
        assertThat(result.get(1).scopeType()).isEqualTo(LimitScopeType.POLICY_GENERAL);
        assertThat(result.get(1).semanticKey()).isEqualTo("POLICY_GENERAL:" + f.policy().getId());
        assertThat(result.get(1).defaultLimit()).isEqualByComparingTo("1000.00");
        assertThat(result.get(1).bucketId()).isNull();
    }

    @Test
    @Transactional
    void walksParentChainAndDeduplicatesByBucketId() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        BenefitLimitBucket parent = bucket(f, "GRP", new BigDecimal("800.00"), LimitPeriodType.ANNUAL, null, LimitRole.STANDARD);
        BenefitLimitBucket child = bucket(f, "SVC", new BigDecimal("500.00"), LimitPeriodType.ANNUAL, parent, LimitRole.STANDARD);
        link(f, child, 1);

        List<ApplicableLimitResolver.ApplicableLimitDefinition> result =
                resolver.resolve(f.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT);

        // child (depth 0, SERVICE) + parent (depth 1, GROUP) + POLICY_GENERAL
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ApplicableLimitResolver.ApplicableLimitDefinition::bucketId)
                .containsExactlyInAnyOrder(child.getId(), parent.getId(), null);
        var parentDef = result.stream().filter(d -> parent.getId().equals(d.bucketId())).findFirst().orElseThrow();
        assertThat(parentDef.scopeType()).isEqualTo(LimitScopeType.GROUP);
        assertThat(parentDef.hierarchyDepth()).isEqualTo(1);
    }

    @Test
    @Transactional
    void excludesPolicyGeneralMirrorBucketFromResultButKeepsSyntheticEntry() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        BenefitLimitBucket mirror = bucket(f, "MIRROR", new BigDecimal("1000.00"), LimitPeriodType.ANNUAL, null, LimitRole.POLICY_GENERAL_MIRROR);
        link(f, mirror, 1);
        BenefitLimitBucket real = bucket(f, "SVC", new BigDecimal("300.00"), LimitPeriodType.ANNUAL, null, LimitRole.STANDARD);
        link(f, real, 2);

        List<ApplicableLimitResolver.ApplicableLimitDefinition> result =
                resolver.resolve(f.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT);

        assertThat(result).extracting(ApplicableLimitResolver.ApplicableLimitDefinition::bucketId)
                .containsExactlyInAnyOrder(real.getId(), null);
        assertThat(result).filteredOn(d -> d.scopeType() == LimitScopeType.POLICY_GENERAL)
                .hasSize(1)
                .first().satisfies(d -> assertThat(d.defaultLimit()).isEqualByComparingTo("1000.00"));
    }

    @Test
    @Transactional
    void mirrorAmountMismatchFailsClosed() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        BenefitLimitBucket mirror = bucket(f, "MIRROR", new BigDecimal("999.00"), LimitPeriodType.ANNUAL, null, LimitRole.POLICY_GENERAL_MIRROR);
        link(f, mirror, 1);

        assertThatThrownBy(() ->
                resolver.resolve(f.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POLICY_GENERAL_MIRROR_MISMATCH");
    }

    @Test
    @Transactional
    void inactiveOrWrongContextBucketsAreExcluded() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        BenefitLimitBucket inactive = bucket(f, "INACTIVE", new BigDecimal("500.00"), LimitPeriodType.ANNUAL, null, LimitRole.STANDARD);
        inactive.setActive(false);
        benefitLimitBucketRepository.save(inactive);
        link(f, inactive, 1);

        BenefitLimitBucket wrongContext = benefitLimitBucketRepository.save(BenefitLimitBucket.builder()
                .policy(f.policy()).benefitGroup(f.group()).code("INPT-" + UUID.randomUUID().toString().substring(0, 6))
                .nameAr("INPT").contextType(EncounterType.INPATIENT).amountLimit(new BigDecimal("400.00"))
                .periodType(LimitPeriodType.ANNUAL).limitRole(LimitRole.STANDARD).active(true).build());
        link(f, wrongContext, 2);

        List<ApplicableLimitResolver.ApplicableLimitDefinition> result =
                resolver.resolve(f.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT);

        // only the synthetic POLICY_GENERAL entry survives
        assertThat(result).hasSize(1);
        assertThat(result.get(0).scopeType()).isEqualTo(LimitScopeType.POLICY_GENERAL);
    }

    @Test
    @Transactional
    void bucketBelongingToADifferentPolicyFailsClosed() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        Fixture other = buildPolicyAndRule(new BigDecimal("2000.00"));
        BenefitLimitBucket foreignBucket = bucket(other, "FOREIGN", new BigDecimal("500.00"), LimitPeriodType.ANNUAL, null, LimitRole.STANDARD);
        link(f, foreignBucket, 1);

        assertThatThrownBy(() ->
                resolver.resolve(f.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUCKET_POLICY_MISMATCH");
    }

    @Test
    @Transactional
    void ruleNotBelongingToRequestedPolicyFailsClosed() {
        Fixture f = buildPolicyAndRule(new BigDecimal("1000.00"));
        Fixture other = buildPolicyAndRule(new BigDecimal("2000.00"));

        assertThatThrownBy(() ->
                resolver.resolve(other.policy().getId(), f.rule().getId(), LocalDate.now(), EncounterType.OUTPATIENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BENEFIT_RULE_POLICY_MISMATCH");
    }
}
