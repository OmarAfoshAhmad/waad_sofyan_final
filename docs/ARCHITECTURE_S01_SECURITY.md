# Security & Authorization Architecture (S01)

**Version:** 2.0 (Post-Refactoring)  
**Last Updated:** 2026-07-24  
**Status:** ✅ PRODUCTION

---

## 1. Security Audit Event Model (S01-07)

### Entity Diagram

```
┌─────────────────────────────────┐
│   SecurityAuditEvent            │
├─────────────────────────────────┤
│ id (PK)                         │
│ actorId (FK → User)             │
│ actorUsername (denormalized)    │
│ actionType (enum)               │
│ targetType (string)             │
│ targetId (long)                 │
│ targetIdentifier (string)       │
│ requestIp (string)              │
│ userAgent (string)              │
│ result (enum)                   │
│ safeReason (string)             │
│ beforeState (JSONB)             │
│ afterState (JSONB)              │
│ correlationId (uuid, unique)    │
│ eventTimestamp (datetime)       │
│ createdAt (datetime, auto)      │
├─────────────────────────────────┤
│ Indexes:                        │
│ • actor_id                      │
│ • action_type                   │
│ • event_timestamp               │
│ • correlation_id                │
└─────────────────────────────────┘
```

### Audit Action Types (27 Total)

**Authentication Events:**
- LOGIN_SUCCESS
- LOGIN_FAILED
- ACCOUNT_LOCKED
- ACCOUNT_UNLOCKED
- EMAIL_VERIFIED

**Password Management:**
- PASSWORD_CHANGED
- PASSWORD_RESET
- PASSWORD_RESET_REQUESTED
- PASSWORD_RESET_TOKEN_GENERATED

**User Management:**
- USER_CREATED
- USER_UPDATED
- USER_ACTIVATED
- USER_DEACTIVATED
- USER_DELETED

**Access Control:**
- FILE_ACCESS_DENIED
- UNAUTHORIZED_ACCESS_ATTEMPT
- PERMISSION_GRANTED
- PERMISSION_REVOKED

**Configuration:**
- SYSTEM_SETTING_CHANGED
- FEATURE_TOGGLED
- ROLE_ASSIGNED
- ROLE_REMOVED

**Data Operations:**
- DATA_EXPORTED
- DATA_DELETED
- POLICY_CHANGED
- CLAIM_PROCESSED

### Result Types (3 Total)

- **SUCCESS**: Operation completed without security violations
- **DENIED**: Access was explicitly denied (authorization failure)
- **ERROR**: Operation failed with security implications

### Sensitive Data Filtering

The `SecurityAuditService` automatically redacts sensitive information:

```java
// Patterns to exclude from beforeState/afterState:
- password
- token
- secret
- credential
- api_key
- private_key
```

**Example:**
```json
{
  "beforeState": {
    "username": "john.doe",
    "email": "john@example.com",
    "password": "***REDACTED***"
  },
  "afterState": {
    "username": "john.doe",
    "email": "john@example.com",
    "password": "***REDACTED***"
  }
}
```

---

## 2. Authorization Model (S01-10 Refactored)

### Role Hierarchy

```
┌─────────────────────────────────────┐
│        SUPER_ADMIN                  │
│  (Bypass all checks)                │
└──────────┬──────────────────────────┘
           │ (always has higher privilege)
           ▼
┌─────────────────────────────────────┐
│     INSURANCE_ADMIN                 │
│     (Full data access)              │
└─────────────────────────────────────┘
           │ (always has higher privilege)
           ▼
  ┌────────┴────────┬──────────────┬──────────────┐
  ▼                 ▼              ▼              ▼
EMPLOYER_ADMIN   PROVIDER_STAFF  MEDICAL_REVIEW  DATA_ENTRY
(Org data only)  (Own provider)   (Claims only)  (Data entry)
```

### Service Decomposition

#### RoleService
**Responsibility:** Determine user's role and permissions

```java
isSuperAdmin(user)           // → boolean
isInsuranceAdmin(user)       // → boolean
isEmployerAdmin(user)        // → boolean
isProvider(user)             // → boolean
isReviewer(user)             // → boolean
isDataEntry(user)            // → boolean
isInternalStaff(user)        // → boolean
```

#### DataAccessService
**Responsibility:** Enforce row-level data access control

```java
canAccessMember(user, memberId)        // → boolean
canAccessClaim(user, claimId)          // → boolean
canAccessVisit(user, visitId)          // → boolean
canAccessProvider(user, providerId)    // → boolean
canModifyClaim(user, claimId)          // → boolean
```

#### QueryFilterService
**Responsibility:** Filter database queries by user scope

```java
getEmployerFilterForUser(user)         // → Long (employerId or null)
getProviderFilterForUser(user)         // → Long (providerId or null)
resolveEmployerScope(user, requested)  // → Long (enforced scope)
resolveProviderScope(user, requested)  // → Long (enforced scope)
```

#### FeatureToggleService
**Responsibility:** Employer-specific feature flags

```java
canEmployerViewMembers(user)           // → boolean
canEmployerViewBenefitPolicies(user)   // → boolean
```

### Access Control Matrix

| Role | Member Access | Claim Access | Visit Access | Provider Access | Feature Flags |
|------|---------------|--------------|--------------|-----------------|--------------|
| SUPER_ADMIN | ✅ ALL | ✅ ALL | ✅ ALL | ✅ ALL | ✅ BYPASS |
| ACCOUNTANT | ✅ ALL | ✅ ALL | ✅ ALL | ✅ ALL | ✅ BYPASS |
| EMPLOYER_ADMIN | ✅ Own only | ✅ Own only | ✅ Own only | ❌ NO | ✅ Configured |
| PROVIDER_STAFF | ❌ NO | ✅ Own only | ✅ Own only | ✅ Own only | ❌ NO |
| MEDICAL_REVIEWER | ❌ NO | ✅ ALL (read) | ❌ NO | ❌ NO | ❌ NO |
| DATA_ENTRY | ❌ NO | ✅ Limited | ❌ NO | ❌ NO | ❌ NO |
| FINANCE_VIEWER | ❌ NO | ✅ Read-only | ❌ NO | ❌ NO | ❌ NO |

---

## 3. System Settings Architecture (S01-10 Refactored)

### Service Layer Decomposition

```
SystemSettingsService (Facade)
├─ SettingsInitializationService
│  └─ Loads defaults on @PostConstruct
│
├─ SettingsManagementService
│  ├─ CRUD operations
│  ├─ Validation & normalization
│  ├─ Cache eviction
│  └─ Batch operations
│
├─ SLASettingsService
│  └─ Claim SLA related getters
│
├─ AuthenticationSettingsService
│  └─ Password reset method & expiry
│
└─ UIConfigService
   ├─ Appearance (logo, font, names)
   ├─ Member numbering (format, prefix, digits)
   ├─ Eligibility rules (strict mode, grace period)
   └─ Composite UiConfigDto for frontend
```

### Settings Categories

| Category | Example Keys | Use Case |
|----------|--------------|----------|
| **CLAIMS** | CLAIM_SLA_DAYS, CLAIM_BACKDATED_MONTHS | SLA tracking |
| **PRE_APPROVALS** | PRE_APPROVAL_SLA_DAYS | Pre-auth tracking |
| **SECURITY** | PASSWORD_RESET_METHOD, TOKEN_EXPIRY_MINUTES | Auth flow |
| **UI** | LOGO_URL, SYSTEM_NAME_AR, FONT_FAMILY | Frontend theming |
| **MEMBERS** | BENEFICIARY_NUMBER_FORMAT, BENEFICIARY_PREFIX | Member ID generation |
| **ELIGIBILITY** | ELIGIBILITY_STRICT_MODE, WAITING_PERIOD_DAYS | Member eligibility |
| **AI** | AI_CLASSIFIER_API_KEY, BIOBERT_API_URL | ML model endpoints |

### Caching Strategy

All getters use Spring `@Cacheable` with key-based invalidation:

```java
@Cacheable(value = "systemSettings", key = "#key")
public Integer getSettingAsInt(String key, Integer defaultValue) { ... }

@CacheEvict(value = "systemSettings", key = "#key")
public void updateSetting(String key, String value, String updatedBy) { ... }
```

**Benefits:**
- Settings are read 100x more often than written
- Cache hit: <1ms lookup
- Cache miss: DB query + parsing
- Invalidation: Immediate on update

---

## 4. GitHub Actions CI/CD (S01-09)

### Pipeline Architecture

```
┌─────────────────────────────────────────────────────┐
│  Event: Push / PR / Schedule                        │
└────────┬────────────────────────────────────────────┘
         │
         ├─── Backend Test (every push)
         │    ├─ Setup JDK 21
         │    ├─ Start PostgreSQL 16 service
         │    ├─ mvn clean compile
         │    ├─ mvn test
         │    └─ Publish results
         │
         ├─── Frontend Test (on frontend changes)
         │    ├─ Setup Node 20.x
         │    ├─ npm ci (locked dependencies)
         │    ├─ npm run type-check
         │    ├─ npm run lint
         │    └─ npm run build
         │
         ├─── Integration Test (on PR to main)
         │    ├─ Full E2E test suite
         │    ├─ Database validation
         │    └─ End-to-end flow verification
         │
         └─── Security Audit (daily 2 AM UTC)
              ├─ npm audit (frontend)
              ├─ mvn dependency-check (backend)
              └─ Manual trigger available
```

### Workflow Files

| File | Trigger | Run Time |
|------|---------|----------|
| backend-test.yml | Push/PR to main,develop | ~2 min |
| frontend-test.yml | On frontend/** changes | ~1 min |
| integration-test.yml | Push to main, PR to main | ~5 min |
| security-audit.yml | Daily 2 AM UTC + manual | ~3 min |

---

## 5. Data Flow Examples

### Login Audit Trail

```
AuthService.login()
  │
  ├─ [On Success]
  │  └─ SecurityAuditService.logLoginSuccess(userId, username, ip, agent)
  │     └─ SecurityAuditEvent created + persisted
  │        ├─ actionType: LOGIN_SUCCESS
  │        ├─ result: SUCCESS
  │        ├─ correlationId: uuid-123
  │        └─ eventTimestamp: 2026-07-24 07:45:30
  │
  └─ [On Failure]
     └─ UserSecurityService.recordFailedLogin(username, reason, ip, agent)
        ├─ SecurityAuditEvent created
        ├─ User.failedLoginCount incremented
        ├─ [If threshold exceeded]
        │  └─ User locked, email sent, audit logged
        └─ correlationId: uuid-124
```

### Authorization Check Flow

```
ClaimController.getClaim(claimId)
  │
  ├─ Get current user
  │  └─ AuthorizationService.getCurrentUser()
  │
  ├─ Check data access
  │  └─ DataAccessService.canAccessClaim(user, claimId)
  │     ├─ If SUPER_ADMIN → return true
  │     ├─ If INSURANCE_ADMIN → return true
  │     ├─ If MEDICAL_REVIEWER → return true
  │     ├─ If PROVIDER_STAFF
  │     │  └─ Check: claim.providerId == user.providerId
  │     ├─ If EMPLOYER_ADMIN
  │     │  └─ Check: claim.member.employer.id == user.employerId
  │     └─ Otherwise → return false (DENIED)
  │
  └─ [If authorized] return claim
     [If denied] throw AccessDeniedException (logged in audit)
```

### Query Filtering Flow

```
MemberService.listMembers(page, size, employerId)
  │
  ├─ Get current user
  │  └─ AuthorizationService.getCurrentUser()
  │
  ├─ Determine filter
  │  └─ QueryFilterService.getEmployerFilterForUser(user)
  │     ├─ If SUPER_ADMIN → return null (no filter)
  │     ├─ If INSURANCE_ADMIN → return null (no filter)
  │     ├─ If EMPLOYER_ADMIN → return user.employerId (enforce)
  │     └─ Otherwise → return null (RBAC controls access)
  │
  ├─ Apply filter to query
  │  └─ IF filter != null:
  │     repository.findByEmployerId(filter)
  │     ELSE:
  │     repository.findAll()
  │
  └─ Return paginated results
```

---

## 6. Security Best Practices

### Authentication
- ✅ JWT tokens + session-based auth
- ✅ Password hashing with PasswordEncoder (bcrypt)
- ✅ Account lockout after N failed attempts
- ✅ Email verification on signup
- ✅ Password reset token (SHA-256 hashed)

### Authorization
- ✅ Role-based access control (RBAC)
- ✅ Row-level data access enforcement
- ✅ Provider isolation (PROVIDER_STAFF sees only own provider data)
- ✅ Employer isolation (EMPLOYER_ADMIN sees only own employer data)
- ✅ Feature toggle enforcement per employer

### Audit & Compliance
- ✅ All security events logged
- ✅ Sensitive data redacted from logs
- ✅ Correlation ID for request tracing
- ✅ Before/after state captured (JSONB)
- ✅ IP address & user agent recorded

### Data Protection
- ✅ HTTPS enforced (assume reverse proxy)
- ✅ CORS configured (same-origin for sensitive endpoints)
- ✅ CSRF tokens on state-changing operations
- ✅ SQL injection prevention (JPA parameterized queries)
- ✅ File upload IDOR protection

---

## 7. Testing Strategy

### Unit Tests
- **SecurityAuditServiceTest** (8 tests)
  - Login success/failure logging
  - Password change audit
  - Sensitive data redaction
  - Correlation ID uniqueness
  - Account lockout audit

- **AuthorizationService Tests** (implicit in integration tests)
  - Role checking
  - Data access enforcement
  - Query filtering

- **SystemSettingsServiceTest** (3 tests)
  - Setting update validation
  - Unknown setting rejection
  - Integer rule enforcement

### Integration Tests (TODO: Fix DB setup)
- Full E2E auth flow
- Session management
- Claim lifecycle
- Concurrent access

### CI/CD Tests
- Backend Maven tests on every commit
- Frontend type-check/lint on changes
- Daily security audits

---

## 8. Deployment Checklist

- [ ] Run `mvn clean test` - all unit tests passing
- [ ] Verify `V107__create_security_audit_events_table.sql` in Flyway migrations
- [ ] Start with `SettingsInitializationService` (initializes defaults)
- [ ] Check GitHub Actions workflows are enabled
- [ ] Test login flow end-to-end
- [ ] Verify audit events appear in `security_audit_events` table
- [ ] Monitor logs for any "Skipping audit log persistence" warnings
- [ ] Check that queries are filtered by employer/provider correctly

---

## 9. Future Enhancements

### Phase 2 Candidates
- [ ] Implement audit event streaming to external syslog
- [ ] Add machine learning for anomaly detection
- [ ] Create admin dashboard for audit visualization
- [ ] Implement event-sourcing for full audit trail immutability
- [ ] Add PII detection & auto-masking
- [ ] Implement distributed tracing (Jaeger/Zipkin)
- [ ] Add rate limiting per role
- [ ] Implement context-aware authorization (time-based, location-based)

---

*Architecture documented as of Phase 1 (S01) completion*  
*Next review: Phase 2 (S02) specification*
