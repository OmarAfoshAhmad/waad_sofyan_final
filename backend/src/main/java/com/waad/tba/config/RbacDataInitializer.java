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
            userExists = userRepository.existsByUsername(username);
        } catch (Exception ex) {
            log.warn("Skipping: users table schema incomplete: {}", ex.getMessage());
            return;
        }

        if (userExists) {
            log.info("Super admin user already exists: {}", username);
            return;
        }

        String envPassword = System.getenv("ADMIN_DEFAULT_PASSWORD");
        if (envPassword == null || envPassword.isBlank()) {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  SECURITY CRITICAL: ADMIN_DEFAULT_PASSWORD is NOT SET!        ║");
            log.error("║  New super admin creation is BLOCKED for security.             ║");
            log.error("╚═══════════════════════════════════════════════════════════════╝");
            return; // BLOCKED: No hardcoded fallback
        }
        String password = envPassword;

        User superAdmin = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("System Super Administrator")
                .userType("SUPER_ADMIN")
                .active(true)
                .build();

        try {
            userRepository.save(superAdmin);
            log.info("Created super admin user: {} (role: SUPER_ADMIN)", username);
        } catch (Exception ex) {
            log.warn("Skipping user creation: {}", ex.getMessage());
        }
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
