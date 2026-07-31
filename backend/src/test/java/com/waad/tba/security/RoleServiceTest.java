package com.waad.tba.security;

import com.waad.tba.modules.rbac.entity.User;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleServiceTest {

    private final RoleService roleService = new RoleService();

    @Test
    void accountantMustBeFinancialInternalStaffOnly() {
        User accountant = User.builder()
                .username("accountant")
                .userType("ACCOUNTANT")
                .build();

        assertTrue(roleService.isFinancialUser(accountant));
        assertTrue(roleService.isInternalStaff(accountant));
        assertFalse(roleService.canAccessInternalOperations(accountant));
    }

    @Test
    void legacyInsuranceAdminAliasMustNotReturnToSecurityCode() throws IOException {
        Path securityDir = Path.of("src/main/java/com/waad/tba/security");
        String securitySource = Files.walk(securityDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .reduce("", String::concat);

        assertFalse(securitySource.contains("InsuranceAdmin"),
                "Legacy InsuranceAdmin alias must not exist in security services");
    }
}
