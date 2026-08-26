package com.waad.tba.modules.rbac.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.dto.PermissionOverrideRequest;
import com.waad.tba.modules.rbac.permission.dto.PermissionOverrideRequest.OverrideMode;
import com.waad.tba.modules.rbac.permission.dto.RoleTemplateUpdateRequest;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PermissionAdministrationIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired UserRepository userRepository;
    @Autowired PermissionAdministrationService administrationService;
    @Autowired EffectivePermissionService effectivePermissionService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokeWinsOverRoleAndWritesImmutableAudit() {
        User actor = saveUser("SUPER_ADMIN");
        User target = saveUser("DATA_ENTRY");
        authenticate(actor);

        var snapshot = administrationService.applyOverrides(target.getId(), List.of(
                new PermissionOverrideRequest("MEMBER_CREATE", OverrideMode.REVOKE,
                        "فصل واجبات مدخل البيانات")));

        assertThat(snapshot.rolePermissions()).contains(SystemPermission.MEMBER_CREATE);
        assertThat(snapshot.effectivePermissions()).doesNotContain(SystemPermission.MEMBER_CREATE);
        assertThat(userRepository.findById(target.getId()).orElseThrow().getAuthorizationVersion()).isEqualTo(1L);
        Integer audits = jdbcTemplate.queryForObject("""
                select count(*) from rbac_permission_change_audit
                 where target_user_id=? and permission_code='MEMBER_CREATE'
                """, Integer.class, target.getId());
        assertThat(audits).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update rbac_permission_change_audit set reason='tampered'
                 where target_user_id=?
                """, target.getId())).hasMessageContaining("append-only");
    }

    @Test
    void delegatorCannotGrantPermissionTheyDoNotOwn() {
        User actor = saveUser("DATA_ENTRY");
        User target = saveUser("DATA_ENTRY");
        authenticate(actor);

        assertThatThrownBy(() -> administrationService.applyOverrides(target.getId(), List.of(
                new PermissionOverrideRequest("CLAIM_APPROVE", OverrideMode.GRANT,
                        "منح غير مشروع"))))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(effectivePermissionService.resolve(target)).doesNotContain(SystemPermission.CLAIM_APPROVE);
    }

    @Test
    void roleTemplateReplacementIsAuditedAndRefreshesAffectedUsers() {
        User actor = saveUser("SUPER_ADMIN");
        User affected = saveUser("FINANCE_VIEWER");
        authenticate(actor);
        List<String> original = administrationService.roleTemplates().stream()
                .filter(role -> role.roleCode().equals("FINANCE_VIEWER"))
                .findFirst().orElseThrow().permissionCodes();
        List<String> changed = new java.util.ArrayList<>(original);
        changed.add("OPERATIONAL_REPORT_VIEW");

        try {
            var updated = administrationService.replaceRolePermissions("FINANCE_VIEWER",
                    new RoleTemplateUpdateRequest(changed, "حاجة تشغيلية موثقة"));

            assertThat(updated.permissionCodes()).contains("OPERATIONAL_REPORT_VIEW");
            assertThat(effectivePermissionService.resolve(
                    userRepository.findById(affected.getId()).orElseThrow()))
                    .contains(SystemPermission.OPERATIONAL_REPORT_VIEW);
            assertThat(userRepository.findById(affected.getId()).orElseThrow().getAuthorizationVersion())
                    .isEqualTo(1L);
            Integer audits = jdbcTemplate.queryForObject("""
                    select count(*) from rbac_permission_change_audit
                     where target_type='ROLE' and target_role_code='FINANCE_VIEWER'
                       and permission_code='OPERATIONAL_REPORT_VIEW' and new_effect='GRANT'
                    """, Integer.class);
            assertThat(audits).isEqualTo(1);
        } finally {
            administrationService.replaceRolePermissions("FINANCE_VIEWER",
                    new RoleTemplateUpdateRequest(original, "إعادة قالب الاختبار"));
        }
    }

    @Test
    void superAdminTemplateCannotBeChanged() {
        User actor = saveUser("SUPER_ADMIN");
        authenticate(actor);

        assertThatThrownBy(() -> administrationService.replaceRolePermissions("SUPER_ADMIN",
                new RoleTemplateUpdateRequest(List.of("USER_VIEW"), "تقليص غير مسموح")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ثابت");
    }

    private User saveUser(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder()
                .username("rbac-" + role.toLowerCase() + "-" + suffix)
                .password("encoded")
                .fullName("RBAC Test " + suffix)
                .email("rbac-" + suffix + "@example.test")
                .userType(role)
                .active(true)
                .build());
    }

    private void authenticate(User actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getUsername(), null, List.of()));
    }
}
