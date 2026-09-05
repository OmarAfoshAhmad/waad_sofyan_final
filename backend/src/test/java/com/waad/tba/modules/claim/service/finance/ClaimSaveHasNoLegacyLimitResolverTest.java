package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.service.ApplicableCountingLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.ApplicableLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.benefitpolicy.service.EffectiveLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.preauthorization.service.PreAuthLimitHoldMapper;

/**
 * P1.6.x architectural proof: {@link ClaimFinancialAdjudicationService} (the
 * sole live adjudicator for claim save) and {@link ClaimLimitSnapshotFactory}
 * (the sole writer of {@code claim_line_limit_snapshots}) do not merely avoid
 * CALLING the retired limit-resolution stack at runtime -- they do not even
 * DEPEND on it. A class with no {@code EffectiveLimitResolver}/
 * {@code ApplicableLimitResolver}/{@code MultiLineMultiBucketEngine}
 * constructor parameter cannot invoke one, which is a stronger guarantee
 * than a {@code verifyNoInteractions} on a mock that happens not to fire
 * this run. PreAuth (untouched by P1.6, migrates in P1.12) still legitimately
 * depends on {@code EffectiveLimitResolver} elsewhere -- this test is scoped
 * to the claim-save path only.
 *
 * P1.11.6 extends the same guard to {@link BenefitBucketLedgerService}
 * (the sole live consumption writer), EXCEPT for {@code LimitBalanceReader}:
 * that dependency is legitimate there -- {@code validatePolicyAnnualLimit}
 * reads the ledger's OWN already-committed general ceiling as a
 * concurrency-safety re-check against a target the canonical decision
 * already produced, never to re-decide what to consume. Forbidding the
 * dependency itself would wrongly outlaw that legitimate use; the ledger's
 * unit test ({@code cw1_canonicalTargetIsExecutedLiterally}) is what proves
 * the CONSUMED figure itself never comes from it.
 */
class ClaimSaveHasNoLegacyLimitResolverTest {

    private static final List<Class<?>> RETIRED_FOR_CLAIM_SAVE = List.of(
            EffectiveLimitResolver.class, ApplicableLimitResolver.class, LimitBalanceReader.class,
            MultiLineMultiBucketEngine.class);

    private static final List<Class<?>> RETIRED_FOR_LEDGER_COMMIT = List.of(
            EffectiveLimitResolver.class, ApplicableLimitResolver.class, MultiLineMultiBucketEngine.class);

    /**
     * P1.12.2: the isolated canonical PreAuth reservation path.
     * {@code ApplicableCountingLimitResolver} joins the forbidden set here
     * (it never applied to claim save, since claims never had a separate
     * TIMES-only resolver) and {@code LimitBalanceReader} is FULLY forbidden
     * -- unlike the ledger, neither the adapter nor the mapper has any
     * legitimate safety-recheck use for it at all.
     */
    private static final List<Class<?>> RETIRED_FOR_PREAUTH_RESERVATION = List.of(
            EffectiveLimitResolver.class, ApplicableLimitResolver.class, ApplicableCountingLimitResolver.class,
            LimitBalanceReader.class, MultiLineMultiBucketEngine.class);

    @Test
    @DisplayName("SMD4a — ClaimFinancialAdjudicationService has no constructor dependency on the retired limit-resolution stack")
    void adjudicationServiceHasNoLegacyDependency() {
        assertNoConstructorParameterOfType(ClaimFinancialAdjudicationService.class, RETIRED_FOR_CLAIM_SAVE);
    }

    @Test
    @DisplayName("SMD4b — ClaimLimitSnapshotFactory has no constructor dependency on the retired limit-resolution stack")
    void snapshotFactoryHasNoLegacyDependency() {
        assertNoConstructorParameterOfType(ClaimLimitSnapshotFactory.class, RETIRED_FOR_CLAIM_SAVE);
    }

    @Test
    @DisplayName("P1.11.6 — BenefitBucketLedgerService has no constructor dependency on "
            + "EffectiveLimitResolver/ApplicableLimitResolver/MultiLineMultiBucketEngine "
            + "(LimitBalanceReader stays: a legitimate safety re-check, not a re-decision)")
    void ledgerHasNoLegacyResolutionDependency() {
        assertNoConstructorParameterOfType(BenefitBucketLedgerService.class, RETIRED_FOR_LEDGER_COMMIT);
    }

    @Test
    @DisplayName("P1.12.2 — BucketLimitSnapshotAdapter has no constructor dependency on the retired "
            + "PreAuth-specific limit-resolution stack (EffectiveLimitResolver/ApplicableLimitResolver/"
            + "ApplicableCountingLimitResolver/LimitBalanceReader/MultiLineMultiBucketEngine)")
    void adapterHasNoLegacyPreauthResolutionDependency() {
        assertNoConstructorParameterOfType(BucketLimitSnapshotAdapter.class, RETIRED_FOR_PREAUTH_RESERVATION);
    }

    @Test
    @DisplayName("P1.12.2 — PreAuthLimitHoldMapper has no constructor dependency on the retired "
            + "PreAuth-specific limit-resolution stack")
    void mapperHasNoLegacyPreauthResolutionDependency() {
        assertNoConstructorParameterOfType(PreAuthLimitHoldMapper.class, RETIRED_FOR_PREAUTH_RESERVATION);
    }

    private void assertNoConstructorParameterOfType(Class<?> target, List<Class<?>> forbidden) {
        for (Constructor<?> constructor : target.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertThat(forbidden).as(
                        "%s must not depend on retired %s -- claim save must resolve limits exactly once, "
                                + "through UnifiedLimitResolver/BucketLimitSnapshotAdapter only",
                        target.getSimpleName(), paramType.getSimpleName())
                        .doesNotContain(paramType);
            }
        }
    }
}
