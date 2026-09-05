# P1.5.1a — Batch-Aware Limit Evaluation Design

**STATUS: DESIGN + isolated, unwired skeleton + tests. No production code touched.**

Prerequisite reading: `P1_5_1_COVERAGE_ENGINE_WIRING_DESIGN.md` (§7.1-7.4
raised the three gaps this document resolves). `P1.5.1 DESIGN = OPEN` until
this document's three findings are folded into a reviewed implementation
plan — this document does not itself close P1.5.1; it closes the batch-
awareness gap (§7.3) and settles the DAYS and countingMethod questions
(§7.2, §7.4) with proof, not assumption.

---

## 1. Three questions, answered from the code, not from the diagram

### 1.1 countingMethod scope — CONFIRMED bucket-level, not line-level

`BenefitLimitBucket.countingMethod` (`BenefitLimitBucket.java:39-40`) is a
column on the BUCKET entity. `BenefitBucketLimitService.LimitSnapshot`
carries one `countingMethod` per snapshot, read off that bucket
(`BenefitBucketLimitService.java` constructors). Since one rule can link to
several buckets through `benefit_rule_buckets` (confirmed structurally —
this is exactly how `pregnancy_buckets`/`benefit_rule_buckets` joins work
elsewhere in the schema), **a single line can legally present two buckets
with two different counting methods**, and today's live engine
(`CoverageEngineService.computeBucketUsage`) already reads
`limit.countingMethod()` PER BUCKET, inside its per-bucket loop
(`CoverageEngineService.java:319`) — never once per line.

**Conclusion: `UnifiedLimitInput.countingMethod` as a single field is a
confirmed structural mismatch, not a hypothetical one.** The correct home
for it is on `BucketLimitSnapshot` (it is already effectively there in
spirit, since `BenefitBucketLimitService.LimitSnapshot` carries it
per-bucket — P1.5.0's adapter just never copied it onto the P1.3 contract
type).

**This is NOT fixed in this round.** Moving `countingMethod` onto
`BucketLimitSnapshot` requires `UnifiedLimitResolver.resolve` to decide
divisibility PER AXIS instead of once per call (`occurrenceDimensionExists`,
`divisible` in `UnifiedLimitResolver.java:74-106` currently read
`input.countingMethod()` a single time). That is a resolver-shape change,
not a batch-context addition, and deserves its own reviewed round —
tracked here as **P1.5.2 — per-axis counting method**, a confirmed
prerequisite for wiring any rule where two applicable buckets genuinely
carry different counting methods. Whether that shape is ever actually
*configured* in production (vs. structurally possible but unused) is a data
question, not a code question, and is listed as the first open item in §5.

### 1.2 DAYS semantics — CONFIRMED per bucket, per resolved period, per distinct service date — never "per line"

`BenefitBucketLimitService` computes, per applicable bucket:
- `usedDays` via `consumptionRepository.countCommittedServiceDays(memberId,
  bucketId, periodStart, periodEnd, excludeClaimId)` — a COUNT of distinct
  committed service dates within the period (`BenefitBucketLimitService.java:58`).
- `serviceDayAlreadyUsed` via `consumptionRepository.existsCommittedForServiceDay(
  memberId, bucketId, serviceDate, excludeClaimId)` — whether TODAY's
  service date is already among those committed dates
  (`BenefitBucketLimitService.java:60`,
  `BenefitBucketConsumptionRepository.java:351-354`).

Both are scoped to `(memberId, bucketId, period)` — **there is no "per
line" dimension anywhere in this query.** A batch of five lines against the
same bucket, same service date, only ever spends ONE day, which is exactly
why `computeBucketUsage` checks `!limit.serviceDayAlreadyUsed() &&
!acc.addedDay` (`CoverageEngineService.java:325`) before ever treating the
day as newly consumed — `serviceDayAlreadyUsed` answers "did an EARLIER
claim already spend this date," and `acc.addedDay` answers "did an earlier
LINE IN THIS SAME BATCH already spend it."

**Conclusion: `requestedDays` is not a per-line quantity input at all.** It
is better modeled as a per-bucket boolean derived by the caller: *does this
bucket's DAYS axis still need today's date accounted for, given what the DB
already committed AND what earlier lines in this same claim/batch already
consumed?* Adding a naive `requestedDays = 1` per line (the original,
now-corrected assumption in `P1_5_1_COVERAGE_ENGINE_WIRING_DESIGN.md` §3)
would double-count every batch with more than one line touching a
days-limited bucket on the same date. This document's §2 design derives
that boolean in the batch context, not in `UnifiedLimitInput`.

### 1.3 BatchUsageAccumulator — has no equivalent in UnifiedLimitResolver, confirmed

`CoverageEngineService.BatchUsageAccumulator` (`CoverageEngineService.java:589-593`)
holds `addedCount` (occurrences), `addedAmount` (money), `addedDay`
(boolean), keyed by `AccumulatorKey(bucketId, periodStart, periodEnd,
serviceDate, countingMethod)` (`CoverageEngineService.java:523-525`), and is
threaded through every line of one `calculateBulk`/`ClaimMapper` call so
line 2 sees line 1's consumption before its own limit is checked. Neither
`BucketLimitSnapshotAdapter` nor `UnifiedLimitResolver` has any concept of
this — both are single-line and stateless by design (P1.4/P1.5.0's own
explicit review gate). §2 below is the replacement.

---

## 2. The replacement: `ClaimLimitEvaluationContext`

Per the direction given for this round: **`UnifiedLimitResolver` stays
pure, single-line, stateless.** A new, separate, equally pure/isolated type
sits between the base (DB-only) snapshots and the resolver, carrying
in-memory pending consumption across the lines of one claim/batch:

```
DB balances
    ↓
BucketLimitSnapshotAdapter.buildForNormalClaim   (base, DB-only snapshots)
    ↓
ClaimLimitEvaluationContext.adjustForNextLine(base)   [NEW]
    ↓
Line 1 → UnifiedLimitResolver.resolve(input, adjustedSnapshots)   [UNCHANGED]
    ↓
ClaimLimitEvaluationContext.recordLineConsumption(baseSnapshotsUsed, decision)   [NEW]
    ↓
Line 2 → adjustForNextLine(base) again → sees Line 1's pending usage
    ↓
UnifiedLimitResolver.resolve(...)
    ↓
...
```

### 2.1 Key shape

```java
record PendingKey(Long bucketId, LocalDate periodStart, LocalDate periodEnd)
```

Deliberately WITHOUT `countingMethod` or `serviceDate` in the key, unlike
today's `AccumulatorKey`:
- `countingMethod` is a fixed property of the bucket for the lifetime of
  one evaluation, so keying on it separately cannot ever produce two
  different pending buckets for the same `(bucketId, period)` — it would
  only be defensive duplication.
- `serviceDate` is dropped for the same reason `CoverageEngineService`'s own
  comment already gives (`CoverageEngineService.java:518-521`): today's API
  resolves exactly one service date per call, so it never varies within one
  context's lifetime. Kept out here rather than copied defensively, since
  carrying an assumption forward unexamined is exactly what this whole
  round exists to stop doing.

### 2.2 What "shared/parent bucket" correctness requires

The key is `(bucketId, periodStart, periodEnd)` — the SAME shape
`BucketLimitSnapshot` already carries per axis. `BucketLimitSnapshotAdapter`
already produces one row per bucket the applicable chain touches, INCLUDING
a shared/parent bucket reached by `BucketChainWalker` (P1.5.0a's own S6
test proved exactly this: a bucket shared across two applicable rows is
still a single, correctly-keyed row). Because `adjustForNextLine` and
`recordLineConsumption` iterate over the FULL snapshot list — not just the
one "binding" bucket — a parent/shared bucket that line 1 touches gets its
pending usage recorded exactly like a directly-linked one, and line 2 (a
different category sharing that same parent) sees it correctly. This
directly answers the requirement: *"الـAccumulator لا يحدث فقط
bindingBucket، بل كل الأوعية التي يجب أن يستهلكها القرار حسب سلسلة
الاستهلاك القانونية"* — every applicable snapshot for the line is recorded,
never only the tightest one.

### 2.3 Per-axis adjustment and recording rules

**AMOUNT**: `adjustedRemaining = baseRemaining - pendingAmount`,
`adjustedCommitted = baseCommitted + pendingAmount`. Recording adds
`decision.bindingAvailableAmount()` to every AMOUNT-axis snapshot the line
touched — the same approved money is posted against every bucket in the
chain simultaneously (mirrors `BenefitBucketLedgerService.addWithParents`'s
real write semantics: parents and children are independent ceilings
measuring the same transaction, never a split).

**TIMES**: identical shape, integer arithmetic, recording adds
`decision.approvedQuantity()`.

**DAYS**: NOT a running total. `dayConsumedThisBatch` is a one-shot flag
per `(bucketId, period)`. `adjustForNextLine` does not need to touch the
snapshot's `remaining` for DAYS at all — instead, the caller asks
`context.dayAlreadyConsumedInBatch(bucketId, periodStart, periodEnd)`
BEFORE building `UnifiedLimitInput.requestedDays` for a line, exactly
mirroring today's `!limit.serviceDayAlreadyUsed() && !acc.addedDay` check.
Recording sets the flag true the first time any line's decision has
`approvedDays() > 0` for that bucket; it is never unset within one context's
lifetime (a context is scoped to one claim/batch evaluation, discarded
after).

### 2.4 `excludeClaimId` / re-edit interaction

`excludeClaimId` is resolved once, upstream, when `BucketLimitSnapshotAdapter`
builds the BASE snapshots (already true today — P1.5.0a's S7 test covers
this). `ClaimLimitEvaluationContext` never re-reads the DB and has no
`excludeClaimId` of its own — it only tracks pending usage the CURRENT
evaluation run itself produces. Re-editing an existing claim naturally
works exactly like re-evaluating a fresh one: the base snapshots already
exclude the claim being edited, and the context accumulates only this
evaluation's own lines, so a line being re-priced does not see its own
prior (superseded) contribution counted twice, and never needs to.

### 2.5 Line order

The design is order-sensitive by construction (line 1 evaluated before line
2 sees its consumption) — matching today's `BatchUsageAccumulator`, which
is also order-sensitive (iterates `request.getLines()` in list order,
`CoverageEngineService.java:54`). This is not a new property introduced
here; it is preserved, not changed. Whether callers ever rely on order-independent
results is a UX question (does the frontend expect the same numbers
regardless of line entry order?), listed as an open item in §5, not decided here.

---

## 3. Isolated skeleton delivered this round

`backend/src/main/java/com/waad/tba/modules/benefitpolicy/service/unifiedlimit/ClaimLimitEvaluationContext.java`
— pure Java, no Spring annotation, no repository dependency, exactly like
`UnifiedLimitResolver` itself. Not referenced from any production call site.

Tests: `ClaimLimitEvaluationContextTest.java` — BG1 through BG5, built on
hand-fixtured `BucketLimitSnapshot`s (same style as
`UnifiedLimitResolverTest`), feeding `UnifiedLimitResolver.resolve` for
each simulated line in sequence.

---

## 4. G-case mapping for this round

| Case | What it proves |
|---|---|
| BG1 | Two lines, same TIMES bucket, remaining=3: line1 requests 2 → approved 2; line2 requests 2 → approved only 1 (not another 2) |
| BG2 | Two lines, same AMOUNT bucket, remaining=500: line1=300 approved fully; line2=300 requested but only 200 available |
| BG3 | Two different categories/rules resolving to the same SHARED parent bucket — line2 sees line1's consumption against the shared parent even though their direct/child buckets differ |
| BG4 | Two lines, same service day, same DAYS bucket — only the first spends the day; the second is not blocked by the day limit and does not double-spend it |
| BG5 | A bucket already carrying a DB-level RESERVED amount from another decision, on top of which two lines in this claim consume further — proves `activeReserved` (from the adapter) and in-batch `pendingAmount` (from this context) subtract independently, not doubled or dropped |

---

## 5. What remains open after this round (explicitly not decided here)

1. **P1.5.2 — per-axis counting method.** Confirmed necessary in principle
   (§1.1); whether it is REACHABLE with today's actual bucket/rule
   configurations (i.e., does any live rule attach two buckets with
   different counting methods) is a data question for whoever runs the
   implementation gate, not settled by this document.
2. **Line-order sensitivity** (§2.5) — acceptable if it matches today's
   behavior exactly (it does, by construction), but worth a one-line
   confirmation with whoever owns the frontend batch-entry UX before
   wiring, since a user re-ordering lines in a draft could see different
   numbers, exactly as they can today.
3. This document does not touch `CoverageEngineService`,
   `BucketLimitSnapshotAdapter`, or `UnifiedLimitResolver`. Wiring
   `ClaimLimitEvaluationContext` into `evaluateLine` is still a live-wiring
   change requiring its own gate, same as everything else in P1.5.1.

**P1.5.1 DESIGN remains OPEN** until item 1 above is resolved (or explicitly
deferred with a documented reason) — this round closes the batch-awareness
gap and the DAYS/countingMethod semantic questions with proof, which was
this round's whole purpose.
