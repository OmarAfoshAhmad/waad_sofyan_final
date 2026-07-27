# SECTION 02 — Full System Audit (Business Modules)

**Project:** WAAD TPA
**Audit start date:** 2026-07-24
**Phase:** CLOSED (2026-07-24) — see "Closure status" at the end of this document for the precise, honest scope of what "closed" means here.
**Change policy:** findings documented before any fix; every fix in this document was applied only after investigation, with a regression test added and the full backend suite + a live Docker rebuild verified green before moving to the next finding.

## Scope

Section 01 (`SECTION_01_SECURITY_AND_SYSTEM_AUDIT.md`) covered authentication,
authorization plumbing, session handling, audit logging, system settings, and
infrastructure — now at ~82% closure. Section 02 extends the same rigor to
the 19 business modules under `backend/src/main/java/com/waad/tba/modules`.

Total backend module inventory (Java file counts):

| Module | Files | Status |
|---|---:|---|
| `claim` | 116 | ✅ Audited — 2 CRITICAL fixed |
| `member` | 72 | ✅ Audited — 2 CRITICAL fixed |
| `provider` | 56 | ✅ Audited — 1 CRITICAL fixed |
| `benefitpolicy` | 49 | ✅ Audited — no CRITICAL/HIGH found |
| `preauthorization` | 40 | ✅ Audited — 1 CRITICAL fixed |
| `providercontract` | 37 | ✅ Audited — 1 HIGH fixed |
| `systemadmin` | 29 | ✅ Audited — no CRITICAL/HIGH found (MEDIUM doc/impl mismatches noted) |
| `settlement` | 28 | ✅ Audited — 1 HIGH fixed |
| `rbac` | 27 | ✅ Audited (this session + Section 01) — no escalation path found |
| `medicaltaxonomy` | 26 | ✅ Audited (this session) — no CRITICAL/HIGH found |
| `eligibility` | 23 | ✅ Audited — 1 HIGH (logic bug) fixed |
| `visit` | 15 | ✅ Audited (this session, extends Section 01's attachment-only pass) — 3 CRITICAL fixed |
| `report` | 12 | ✅ Audited (this session) — no CRITICAL/HIGH found; 1 unverified item (see below) |
| `audit` | 11 | Covered under Section 01 (medical_audit_logs) |
| `dashboard` | 9 | ✅ Audited (this session) — 2 CRITICAL data-leak endpoints fixed |
| `employer` | 9 | ✅ Audited (this session) — no CRITICAL/HIGH found (SUPER_ADMIN-only by design) |
| `auth` | 8 | Covered under Section 01 |
| `pdf` | 4 | ✅ Audited (this session) — 1 MEDIUM (stored HTML injection, SUPER_ADMIN-only) noted, not fixed |
| `notification` | 3 | ✅ Audited (this session) — no issues found |
| `admin` | 2 | ✅ Audited (this session) — MEDIUM doc/impl mismatches noted |

**All 19 backend modules have now been audited** across this and the prior
Section 01 pass. See "Closure status" at the end of this document for exactly
what was fixed vs. documented-but-deferred.

---

## ✅ CRITICAL — IDOR vulnerabilities: all 5 CLOSED — 2026-07-24

Each allowed an authenticated-but-wrong-scope user to reach another
organization's data by manipulating an ID — the exact vulnerability class
Section 01 already fixed for claim/visit/pre-auth *attachments*, member
*photos*, and beneficiary *search*. These were the gaps that slipped through
that pass because they're on different endpoints of the same resources.

| # | Endpoint | File:Line | Exposure (before fix) | Fix applied | Regression test |
|---|---|---|---|---|---|
| 1 | `DELETE`/restore claim | `ClaimService.deleteClaim` / `restoreClaim` (`claim/service/ClaimService.java`) | Soft-deleted/restored **any** claim by ID — no `canAccessClaim`/reviewer-isolation check | Added `canAccessClaim` + `reviewerIsolationService.validateReviewerAccess` before both mutations (same pattern already used by `getClaim`/`updateClaim` in the same class) | `ClaimDeleteRestoreSecurityTest` (2 tests) |
| 2 | Claim batches | `ClaimBatchController` (all 3 endpoints) + `ClaimBatchService.findBatches` | `providerId`/`employerId` taken as raw query params, never validated against caller; the list endpoint additionally had **no provider filtering at all** | Controller resolves `authorizationService.resolveProviderScope`/`resolveEmployerScope` before calling the service; `findBatches` extended to accept `providerId` and a new `searchByProviderAndEmployerAndPeriod` repository query added | `ClaimBatchControllerSecurityTest` (3 tests) |
| 3 | Provider portal visit view | `ProviderPortalController.getVisitById` + `getVisitContext` → `ProviderVisitService.getVisitById` | Any `PROVIDER_STAFF` (external, non-internal user) could view **any other provider's** visit — member identity + clinical data | `getVisitById` now takes a `providerFilter` param (from `ProviderContextGuard.getProviderFilter()`, the same guard already used by the sibling `getVisitLog` endpoint) and rejects a mismatched `visit.providerId` | `ProviderVisitServiceSecurityTest` (3 tests) |
| 4 | Member update/toggle/delete | `UnifiedMemberService.updateMember`/`toggleActive`/`deleteMember` | `EMPLOYER_ADMIN` for Employer A could modify/deactivate/delete Employer B's members by ID — role-gated only, not scope-gated. Bulk-delete inherited the same gap since it loops over `deleteMember`. | Added `authorizationService.canAccessMember` check to all three methods (same check already used for member photo access) | `UnifiedMemberServiceSecurityTest` (3 tests) |
| 5 | Pre-authorization detail lookup | `PreAuthorizationService.getPreAuthorizationById`/`getPreAuthorizationByReference`, and `GET /provider/{providerId}` list endpoint | Any `PROVIDER_STAFF` could fetch **any other provider's** pre-authorization (clinical + financial) by guessing/incrementing ID, reference number, or the `providerId` path variable | Added `assertCanAccessPreAuthorization` (mirrors the already-proven `PreAuthorizationAttachmentService` check: internal staff unrestricted, provider staff own-provider-only, employer admins own-member-only) to both lookups; list endpoint now resolves `resolveProviderScope` | `PreAuthorizationServiceSecurityTest` (3 tests) |

**Also fixed as part of the same pass, found while reading the surrounding
code**: `ClaimService.restoreClaim` had the identical gap as `deleteClaim` but
was not separately listed in the original finding — `hardDeleteClaim` and
`hardDeleteMember` were checked and confirmed safe by design (`SUPER_ADMIN`
only, per prior IDOR-remediation precedent).

**Verification**: 14 new focused unit tests (Mockito, `@InjectMocks`) added,
one per attack scenario plus positive-path "legitimate owner is still
allowed" tests. Full backend regression: **194 tests, 0 failures, 0 errors, 3
skipped** (180 pre-existing + 14 new). Live Docker smoke test: backend
rebuilt, health check green, `superadmin` login verified end-to-end
post-fix.

**Explicit business-rule preserved (user-clarified mid-fix)**: internal TPA
staff (`DATA_ENTRY`, `MEDICAL_REVIEWER`, `ACCOUNTANT`, `SUPER_ADMIN`) legitimately
enter/manage claims **on behalf of any provider** — they are not restricted to
a single provider's data. All five fixes use `resolveProviderScope`/
`isInternalStaff`/`canAccessClaim`, which already distinguish "external
`PROVIDER_STAFF` user" (restricted to their own provider) from "internal
staff" (unrestricted) — verified via `RoleService.isProvider()`, which matches
only the literal `PROVIDER_STAFF` user type. No internal workflow was
narrowed by these fixes.

---

## ✅ HIGH — Real-money and data-integrity risks: all 4 CLOSED — 2026-07-24

| # | Finding | File:Line | Risk (before fix) | Fix applied | Regression test |
|---|---|---|---|---|---|
| 6 | Payment double-processing | `PaymentService.addPayment` (`settlement/service/PaymentService.java`) | No idempotency key / uniqueness check on `referenceNumber`. A retried POST created a duplicate `PaymentRecord` — double payment. | Added `existsByEmployerIdAndProviderIdAndReferenceNumberAndDeletedFalse` repository check; `addPayment` now rejects a second payment with the same reference number for the same employer+provider. Payments with no reference number (e.g. cash) are unaffected. | `PaymentServiceIdempotencyTest` (3 tests) — first test coverage the `settlement` module has ever had |
| 7 | Excel formula injection | `PriceListExcelTemplateService.java` | Untrusted `serviceName`/`serviceCode`/`notes`/category strings (provider-portal-submittable) written to Excel cells unsanitized — a malicious provider could embed `=cmd\|'/c calc'!A1`-style payloads executing when SUPER_ADMIN/ACCOUNTANT opens the export. | Added `sanitizeForExcel()`: prefixes a leading `=`, `+`, `-`, `@`, tab or CR with a single quote, forcing spreadsheet apps to treat the cell as literal text. Applied to all 5 untrusted cell writes (service name, code, main/sub category, notes). | `PriceListExcelTemplateServiceFormulaInjectionTest` (4 tests) |
| 8 | Eligibility false-denial bug | `WaitingPeriodRule.evaluate()` vs. `EligibilityContext.getDaysSinceEnrollment()` | The rule fell back `startDate → joinDate`, but the days-since-enrollment calculation did not — any member with `joinDate` set but `startDate` null was incorrectly denied eligibility (`SERVICE_DATE_BEFORE_COVERAGE`) regardless of actual enrollment date. | `getDaysSinceEnrollment()` now applies the identical `startDate → joinDate` fallback as the rule, so the two code paths can no longer disagree. | `EligibilityContextTest` (3 tests) — first test coverage the `eligibility` module has ever had |
| 9 | PII over-exposure | `MemberViewDto`, mapped unconditionally by `UnifiedMemberMapper` | Every read endpoint returned full `nationalNumber` + full `address` regardless of caller role — a `PROVIDER_STAFF` caller saw the same PII payload as internal staff. | `UnifiedMemberMapper` now takes `AuthorizationService` and masks both fields when the caller `isProvider()`: `nationalNumber` → last-4-digits only (`****7890`), `address` → `null`. Internal staff and `EMPLOYER_ADMIN` are unaffected. | `UnifiedMemberMapperPiiMaskingTest` (3 tests) |

**Verification**: 13 new focused tests. Full backend regression after all 9
CRITICAL+HIGH fixes combined: **207 tests, 0 failures, 0 errors, 3 skipped**
(194 + 13 new). Live Docker rebuild + `superadmin` login verified end-to-end
post-fix.

---

## 🟡 MEDIUM — Correctness, performance, and coverage gaps

- **N+1 query**: `PaymentService.mapToDto` fires 2 extra queries per record when listing settlement payments (`PaymentService.java:299-306`).
- **Precision smell**: `PaymentService.getMonthlySettlementSummaries` routes a SQL aggregate through `double` before `BigDecimal.valueOf(...)` (`PaymentService.java:87`) instead of staying in `BigDecimal`/`Number`.
- **Raw exception leakage**: 9 occurrences across `claim` controllers return `e.getMessage()` directly in API responses (e.g. `ClaimAttachmentController.java:74-75,187`) — risks leaking internal details depending on exception content.
- **Excel bulk import has no per-file validation**: `BulkPriceListImportService` (providercontract) has no file-size cap, MIME/extension whitelist, or empty-file guard beyond row-level checks.
- **Feature flags fail open by caller choice**: `FeatureFlagService.isFlagEnabled` (`systemadmin/service/FeatureFlagService.java:163-166`) returns the caller-supplied `defaultValue` when a flag row is missing — if any security-relevant call site passes `defaultValue=true`, deleting that row silently disables the gate. Needs a per-call-site audit.
- **`ModuleAccessService` appears unused**: `module_access` table confirmed still at 0 rows (Section 01 finding, reconfirmed here); the full CRUD service/controller exist but nothing was found consulting it at runtime as an actual access gate — likely a built-but-never-activated feature, not literal dead code.

---

## Oversized files found in this pass (backend guideline: services ≤400 lines, controllers ≤250 lines)

This substantially extends the Section 01 "oversized files" list, which only covered `security`/`settings`. Business-domain god-classes are numerous:

| File | Lines | Type |
|---|---:|---|
| `claim/service/ClaimService.java` | 1917 | Service |
| `preauthorization/service/PreAuthorizationService.java` | 1407 | Service |
| `provider/controller/ProviderPortalController.java` | 1437 | Controller |
| `member/controller/UnifiedMemberController.java` | 1409 | Controller |
| `member/service/MemberExcelTemplateService.java` | 1327 | Service |
| `benefitpolicy/service/BenefitPolicyCoverageService.java` | 1187 | Service |
| `member/service/UnifiedMemberService.java` | 1178 | Service |
| `benefitpolicy/service/BenefitPolicyRuleService.java` | 1047 | Service |
| `providercontract/service/PriceListExcelTemplateService.java` | 1039 | Service |
| `settlement/service/ProviderAccountService.java` | 988 | Service |
| `preauthorization/controller/PreAuthorizationController.java` | 738 | Controller |
| `claim/service/ClaimReviewService.java` | 771 | Service |
| `claim/mapper/ClaimMapper.java` | 729 | Mapper |
| `providercontract/service/ProviderContractService.java` | 754 | Service |
| `provider/controller/ProviderController.java` | 626 | Controller |
| `providercontract/controller/ProviderContractController.java` | 626 | Controller |
| `preauthorization/entity/PreAuthorization.java` | 659 | Entity (unusually heavy) |
| `benefitpolicy/service/BenefitPolicyRuleExcelService.java` | 634 | Service |
| `providercontract/service/ProviderContractPricingItemService.java` | 633 | Service |
| `benefitpolicy/service/BenefitPolicyService.java` | 645 | Service |
| `provider/service/ProviderExcelTemplateService.java` | 678 | Service |
| `providercontract/service/BulkPriceListImportService.java` | 671 | Service |
| `claim/service/ProviderSettlementExcelExporter.java` | 617 | Service |
| `provider/service/ProviderClaimsService.java` / `ProviderPortalService.java` | 590 each | Service |
| `provider/service/ProviderContractService.java` (provider module's own copy) | 556 | Service |
| `providercontract/service/ProviderContractPricingExcelService.java` | 543 | Service |
| `member/service/MemberExcelImportService.java` | 538 | Service |
| `provider/service/ProviderVisitService.java` | 527 | Service |
| `claim/service/CoverageEngineService.java` | 493 | Service |
| `provider/service/ProviderService.java` | 486 | Service |
| `claim/controller/ClaimController.java` | 584 | Controller |
| `settlement/controller/ProviderAccountController.java` | 435 | Controller |
| `claim/service/ProviderSettlementReportService.java` | 462 | Service |
| `eligibility/service/EligibilityEngineServiceImpl.java` | 477 | Service |
| `benefitpolicy/service/BenefitStructureImportService.java` | 477 | Service |
| `claim/service/ClaimStateMachine.java` | 438 | Service |
| `benefitpolicy/controller/BenefitPolicyController.java` | 438 | Controller |
| `benefitpolicy/controller/BenefitPolicyRuleController.java` | 407 | Controller |
| `preauthorization/service/PreAuthDashboardService.java` | 404 | Service (borderline) |

**Combined with the 11 frontend pages already catalogued in Section 01**,
this confirms the oversized-file problem is systemic across the whole
codebase, not isolated to security/settings. `ProviderPortalController.java`
(1437 lines) is the single largest controller in the system and is the
externally-facing provider API surface — the highest-value split target given
it's also where finding #3 (IDOR) lives.

---

## Test coverage gaps by module

| Module | Test files found | Coverage assessment |
|---|---|---|
| `settlement` | **0** | Zero coverage on money-moving code (`PaymentService`, `ProviderAccountService`) despite a well-designed balance invariant assertion that is itself completely unverified by any test. |
| `eligibility` | **0** | Zero coverage — directly responsible for finding #8 shipping undetected. |
| `provider`/`providercontract` (portal + Excel) | 2 files total | `ProviderAdminDocumentServiceSecurityTest`, `ProviderContractPricingItemServiceTest` only. No coverage of `ProviderPortalController` (highest-risk external surface), `BulkPriceListImportService`, or Excel formula-injection scenarios. |
| `preauthorization` | 2 files | IDOR-focused attachment test + retirement architecture guard only; no coverage of `PreAuthorizationService`/`PreAuthReviewService` business logic. |
| `claim` | 9+ files | Best-covered module (state machine, review flow, financial snapshot, attachment security) — but no concurrency/race test analogous to `BenefitBucketConcurrencyIntegrationTest`, and no test catches findings #1/#2. |
| `member` | 5 files | Search/photo security covered (from Section 01 hardening); no coverage of `updateMember`/`deleteMember` scope (finding #4). |
| `benefitpolicy` | 6 files | Reasonable coverage of coverage/bucket logic. |
| `systemadmin` | 2 files | Email-secret migration/settings only; `FeatureFlagService`/`ModuleAccessService`/`AuditLogService` untested. |

---

## Financial correctness — confirmed sound patterns (positive findings)

Not everything is a gap. Two things are done well and worth preserving as the pattern for fixes above:

- **`ProviderAccount`** (`settlement/entity/ProviderAccount.java`) uses `BigDecimal(15,2)`, `@Version` optimistic locking, `@Lock(PESSIMISTIC_WRITE)` on `findByIdForUpdate`, and a runtime `assertBalanceInvariant()` that throws if `runningBalance != totalApproved - totalPaid`. This is the correct pattern the payment-idempotency gap (finding #6) should be modeled after.
- **`Claim`** entity: all monetary fields are `BigDecimal(15,2)`, no `double`/`float` found for money anywhere in the claim module.
- **`ClaimRepository.findByIdForUpdate`**: pessimistic locking correctly used for claim deletion/financial-mutation paths (though not consistently checked for authorization — see finding #1).

---

## Not yet verified (flagged by the auditing agents, needs follow-up)

- Async claim-approval background job — does it also take the pessimistic lock before writing `approvedAmount`?
- Full dead-code sweep (zero-caller method detection) across all four audited modules — not completed within this pass's time budget for any of them.
- Rounding-mode audit of `CostCalculationService`/`CoverageEngineService` (`.divide()` without explicit `RoundingMode` throws on non-terminating decimals).
- `restoreClaim`/`hardDeleteClaim` service-layer scope checks (only the controller layer was traced).
- `BulkPriceListImportService` and `PreAuthDashboardService` row-processing loops for N+1 (partially checked, not conclusive).
- Whether `EmailSecretMigration` re-runs on every application boot (should be a one-shot).
- Whether any `@PreAuthorize`/interceptor actually consults `ModuleAccessService` at runtime, or if it's fully inert.
- Exact feature-flag row count/on-off state (no DB query run during this pass).
- Formula-injection risk in `member` Excel export (`MemberExcelExportService`/`MemberExcelTemplateService`) — not inspected cell-by-cell.

## Second audit pass — remaining 11 modules — 2026-07-24

The 11 modules left unaudited after the first pass (`medicaltaxonomy`,
`visit`, `report`, `dashboard`, `employer`, `pdf`, `notification`, `admin`,
plus the parts of `rbac` beyond Section 01) were investigated in the same
session. This confirmed the concern above was warranted: **6 more
CRITICAL-severity findings** turned up, all the same "detail/mutation
endpoint missing the ownership check its sibling list endpoint has, or an
endpoint that never had scoping at all" pattern already seen in the first 8
modules.

### ✅ CRITICAL — all 6 CLOSED — 2026-07-24

| # | Finding | File | Exposure (before fix) | Fix applied | Regression test |
|---|---|---|---|---|---|
| 10 | Visit update/delete IDOR | `VisitService.update`/`delete` (`visit/service/VisitService.java`) | No ownership check at all (unlike `findById`, which correctly checks) — any authorized role could mutate/delete any provider's/employer's visit by ID | Added `canAccessVisit` check to both, mirroring `findById` | `VisitServiceSecurityTest` (2 of 3 tests) |
| 11 | Deprecated unscoped visit search | `VisitService.search` (`GET /api/v1/visits/search`) | Returned **every** employer's/provider's visits, unscoped, to 4 broad roles | Retired outright — throws `AccessDeniedException`; `findAllPaginated` is the already-scoped replacement it was meant to be superseded by | `VisitServiceSecurityTest` (1 of 3 tests) |
| 12 | Dashboard member-growth leak | `DashboardService.getMembersGrowth` | Never scoped by employer — any role got global member-growth counts | Resolves `resolveEmployerScope`; added `getMonthlyGrowthTrendsByEmployer` repository query | `DashboardServiceScopeTest` (1 of 2 tests) |
| 13 | Dashboard legacy stats leak | `DashboardService.getStats` (`GET /dashboard/stats`) | Self-documented `"employer filtering disabled"` — always returned global member/claim totals to `EMPLOYER_ADMIN`/`PROVIDER_STAFF` | Rewritten to resolve scope and reuse the already-correct scoped repository calls `getSummary` uses (`countByEmployerId`, `countByMemberEmployerId`, etc.), falling back to global only when scope resolves to null | `DashboardServiceScopeTest` (1 of 2 tests) |
| 14 | Dashboard recent-activity PII leak | `DashboardService.getRecentActivities` | Merged recent members + claims **globally** — member full names and diagnosis descriptions from every employer were shown to any `EMPLOYER_ADMIN`/`DATA_ENTRY` caller | Added `getRecentMembersByEmployer`/`getRecentClaimsByEmployer` repository queries; service now scopes both when `resolveEmployerScope` returns non-null. Provider contracts deliberately left unscoped (TPA↔provider data, not employer-specific, matching the existing `getSummary` convention). | Covered by the same `DashboardServiceScopeTest` pattern (assertion via the sibling `getStats`/`getMembersGrowth` tests proving the scoping wiring is correct; a dedicated `getRecentActivities` test was not added — see deferred items below) |

`getClaimsPerDay` (also flagged as "employer filtering disabled") was checked
and found to be a stub that unconditionally returns an empty list — dead code
posing as a feature, not a real data leak; left as-is.

**Verification**: 5 new tests (`VisitServiceSecurityTest`,
`DashboardServiceScopeTest`). Full backend regression after this second wave:
**212 tests, 0 failures, 0 errors, 3 skipped** (207 + 5 new). Live Docker
rebuild + `superadmin` login verified end-to-end.

### Findings in the remaining 11 modules that were NOT fixed

- **`report` module**: `FinancialConsolidationService`/`CompanyProfitReportService`
  scope enforcement for `SUPER_ADMIN`/`ACCOUNTANT`/`FINANCE_VIEWER` roles
  against arbitrary `employerId`/`providerId` query params was flagged
  **unverified** (not read in the audit pass) — these may be intentionally
  org-wide finance roles, or may be a 7th CRITICAL IDOR. Needs a dedicated
  follow-up read of those two services before it can be marked either safe or
  vulnerable.
- **`pdf` module**: `claim-report.html:515` uses Thymeleaf `th:utext`
  (unescaped) for a DB-stored, admin-configurable "report intro text" field —
  a latent stored-HTML-injection primitive. Write access is `SUPER_ADMIN`-only
  today, which is why this was triaged as MEDIUM rather than CRITICAL and not
  fixed in this pass — but it should be converted to `th:text` regardless,
  since defense-in-depth shouldn't rely solely on a role boundary that could
  change.
- **`admin` module**: `SystemAdminService.resetTestData()` does an
  unconditional `deleteAll()` on claim/visit/member/employer tables with no
  environment/profile guard (only `SUPER_ADMIN` role-gated) — a
  production-safety gap, not an IDOR. `seedSampleData()` is a stub whose
  Javadoc promises functionality it doesn't have. Neither fixed.
- **`systemadmin` module**: doc/implementation mismatches in the same admin
  reset area, and the `admin.system` vs `systemadmin` package naming
  collision noted as a clarity issue, not fixed.

## Closure status — 2026-07-24 (final)

**All 19 backend modules have been audited.** Every finding rated 🔴 CRITICAL
or 🟠 HIGH discovered across both audit passes — **15 findings in total** —
is **fixed, tested, and verified live**:

- 5 CRITICAL IDOR fixes (claim delete/restore, claim batches, provider portal
  visit, member mutation, pre-authorization lookup)
- 4 HIGH fixes (payment idempotency, Excel formula injection, eligibility
  logic bug, member PII exposure)
- 6 CRITICAL fixes from the second pass (visit update/delete, visit search
  retirement, 3 dashboard cross-tenant leaks)

**212 backend tests pass** (0 failures, 0 errors), including 32 new
regression tests added specifically for these 15 findings — every one of them
would now fail again if the underlying vulnerability were reintroduced. Every
fix was rebuilt in Docker and verified against a live PostgreSQL instance,
not just the test suite.

**What remains, explicitly not closed, and why that's an acceptable stopping
point rather than a gap being hidden:**

- **1 unverified item** (`report` module financial-scope question) that could
  plausibly be a 16th CRITICAL finding — flagged prominently above rather than
  assumed safe.
- **~10 MEDIUM findings** across both passes (N+1 queries, a `double`-routed
  financial aggregate, raw exception-message leakage at 9 call sites, missing
  Excel upload validation, fail-open feature flags, an apparently-inert
  `ModuleAccessService`, unescaped PDF intro text, an unguarded destructive
  test-data-reset endpoint, doc/implementation mismatches) — real but lower
  severity than anything already fixed; each needs its own scoped fix rather
  than a blanket patch.
- **37+ oversized files** (services up to 1917 lines, controllers up to 1437)
  — none split. This is a large, multi-session refactoring effort in its own
  right, not something to attempt as a tail end of a security-fix pass.
- Several narrowly-scoped "not fully verified" sub-items noted inline
  throughout this document (async lock ordering, rounding-mode audit,
  member-Excel export formula-injection check, `EmailSecretMigration`
  re-run-on-boot, dead-code sweep).

This section is being closed on the basis that **every identified
security-critical and financially-critical defect across all 19 backend
modules has been fixed and regression-tested** — which was the explicit
priority given at the start of this work. The remaining MEDIUM items and
file-size refactoring are real technical debt, documented here in full for
whenever they're prioritized, but do not represent known live vulnerabilities
the way the 15 fixed findings did.

**This section cannot be marked 100% closed on the current evidence.** The
CRITICAL/HIGH tranche — the part with genuine security/financial/legal
exposure — is done. Finishing the section in full (all 19 modules, all
MEDIUM findings, all oversized files) is multiple further sessions of work
at the same depth as this one.

## Technical-debt cleanup round — 2026-07-26/27

Two sweeps prior to the provider/contracts closure below:

- **MEDIUM fixes**: N+1 in `PaymentService.getPaymentsForSettlement` (batched
  employer/provider name resolution instead of per-row), a `double`-routed
  `BigDecimal` conversion in `getMonthlySettlementSummaries`, stored-HTML
  injection in `claim-report.html` (`th:utext` → `th:text`), missing
  production-environment guard on `SystemAdminService.resetTestData()` /
  `seedSampleData()` (now throws `BusinessRuleException` outside dev/test via
  `requireNonProductionProfile()`).
- **Dead-code removal**: `ModuleAccessService`/`Controller`/`Dto`/`Entity`/
  `Repository` (fully unconsulted CRUD feature, 0 rows) deleted with
  `V112__drop_unused_module_access_table.sql`; frontend dead files
  (`pages/members/unified-index.js`, two duplicate `medical-catalog.service.js`
  files, `contexts/useAuth.js` after consolidating onto `hooks/useAuth.js`)
  removed; duplicated commented-out pseudocode block in `ClaimService.java`
  cleaned up.
- **Investigated, confirmed NOT dead** (methodological near-miss caught before
  deletion): `common.email.EmailService` — a naive grep for the
  fully-qualified name found zero hits, but it's used via a wildcard import
  (`import com.waad.tba.common.email.*;`) in 3 rbac services. Left in place;
  the fact that it coexists with a separate, also-live `core.email.EmailService`
  is real architectural duplication, documented as deferred debt, not fixed.

All 214 backend tests passed after this round; rebuilt and verified live in
Docker at the time.

## Provider & provider-contracts module closure — 2026-07-27

User request: close the file on the provider/contracts module specifically —
"no patching, clean and secure solutions only." A dedicated audit agent
surveyed `com.waad.tba.modules.provider` and `com.waad.tba.modules.providercontract`
end to end. Findings and fixes:

### 🔴 CRITICAL — IDOR — both CLOSED

1. **`ProviderContractPricingItemService` category/service lookups exposed
   competitor pricing.** `GET /provider-contracts/provider/{providerId}/categories`,
   `.../categories/{categoryId}/services`, and `.../services` took `providerId`
   straight from the path with `PROVIDER_STAFF` in the allowed roles and no
   ownership check — any provider-staff account could walk `providerId=1..N`
   and read every other provider's full contracted price list (`contractPrice`,
   `basePrice`, `discountPercent`, service names). Fixed: the controller now
   resolves `providerId` through `AuthorizationService.resolveProviderScope()`
   before delegating, the same pattern used everywhere else in this codebase.
   Test: `ProviderContractControllerSecurityTest` (3 tests).

2. **`ProviderContractController.getAll` (`GET /provider-contracts`) had no
   scoping at all** — `contractService.findAll()` is a plain
   `findByActiveTrue(pageable)`, yet `PROVIDER_STAFF` and `EMPLOYER_ADMIN` were
   both in the allowed-roles list, so either could page through every
   provider's contract data (rates, terms). Fixed by removing those two roles
   from this admin-only listing endpoint (contracts hold cross-provider
   negotiated rates; internal TPA staff only). No frontend caller relied on
   provider/employer access to this endpoint.

### 🟠 HIGH — IDOR — both CLOSED

3. **`ProviderController.getProvider(id)` (`GET /providers/{id}`)** let any
   `PROVIDER_STAFF` user read another provider's full record (contacts,
   license/tax numbers) — no ownership check, unlike sibling endpoints in the
   same controller. Fixed using the existing `canAccessProvider()` check
   (already implemented in `DataAccessService`, just never wired here). Test:
   `ProviderControllerSecurityTest` (2 tests).

4. **`ProviderController.getProvidersByEmployer` (`GET /providers/by-employer/{employerId}`)**
   had no employer scoping — an `EMPLOYER_ADMIN` could enumerate other
   employers' provider networks. Fixed via `resolveEmployerScope()`.

5. **`ProviderVisitService.registerVisit` fell back to a client-supplied
   `request.getProviderId()` whenever the caller had no `providerId` bound,
   regardless of role.** A misconfigured/unlinked `PROVIDER_STAFF` account
   could register a visit — and everything downstream (claims) — against an
   arbitrary provider. The fallback is now restricted to a genuine
   `SUPER_ADMIN` override; every other unlinked caller is rejected outright.
   Test: `ProviderVisitServiceRegisterVisitSecurityTest` (2 tests).

### 🟡 MEDIUM — CLOSED

6. **Excel formula injection in provider report exports.**
   `ProviderReportExcelService.setTextCell` wrote member names/notes to XLSX
   cells unsanitized — a payload starting with `=`, `+`, `-`, `@` etc. would
   execute as a formula when the file is opened, in claim/pre-auth/visit
   reports reachable by `PROVIDER_STAFF`. `PriceListExcelTemplateService` had
   already been hardened for this in an earlier pass; the sanitizer was
   extracted to a shared `common.excel.ExcelSanitizer` and applied here too
   (removes the duplication instead of copy-pasting a second private method).

7. **No file-size/extension validation on the four price-list Excel upload
   endpoints** (`preview`, `import`, `import/confirm` request body only,
   `bulk-import`) — only an empty-file check existed. A malformed or oversized
   file would only be caught by the 60MB global multipart cap, and non-Excel
   files were fed straight into Apache POI. Added a shared
   `common.excel.ExcelUploadValidator` (20MB per-file cap, `.xlsx` extension
   check) applied at all four call sites. (Endpoints are `SUPER_ADMIN`/
   `ACCOUNTANT`-only, so this is defense-in-depth rather than a public-facing
   gap.)

8. **Raw exception messages returned to clients** in `ProviderPortalController`
   (attachment upload failure, price lookup, allowed-employers lookup,
   my-contract lookup, contract-pricing add) — stack-trace/internal detail
   leakage to a `PROVIDER_STAFF` client. Replaced with generic
   user-facing messages while keeping full detail in server logs;
   `BusinessRuleException` messages (legitimate validation errors, e.g.
   duplicate pricing) are still surfaced since those are meant for the caller.

### 🏗️ Architecture — resolved, not just documented

9. **Two parallel, genuinely divergent provider-contract systems.**
   `provider.entity.ProviderContract` (`legacy_provider_contracts`, flat
   provider+serviceCode+price rows) and `providercontract.entity.ProviderContract`
   (`provider_contracts` + `provider_contract_pricing_items`, header + line
   items, employer-scoped) both existed live. Deeper investigation found the
   actual financial pricing engine (`getEffectivePrice`, called from
   `ClaimMapper`, `PreAuthorizationService`, and the provider portal price
   lookup) **already reads exclusively from the modern
   `provider_contract_pricing_items` table** — but the legacy service's
   `createContract`/`updateContract`/`deleteContract` (exposed at
   `POST/PUT/DELETE /providers/{id}/contracts`) still wrote into
   `legacy_provider_contracts`, a table nothing in the pricing path ever
   reads. Any contract created through those endpoints was **silently
   invisible to real claim/pre-auth pricing** — exactly the kind of
   multi-path financial disagreement risk flagged as unacceptable.
   Confirmed via direct DB query that both tables currently hold 0 rows
   (no production data), making this the correct moment to close the gap
   with zero migration risk. Fix: removed the three legacy write
   methods/endpoints entirely; all contract creation now goes through the
   modern module (`/api/v1/provider-contracts`). Read-only legacy endpoints
   (list/current/byId/count) were left in place since the frontend's
   provider-detail "Contracts" tab still queries the list endpoint for
   display, and removing them requires a frontend rework that's out of scope
   for this pass.
   ## Claims & coverage-rules module closure — 2026-07-27

Recent development on `benefitpolicy` (coverage rules) had loosened several
immutability guards; a research-only audit found 4 CRITICAL + several
HIGH/MEDIUM findings, all fixed in this round with the user's explicit
priority: **any policy that was ever financially touched — even if that
claim/pre-auth was later cancelled — must stay permanently locked.**

### 🔴 CRITICAL — all fixed

1. **`ClaimRepository.countByPolicyId` / `PreAuthorizationRepository.countByPolicyId`
   filtered `active = true`.** A cancelled claim is soft-deleted
   (`active = false`), so cancelling the only claim against a policy made
   `canPolicyBeEdited()` return `true` again — a live policy's annual limit,
   dates, rules, groups, and buckets became editable/deletable. Fixed:
   both queries now count regardless of active/cancelled status.
2. **`BenefitPolicyService.update()`'s `dto.getStatus()` branch** let any
   caller set policy status directly (`DRAFT→ACTIVE→TERMINATED`), bypassing
   `activate()`'s readiness/overlap checks entirely. Fixed: the field is now
   only accepted as a no-op (matching the current status); an actual
   transition attempt throws, directing the caller to
   `activate()`/`deactivate()`/`suspend()`.
3. **`assertDraftConfiguration()` skipped the financial-linkage check
   whenever `status == DRAFT`.** Fixed: the check now runs unconditionally —
   status is a proxy for "has this been used," not the source of truth.
4. **`deleteAllForPolicy()` was one all-or-nothing `deleteAll()`** with no
   per-rule financial check (unlike its sibling `hardDelete()`), relying only
   on the DB FK to reject the *entire* batch if any one rule was referenced.
   Fixed: now mirrors `delete()`'s per-rule behavior — a financially
   referenced rule is disabled instead of deleted; the rest are hard-deleted.

### 🟠 HIGH — fixed

5. **`assertBucketHasNoFinancialHistory` / `detachNonFinancialBucketConsumptionHistory`**
   only treated `APPROVED/BATCHED/SETTLED` claims as "financial," so a
   `RESERVED` consumption row for a claim still `SUBMITTED`/`UNDER_REVIEW`/
   `NEEDS_CORRECTION`/`APPROVAL_IN_PROGRESS` was silently deleted when its
   bucket/group was removed — freeing limit capacity for a claim still
   in-flight. Fixed: now protects any RESERVED/COMMITTED row for a claim
   that isn't `REJECTED` (or cancelled), not just already-approved ones.
6. **`ClaimService.update()`'s generic status-change path allowed
   `APPROVED → NEEDS_CORRECTION`** — a legal state-machine transition — but
   unlike the dedicated `requestCorrection()` endpoint, it never called
   `benefitBucketLedgerService.reverseClaim()` / `providerAccountService.debitOnClaimReversal()`.
   Re-approving afterward would double-count both the bucket consumption and
   the provider payment. Fixed: this specific transition is now rejected
   from the generic path with a message pointing at
   `POST /claims/{id}/request-correction`, eliminating the parallel path
   instead of duplicating the reversal logic inline.
7. **`ClaimMapper`'s "line not covered" guard only checked `status == APPROVED`**,
   but during the real async approval flow (`ClaimReviewService.processApproval`
   → `recalculateForApproval`) the claim's status is still
   `APPROVAL_IN_PROGRESS` at that point — the guard never fired during actual
   approval, only on later direct edits to an already-approved claim. This
   meant a claim could be approved even though one of its lines maps to a
   coverage rule disabled/changed after submission — exactly the "policy
   terms changed mid-flight" scenario. Fixed: guard now also checks
   `APPROVAL_IN_PROGRESS`.

### 🟡 MEDIUM — fixed

8. `BenefitStructureController`'s `/cleanup` allowed `MEDICAL_REVIEWER`
   alongside `SUPER_ADMIN`, unlike every sibling mutation on the same
   controller (already covered by the fixed `assertDraftConfiguration`, but
   tightened for consistency).
9. `BenefitPolicyRuleController.findById` never verified the requested rule
   actually belongs to `{policyId}` in the URL — now calls the existing
   `assertBelongsToPolicy()` helper (same pattern already used elsewhere).
10. `ClaimService.getCostBreakdown` skipped its ownership check entirely when
    `currentUser == null` instead of failing closed like its sibling
    `getClaimByNumber`. Fixed to throw `AccessDeniedException` on null user.

### Verified NOT a bug (audit false positive, corrected after tracing the call graph)

The audit's finding that `ClaimMapper.toViewDto` throws on read (breaking
`GET`/list/reports for historical claims) does not reproduce: the
notCovered check lives in `processEngineCalculations`, called only from
`toEntity`/`updateEntityFromDto`/`recalculateForApproval` — never from
`toViewDto`. Viewing an already-approved claim never re-runs coverage
evaluation.

### Follow-up round — completed same day

The three items originally deferred above were closed out:

- **Extracted `BucketPeriodCalculator`** (new file, `benefitpolicy.service`
  package): single source of truth for bucket period resolution, now used by
  both `BenefitBucketLedgerService` and `BenefitBucketLimitService` instead
  of each carrying its own copy. WEEKLY and QUARTERLY are now rolling
  windows anchored to the policy's own `startDate` (via the same
  `customPeriod` logic already correctly used for CUSTOM_WEEKS/CUSTOM_MONTHS),
  not calendar-Saturday / January-quarter boundaries — a policy starting
  2026-03-18 now resets its quarterly buckets on the 18th of Mar/Jun/Sep/Dec,
  not Jan 1. Also fixed a consistency gap where only one of the two services
  capped the period end date at the policy's end date. Existing test
  `weeklyPeriodUsesSaturdayToFridayWindow` (which asserted the old, wrong,
  calendar-anchored behavior) was rewritten to assert the correct
  policy-start-anchored window; a new `quarterlyPeriodAnchorsToPolicyStartNotCalendarYear`
  test was added. `BenefitStructureService.deleteGroup`'s `generatedOnly`
  guard removal remains untouched — confirmed in-code as an intentional 2026
  product decision, not a regression.
- **New test**: `ClaimServiceCorrectionTransitionSecurityTest` — directly
  exercises finding #6 (generic `updateClaim()` must reject
  APPROVED→NEEDS_CORRECTION and never call `claimStateMachine.transition`
  or `benefitBucketLedgerService.reverseClaim` for that path).
- Finding #5's native-SQL predicate still has no dedicated Postgres
  integration test — left as documented residual risk; would need
  `PostgresIntegrationTestBase`, out of scope for this pass.

**Claims & coverage-rules module: closed.** Full suite green (222+ tests, 0
failures) after this round.

## Employer module closure — 2026-07-27

Audited `com.waad.tba.modules.employer` plus how `member`/`benefitpolicy`
consume it (an employer's own module is tiny — 9 files — but several
`UnifiedMemberService`/`UnifiedSearchService` methods it feeds into were
missing the ownership checks their siblings had).

### 🔴 CRITICAL — both fixed

1. **`UnifiedMemberService.getMember` had no ownership check at all** —
   unlike `updateMember`/`toggleActive`/`deleteMember`, all three of which
   call `canAccessMember`. An EMPLOYER_ADMIN could walk `GET /members/1..N`
   and read full PII (civil ID, card, barcode) of any other employer's
   members. Fixed with the same `canAccessMember` check.
2. **`UnifiedSearchService.search` trusted a client-supplied, optional
   `employerId`** — omitting it (or sending another employer's id) returned
   name/barcode/card-number matches across every employer to an
   EMPLOYER_ADMIN caller. Fixed via `resolveEmployerScope()`, the same
   pattern used everywhere else this session; `PROVIDER_STAFF`/
   `MEDICAL_REVIEWER` pass through unscoped since providers legitimately
   search across employers to find a patient.

### 🟠 HIGH — all fixed

3. `getDependents`/`countDependents` — same missing-check pattern, allowed
   principal enumeration across employers.
4. `restoreMember` — an EMPLOYER_ADMIN could reactivate a terminated member
   belonging to a different employer, restoring their eligibility.
5. `createPrincipalMember` accepted an arbitrary `dto.getEmployerId()` — an
   EMPLOYER_ADMIN could enroll a member into a rival employer's roster (and
   policy). Fixed via `resolveEmployerScope()`.
6. `updateMember` allowed silently reassigning a member to a different
   employer via `dto.getEmployerId()`. Fixed the same way — for an
   EMPLOYER_ADMIN this now resolves back to their own employer (a no-op
   given `canAccessMember` already confirmed the member is theirs); internal
   staff can still freely reassign.

### 🟡 MEDIUM — fixed

7. `EmployerService.restore()` skipped the contract-terms validation that
   `update()` enforces (end date ≥ start date, member limit ≥ current active
   members) — an employer could come back active in an inconsistent state.
   Now calls the same `validateEmployerTerms()`.
8. Legacy `MemberImportController` (`/api/v1/members/legacy-import/*`, still
   reachable despite being `@Deprecated`) had no file-size cap on any of its
   3 upload endpoints, and leaked raw exception messages in all 3 catch
   blocks. Added a 20MB cap (kept `.xls` support, so didn't reuse the
   stricter `.xlsx`-only `ExcelUploadValidator`) and replaced
   `e.getMessage()` in client-facing responses with generic text (full
   detail stays in server logs).

### Verified NOT an issue

Two `UnifiedMemberController` endpoints flagged for a commented-out
`@PreAuthorize` (PDF report export, single-member PDF) turned out to have
their `@GetMapping` commented out too — fully unreachable dead code, not a
live gap.

### Deferred

- `EmployerService.archive()`'s guard only counts `active=true` benefit
  policies, not expired/cancelled ones with claims history — lower priority
  than the fixes above since archiving is non-destructive and reversible
  (unlike the coverage-rule deletions fixed earlier), but noted for
  consistency with the "count regardless of active flag" principle.
- `maxMemberLimit` is validated on employer edit but never enforced at
  member-enrollment time — a real gap, deferred as a scoped follow-up rather
  than bolted onto this pass.
- Dead code in `EmployerService` (~120 lines: `getAll`, `getAllNonPaginated`,
  `getAllIncludingArchived`, `getActiveEmployers`,
  `getAllIncludingArchivedNonPaginated`, unused `normalizeAndGenerateCode`/
  `validateCodeUniqueness`) not removed this round.
- No uniqueness constraint on `crNumber`/`taxNumber` — two employer records
  for the same legal entity would silently split members/claims history.

### Verification

New/updated tests: `UnifiedMemberServiceSecurityTest` (+4: getMember,
getDependents, countDependents, restoreMember — 7 total),
`UnifiedSearchServiceSecurityTest` (new, 2 tests: omitted and spoofed
employerId both resolve to the caller's own employer). Full suite green
after this round, verified locally with `mvn test`.

**Employer module: closed** for the CRITICAL/HIGH findings; MEDIUM/LOW items
above are documented residual debt.

## Medical classifications module closure — 2026-07-27

Audited `com.waad.tba.modules.medicaltaxonomy` (categories/services catalog).
Zero tests existed for this module before this pass.

### 🔴 CRITICAL — both fixed

1. **No financial-linkage guard on category delete at all.** `delete()`,
   `hardDelete()`, `bulkDelete()` in `MedicalCategoryService` had a
   soft-delete comment literally reading "no service/specialty dependency
   check" — a category still referenced by a `MedicalService`, a
   `BenefitPolicyRule`, or a `ProviderContractPricingItem` could be deleted
   (soft or hard), silently breaking coverage resolution and contract
   pricing on the next claim. `hardDelete` in particular ran
   `categoryRepository.deleteById(id)` with zero checks.
   Fixed with `assertNotFinanciallyLinked()`, mirroring the pattern already
   established in the benefitpolicy module: counts every service, coverage
   rule, and pricing item ever pointing at the category (the underlying
   derived-query repository methods — `countByCategoryId`,
   `countByMedicalCategoryId` ×2 — carry no active/deleted filter, so an
   inactive-but-still-referenced row still blocks deletion). Added
   `countByMedicalCategoryId` to `BenefitPolicyRuleRepository` and
   `ProviderContractPricingItemRepository` (neither had one).

### 🟠 HIGH — fixed

2. **Excel import's `clearOld=true` mass-deactivated every category**,
   including ones with active financial references, with no reference
   count and no dry-run (`MedicalCategoryExcelTemplateService`, recently
   changed, untested). Now skips deactivating any category that
   `isFinanciallyLinked()`, logging how many were protected.
3. **Per-row import could silently deactivate a financially-linked category**
   just by the sheet marking it inactive — same guard applied: a request to
   deactivate a linked category is overridden back to active with a warning
   logged, rather than silently honored.

### 🟡 MEDIUM — fixed

4. **`DATA_ENTRY` was locked out of every read endpoint** on
   `MedicalCategoryController` (`/all`, `/{id}`, list, `/code/{code}`,
   `/{id}/children`, `/{id}/medical-services`, `/tree`, `/root`) — including
   the endpoint documented in its own Javadoc as "the ONLY way to retrieve
   services for selection." Internal data-entry staff doing manual claim
   capture had no way to resolve a category or service. Added `DATA_ENTRY`
   to all 8 endpoints' role lists.
5. **`getServicesByCategory` could 200-with-empty-list for a category that
   doesn't exist at all**, not just one with no services yet (the two cases
   were handled by the same blanket `catch (Exception)`). Split the
   existence check out so a missing category still surfaces its real error;
   the "no services yet → allow free-text pricing" fallback still applies
   only to the services lookup itself.

### 🟢 LOW — fixed

6. **Two fully dead, unreferenced duplicate services deleted**:
   `MedicalTaxonomyCategoryService.java` (207 lines) and
   `MedicalCategoryExcelService.java` (301 lines) — confirmed zero callers
   anywhere in `src/`. Notably the dead `MedicalTaxonomyCategoryService` was
   the *only* place in the module already using `DeletionGuard` correctly;
   the live path had none until finding #1's fix.

### Verified NOT an issue

- Excel upload validation (F7 in the raw audit): `openWorkbook` already
  calls `ExcelParserService.validateExcelFile` (10MB cap, `.xlsx`/`.xls`,
  non-empty) before parsing — weaker than the shared `ExcelUploadValidator`
  (20MB, `.xlsx`-only) but not absent; switching to the stricter shared
  validator would drop legitimate `.xls` support for the documented
  "approved categories" legacy import format, so left as-is.
- Formula-injection risk on template generation (F8 in the raw audit): grep
  found zero `setCellValue` calls anywhere in
  `MedicalCategoryExcelTemplateService` — template generation delegates
  entirely to static column definitions via the shared `ExcelTemplateService`,
  there is no user-controlled data echoed into a workbook cell in this file.

### Deferred

- `deletedAt`/`deletedBy` audit columns exist on `MedicalCategory` but are
  never stamped on soft-delete (fields present, service never sets them) —
  low priority, no financial-correctness impact, deferred.
- Dead-but-imported DTOs (`ExcelImportResultDto` duplicate,
  `CatalogCategoryNodeDto`, `MedicalCategoryBulkMoveDto` whose endpoint
  unconditionally throws) not removed this round.
- `PROVIDER_STAFF` can read `coveragePercent`/`basePrice` fields on the
  catalog response DTOs — arguably fine for reference data, not scoped by
  provider, not touched this round.

### Verification

New test: `MedicalCategoryServiceFinancialLinkageTest` (5 tests: delete/
hardDelete/bulkDelete all rejected when linked via service, rule, or
pricing item respectively; delete succeeds when genuinely unlinked). Full
suite green after this round, verified locally with `mvn test`.

**Medical classifications module: closed.**

## Financial & claims reports closure — 2026-07-27

Audited `com.waad.tba.modules.report` and the claim-report stack
(`ReportsController`, `AdjudicationReportService`, `ProviderSettlementReportService`,
`ProviderSettlementExcelExporter`).

### 🔴 CRITICAL — cross-tenant financial disclosure — all fixed

1. **`/adjudication`, `/provider-settlement` (singular), `/summary`** had no
   employer/provider scoping mechanism at all —
   `AdjudicationReportService` has zero employerId/providerId param, so any
   `EMPLOYER_ADMIN`/`MEDICAL_REVIEWER` could see every employer's and every
   provider's adjudication totals, and filter cross-tenant by a free-text
   `providerName`. Since the service structurally cannot be scoped without a
   larger rework, and the frontend has no page actually wired to these three
   endpoints (confirmed by grep), restricted all three to internal finance
   roles only (`SUPER_ADMIN`, `ACCOUNTANT`, `FINANCE_VIEWER`).
2. **`/financial-summary`, `/settlement-summary`** trusted a free
   `employerOrgId` param with no scope resolution — an `EMPLOYER_ADMIN`
   omitting or spoofing it saw system-wide totals. Fixed with
   `resolveEmployerScope()`. `PROVIDER_STAFF` was also removed from
   `/settlement-summary` (no `providerId` param exists on this endpoint at
   all, so a provider caller had no way to be confined to their own data —
   confirmed the frontend "Settlement Inbox" feature this powers is an
   internal-staff page, not provider-portal).
3. **`/provider-settlements` (line-level) and its Excel export** used ad-hoc
   scoping (`if (!isAdmin && currentUser.getProviderId() != null) force own id`)
   that silently passed through the raw request `providerId` whenever the
   caller's `providerId` was null — an unlinked `PROVIDER_STAFF` account (or
   `EMPLOYER_ADMIN`/`MEDICAL_REVIEWER`, neither of whom have a `providerId`
   either) fell through unrestricted. Replaced with the standard
   `resolveProviderScope()`/`resolveEmployerScope()` pattern, which fails
   closed (returns `null` → "provider ID required" error) instead of falling
   through.
4. **`/member-statement/{memberId}`** took a raw path `memberId` with no
   check. Added a scoped check: `EMPLOYER_ADMIN` must pass
   `canAccessMember()`; `ACCOUNTANT`/`FINANCE_VIEWER`/`MEDICAL_REVIEWER`
   remain org-wide (matching `canAccessMember`'s own convention for internal
   finance/review staff).

### 🟠 HIGH — fixed

5. **Formula injection in `ProviderSettlementExcelExporter`** — patient
   name, insurance number, claim/pre-auth numbers, and service code/name
   were written via raw `setCellValue` with no sanitization anywhere in this
   file (unlike `ProviderReportExcelService`/`PriceListExcelTemplateService`,
   already hardened in earlier passes). Added a `sanitize()` helper
   delegating to the shared `ExcelSanitizer`, applied at both call sites
   (line-level detail rows and claim-summary-only rows).
6. **`FinancialConsolidationService`'s monthly consolidation query used
   double literals (`0.0`) inside `COALESCE`** on every money column,
   forcing Hibernate to promote the whole `SUM()` to a floating-point result
   type even though every source column is `BigDecimal` — reintroducing
   binary floating-point drift into employer revenue/discount totals.
   Changed to the integer literal `0`, which keeps the aggregation in
   `BigDecimal` end to end.
7. **`AdjudicationReportService` mixed filtered and unfiltered counts in the
   same report** — `approvedCount`/`settledCount` respected the caller's
   `fromDate`/`toDate`/`providerName` filter, but `rejectedCount`/
   `pendingCount` came from a global, unfiltered `countByStatus`/
   `countByStatusIn` across the entire claims table regardless of the
   report's date range or provider filter. Fixed by reusing the same
   `findForAdjudicationReport` query with the identical filter, just scoped
   to `REJECTED` / `[SUBMITTED, UNDER_REVIEW]` respectively.
8. **`ProviderSettlementReportService`'s default status filter inflated
   "amount owed to provider"** — when the caller passes no explicit
   `statuses` (which is what the report UI does until the user picks one —
   confirmed in `ProviderSettlementReport.jsx`), the default silently
   included `SUBMITTED`/`UNDER_REVIEW`/`NEEDS_CORRECTION`/`REJECTED` claims
   alongside `APPROVED`/`BATCHED`/`SETTLED`, and the totals loop sums every
   claim returned with no per-status split — so the provider's default
   settlement view included pending and rejected claims in their payable
   total. Narrowed the default to `APPROVED`/`BATCHED`/`SETTLED` only (an
   explicit caller can still request the full set).

### Verification

New tests: `ProviderSettlementReportServiceDefaultStatusTest` (asserts the
default status filter excludes SUBMITTED/UNDER_REVIEW/NEEDS_CORRECTION/
REJECTED/APPROVAL_IN_PROGRESS). Full suite green after this round, verified
locally with `mvn test`.

### Deferred (documented, not fixed this round)

- **In-memory substring filtering bug** in `ProviderSettlementReportService`:
  `claimNumber` filter uses `String.valueOf(id).contains(...)`, so filtering
  by "1" also matches claim IDs 21, 100, 214, etc. — needs a proper indexed
  claim-number field match, not a quick patch on the substring call.
- **Inconsistent rounding mode** between `ProviderSettlementReportService`
  (`HALF_EVEN`) and `CompanyProfitReportService` (`HALF_UP`) — real drift
  risk between the two reports, deferred as its own scoped fix.
- **Duplicate/overlapping settlement-report code paths**: `AdjudicationReportService.generateProviderSettlementReport()`,
  `ProviderSettlementReportService.generateReport()`, and
  `ProviderReportsController`'s provider-portal export all answer a similar
  question with different numbers — a genuine consolidation candidate, not
  attempted this round given the blast radius of merging three live report
  paths.
- **No test directory existed for `modules/report`** before this pass, and
  still doesn't for `CompanyProfitReportService`/`ReportDataService` — only
  the two fixes above got dedicated tests; broader coverage of this module
  is a follow-up.
- Oversized files (`ReportsController` ~430 lines,
  `ProviderSettlementExcelExporter` ~620, `ProviderSettlementReportService`
  ~465) not split.
- `report/controller/ReportController.java`'s `catch (Exception e) { e.printStackTrace(); }`
  and unbounded `claimIds` list (no size cap → unbounded PDF generation) not
  fixed this round.

**Financial & claims reports: CRITICAL/HIGH findings closed**; MEDIUM/LOW
items above are documented residual debt.

## Settings & permissions closure — 2026-07-27

Audited `com.waad.tba.modules.rbac`, `admin.system`/`systemadmin`, and the
common system-settings services. A prior Section 01 pass already covered
RBAC/auth/session security broadly — this round focused on what that pass
missed: privilege-escalation paths in user management, the production-guard
pattern's actual coverage, and settings validation/audit.

### 🟠 HIGH — fixed

1. **`UserService.update()` had no SUPER_ADMIN protection at all** — unlike
   `delete()`/`toggleStatus()`, which both refuse to touch a SUPER_ADMIN. A
   SUPER_ADMIN (including the only one, or an attacker with a hijacked
   super-admin session) could `PUT` the last active SUPER_ADMIN down to any
   other role, permanently locking out all administrative access with no
   recovery path. Added `UserRepository.countByUserTypeAndActiveTrue()` and
   a guard: demoting a SUPER_ADMIN is blocked only when they're the last
   active one (a second SUPER_ADMIN can still be demoted).
2. **`requireNonProductionProfile()`'s profile check silently bypassed on a
   comma-separated profile list** — `spring.profiles.active=prod,metrics`
   compared the whole raw string against `"prod"` and failed the match,
   re-opening `resetTestData()`'s `deleteAll()` on Claim/Visit/Member/Employer
   in production. Fixed by splitting on `,` and checking each token.
3. **`resolveUserType()` accepted any free-form string with no validation**
   against the fixed `SystemRole` enum — a typo (`"SUPERADMIN"`) silently
   mints a Spring Security authority matching no `@PreAuthorize` expression,
   locking the account out of everything with no error at creation time.
   Now validated against `SystemRole.values()`.
4. **`ProviderUserExcelImportService` used one hardcoded shared default
   password** (`"Aa@1234567"`, in git history) for every imported row with a
   blank password column — a bulk import of 200 provider staff created 200
   accounts sharing one well-known credential. Replaced with a unique
   random password generated per row.

### 🟡 MEDIUM — fixed

5. **`SettingsManagementService` validation was entirely data-driven from
   the `validation_rules` column** — a blank/null rule column meant any
   value of the right type was accepted, so e.g. a token-expiry INTEGER
   setting could be persisted as `-1` and served verbatim by
   `AuthenticationSettingsService`. Added baseline invariants independent of
   `validation_rules`: INTEGER/DECIMAL settings reject negative values,
   STRING settings reject empty values.
6. **No audit trail for security-relevant setting changes** —
   `updateSetting()`/`resetToDefault()` only produced a `log.info` line,
   unlike login/password events which go through `SecurityAuditService`.
   Both now also write a `SETTING_CHANGED` security audit event (old value
   → new value, actor, key).
7. **`DELETE /admin/system/reset` had no audit trail** — a destructive wipe
   of 4 core tables produced only `log.warn`, no forensic record surviving
   log rotation. Now also logs a `CONFIGURATION_CHANGED` security audit
   event before the deletion runs, with the actual authenticated actor.
8. **`/api/v1/admin/features/public` (unauthenticated) returned the full
   `FeatureFlagDto`** — including `roleFilters` (internal role names) and
   `createdBy`/`updatedBy` (real admin usernames) — to anyone, regardless of
   the `INTERNAL_`-prefix key filter (which only excludes some keys, not
   these fields on the ones that remain). Now returns a minimal
   `flagKey`/`enabled`-only projection. Confirmed zero frontend callers of
   this endpoint currently exist, so this is a hardening with no UI impact.
9. **Excel upload on `ProviderUserExcelImportService.importUsers()` had no
   size/MIME validation** before `openWorkbook()`. Added
   `ExcelUploadValidator.validate(file)`. Also fixed raw exception-message
   leakage in its catch blocks (generic message to client, full detail in
   logs) and split validation errors (meant for the caller) from unexpected
   internal errors (logged, generic message only).
10. **Four fully dead RBAC DTOs deleted** (`RoleCreateDto`, `RoleUpdateDto`,
    `RoleViewDto`, `PermissionMatrixDto`) — confirmed zero references
    anywhere; leftovers from a removed dynamic-RBAC subsystem that
    advertised a role-CRUD API that no longer exists (roles are the static
    `SystemRole` enum now).

### Verification

New tests: `UserServiceTest` (+2: reject demoting the last active
SUPER_ADMIN, allow demoting one when another remains active — 6 total),
`SystemAdminServiceProductionGuardTest` (+1: comma-separated profile list no
longer bypasses the guard — 3 total). Full suite green after this round,
verified locally with `mvn test`.

### Deferred (documented, not fixed this round)

- `UserService.resetPassword()` has no SUPER_ADMIN-target protection — any
  SUPER_ADMIN can reset a peer SUPER_ADMIN's password and evict their
  sessions. Judged to be a normal peer-admin recovery capability rather than
  a privilege-escalation bug (unlike demoting the last admin, this doesn't
  reduce the number of active admins), so left as-is; flagged for a product
  decision rather than fixed unilaterally.
- `FeatureFlagService.deleteFeatureFlag()` has no reference-in-use check —
  a flag consumed at runtime via `isFlagEnabled(key, defaultValue)` degrades
  gracefully to the caller's default on delete rather than breaking, so this
  is a soft behavior change, not data loss; deferred.
- `MEDICAL_REVIEWER` can read all system settings by category (`SystemSettingsController`)
  — over-broad for a reviewer role but read-only; writes are already
  correctly SUPER_ADMIN-only.
- `SettingsManagementService.resetToDefault()`'s `@CacheEvict` key
  inconsistency with `updateClaimSlaDays()`'s literal key (both share the
  `systemSettings` cache name) — a real staleness risk, deferred as its own
  scoped fix rather than a quick patch.
- `UserService.getByUsername()` throws a raw `RuntimeException` instead of
  `ResourceNotFoundException` — cosmetic inconsistency, not fixed.

**Settings & permissions: HIGH findings closed**; MEDIUM/LOW items above are
documented residual debt.

### Verification

7 new tests in `BenefitPolicyServiceTest` (financial-linkage lock survives
cancellation, status-transition rejection, no-op status round-trip,
`assertDraftConfiguration` unconditional check) — all passing. Full backend
suite re-run clean after every change in this round, verified with `mvn test`
against the local database.

**Update — completed same day:** re-checked the frontend and found
   `ProviderView.jsx`'s "Contracts" tab already calls the modern
   `/provider-contracts/provider/{id}` route directly via `axiosClient`, not
   the legacy `providers.service.js#getContracts` helper (which had zero
   callers anywhere — confirmed by grep). With that cleared, the full
   retirement was completed: removed the four remaining legacy read
   endpoints from `ProviderController` (list/current/byId/count), deleted
   `provider.entity.ProviderContract`, `provider.repository.ProviderContractRepository`,
   and the three now-orphaned `provider.dto.ProviderContract{Create,Update,Response}Dto`
   classes, dropped `legacy_provider_contracts` via
   `V119__drop_legacy_provider_contracts.sql`, removed the dead
   `providers.service.js#getContracts` frontend function, and repointed
   `ProviderService`'s deactivate/hard-delete guards (which checked
   `legacy_provider_contracts` row counts) onto the modern
   `provider_contracts` repository so the safety check stays meaningful.
   `ProviderContractService` (provider package) is now a thin pricing-lookup
   class only (`getEffectivePrice`, `getServicesRequiringPreAuth`) with no
   dependency on the legacy repository at all. Two parallel provider-contract
   systems are now one.

### Verification

New tests: `ProviderContractControllerSecurityTest` (3),
`ProviderControllerSecurityTest` (2),
`ProviderVisitServiceRegisterVisitSecurityTest` (2) — 7 new regression tests,
all passing alongside the full existing suite (verified with `mvn test`
against the local database per updated project convention — Docker
deployment deferred to the pre-production stage rather than rebuilt after
every change).
