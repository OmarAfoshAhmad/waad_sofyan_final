package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyGapReportDto.ContextGapReason;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket.LimitRole;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.claimcontext.entity.ClaimContextDefinition;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1: a rule/bucket/claim-context gap is easy to create silently -- the
 * link between a rule and a bucket lives in a separate join table
 * (BenefitRuleBucket), not a column on either side, and claim_context_code
 * is a free-text column with no database FK to claim_contexts. This proves
 * the audit finds exactly the three shapes of gap it was built for, and
 * nothing it shouldn't (a bucket reachable only as a parent, a
 * POLICY_GENERAL_MIRROR bucket, a rule whose bucket-less-ness is a
 * deliberate "only the general ceiling applies" choice) -- and that its
 * context check is CoverageDecisionService#resolve's own null-vs-inactive
 * distinction, not a coarser "is it in the active set" check.
 */
@ExtendWith(MockitoExtension.class)
class BenefitPolicyGapAuditServiceTest {

    @Mock private BenefitPolicyRuleRepository ruleRepository;
    @Mock private BenefitRuleBucketRepository ruleBucketRepository;
    @Mock private BenefitLimitBucketRepository bucketRepository;
    @Mock private ClaimContextDefinitionRepository claimContextRepository;

    @InjectMocks
    private BenefitPolicyGapAuditService service;

    private MedicalCategory category(String code) {
        return MedicalCategory.builder().id(1L).code(code).nameAr("تصنيف").build();
    }

    private BenefitPolicyRule rule(long id, String contextCode) {
        return BenefitPolicyRule.builder().id(id).medicalCategory(category("CAT-X"))
                .encounterType(EncounterType.OUTPATIENT).claimContextCode(contextCode).build();
    }

    private BenefitLimitBucket bucket(long id, boolean active, LimitRole role, BenefitLimitBucket parent) {
        return BenefitLimitBucket.builder().id(id).code("B" + id).nameAr("وعاء")
                .active(active).limitRole(role).parentBucket(parent).build();
    }

    @Test
    void ruleWithNoBucketLinkIsReportedButNotCritical() {
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L))
                .thenReturn(List.of(rule(1L, "OUTPATIENT")));
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of());
        when(claimContextRepository.findById("OUTPATIENT")).thenReturn(
                Optional.of(ClaimContextDefinition.builder().code("OUTPATIENT")
                        .baseEncounterType(EncounterType.ANY).active(true).build()));

        var report = service.audit(1L);

        assertThat(report.getRulesWithoutBucket()).hasSize(1);
        assertThat(report.hasCriticalGaps()).isFalse();
        assertThat(report.hasAnyGaps()).isTrue();
    }

    @Test
    void bucketReachableOnlyAsAParentIsNotReportedAsOrphan() {
        BenefitLimitBucket parent = bucket(1L, true, LimitRole.STANDARD, null);
        BenefitLimitBucket child = bucket(2L, true, LimitRole.STANDARD, parent);
        BenefitPolicyRule rule = rule(10L, null);
        BenefitRuleBucket link = BenefitRuleBucket.builder().id(100L).rule(rule).bucket(child).build();

        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(List.of(rule));
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of(link));
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of(parent, child));

        var report = service.audit(1L);

        assertThat(report.getBucketsWithoutRule()).isEmpty();
    }

    @Test
    void aTrulyUnreachableActiveBucketIsReportedAsOrphan() {
        BenefitLimitBucket orphan = bucket(3L, true, LimitRole.STANDARD, null);
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(List.of());
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of(orphan));

        var report = service.audit(1L);

        assertThat(report.getBucketsWithoutRule()).hasSize(1);
        assertThat(report.getBucketsWithoutRule().get(0).getBucketId()).isEqualTo(3L);
    }

    @Test
    void aPolicyGeneralMirrorBucketWithNoLinkIsNeverReportedAsOrphan() {
        BenefitLimitBucket mirror = bucket(4L, true, LimitRole.POLICY_GENERAL_MIRROR, null);
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(List.of());
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of(mirror));

        var report = service.audit(1L);

        assertThat(report.getBucketsWithoutRule()).isEmpty();
    }

    @Test
    void anInactiveBucketWithNoLinkIsNotReportedAsOrphan() {
        BenefitLimitBucket inactive = bucket(5L, false, LimitRole.STANDARD, null);
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(List.of());
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of(inactive));

        var report = service.audit(1L);

        assertThat(report.getBucketsWithoutRule()).isEmpty();
    }

    @Test
    void aRuleNamingAClaimContextThatWasNeverCreatedIsCriticalAndMissing() {
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L))
                .thenReturn(List.of(rule(1L, "PREGNANCY_COMPLICATIONS")));
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of());
        when(claimContextRepository.findById("PREGNANCY_COMPLICATIONS")).thenReturn(Optional.empty());

        var report = service.audit(1L);

        assertThat(report.getRulesWithUnknownContext()).hasSize(1);
        var gap = report.getRulesWithUnknownContext().get(0);
        assertThat(gap.getClaimContextCode()).isEqualTo("PREGNANCY_COMPLICATIONS");
        assertThat(gap.getReason()).isEqualTo(ContextGapReason.MISSING);
        assertThat(report.hasCriticalGaps()).isTrue();
    }

    @Test
    void aRuleNamingAClaimContextThatExistsButIsDisabledIsCriticalAndDisabled() {
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L))
                .thenReturn(List.of(rule(1L, "PREGNANCY_COMPLICATIONS")));
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of());
        when(claimContextRepository.findById("PREGNANCY_COMPLICATIONS")).thenReturn(
                Optional.of(ClaimContextDefinition.builder().code("PREGNANCY_COMPLICATIONS")
                        .baseEncounterType(EncounterType.INPATIENT).active(false).build()));

        var report = service.audit(1L);

        assertThat(report.getRulesWithUnknownContext()).hasSize(1);
        assertThat(report.getRulesWithUnknownContext().get(0).getReason()).isEqualTo(ContextGapReason.DISABLED);
    }

    @Test
    void aRuleWhoseEncounterTypeConflictsWithAnActiveContextsBaseEncounterTypeIsDisabled() {
        // Mirrors CoverageDecisionService's own third rejection branch: a
        // context that exists and is active, but whose baseEncounterType
        // does not match the rule's encounterType, is still CONTEXT_MISMATCH
        // on the live path -- folded into DISABLED here (same fix category).
        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L))
                .thenReturn(List.of(rule(1L, "PREGNANCY_COMPLICATIONS")));
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of());
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of());
        when(claimContextRepository.findById("PREGNANCY_COMPLICATIONS")).thenReturn(
                Optional.of(ClaimContextDefinition.builder().code("PREGNANCY_COMPLICATIONS")
                        .baseEncounterType(EncounterType.INPATIENT).active(true).build()));

        var report = service.audit(1L); // rule() builds with encounterType=OUTPATIENT

        assertThat(report.getRulesWithUnknownContext()).hasSize(1);
        assertThat(report.getRulesWithUnknownContext().get(0).getReason()).isEqualTo(ContextGapReason.DISABLED);
    }

    @Test
    void aCleanPolicyReportsNoGapsAtAll() {
        BenefitLimitBucket bucket = bucket(6L, true, LimitRole.STANDARD, null);
        BenefitPolicyRule rule = rule(20L, "OUTPATIENT");
        BenefitRuleBucket link = BenefitRuleBucket.builder().id(200L).rule(rule).bucket(bucket).build();

        when(ruleRepository.findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(List.of(rule));
        when(ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(1L)).thenReturn(List.of(link));
        when(bucketRepository.findByPolicyIdOrderByCode(1L)).thenReturn(List.of(bucket));
        when(claimContextRepository.findById("OUTPATIENT")).thenReturn(
                Optional.of(ClaimContextDefinition.builder().code("OUTPATIENT")
                        .baseEncounterType(EncounterType.ANY).active(true).build()));

        var report = service.audit(1L);

        assertThat(report.hasAnyGaps()).isFalse();
    }
}
