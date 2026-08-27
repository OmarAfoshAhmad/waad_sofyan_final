package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * S-03. providerId was read straight from the request body, so any caller
 * holding PREAUTH_CREATE could file a pre-authorization in another provider's
 * name -- classic BOLA, with the request deciding whose record it was.
 *
 * The row is the assertion. A refusal that has already flushed the insert has
 * refused nothing, and a "success" that quietly filed under the caller's own
 * provider would be wrong in the other direction.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreAuthPortalProviderScopeTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthPortalController controller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long ownProvider;
    private long foreignProvider;

    /**
     * Real providers, because users.provider_id is a foreign key, and the
     * portal flags on, because FeatureGuard otherwise refuses with 503 before
     * the scope logic under test is reached. Both flags ship seeded false in
     * V25 -- the right production default and the wrong one here.
     */
    @BeforeEach
    void seedProvidersAndEnablePortal() {
        ownProvider = insertProvider("own");
        foreignProvider = insertProvider("foreign");
        jdbc.update("""
                UPDATE feature_flags SET enabled = true
                 WHERE flag_key IN ('PROVIDER_PORTAL_ENABLED', 'DIRECT_PREAUTH_SUBMISSION_ENABLED')
                """);
    }

    private long insertProvider(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject("""
                INSERT INTO providers (name, license_number, provider_type)
                VALUES (?, ?, 'HOSPITAL') RETURNING id
                """, Long.class, "Scope " + label + " " + suffix, "LIC-" + suffix);
        return id;
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsProviderStaff(Long providerId) {
        String username = "portal-scope-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("Scope@Test123"))
                .fullName("Portal Scope Staff")
                .email(username + "@waad.test")
                .userType("PROVIDER_STAFF")
                .providerId(providerId)
                .active(true)
                .emailVerified(true)
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    private Map<String, Object> payloadNaming(Long providerId) {
        Map<String, Object> line = new HashMap<>();
        line.put("code", "SVC-1");
        line.put("name", "Service");
        line.put("contractPrice", "100.00");
        line.put("medicalCategoryId", 7);

        Map<String, Object> member = new HashMap<>();
        member.put("id", 42);

        Map<String, Object> payload = new HashMap<>();
        payload.put("member", member);
        payload.put("expectedServiceDate", "2026-12-01");
        payload.put("lines", List.of(line));
        if (providerId != null) {
            payload.put("providerId", providerId);
        }
        return payload;
    }

    private long rowsFor(long providerId) {
        Long count = jdbc.queryForObject(
                "select count(*) from pre_authorizations where provider_id = ?",
                Long.class, providerId);
        return count == null ? 0L : count;
    }

    @Test
    void providerCannotFileInAnotherProvidersName() {
        authenticateAsProviderStaff(ownProvider);
        long foreignBefore = rowsFor(foreignProvider);

        controller.submitBulkRequest(payloadNaming(foreignProvider));

        assertThat(rowsFor(foreignProvider))
                .as("naming another provider in the body must not place a row under it")
                .isEqualTo(foreignBefore);
    }

    @Test
    void theRequestIsFiledUnderTheAuthenticatedProviderNotTheClaimedOne() {
        authenticateAsProviderStaff(ownProvider);
        long ownBefore = rowsFor(ownProvider);

        controller.submitBulkRequest(payloadNaming(foreignProvider));

        assertThat(rowsFor(ownProvider))
                .as("identity decides ownership, so the row lands under the caller")
                .isEqualTo(ownBefore + 1);
    }

    @Test
    void aProviderNeedNotSupplyItsOwnIdAtAll() {
        authenticateAsProviderStaff(ownProvider);
        long ownBefore = rowsFor(ownProvider);

        controller.submitBulkRequest(payloadNaming(null));

        assertThat(rowsFor(ownProvider))
                .as("the body no longer has to carry what the session already knows")
                .isEqualTo(ownBefore + 1);
    }

    @Test
    void anAccountWithNoProviderScopeIsRefusedRatherThanDefaulted() {
        authenticateAsProviderStaff(null);

        assertThatThrownBy(() -> controller.submitBulkRequest(payloadNaming(foreignProvider)))
                .as("a provider account with no provider is misconfigured, not system-wide")
                .isInstanceOf(AccessDeniedException.class);
    }
}
