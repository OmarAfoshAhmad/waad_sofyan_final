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

## Risk register

| ID | Priority | Risk |
|---|---|---|
| S01-01 | Mitigated | Browser session is authoritative; JWT retained only for the planned mobile client |
| S01-02 | Closed | Persistent inventory, owned-session revocation and revoke-all implemented and tested |
| S01-03 | Mitigated | Role consistency and beneficiary-search ownership enforced; remaining resource matrix is incremental |
| S01-04 | Closed | One canonical user API/service; duplicate controller, service and DTO family removed |
| S01-05 | Closed | Generic file-key API removed; all active downloads use owning-resource endpoints and storage coordinates stay internal |
| S01-06 | Closed | All 96 Flyway migrations repeatedly built from zero on PostgreSQL 16 under Testcontainers |
| S01-07 | P1 | Three general audit models |
| S01-08 | P1 | Generic mixed settings |
| S01-09 | P1 | No CI/insufficient frontend tests |
| S01-10 | P1 | Oversized security/settings files |
| S01-11 | Closed | Inbound IMAP retired; remaining SMTP secret encrypted at rest and write-only over JSON |
| S01-12 | Closed | Legacy user columns and redundant indexes removed; frontend now reads canonical `active` |

## Ordered implementation

1. Regression tests for current session, lockout, reset, file access and admin endpoints.
2. Freeze role/permission catalogue and authorization matrix.
3. Create final RBAC schema and migrate `userType`.
4. Consolidate user administration.
5. Make browser auth session-only and remove legacy localStorage JWT callers.
6. Add persistent session inventory and revoke-current/revoke-all.
7. Consolidate security/admin audit.
8. Centralize file policy and complete IDOR tests.
9. Split typed settings domains.
10. Harden startup, Dockerfiles, nginx, CORS, cookies and Actuator.
11. Split oversized units.
12. Produce final schema and clean Flyway baseline.
13. Build PostgreSQL from zero; run Hibernate validate, all tests, restart and drift checks.
14. Complete all Section 01 documents and closure review.

## Current accounting

- Production files deleted: **22** (legacy token refresh, raw file controller,
  duplicate user administration, and the complete inbound email pre-
  authorization backend/frontend feature)
- Tables/columns deleted: **2 tables and 21 columns**
- Indexes deleted: **3**
- Migrations deleted: **0**
- Current migrations: **97**
- Tests run in this audit phase: **165 backend tests + frontend production build**.
- Open P0: **0**
- Open P1: **4**
- Section closure estimate: **50%** (inventory and reproducible green baseline,
  not production readiness)
