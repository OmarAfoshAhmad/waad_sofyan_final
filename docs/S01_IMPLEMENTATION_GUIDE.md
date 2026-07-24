# S01 Implementation Guide - How to Use the New Services

**For:** Developers integrating S01 changes (Security Audit, CI/CD, Refactored Services)  
**Date:** 2026-07-24  
**Version:** 1.0

---

## Table of Contents

1. [Using the Audit Service](#1-using-the-audit-service)
2. [Using Authorization Services](#2-using-authorization-services)
3. [Using Settings Services](#3-using-settings-services)
4. [Testing Your Code](#4-testing-your-code)
5. [Troubleshooting](#5-troubleshooting)

---

## 1. Using the Audit Service

### What It Does

Logs security events (logins, password changes, access denials, etc.) for compliance and forensics.

### Dependency Injection

```java
@Service
public class MyService {
    private final SecurityAuditService auditService;
    
    public MyService(SecurityAuditService auditService) {
        this.auditService = auditService;
    }
}
```

### Convenience Methods

#### Log Login Success

```java
auditService.logLoginSuccess(
    userId,              // Long
    username,            // String
    clientIp,            // String (can be null)
    userAgent            // String (can be null)
);

// Creates audit event:
// - actionType: LOGIN_SUCCESS
// - result: SUCCESS
// - actor: the logged-in user
// - correlationId: auto-generated UUID
```

#### Log Login Failure

```java
auditService.logLoginFailure(
    username,            // String (attempted username)
    clientIp,            // String
    userAgent,           // String
    failureReason        // String (e.g., "Invalid password")
);

// Creates audit event:
// - actionType: LOGIN_FAILED
// - result: DENIED
// - note: userId is null (user doesn't exist or auth failed)
```

#### Log Password Changed

```java
auditService.logPasswordChanged(
    userId,              // Long (whose password changed)
    username,            // String
    clientIp,            // String
    userAgent            // String
);

// Creates audit event:
// - actionType: PASSWORD_CHANGED
// - result: SUCCESS
```

#### Log Account Locked

```java
auditService.logAccountLocked(
    userId,              // Long
    username,            // String
    clientIp             // String
);

// Creates audit event:
// - actionType: ACCOUNT_LOCKED
// - result: SUCCESS
```

#### Log File Access Denied

```java
auditService.logFileAccessDenied(
    userId,              // Long (who tried to access)
    username,            // String
    fileName,            // String (what they tried to access)
    reason               // String (why it was denied)
);

// Creates audit event:
// - actionType: FILE_ACCESS_DENIED
// - targetType: "FILE"
// - targetIdentifier: fileName
// - result: DENIED
```

### Generic Method for Custom Events

```java
auditService.logSecurityEvent(
    actorId,             // Long (who did it)
    actorUsername,       // String
    actionType,          // AuditActionType (enum)
    targetType,          // String (e.g., "CLAIM", "MEMBER", "FILE")
    targetId,            // Long (what entity)
    targetIdentifier,    // String (human-readable identifier)
    requestIp,           // String
    userAgent,           // String
    result,              // AuditResult (enum: SUCCESS, DENIED, ERROR)
    safeReason,          // String (why it was denied/errored)
    beforeState,         // String (JSON before)
    afterState           // String (JSON after)
);
```

### Best Practices

✅ **DO:**
- Log all security-relevant events (login, password change, permission denied)
- Include IP address and user agent (helps with forensics)
- Use enum AuditActionType and AuditResult
- Log BEFORE you modify data (so beforeState is accurate)
- Keep reasons brief but informative

❌ **DON'T:**
- Include passwords, API keys, or secrets (they're auto-redacted, but don't put them in)
- Log at INFO level (audit logging is at DEBUG level in logs)
- Assume sensitive data is redacted without reviewing code
- Forget to log access denials (they're important for security!)

---

## 2. Using Authorization Services

### Service Hierarchy

```
AuthorizationService (Facade)
├─ RoleService          (Check if user is admin, provider, etc.)
├─ DataAccessService    (Can user access this specific resource?)
├─ QueryFilterService   (What should this query return for this user?)
└─ FeatureToggleService (Can employer enable this feature?)
```

### Get Current User

```java
@Service
public class ClaimService {
    private final AuthorizationService authService;
    
    public void processClaim(Long claimId) {
        // Get the currently logged-in user from security context
        User currentUser = authService.getCurrentUser();
        
        if (currentUser == null) {
            throw new AccessDeniedException("Not authenticated");
        }
    }
}
```

### Check User Role

```java
if (authService.isSuperAdmin(currentUser)) {
    // User can do anything
}

if (authService.isInsuranceAdmin(currentUser)) {
    // User has full data access
}

if (authService.isEmployerAdmin(currentUser)) {
    // User is restricted to their employer's data
    Long employerId = currentUser.getEmployerId();
}

if (authService.isProvider(currentUser)) {
    // User is a healthcare provider
    Long providerId = currentUser.getProviderId();
}

if (authService.isReviewer(currentUser)) {
    // User reviews claims
}
```

### Check Data Access

```java
@GetMapping("/{claimId}")
public ResponseEntity<ClaimDto> getClaim(@PathVariable Long claimId) {
    User currentUser = authService.requireCurrentUser(); // throws if not authenticated
    
    // Check if user can access this specific claim
    if (!authService.canAccessClaim(currentUser, claimId)) {
        throw new AccessDeniedException("You cannot access this claim");
    }
    
    // Safe to return claim
    return ResponseEntity.ok(claimService.findById(claimId));
}
```

### Filter Queries by User Scope

```java
@GetMapping("/members")
public ResponseEntity<List<MemberDto>> listMembers(@RequestParam(defaultValue = "1") int page) {
    User currentUser = authService.getCurrentUser();
    
    // Get the employer filter for this user
    // Returns: user's employerId if EMPLOYER_ADMIN, null otherwise
    Long employerFilter = authService.getEmployerFilterForUser(currentUser);
    
    List<Member> members;
    if (employerFilter != null) {
        // User is EMPLOYER_ADMIN - only return their employer's members
        members = memberRepository.findByEmployerId(employerFilter, PageRequest.of(page - 1, 20));
    } else {
        // User is SUPER_ADMIN or ACCOUNTANT - return all members (RBAC controls access)
        members = memberRepository.findAll(PageRequest.of(page - 1, 20));
    }
    
    return ResponseEntity.ok(members.stream().map(MemberDto::from).toList());
}
```

### Check if User Can Modify Resource

```java
@PutMapping("/claims/{claimId}")
public ResponseEntity<ClaimDto> updateClaim(
    @PathVariable Long claimId,
    @RequestBody ClaimUpdateDto dto
) {
    User currentUser = authService.requireCurrentUser();
    
    // Check if user can modify (not just read) this claim
    if (!authService.canModifyClaim(currentUser, claimId)) {
        throw new AccessDeniedException("You cannot modify this claim");
    }
    
    return ResponseEntity.ok(claimService.update(claimId, dto));
}
```

### Check Employer Feature Flags

```java
@GetMapping("/members")
public ResponseEntity<List<MemberDto>> listMembers() {
    User currentUser = authService.getCurrentUser();
    
    // Check if this employer can view members
    if (!authService.canEmployerViewMembers(currentUser)) {
        throw new AccessDeniedException("Your organization is not permitted to view members");
    }
    
    return ResponseEntity.ok(memberService.listAll());
}
```

---

## 3. Using Settings Services

### Get System Settings

```java
@Service
public class ClaimProcessingService {
    private final SystemSettingsService settingsService;
    
    public void processClaim(Long claimId) {
        // Get SLA (Service Level Agreement) days
        int slaaDays = settingsService.getClaimSlaDays();
        // Returns: 10 (default) or configured value
        
        LocalDateTime deadline = claim.getCreatedAt().plusDays(slaDays);
    }
}
```

### Common Getters

```java
// SLA Settings
int claimSlaDays = settingsService.getClaimSlaDays();              // Default: 10
int preApprovalSlaDays = settingsService.getPreApprovalSlaDays(); // Default: 3
int backdatedMonths = settingsService.getClaimBackdatedMonths();   // Default: 3

// Authentication Settings
String method = settingsService.getPasswordResetMethod();          // "TOKEN" or "OTP"
int tokenExpiry = settingsService.getPasswordResetTokenExpiryMinutes(); // Default: 60
int otpExpiry = settingsService.getPasswordResetOtpExpiryMinutes();     // Default: 10
int otpLength = settingsService.getPasswordResetOtpLength();            // Default: 6

// UI Settings
String logoUrl = settingsService.getLogoUrl();
String fontFamily = settingsService.getFontFamily();               // Default: "Tajawal"
int fontSize = settingsService.getFontSizeBase();                  // Default: 14
String systemNameAr = settingsService.getSystemNameAr();           // Arabic name
String systemNameEn = settingsService.getSystemNameEn();           // English name

// Member Numbering
String format = settingsService.getBeneficiaryNumberFormat();      // "PREFIX_SEQUENCE"
String prefix = settingsService.getBeneficiaryNumberPrefix();      // "MEM"
int digits = settingsService.getBeneficiaryNumberDigits();         // Default: 6

// Eligibility
boolean strictMode = settingsService.isEligibilityStrictMode();    // Default: false
int waitingPeriod = settingsService.getWaitingPeriodDaysDefault();  // Default: 30
int gracePeriod = settingsService.getEligibilityGracePeriodDays(); // Default: 7
```

### Get All UI Settings at Once (for Frontend)

```java
@GetMapping("/api/ui-config")
public ResponseEntity<UIConfigService.UiConfigDto> getUiConfig() {
    // Returns composite DTO with all UI-relevant settings
    return ResponseEntity.ok(settingsService.getUiConfig());
    
    // Result:
    // {
    //   "logoUrl": "https://...",
    //   "fontFamily": "Tajawal",
    //   "fontSizeBase": 14,
    //   "systemNameAr": "نظام واعد الطبي",
    //   "systemNameEn": "TBA WAAD System"
    // }
}
```

### Update Settings (Admin Only)

```java
@PostMapping("/admin/settings/{key}")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public ResponseEntity<SystemSettingDto> updateSetting(
    @PathVariable String key,
    @RequestBody String value,
    Principal principal
) {
    // Update setting and evict from cache automatically
    SystemSettingDto result = settingsService.updateSetting(key, value, principal.getName());
    
    return ResponseEntity.ok(result);
}
```

### Reset Setting to Default

```java
@DeleteMapping("/admin/settings/{key}")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public ResponseEntity<Void> resetSetting(
    @PathVariable String key,
    Principal principal
) {
    settingsService.resetToDefault(key, principal.getName());
    return ResponseEntity.noContent().build();
}
```

---

## 4. Testing Your Code

### Testing Authorization

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    
    @Mock
    private AuthorizationService authService;
    
    @InjectMocks
    private MyService myService;
    
    @Test
    void testAccessDeniedForNonAdmin() {
        User normalUser = User.builder()
            .id(1L)
            .username("user1")
            .userType("DATA_ENTRY")
            .build();
        
        when(authService.isSuperAdmin(normalUser)).thenReturn(false);
        
        assertThrows(AccessDeniedException.class, () -> {
            myService.deleteAllUsers();
        });
    }
    
    @Test
    void testAccessAllowedForAdmin() {
        User adminUser = User.builder()
            .id(2L)
            .username("admin")
            .userType("SUPER_ADMIN")
            .build();
        
        when(authService.isSuperAdmin(adminUser)).thenReturn(true);
        
        myService.deleteAllUsers(); // Should not throw
    }
}
```

### Testing Audit Logging

```java
@Test
void testLoginSuccessLogsAuditEvent() {
    User user = User.builder()
        .id(1L)
        .username("john")
        .build();
    
    SecurityAuditEvent capturedEvent = ArgumentCaptor.forClass(SecurityAuditEvent.class);
    
    // Call login
    authService.login("john", "password");
    
    // Verify audit was logged
    verify(auditEventRepository).save(capturedEvent.capture());
    
    SecurityAuditEvent event = capturedEvent.getValue();
    assertEquals(SecurityAuditEvent.AuditActionType.LOGIN_SUCCESS, event.getActionType());
    assertEquals(SecurityAuditEvent.AuditResult.SUCCESS, event.getResult());
    assertNotNull(event.getCorrelationId());
}
```

### Testing Settings

```java
@Test
void testGetClaimSlaDays() {
    int slaDays = settingsService.getClaimSlaDays();
    
    assertEquals(10, slaDays); // Assuming default or mocked value
}

@Test
void testSettingCacheEvictionOnUpdate() {
    // First call - cache miss
    int before = settingsService.getClaimSlaDays();
    
    // Update setting
    settingsService.updateClaimSlaDays(15, "admin");
    
    // Second call - should return new value (cache was evicted)
    int after = settingsService.getClaimSlaDays();
    
    assertEquals(15, after);
}
```

---

## 5. Troubleshooting

### Q: Audit events not being logged
**A:** Check:
- Is SecurityAuditService injected correctly?
- Did you call the right method (logLoginSuccess, logPasswordChanged, etc.)?
- Check logs for "Skipping audit log persistence due to schema mismatch" - means table doesn't exist
- Verify Flyway migration V107 has run: `SELECT COUNT(*) FROM security_audit_events`

### Q: Authorization check always fails
**A:** Check:
- Is SecurityContextHolder populated? (Should be by Spring Security filter)
- Did you call `requireCurrentUser()` instead of `getCurrentUser()`?
- Is the user's role correctly set in User entity?
- Did you check the role hierarchy? (SUPER_ADMIN > INSURANCE_ADMIN > others)

### Q: Settings not being read
**A:** Check:
- Did SettingsInitializationService run? (Should happen at startup)
- Did you call the right getter? (getClaimSlaDays, not getClaimSlaMinutes)
- Is the setting name correct? (CLAIM_SLA_DAYS not CLAIM_SLA)
- Check database: `SELECT * FROM system_setting WHERE setting_key = 'CLAIM_SLA_DAYS'`

### Q: Settings changes not taking effect
**A:** Check:
- Did you call `updateSetting()` instead of just updating the database?
- Is the cache being evicted? (Use @CacheEvict)
- Try clearing cache manually in admin panel or calling `resetToDefault()`
- Restart application if cache is stuck

### Q: "Cannot inject UserSecurityService" error
**A:** Check:
- Did you create the three new services (PasswordManagementService, EmailVerificationService, LoginSecurityService)?
- Are they annotated with @Service?
- Are all their dependencies being injected?
- Did you remove old UserSecurityService code completely?

### Q: Test fails with "Cannot invoke... because... is null"
**A:** Check:
- Are you using @Mock for external dependencies?
- Are you using @InjectMocks for the class under test?
- Did you add @ExtendWith(MockitoExtension.class) to the test class?
- Are all dependencies mocked before calling the method?

---

## References

- **Audit Service:** `com.waad.tba.security.audit.SecurityAuditService`
- **Authorization Services:** `com.waad.tba.security.*`
- **Settings Services:** `com.waad.tba.common.service.*`
- **Test Examples:** `src/test/java/com/waad/tba/`

---

*Last Updated: 2026-07-24*  
*For S01 implementation and integration*
