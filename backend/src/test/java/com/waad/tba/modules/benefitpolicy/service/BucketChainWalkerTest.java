package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;

/**
 * The one shared bucket-chain walk, used by the live coverage path
 * (BenefitBucketLimitService), the not-yet-wired ApplicableLimitResolver,
 * and BenefitPolicyGapAuditService. Before this class existed, the live
 * path's own copy of this walk had no cycle guard at all -- a cyclic
 * parentBucket chain would hang the request forever, not fail. That is the
 * one behavior this test pins as non-negotiable.
 */
class BucketChainWalkerTest {

    private BenefitLimitBucket bucket(long id, BenefitLimitBucket parent) {
        return BenefitLimitBucket.builder().id(id).code("B" + id).nameAr("وعاء").parentBucket(parent).build();
    }

    @Test
    void aSingleBucketWithNoParentIsJustItself() {
        BenefitLimitBucket only = bucket(1L, null);

        assertThat(BucketChainWalker.chainFrom(only)).containsExactly(only);
    }

    @Test
    void walksUpTheFullParentChainInOrder() {
        BenefitLimitBucket grandparent = bucket(1L, null);
        BenefitLimitBucket parent = bucket(2L, grandparent);
        BenefitLimitBucket child = bucket(3L, parent);

        assertThat(BucketChainWalker.chainFrom(child)).containsExactly(child, parent, grandparent);
    }

    @Test
    void aCycleFailsClosedInsteadOfLoopingForever() {
        BenefitLimitBucket a = bucket(1L, null);
        BenefitLimitBucket b = bucket(2L, a);
        a.setParentBucket(b); // a -> b -> a

        assertThatThrownBy(() -> BucketChainWalker.chainFrom(a))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUCKET_HIERARCHY_CYCLE");
    }

    @Test
    void aBucketThatIsItsOwnParentFailsClosedImmediately() {
        BenefitLimitBucket self = bucket(1L, null);
        self.setParentBucket(self);

        assertThatThrownBy(() -> BucketChainWalker.chainFrom(self))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUCKET_HIERARCHY_CYCLE");
    }
}
