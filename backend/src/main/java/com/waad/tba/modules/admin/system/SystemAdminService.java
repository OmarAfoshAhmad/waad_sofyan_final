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

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    private void requireNonProductionProfile(String operation) {
        if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
            log.error("⛔ Blocked destructive admin operation '{}' — active profile is '{}'", operation, activeProfile);
            throw new BusinessRuleException(
                    "هذه العملية غير متاحة في بيئة الإنتاج: " + operation);
        }
    }

    @Transactional
    public ApiResponse<Void> resetTestData() {
        requireNonProductionProfile("resetTestData");
        log.warn("Resetting test data (excluding RBAC tables)...");
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
