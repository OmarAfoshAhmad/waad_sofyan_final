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
    void migrationSeedsEveryVersionControlledPermission() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V191__normalized_role_and_user_permissions.sql"));
        Arrays.stream(SystemPermission.values())
                .forEach(permission -> assertTrue(sql.contains("'" + permission.name() + "'"),
                        () -> "Migration does not seed " + permission.name()));
    }
}
