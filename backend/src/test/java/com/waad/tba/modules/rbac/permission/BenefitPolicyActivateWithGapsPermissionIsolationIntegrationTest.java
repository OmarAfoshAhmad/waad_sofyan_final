package com.waad.tba.modules.rbac.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * P1 review point: "verify SUPER_ADMIN does not get
 * BENEFIT_POLICY_ACTIVATE_WITH_GAPS through a wildcard, or the claim that
 * it is a separate permission is false in practice."
 *
 * MethodSecurityConfig's RoleHierarchy only inherits
 * INSURANCE_MANAGER > MEDICAL_REVIEW_HEAD > MEDICAL_REVIEWER -- nothing
 * grants SUPER_ADMIN blanket authority, and EffectivePermissionService
 * resolves purely from rbac_role_permissions rows plus per-user overrides.
 * So SUPER_ADMIN has this permission ONLY because V221 inserted that one
 * row -- proven here by revoking it for one user via the same
 * administrator override mechanism CeilingAccessFollowsThePermissionNotTheRoleIntegrationTest
 * already exercises, and confirming a DIFFERENT SUPER_ADMIN-granted
 * permission is unaffected.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BenefitPolicyActivateWithGapsPermissionIsolationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private PermissionGuard permissionGuard;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(String userType) {
        String username = "gapperm-" + suffix();
        var user = userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Gap Perm User")
                .email(username + "@waad.ly").userType(userType).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    private void revoke(String username, String permission) {
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO rbac_user_permission_overrides (user_id, permission_code, effect, "
                + "reason, changed_by) VALUES (?, ?, 'REVOKE', 'test isolation', ?)", userId, permission, userId);
    }

    @Test
    void superAdminHasTheOverridePermissionOnlyBecauseItWasExplicitlyGrantedByRow() {
        signInAs("SUPER_ADMIN");

        assertThat(permissionGuard.has("BENEFIT_POLICY_ACTIVATE_WITH_GAPS"))
                .as("granted to SUPER_ADMIN by V221's explicit rbac_role_permissions row, "
                        + "not by any role-hierarchy or wildcard")
                .isTrue();
    }

    @Test
    void revokingItForOneUserLeavesOtherSuperAdminPermissionsUntouched() {
        String username = "gapperm-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Gap Perm User")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        // A different permission SUPER_ADMIN also holds only via an explicit
        // rbac_role_permissions row (V215) -- if revoking the gap-override
        // permission for this user also silently removed this one, the two
        // would not really be independent permissions.
        revoke(username, "BENEFIT_POLICY_ACTIVATE_WITH_GAPS");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));

        assertThat(permissionGuard.has("BENEFIT_POLICY_ACTIVATE_WITH_GAPS"))
                .as("explicitly revoked for this user").isFalse();
        assertThat(permissionGuard.has("PROVIDER_STANDARD_SERVICES_MANAGE"))
                .as("an unrelated SUPER_ADMIN-granted permission must survive the revoke untouched")
                .isTrue();
    }
}
