# P1.5.1 — CoverageEngineService ↔ UnifiedLimitResolver Wiring Design

**STATUS: DESIGN ONLY. No production code touched. No wiring performed.**

Written while P1.5.0b is still OPEN (B4/B5 blocked on an unrelated, external,
in-progress migration fix — see the P1.5.0b status thread). This document
exists so the first live wiring, when it happens, is not a discovery
exercise: every call site, field mapping, and risk below was found by
reading the current code, not assumed from the architecture diagram.

---

## 1. The one correction this review makes to the assumed diagram

The plan handed down for P1.5.1 was:

```
CoverageDecisionService
        ↓
build UnifiedLimitInput
        ↓
BucketLimitSnapshotAdapter
        ↓
UnifiedLimitResolver
        ↓
UnifiedLimitDecision
        ↓
CoverageDecision result
```

This is not quite what the code does today. **`CoverageDecisionService.resolve()`
never makes a quantity/day decision at all** — it only:

1. Resolves which `BenefitPolicyRule` applies (category + claim context +
   policy exclusions), and
2. Calls `BenefitBucketLimitService.findApplicable(...)` once, mapping the
   raw `LimitSnapshot`s 1:1 into `CoverageLimitSnapshot`s and attaching them
   to `CoverageDecision.limits` — **as inert data**, not a decision.

(`CoverageDecisionService.java:88-96`)

The actual quantity/times/days decision — the thing `UnifiedLimitResolver`
exists to replace — lives one layer up, in
**`CoverageEngineService.evaluateLine` → `computeUsage` → `computeBucketUsage`**
(`CoverageEngineService.java:141-149`, `287-459`). This method independently
walks `coverageDecision.limitsOrEmpty()` and does its own occurrence-split
and amount-ceiling math using `DivisibleLimitSplitter` — the same splitter
`UnifiedLimitResolver` already reuses, but wired here through a third,
independent implementation.

**Corrected diagram — the real insertion point:**

```
CoverageEngineService.evaluateLine
        │
        ├─▶ coverageDecisionService.resolve(...)      [UNCHANGED — rule resolution only]
        │        → appliedRuleId, coveragePercent, requiresPreApproval
        │
        ├─▶ BucketLimitSnapshotAdapter.buildForNormalClaim(          [NEW call]
        │        policyId, appliedRuleId, memberId, serviceDate,
        │        encounterType, excludeClaimId)
        │        → List<BucketLimitSnapshot>
        │
        ├─▶ UnifiedLimitResolver.resolve(UnifiedLimitInput, snapshots)  [NEW call,
        │        → UnifiedLimitDecision                                 replaces computeUsage/computeBucketUsage]
        │
        └─▶ CoverageResult.builder()...build()          [existing fields, new sources]
```

`CoverageDecisionService` itself does not need to change shape for this —
its contract (`CoverageDecisionRequest` in, `CoverageDecision` out,
`.limits` included) is consumed by two OTHER callers unrelated to the
quantity decision (see §6). Changing it is a bigger, separately-approved
move; P1.5.1 does not require it.

---

## 2. Confirmed: Preview and Save-A already share one entry point

```
CoverageEngineController (/analyze, calculateSingle/calculateBulk)  ─┐
                                                                      ├─▶ CoverageEngineService.evaluateLine
ClaimMapper.processEngineCalculations (ClaimMapper.java:407)        ─┘
```

Both call sites converge on the exact same `evaluateLine` method — this was
verified by reading both call sites (`CoverageEngineController.java:53,73`,
`ClaimMapper.java:407`), not assumed. **One wiring point genuinely covers
both Preview and Save-A**, exactly as the closure plan requires. There is no
third entry point for the normal-claim path (`calculateBulk` and
`calculateSingle` both delegate to `evaluateLine` too —
`CoverageEngineService.java:56,76`).

`PreAuthorizationDecisionBuilder` is a **separate, fourth** consumer of
`CoverageDecisionService.resolve()` (`PreAuthorizationDecisionBuilder.java:252`)
that does its own quantity resolution via `EffectiveLimitResolver` +
`ApplicableCountingLimitResolver` — confirmed unrelated to `evaluateLine`,
untouched by this wiring, exactly as already agreed (P1.5.0b's own domain,
deferred to before P1.12).

---

## 3. UnifiedLimitInput — field-by-field source mapping

All fields are already available at the point `evaluateLine` currently
calls `coverageDecisionService.resolve(...)` — no new lookup needed.

| `UnifiedLimitInput` field | Source in `evaluateLine` today | Notes |
|---|---|---|
| `policyId` | `request.getPolicyId()` | Already on `BulkCoverageEngineRequest`, no lookup (confirmed `BulkCoverageEngineRequest.java:36`) |
| `ruleId` | `appliedRuleId` (from `coverageDecision.appliedRuleOptional()`) | Only available *after* `coverageDecisionService.resolve()` returns — the new calls must come after, not instead of, that resolve |
| `memberId` | `request.getMemberId()` | Already used at line 121 |
| `serviceDate` | `request.getServiceDate()` | — |
| `encounterType` | `request.getEncounterType()` | — |
| `requestedQuantity` | `line.getQuantity()` | Already `quantity` local var, line 95 |
| `requestedDays` | **No explicit field exists today.** `computeBucketUsage` treats a `PER_DAY`/`PER_VISIT` occurrence as an implicit single day (`!acc.addedDay`), never a real day-count input. | **Open mapping decision**: pass `requestedDays = 1` when `countingMethod` is day-like and the day has not already been counted in this batch, else `0`. Needs the exact today's condition (`!limit.serviceDayAlreadyUsed() && !acc.addedDay`) reproduced faithfully — this is the single trickiest field in this mapping and deserves its own scoped review before implementation, not a guess. |
| `countingMethod` | `limit.countingMethod()` off the *constraining* `CoverageLimitSnapshot`, defaulted to `EACH_LINE` | Today this is read per-bucket inside the loop, not once per line — `UnifiedLimitInput` assumes one counting method per decision. Multiple buckets with different counting methods on the same line is already a real shape in `computeBucketUsage`'s loop; `UnifiedLimitResolver` was designed against one axis winning at a time (`reduceAxis`), not one counting method per snapshot list. This needs its own gate before wiring, not a silent assumption. |
| `effectiveUnitPrice` | `effectiveUnitPrice` local var (`resolveEffectiveUnitPrice(...)`, line 104) | — |
| `eligibleAmount` | `effectiveTotal` local var (line 106) | — |
| `excludeClaimId` | `request.getExcludeClaimId()` | Already on `BulkCoverageEngineRequest.java:60`, propagates to Preview (null) and Save (the claim's own id) identically today |
| `reservationMode` | `ReservationEvaluationMode.NORMAL` | This wiring is NORMAL mode only — PREAUTHORIZED_CLAIM stays out of scope here exactly as it stayed out of scope in the adapter itself |
| `preAuthorizationId` / `memberPolicyAssignmentId` | `null` | Not applicable to `evaluateLine`'s normal-claim path |

---

## 4. UnifiedLimitDecision → CoverageResult field mapping

| `CoverageResult` field (current source) | `UnifiedLimitDecision` replacement |
|---|---|
| `limitRefused` (from `UsageComputation.limitRefused()`) | `eligibleAmount - bindingAvailableAmount` (a derived subtraction UnifiedLimitResolver does not itself expose as a field today — needs either a resolver field addition or a one-line derivation at the call site) |
| `usageDetails` (`UsageComputation.usageDetails()`, a rich `UsageDetails` DTO with `usedAmountBeforeLine`, `remainingAmount`, `approvedUnits`, `refusedUnits`, etc.) | Must be rebuilt from `UnifiedLimitDecision`'s `amount()/times()/days()` `LimitAxis` records plus `approvedQuantity/refusedQuantity/approvedDays/refusedDays`. **Every field of `UsageDetails` needs an explicit source before implementation** — this DTO is consumed by the frontend line-level display and is not optional scope. |
| `requiresPreApproval`, `coveragePercent`, `appliedRuleId`, `resolvedCategoryId` | **Unchanged** — sourced from `coverageDecision`, not from the limit decision at all |

---

## 5. The eight questions, answered against the actual code

**ما الذي سيختفي من BenefitBucketLimitService call؟**
Nothing disappears from `BenefitBucketLimitService` itself — `findApplicable`
stays exactly as-is (P1.5.0a already established it as the correct,
gap-free selection source). What *would* change is who calls it: today
`CoverageDecisionService.resolve()` calls it once per line to build
`CoverageDecision.limits`. After wiring, `BucketLimitSnapshotAdapter` would
also need applicable buckets for the same rule/member/date — **this is a
duplicate call risk, not yet resolved (see §7.1)**.

**ما الذي سيبقى كما هو؟**
`CoverageDecisionService.resolve()`'s signature, `CoverageDecisionRequest`,
`CoverageDecision`'s `covered/coveragePercent/appliedRule/source/reasonCode`
fields, `WaadFinancialEngine`, `ClaimFinancialAdjudicationService`,
`PreAuthorizationDecisionBuilder`'s own path, and the two other consumers of
`CoverageDecision.limits` (§6) — none of these are touched by this wiring.

**من يكتب appliedRuleId؟**
`ClaimMapper.java:475` writes `ClaimLine.appliedRuleId` directly from
`CoverageResult.getAppliedRuleId()`, which `CoverageEngineService` sources
from `coverageDecision.appliedRuleOptional()` — unchanged by this wiring,
since `CoverageDecisionService.resolve()` itself is untouched. No second
writer exists (confirmed by grep — `appliedRuleId` appears nowhere else in
the claim module's write paths).

**من يكتب limit snapshots؟**
Neither `CoverageDecisionService` nor `CoverageEngineService` today.
`claim_line_limit_snapshots` rows are built by a `limitSnapshotFactory` fed
from `ClaimFinancialAdjudicationService`'s adjudication result — **Engine
B**, not Engine A (`ClaimFinancialSnapshotService.java:108`). This is the
already-documented P1.1 defect (the snapshot table has zero occurrence
columns because its source has zero occurrence dimension). P1.5.1 does not
change this writer — it is out of scope for a read/decide-only wiring, and
fixing it is its own later step once Engine A and B are unified per ADR-008.

**هل Preview وSave-A يمران من نفس النقطة؟**
Yes — confirmed in §2, by reading both call sites, not assumed.

**هل excludeClaimId محفوظ؟**
Yes — `BulkCoverageEngineRequest.excludeClaimId` flows into `evaluateLine`
identically for both callers today, and `BucketLimitSnapshotAdapter`'s
signature already takes `excludeClaimId` as a first-class parameter
(P1.5.0a), so no new plumbing is needed here.

**هل policyId متاح بدون lookup إضافي؟**
Yes — `request.getPolicyId()` is already read directly at line 121;
`@NotNull` on the DTO field guarantees it is never absent.

**هل يوجد أي N+1 جديد؟**
Yes, potentially one: `BenefitBucketLimitService.findApplicable` would be
called twice per line if `BucketLimitSnapshotAdapter.buildForNormalClaim` is
invoked as-is alongside the existing call inside
`CoverageDecisionService.resolve()`. Flagged as the primary open decision
in §7.1 — not resolved by this document, since resolving it means either
touching `CoverageDecisionService`'s contract (three consumers, needs its
own review) or adding an adapter entry point that accepts pre-fetched
snapshots (a smaller, additive change, no consumer impact). Recommendation
leans toward the latter, but the decision is for the implementation gate,
not this design pass.

---

## 6. Blast radius check: other consumers of `CoverageDecision.limits`

Two callers besides `CoverageEngineService` read `coverageDecision.limitsOrEmpty()`:

- `BenefitPolicyCoverageService.java:1047,1053` — reads amount/times limits
  for what appears to be an eligibility/summary view, not a claim quantity
  decision.
- `BenefitPolicyRuleService.java:274` — same shape, different consumer.

Neither is touched by this wiring since `CoverageDecisionService.resolve()`
and `CoverageDecision.limits` are left exactly as they are (§1). This is
exactly why the wiring point was moved to `CoverageEngineService` instead of
inside `CoverageDecisionService` itself: touching the latter's output shape
would ripple into these two unrelated consumers for no reason connected to
P1.5.1's goal.

---

## 7. Open risks — must be resolved before implementation, not during it

### 7.1 Duplicate `findApplicable` call (the N+1 above)
Two live options, neither implemented here:
- **(a)** Add an overload to `BucketLimitSnapshotAdapter` that accepts an
  already-fetched `List<BenefitBucketLimitService.LimitSnapshot>` instead of
  calling `findApplicable` itself — additive, no consumer impact, keeps
  `CoverageDecisionService` untouched.
- **(b)** Change `CoverageDecisionService.resolve()` to stop populating
  `.limits` and let `CoverageEngineService` be the sole caller of
  `findApplicable` — cleaner long-term, but requires re-reviewing §6's two
  other consumers, which is a larger, separately-scoped change.

### 7.2 `requestedDays` has no first-class input today
`ClaimLineInput` has no day-count field; the current day logic is an
implicit single-day check embedded in the batch accumulator's state
(`!acc.addedDay`). `UnifiedLimitResolver` expects an explicit
`requestedDays` int. Reproducing today's exact semantics (not a
reinterpretation of them) needs its own small, focused review — this is a
correctness-sensitive mapping, not a cosmetic one.

### 7.3 `BatchUsageAccumulator` has no equivalent in `UnifiedLimitResolver`
`computeBucketUsage` accumulates occurrence/amount/day usage **across lines
within the same batch call** (`batchUsageContext`, keyed by
`AccumulatorKey(bucketId, periodStart, periodEnd, serviceDate,
countingMethod)`) so that line 2 of a batch sees line 1's consumption before
its own limit is checked — this prevents two lines in the same
`/analyze` batch (or the same claim being saved) from each independently
believing the full limit is available. `BucketLimitSnapshotAdapter` and
`UnifiedLimitResolver` are both single-line and stateless; neither has a
batch-accumulator concept. **This is the single largest open question for
implementation**: either the adapter needs to accept a running
committed/reserved adjustment per bucket across the batch loop (mirroring
`BatchUsageAccumulator`), or `excludeClaimId`+a fresh per-batch reservation
write between lines would need to exist, which does not (and per this
phase's rules, must not) happen in read-only adapters. This needs a
dedicated design pass of its own before P1.5.1 can safely replace
`computeBucketUsage` for a batch of more than one line.

### 7.4 One counting method per `UnifiedLimitInput`, multiple per line today
`computeBucketUsage`'s loop reads `limit.countingMethod()` per bucket
snapshot — two buckets constraining the same line could, in principle,
carry different counting methods. `UnifiedLimitInput.countingMethod` is a
single value for the whole decision. Whether this shape is ever actually
exercised in production data (vs. a defensive generality in the old code)
needs a quick data/config check before assuming it can be collapsed to one
value safely.

---

## 8. G1–G8 before/after — Expected Plan only, no code

| Case | Today (`computeBucketUsage`) | After wiring (`UnifiedLimitResolver`) | Expected outcome delta |
|---|---|---|---|
| G1 (amount-only, no occurrence) | Amount ceiling check only, quantity never touched | Same math, `bindingAvailableAmount` from `amount.remaining()` | None — same numbers, different code path |
| G2 (Physio: 3 requested, 2 remaining, EACH_UNIT) | `DivisibleLimitSplitter.splitUnits` inline in the loop | Same splitter, called inside `UnifiedLimitResolver` | None — golden case already pins this |
| G3 (days atomic) | Implicit single-day check (`!acc.addedDay`) | Explicit `requestedDays` (see §7.2) compared to `days.remaining()` | **Behavior must match exactly** — this is the field most likely to regress if §7.2 is rushed |
| G4 (amount+times together) | Both axes checked independently in one loop iteration, greatest refusal wins | `UnifiedLimitResolver` computes `unitsAffordableByTimes`/`unitsAffordableByAmount` and takes the tighter | None expected, but multi-bucket-per-line (§7.4) needs to be ruled out first |
| G5 (shared bucket + reservation) | Today's `computeBucketUsage` has **no reservation concept at all** — it only reads `usedTimes`/`usedAmount` (committed), never RESERVED | `UnifiedLimitResolver` via the adapter DOES see RESERVED | **This is a real behavior change, not a neutral refactor**: a line that today ignores another claim's in-flight reservation would, after wiring, correctly see it as unavailable. This must be flagged to reviewers as an intended fix, not hidden inside "the same numbers" |
| G6 (policy mismatch) | Never checked today — `computeBucketUsage` has no such guard | Adapter blocks before resolver runs | New protection, not previously enforced — another intended-fix, not a neutral change |
| G7 (preauth owns reservation) | N/A — `evaluateLine` is the NORMAL-claim path; preauth conversion is `PreAuthorizationDecisionBuilder`'s separate path, untouched here | N/A — out of scope for this wiring | No change (correctly out of scope) |
| G8 (atomic PER_VISIT with a TIMES axis) | `DivisibleLimitSplitter` already refuses whole-or-nothing for non-`EACH_UNIT` methods | Same | None |

**The two behavior changes worth calling out loudly to whoever reviews the
real implementation gate**: G5 and G6 are not neutral refactors. Today's
`evaluateLine` path has never checked RESERVED amounts or bucket/policy
ownership at all. Wiring `UnifiedLimitResolver` in fixes two real gaps —
which is the whole point of P1 — but it means the implementation gate must
budget for reviewing actual production data for cases where this
correction changes an outcome, not just for code-shape equivalence.

---

## 9. What this document does NOT decide

- Whether §7.1's duplicate-query fix is (a) or (b).
- The exact `requestedDays` derivation (§7.2).
- How `BatchUsageAccumulator`'s cross-line role is preserved or replaced (§7.3).
- Whether §7.4's multi-counting-method-per-line shape is real or dead code.
- Any change to `UsageDetails`' field-by-field construction (§4).

Each is a small, scoped decision that belongs to the implementation gate,
not to this design pass — surfacing them now is the point of doing the
design pass before touching a live financial engine.
