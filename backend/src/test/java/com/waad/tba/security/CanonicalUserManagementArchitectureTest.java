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
    }
}
