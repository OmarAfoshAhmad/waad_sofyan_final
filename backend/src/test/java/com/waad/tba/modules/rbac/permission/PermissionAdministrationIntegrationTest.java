package com.waad.tba.modules.rbac.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.permission.dto.ManagedUserUpdateRequest;
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
    @Autowired ManagedUserAccessService managedUserAccessService;
    @Autowired EffectivePermissionService effectivePermissionService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired com.waad.tba.modules.rbac.service.UserService userService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operationalTemplateMigrationPreservesMemberAndEmployerWorkflows() {
        List<String> employerAdmin = jdbcTemplate.queryForList("""
                select permission_code from rbac_role_permissions
                 where role_code='EMPLOYER_ADMIN'
                """, String.class);
        assertThat(employerAdmin).contains(
                "MEMBER_VIEW", "MEMBER_CREATE", "MEMBER_EDIT_IDENTITY",
                "MEMBER_CHANGE_STATUS", "MEMBER_TRANSFER_EMPLOYER",
                "MEMBER_EXPORT", "MEMBER_LIMIT_VIEW", "EMPLOYER_VIEW");
        assertThat(employerAdmin).doesNotContain("MEMBER_HARD_DELETE", "MEMBER_IMPORT");

        Integer missingEmployerView = jdbcTemplate.queryForObject("""
                select count(*) from (values
                  ('DATA_ENTRY'), ('EMPLOYER_ADMIN'), ('PROVIDER_STAFF'),
                  ('MEDICAL_REVIEWER'), ('MEDICAL_REVIEW_HEAD'), ('INSURANCE_MANAGER'),
                  ('ACCOUNTANT'), ('FINANCE_VIEWER')
                ) expected(role_code)
                where not exists (
                  select 1 from rbac_role_permissions actual
                   where actual.role_code=expected.role_code
                     and actual.permission_code='EMPLOYER_VIEW'
                )
                """, Integer.class);
        assertThat(missingEmployerView).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from rbac_role_permissions
                 where role_code='SUPER_ADMIN'
                   and permission_code in ('MEMBER_LIMIT_VIEW','MEMBER_REINSTATE_TERMINATED')
                """, Integer.class)).isEqualTo(2);
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

    @Test
    void managedUpdateRollsBackIdentityRoleAndScopeWhenPermissionUpdateFails() {
        User actor = saveUser("SUPER_ADMIN");
        User target = saveUser("FINANCE_VIEWER");
        authenticate(actor);
        String originalName = target.getFullName();

        UserUpdateDto update = UserUpdateDto.builder()
                .username(target.getUsername())
                .fullName("Name that must roll back")
                .email(target.getEmail())
                .active(true)
                .userType("ACCOUNTANT")
                .build();

        assertThatThrownBy(() -> managedUserAccessService.update(target.getId(),
                new ManagedUserUpdateRequest(update, List.of(
                        new PermissionOverrideRequest("NOT_A_REAL_PERMISSION", OverrideMode.GRANT,
                                "إجبار فشل الجزء الثاني")), "اختبار الذرية")))
                .isInstanceOf(IllegalArgumentException.class);

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo(originalName);
        assertThat(reloaded.getUserType()).isEqualTo("FINANCE_VIEWER");
        assertThat(reloaded.getAuthorizationVersion()).isZero();
    }

    @Test
    void delegatedManagerCannotCreateRoleWhoseTemplateExceedsTheirPermissions() {
        User actor = saveUser("DATA_ENTRY");
        authenticate(actor);
        jdbcTemplate.update("""
                insert into rbac_user_permission_overrides
                    (user_id,permission_code,effect,reason,changed_by)
                values (?, 'USER_MANAGE', 'GRANT', 'delegated test', ?),
                       (?, 'ROLE_PERMISSION_MANAGE', 'GRANT', 'delegated test', ?)
                """, actor.getId(), actor.getId(), actor.getId(), actor.getId());

        UserCreateDto request = UserCreateDto.builder()
                .username("forbidden-admin-" + UUID.randomUUID().toString().substring(0, 8))
                .fullName("Forbidden administrator")
                .email("forbidden-" + UUID.randomUUID() + "@example.test")
                .password("Strong@Pass123")
                .userType("SUPER_ADMIN")
                .build();

        assertThatThrownBy(() -> managedUserAccessService.create(
                new com.waad.tba.modules.rbac.permission.dto.ManagedUserCreateRequest(request, List.of())))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("صلاحيات لا يملكها");
        assertThat(userRepository.existsByUsernameIgnoreCase(request.getUsername())).isFalse();
    }

    @Test
    void concurrentDemotionsCannotRemoveEveryActiveSuperAdmin() throws Exception {
        jdbcTemplate.update("update users set is_active=false where user_type='SUPER_ADMIN'");
        User first = saveUser("SUPER_ADMIN");
        User second = saveUser("SUPER_ADMIN");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> demoteAfter(start, first));
            var secondResult = executor.submit(() -> demoteAfter(start, second));
            start.countDown();
            boolean a = firstResult.get(20, TimeUnit.SECONDS);
            boolean b = secondResult.get(20, TimeUnit.SECONDS);

            assertThat(java.util.List.of(a, b)).containsExactlyInAnyOrder(true, false);
            assertThat(userRepository.countByUserTypeAndActiveTrue("SUPER_ADMIN")).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean demoteAfter(CountDownLatch start, User target) {
        try {
            start.await(10, TimeUnit.SECONDS);
            userService.update(target.getId(), UserUpdateDto.builder()
                    .username(target.getUsername())
                    .fullName(target.getFullName())
                    .email(target.getEmail())
                    .active(true)
                    .userType("FINANCE_VIEWER")
                    .build(), "اختبار تزامن حماية آخر مدير");
            return true;
        } catch (IllegalArgumentException expected) {
            return false;
        } catch (Exception unexpected) {
            throw new RuntimeException(unexpected);
        }
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
