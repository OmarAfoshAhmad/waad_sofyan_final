# SECTION 01 — Security, System Settings and Infrastructure Audit

**Project:** WAAD TPA  
**Audit date:** 2026-07-23  
**Phase:** inventory and dependency mapping only  
**Change policy:** no production code, schema, endpoint or migration was removed in this phase.

## Executive decision

Section 01 is **not closed**. Useful hardening controls exist, but
authentication, authorization, audit and settings do not yet have one coherent
source of truth.

Confirmed findings:

1. Browser authentication is session-first, but a complete JWT fallback remains active.
2. `refresh-token` is not a persisted rotating refresh token; it reissues an
   access token after authenticating the current access token.
3. A frontend helper stores JWT/user data in `localStorage` and is imported by
   three live administration pages.
4. Authorization is static role-based while DTOs, tables and services still
   model dynamic permissions.
5. Role names are duplicated as string literals across controllers.
6. User administration exists in both `rbac` and `systemadmin`.
7. Three overlapping audit models exist: `user_audit_log`, `audit_logs`, and
   `medical_audit_logs`.
8. `system_settings` mixes unrelated operational and UI domains.
9. Flyway contains 94 migrations with create/fix/drop/recreate chains.
10. CI is absent and security test coverage is insufficient.

## Scope and evidence

Inspected:

- Backend `security`, `auth`, `rbac`, `systemadmin`, `audit`, file handling,
  settings and global errors.
- Frontend routes, auth context, Axios, guards, user/settings pages.
- All Flyway SQL, Spring profiles, Compose, Dockerfiles, nginx,
  `.env.example`, Maven and frontend package configuration.
- Existing tests and static references across Backend, Frontend, SQL and tests.

Live-schema inspection was completed against local PostgreSQL 18.4 using
`psql 18.4`. The database is `tba_waad_system`, contains 79 public base tables,
and Flyway records 94 successful migrations through V103. Docker CLI 29.6.2 is
installed, but Docker Desktop's Linux engine was not running; container-based
tests remain pending.

The task description says Next.js, but the repository uses React 19, Vite 7 and
React Router (`frontend/package.json`, `frontend/src/routes/index.jsx`).

## Authentication inventory

### Browser session path

| Element | State | Evidence | Decision |
|---|---|---|---|
| Session login | Used | `AuthController.java:76` | Keep as canonical browser login |
| Session fixation defense | Used | `AuthController.java:93-98` | Keep and test |
| Current session user | Used | `AuthController.java:120` | Keep |
| Server logout | Used | `AuthController.java:142-148` | Keep; add audit and all-session revocation |
| Session filter | Used | `SessionAuthenticationFilter.java` | Keep after cleanup |
| HttpOnly cookie | Configured | `application.yml` | Keep |
| Secure/SameSite Strict | Production | `application-prod.yml` | Keep and integration-test |
| Frontend bootstrap | Used | `AuthContext.jsx:159` | Keep |
| Credentialed Axios | Used | `utils/axios.js:24` | Keep |

### JWT compatibility path

| Element | State | Problem | Decision |
|---|---|---|---|
| `JwtTokenProvider` | Active | Claims include email/full name/scope beyond minimum | Remove from browser; isolate only for an approved API client |
| `JwtAuthenticationFilter` | Active | Runs with session filter | Remove or isolate in separate chain |
| `/auth/login` | Duplicate | Parallel to `/auth/session/login` | Candidate removal |
| `/auth/me` | Duplicate | Parallel to `/auth/session/me` | Candidate removal |
| `/auth/refresh-token` | Misnamed | No family, rotation, hash, revocation or reuse detection | Remove/replace |
| `tokenRefresh.service.js` | Legacy but used | Reads/writes `localStorage` | Replace with `refreshUser()` then remove |

Live imports of the legacy helper:

- `frontend/src/pages/rbac/users/UserEdit.jsx`
- `frontend/src/pages/providers/ProviderEdit.jsx`
- `frontend/src/pages/providers/ProviderCreate.jsx`

No refresh-token entity/repository was found. `password_reset_tokens` and
`email_verification_tokens` are unrelated. Recommended browser model:
server-side revocable session with opaque HttpOnly cookie. A rotating refresh
token should only be introduced for a documented non-browser client.

Live PostgreSQL confirms that no `roles`, `permissions`, `user_roles`,
`role_permissions`, `sessions`, `spring_session`,
`spring_session_attributes`, or `refresh_tokens` table exists.

## Authentication and password controls

| Item | State | Decision |
|---|---|---|
| Password encoder | Central config exists | Keep; verify work factor |
| Login attempts/lockout | Entities/settings exist | Keep; add boundary/integration tests |
| Password reset | Exists; V95 alignment needed | Rebuild final hashed-secret schema |
| OTP reset | Mixed into generic settings | Move to typed security config |
| Email verification | Exists | Remove duplicate blanket verification from baseline |
| Password policy | Backend plus multiple frontend validators | Backend canonical; one UI validator |
| Login audit listener | Exists | Route to unified security audit writer |

Live token-schema findings:

- `email_verification_tokens` has both `expiry_date` and `expires_at`; all 40
  rows currently contain equal values.
- Its unique token index is duplicated by a non-unique token index.
- `password_reset_tokens` has both expiry columns and token/OTP shapes; it
  currently contains zero rows.
- Token and OTP fields are stored directly, not as hashes.

## Authorization and RBAC

`SystemRole` declares static roles as the source of truth and says dynamic RBAC
was removed. Contradictory artifacts remain:

- `PermissionMatrixDto`
- role/user DTO permission lists
- `ModuleAccess.required_permissions`
- frontend role and permission stores/guards

Repeated controller literals include `SUPER_ADMIN`, `DATA_ENTRY`,
`MEDICAL_REVIEWER`, `EMPLOYER_ADMIN`, `PROVIDER_STAFF`, `ACCOUNTANT`,
`FINANCE_VIEWER`, and `RECEPTIONIST`. `RECEPTIONIST` is used by
`ProviderContractPricingExcelController` but must be checked against the final
role catalogue.

Live users currently contain 1 `SUPER_ADMIN`, 1 `MEDICAL_REVIEWER`, and 39
`PROVIDER_STAFF`; all 41 are active. The database role check recognizes seven
roles but not `RECEPTIONIST`, confirming that controller reference is
inconsistent with the persisted contract.

Required final direction:

- `users`, `roles`, `permissions`, `user_roles`, `role_permissions`
- centralized `SystemRoles` and `SecurityPermissions`
- no raw role lists in controllers
- organization/resource scope enforced separately from permission
- frontend guards are presentation only

## Duplicate user administration

| Element | State/problem | Decision |
|---|---|---|
| `rbac.controller.UserController` | Canonical account API | Retained; now owns administrator password reset |
| `systemadmin.controller.UserManagementController` | Duplicate account API | Removed after caller mapping |
| `rbac.service.UserService` | Canonical CRUD/bindings/password-reset service | Retained |
| `systemadmin.service.UserManagementService` | Duplicate creation/update/mapping | Removed |
| `UserSecurityService` (578 lines) | Password, reset, lockout, verification mixed | Split by responsibility |
| `AuthorizationService` (839 lines) | Role and domain scopes mixed | Split permission evaluation from scope policies |

The duplicate user-administration deletion was completed only after frontend
callers, routes and tests were mapped; the architecture test now prevents its
reintroduction.

The user table now has one canonical active-state column: `is_active`, surfaced
to the API and frontend as `active`. Legacy Spring-Security-style account flags,
the duplicate `enabled` state, unused identity-verification placeholders,
`company_id`, duplicate last-login storage and unused audit-name columns were
removed from the live schema by `V106__clean_legacy_user_columns_and_indexes.sql`.
The redundant username/email/enabled indexes were also removed; username and
email remain protected by their unique constraints.

## Audit logging

| Model/table | Finding | Decision |
|---|---|---|
| `user_audit_log` | Security-specific but incomplete | Merge into unified security/admin event |
| `audit_logs` | Generic system-admin audit | Merge/remove |
| `medical_audit_logs` | Rich domain audit | Design input, not automatically final |
| claim/preauth/payment audit | Domain financial history | Keep separately where immutable history is required |

Final security event requires actor, action, target, timestamp, IP, user agent,
result, safe reason, correlation ID and safe before/after. Never record
passwords, raw JWT/session/reset tokens or document contents.

Live audit evidence:

| Table | Rows | Missing IP | Missing user agent | Missing actor |
|---|---:|---:|---:|---:|
| `user_audit_log` | 484 | 484 | 484 | 0 |
| `audit_logs` | 33 | 33 | 33 | 33 |
| `medical_audit_logs` | 72 | n/a | n/a | 0 |
| `user_login_attempts` | 486 | 486 | 486 | 43 |

All 72 medical audit rows have correlation IDs and before/after state.

## System settings cleanup

The generic `system_settings` table remains as the compatibility store for the
current admin UI, but the unsafe parts were removed from the write path. Admin
responses now use `SystemSettingDto` instead of exposing the JPA entity, and
updates no longer create ad-hoc rows through an upsert. A setting must already
exist, be active, be editable and pass its type/rule validation before it can be
changed.

Default records are now registered on startup for UI appearance, beneficiary
numbering, eligibility and AI classifier settings, so the frontend no longer
depends on implicit row creation. Architecture and unit tests protect this
contract.

Remaining work before S01-08 is fully closed: move high-sensitivity values such
as classifier provider credentials out of generic `system_settings` into a
dedicated encrypted integration-secret store, then expose a write-only
credential contract like SMTP.

## File access

Confirmed improvements:

- generic `FileController` is `SUPER_ADMIN` only
- claim download uses `claimId + attachmentId`
- `ClaimAttachmentService` calls `canAccessClaim`
- two claim attachment security unit tests exist

Remaining:

- central policy is absent across claims, preauth, visits, provider documents and reports
- cross-provider/cross-employer IDOR matrix is absent
- extension-derived MIME is not magic-byte validation
- antivirus/quarantine and sensitive-download audit are unproven
- business APIs must not expose direct storage-key access

Decision: one `FileAccessPolicy` plus resource-specific resolvers and IDOR tests.

## System settings

`SystemSettingsService` is 510 lines and mixes claim/preauth SLA, backdating, UI,
beneficiary numbering, eligibility, password reset and AI integrations.

Final domains:

- organization appearance
- security (typed environment config; normally not UI-editable)
- insurance operations
- claim operations
- files
- email
- integrations

Every setting requires key, type, scope, default, validation, sensitivity,
editable permission, audit and restart policy. Secrets must not be ordinary
settings returned to the browser.

Live settings evidence:

- 27 settings, all active and editable.
- Categories: UI 14, SECURITY 4, MEMBERS 3, ELIGIBILITY 3, CLAIMS 2,
  PRE_APPROVALS 1.
- `module_access` exists but contains zero rows; `feature_flags` has six.
- `email_settings` has one row.

The original email-settings design allowed SMTP/IMAP passwords to be persisted
as plaintext. This finding is now closed: inbound IMAP and email pre-
authorization intake were retired completely, while the remaining outbound
SMTP password is encrypted with AES-256-GCM under an external
`APP_SECRET_ENCRYPTION_KEY`. Password fields are write-only at the JSON
boundary, saved legacy SMTP plaintext is encrypted at startup, and connection
errors no longer echo provider messages or credentials.

## Frontend

Active session path is coherent: `AuthContext`, `withCredentials`, central 401
event and distinct 403 event.

No live imports were found for these template families:

- `pages/auth/auth0/**`
- `pages/auth/aws/**`
- `pages/auth/firebase/**`
- `pages/auth/supabase/**`
- `pages/auth/jwt/TestLogin.jsx`
- `sections/auth/auth-forms/AuthLogin.jsx`

They are deletion candidates after dependency/build proof.

Oversized files:

| File | Lines | Decision |
|---|---:|---|
| `SystemSettingsPage.jsx` | 1366 | Split by domain |
| `UserEdit.jsx` | 1024 | Split container/form/roles/sessions |
| `UsersList.jsx` | 476 | Split table/query/actions |
| `UserCreate.jsx` | 468 | Share account form/schema |
| `AuthContext.jsx` | 276 | Extract inactivity/cross-tab hooks |

Configuration/auth state is also spread across duplicate `useAuth` files, an old
auth reducer, RBAC store, `CompanySettingsContext`, `SystemConfigContext` and
`ConfigContext`; imports must be mapped before consolidation.

## Errors

`GlobalExceptionHandler` is 675 lines. Split security, validation, conflict and
internal advice while retaining one error contract. Positive controls include
disabled production stack/message leakage and MDC support. Missing proof:
correlation ID in every response, 429 contract, denial audit, and leakage tests.

## Infrastructure

Positive:

- DB port is not exposed in production Compose
- production `ddl-auto=validate`
- production Flyway validation
- no DB/JWT runtime secret fallback
- backend is intended behind nginx
- Actuator limited to health/info/prometheus
- production cookie is Secure/HttpOnly/SameSite Strict

Findings:

| Item | Finding | Decision |
|---|---|---|
| CI/CD | `.github/workflows` absent | Add enforced pipeline |
| Startup validator | Checks DB/JWT/email only | Add profile, ddl, CORS, cookie, default-secret checks |
| CORS | Origin patterns + credentials | Reject wildcards at startup |
| CSRF | Disabled based on SameSite | Prove assumptions with browser integration tests |
| Uploads | Repository bind mount | Managed volume/object store in production |
| Container user | Needs hardening proof | Run backend/nginx non-root |
| Flyway dev | validation disabled | Enable outside migration-development profile |
| Common Flyway | baseline-on-migrate true | Remove after clean baseline |
| Frontend | no typecheck/test script | Add checker and test runner |
| Docker engine | CLI installed, Linux engine not running | Start before Testcontainers/Compose proof |

## Flyway inventory

- Migration count: **94**
- Version range: V1–V103 with gaps
- No duplicate version number detected
- Repair chains include policy fixes, provider fixes/cleanup, payment
  create/fix/recreate, duplicate V60/V83 email verification, legacy contract
  creation, catalog “restore” V93 and reset-token alignment V95.

The chain should not be the final baseline, but cannot be deleted before the
final Section 01 schema and regression suite are agreed.

## Live schema quality findings

### Users

- `enabled` and `is_active` duplicate account state; all 41 rows currently agree.
- `last_login` and `last_login_at` overlap and differ on three rows.
- `company_id` is unused on every row.
- Five `can_view_*` booleans coexist with roles, frontend guards and empty
  `module_access`.
- Username/email uniqueness exists; no normalized-email duplicate group exists.

### Redundant indexes

Confirmed pairs include username/email unique indexes plus ordinary lookup
indexes, setting-key unique plus ordinary index, token unique plus ordinary
index, two failed-login window indexes, and parallel expiry indexes for
duplicated expiry columns.

### Missing constraints

- `audit_logs.user_id`, `medical_audit_logs.user_id`, and
  `user_login_attempts.user_id` have no foreign key.
- `email_settings` has no rule enforcing a single active configuration.
- settings category/type/value validation is mostly not enforced in DB.

## Tests

Confirmed: two claim attachment security unit tests, two `UserServiceTest`
tests, and a Testcontainers PostgreSQL base. The base now owns one PostgreSQL
16 container for the full test JVM, preventing Spring's cached context from
retaining the port of a container stopped between test classes.

Baseline executed on 2026-07-23:

- Full backend regression after resource-scope hardening: **163 tests, 0
  failures, 0 errors, 3 skipped**.
- Session authentication integration suite: **8 tests, 0 failures, 0 errors**.
  It proves session-id rotation on login, absence of JWT in the browser login
  response, authenticated `/session/me`, logout revocation, immediate rejection
  of a deactivated user, rejection of invalid credentials without creating an
  authenticated session, ownership-safe session revocation, two-device
  revoke-all, revocation of every session after a password change, and that a
  Bearer header cannot replace an authenticated browser session.
- Flyway: all **95 migrations** applied successfully to an empty PostgreSQL 16
  database before the integration tests.
- Claim concurrency: four amount/times/days/shared-limit cases passed.
- Claim lifecycle: create, explicit line decisions, after-commit asynchronous
  approval, provider credit, settlement, approved-claim reversal and
  idempotency passed.
- Frontend ESLint: exit code 0 with no errors, but a large warning backlog
  (formatting, unused imports and hook dependencies).
- Frontend production build: passed; Vite transformed 18,021 modules.
- Performance warning: `vendor` ≈ 1.88 MB and `excel` ≈ 1.13 MB minified,
  requiring later route/vendor splitting.

Not found: refresh rotation/reuse (only relevant if the legacy JWT contract is
retained), password/lockout integration, authorization matrix,
security audit, startup validation, Section 01 clean-Flyway test, and frontend
auth/permission/session tests. Some existing tests still use old `ADMIN` and
`REVIEWER` roles.

The browser callers have been migrated away from JWT/local-storage
authentication: provider/user administration now refreshes `/session/me`,
medical-category template download uses the session cookie, and reviewer SSE
uses credentials without a token query parameter. The legacy backend JWT
filter/endpoints remain isolated as an unresolved external-client compatibility
decision; they are no longer required by the current browser application.

### CSRF correction — 2026-07-23

The earlier implementation and its comments contradicted each other:
`CookieConfig` claimed `SameSite=Strict` while setting `Lax`, and Spring CSRF
was disabled. This is now corrected:

- authenticated mutating requests require `X-XSRF-TOKEN`;
- Spring issues the separate readable `XSRF-TOKEN` cookie eagerly on safe
  bootstrap requests;
- Axios sends the token header with credentialed requests;
- `JSESSIONID` remains HttpOnly and is now actually `SameSite=Strict`;
- only unauthenticated login/register/reset/verification entry points are
  excluded from CSRF token validation.

Integration coverage proves that logout without a CSRF token returns 403
without destroying the valid session, while logout with a valid token succeeds.
The complete backend regression and the frontend production build both pass.

### Persistent session foundation — 2026-07-23

Tomcat-local sessions were replaced with Spring Session JDBC. Migration V104
creates `spring_session` and `spring_session_attributes` with unique session
identity, expiry/principal indexes, and cascading attribute cleanup. Spring
schema initialization is disabled so Flyway remains the sole DDL owner.

The HTTP integration suite now uses real `JSESSIONID` cookies rather than
`MockHttpSession`, and proves session fixation rotation, persistence across
requests, logout deletion, deactivation rejection and CSRF behavior against
PostgreSQL. This makes sessions cluster-safe and establishes the storage needed
for revoke-all and active-session administration.

The authenticated session API now supports:

- `GET /api/v1/auth/session/sessions` — list only the current user's sessions;
- `DELETE /api/v1/auth/session/sessions/{sessionId}` — revoke an owned session;
- `POST /api/v1/auth/session/logout-all` — revoke all sessions and invalidate
  the current request session.

Session ownership is enforced through Spring Session's principal index; a user
cannot probe or revoke another user's session id. The two-device integration
scenario creates two PostgreSQL-backed sessions, identifies the presented
session, revokes all, and proves both cookies are subsequently unauthorized.

Every successful password mutation path now revokes the affected user's
sessions: authenticated self-change, administrative password change, token
reset, and OTP reset. The current request is explicitly invalidated after a
self-change. Authentication-dependent controller methods use
`Authentication.getName()` so they work with the persisted string principal.

Surefire completed normally with Maven exit code 0: **163 tests, 0 failures,
0 errors, 3 skipped**. This also closes the earlier post-suite JVM shutdown
hygiene observation.

### Mobile authentication decision — 2026-07-23

JWT is intentionally retained because a native mobile application is planned
after the web system stabilizes. It is not a browser fallback:

- a restored server-side browser session is authoritative and cannot be
  replaced by an `Authorization: Bearer` header;
- JWT is accepted only from the Authorization header; token query parameters
  were removed, including the obsolete SSE compatibility path;
- newly issued access tokens carry and validate issuer `tba-waad`, audience
  `tba-waad-mobile`, and token type `mobile_access`;
- access-token lifetime remains short and configurable.

Before the mobile client is released, its authentication contract must be
versioned separately and completed with opaque rotating refresh tokens stored
hashed server-side, reuse detection, per-device inventory/revocation, secure OS
keychain storage, and explicit logout/password-change revocation. The current
JWT access token alone must not be promoted to a long-lived mobile login.

### Authorization consistency hardening — 2026-07-23

`SystemRole` remains the source of truth for the seven supported roles. The
undefined `RECEPTIONIST` reference was removed from financial price-list
export; that operation remains restricted to `SUPER_ADMIN` and `ACCOUNTANT`.
`AuthorizationAnnotationConsistencyTest` scans every Java source expression
using `hasRole`/`hasAnyRole` and fails the build if an unregistered role is
introduced.

The legacy administrative password-reset endpoint remains compatible with the
current frontend payload but no longer accepts an unvalidated map. It now uses
the shared password policy and revokes every persisted session belonging to the
affected account. Self-service profile/password endpoints also declare
`isAuthenticated()` explicitly instead of relying only on the global fallback.

This closes role-name drift and the identified password-reset bypass. A full
business allow/deny matrix is still required incrementally for employer,
provider, claims and member data scopes; role-name consistency alone does not
prove tenant/resource ownership.

### Beneficiary resource-scope hardening — 2026-07-23

Beneficiary search no longer trusts the caller-supplied `employerId` as an
authorization boundary. The service resolves the effective employer from the
authenticated user before executing any search strategy:

- an employer administrator is always constrained to their own employer, even
  when a different employer id is supplied;
- provider staff must supply an employer scope and that employer must exist in
  the provider's allowed-employer list;
- unscoped and unrelated-employer provider searches are denied;
- privileged administrative roles retain their intended cross-employer search.

Four explicit owner/non-owner tests cover these rules in addition to the
existing search tests. The PostgreSQL-backed backend regression completed
without failures.

This is a concrete closure of the beneficiary-search path, not proof that every
resource is isolated. Attachments/files and the remaining member, employer,
provider and claim endpoints still require the same service-layer ownership
matrix and IDOR tests.

### Visit attachment IDOR hardening — 2026-07-23

The visit attachment API previously accepted both `visitId` and `attachmentId`
in its route while the service ignored `visitId` for download and deletion.
Consequently, a role admitted by the endpoint could address an attachment by
its global id without proving access to the parent visit.

All visit attachment operations now enforce the authenticated user's
service-layer visit scope before querying attachment metadata or touching file
storage. Download and deletion require the attachment to belong to the
`visitId` in the request path. Listing, counting, bulk deletion and upload use
the same visit authorization boundary.

Three IDOR tests prove that an unauthorized caller cannot reach the attachment
repository or storage, a cross-visit attachment id is rejected, and denied
deletion cannot mutate either storage or metadata. Together with the existing
claim attachment security tests, the focused suite passed **5/5**. The complete
backend regression passed without failures.

### Pre-authorization attachment IDOR hardening — 2026-07-23

Pre-authorization attachment download and deletion previously accepted the
parent `id` in the route but resolved the file using only the global attachment
id. Listing, counting and upload also lacked a service-layer resource check.

Every operation now loads and authorizes the parent pre-authorization first.
Internal staff retain their intended system scope, provider staff are limited
to their own `providerId`, and employer administrators must pass the existing
member/employer ownership check. Download and deletion additionally require
the attachment's stored `preAuthorizationId` to equal the parent id in the
request path.

Four tests cover denial before metadata/storage access for an unrelated
provider, cross-parent attachment rejection, mutation-free denied deletion,
and successful download by the owning provider. The combined claim, visit and
pre-authorization attachment security suite passed **9/9**. Full backend
regression passed without failures.

The three primary clinical attachment paths are now parent-scoped. Remaining
file work is to inventory direct file-key/photo/document endpoints and prove
that none bypass the resource services.

### Member photo resource-scope hardening — 2026-07-23

Member photo endpoints were role-gated, but the underlying service previously
read or changed any `memberId` without enforcing employer ownership. An
employer administrator could therefore address a member from another employer,
and provider access was not tied to the provider's allowed employers.

Photo reads and metadata changes now authorize the member in the service layer.
Internal staff retain their intended scope, employer administrators use the
canonical member/employer ownership check, and provider staff can access only
members whose employer is in that provider's allowed-employer list. Upload
performs the check before writing bytes, avoiding unauthorized orphan files.

Three tests cover cross-employer denial, mutation-free denial for an unrelated
provider, and successful access for a provider linked to the member's employer.
The member search/photo focused suite passed **11/11**. Full backend regression
passed without failures.

### Provider administrative document hardening — 2026-07-23

The current provider-administration controller was already restricted to
`SUPER_ADMIN`, so no ordinary user exploit was exposed through that controller.
However, `ProviderAdminDocumentService` trusted its `providerId` argument, which
made future reuse from another endpoint unsafe and left authorization dependent
on controller placement.

List, create and delete now enforce provider resource access inside the service
before querying metadata or touching storage. Cross-provider document ids are
still validated before deletion. The response DTO no longer exposes the
server's absolute `filePath`; storage location remains internal.

Three tests prove that unrelated-provider listing is denied before repository
access, denied creation cannot upload or persist an orphan file, and a document
belonging to another provider cannot mutate storage or metadata. Full backend
regression passed **163 tests, 0 failures, 0 errors, 3 skipped**.

### Raw file-key endpoint removal — 2026-07-23

The unused generic `/api/v1/files` controller and its unused frontend client
were removed. Local storage no longer emits raw file-key URLs and rejects
presigned-URL requests until a storage implementation can produce genuinely
opaque, expiring links. Downloads remain available only through
resource-specific endpoints that authorize the owning claim, visit,
pre-authorization, member or provider resource.

The legacy email pre-authorization attachment link was also replaced. The API
now accepts the email-request id plus the attachment database id, verifies that
the attachment belongs to that request, and only then reads storage. Storage
keys and filesystem paths are no longer serialized in the email-attachment
DTO. Download access is restricted to `SUPER_ADMIN` and `MEDICAL_REVIEWER`;
provider-wide inbox isolation remains a separate authorization task before
provider staff can safely download these attachments.

Four architecture tests prevent restoration of the raw controller, raw local
URLs, or storage coordinates in the email-attachment DTO. The frontend
production build and full backend regression passed.

### Email pre-authorization provider isolation — 2026-07-23

Provider staff inbox queries are now forced to the authenticated user's
`providerId`; a provider id from the request is neither accepted nor trusted.
Direct request reads and attachment metadata access validate the owning
provider again inside `EmailPreAuthService`, before mapping DTOs or reading
storage. Provider users without a provider binding fail closed.

An unused legacy controller at `/api/preauthorization/email-requests` was
removed. It bypassed the service layer, serialized the persistence entity
directly, returned unscoped rows, and exposed a direct delete operation. A
permanent architecture test now prevents that controller from being restored.

Four focused security tests prove provider-scoped list queries, cross-provider
detail denial, fail-closed behavior for an unbound provider, and denial before
attachment metadata/storage access. Full backend regression passed **159
tests, 0 failures, 0 errors, 3 skipped**.

### Canonical user administration — 2026-07-23

`rbac.controller.UserController` and `rbac.service.UserService` are now the
single administrative user API and use-case service. The only live caller of
the duplicate `/admin/user-management` API was the administrator password
reset action; it was moved to
`PUT /api/v1/admin/users/{id}/reset-password`, and the frontend caller now uses
that canonical route.

The duplicate `UserManagementController`, `UserManagementService`, and its
three parallel system-admin user DTOs were removed. Their unused CRUD and
status implementations could otherwise diverge from role-binding rules,
SUPER_ADMIN protections and audit behavior in the canonical service.

The canonical reset validates the shared password policy at the API boundary,
rejects a password equal to the username, uses the configured password
encoder, updates `passwordChangedAt`, records a security audit event and
revokes every active session for the affected account. Two service tests cover
successful secure reset and mutation-free policy rejection. Two architecture
tests prevent restoration of the legacy implementation and ensure that the
canonical controller owns the reset route. Full backend regression passed
**163 tests, 0 failures, 0 errors, 3 skipped** and the frontend production
build passed.

### Email pre-authorization retirement and SMTP secret protection — 2026-07-23

The provider portal is now the sole pre-authorization intake channel. The
email inbox controller, scheduler, parser/service, notification service,
entities, repositories, mapper, DTOs, frontend inbox, route, menu item and API
client were removed. The former portal workaround
`emailRequestId=9999` was also removed; portal requests are valid first-class
requests without a fabricated email or visit identifier.

Migration `V105__retire_email_preauthorization_intake.sql` drops the two email
intake tables, the `pre_authorizations.email_request_id` column and all IMAP
listener fields from `email_settings`. An architecture test prevents the
retired components from returning.

The remaining outbound SMTP credential is encrypted before persistence using
random-nonce AES-256-GCM. A startup migration encrypts existing plaintext,
tampered ciphertext and plaintext reads fail closed, DTO passwords are
write-only, and Docker/environment templates require an external stable key.
All **96 Flyway migrations** completed against clean PostgreSQL. Full backend
regression passed **165 tests, 0 failures, 0 errors, 3 skipped** and the
frontend production build passed.

### Re-audit pass — 2026-07-24

A fresh inventory pass was run against the current repository state (parallel
review of backend security config, Flyway migrations, Docker/config/secrets,
and frontend structure) to confirm the previous findings are still accurate
and to surface drift introduced since the last update.

**Corrections to prior findings:**

- The claim-attachment IDOR concern is confirmed **closed**, not open. Direct
  inspection of `ClaimAttachmentService.downloadAttachment` confirms it calls
  `authorizationService.canAccessClaim(currentUser, claimId)` before releasing
  file bytes, matching the "Visit attachment IDOR hardening" and "Pre-
  authorization attachment IDOR hardening" entries already recorded above. An
  initial automated pass flagged this as open because it only inspected the
  controller's `@PreAuthorize` annotation and missed the service-layer check;
  manual verification overrides that false positive.

**New findings (not previously recorded):**

1. **Migration count drift.** The migration directory now contains **100
   files, through V108** (`V104__create_persistent_http_sessions.sql`,
   `V105__retire_email_preauthorization_intake.sql`,
   `V106__clean_legacy_user_columns_and_indexes.sql`,
   `V107__create_security_audit_events_table.sql`,
   `V108__fix_benefit_policy_rules_schema.sql` were added after the last
   Flyway inventory count of 94/97). The Flyway inventory section above needs
   re-counting before the clean-baseline work in Ordered-implementation step
   12 begins.

2. **`GET /api/v1/admin/system-settings/ui-config` contradicts its own
   documentation.** `SystemSettingsController` carries class-level
   `@PreAuthorize("isAuthenticated()")` (no method-level override), so the
   endpoint requires an active session — but its Javadoc and `@Operation`
   summary both say "public — no authentication required," and it is not
   listed in `SecurityConfig`'s `permitAll` matcher list either. Verified live:
   an unauthenticated `curl` to this path returns 401. Since the frontend is
   expected to call this on initial app load (before login, for branding/UI
   config), this is either a real bug (endpoint should be `permitAll`) or the
   Javadoc is simply wrong and the frontend already tolerates the 401 during
   bootstrap. Needs an explicit decision, not a silent fix.

3. **Dual audit-logging duplication reconfirmed, with a concrete example.**
   `LoginSecurityService` writes login success/failure/lockout events to
   `user_audit_log` (via `auditLogRepository`), while `SecurityAuditService`
   (backing the new `security_audit_events` table, V107) is called separately
   from `AuthService`/`UserService`. The same login event can therefore be
   recorded twice, in two different tables, by two different code paths. This
   is the concrete evidence behind risk S01-07, which remains open.

4. **`SecurityAuditService` is called from only two classes** (`AuthService`,
   `UserService`). Role/permission changes, session-revocation endpoints
   (`DELETE /session/sessions/{id}`, `POST /session/logout-all`), sensitive
   system-setting changes, and the dedicated `logFileAccessDenied` method have
   no confirmed caller in the current codebase — meaning several categories
   the audit checklist requires (role change, permission change, session
   revocation, sensitive setting change, access denial) are not yet flowing
   into the unified security-event table at all.

5. **JWT compatibility path still has no revocation.** Confirmed again: no
   blacklist/deny-list exists for issued JWTs. A logout, password change, or
   account deactivation invalidates the browser session but does **not**
   invalidate a previously issued JWT for the mobile-compatibility path — it
   remains valid until natural expiry. This is accepted as a known gap per the
   "Mobile authentication decision" entry above (JWT is pre-positioned for a
   future mobile client, not yet hardened), but it should stay listed as open
   until that mobile contract is actually built.

6. **Environment drift found live in this session, now corrected.** During
   interactive local debugging, `.env` had been temporarily changed to
   `SPRING_PROFILES_ACTIVE=dev` and `frontend/nginx.conf` had its
   `limit_req_zone`/`limit_req` directives commented out entirely (no rate
   limiting on `/api/` or `/api/v1/auth/`). Both were identified and reverted
   in this session: nginx rate limiting (20r/s general, 5r/m auth, burst
   caps unchanged) was restored and the frontend image rebuilt; `.env` was
   restored to `SPRING_PROFILES_ACTIVE=prod`. Verified live after the revert:
   `Origin: http://localhost:3000` now correctly receives "Invalid CORS
   request" under the prod profile, and rate-limited endpoints respond
   normally under burst limits. This confirms the dev-profile/no-rate-limit
   state was never committed — it only existed transiently in the running
   containers/.env during this debugging session — but it is recorded here in
   case a similar local-debug detour recurs before Section 01 closes.

7. **Additional oversized frontend files beyond the previously recorded list.**
   A fresh line-count pass surfaced business-logic pages far larger than the
   ~300-line guideline and not previously catalogued:
   `pages/provider/ProviderClaimsSubmission.jsx` (2615 lines),
   `pages/claims/batches/ClaimBatchEntry.jsx` (2485),
   `pages/benefit-policies/BenefitPolicyRulesTab.jsx` (2174),
   `pages/provider-contracts/ProviderContractView.jsx` (1820),
   `pages/claims/ClaimViewMedicalReview.jsx` (1377),
   `pages/provider/ProviderPreApprovalSubmission.jsx` (1337),
   `pages/claims/batches/ClaimBatchDetail.jsx` (1294),
   `pages/provider/ProviderEligibilityCheck.jsx` (1206),
   `pages/providers/ProviderEdit.jsx` (1160). These are in addition to
   `SystemSettingsPage.jsx` (1366) and `UserEdit.jsx` (1024) already listed.
   Several of these (claims submission, batch entry, policy rules) are core
   business pages, not just admin/settings screens — the file-size problem is
   broader than the security/admin surface this section originally scoped,
   and will recur in later sections unless a shared decomposition pattern is
   established now.

8. **`token-storage.js` legacy status reconfirmed.** The file still contains a
   "backward compatibility" path that migrates any leftover `localStorage`
   token into `sessionStorage`, but `utils/axios.js` confirms the app is fully
   cookie/session based (`withCredentials: true`, no `Authorization` header
   injection in the request interceptor). This file is dead weight for the
   current auth model and a removal candidate once there is confidence no
   returning user still has a stale `localStorage` token from before the
   session-based cutover.

### S01-07 closure — audit consolidation — 2026-07-24

`user_audit_log` writes were fully redirected to the unified
`security_audit_events` table (V107). All four services that previously wrote
directly to `UserAuditLog` now delegate to `SecurityAuditService`:

- **`LoginSecurityService`** — `LOGIN_SUCCESS`, `LOGIN_FAILED`, and
  `ACCOUNT_LOCKED` now call `SecurityAuditService`'s dedicated convenience
  methods directly. The private duplicate `auditLog(...)` helper was deleted.
- **`UserSecurityService.auditLog(...)`** — the shared "backward
  compatibility" wrapper used by `UserService` for
  `ACCOUNT_CREATED`/`ACCOUNT_UPDATED`/`ACCOUNT_DELETED`/`ACCOUNT_ACTIVATED`/
  `ACCOUNT_DEACTIVATED`/`PASSWORD_RESET`/`ROLE_ASSIGNED` now resolves the
  acting administrator from `SecurityContextHolder` (falling back to
  `"system"` for unauthenticated contexts such as scheduled jobs) and calls
  `SecurityAuditService.logUserAdminEvent(...)`. A private
  `mapLegacyAction(String)` translates the legacy `UserAuditLog.ACTION_*`
  string constants to the typed `AuditActionType` enum; it is intentionally
  narrow and not meant to grow — new call sites should call
  `SecurityAuditService` directly.
- **`PasswordManagementService`** — password-change (success and
  incorrect-current-password denial), password-reset-requested, and
  password-reset-via-token all write to `security_audit_events` with the
  correct `AuditResult` (`SUCCESS` vs `DENIED`).
- **`EmailVerificationService`** — `EMAIL_VERIFIED` now writes to the unified
  table.

Three `AuditActionType` enum values were added to cover cases the original
V107 enum lacked: `ACCOUNT_DELETED`, `ACCOUNT_UNLOCKED`, `EMAIL_VERIFIED`
(safe — the column is `VARCHAR(50)`, not a Postgres enum type, so no
migration was required).

**A real, previously-undiscovered production defect was found and fixed
during this work.** `SecurityAuditEvent.beforeState`/`afterState` were mapped
as plain `String` columns against `jsonb` database columns without
`@JdbcTypeCode(SqlTypes.JSON)` (the correct pattern already existed elsewhere
in the codebase, in `modules/audit/entity/AuditLog.java`, but was not applied
here). Every call to `SecurityAuditService.logSecurityEvent(...)` failed at
the database with `column "after_state" is of type jsonb but expression is of
type character varying` — including with `null` before/after state — and was
**silently swallowed** by `logSecurityEvent`'s broad `catch (Exception e)`
block, which only logs and returns `null`. This meant `security_audit_events`
had been silently accumulating zero rows in the actual running deployment
since V107 was introduced, despite `AuthService`/`UserService` appearing to
call it correctly. The entity mapping was fixed
(`@JdbcTypeCode(SqlTypes.JSON)` added to both fields), and a second latent
mismatch was corrected in the same pass: `actorId` was mapped `nullable =
false` in the entity while the actual V107 column is nullable (required for
`LOGIN_FAILED` events against a username with no matching user).

Verified live against the running Docker deployment, not just the test
suite (Testcontainers masked the defect because
`SecurityAuditServiceTest` exercises the service against mocks, and the
integration suite's Testcontainers Postgres instance behaved differently
under test than the actual docker-compose Postgres 16 instance under this
specific jsonb binding path):

- `LOGIN_SUCCESS` and `LOGIN_FAILED` (including the `actor_id IS NULL` case
  for an unknown username) persist correctly.
- `ACCOUNT_CREATED` and `ACCOUNT_DELETED` persist with `actor_username`
  correctly resolved to the acting administrator (`superadmin`) and
  `target_identifier` correctly resolved to the affected account
  (`audittest1`), not conflated with each other.
- Full backend regression: **178 tests, 0 failures, 0 errors, 3 skipped**,
  both before and after the entity fix (the defect is Postgres-jsonb-specific
  and did not surface against the test datasource, which is itself a gap —
  see the new finding below).

**New finding from this work:** silent audit-write failure is itself a risk.
`SecurityAuditService.logSecurityEvent` catches every exception and only
logs+returns `null`, so a future regression of this kind (or any other
DB-level failure on this table) would again silently stop recording security
events with no alert, no metric, and no test catching it end-to-end against
real PostgreSQL. This should be closed before Section 01 closes: either the
test suite needs at least one test that runs a real `logSecurityEvent` call
against a genuine `jsonb`-backed Postgres instance and asserts a row was
written (not mocked), or the silent-catch behavior itself should be revisited
so a persistent audit-write failure is observable.

`user_audit_log` now has **zero live writers** anywhere in the codebase — only
`UserAuditLogRepository` remains, unused. Its 484 historical rows are
preserved for now; dropping the table/entity is a separate, later decision
(distinct from redirecting writes) once there is confidence nothing reads it
for compliance/history purposes. `AuditLogService`/`audit_logs` (the
near-empty systemadmin table, 33 rows, used only by
`MedicalReviewerProviderAssignmentService` and `VisitService` for
business-action logging, not security events) and `medical_audit_logs` (the
clinical audit trail) were deliberately left untouched — they are business/
clinical domain logs, not security events, and merging them would conflate
different retention and access-control requirements.

### `user_audit_log` table removal — 2026-07-24

With zero live writers confirmed across the codebase, and the database
explicitly confirmed experimental (no compliance obligation to retain the 484
historical rows), the table itself was removed rather than left as an
orphaned artifact:

- `UserSecurityService.auditLog(...)` was changed from a legacy
  `String action` parameter to the typed `SecurityAuditEvent.AuditActionType`
  directly, eliminating the `mapLegacyAction(String)` translation shim added
  earlier the same day — a compatibility layer that was no longer needed once
  every caller could be updated directly, per the rule against keeping
  unnecessary compatibility layers.
- All five call sites in `UserService` (`ACCOUNT_CREATED`, `ACCOUNT_UPDATED`,
  `ACCOUNT_DELETED`, `ACCOUNT_ACTIVATED`/`ACCOUNT_DEACTIVATED`,
  `PASSWORD_RESET`) now pass the enum constant directly.
- A second, previously unnoticed duplicate write was found and removed in the
  same pass: `UserService.resetPassword(...)` called both
  `auditService.logPasswordChanged(...)` (writing `PASSWORD_CHANGED`) **and**
  `securityService.auditLog(..., ACTION_PASSWORD_RESET, ...)` for the same
  administrative action, logging it twice under two different action types.
  Kept the semantically correct one (`PASSWORD_RESET`, "Password reset by
  administrator"); removed the redundant `auditService` field and import,
  which had become unused.
- `UserAuditLog` (entity) and `UserAuditLogRepository` were deleted outright
  — no `@Deprecated` annotation, no stub retained.
- `UserServiceTest` was updated: the now-unused `SecurityAuditService
  auditService` mock was removed, and the assertion referencing the deleted
  `UserAuditLog.ACTION_PASSWORD_RESET` constant and the old 6-argument
  `auditLog(...)` signature was updated to the new 5-argument, typed-enum
  call.
- Migration `V109__drop_legacy_user_audit_log.sql` drops the table. It does
  **not** touch `user_login_attempts` (still actively written by
  `LoginSecurityService` for lockout tracking, unrelated table created in the
  same original V7 migration) or `audit_logs`/`medical_audit_logs` (separate
  business/clinical domains, out of scope).

Verified: full backend regression (**178 tests, 0 failures, 0 errors, 3
skipped**) including Flyway building all 109 migrations from an empty
PostgreSQL 16 database through V109; live Docker deployment confirms
`\dt user_audit_log` returns no relation and `superadmin` login still
succeeds end-to-end with the event correctly landing in
`security_audit_events`.

These items do not change the closure percentage materially (they refine
and extend existing open risks rather than closing or newly opening major
ones), except item 6, which was corrected within this same session. They are
folded into the risk register and ordered-implementation plan below.

### Facility price-preparation & AI classifier feature removal — 2026-07-24

Two related experimental frontend windows were removed in full, by explicit
product decision (not a security finding): "تجهيز قوائم أسعار المرافق
الصحية" (Facility Price List Preparation, labeled "تجريبي"/experimental in
the menu) and "إعداد API للذكاء الاصطناعي" (AI Classifier API Settings,
whose own subtitle stated it existed only to feed the price-preparation
tool). Both were investigated end-to-end before deletion:

- **Frontend deleted:** `pages/settings/FacilityPricePreparationPage.jsx`
  (849 lines) and `pages/settings/AIKeySettingsPage.jsx` (298 lines).
- **Routes removed:** `/settings/facility-price-preparation` and
  `/settings/ai-key`, plus their lazy-load imports in `MainRoutes.jsx`.
- **Menu entry removed:** "تجهيز قوائم أسعار المرافق" from
  `menu-items/components.jsx` (the AI settings page had no separate menu
  entry — it was only reachable via direct route or embedded).
- **Embedded usage removed:** `AIKeySettingsPage` was also mounted as tab
  index 8 ("إعدادات الذكاء الاصطناعي") inside `SystemSettingsPage.jsx` — the
  import, `<Tab>` header, and `<TabPanel>` block were all removed together;
  the now-unused `KeyIcon` import was removed too.
- **Backend settings removed:** `AI_CLASSIFIER_API_KEY`, `AI_CLASSIFIER_MODEL`,
  `AI_CLASSIFIER_ENDPOINT`, `BIOBERT_API_URL` — constants and their
  default-row creation blocks deleted from `SettingsInitializationService`;
  `getBiobertApiUrl()` deleted from both `UIConfigService` (definer) and
  `SystemSettingsService` (facade passthrough), confirmed to have zero other
  callers before removal.
- **Database rows removed:** migration `V111` deletes the 4 corresponding
  `system_settings` rows outright (database is experimental; the API key
  value was empty, no secret was discarded).

**Verified NOT touched, after explicit investigation to avoid a false
positive:** `PriceListExcelTemplateService` /
`ProviderContractPricingExcelController` — a same-named-sounding but
functionally unrelated, live production feature for provider-contract
pricing Excel import/export. Confirmed via caller/entity tracing before
any deletion.

**Incidental finding, now moot:** the deleted AI classifier tool called a
third-party AI endpoint (OpenRouter) directly from the browser using an API
key read out of `system_settings`, i.e. a client-side credential-exposure
pattern independent of the feature's experimental status. Recorded here only
because the pattern (settings-sourced secret consumed by client-side
`fetch`) should not be repeated if a similar integration is built later —
any future AI/third-party-API integration should proxy through a backend
endpoint that holds the credential server-side.

Verified: full backend regression (**180 tests, 0 failures, 0 errors, 3
skipped**) including Flyway building all 111 migrations from empty
PostgreSQL 16 through V111; frontend production build passes with both
page chunks confirmed absent from the output; live Docker deployment
confirms the 4 settings rows are gone, the generic settings-list endpoint
still serves correctly with the AI category now empty, and `superadmin`
login succeeds end-to-end.

## Risk register

| ID | Priority | Risk |
|---|---|---|
| S01-01 | Mitigated | Browser session is authoritative; JWT retained only for the planned mobile client |
| S01-02 | Closed | Persistent inventory, owned-session revocation and revoke-all implemented and tested |
| S01-03 | Mitigated | Role consistency and beneficiary-search ownership enforced; remaining resource matrix is incremental |
| S01-04 | Closed | One canonical user API/service; duplicate controller, service and DTO family removed |
| S01-05 | Closed | Generic file-key API removed; all active downloads use owning-resource endpoints and storage coordinates stay internal |
| S01-06 | Closed | All 96 Flyway migrations repeatedly built from zero on PostgreSQL 16 under Testcontainers |
| S01-07 | Closed | Security-relevant writes (login, lockout, password, email verification, user CRUD) unified into `security_audit_events`. `user_audit_log` table, entity and repository fully removed (V109), zero remaining references. `audit_logs` (business-action log, 33 rows) and `medical_audit_logs` (clinical audit trail) deliberately kept separate as distinct domains, not merged. Remaining: S01-13 (observable-failure path for `logSecurityEvent`). |
| S01-08 | Closed | Generic settings no longer expose JPA entities or accept ad-hoc upsert writes; typed domain services (`SLASettingsService`, `AuthenticationSettingsService`, `UIConfigService`, `SettingsManagementService`, `SettingsInitializationService`) confirmed already in place — `SystemSettingsService` is a 147-line facade, not the 510-line file the original audit measured. The AI-classifier-credential gap this risk originally flagged is now moot: the entire AI classifier feature (settings, frontend windows, routes) was removed outright on 2026-07-24 rather than given a dedicated encrypted secret store, since it was an experimental tool with no other consumer. |
| S01-09 | Mitigated | CI confirmed already in place (2026-07-24 — pre-dates this session): `.github/workflows/backend-test.yml`, `frontend-test.yml`, `integration-test.yml`, `security-audit.yml` (scheduled daily). Frontend automated test coverage (unit/component tests, not just lint/build/typecheck) remains the real residual gap. |
| S01-10 | Mitigated | Backend split confirmed already done: `AuthorizationService` (149 lines) and `UserSecurityService` (thin facade after S01-07 work) both delegate to properly-scoped services (`RoleService`, `DataAccessService`, `QueryFilterService`, `FeatureToggleService`, `PasswordManagementService`, `EmailVerificationService`, `LoginSecurityService`) — none exceed the guideline. Remaining: 11 frontend pages between 1,024 and 2,615 lines (led by `ProviderClaimsSubmission.jsx`, `ClaimBatchEntry.jsx`, `BenefitPolicyRulesTab.jsx`) — deliberately not attempted in this pass given the complete absence of frontend test coverage to catch regressions in live claims/provider workflows; needs a dedicated session with a test-first approach. |
| S01-13 | Closed | Added `SecurityAuditServicePostgresIntegrationTest` (2026-07-24) — runs `logSecurityEvent` against real PostgreSQL 16 via Testcontainers (not mocked), asserting a row is actually persisted and the `beforeState`/`afterState` jsonb columns round-trip correctly. This test would have caught the jsonb/varchar defect; it now guards against regression. The `catch (Exception)` in `logSecurityEvent` itself is intentionally still broad (an audit-logging failure must not break the primary user action, e.g. login), but is no longer untested. |
| S01-11 | Closed | Inbound IMAP retired; remaining SMTP secret encrypted at rest and write-only over JSON |
| S01-12 | Closed | Legacy user columns and redundant indexes removed; frontend now reads canonical `active`. — 2026-07-24: six additional confirmed-dead tables dropped (`network_providers`, `claim_history`, `coverage_simulation_runs`, `coverage_simulation_items`, `medical_semantic_rules`, `medical_synonyms`, all 0 rows, zero Entity/Repository references, zero cross-migration reference after creation) via V110. `legacy_provider_contracts` was investigated and confirmed **live** (mapped by `ProviderContract.java`) — correcting an earlier automated-pass false positive; explicitly not dropped. |

## Ordered implementation

1. Regression tests for current session, lockout, reset, file access and admin endpoints. ✅
2. Freeze role/permission catalogue and authorization matrix. ✅
3. Create final RBAC schema and migrate `userType`. ✅
4. Consolidate user administration. ✅
5. Make browser auth session-only and remove legacy localStorage JWT callers. ✅
6. Add persistent session inventory and revoke-current/revoke-all. ✅
7. Consolidate security/admin audit. ✅ (2026-07-24 — S01-07 closed)
8. Centralize file policy and complete IDOR tests. Mitigated (claim/visit/pre-auth/member-photo/provider-document attachment paths hardened; central cross-resource `FileAccessPolicy` still not unified into one class)
9. Split typed settings domains. ✅ (confirmed already done, 2026-07-24 — `SystemSettingsService` is a 147-line facade over 5 typed domain services; the AI-classifier-secret-store gap noted under S01-08 is now moot — the entire AI classifier feature was removed the same day, see below)
10. Harden startup, Dockerfiles, nginx, CORS, cookies and Actuator. Mitigated (prod `ddl-auto=validate`, restricted actuator exposure, no wildcard CORS, Secure/HttpOnly/SameSite=Strict cookies all confirmed in place 2026-07-24; a formal fail-fast startup validator for weak-secret/misconfiguration detection is not yet added)
11. Split oversized units. Partially done (backend confirmed already complete 2026-07-24 — `AuthorizationService`/`UserSecurityService` are thin facades; 11 frontend pages, 1,024–2,615 lines each, remain and need a dedicated test-first session)
12. Produce final schema and clean Flyway baseline. In progress (2026-07-24 — 7 dead tables investigated, 6 confirmed dead and dropped via V110, 1 false positive corrected and preserved; the ~70-migration reduction to a 5-file baseline per the original Flyway inventory is not yet done)
13. Build PostgreSQL from zero; run Hibernate validate, all tests, restart and drift checks. ✅ (proven repeatedly through V110, most recently 2026-07-24: 178 tests, 0 failures)
14. Complete all Section 01 documents and closure review. In progress

## Current accounting

- Production files deleted: **32** (legacy token refresh, raw file controller,
  duplicate user administration, the complete inbound email pre-authorization
  backend/frontend feature, six dead frontend auth template paths
  (`pages/auth/auth0/**`, `pages/auth/aws/**`, `pages/auth/firebase/**`,
  `pages/auth/supabase/**`, `pages/auth/jwt/TestLogin.jsx`,
  `sections/auth/auth-forms/AuthLogin.jsx`), `UserAuditLog.java` +
  `UserAuditLogRepository.java`, `utils/token-storage.js`, and —
  2026-07-24 — `FacilityPricePreparationPage.jsx` + `AIKeySettingsPage.jsx`
  (the full AI-classifier/price-preparation feature removal))
- Tables/columns deleted: **9 tables and 21 columns** — `user_audit_log` (V109),
  plus `network_providers`, `claim_history`, `coverage_simulation_runs`,
  `coverage_simulation_items`, `medical_semantic_rules`, `medical_synonyms`
  (V110; all confirmed 0 rows, 0 Entity/Repository references, 0
  post-creation migration references). `legacy_provider_contracts` was
  investigated and confirmed live (mapped by `ProviderContract.java`) —
  correcting an earlier false positive — and deliberately **not** dropped.
- Settings rows deleted: **4** — `AI_CLASSIFIER_API_KEY`, `AI_CLASSIFIER_MODEL`,
  `AI_CLASSIFIER_ENDPOINT`, `BIOBERT_API_URL` (V111), alongside their backend
  constants, default-initialization code, and getter methods, and the two
  frontend windows/routes/menu-entry/embedded-tab that were their only
  consumers.
- Indexes deleted: **3**
- Migrations deleted: **0** (net-new baseline consolidation, distinct from the
  additive `V109`–`V111` drop migrations, still pending)
- Current migrations: **111** (V1–V111: V109 dropped `user_audit_log`, V110
  dropped 6 confirmed-dead tables, V111 dropped the 4 AI-classifier settings
  rows — all 2026-07-24)
- Production defects found and fixed during this audit pass (not in original
  scope, discovered via live-environment testing): **1** —
  `SecurityAuditEvent.beforeState`/`afterState` jsonb/varchar entity mapping
  bug that silently discarded every `security_audit_events` write in the
  actual deployment since V107 was introduced.
- Environment/config drift found live and reverted: **2** — `.env`
  `SPRING_PROFILES_ACTIVE` (dev→prod) and `frontend/nginx.conf` rate limiting
  (both had been temporarily changed during interactive local debugging and
  were never committed).
- Backend file-size findings confirmed already resolved (not previously
  re-verified): `SystemSettingsService` (510→147 lines, facade over 5 typed
  domain services) and `AuthorizationService` (839→149 lines, facade over 4
  scoped services) — both already split before this audit pass, correcting
  stale line counts in the original inventory.
- `token-storage.js` removed: confirmed all 4 importers (`utils/axios.js`,
  `services/api/auth.service.js`, `contexts/AuthContext.jsx`, `api/rbac.js`)
  only ever called the defensive `clearToken()` cleanup — never
  `getToken()`/`setToken()`/`hasToken()`, confirming the app is fully
  session-cookie based with no live JWT-in-storage path. All four call sites
  and imports removed; file deleted outright.
- CI confirmed already in place (pre-dating this session):
  `.github/workflows/backend-test.yml`, `frontend-test.yml`,
  `integration-test.yml`, `security-audit.yml` (daily scheduled scan) — S01-09
  downgraded from "no CI" to "frontend automated test coverage is the residual
  gap."
- S01-13 closed: `SecurityAuditServicePostgresIntegrationTest` added, running
  `logSecurityEvent` against real PostgreSQL via Testcontainers and asserting
  the jsonb `beforeState`/`afterState` round-trip — the exact gap that let the
  jsonb/varchar defect ship undetected by the fully-mocked
  `SecurityAuditServiceTest`.
- Facility price-preparation & AI classifier feature removed in full (S01-08
  closed): 2 frontend pages, 2 routes, 1 menu entry, 1 embedded settings tab,
  4 backend settings constants/initializers/getters, 4 database rows — see
  dedicated section above. Verified not to affect the unrelated, live
  `PriceListExcelTemplateService`/`ProviderContractPricingExcelController`
  provider-contract-pricing feature.
- Tests run in this audit phase: **180 backend tests** (178 + 2 new real-Postgres
  audit integration tests) **+ frontend production build**, plus live Docker
  smoke tests (login success/failure, account lockout, user create/delete,
  dead-table absence, live-table presence, AI-settings-rows absence) directly
  against PostgreSQL — the layer where the jsonb defect above was actually
  caught, since it did not reproduce against the Testcontainers/mocked test
  paths.
- Open P0: **0**
- Open P1: **1** (the frontend half of S01-10 — 11 oversized React pages,
  1,024–2,615 lines each, deliberately deferred to a dedicated test-first
  session given zero frontend regression-test coverage on live claims/provider
  workflows). Every other previously-open P1 (S01-09 CI, S01-13 silent
  audit-write-failure) is now closed or mitigated. The ~70-migration Flyway
  baseline consolidation (step 12) remains unexecuted but is tracked as an
  Ordered-implementation step, not a separate P1 risk, given the database is
  explicitly experimental and the current migration chain is fully
  reproducible from zero.
- Section closure estimate: **82%** (inventory, reproducible green baseline,
  full S01-07 audit consolidation, S01-13 closed with a real regression test,
  S01-09 confirmed already resolved, `token-storage.js` removed, S01-08 closed
  via full removal of the AI-classifier/price-preparation feature,
  confirmation that both flagged backend oversized-file risks were already
  resolved, 6 additional dead tables removed with 1 false positive corrected,
  and one live-environment production defect fix; the two remaining gaps
  before full closure are the 11 oversized frontend pages and the Flyway
  baseline rebuild — both large, deliberately-scoped-out pieces of work rather
  than quick fixes)
