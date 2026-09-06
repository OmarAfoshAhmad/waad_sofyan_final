package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one, shared "walk a bucket and its parentBucket ancestors" primitive.
 *
 * Before this class existed, {@link BenefitBucketLimitService#findApplicable}
 * (the live claim-coverage path) and the now-retired {@code ApplicableLimitResolver}
 * (P1.12.4: deleted, having reached zero live callers once Claims and PreAuth
 * both moved onto the canonical {@code UnifiedLimitResolver}/
 * {@code BucketLimitSnapshotAdapter} pipeline) each carried their OWN copy of
 * this walk, and disagreed: only {@code ApplicableLimitResolver} detected a
 * cyclic parentBucket chain. A cycle in the live path did not throw -- it
 * hung the request forever, since the live loop's only stop condition was
 * {@code current == null}.
 *
 * P1 review found this and was explicit: mirroring the live path's weaker
 * behavior into a new audit tool would launder an unsafe shortcut as if it
 * were a deliberate rule. So this walk is the hardened version -- cycle
 * detection is not optional -- and both {@link BenefitBucketLimitService}
 * and the gap audit ({@code BenefitPolicyGapAuditService}) call it, rather
 * than each keeping their own copy that can drift further apart.
 *
 * The BUCKET_POLICY_MISMATCH check {@code ApplicableLimitResolver} used to
 * perform separately, per bucket, after calling this walker, now lives in
 * {@code BucketLimitSnapshotAdapter.validateAndScope} -- the canonical
 * adapter's own equivalent, returning a structured {@code BLOCKED} result
 * instead of a raw exception (P1.12.1/P1.12.3's PA7 finding).
 */
final class BucketChainWalker {
    private BucketChainWalker() {}

    /**
     * {@code start}, then {@code start.getParentBucket()}, and so on until
     * null. Fails closed on a cycle instead of looping forever -- the one
     * behavior change from BenefitBucketLimitService's original inline
     * loop, and a pure safety net: for any non-cyclic chain (everything
     * that already worked), the returned list is identical to what that
     * loop collected.
     */
    static List<BenefitLimitBucket> chainFrom(BenefitLimitBucket start) {
        List<BenefitLimitBucket> chain = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        BenefitLimitBucket current = start;
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException(
                        "BUCKET_HIERARCHY_CYCLE: bucket id=" + current.getId() + " is its own ancestor");
            }
            chain.add(current);
            current = current.getParentBucket();
        }
        return chain;
    }
}
