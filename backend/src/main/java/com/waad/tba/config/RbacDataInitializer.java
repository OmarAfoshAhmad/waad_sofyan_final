package com.waad.tba.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Static Data Initializer — Authorization Simplification (Phase 5)
 *
 * Ensures the superadmin user exists with userType=SUPER_ADMIN.
 * No roles or permissions tables are involved.
 */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class RbacDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.admin.default-password:#{null}}")
    private String configuredAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Static Data Initializer — Phase 5 (Role-Based Auth)       ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        try {
            ensureSuperAdminUser();
        } catch (Exception e) {
            log.error("Initialization failed: {}", e.getMessage(), e);
            log.warn("Continuing startup without bootstrap data");
        }
    }

    private void ensureSuperAdminUser() {
        String username = "superadmin";
        String email = "superadmin@tba.sa";

        if (!hasColumn("users", "is_active")) {
            log.warn("Skipping: users table schema incomplete (missing is_active)");
            return;
        }

        boolean userExists;
        try {
            userExists = userRepository.existsByUsernameIgnoreCase(username);
        } catch (Exception ex) {
            log.warn("Skipping: users table schema incomplete: {}", ex.getMessage());
            return;
        }

        if (userExists) {
            log.info("Super admin user already exists: {}. Synchronizing password...", username);
            updateSuperAdminPassword(username);
            return;
        }

        // Priority: env var → Spring property (app.admin.default-password)
        String envPassword = System.getenv("ADMIN_DEFAULT_PASSWORD");
        String password;
        if (envPassword != null && !envPassword.isBlank()) {
            password = envPassword;
        } else if (configuredAdminPassword != null && !configuredAdminPassword.isBlank()) {
            password = configuredAdminPassword;
            log.warn("Using configured admin password from application properties (dev mode).");
        } else {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  SECURITY CRITICAL: ADMIN_DEFAULT_PASSWORD is NOT SET!        ║");
            log.error("║  Set ADMIN_DEFAULT_PASSWORD env var or app.admin.default-password. ║");
            log.error("╚═══════════════════════════════════════════════════════════════╝");
            return;
        }

        User superAdmin = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("System Super Administrator")
                .userType("SUPER_ADMIN")
                .active(true)
                .emailVerified(true)
                .build();

        try {
            userRepository.save(superAdmin);
            log.info("Created super admin user: {} (role: SUPER_ADMIN)", username);
        } catch (Exception ex) {
            log.warn("Skipping user creation: {}", ex.getMessage());
        }
    }

    private void updateSuperAdminPassword(String username) {
        // Handle superadmin
        String adminPassword = System.getenv("ADMIN_DEFAULT_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = configuredAdminPassword;
        }

        // If no configured password, default to 'admin' for safety during this fix
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = "admin";
        }

        String finalAdminPassword = adminPassword;
        userRepository.findByUsername("superadmin").ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(finalAdminPassword));
            user.setEmailVerified(true);
            user.unlockAccount();
            userRepository.save(user);
            log.info("🔐 [SECURITY] Password synced for superadmin.");
        });

        // Handle nada (Medical Reviewer)
        userRepository.findByUsername("nada").ifPresent(user -> {
            // Force a known password for testing if not already set or needing reset
            user.setPassword(passwordEncoder.encode("nada123"));
            user.setEmailVerified(true);
            user.unlockAccount();
            userRepository.save(user);
            log.info("🔐 [SECURITY] Password synced for user 'nada' to 'nada123'.");
        });
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                    Integer.class, tableName, columnName);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
