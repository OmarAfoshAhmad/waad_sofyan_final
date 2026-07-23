package com.waad.tba.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Ensures integration tests can never connect to a developer or production DB.
 * A disposable PostgreSQL database is created for the test JVM and destroyed at
 * the end; Flyway always runs against an empty schema.
 */
public abstract class PostgresIntegrationTestBase {
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
}
