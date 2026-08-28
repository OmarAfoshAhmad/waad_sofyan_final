package com.waad.tba.modules.benefitpolicy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;

import lombok.RequiredArgsConstructor;

/**
 * The only component permitted to write to the consumption ledger.
 *
 * Two services own the two life cycles -- BenefitBucketLedgerService for
 * claims, PreAuthReservationLedgerService for pre-authorization holds -- and
 * they stay separate because their locks, their idempotency keys and their
 * ordering genuinely differ. Merging them would be a separation-of-concerns
 * error. But leaving both calling the repository directly would mean the
 * ledger has no single point where its invariants can be enforced, and every
 * future service would be one more place to audit.
 *
 * So: two semantic writers above one save gate.
 *
 * This is deliberately NOT a save(entity) facade -- that would re-expose the
 * repository under a new name. Each operation below can only build a movement
 * of one legitimate shape, so an illegitimate combination cannot be expressed
 * by a caller at all.
 *
 * It resolves nothing, calculates nothing and takes no business locks. Those
 * are the callers' work; this validates the shape and appends.
 *
 * MANDATORY propagation: a ledger movement must never open a transaction of
 * its own. If it did, a hold could commit while the approval that justified it
 * rolled back -- money removed from a member with nothing recording why.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BenefitConsumptionEntryWriter {

    private final BenefitBucketConsumptionRepository consumptionRepository;

    /** A claim consuming a bucket. */
    public BenefitBucketConsumption appendClaimCommit(
            Claim claim, ClaimLine line, BenefitPolicy policy, Long memberId, BenefitLimitBucket bucket,
            LocalDate periodStart, LocalDate periodEnd, BigDecimal amount, int times,
            Integer calculationVersion, String idempotencyKey) {

        requireClaimShape(claim, line, bucket, idempotencyKey);
        requireNonNegativeDimensions(amount, times);
        requireSomeMovement(amount, times);

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .claim(claim).claimLine(line).policy(policy).memberId(memberId).bucket(bucket)
                .periodStart(periodStart).periodEnd(periodEnd)
                .approvedAmount(amount).timesConsumed(times)
                .status(BenefitBucketConsumption.Status.COMMITTED)
                .sourceType(BenefitBucketConsumption.SourceType.CLAIM)
                .limitScope(BenefitBucketConsumption.LimitScope.BUCKET)
                .calculationVersion(calculationVersion).idempotencyKey(idempotencyKey)
                .committedAt(LocalDateTime.now()).build());
    }

    /**
     * A claim consuming the policy's GENERAL ceiling.
     *
     * One movement per line, never per bucket: a line can map to several
     * buckets, and the general ceiling is spent once by the line whatever
     * those buckets are. Deriving the general figure by summing bucket rows
     * would count the same money as many times as it was categorised.
     *
     * It carries no bucket because the general ceiling is a scope rather than
     * a bucket -- there is no row for it to point at.
     */
    public BenefitBucketConsumption appendClaimGeneralCommit(
            Claim claim, ClaimLine line, BenefitPolicy policy, Long memberId,
            LocalDate periodStart, LocalDate periodEnd, BigDecimal amount,
            Integer calculationVersion, String idempotencyKey) {

        if (claim == null || line == null) {
            throw new IllegalArgumentException("A claim movement needs both its claim and its line");
        }
        requireKey(idempotencyKey);
        requireNonNegativeDimensions(amount, 0);
        requireSomeMovement(amount, 0);

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .claim(claim).claimLine(line).policy(policy).memberId(memberId)
                .periodStart(periodStart).periodEnd(periodEnd)
                .approvedAmount(amount).timesConsumed(0)
                .status(BenefitBucketConsumption.Status.COMMITTED)
                .sourceType(BenefitBucketConsumption.SourceType.CLAIM)
                .limitScope(BenefitBucketConsumption.LimitScope.POLICY_GENERAL)
                .calculationVersion(calculationVersion).idempotencyKey(idempotencyKey)
                .committedAt(LocalDateTime.now()).build());
    }

    /** The compensating movement that releases a claim's consumption. */
    public BenefitBucketConsumption appendClaimReversal(
            BenefitBucketConsumption original, BigDecimal amount, int times,
            String idempotencyKey, LocalDateTime reversedAt) {

        requireOriginal(original, BenefitBucketConsumption.SourceType.CLAIM);
        requireNonNegativeDimensions(amount, times);
        requireSomeMovement(amount, times);

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .claim(original.getClaim()).claimLine(original.getClaimLine()).policy(original.getPolicy())
                .memberId(original.getMemberId()).bucket(original.getBucket())
                .periodStart(original.getPeriodStart()).periodEnd(original.getPeriodEnd())
                .approvedAmount(amount).timesConsumed(times)
                .status(BenefitBucketConsumption.Status.REVERSED)
                .reversalReason(BenefitBucketConsumption.ReversalReason.CLAIM_REVERSAL)
                .sourceType(BenefitBucketConsumption.SourceType.CLAIM)
                .limitScope(original.getLimitScope())
                .calculationVersion(original.getCalculationVersion()).idempotencyKey(idempotencyKey)
                .reversalOf(original).reversedAt(reversedAt).build());
    }

    /**
     * A pre-authorization holding limit. Both dimensions travel on one
     * movement, matching the snapshot: a bucket can cap money and occurrences
     * at once, and they are never added.
     */
    public BenefitBucketConsumption appendPreAuthReservation(
            Long preauthId, Long preauthLineId, BenefitPolicy policy, Long memberId,
            Long memberPolicyAssignmentId,
            BenefitLimitBucket bucket, BenefitBucketConsumption.LimitScope scope,
            LocalDate periodStart, LocalDate periodEnd, BigDecimal amount, int times,
            Integer calculationVersion, String idempotencyKey) {

        requirePreAuthShape(preauthId, preauthLineId, bucket, scope, idempotencyKey);
        requireNonNegativeDimensions(amount, times);
        // A hold of nothing in both dimensions records a decision, not a
        // movement. The snapshot already documents that; the ledger carries
        // only real financial or numerical effect.
        requireSomeMovement(amount, times);

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .policy(policy).memberId(memberId).bucket(bucket)
                .preauthId(preauthId).preauthLineId(preauthLineId)
                .memberPolicyAssignmentId(memberPolicyAssignmentId)
                .periodStart(periodStart).periodEnd(periodEnd)
                .approvedAmount(amount).timesConsumed(times)
                .status(BenefitBucketConsumption.Status.RESERVED)
                .sourceType(BenefitBucketConsumption.SourceType.PREAUTH)
                .limitScope(scope)
                .calculationVersion(calculationVersion).idempotencyKey(idempotencyKey)
                .build());
    }

    /** The compensating movement that gives a hold back. */
    public BenefitBucketConsumption appendPreAuthRelease(
            BenefitBucketConsumption original, BigDecimal amount, int times,
            BenefitBucketConsumption.ReversalReason reason, String idempotencyKey,
            LocalDateTime reversedAt) {

        requireOriginal(original, BenefitBucketConsumption.SourceType.PREAUTH);
        requireNonNegativeDimensions(amount, times);
        requireSomeMovement(amount, times);
        if (reason != BenefitBucketConsumption.ReversalReason.PREAUTH_RELEASE
                && reason != BenefitBucketConsumption.ReversalReason.PREAUTH_EXPIRY
                && reason != BenefitBucketConsumption.ReversalReason.PREAUTH_CANCELLATION
                && reason != BenefitBucketConsumption.ReversalReason.PREAUTH_CONVERSION_RELEASE) {
            throw new IllegalArgumentException(
                    "A pre-authorization release must state a pre-authorization reason, not " + reason);
        }

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .policy(original.getPolicy()).memberId(original.getMemberId()).bucket(original.getBucket())
                .preauthId(original.getPreauthId()).preauthLineId(original.getPreauthLineId())
                .memberPolicyAssignmentId(original.getMemberPolicyAssignmentId())
                .periodStart(original.getPeriodStart()).periodEnd(original.getPeriodEnd())
                .approvedAmount(amount).timesConsumed(times)
                .status(BenefitBucketConsumption.Status.REVERSED)
                .reversalReason(reason)
                .sourceType(BenefitBucketConsumption.SourceType.PREAUTH)
                .limitScope(original.getLimitScope())
                .calculationVersion(original.getCalculationVersion()).idempotencyKey(idempotencyKey)
                .reversalOf(original).reversedAt(reversedAt).build());
    }

    /**
     * Spending this system never saw, carried in from whatever kept the
     * member's balance before it.
     *
     * COMMITTED, not RESERVED: a balance brought forward is already spent. It
     * names no claim, because no claim of ours produced it -- that is the
     * whole point of the source type, and the reason a fabricated claim is not
     * an acceptable substitute.
     *
     * @param bucket null for the policy's general ceiling, which is a scope
     *               rather than a bucket and has no row to point at
     */
    public BenefitBucketConsumption appendOpeningConsumption(
            BenefitPolicy policy, Long memberId, Long openingBatchId, BenefitLimitBucket bucket,
            BenefitBucketConsumption.LimitScope scope, LocalDate periodStart, LocalDate periodEnd,
            BigDecimal amount, int times, String idempotencyKey) {

        requireOpeningShape(openingBatchId, bucket, scope, idempotencyKey);
        requireNonNegativeDimensions(amount, times);
        requireSomeMovement(amount, times);

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .policy(policy).memberId(memberId).bucket(bucket)
                .openingBatchId(openingBatchId)
                .periodStart(periodStart).periodEnd(periodEnd)
                .approvedAmount(amount).timesConsumed(times)
                .status(BenefitBucketConsumption.Status.COMMITTED)
                .sourceType(BenefitBucketConsumption.SourceType.OPENING_IMPORT)
                .limitScope(scope)
                .calculationVersion(1).idempotencyKey(idempotencyKey)
                .committedAt(LocalDateTime.now()).build());
    }

    /**
     * The compensating movement that corrects an imported balance.
     *
     * It carries the CORRECTING batch, not the corrected one. A release
     * inherits its hold's attribution because the two are one act; a
     * correction is a later decision by a named person for a stated reason,
     * and filing it under the original batch would hide that anyone
     * intervened. The database enforces the difference.
     */
    public BenefitBucketConsumption appendOpeningCorrection(
            BenefitBucketConsumption original, Long correctingBatchId, BigDecimal amount, int times,
            String idempotencyKey, LocalDateTime reversedAt) {

        requireOriginal(original, BenefitBucketConsumption.SourceType.OPENING_IMPORT);
        requireNonNegativeDimensions(amount, times);
        requireSomeMovement(amount, times);
        if (correctingBatchId == null) {
            throw new IllegalArgumentException("A correction must name the batch that performed it");
        }
        if (correctingBatchId.equals(original.getOpeningBatchId())) {
            throw new IllegalArgumentException(
                    "A correction may not be filed under the batch it corrects");
        }

        return consumptionRepository.save(BenefitBucketConsumption.builder()
                .policy(original.getPolicy()).memberId(original.getMemberId()).bucket(original.getBucket())
                .openingBatchId(correctingBatchId)
                .periodStart(original.getPeriodStart()).periodEnd(original.getPeriodEnd())
                .approvedAmount(amount).timesConsumed(times)
                .status(BenefitBucketConsumption.Status.REVERSED)
                .reversalReason(BenefitBucketConsumption.ReversalReason.OPENING_CORRECTION)
                .sourceType(BenefitBucketConsumption.SourceType.OPENING_IMPORT)
                .limitScope(original.getLimitScope())
                .calculationVersion(original.getCalculationVersion()).idempotencyKey(idempotencyKey)
                .reversalOf(original).reversedAt(reversedAt).build());
    }

    /**
     * One flush per operational unit, not one per line. Batching is what keeps
     * a many-line claim from issuing a round trip per bucket, and the caller
     * translates constraint violations around this single point.
     */
    public void flush() {
        consumptionRepository.flush();
    }

    // ── shape rules ──────────────────────────────────────────────────────

    private void requireClaimShape(Claim claim, ClaimLine line, BenefitLimitBucket bucket, String key) {
        if (claim == null || line == null) {
            throw new IllegalArgumentException("A claim movement needs both its claim and its line");
        }
        if (bucket == null) {
            throw new IllegalArgumentException("A claim movement consumes a bucket");
        }
        requireKey(key);
    }

    private void requireOpeningShape(Long openingBatchId, BenefitLimitBucket bucket,
            BenefitBucketConsumption.LimitScope scope, String key) {
        if (openingBatchId == null) {
            throw new IllegalArgumentException(
                    "An opening balance must name the batch that imported it");
        }
        if (scope == null) {
            throw new IllegalArgumentException("A movement must name the ceiling it measures against");
        }
        if (scope == BenefitBucketConsumption.LimitScope.BUCKET && bucket == null) {
            throw new IllegalArgumentException("A bucket-scoped movement needs its bucket");
        }
        if (scope == BenefitBucketConsumption.LimitScope.POLICY_GENERAL && bucket != null) {
            throw new IllegalArgumentException(
                    "A general-ceiling movement measures the policy, not a bucket");
        }
        requireKey(key);
    }

    private void requirePreAuthShape(Long preauthId, Long preauthLineId, BenefitLimitBucket bucket,
            BenefitBucketConsumption.LimitScope scope, String key) {
        if (preauthId == null || preauthLineId == null) {
            throw new IllegalArgumentException(
                    "A pre-authorization movement needs both its head and its line");
        }
        if (scope == null) {
            throw new IllegalArgumentException("A movement must name the ceiling it measures against");
        }
        // Mirrors the database: the general ceiling is synthetic and has no
        // bucket, and a bucket movement must name one.
        if (scope == BenefitBucketConsumption.LimitScope.POLICY_GENERAL && bucket != null) {
            throw new IllegalArgumentException("A general-ceiling movement must not carry a bucket");
        }
        if (scope == BenefitBucketConsumption.LimitScope.BUCKET && bucket == null) {
            throw new IllegalArgumentException("A bucket movement must name its bucket");
        }
        requireKey(key);
    }

    private void requireOriginal(BenefitBucketConsumption original,
            BenefitBucketConsumption.SourceType expectedSource) {
        if (original == null || original.getId() == null) {
            throw new IllegalArgumentException("A compensating movement must name a posted original");
        }
        if (original.getStatus() == BenefitBucketConsumption.Status.REVERSED) {
            throw new IllegalArgumentException("A compensating movement cannot itself be reversed");
        }
        if (original.getSourceType() != expectedSource) {
            throw new IllegalArgumentException("A compensating movement must match its original's source: "
                    + original.getSourceType() + " cannot be released as " + expectedSource);
        }
    }

    private void requireNonNegativeDimensions(BigDecimal amount, int times) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("A movement's amount must be present and non-negative");
        }
        if (times < 0) {
            throw new IllegalArgumentException("A movement's occurrence count must be non-negative");
        }
    }

    private void requireSomeMovement(BigDecimal amount, int times) {
        if (amount.signum() == 0 && times == 0) {
            throw new IllegalArgumentException(
                    "A movement with neither an amount nor an occurrence records nothing");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Every movement needs an idempotency key");
        }
    }
}
