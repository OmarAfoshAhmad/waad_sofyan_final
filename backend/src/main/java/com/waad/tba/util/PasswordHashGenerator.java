package com.waad.tba.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class to generate BCrypt password hash
 * Run this to get the correct hash for SUPER_ADMIN password
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = args.length > 0 ? args[0]
                : System.getenv().getOrDefault("ADMIN_DEFAULT_PASSWORD", "ChangeMe!123");
        String hash = encoder.encode(password);

        System.out.println("=".repeat(80));
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("=".repeat(80));
        System.out.println("\nSQL to update SUPER_ADMIN password:");
        System.out.println("UPDATE users SET password = '" + hash + "' WHERE email = 'superadmin@tba.sa';");
        System.out.println("=".repeat(80));
    }
}
