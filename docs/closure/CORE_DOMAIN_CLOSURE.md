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

Examined against the E-01..E-12 gate. Ten of twelve (E-01, E-02, E-03, E-04,
E-06, E-08, E-09, E-10, E-11, E-12) are `VERIFIED`. The remaining two
(E-05 permission-not-role, E-07 import) are `PARTIALLY VERIFIED` -- not
assumed closed.

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

### E-06 restore — VERIFIED

**CLAIM:** archive and restore are explicit, guarded, audited transitions --
not the inverse of a flag flip.

**FAILURES FOUND**
1. `restore()` on an already-active employer succeeded silently. `archive()`
   on an already-archived one did too. Neither refused a transition that
   cannot happen; a bulk operation reporting "success" for a no-op hides that
   from the one place an operator would see it.
2. **Zero audit signal anywhere in the module.** Archiving hides an employer
   from every list in the system and restoring brings a tenant back; neither
   left a record anyone could query, only a log line.

**ROOT CAUSE** Both were written as setters (`employer.setActive(...)`) with
validation bolted on, not as transitions with entry conditions.

**FIX** Both refuse when the employer is already in the requested state.
Both call through `AuditLogService`. Naming the act ran into a real
constraint: `AuditAction` has `RESTORED` but no `ARCHIVED` — adding one is an
audit-vocabulary decision, not a local one, so archive is recorded as
`UPDATED` with the specific act in the queryable reason text, and restore
uses `RESTORED` directly since it already exists.

**REGRESSION TESTS** `EmployerRestoreIntegrationTest` -- 7 cases: both
double-transition refusals (each proved to bite by removing the guard and
watching the specific case fail), both audit assertions (querying
`medical_audit_logs` for the actual persisted reason and action), restore
over a member cap already exceeded while archived, restore with an invalid
contract, and a valid restore succeeding.

**REMAINING UNKNOWN** Restoring while a conflicting active policy exists
(protocol's fourth restore case) was not constructed -- `validateEmployerTerms`
covers dates and member cap; whether a second mechanism blocks a policy
conflict specifically was not traced.

### E-08 concurrency — VERIFIED

**CLAIM:** archiving an employer and assigning a new member to it, run at the
same instant, cannot both succeed and cannot corrupt either fact.

**FAILURE FOUND** Neither side took any lock. `EmployerService.archive`
counts current assignments then writes `active=false`; `MemberEmployerResolver
.assignEmployer` had no check on the employer's state at all -- a member could
be assigned to an archived employer with nothing refusing it, and a real race
between the two could let an employer archive itself believing nobody
belonged to it a moment before an assignment landed that would have blocked
it.

**FIX** Both take the same `PESSIMISTIC_WRITE` lock on the employer row
(`EmployerRepository.findByIdForLifecycleTransition`) before deciding, so the
two serialise on the row rather than racing. `assignEmployer` now also
refuses outright when the employer it is locking is not active.

**EVIDENCE, not simulated** -- Constitution Phase 10 requires real threads,
not sequential calls standing in for a race. Two real threads, released past
a `CountDownLatch` barrier at the same instant, each in its own transaction
via `TransactionTemplate`.

**WITH the lock:** 20 repetitions within one Spring context, run twice over
(40 trials total) -- every one landed in a consistent state (either the
employer stayed active and the assignment succeeded, or the employer
archived and the assignment was refused; never both, never neither), in
~32 seconds per 20.

**WITHOUT it** (both locks removed during triage, to prove the fix is load-
bearing and not decorative): a single trial could pass by luck, but any
attempt to repeat the race made the whole test JVM hang -- past a
150-second hard kill, with the Maven summary never printing a test count.
This is a finding worth stating precisely, because it is not what was
expected. Postgres was holding one thread on a genuine row-level wait with
no `lock_timeout` configured -- not a fast, cleanly-caught conflict, but an
unbounded block. `Future.get(timeout)` in the test gives up waiting for the
result; it does not cancel the underlying blocked database call, so the
connection never returns to the pool, and a handful of repetitions exhausted
it. **An unguarded race here is not a rare wrong answer -- it is a liveness
hazard that can starve the connection pool under real concurrent traffic**,
which is a more severe failure mode than the data-corruption race this test
set out to find.

**REGRESSION TEST**
`EmployerArchiveVersusMemberAssignmentConcurrencyIntegrationTest`.

**REMAINING UNKNOWN** Other archive-adjacent races named in the protocol
(archive vs. policy activation, for one) were not constructed.

### E-09 DB constraints — VERIFIED

**CLAIM:** every rule `EmployerService.validateEmployerTerms` enforces in
Java also holds at the database, so a write reaching this table by any other
path -- an import, a script, a future service -- cannot violate it.

**FAILURE FOUND** Two rules were Java-only. `contract_end_date >=
contract_start_date` and `max_member_limit > 0` had no `CHECK` behind them at
all.

**FIX** `V200` adds both as `NOT VALID` + a separate `VALIDATE`, the pattern
already used elsewhere in this schema for a live table: existing rows are
not re-scanned at ALTER time, and validation afterwards does not block
concurrent writes.

**REGRESSION TEST**
`EmployerTermsConstraintsAcrossV200MigrationTest` -- applies over a live V199
database carrying a pre-existing, compliant employer row (proving the
constraint does not disturb what was already there), then exercises both
CHECKs: reversed dates refused, equal dates and an open-ended contract
accepted, a zero or negative cap refused, no cap accepted. Proved to bite by
neutralising both CHECKs to `CHECK (true)` and watching the refusal
assertions fail.

**A fixture broke, for the right reason.** Closing this immediately made a
gap visible in `EmployerRestoreIntegrationTest`: its "restore with an invalid
contract" case forced an end-before-start row into existence with a raw
`UPDATE`, bypassing `EmployerService` entirely -- which is exactly the write
path this migration exists to close. The `UPDATE` itself now fails on
`chk_employer_contract_period` before `restore()` is ever reached. The test
was rewritten to assert the stronger, now-true guarantee directly: the
database refuses the write, and an employer whose terms were never actually
corrupted restores without incident. Both layers agree; neither is
compensating for the other's absence.

**REMAINING UNKNOWN** `employers.is_default` is a unique-in-intent flag
consumed by `SystemController` to pick "the" system employer, but **no write
path in the codebase ever sets it** -- grep found reads only. Adding a
uniqueness constraint on a column nothing writes to would be a schema
decision with no live risk to justify it today; left as an open observation
rather than fixed speculatively. If a write path is ever added, this needs
revisiting before it ships.

### E-10 audit (beyond archive/restore) — VERIFIED

**CLAIM:** every significant employer write is auditable, with who did it
and what actually changed.

**FAILURE FOUND** `create()` and `update()` had zero audit signal.
`update()` is the more serious gap: it can change the code, the name and the
contract terms (dates, member cap) with nothing recording it. The import
path writes through these same methods, so it inherited the gap too.

**FIX** Both call through `AuditLogService`, matching archive/restore.
`update()` captures the four fields worth distinguishing -- code, name,
contract dates, member cap -- before mutating, and the reason text names the
actual before and after only for what changed; a contact-detail-only update
does not fabricate a claim that identity or terms moved.

**REGRESSION TESTS** Three cases in `EmployerRestoreIntegrationTest`:
creation is recorded, an identity/term change is recorded with the real
before-and-after values in the reason text, and a phone-only update's reason
contains neither an identity-change nor a contract-term-change phrase. Both
audit calls proved to bite individually by removal.

### E-11 performance — VERIFIED

**CLAIM:** the employer-facing query paths do not degrade as the book
grows.

**DATASET** "30,000 members" in the protocol is a MEMBERS-scale number;
there are never 30,000 employers in this system. The dataset was shaped for
what actually stresses the employer paths instead: 500 employers (a
realistic upper bound for a TPA's book) and one carrying 5,000 members with
a real dated assignment each -- the shape `archive()`'s count query and the
scoped list query actually have to survive.

**MEASURED, not guessed** (Hibernate `Statistics.getPrepareStatementCount()`):

| path | result |
|---|---|
| `archive()` against 5,000 assigned members | 5 statements, 45 ms |
| first page of 500 employers vs. the last page | 23 statements, both |
| one tenant scoped to 1 of 500 vs. the global read | 3 statements vs. 23 |

**A fixture bug caught itself.** The first version of this test seeded
5,000 members with only the `employer_id` pointer, no
`member_employer_assignments` row -- the exact mistake
`EmployerArchiveFollowsTheDatedAssignmentIntegrationTest` (E-03) had already
named. `countActiveMembersAssignedOn` reads the assignments, not the
pointer, so it saw zero members; the test passed anyway because the
employer's still-active benefit policy blocked archiving on its own, and the
statement count being measured was never exercised against the roster it
claimed to be. Caught by the coincidence of two runs reporting the identical
"5 statements" before and after seeding assignments -- suspicious agreement,
not proof -- and confirmed by removing the assignment writes and watching
the test fail with "a 5,000-member roster must block archiving". The fixed
fixture writes a real dated assignment per member and disables the policy so
only the roster condition is under test.

**REGRESSION TEST** `EmployerQueryScalePerformanceIntegrationTest` -- three
cases, numbers above.

### E-12 integration gate — VERIFIED

**CLAIM:** archiving an employer changes its lifecycle state only. It does
not, by itself, withdraw a user's authorization scope. The two are separate
mechanisms answering separate questions -- `Employer.active` (is this
employer active?) vs. a user's `employerId`/scope (may this user reach it?).
Folding the first into `MemberAccessScopeResolver` would make every
historical record an archived employer ever produced invisible to the
people authorized to audit it, contradicting the append-only history this
system is built around. Withdrawing a user's own access is a separate
administrative act, not a side effect of the employer's state.

**EVIDENCE** `EmployerLifecycleIsSeparateFromAuthorizationIntegrationTest`
— one chained scenario, not six isolated cases:

1. Employer A active, a historical member already departed before archiving
   (seeded with a closed dated assignment, respecting the append-only
   trigger on `member_employer_assignments` rather than fighting it).
2. Archiving A is blocked while its benefit policy is still active (E-02),
   independent of the historical member (already departed, E-03); ending
   the policy then lets archiving succeed.
3. User A, scoped to A, still reads the historical member and A's audit
   trail after A is archived.
4. User A cannot enroll a new member or dependent under archived A. This
   is **not new production code** -- it is the E-08 guard already living in
   `MemberEmployerResolver.assignEmployer` (`if (!employer.getActive())
   throw ...`), which both `createPrincipalMember` and dependent enrollment
   already route through via `recordInitialEmployerAssignment`. Confirmed
   by proof-of-bite: temporarily removing that check made this exact test
   fail (`Tests run: 1, Failures: 1`); restoring it made it pass again.
5. User B, scoped to employer B, is denied throughout -- A's archiving
   changes nothing about B's exposure to it.
6. Only when User A's own scope is reassigned (a separate administrative
   act, unrelated to archiving) does User A lose the read.

**A duplication mistake caught before it landed.** Implementing step 4, the
first instinct was to add two new guards directly in
`UnifiedMemberService` (`createPrincipalMember`, `createDependentInternal`).
Proof-of-bite on the new `createPrincipalMember` guard alone still passed --
because both paths already call
`memberEmployerResolver.assignEmployer(...)`, which already refuses an
archived employer as part of the E-08 concurrency fix from earlier in this
closure round. The two new checks were live duplicated business logic, a
direct violation of the constitution's "لا نسخ Business Logic إلى أكثر من
مكان". Reverted via `git checkout`; no production code changed for E-12 --
only the test.

**TEST RESULT** 1 passed (`EmployerLifecycleIsSeparateFromAuthorizationIntegrationTest`).

**REGRESSION TEST** `EmployerLifecycleIsSeparateFromAuthorizationIntegrationTest`.

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

## BENEFIT POLICIES — PARTIALLY VERIFIED

Following the same P-01..P-xx gate discipline used for Employers. Two gates
closed this round; one P0 finding surfaced and is deliberately deferred
rather than fixed blind.

### P0 finding, deferred by decision: a second, parallel scope model — OPEN

`BenefitPolicyController` enforces employer scope itself
(`currentEmployerScope()`, `scopedEmployerId()`, `assertEmployerScope()`,
lines 481-503), built on `authorizationService.isEmployerAdmin()` /
`getEmployerFilterForUser()` -- not on `MemberAccessScopeResolver`, the
canonical mechanism Employers and Members already unified onto. Two scope
models for the same question ("which employers may this caller reach?")
is exactly the duplication §2/§6 of the constitution forbid, and here it is
not merely a style issue:

**`currentEmployerScope()` returns `null` (= unscoped, see every employer's
policies) for any caller that is not `isEmployerAdmin()`** -- which includes
`ACCOUNTANT` and `MEDICAL_REVIEWER`, both `@PreAuthorize`d on nearly every
read endpoint. Under the canonical `MemberAccessScopeResolver`, neither role
is global by default: each is scoped to its own `employerId` if it has one,
denied otherwise (`MemberAccessScopeResolver.java:64-72`, `"Reviewers and
data-entry staff are NOT global by default"`). The controller's ad hoc check
silently grants the wider of the two answers.

**Deliberately not fixed blind.** Whether this is a live leak depends on
data this environment cannot see: if some `ACCOUNTANT`/`MEDICAL_REVIEWER`
accounts in production are legitimately central staff with no `employerId`
recorded, applying the canonical resolver's deny-by-default today would lock
out real users the moment it ships -- a production incident from an
unaudited migration, not a security improvement. Decision recorded: the
target architecture separates **role** from **scope** entirely -- no
`if (role == ACCOUNTANT) return ALL`. A caller's reach is stated explicitly
(a specific employer set, or an explicit system-wide grant), never inferred
from their role name. Central staff get an explicit system-wide scope
assignment as a deliberate, auditable administrative act, not an implicit
side effect of `user_type`. Before that model replaces the controller's
current check, production `users` data must be audited for `user_type IN
('ACCOUNTANT','MEDICAL_REVIEWER')` crossed with `employer_id IS NULL`, and
every account found migrated to an explicit scope. The local/dev database
available in this environment has exactly one user (`SUPER_ADMIN`) and
cannot answer this question, so the migration and the resolver change are
`OPEN`, not assumed safe.

### P-06 audit (create/update/lifecycle) — VERIFIED

**CLAIM:** every action that changes what a benefit policy's members are
covered for is recorded to the queryable audit trail, not only a log line.

**BEFORE:** `BenefitPolicyService` had zero calls into `AuditLogService` --
`create`, `update`, `activate`, `deactivate`, `suspend`, `revertToDraft`,
`cancel`, soft `delete` and `restore` were all silent. This is the same gap
Employers had before E-10, just never closed here.

**FIX:** added the same facade call `EmployerService` already uses
(`AuditLogService.createAuditLog`), reusing it rather than inventing a
second audit call site. `EntityType.BENEFIT_POLICY` did not exist and was
added (plain `VARCHAR(40)` column, no `CHECK` constraint on it anywhere in
the migration history -- confirmed by grep -- so no migration was needed).
`update()`'s audit reason names only the fields the caller actually changed
(same pattern as `EmployerService.updateAuditReason`), not a generic
"updated".

**TEST RESULT** `BenefitPolicyServiceTest` -- 15 tests, including
`create_activate_andDelete_eachWriteToTheAuditTrail`, added this round.
Proved to bite: temporarily removing the `CREATED` audit call
(`/tmp/bps_proof.bak` diff) failed the test (`Wanted but not invoked`);
restoring it passed again.

### P-03 source of truth for "policy currently active for a member" — VERIFIED (inherited)

Not re-derived this round -- already the single reader used across
claim/eligibility/preauth/coverage code: `MemberPolicyResolver.resolveFor
(member, serviceDate)`, dated via `member_policy_assignments`
(append-only history since `V171`). Proved under cross-module integration
by `MemberMovesBetweenEmployersJointTemporalIntegrationTest` (Phase 8,
documented under CROSS-MODULE INTEGRATION below), which exercises the
sibling resolver (`MemberEmployerResolver`) the same way; `MemberPolicyResolver`
follows the identical pattern and is called from the same call sites.

`OPEN`: no dedicated policy-resolver test exists independent of the
cross-module one; not falsified, but not directly proved either.

`OPEN`, not examined this round: P-02 (archive/lifecycle transition
guards beyond what `BenefitPolicyServiceTest` already covers), P-04/P-05
(scope closure, blocked on the production audit above), P-07 (import),
P-08 (concurrency -- `checkOverlappingActivePolicy` has no proven
concurrent-activation test, only the sequential unit tests), P-09 (DB
constraints -- migrations exist per the earlier survey but are untested
across a live migration the way Employer's V200 was), P-10 (performance),
P-11 (integration gate).

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
