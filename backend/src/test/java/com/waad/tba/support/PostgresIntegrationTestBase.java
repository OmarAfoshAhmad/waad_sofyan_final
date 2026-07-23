package com.waad.tba.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Ensures integration tests can never connect to a developer or production DB.
 * A disposable PostgreSQL database is created for the test JVM and destroyed at
 * the end; Flyway always runs against an empty schema.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTestBase {
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tba_waad_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> "test-only-jwt-secret-that-is-longer-than-thirty-two-bytes");
        registry.add("email.enabled", () -> "false");
    }
}
