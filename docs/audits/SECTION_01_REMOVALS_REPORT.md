# SECTION 01 — Removal Candidates and Dependency Plan

**Status:** candidates only; nothing listed here has been deleted.

## High-confidence frontend candidates

| Candidate | Evidence | Required proof |
|---|---|---|
| `pages/auth/auth0/**` | No live import found | Dependency graph, build, route smoke |
| `pages/auth/aws/**` | No live import found | Same |
| `pages/auth/firebase/**` | No live import found | Same |
| `pages/auth/supabase/**` | No live import found | Same |
| `pages/auth/jwt/TestLogin.jsx` | No live import found | Build/route smoke |
| `sections/auth/auth-forms/AuthLogin.jsx` | Duplicate; no live import | Confirm no dynamic import |

## Conditional frontend candidates

| Candidate | Current dependency | Replacement |
|---|---|---|
| `tokenRefresh.service.js` | `UserEdit`, `ProviderEdit`, `ProviderCreate` | Session `refreshUser()` |
| `token-storage.js` | Compatibility cleanup/logout | Remove after JWT browser flow |
| old auth reducer/helpers | Import mapping incomplete | One `AuthContext`/`useAuth` |
| duplicate settings contexts | Import mapping incomplete | Public appearance provider + typed admin clients |

## Conditional Backend candidates

| Candidate | Reason | Before removal |
|---|---|---|
| JWT `/auth/login` | Duplicates session login | Decide external API/mobile contract |
| JWT `/auth/me` | Duplicates session user | Map callers |
| `/auth/refresh-token` | Not real refresh-token rotation | Replace three frontend callers |
| JWT filter in browser chain | Hybrid compatibility | Decide separate API chain |
| excess JWT personal claims | Violates minimum claims | External contract check |
| one user-management controller | Duplicate API | Frontend/API matrix |
| one user-management service | Duplicate operations | Migrate tests/controllers |
| misleading permission DTOs | No effective permission model | Final RBAC may reuse names |

## Database candidates

No object is approved for deletion before live-schema inspection and final RBAC,
session and audit design.

| Candidate | Evidence | Likely decision |
|---|---|---|
| three generic audit tables | Overlapping writers | Merge to one security/admin event |
| `module_access.required_permissions` | Dynamic permissions declared removed | Rebuild or remove duplication |
| V60/V83 blanket email verification | Duplicate operation | Exclude from baseline |
| reset token legacy columns | V95 alignment workaround | Final hashed-secret model |
| old repair migration chain | 94 migrations with reversals | Archive; clean tested baseline |
| `legacy_provider_contracts` | V12 + legacy code | Record only; implementation belongs to contract section |

Additional live-schema candidates:

| Candidate | Live evidence | Required proof/replacement |
|---|---|---|
| `users.enabled` or `users.is_active` | Same value for all 41 users | Select canonical account state and test disabling |
| `users.last_login` or `last_login_at` | Three rows differ | Define semantics and migrate trusted timestamp |
| `users.company_id` | Zero populated rows | Prove no writer, then remove |
| five `users.can_view_*` fields | Coexist with role/guard systems | Replace with final RBAC |
| duplicate token expiry columns | Equal on all 40 email tokens; reset table empty | Keep one `expires_at` |
| redundant lookup/token indexes | Unique indexes already cover several lookups | Confirm query plans; clean baseline |
| duplicate failed-login indexes | Equivalent username/time partial indexes | Retain one |
| SMTP/IMAP password columns | Empty live, but code writes/returns raw values | Move to environment/secret manager |

## Explicitly retained

- session login/me/logout
- login-attempt and lockout capability
- password reset and email verification capability
- claim/preauth/payment domain audit histories
- correlation/MDC support
- `FileStorageService` abstraction
- production `ddl-auto=validate`

## Safe removal sequence

1. Add regression tests.
2. Map endpoint/import/database dependencies.
3. Replace live callers.
4. Remove unused auth templates.
5. Remove browser JWT compatibility.
6. Consolidate users.
7. Consolidate audit after migration tests.
8. Split/migrate settings.
9. Freeze final schema.
10. replace old Flyway chain with clean baseline.

## Accounting

- Deleted files: **1**
- Deleted tables: **0**
- Deleted columns: **0**
- Deleted migrations: **0**
- Candidate groups: frontend **10**, Backend **8**, database **14**

## Baseline gate completed — 2026-07-23

No removal has been authorized by evidence yet. The pre-removal gate is now
green: 134 backend tests pass against a disposable PostgreSQL 16 database
created from all 95 Flyway migrations; frontend lint and production build also
complete successfully.

Authentication dependency mapping confirms the browser's canonical path is
`/auth/session/login`, `/auth/session/me`, and `/auth/session/logout` with an
HttpOnly `JSESSIONID`.

## First evidence-backed removal — 2026-07-23

`frontend/src/services/auth/tokenRefresh.service.js` was removed after all of
its callers were replaced:

- provider edit refreshes the authenticated user through `refreshUser()`;
- unused imports were removed from provider creation and user editing;
- category template download sends the session cookie;
- reviewer SSE no longer exposes `serviceToken` in the URL.

Repository-wide search found no remaining import or call to
`tokenRefresh.service.js`/`refreshToken`. The production frontend build passed
after removal. `token-storage.js` is temporarily retained only to erase
pre-migration browser artifacts during login/logout.

Seven PostgreSQL-backed session integration tests now cover session fixation,
`/session/me`, logout revocation, user deactivation, invalid credentials, the
absence of a JWT in the session-login response, session ownership, two-device
revoke-all, and automatic revocation after a password change.

Legacy JWT remains active only in the backend security chain. It has no current
browser caller, but removal is deferred until the external API/mobile contract
is explicitly proven or versioned.
