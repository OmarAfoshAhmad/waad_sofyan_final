# Service-date coverage gate — independent finding (2026-09-03)

Opened while analyzing the six unresolved legacy claims in
`waad_production_review_20260903` (see
`CLAIMS_POLICY_SNAPSHOT_BACKFILL_GAPS.md`). Question: does current `main`
already reject a claim whose `serviceDate` falls outside its policy's
coverage window, or did that check arrive after those six claims were
created?

## Answer, proven by test, not assumed

**Current `main` already fails closed on all six scenarios asked for.**
See `backend/src/test/java/com/waad/tba/modules/claim/service/ServiceDateCoverageGateIntegrationTest.java`
(6 tests, all passing against current `main`):

1. Service date before `policy.startDate` — rejected.
2. Service date after `policy.endDate` — rejected.
3. Policy not `ACTIVE` per its status history at the service date (even
   though its live `status` column reads `ACTIVE` today) — rejected.
4. An assignment (`member_policy_assignments`/`member_employer_assignments`)
   that does not cover the service date — rejected. This is exactly the
   shape of the six legacy claims' problem.
5. Claim **creation**, end-to-end through `ClaimService.createClaim`,
   bypassing any frontend check entirely — rejected server-side.
6. Editing a **DRAFT**'s `serviceDate` to move it outside the policy's
   window — also rejected, though by a different mechanism than creation
   (below).

## How it's enforced — two mechanisms, not one

- **Creation**: `MemberContextResolver.resolveForOrFail`, called from
  `ClaimService.createClaim` before `ClaimMapper` runs at all, resolves
  employer/policy/assignment strictly for `serviceDate` and throws
  `BusinessRuleException` on any mismatch.
- **Update**: `ClaimMapper.processEngineCalculations` itself resolves the
  policy *leniently* (`MemberPolicyResolver#resolveFor`, returns
  `Optional.empty()` on no match, does not throw) — reading only that
  method suggests editing a draft's `serviceDate` could slip through. It
  cannot: the same call chain ends in
  `ClaimFinancialAdjudicationService#adjudicate`, which resolves the policy
  a **second time**, strictly, via `#resolveForOrFail`, and throws before
  the update persists.

That second point is worth flagging on its own, separately from this
finding: the update path asks "what policy applies at this serviceDate"
twice in one request, through two different resolvers with two different
failure behaviors, and only the second one is fail-closed. It works today
because both call sites happen to run in the same request before anything
is persisted — but it is the same shape of duplicated dated-resolution this
project's `CLAUDE.md` calls out as a "طريقة واحدة معتمدة" violation risk,
and P0 exists specifically because a duplicated resolution silently drifted
once already (`Claim.policyId` never being captured from the context that
had already authorized the claim). Not fixing it here — flagging it so it
isn't independently rediscovered later.

## Conclusion for the six legacy claims

Since main already fails closed on this exact scenario (#4 above
reproduces it directly), the six legacy claims almost certainly predate
this gate being added to the codebase — not a live, currently-reachable
bug. This does not by itself tell us which of the three explanations in
`CLAIMS_POLICY_SNAPSHOT_BACKFILL_GAPS.md` (wrong policy start date, wrong
service date, or a wrongful approval) is correct for those six specific
claims — that still needs the documentary evidence described there. It
does mean: no new claim can be created or edited into the same state today,
so this is a closed historical data question, not an open engine defect.
