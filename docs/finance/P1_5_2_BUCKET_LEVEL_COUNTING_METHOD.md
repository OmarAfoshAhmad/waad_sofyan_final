# P1.5.2 — countingMethod Moves to BucketLimitSnapshot

**STATUS: implemented as an isolated change to the still-unwired
`UnifiedLimitResolver`/`BucketLimitSnapshot`/`BucketLimitSnapshotAdapter`
skeleton. No production call site references any of these types yet — this
is exactly as safe to change as everything else in the P1.4/P1.5 skeleton.**

Amends `P1_UNIFIED_LIMIT_DECISION_CONTRACT.md` (P1.3) — see that document's
§6 for the contract-level amendment text. This document is the
implementation record: what changed, why the golden cases still hold, and
the new CM1-CM5 cases that prove the genuinely new behavior.

---

## 1. What changed

- `BucketLimitSnapshot` gained a `countingMethod` field (every row already
  carried one bucket's data; it simply did not carry that bucket's counting
  method before).
- `UnifiedLimitInput.countingMethod` was removed. `UnifiedLimitInput` no
  longer has an opinion on divisibility at all -- that question is answered
  per bucket, from data the caller already has (the bucket's own
  `LimitSnapshot.countingMethod()`), not asserted once for the whole line.
- `UnifiedLimitResolver.resolve` computes quantity affordability PER TIMES
  SNAPSHOT (each using its own `countingMethod` against its own
  `remaining`, combined with the single shared AMOUNT-axis capacity), then
  takes the tightest result across buckets -- exactly the same pattern
  already used for `remaining` itself (`reduceAxis`'s "never let one bucket's
  number leak into another's calculation, only the final tightest value
  crosses" rule, generalized from remaining to approved-quantity).
- `BucketLimitSnapshotAdapter` populates `countingMethod` on every TIMES
  (and AMOUNT, for symmetry/future use) row from
  `LimitSnapshot.countingMethod()` -- already available, no new query.

## 2. Why every existing golden case (G1-G8) still holds

The per-bucket formula was checked by hand against every existing fixture
before being implemented, not after:

| Case | Bucket(s) | Old single-value calc | New per-bucket calc | Match? |
|---|---|---|---|---|
| G2 (Physio) | 1 TIMES bucket, EACH_UNIT | divisible, tightest=min(3,2)=2 | same bucket, same formula | Identical |
| G4 | 1 bucket carrying both AMOUNT+TIMES, EACH_UNIT | divisible, combinedCapacity=min(4,2)=2 | same bucket's own countingMethod, same combinedCapacity | Identical |
| G5 | 1 TIMES bucket, EACH_UNIT | divisible, tightest=min(8,6)=6 | same | Identical |
| G7 | 1 TIMES bucket, EACH_UNIT | divisible, tightest=min(8,8)=8 | same | Identical |
| G8 | 1 TIMES bucket, PER_VISIT | atomic, fits=false → 0 | same bucket's own atomic rule → 0 | Identical |

A single-bucket case is mathematically a one-iteration loop of the new
general formula -- there was no scenario where collapsing the old
"one global countingMethod" special case into "N buckets, N=1" could change
a result, and the table above confirms it did not, case by case.

## 3. The new behavior CM1-CM5 prove

**CM1** -- one divisible (EACH_UNIT) bucket alone: requested 3, remaining 2
→ approved 2 (this is just G2's shape restated as its own named case, since
after this change G2 IS a CM1 instance).

**CM2** -- one atomic (PER_VISIT) bucket alone: requested 3, remaining 2 →
approved 0 (G8's shape, same reasoning).

**CM3 -- the case that did not exist before this round.** Two TIMES
snapshots on the SAME line, bucket A divisible (remaining 2) and bucket B
atomic (remaining 2), requested 3, no AMOUNT axis configured (unlimited
money capacity):
- Bucket A alone (divisible): `min(3, min(2, ∞)) = 2`.
- Bucket B alone (atomic): `3 <= min(2, ∞)`? No → `0`.
- Combined (tightest across buckets): `min(2, 0) = 0`.

Neither bucket's method leaks into the other's calculation -- A's divisible
math still produces 2 on its own terms, B's atomic math still produces 0 on
its own terms, and only the two ALREADY-COMPUTED results are compared. This
is the structural proof the review asked for: a bucket's countingMethod
governs only its own contribution.

**CM4** -- AMOUNT only, no TIMES snapshot at all: still routes through the
`!occurrenceDimensionExists` branch (now `timesSnapshots.isEmpty()`)
exactly as G1 does. A bucket carrying only an AMOUNT limit never has a
`countingMethod` opinion applied to it -- the field simply never enters the
calculation on this path, so a database default counting method on such a
bucket cannot spuriously fragment a continuous money ceiling into units.

**CM5** -- combines with P1.5.1a's `ClaimLimitEvaluationContext`: two lines,
two different buckets with two different counting methods, batch-adjusted
between lines. Proves the per-bucket countingMethod survives
`adjustForNextLine`'s snapshot rebuilding (the adjusted snapshot copies
`countingMethod` through unchanged -- confirmed by construction, since
`withCommittedAndRemaining` only overwrites `committed`/`remaining`).

## 4. What this does NOT change

- `LimitAxis` (the reduced per-axis record in `UnifiedLimitDecision`) still
  carries no `countingMethod` -- it remains a pure numeric summary
  (`configured/committed/reserved/remaining`) for reporting. Divisibility is
  consumed entirely inside `resolve()`'s quantity section and never needs to
  leave as a decision field.
- `bindingBucketId`/`bindingConstraintType` reporting is now MORE precise
  than before (it identifies the actual bucket whose own calculation was
  tightest, not "the first applied bucket" as a placeholder), which is a
  side-effect improvement, not a scope change -- the old placeholder was
  never asserted on by any existing test in a way this breaks.
- `DAYS` continues to have no `countingMethod` concept at all (always
  atomic, per P1.3 §1.3) -- unaffected by this change.
