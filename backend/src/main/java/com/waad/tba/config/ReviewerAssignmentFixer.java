package com.waad.tba.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Temporary Utility to fix Medical Reviewer assignments for 'nada'.
 * Executed once at startup to ensure the user has access to all providers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewerAssignmentFixer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("🛠️ [FIX] Starting Medical Reviewer Assignment Fix for 'nada'...");

            // 1. Find nada's ID
            Long userId;
            try {
                userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = 'nada' LIMIT 1", Long.class);
            } catch (Exception e) {
                log.warn("⚠️ [FIX] User 'nada' not found or multiple entries. Skipping fix.");
                return;
            }

            if (userId == null) return;

            log.info("🛠️ [FIX] Found user 'nada' with ID: {}", userId);

            // 2. Clear existing assignments to avoid duplicates
            jdbcTemplate.update("DELETE FROM medical_reviewer_providers WHERE reviewer_id = ?", userId);

            // 3. Fetch all provider IDs
            // Simplified query to avoid dialect-specific grammar issues with booleans
            List<Long> providerIds = jdbcTemplate.queryForList(
                "SELECT id FROM providers", Long.class);

            log.info("🛠️ [FIX] Found {} active providers to assign.", providerIds.size());

            java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());

            // 4. Assign to each provider
            int count = 0;
            for (Long providerId : providerIds) {
                try {
                    jdbcTemplate.update(
                        "INSERT INTO medical_reviewer_providers (reviewer_id, provider_id, active, created_at, updated_at, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, 'SYSTEM_FIX')",
                        userId, providerId, true, now, now
                    );
                    count++;
                } catch (Exception e) {
                    log.error("⚠️ [FIX] Failed to assign provider {}: {}", providerId, e.getMessage());
                }
            }

            log.info("✅ [FIX] Successfully assigned 'nada' (ID: {}) to {} providers.", userId, count);

        } catch (Exception e) {
            log.error("❌ [FIX] Global failure in reviewer assignment fix: {}", e.getMessage());
        }
    }
}
