package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The ceiling drawer reads a bucket's limit straight off the bucket.
 *
 * That is correct only while the engine does the same: ApplicableLimitResolver
 * copies {@code bucket.amountLimit} into defaultLimit, and the one
 * LimitSourceProvider hands it back unchanged. So there is a single number
 * today, read two ways.
 *
 * Add a second provider -- an employer override, a per-member grant -- and the
 * engine's answer starts to differ from the bucket's own field, while the
 * drawer keeps showing the field. Nothing about that failure is visible: the
 * number is plausible, it is just not the one a claim will be judged against.
 *
 * So this test fails the moment a second provider appears. When that happens
 * the fix is to make MemberLimitDetailService resolve limits through
 * EffectiveLimitResolver, not to relax this assertion.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BucketLimitHasASingleSourceTest extends PostgresIntegrationTestBase {

    @Autowired private List<LimitSourceProvider> providers;

    @Test
    @DisplayName("exactly one limit source, and it passes the bucket's own limit through")
    void bucketLimitStillHasASingleSource() {
        assertThat(providers)
                .as("MemberLimitDetailService reads bucket.amountLimit directly; a second "
                        + "provider would make the engine resolve something else and the "
                        + "drawer would keep showing a limit no decision uses")
                .hasSize(1);
        assertThat(providers.get(0))
                .isInstanceOf(PolicyDefaultLimitSourceProvider.class);
    }
}
