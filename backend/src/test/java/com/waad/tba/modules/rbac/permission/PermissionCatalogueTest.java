package com.waad.tba.modules.rbac.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.waad.tba.security.rbac.SystemRole;

class PermissionCatalogueTest {
    @Test
    void everyPermissionCodeIsUniqueAndHasStableAuthority() {
        assertEquals(SystemPermission.values().length,
                Arrays.stream(SystemPermission.values()).map(Enum::name).distinct().count());
        Arrays.stream(SystemPermission.values()).forEach(permission -> {
            assertEquals("PERM_" + permission.name(), permission.authority());
            assertFalse(permission.displayNameAr().isBlank());
        });
    }

    @Test
    void superAdminTemplateContainsTheWholeCatalogue() {
        assertEquals(SystemPermission.values().length,
                RolePermissionDefaults.forRole(SystemRole.SUPER_ADMIN).size());
        assertTrue(RolePermissionDefaults.forRole(SystemRole.DATA_ENTRY)
                .contains(SystemPermission.MEMBER_CREATE));
        assertFalse(RolePermissionDefaults.forRole(SystemRole.DATA_ENTRY)
                .contains(SystemPermission.MEMBER_HARD_DELETE));
    }

    @Test
    void operationalTemplatesPreserveIntendedMemberAndEmployerWorkflows() {
        var employerAdmin = RolePermissionDefaults.forRole(SystemRole.EMPLOYER_ADMIN);
        assertTrue(employerAdmin.containsAll(java.util.Set.of(
                SystemPermission.MEMBER_VIEW,
                SystemPermission.MEMBER_CREATE,
                SystemPermission.MEMBER_EDIT_IDENTITY,
                SystemPermission.MEMBER_CHANGE_STATUS,
                SystemPermission.MEMBER_TRANSFER_EMPLOYER,
                SystemPermission.MEMBER_EXPORT,
                SystemPermission.MEMBER_LIMIT_VIEW,
                SystemPermission.EMPLOYER_VIEW)));
        assertFalse(employerAdmin.contains(SystemPermission.MEMBER_HARD_DELETE));
        assertFalse(employerAdmin.contains(SystemPermission.MEMBER_IMPORT));

        assertTrue(RolePermissionDefaults.forRole(SystemRole.PROVIDER_STAFF)
                .contains(SystemPermission.EMPLOYER_VIEW));
        assertTrue(RolePermissionDefaults.forRole(SystemRole.PROVIDER_STAFF)
                .contains(SystemPermission.MEMBER_LIMIT_VIEW));
        assertTrue(RolePermissionDefaults.forRole(SystemRole.MEDICAL_REVIEWER)
                .contains(SystemPermission.EMPLOYER_VIEW));
        assertTrue(RolePermissionDefaults.forRole(SystemRole.ACCOUNTANT)
                .contains(SystemPermission.EMPLOYER_VIEW));
        assertTrue(RolePermissionDefaults.forRole(SystemRole.FINANCE_VIEWER)
                .contains(SystemPermission.EMPLOYER_VIEW));
    }

    @Test
    void migrationSeedsEveryVersionControlledPermission() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V191__normalized_role_and_user_permissions.sql"))
                + Files.readString(Path.of("src/main/resources/db/migration/"
                        + "V192__align_operational_role_permission_templates.sql"))
                + Files.readString(Path.of("src/main/resources/db/migration/"
                        + "V193__preauth_command_permissions.sql"));
        Arrays.stream(SystemPermission.values())
                .forEach(permission -> assertTrue(sql.contains("'" + permission.name() + "'"),
                        () -> "Migration does not seed " + permission.name()));
    }
}
