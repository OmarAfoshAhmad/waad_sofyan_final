package com.waad.tba.security.audit;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for S01-13: SecurityAuditServiceTest exercises
 * logSecurityEvent against a mocked repository only, which never touches a
 * real jsonb column and therefore could not have caught the
 * beforeState/afterState jsonb-vs-varchar entity mapping defect found and
 * fixed on 2026-07-24 (SecurityAuditEvent lacked @JdbcTypeCode(SqlTypes.JSON)
 * on its jsonb-backed columns). That defect silently discarded every
 * security_audit_events write in the running deployment while every unit
 * test still passed.
 *
 * This test runs against the same real PostgreSQL 16 engine used in
 * production (via Testcontainers, through Flyway) and asserts a row is
 * actually persisted and readable back — including the beforeState/
 * afterState jsonb path — closing the gap a mocked unit test cannot cover.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
class SecurityAuditServicePostgresIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private SecurityAuditEventRepository repository;

    @Test
    @Transactional
    void logSecurityEventPersistsAgainstRealPostgresIncludingJsonbState() {
        long before = repository.count();

        // actorId is intentionally null: real users don't exist in this empty
        // test database, and fk_audit_actor legitimately rejects a fabricated
        // id. A null actor (e.g. a system-initiated change) is itself a valid,
        // already-covered production case (see logLoginFailure below) and
        // keeps this test focused on the jsonb before/after-state path.
        SecurityAuditEvent saved = securityAuditService.logSecurityEvent(
                null, "integration-test-user",
                SecurityAuditEvent.AuditActionType.SETTING_CHANGED,
                "SETTING", 7L, "CLAIM_SLA_DAYS",
                "10.0.0.5", "JUnit",
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Integration test",
                Map.of("value", "10"),
                Map.of("value", "15"));

        assertThat(saved)
                .as("logSecurityEvent must not silently swallow a real persistence failure")
                .isNotNull();
        assertThat(repository.count()).isEqualTo(before + 1);

        SecurityAuditEvent reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBeforeState()).contains("\"value\":\"10\"");
        assertThat(reloaded.getAfterState()).contains("\"value\":\"15\"");
        assertThat(reloaded.getActorId()).isNull();
    }

    @Test
    @Transactional
    void logLoginFailurePersistsWithNullActorIdAgainstRealPostgres() {
        long before = repository.count();

        securityAuditService.logLoginFailure("unknown-user", "10.0.0.6", "JUnit", "Bad credentials");

        assertThat(repository.count()).isEqualTo(before + 1);
    }
}
