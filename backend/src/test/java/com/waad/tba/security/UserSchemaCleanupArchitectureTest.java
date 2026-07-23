package com.waad.tba.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UserSchemaCleanupArchitectureTest {

    @Test
    void userEntityMustUseSingleActiveStateColumn() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/rbac/entity/User.java"));

        assertTrue(source.contains("@Column(name = \"is_active\")"));
        assertFalse(source.contains("@Column(name = \"enabled\")"));
        assertFalse(source.contains("account_non_expired"));
        assertFalse(source.contains("account_non_locked"));
        assertFalse(source.contains("credentials_non_expired"));
    }

    @Test
    void frontendUsersScreensMustReadCanonicalActiveState() throws Exception {
        String editSource = Files.readString(Path.of(
                "../frontend/src/pages/rbac/users/UserEdit.jsx"));
        String detailsSource = Files.readString(Path.of(
                "../frontend/src/pages/rbac/users/UserDetails.jsx"));

        assertFalse(editSource.contains("user.enabled"));
        assertFalse(detailsSource.contains("user?.enabled"));
        assertTrue(editSource.contains("user.active"));
        assertTrue(detailsSource.contains("user?.active"));
    }
}
