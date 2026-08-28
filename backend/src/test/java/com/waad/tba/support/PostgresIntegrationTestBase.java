package com.waad.tba.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;

import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.service.MemberEmployerResolver;
import com.waad.tba.modules.member.service.MemberPolicyResolver;

/**
 * Ensures integration tests can never connect to a developer or production DB.
 * A disposable PostgreSQL database is created for the test JVM and destroyed at
 * the end; Flyway always runs against an empty schema.
 */
public abstract class PostgresIntegrationTestBase {
    @Autowired(required = false)
    private MemberEmployerResolver temporalEmployerResolver;
    @Autowired(required = false)
    private MemberPolicyResolver temporalPolicyResolver;

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tba_waad_test")
            .withUsername("test_user")
            .withPassword("test_password");

    /*
     * StartupSecurityValidator runs as an EnvironmentPostProcessor, before
     * DynamicPropertySource contributes the container JDBC properties. Supply
     * test-only bootstrap values at JVM-property precedence so integration tests
     * never depend on developer secrets or the real .env file.
     */
    static {
        System.setProperty("DB_PASSWORD", "test_password");
        System.setProperty("JWT_SECRET", "test-only-jwt-secret-that-is-longer-than-thirty-two-bytes");
        System.setProperty("EMAIL_ENABLED", "false");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> "test-only-jwt-secret-that-is-longer-than-thirty-two-bytes");
        registry.add("email.enabled", () -> "false");
    }

    /**
     * Enrols an integration-test member through the same dated assignment
     * services used by production. Tests must not manufacture the legacy
     * current pointers without their temporal source of truth.
     */
    protected Member initializeTemporalAssignments(Member member) {
        if (temporalEmployerResolver == null || temporalPolicyResolver == null) {
            throw new IllegalStateException("Temporal member services are not available in this test context");
        }
        if (member.getEmployer() == null || member.getBenefitPolicy() == null) {
            throw new IllegalArgumentException("Test member requires employer and benefit policy");
        }
        java.time.LocalDate policyStart = member.getBenefitPolicy().getStartDate();
        java.time.LocalDate effectiveFrom = policyStart == null
                ? java.time.LocalDate.now().minusYears(10)
                : policyStart;
        temporalEmployerResolver.assignEmployer(member, member.getEmployer(), effectiveFrom,
                "integration test enrollment", EmployerAssignmentSource.SYSTEM, 1L);
        temporalPolicyResolver.assignPolicy(member, member.getBenefitPolicy(), effectiveFrom,
                "integration test enrollment", PolicyAssignmentSource.SYSTEM, 1L);
        return member;
    }
}
