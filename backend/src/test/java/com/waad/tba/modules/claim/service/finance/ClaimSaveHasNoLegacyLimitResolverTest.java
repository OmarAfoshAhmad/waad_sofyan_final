package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.preauthorization.service.PreAuthLimitHoldMapper;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationDecisionBuilder;

/**
 * P1.6.x/P1.11.6/P1.12.2-3 architectural proof, and P1.12.4's closing form of
 * it: the retired limit-resolution island --
 * {@code EffectiveLimitResolver}, {@code ApplicableLimitResolver},
 * {@code ApplicableCountingLimitResolver}, {@code LimitSourceProvider},
 * {@code PolicyDefaultLimitSourceProvider}, {@code MultiLineMultiBucketEngine}
 * -- is not merely uncalled from the live Claim/PreAuth decision paths; as of
 * P1.12.4 the classes themselves no longer exist in the codebase. {@link
 * #retiredLimitResolutionClassesNoLongerExist} is the strongest possible form
 * of that guard: a class that cannot be loaded cannot be a dependency of
 * anything, ever again, without a NEW class first reappearing under one of
 * these names.
 *
 * {@link LimitBalanceReader} survives: its {@code readGeneralCeiling}/
 * {@code readGeneralCeilingBulk}/{@code readGeneralCeilingCommittedBulk}
 * methods are live, read-only reporting (member balance screens, provider
 * portal, financial summaries) -- the per-decision {@code read}/
 * {@code readForPreauthorizedClaim} methods that DID belong to the retired
 * stack were deleted in P1.12.4. The class is still a legitimate dependency
 * for {@link BenefitBucketLedgerService}'s own concurrency-safety re-check,
 * but never for a class that resolves limits or builds a reservation from
 * scratch.
 */
class ClaimSaveHasNoLegacyLimitResolverTest {

    private static final List<String> RETIRED_CLASS_NAMES = List.of(
            "com.waad.tba.modules.benefitpolicy.service.EffectiveLimitResolver",
            "com.waad.tba.modules.benefitpolicy.service.ApplicableLimitResolver",
            "com.waad.tba.modules.benefitpolicy.service.ApplicableCountingLimitResolver",
            "com.waad.tba.modules.benefitpolicy.service.LimitSourceProvider",
            "com.waad.tba.modules.benefitpolicy.service.PolicyDefaultLimitSourceProvider",
            "com.waad.tba.modules.claim.service.finance.MultiLineMultiBucketEngine");

    /**
     * P1.12.4: no class with any of these names exists anywhere on the
     * classpath -- Claims' own live path (already proven clean since P1.6)
     * and PreAuth's live path (P1.12.3) both inherit this for free, since
     * neither can depend on a class that cannot be loaded at all.
     */
    @Test
    @DisplayName("P1.12.4 — the retired limit-resolution island no longer exists as compiled classes")
    void retiredLimitResolutionClassesNoLongerExist() {
        for (String className : RETIRED_CLASS_NAMES) {
            assertThatThrownBy(() -> Class.forName(className))
                    .as("%s must have been deleted in P1.12.4, not merely unreferenced", className)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    @DisplayName("SMD4a — ClaimFinancialAdjudicationService has no constructor dependency on LimitBalanceReader")
    void adjudicationServiceHasNoLegacyDependency() {
        assertNoConstructorParameterOfType(ClaimFinancialAdjudicationService.class, List.of(LimitBalanceReader.class));
    }

    @Test
    @DisplayName("SMD4b — ClaimLimitSnapshotFactory has no constructor dependency on LimitBalanceReader")
    void snapshotFactoryHasNoLegacyDependency() {
        assertNoConstructorParameterOfType(ClaimLimitSnapshotFactory.class, List.of(LimitBalanceReader.class));
    }

    @Test
    @DisplayName("P1.12.2 — BucketLimitSnapshotAdapter has no constructor dependency on LimitBalanceReader "
            + "(neither it nor the mapper has any legitimate safety-recheck use for it)")
    void adapterHasNoLegacyPreauthResolutionDependency() {
        assertNoConstructorParameterOfType(BucketLimitSnapshotAdapter.class, List.of(LimitBalanceReader.class));
    }

    @Test
    @DisplayName("P1.12.2 — PreAuthLimitHoldMapper has no constructor dependency on LimitBalanceReader")
    void mapperHasNoLegacyPreauthResolutionDependency() {
        assertNoConstructorParameterOfType(PreAuthLimitHoldMapper.class, List.of(LimitBalanceReader.class));
    }

    /**
     * P1.12.3 — the live wiring itself: {@code PreAuthorizationDecisionBuilder}
     * (the sole live PreAuth decision orchestrator) no longer resolves limits
     * or reads balances on its own -- {@code decideLine} delegates entirely
     * to {@code BucketLimitSnapshotAdapter.buildForPreauthReservation} +
     * {@code UnifiedLimitResolver} + {@code PreAuthLimitHoldMapper}.
     */
    @Test
    @DisplayName("P1.12.3 — PreAuthorizationDecisionBuilder has no constructor dependency on LimitBalanceReader: "
            + "it no longer resolves limits or reads balances itself")
    void builderHasNoLegacyPreauthResolutionDependency() {
        assertNoConstructorParameterOfType(PreAuthorizationDecisionBuilder.class, List.of(LimitBalanceReader.class));
    }

    private void assertNoConstructorParameterOfType(Class<?> target, List<Class<?>> forbidden) {
        for (Constructor<?> constructor : target.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertThat(forbidden).as(
                        "%s must not depend on retired %s -- limits are resolved exactly once, "
                                + "through UnifiedLimitResolver/BucketLimitSnapshotAdapter only",
                        target.getSimpleName(), paramType.getSimpleName())
                        .doesNotContain(paramType);
            }
        }
    }
}
