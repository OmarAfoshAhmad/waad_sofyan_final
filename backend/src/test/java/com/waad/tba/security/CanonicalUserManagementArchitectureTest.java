package com.waad.tba.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CanonicalUserManagementArchitectureTest {

    @Test
    void legacyUserManagementImplementationMustNotExist() {
        Path root = Path.of("src/main/java/com/waad/tba/modules/systemadmin");

        assertFalse(Files.exists(root.resolve("controller/UserManagementController.java")));
        assertFalse(Files.exists(root.resolve("service/UserManagementService.java")));
        assertFalse(Files.exists(root.resolve("dto/UserCreateDto.java")));
        assertFalse(Files.exists(root.resolve("dto/UserUpdateDto.java")));
        assertFalse(Files.exists(root.resolve("dto/UserViewDto.java")));
    }

    @Test
    void canonicalControllerMustOwnAdminPasswordReset() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/rbac/controller/UserController.java"));

        assertTrue(source.contains("@PutMapping(\"/{id:\\\\d+}/reset-password\")"));
        assertTrue(source.contains("userService.resetPassword(id, payload.newPassword())"));
        assertTrue(source.contains("@permissionGuard.has('USER_VIEW')"));
        assertTrue(source.contains("@permissionGuard.has('USER_MANAGE')"));
        assertFalse(source.contains("hasRole('SUPER_ADMIN')"));
    }

    @Test
    void permissionAdministrationMustNotFallBackToAStaticRole() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/rbac/controller/PermissionAdministrationController.java"));
        assertTrue(source.contains("@permissionGuard.has('ROLE_PERMISSION_MANAGE')"));
        assertFalse(source.contains("hasRole("));
        assertTrue(source.contains("@permissionGuard.has('USER_MANAGE')"));
    }

    @Test
    void frontendUserMutationsMustNotResubmitLegacyFeatureFlags() throws Exception {
        String source = Files.readString(Path.of("../frontend/src/services/rbac/users.service.js"));
        assertFalse(source.contains("canViewClaims: current.canViewClaims"));
        assertFalse(source.contains("canViewVisits: current.canViewVisits"));
        assertFalse(source.contains("canViewReports: current.canViewReports"));
        assertFalse(source.contains("canViewMembers: current.canViewMembers"));
        assertFalse(source.contains("canViewBenefitPolicies: current.canViewBenefitPolicies"));
    }
}
