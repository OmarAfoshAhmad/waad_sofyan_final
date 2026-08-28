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

    /**
     * Every migration, not three named ones.
     *
     * The list used to be hard-coded, so adding a permission in a new
     * migration meant editing this test to keep it passing -- and a guard you
     * have to edit to satisfy is a guard that gets edited rather than
     * satisfied. Reading the whole folder means a permission the enum declares
     * and no migration seeds fails on its own.
     */
    @Test
    void migrationSeedsEveryVersionControlledPermission() throws Exception {
        StringBuilder sql = new StringBuilder();
        try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path migration : migrations.filter(f -> f.toString().endsWith(".sql")).toList()) {
                sql.append(Files.readString(migration)).append('\n');
            }
        }
        String allMigrations = sql.toString();
        Arrays.stream(SystemPermission.values())
                .forEach(permission -> assertTrue(allMigrations.contains("'" + permission.name() + "'"),
                        () -> "No migration seeds " + permission.name()));
    }
}
