package com.waad.tba.modules.admin.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.audit.SecurityAuditEvent;
import com.waad.tba.security.audit.SecurityAuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SECTION_02 finding: resetTestData() performs an unconditional
 * {@code deleteAll()} on core business tables, gated only by the
 * SUPER_ADMIN role with no environment safeguard — a single misdirected
 * request against a production database would be irreversible. A profile
 * check now fails closed outside dev/test environments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAdminService {

    private final ClaimRepository claimRepository;
    private final VisitRepository visitRepository;
    private final MemberRepository memberRepository;
    private final EmployerRepository employerRepository;
    private final SecurityAuditService securityAuditService;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    private void requireNonProductionProfile(String operation) {
        // spring.profiles.active can be a comma-separated list (e.g.
        // "prod,metrics") — comparing the whole raw string against "prod"
        // silently passed for any multi-profile activation, re-opening
        // deleteAll() in production. Check each token individually.
        boolean isProduction = activeProfile != null && java.util.Arrays.stream(activeProfile.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (isProduction) {
            log.error("⛔ Blocked destructive admin operation '{}' — active profile is '{}'", operation, activeProfile);
            throw new BusinessRuleException(
                    "هذه العملية غير متاحة في بيئة الإنتاج: " + operation);
        }
    }

    @Transactional
    public ApiResponse<Void> resetTestData() {
        requireNonProductionProfile("resetTestData");
        log.warn("Resetting test data (excluding RBAC tables)...");

        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actorUsername = auth != null ? auth.getName() : "system";
        // A destructive database wipe must leave a queryable forensic
        // record independent of application logs, which rotate/expire —
        // previously this was only a log.warn line.
        securityAuditService.logSecurityEvent(null, actorUsername,
                SecurityAuditEvent.AuditActionType.CONFIGURATION_CHANGED,
                "SYSTEM", null, "resetTestData", null, null,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Destructive test-data reset: claims, visits, members, employers deleted",
                null, null);

        claimRepository.deleteAll();
        visitRepository.deleteAll();
        memberRepository.deleteAll();
        employerRepository.deleteAll();
        log.info("Test data cleared.");
        return ApiResponse.success("Test data cleared", null);
    }

    @Transactional
    public ApiResponse<Void> initDefaults() {
        log.info("Initializing default system data...");
        // Default employer should be created via seed data or migration
        // RBAC initialization moved to RbacDataInitializer
        return ApiResponse.success("Defaults initialized", null);
    }

    /**
     * Not implemented — historically documented (controller Javadoc) as
     * inserting sample employer/member/claim/visit data, but the schema it
     * referenced (separate insurance-company/reviewer-company tables) was
     * removed by the employer/company consolidation refactor. Left as an
     * explicit no-op with an honest response rather than a misleading
     * "success" that inserts nothing, until this is either implemented
     * against the current schema or removed.
     */
    @Transactional
    public ApiResponse<Void> seedSampleData() {
        requireNonProductionProfile("seedSampleData");
        log.warn("seedSampleData() is not implemented against the current schema — no data was inserted.");
        return ApiResponse.success("No sample data was inserted (not yet implemented)", null);
    }

    // RBAC initialization methods removed - now handled by RbacDataInitializer
}
