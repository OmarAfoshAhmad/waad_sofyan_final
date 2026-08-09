package com.waad.tba.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.TestingAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;

class MedicalReviewRoleHierarchyTest {

    private final RoleHierarchy hierarchy = new MethodSecurityConfig().roleHierarchy();

    @Test
    void departmentHeadInheritsMedicalReviewerAuthority() {
        var reachable = hierarchy.getReachableGrantedAuthorities(
                new TestingAuthenticationToken("head", "n/a", "ROLE_MEDICAL_REVIEW_HEAD")
                        .getAuthorities()).stream().map(authority -> authority.getAuthority()).toList();

        assertThat(reachable)
                .contains("ROLE_MEDICAL_REVIEW_HEAD", "ROLE_MEDICAL_REVIEWER");
    }

    @Test
    void medicalReviewerDoesNotInheritDepartmentHeadAuthority() {
        var reachable = hierarchy.getReachableGrantedAuthorities(
                new TestingAuthenticationToken("reviewer", "n/a", "ROLE_MEDICAL_REVIEWER")
                        .getAuthorities()).stream().map(authority -> authority.getAuthority()).toList();

        assertThat(reachable)
                .contains("ROLE_MEDICAL_REVIEWER")
                .doesNotContain("ROLE_MEDICAL_REVIEW_HEAD");
    }
}
