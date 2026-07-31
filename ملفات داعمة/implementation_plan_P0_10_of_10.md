# Implementation Plan — P0 Security Blockers, Data Integrity, and Production Readiness

## 1. Objective

This phase closes the confirmed **P0 critical blockers** affecting authorization, attachment access, role consistency, database migration safety, production configuration, and release verification.

The phase is complete only when every acceptance criterion in this document is satisfied by automated evidence. Compilation alone, manual spot checks, or partially protected endpoints do not qualify as closure.

---

## 2. Non-Negotiable Security Decisions

> [!IMPORTANT]
> These decisions are approved defaults for implementation and must not be weakened without an explicit architectural decision record.

1. **Provider isolation is absolute.** A provider user must never access attachments, claims, visits, or provider documents belonging to another provider, even when the UUID, database identifier, file key, or storage path is known.
2. **Tenant boundaries are enforced on every protected read.** Authorization must validate both tenant context and business ownership.
3. **Access is denied by default.** Missing user context, missing tenant context, unresolved provider context, unsupported roles, or inconsistent ownership must result in denial.
4. **Authorization is enforced server-side.** Frontend route guards and hidden buttons are usability controls only and are never treated as security controls.
5. **File identifiers are untrusted input.** A UUID, filename, document key, or local path must not grant access by itself.
6. **Cross-scope resources return `404 Not Found` by default.** This reduces resource-enumeration leakage. Use `403 Forbidden` only where the API contract explicitly requires revealing that the resource exists.
7. **Business roles remain distinct.** `INSURANCE_ADMIN` must not be silently converted to `SYSTEM_ADMIN`. Role migration is based on semantic equivalence, not string similarity.
8. **Flyway is the only production schema authority.** Hibernate must use `ddl-auto: validate` in production.
9. **No P0 closure without negative tests.** Every allow case must have a corresponding deny case.
10. **No production release with unresolved P0 findings, disabled security tests, or undocumented exceptions.**

---

## 3. Scope

### 3.1 In Scope

- Claim attachment download, preview, metadata, and deletion endpoints.
- Visit attachment download, preview, metadata, and deletion endpoints.
- Provider document download, preview, metadata, and deletion endpoints.
- Any generic file-serving endpoint that can expose the same stored resources.
- Tenant, provider, role, and business-ownership authorization.
- Role constants, persisted roles, JWT claims, Spring Security expressions, fixtures, and frontend role checks.
- Production file-storage paths and filesystem permissions.
- Production Spring and Docker configuration.
- Flyway migration cleanup and fresh-database reproducibility.
- Automated tests, CI release gates, security audit logging, and rollback readiness.

### 3.2 Out of Scope

The following are not silently included in this phase unless discovered as direct P0 dependencies:

- Major business-feature redesign.
- New claim or pre-authorization workflows.
- Cosmetic frontend refactoring.
- Performance optimization unrelated to the security fixes.

Any newly discovered P0 vulnerability becomes in scope immediately and must be added to the findings register.

---

## 4. Completion Model

The work is executed in ordered gates. A gate is not considered closed until its exit criteria pass.

| Gate | Area | Exit condition |
|---|---|---|
| G0 | Discovery and inventory | Every relevant endpoint, role string, storage path, and migration is catalogued |
| G1 | RBAC contract | Canonical roles and permissions are documented, implemented, and migration-safe |
| G2 | Attachment authorization | All attachment/document routes use centralized, deny-by-default authorization |
| G3 | Storage and production hardening | Paths, permissions, profiles, secrets, and file handling are production-safe |
| G4 | Flyway and schema integrity | A fresh PostgreSQL database migrates successfully and matches the JPA model |
| G5 | Automated verification | Security, integration, migration, and regression suites pass in CI |
| G6 | Release readiness | Rollback, evidence, and final P0 sign-off are complete |

---

# Gate G0 — Complete Security and Configuration Inventory

## 5. Endpoint Inventory

Search the backend for every route that can read or expose stored files, including:

- `Resource`
- `ResponseEntity<Resource>`
- `FileSystemResource`
- `InputStreamResource`
- `byte[]`
- `Files.readAllBytes`
- `Files.newInputStream`
- `/download`
- `/preview`
- `/attachments`
- `/documents`
- `/files`
- storage service methods returning paths or streams

Create a tracked inventory containing:

| Field | Required value |
|---|---|
| Endpoint | HTTP method and route |
| Controller method | Class and method |
| Resource type | Claim attachment, visit attachment, provider document, or other |
| Parent entity | Claim, visit, provider, member, etc. |
| Current authorization | Annotation and runtime checks |
| Required ownership rule | Tenant, provider, insurer, reviewer scope |
| Response on denial | 404 or 403 |
| Automated test | Test class and case |
| Status | Open / Fixed / Verified |

No endpoint may be excluded because it is considered “internal” unless network and method-level controls prove that it cannot be called by an untrusted user.

## 6. Role and Permission Inventory

Search all backend, frontend, tests, migrations, seeds, and configuration for:

- `REVIEWER`
- `MEDICAL_REVIEWER`
- `INSURANCE_ADMIN`
- `SYSTEM_ADMIN`
- `SUPER_ADMIN`
- `PROVIDER`
- `EMPLOYER_ADMIN`
- `PORTAL_USER`
- `hasRole`
- `hasAnyRole`
- `hasAuthority`
- role-name string comparisons

Produce a role-usage report before changing any value. The report must identify whether each legacy name is:

- a true deprecated alias;
- a distinct business role;
- an invalid or dead value;
- stored in production-like data;
- embedded in issued JWTs or test fixtures.

## 7. G0 Exit Criteria

- [ ] All file-serving endpoints are in the inventory.
- [ ] All role strings and authorization expressions are inventoried.
- [ ] All production and Docker storage-path definitions are inventoried.
- [ ] All Flyway migrations and Hibernate schema settings are inventoried.
- [ ] No unknown P0 endpoint remains.

---

# Gate G1 — Canonical RBAC Contract and Safe Role Standardisation

## 8. Canonical Role Model

Use one authoritative role enum or constants class. No controller, service, frontend guard, test fixture, or migration may define ad hoc role strings.

Recommended canonical roles for the current platform:

| Role | Intended scope |
|---|---|
| `SUPER_ADMIN` or `SYSTEM_ADMIN` | Platform-wide administration explicitly granted by policy |
| `INSURANCE_ADMIN` | Administration limited to the assigned insurance organization and its business data |
| `MEDICAL_REVIEWER` | Medical review actions within explicitly assigned review scope |
| `PROVIDER` | Provider-owned data only |
| `EMPLOYER_ADMIN` | Employer-owned administrative data only; no implicit access to clinical attachments |
| `VIEW_REPORTS` | Read-only reports within assigned scope |
| `PORTAL_USER` | Base portal access only; grants no sensitive business permission by itself |

> [!WARNING]
> `INSURANCE_ADMIN` and `SYSTEM_ADMIN` are not interchangeable. Do not replace one with the other unless the role matrix and persisted-data analysis prove they are semantically identical.

## 9. Permission-Based Controller Rules

Prefer explicit permissions for sensitive actions rather than broad role lists.

Example permissions:

- `CLAIM_ATTACHMENT_READ`
- `CLAIM_ATTACHMENT_DELETE`
- `VISIT_ATTACHMENT_READ`
- `VISIT_ATTACHMENT_DELETE`
- `PROVIDER_DOCUMENT_READ`
- `PROVIDER_DOCUMENT_DELETE`
- `PREAUTH_REVIEW`

Controller annotations should provide coarse method protection, while the service-level policy enforces object ownership.

Example:

```java
@PreAuthorize("hasAuthority('CLAIM_ATTACHMENT_READ')")
@GetMapping("/{attachmentId}/download")
public ResponseEntity<Resource> download(@PathVariable UUID attachmentId) {
    return attachmentApplicationService.download(attachmentId);
}
```

## 10. Pre-Authorization Review Correction

### [MODIFY] `PreAuthReviewController.java`

Do not blindly change:

```java
hasAnyRole('REVIEWER', 'INSURANCE_ADMIN')
```

to:

```java
hasAnyRole('MEDICAL_REVIEWER', 'SYSTEM_ADMIN')
```

Instead:

1. Introduce or reuse the canonical `PREAUTH_REVIEW` permission.
2. Map `MEDICAL_REVIEWER` to that permission.
3. Map `INSURANCE_ADMIN` only if insurance administrators are intentionally permitted to perform medical review.
4. Map `SYSTEM_ADMIN` only if platform administrators are intentionally allowed to perform operational medical review.
5. Add tests for each allowed and denied role.

Preferred annotation:

```java
@PreAuthorize("hasAuthority('PREAUTH_REVIEW')")
```

## 11. Persisted Role Migration

If legacy persisted values exist:

1. Back up the role and user-role tables.
2. Add a Flyway migration that maps only confirmed aliases.
3. Reject or report unknown role strings.
4. Preserve distinct business roles.
5. Update seeds and fixtures.
6. Invalidate or rotate tokens containing obsolete role claims where required.

The migration must be idempotent at the data level and verified against realistic test data.

## 12. G1 Exit Criteria

- [ ] One canonical role/permission source exists.
- [ ] No security decision depends on uncontrolled string literals.
- [ ] `INSURANCE_ADMIN` was not elevated to `SYSTEM_ADMIN` without an approved matrix.
- [ ] Persisted roles and JWT claims are migration-safe.
- [ ] Every protected controller has role/permission tests.

---

# Gate G2 — Centralized Attachment and Document Authorization

## 13. Central Authorization Components

Create a centralized policy layer. Recommended structure:

```text
security/
  CurrentUserContext.java
  ResourceAccessDecision.java
  AttachmentAccessPolicy.java
  ClaimAttachmentAccessPolicy.java
  VisitAttachmentAccessPolicy.java
  ProviderDocumentAccessPolicy.java
  FileAccessAuditService.java
```

The policy receives the authenticated context and resource identity, then returns an allow decision or throws a domain-specific not-found/forbidden exception.

Authorization logic must not be duplicated across controllers.

## 14. Current User Context

Create a trusted server-side context containing, as applicable:

- authenticated user ID;
- canonical roles and permissions;
- tenant or insurance organization ID;
- provider ID;
- employer ID;
- reviewer scope;
- active/inactive status;
- token/session identifier for audit correlation.

Never accept these scope values from request parameters or headers unless they are independently verified against the authenticated identity.

## 15. Query-Level Ownership Enforcement

Prefer repository queries that retrieve only accessible resources.

Example concept:

```java
Optional<ClaimAttachment> findAccessibleAttachment(
    UUID attachmentId,
    UUID tenantId,
    UUID providerId,
    Set<String> permissions
);
```

At minimum, the access decision must validate the complete chain:

```text
attachment -> parent claim/visit/provider document -> owning provider -> tenant
```

The physical file must not be opened until authorization succeeds.

## 16. Resource-Specific Rules

### 16.1 Claim Attachments

#### [MODIFY] `ClaimAttachmentController.java`
#### [MODIFY] `ClaimAttachmentService.java`

Required policy:

- Provider user: claim belongs to the authenticated provider and tenant.
- Medical reviewer: claim is inside the reviewer’s authorized scope.
- Insurance administrator: claim belongs to the assigned insurance tenant and the permission is granted.
- System administrator: allowed only through an explicit permission, not merely the role name.
- Employer administrator and portal-only user: denied unless an explicit, documented use case grants access.

Apply the same policy to:

- download;
- inline preview;
- metadata retrieval;
- deletion;
- replacement/update;
- listing attachments under a claim.

### 16.2 Visit Attachments

#### [MODIFY] `VisitAttachmentController.java`
#### [MODIFY] corresponding visit attachment service/repository

Validate:

- visit tenant;
- visit provider;
- linked member/employer boundaries where relevant;
- reviewer assignment or permission;
- attachment parent consistency.

### 16.3 Provider Documents

#### [MODIFY] all provider-document controllers, services, and repositories identified in G0

Validate:

- provider ownership;
- tenant boundary;
- document visibility/type;
- administrative permission;
- deletion authority separately from read authority.

This component is mandatory. Mentioning provider documents in the scope without modifying their endpoints does not close the finding.

### 16.4 Generic File Endpoints

Any generic endpoint that accepts a storage key, relative path, filename, or UUID must either:

- be removed;
- be made private to trusted internal code; or
- resolve the key to a protected domain entity and execute the same authorization policy.

Direct filesystem access by request-supplied path is prohibited.

## 17. Path and Response Safety

Before reading a file:

1. Resolve the configured storage root to a canonical path.
2. Resolve the stored relative path against the root.
3. Normalize the result.
4. Verify the result starts with the canonical storage root.
5. Reject `..`, absolute paths, symbolic-link escapes, invalid encodings, and null-byte patterns.
6. Verify the database record and physical file agree.
7. Set safe `Content-Type` and `Content-Disposition` headers.
8. Add `X-Content-Type-Options: nosniff` where appropriate.
9. Never expose the real server filesystem path in API responses or logs.

## 18. Audit Logging

Record successful and denied access attempts for sensitive files with:

- timestamp;
- authenticated user ID;
- tenant/provider context;
- resource type and resource ID;
- parent entity ID;
- action: preview/download/delete;
- outcome: allowed/denied/not-found;
- denial reason code;
- request correlation ID;
- source IP where permitted by policy.

Do not log medical file contents, tokens, passwords, or sensitive raw paths.

## 19. G2 Exit Criteria

- [ ] Every inventoried file endpoint uses centralized authorization.
- [ ] Authorization occurs before the physical file is opened.
- [ ] Cross-provider and cross-tenant access is impossible by UUID or file key.
- [ ] Preview, metadata, listing, download, update, and delete paths are all protected.
- [ ] Generic file-serving backdoors are removed or secured.
- [ ] Denied access is audit-logged without leaking sensitive data.

---

# Gate G3 — Production Configuration and File-Storage Hardening

## 20. Spring Production Profile

### [MODIFY] `application-prod.yml`

Required production settings:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    validate-on-migrate: true
    clean-disabled: true
```

Also verify:

- production cannot start with an accidental development profile;
- no environment variable overrides `ddl-auto` to `update`, `create`, or `create-drop`;
- SQL logging and bind-parameter logging are disabled in production;
- stack traces and internal exception details are not exposed to clients;
- Actuator endpoints are restricted;
- CORS is allowlisted, not wildcarded with credentials;
- upload size and request size limits are explicit;
- secrets have no repository defaults.

## 21. Docker Configuration

### [MODIFY] `docker-compose.yml`

Required controls:

- backend and storage paths use one consistent environment variable;
- the storage directory is a persistent volume outside the container layer;
- backend container runs as a non-root user;
- volume permissions allow only the application user and approved operators;
- database and internal services are not exposed publicly unless required;
- secrets are supplied through a secure environment mechanism;
- health checks cover application readiness and database connectivity;
- restart policy is appropriate and does not hide crash loops;
- production compose does not mount source code or development tooling.

Example consistency rule:

```text
FILE_STORAGE_LOCAL_BASE_PATH inside Spring configuration
= mounted backend path inside the container
= documented host volume destination
```

## 22. File Upload Controls

Because downloaded files originate from uploads, verify the upload path too:

- allowlisted file types and extensions;
- MIME and extension consistency checks;
- maximum file size;
- generated internal filenames rather than trusting the original name;
- malware scanning integration point or quarantine workflow;
- no executable permission on uploaded files;
- no direct web-server execution from the upload directory.

## 23. G3 Exit Criteria

- [ ] Production uses `ddl-auto: validate` with Flyway enabled.
- [ ] No insecure environment override is possible in the deployment definition.
- [ ] Backend runs non-root with least-privilege filesystem access.
- [ ] Storage volume is persistent, consistent, backed up, and not directly public.
- [ ] File upload and response headers are hardened.
- [ ] Secrets and internal diagnostics are not exposed.

---

# Gate G4 — Flyway Cleanup and Schema Integrity

## 24. Migration Strategy for the Experimental Database

Because the current database is experimental and destructive cleanup is permitted, use a controlled baseline reset instead of preserving unnecessary migration accumulation.

### Approved strategy

1. Export any test data that must be retained.
2. Archive the existing migration directory in version control for traceability.
3. Review the final intended JPA/domain schema and remove obsolete fields, tables, indexes, and constraints.
4. Create a clean baseline migration such as:

```text
V1__baseline_schema.sql
```

5. Add essential reference/seed data in a separate migration, for example:

```text
V2__seed_required_reference_data.sql
```

6. Add future changes only as small, ordered, immutable migrations.
7. Never edit an already released production migration after the baseline is adopted.

> [!CAUTION]
> Baseline squashing is allowed only before a real production database depends on the existing migration history. If any production-like environment must be preserved, use an additive migration path instead.

## 25. Migration Quality Rules

- One migration has one coherent purpose.
- No generated dump with uncontrolled ordering.
- Every foreign key has an intentional delete/update policy.
- Required indexes exist for ownership and authorization queries.
- Unique constraints represent domain invariants.
- Nullable columns are justified.
- Obsolete columns and tables are removed from the clean baseline.
- Seed users do not use default production passwords.
- Migration SQL is PostgreSQL-compatible and reviewed.

## 26. Required Migration Verification

Run on an empty PostgreSQL 16 database:

```bash
./mvnw clean verify
```

Then verify:

1. Flyway migrates from zero to latest.
2. The application starts with `ddl-auto=validate`.
3. No schema-validation mismatch exists.
4. The migration can be reproduced in CI using Testcontainers.
5. A second startup makes no unintended schema changes.
6. Authorization indexes and constraints exist.

For local destructive verification only:

```bash
flyway clean
flyway migrate
```

Production must keep `clean-disabled=true`.

## 27. G4 Exit Criteria

- [ ] Experimental migration history is clean, intentional, and documented.
- [ ] A fresh PostgreSQL 16 database migrates successfully.
- [ ] Hibernate validation passes with zero schema drift.
- [ ] No obsolete schema objects remain.
- [ ] Migration verification runs automatically in CI.

---

# Gate G5 — Automated Security, Integration, and Regression Verification

## 28. Test Architecture

Use PostgreSQL/Testcontainers for security and repository integration tests. H2-only success is insufficient for production readiness.

Required layers:

- unit tests for policy decisions;
- repository tests for tenant/provider-filtered queries;
- controller integration tests with real Spring Security;
- migration tests against PostgreSQL 16;
- Docker/container smoke tests;
- frontend authorization regression tests where role handling changed.

## 29. Mandatory Authorization Test Matrix

For each attachment/document resource and each operation:

| Scenario | Expected result |
|---|---|
| Owner provider accesses own resource | Allowed |
| Provider A accesses Provider B resource | 404/denied |
| Same provider ID in another tenant | 404/denied |
| Authorized medical reviewer within scope | Allowed |
| Medical reviewer outside assigned scope | 404/denied |
| Authorized insurance administrator in tenant | Allowed only with explicit permission |
| Insurance administrator in another tenant | 404/denied |
| Employer administrator requests clinical attachment | Denied unless explicitly permitted |
| Portal-only user requests sensitive file | Denied |
| System administrator without action permission | Denied |
| System administrator with explicit action permission | Allowed |
| Unauthenticated request | 401 |
| Missing provider/tenant context | Denied |
| Disabled or deleted user | Denied |
| Valid UUID for nonexistent record | 404 |
| Existing DB record with missing physical file | Controlled 404/error, audit logged |
| Tampered storage path | Denied |
| `../` path traversal attempt | Denied |
| Symlink escape attempt | Denied |
| Preview endpoint cross-scope attempt | Denied |
| Metadata endpoint cross-scope attempt | Denied |
| Delete endpoint with read-only permission | Denied |

## 30. Role Regression Tests

Test every affected role against `PreAuthReviewController` and other changed controllers:

- allowed roles/permissions return expected success;
- legacy `REVIEWER` does not work after migration unless a temporary compatibility layer is explicitly approved;
- `INSURANCE_ADMIN` is not granted platform-wide powers;
- `PORTAL_USER` alone grants no sensitive action;
- JWT role/permission claims match persisted assignments;
- frontend and backend use the same canonical role names.

## 31. CI Release Gates

The pull request and release pipeline must fail on any of the following:

- compilation failure;
- unit or integration test failure;
- migration failure;
- schema-validation failure;
- security test failure;
- disabled/quarantined P0 test;
- newly introduced legacy role string;
- insecure production `ddl-auto` value;
- secret detected in repository or image;
- critical/high dependency or container vulnerability without an approved exception.

Required backend command:

```bash
./mvnw clean verify
```

`test-compile` is not an acceptable release gate because it does not execute the test suite.

## 32. Manual Verification

Manual verification supplements automated tests; it does not replace them.

Perform with at least two providers in the same tenant and providers in different tenants:

1. Authenticate as Provider A.
2. Download and preview Provider A resources successfully.
3. Attempt Provider B UUIDs and confirm non-disclosing denial.
4. Repeat for claim attachments, visit attachments, and provider documents.
5. Authenticate as each administrative/reviewer role and verify the approved matrix.
6. Confirm denied attempts appear in audit logs with correlation IDs.
7. Confirm responses reveal neither physical paths nor sensitive entity details.

## 33. G5 Exit Criteria

- [ ] `./mvnw clean verify` passes.
- [ ] PostgreSQL/Testcontainers security tests pass.
- [ ] Every inventory endpoint has positive and negative coverage.
- [ ] Role regression tests pass across backend and frontend.
- [ ] CI rejects insecure configuration and legacy role regressions.
- [ ] No P0 test is skipped, disabled, or flaky.

---

# Gate G6 — Release, Rollback, and Evidence

## 34. Deployment Sequence

1. Freeze schema and role changes for the release candidate.
2. Back up the database and file-storage metadata.
3. Build immutable backend/frontend images.
4. Run migrations in a staging environment matching production.
5. Run automated smoke and authorization tests.
6. Deploy backend before exposing changed clients if API compatibility requires it.
7. Verify health, logs, migration status, and file access.
8. Enable traffic gradually where infrastructure supports it.
9. Monitor denied-access spikes, 5xx errors, storage errors, and authentication failures.

## 35. Rollback Plan

The release package must include:

- previous immutable application image tags;
- database backup/restore instructions;
- file-storage backup validation;
- documented handling of non-reversible migrations;
- rollback owner and decision authority;
- rollback triggers, including elevated 5xx rate, authorization bypass, migration mismatch, or widespread attachment failure.

Do not rely on Flyway `undo` unless it is explicitly implemented and tested. Prefer forward-fix migrations or database restore for critical schema rollback.

## 36. Required Evidence Package

Before P0 sign-off, attach:

- endpoint inventory;
- approved RBAC matrix;
- list of changed files;
- migration report;
- test report;
- security test matrix results;
- fresh-database startup evidence;
- Docker configuration review;
- manual verification checklist;
- unresolved-risk register, which must contain no open P0 item;
- rollback procedure.

## 37. Final Acceptance Criteria

The phase receives production-readiness approval only when all statements below are true:

- [ ] A provider cannot access another provider’s resources by any identifier.
- [ ] A user cannot cross tenant boundaries.
- [ ] All attachment/document operations are protected consistently.
- [ ] Authorization is centralized and deny-by-default.
- [ ] No direct request-controlled filesystem access exists.
- [ ] Canonical roles and permissions are consistent across database, JWT, backend, frontend, tests, and seeds.
- [ ] `INSURANCE_ADMIN` was not incorrectly elevated to `SYSTEM_ADMIN`.
- [ ] Production uses Flyway with Hibernate validation only.
- [ ] A fresh PostgreSQL 16 database builds from migrations without drift.
- [ ] The application and storage run with least privilege.
- [ ] Automated positive and negative security tests pass.
- [ ] CI blocks regression of the fixed vulnerabilities.
- [ ] Audit logs provide traceability without sensitive-data leakage.
- [ ] Backup and rollback procedures are verified.
- [ ] No P0 finding remains open or accepted without an explicit signed exception.

---

## 38. Expected File Changes

The exact list is finalized after G0 inventory. It must include at least:

### Existing files to modify

- `ClaimAttachmentController.java`
- `ClaimAttachmentService.java`
- claim attachment repository/query layer
- `VisitAttachmentController.java`
- visit attachment service/repository layer
- all provider document controllers/services/repositories
- `PreAuthReviewController.java`
- canonical role/permission definitions
- Spring Security configuration
- JWT role/authority mapping
- user/role seed and fixtures
- frontend role/route guards affected by renamed roles
- `application-prod.yml`
- `docker-compose.yml`
- Flyway migration files

### Recommended new files

- `CurrentUserContext.java`
- `AttachmentAccessPolicy.java`
- resource-specific access policy classes
- `FileAccessAuditService.java`
- authorization integration test classes
- PostgreSQL/Testcontainers migration test
- RBAC matrix documentation
- P0 endpoint inventory document

---

## 39. Definition of Done

“Implemented” means code exists.

“Verified” means automated and manual evidence proves the expected behavior.

“Closed” means the finding is verified, documented, regression-protected, deployed safely, and has no unresolved P0 dependency.

Only **Closed** findings count toward production readiness.
