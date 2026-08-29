# CORE DOMAIN CLOSURE

> **Vocabulary:** `OPEN` / `PARTIALLY VERIFIED` / `VERIFIED` / `BLOCKED` only.
> No percentages. `CLOSED` is not used and will not be used until every gate
> below reads `VERIFIED`.
>
> **Evidence rule:** a claim here is backed by a file path, a test name, a
> database constraint, or a recorded run. A claim with none of those is
> `UNKNOWN`, and `UNKNOWN` is written as `OPEN`.

---

## 0. Baseline

| | |
|---|---|
| Branch | `fix/member-import-report` |
| Last commit at time of writing | `54a82975` |
| Working tree | 46 files modified, uncommitted |
| Schema head | `V199__member_general_limit_uplift.sql` |

**Status of this document: PARTIALLY VERIFIED.** It covers what has been
verified by reading code and running tests during this closure round. Phases
not yet examined are listed as `OPEN`, not omitted.

---

## MEMBERS

### Access scope — PARTIALLY VERIFIED

**CLAIM:** every member-scoped route resolves an authorised scope, and the
permission is the decider rather than the role name.

**EVIDENCE**
- `MemberOperationPermissions` is the single operation→permission map and is
  exhaustive by construction: `requiredFor()` throws
  `IllegalStateException` for an operation nobody has decided about.
- `MemberPoliciesDecideByPermissionArchitectureTest` bans role-name checks in
  the decision policies and asserts every operation has a decision.
- `EndpointGatesMatchServiceOperationsArchitectureTest` pairs each
  `@PreAuthorize` with the `MemberOperation` its service enforces and fails on
  a mismatch; its second case fails on any member endpoint with no
  `@PreAuthorize` at all.
- `MemberCommandAccessPolicyIntegrationTest` (12 tests) covers super admin,
  employer admin in and out of scope, data entry, provider staff, reviewers,
  bulk-with-one-out-of-scope, and per-user GRANT overrides.

**FAILURES FOUND (this round)**
1. `PUT /{id}/reinstate` declared `MEMBER_REINSTATE_TERMINATED` while the
   service required `MemberOperation.REINSTATE` → `MEMBER_CHANGE_STATUS`, and
   separately read `MEMBER_REINSTATE_TERMINATED` by hand. The dedicated grant
   was unusable on its own; the effective rule was both permissions, which
   neither layer stated. `MemberOperation.REINSTATE_TERMINATED`, its map entry
   and its refusal message existed with no caller.
2. `GET /duplicates` declared `SYSTEM_SETTINGS_VIEW` while `findDuplicates()`
   required `RESOLVE_DUPLICATES` → `DANGER_ZONE_EXECUTE`.

**ROOT CAUSE** Nothing compared an endpoint's declared gate with the gate its
service enforces.

**FIX** Service now requires `REINSTATE_TERMINATED`; the hand-rolled
permission read and its now-unused collaborator are removed;
`MemberStatusTransitionService.reinstateTerminated` no longer takes a boolean
saying whether the caller is allowed (the domain does not answer that
question). `GET /duplicates` declares both permissions, which is what is
enforced.

**REGRESSION TEST**
`MemberCommandAccessPolicyIntegrationTest.theExceptionalReinstateGrantIsEnoughOnItsOwn`
and `.theEverydayStatusGrantDoesNotReachATerminatedMembership`.

**TEST RESULT** Both pass. `EndpointGatesMatchServiceOperationsArchitectureTest`
was proved to bite by reintroducing both defects: it named each by file.

**REMAINING UNKNOWN**
- Browser verification with real `DATA_ENTRY` / `EMPLOYER_ADMIN` sessions has
  not been performed this round. Protocol Phase 20 is `OPEN`.

---

### Temporal employer assignment — OPEN

Not examined this round. `member_employer_assignments` carries
`[assignment_start_date, assignment_end_date)` and an `EXCLUDE` constraint
from `V183`, and `PreauthLinkRepointedByV181MigrationTest` and
`ReservationNamesPolicyAssignmentAcrossV187MigrationTest` exist — but the
boundary cases the protocol names (`from - 1`, `from`, `to - 1`, `to`, two
simultaneous currents) were not re-verified here.

---

### Temporal policy assignment — OPEN

Same. `DatedMemberContextArchitectureTest` exists and bans current-pointer
reads in dated paths; the boundary matrix was not re-run this round.

---

### Ledger correctness — PARTIALLY VERIFIED

**CLAIM:** net consumption is `COMMITTED − REVERSED`, read from the append-only
ledger, never from a filtered status.

**EVIDENCE** `LimitBalanceReader` is the only reader; `BalanceDisplayIsNotClampedArchitectureTest`
bans `.max(BigDecimal.ZERO)` on balance-display paths, so an overspend stays
negative rather than becoming indistinguishable from exactly-spent.

**FAILURES FOUND (this round)** The client did what the server is forbidden to
do. `ClaimLineRow.jsx` wrapped the server's `remainingAmount` in
`Math.max(0, …)` and fell back to recomputing `amountLimit − usedAmount` when
it was absent; `useBenefitPolicyReport.js` clamped an aggregate remaining.

**FIX** All three clamps removed; the recomputing fallback removed.

**REGRESSION TEST** `frontend/src/__tests__/balanceIsNotClampedInTheClient.test.js`
— three cases: it finds source files (so it cannot pass on nothing), it bans
clamping a balance (excluding day countdowns, which are correctly clamped),
and it bans falling back to recomputing a figure the server sends.

**TEST RESULT** 3 passed.

**REMAINING UNKNOWN** Concurrent commit/reverse/double-reverse was not re-run
this round.

---

### Exceptional general-ceiling uplift — PARTIALLY VERIFIED

**CLAIM:** one member's general ceiling can be raised by a dated, reasoned,
revocable exception that is additive, visible as such, and honoured by every
decision.

**EVIDENCE**
- `V199__member_general_limit_uplift.sql`: `[effective_from, effective_to)`
  half-open; `amount > 0`; `reason` non-blank; source/employer agreement;
  revocation requires a reason; `MEMBER_LIMIT_UPLIFT_MANAGE`, SUPER_ADMIN only.
- Resolution lives inside `LimitBalanceReader`, in **both** entry points, so no
  caller can omit it.
- `MemberLimitUpliftIntegrationTest` — 14 tests.
- `LimitUpliftAcrossV199MigrationTest` — applies over a live V198 database with
  pre-existing members, employers and permission grants; exercises every CHECK
  against real rows; asserts an existing administrator grant is untouched.
- `MemberCeilingComesFromOneReaderArchitectureTest` — bans reading a policy's
  annual limit off a policy outside an enumerated allow-list, and asserts every
  public reader entry point resolves the uplift.

**FAILURES FOUND (this round)** — three, all the same class:
1. **The uplift was visible but not spendable.** It was wired into
   `readGeneralCeilingBulk` (the list read) and not into `readGeneralCeiling`
   (the single read every decision uses). A member granted 15,000 extra saw a
   raised ceiling everywhere and was refused past the policy figure.
2. `MemberFinancialSummaryService` reported `annualLimit` from the policy while
   `actualRemaining` beside it came from the effective ceiling — a remaining
   balance larger than the limit, on the field `/remaining-limit` returns to the
   provider portal during claim entry.
3. `MemberSearchDto.coverageLimit` presented the policy's limit as the member's
   coverage. Unread by any screen; removed rather than made to do a per-row
   ceiling read.
4. **The revocation CHECK refused nothing.** `btrim(NULL) <> ''` is `NULL`, so
   `(false OR NULL)` is `NULL`, and Postgres admits a NULL check. A revocation
   with no reason was accepted. Found only because the migration was tested
   against real rows.

**ROOT CAUSE** (1–3) A value that used to be a column became a computed thing,
and the places reading the column were not enumerable. (4) A constraint written
but never seen to refuse anything.

**FIX** Both reader entry points resolve the uplift; the summary reports the
effective ceiling with `policyLimit` and `upliftAmount` beside it; the search
field is gone; the CHECK gained the `IS NOT NULL` that makes it evaluate.

**REGRESSION TEST**
`MemberLimitUpliftIntegrationTest.theUpliftIsSpendableAndNotJustVisible`
(proved to bite: reintroducing the bypass fails it),
`.anUpliftNeverManufacturesACeiling`,
`.simultaneousRevocationsDoNotBothWrite` (proved to bite 3/3 without the
pessimistic lock), `.aSameDayMistakeRaisesNobodysCeiling`,
`.revokingTwiceIsRefusedAndChangesNothing`,
`.overlappingWindowsAreCountedByDateAndIndependently`,
`.bothAccountsAreRecorded`.

**TEST RESULT** 14 passed. `LimitUpliftAcrossV199MigrationTest` 2 passed.

**REMAINING UNKNOWN**
- A grant and a revoke racing each other (not two revokes) is untested.
- Behaviour when an uplift's window is open across a policy-year boundary is
  untested.

---

### Family integrity — OPEN
### Duplicate resolution — OPEN
### Import / export — PARTIALLY VERIFIED

Import history filters, the interrupted-batch reading and the rollback path
were closed earlier this round with
`MemberImportLogFilterIntegrationTest` (7 tests, including a case proving the
employer-scoped read and the global read cannot diverge) and
`MemberImportLogSummaryDtoTest` (5). A pre-existing defect was found and fixed:
the employer-scoped history read is a native query and Spring appended the
`Pageable`'s `Sort` to it as a JPA property name, so **an employer-scoped user
could not open the import history at all**. The test that guarded that path
passed an unsorted `Pageable` and never saw it.

`OPEN`: re-import, partial failure, rollback-twice and concurrent rollback were
not re-run this round.

---

### Performance — OPEN

`MemberLimitOverviewServiceIntegrationTest` asserts a fixed query count (now 7,
raised from 6 by the uplift read) independent of page size, and
`GeneralCeilingBulkReadIntegrationTest` asserts 3 (from 2) independent of row
count. The protocol's 30,000-member dataset gate was not run this round.

---

## EMPLOYERS — PARTIALLY VERIFIED

Examined against the E-01..E-12 gate. Four of twelve are `VERIFIED`; the rest
were not reached this round and are `OPEN`, not assumed.

### E-01 no delete erases history — VERIFIED

`EmployerService.delete` always throws; an employer is named by member
assignments, policies, claims and ledger rows. `EmployerScopeClosureGateTest`
asserts the refusal AND that neither `delete` nor `deleteById` was reached.

### E-02 archive is a guarded transition — VERIFIED

Blocked by members belonging today, and separately by an active policy. Two
cases, so one passing cannot mask the other.

### E-03 the dated assignment is the source of truth — VERIFIED

**CLAIM:** no decision about a member's employer is made from
`members.employer_id`.

**FAILURES FOUND** — one looked for, three found by the guard written for it:

| where | what it decided from the pointer |
|---|---|
| `EmployerService.archive` | whether an employer may be archived |
| `EmployerService.update` | whether a member cap may be lowered -- while `restore()` had already moved to the assignments, so one rule had two sources |
| `MemberExcelImportService` | **who gets terminated** by a replacement import |
| `MedicalAuditLogController` | which members appear in an employer's audit trail |

The third is the shape of the risk: a destructive write decided from a cache
of the answer. The fourth taught something -- its question is "who was EVER
here", not "who is here today", so answering it with the nearest dated query
would have made it *more* wrong. `findMemberIdsEverAssignedTo` was built for
it rather than allow-listing the file.

**FIX** Three reads, each named for the date it answers, so nobody reaching
for one gets another's answer: `countActiveMembersAssignedOn`,
`findMemberIdsAssignedOn`, `findMemberIdsEverAssignedTo`. Plus
`countActiveMembersWithNoAssignmentTo`, which measures the GAP between the two
sources and exists so a destructive operation can refuse to run over data it
cannot place.

**A design question the fix exposed.** Two atomicity tests seeded a member with
the pointer and no assignment. Under the old code the replacement terminated
them; under the new one it did not see them. Both are wrong, and skipping
silently is the worse of the two -- the import reports success and the
stragglers stay. Replacement now refuses and names how many records need
fixing.

**REGRESSION TESTS**
`EmployerArchiveFollowsTheDatedAssignmentIntegrationTest` -- five cases. Two of
them prove WHICH SOURCE was read, by making the pointer and the assignment
disagree on purpose:

- pointer says B, assignment says A -> archiving A is blocked
- pointer says A, assignment says B -> archiving A is allowed

Both fail under the old implementation; verified by reverting it.

`MemberExcelImportAtomicityIntegrationTest
.replacementRefusesWhenAMemberCarriesThePointerWithNoDatedAssignment`.

`EmployerDecisionsReadTheDatedAssignmentArchitectureTest` bans pointer reads
outside an allow-list of three display-only files, and asserts the dated reads
are named for their dates.

### E-04 scope — VERIFIED

**A live cross-tenant leak, reachable with default role templates.**
`GET /api/v1/employers` was guarded by `EMPLOYER_VIEW` alone and `searchPage`
carried no scope predicate -- no aspect, no interceptor, no Hibernate filter
exists anywhere in the codebase. `DATA_ENTRY` holds `EMPLOYER_VIEW` by default
(V191) and is scoped to one employer.

Three tests failed on first run: listing returned every employer, reading
another by id succeeded, and **archiving another employer succeeded**.

The only correct logic was buried inside `getSelectors()` and keyed on the
role NAME -- an E-05 violation of its own, applied to one of eight reads.

**FIX** `EmployerService` consults `MemberAccessScopeResolver`, not a second
scope model that could disagree with it. `findEmployerById` is the shared door
for getById/update/archive/restore, so the check sits there once; an
out-of-scope employer is reported NOT FOUND rather than forbidden, because a
403 on a specific id confirms that id exists. The listing narrows IN THE
QUERY -- filtering a page afterwards leaves its total count describing rows the
caller may not see.

Proved to bite by removing the check from the finder and from the listing
separately.

### E-05 permission not role — PARTIALLY VERIFIED

Every endpoint uses `permissionGuard.has(...)`; no role shortcuts in the
controller. But `EMPLOYER_MANAGE` alone gates update, archive, restore, the
refused delete AND bulk-archive. Whether that breadth is intended is a business
question, not a code one.

### E-06 restore, E-08 concurrency, E-09 DB constraints, E-10 audit, E-11 performance, E-12 integration gate — OPEN

Not examined.

### E-07 import — PARTIALLY VERIFIED

The employer import writes through `EmployerService`, so it inherits the scope
check. A case was added for the sharpest form: a scoped operator putting
another employer's code in a spreadsheet to reactivate it. Refused; the
employer stays archived.

That case could not have failed before -- **the entire employer import suite
ran with no authenticated user at all**, so nothing about who was importing
was ever part of the test.

`OPEN`: invalid rows, partial failure, rollback.

---

### Found in MEMBERS while auditing employers

`MemberAccessScopeResolver.providerScope` ignored
`ProviderAllowedEmployer.active`. `Provider.allowedEmployers` is an unfiltered
`@OneToMany`, so a provider's scope included every employer it had EVER been
contracted with -- an ended contract left their staff able to read that
employer's members indefinitely, with the link correctly marked inactive and
the code that mattered not reading the mark.

Fixed at its root.
`MemberAccessScopeResolverIntegrationTest.anEndedContractEndsTheProvidersReach`,
proved to bite.

**The generalisation, still OPEN:** a relation with a state or a validity
window is not a foreign key. `Provider -> Employer` was one instance;
`User -> Employer`, `Policy -> Employer` and `Member -> Employer` deserve the
same question.

## BENEFIT POLICIES — OPEN

Not examined this round, except where the ceiling touches them (above).

## COVERAGE RULES — PARTIALLY VERIFIED

**Single limit engine:** `LimitBalanceReader` is now provably the only source
of a member ceiling (`MemberCeilingComesFromOneReaderArchitectureTest`).

**Frontend does not recompute:** enforced by
`balanceIsNotClampedInTheClient.test.js`.

`OPEN`: single decision engine (`CoverageDecisionService` is referenced from
five files; whether all five delegate rather than re-implement was not
verified), bucket authority, rule immutability, coverage scope.

---

## CROSS-MODULE INTEGRATION — PARTIALLY VERIFIED

**CLAIM:** a decision about a past date still resolves to the employer, the
policy and the balance that applied on that date, after the member has moved on.

**EVIDENCE** `MemberMovesBetweenEmployersJointTemporalIntegrationTest` — the
protocol's Phase 8 test, which did not exist before this round. One member,
Employer A / Policy A (ceiling 40,000, 30,000 consumed) until Jul 1, then
Employer B / Policy B (ceiling 60,000, 10,000 consumed). The member's CURRENT
pointers say B throughout, so every assertion about February must come from the
dated assignments or fail.

Four cases: the old service date resolves to A with 10,000 remaining; the new
one resolves to B with 50,000 remaining, unreduced by A's spending; the move
date itself belongs to exactly one context (half-open, so B); and consuming
under B does not move A's balance.

**TEST RESULT** 4 passed. Proved to bite: replacing the dated read with
`member.getEmployer()` — the exact mistake the model exists to prevent — fails
it by name.

**REMAINING UNKNOWN**
- The consumption is seeded as opening-balance ledger rows rather than through
  a claim, so the claim pipeline's own dated resolution is not covered here.
- Coverage RULES and buckets across the move are not covered; only the general
  ceiling is.

---

## OBSERVABILITY — PARTIALLY VERIFIED

`trackingId` is end-to-end: `LogMdcFilter` stamps `traceId` per request,
`logback-spring.xml` prints `[%X{traceId}]` on every line,
`GlobalExceptionHandler` returns it, and `normalizeApiError` +
`GlobalApiErrorToaster` now surface it to the user (the link that was missing).

`MemberImportMetrics` publishes import outcomes, duration percentiles,
interrupted batches and uplift actions.
`MetricLabelsAreBoundedArchitectureTest` bans a label whose value varies per
record and was proved to bite.

`OPEN`: no alerting; metrics not verified against a live scrape.

---

## FULL SUITE — VERIFIED

**Backend, `mvn -o clean test`, nothing concurrent:**

```
Tests run: 1260, Failures: 0, Errors: 0, Skipped: 4
BUILD SUCCESS
```

Two earlier runs are recorded here because discarding them silently would be
the kind of thing this document exists to prevent:

- A run reporting **175 errors was invalid**. The suite ran in the background
  while `mvn compile` ran concurrently against the same `target/`, producing
  `NoClassDefFound` on classes that exist in the source. A testing-process
  error, not a regression; discarded and re-run.
- The first genuinely clean run reported **1 failure**, in a test written this
  round: it compared an in-memory `LocalDateTime` (nanoseconds) with the same
  value read back from Postgres (microseconds), so it passed only when the
  nanoseconds happened to be zero. A flaky test is worse than a failing one.
  Fixed by comparing two reads of the stored row, which also states the claim
  more exactly.

**Frontend, `npx vitest run`:**

```
Test Files  19 passed (19)
Tests       72 passed (72)
```

**Production build:** `npm run build` — exit 0, built in 1m 32s.

---

## FINAL

```
MEMBERS                   = PARTIALLY VERIFIED
EMPLOYERS                 = PARTIALLY VERIFIED
BENEFIT_POLICIES          = OPEN
COVERAGE_RULES            = PARTIALLY VERIFIED
CROSS_MODULE_INTEGRATION  = PARTIALLY VERIFIED

FINAL CLOSURE             = OPEN
```

**BLOCKERS before any of these can move to `VERIFIED`:**

1. The Phase 8 joint temporal integration test does not exist.
2. Employers and Benefit Policies have not been examined against this protocol.
3. No browser gate has been run with real role sessions.
4. The 30,000-member performance gate has not been run.
5. A clean full-suite run has not been recorded.
