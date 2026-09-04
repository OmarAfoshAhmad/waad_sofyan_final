# V217 backfill gaps — per-claim analysis (2026-09-03)

> **Status update (2026-09-04):** the nine claims analyzed below —
> including all six `LEGACY_UNRESOLVED` cases — were test data and have
> since been permanently deleted from production (confirmed by the user).
> They exist only in the dated review copy this analysis ran against, kept
> here as evidence and as the fixture behind
> `ClaimsHistoricalContextStatusAcrossV219MigrationTest`. **They are not an
> outstanding data debt on the current production database.** Deploying
> V217–V220 against production as it stands today is expected to produce
> **zero** `claims_historical_context_backfill_gaps` rows and zero
> `LEGACY_UNRESOLVED` claims — conditional on no new claims being created
> between this check and the deploy that would independently land in that
> state. The rest of this document is kept as-written (dated 2026-09-03)
> for the analysis trail; read it as history, not as a live gap report.

`V217__claims_historical_policy_snapshot.sql` ran against
`waad_production_review_20260903` (restored production review copy,
local PostgreSQL, port 5432). Pre-migration backup:
`%TEMP%\waad_production_review_20260903_pre_v217.dump`.

Result: 3 of 9 claims backfilled cleanly from `claim_line_limit_snapshots`
agreement. 6 remain NULL and are logged in
`claims_historical_context_backfill_gaps`. **No data has been modified** —
this is analysis only, per the agreed order of work (analyze and report
first, decide second, migrate third).

## Revised finding — supersedes the 2026-09-03 earlier note in this file

The first pass of this analysis said the six were the same pattern
`V216__late_imported_members_follow_policy_start.sql` already corrects
(member enrolled late, assignment dated after the true service). That is
**incomplete**. Re-checking against `benefit_policies.start_date` shows the
policy itself starts after the claim's `service_date`, in every one of the
six — not just the member's assignment:

| claim_id | member | service_date | policy | policy.start_date | assignment_start_date | approved_amount | claim created_at |
|---|---|---|---|---|---|---|---|
| 1 | 11438 (فارس مصطفى الجهاني) | 2026-08-01 | 901 / POL-2026-019 | 2026-08-05 | 2026-08-05 | 45.00 | 2026-08-11 |
| 101 | 30058 (فوزية محي الدين السنوسي) | 2026-08-01 | 1401 / POL-2026-029 | 2026-08-10 | 2026-08-10 | 6970.50 | 2026-08-16 |
| 151 | 30058 (same) | 2026-08-01 | 1401 / POL-2026-029 | 2026-08-10 | 2026-08-10 | 4590.00 | 2026-08-16 |
| 201 | 306 (محمد مفتاح محمد) | 2026-07-01 | 751 / POL-2026-016 | 2026-08-05 | 2026-08-05 | 1039.50 | 2026-08-26 |
| 351 | 4548 (جاسر مراد محمد عبدالرحمن) | 2026-08-01 | 751 / POL-2026-016 | 2026-08-05 | 2026-08-05 | 121.50 | 2026-08-29 |
| 401 | 4548 (same) | 2026-08-01 | 751 / POL-2026-016 | 2026-08-05 | 2026-08-05 | 1883.50 | 2026-08-29 |

Checked and ruled out for all three employers involved (51, 201, 701): no
earlier policy exists that the claim could instead attach to —
`benefit_policies` has exactly one row per employer with a `start_date`
that predates or matches these service dates: none. `assignment_start_date`
exactly equals `policy.start_date` in all six rows, which is itself the
signature of the V171/V183 backfill formula
(`COALESCE(m.start_date, m.created_at::date, ...)`) landing on the
policy's own start rather than on any real enrollment record — because
`members.start_date` is NULL for all six members and their `created_at` in
this dataset happens to fall on/after the policy start.

## Why this is a materially different problem than V216's

V216 corrects members whose **assignment** was dated later than their real
eligibility, when a real earlier date (the policy's own start) is
available to correct it to. That mechanism has nothing left to reach for
here: the assignment is *already* dated at the policy's start — the
earliest date the assignment could honestly claim. The claim's
`service_date` is before that. Extending V216 to also touch
claims-bearing members would not close these six gaps; it would try to
move the assignment to the exact same date it already has.

## What the data actually says happened

All six claims were **created** well after their service dates (8–29 days
later: `created_at` 2026-08-11 through 2026-08-29, `service_date` 2026-07-01
or 2026-08-01) and were **approved with real, non-trivial amounts**
(45.00 to 6970.50) — these are not placeholder/test rows in the
conventional sense; the claim engine adjudicated and approved a service
date that, per the policy's own `start_date`, was before that policy
existed for this employer.

Three explanations remain open, and none can be picked without a human
decision informed by facts outside this database:

1. **The policy's recorded `start_date` is wrong** — coverage genuinely
   began earlier (e.g. a verbal/contractual start date predating the
   system record), and `benefit_policies.start_date` should be corrected.
   This would also affect every other member/claim under that policy, not
   just these six.
2. **The claim's `service_date` is a data-entry error** — the service
   actually happened after policy start, and was mis-keyed on entry.
   Correcting it is a claim-level correction, not an assignment/policy one,
   and normally requires the claim's own correction workflow
   (`NEEDS_CORRECTION`), not a migration.
3. **The approval itself was a mistake** — the claim was approved despite
   predating coverage, and should not have been. This would be a coverage-
   validation gap in the claim engine (does it check `service_date` against
   `policy.start_date` at all?) independent of V217, and out of this
   migration's scope to fix.

This document does not choose between the three. `claims_historical_context_backfill_gaps`
keeps the 12 gap rows (6 claims × `policy_id` + `employer_assignment_id`)
as the durable, unmodified record.

## Decision status

Still deferred, per your instruction. Nothing in `claims`,
`member_policy_assignments`, `member_employer_assignments`, or
`benefit_policies` has been changed by this analysis. V218 (added
2026-09-03) runs `VALIDATE CONSTRAINT` on the three new FKs — safe with
these NULLs still present — but does **not** add `NOT NULL`; that is
blocked on resolving these six (or a documented, reviewed exception for
each), scoped to a later migration (V219 or a rewritten V218, per whichever
you choose in step 3 of the agreed order).
