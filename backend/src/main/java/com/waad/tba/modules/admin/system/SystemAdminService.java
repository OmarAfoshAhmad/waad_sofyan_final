package com.waad.tba.modules.admin.system;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAdminService {

    private final ClaimRepository claimRepository;
    private final VisitRepository visitRepository;
    private final MemberRepository memberRepository;
    private final EmployerRepository employerRepository;

    @Transactional
    public ApiResponse<Void> resetTestData() {
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

    @Transactional
    public ApiResponse<Void> seedSampleData() {
        log.warn("seedSampleData() available for future implementation");
        return ApiResponse.success("Seed data ready for implementation", null);
    }

    // RBAC initialization methods removed - now handled by RbacDataInitializer
}
