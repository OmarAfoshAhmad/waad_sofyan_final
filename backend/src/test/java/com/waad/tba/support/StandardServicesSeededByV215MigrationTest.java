package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V215+V225 seed manual-amount standard services (pharmacy/optics/lab invoices)
 * against categories that must already exist -- it must never create a
 * substitute category, and it must link each service to the real category
 * row, not a guessed id.
 */
class StandardServicesSeededByV215MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v215")
            .withUsername("test_user")
            .withPassword("test_password");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("standard invoice services are seeded against their real categories, idempotently")
    void v215SeedsStandardServicesAndDefaults() throws Exception {
        migrateToLatest();

        try (Connection connection = DriverManager.getConnection(
                     POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {

            assertThat(scalarString(statement,
                    "select ms.pricing_mode from medical_services ms "
                            + "join medical_categories mc on mc.id = ms.category_id "
                            + "where ms.code = 'SYS-DRUG-GENERAL' and mc.code = 'CAT-DRUG-GENERAL'"))
                    .as("linked by category code, not a hardcoded id")
                    .isEqualTo("MANUAL_AMOUNT");

            assertThat(scalarString(statement,
                    "select ms.pricing_mode from medical_services ms "
                            + "join medical_categories mc on mc.id = ms.category_id "
                            + "where ms.code = 'SYS-DRUG-CHRONIC' and mc.code = 'CAT-DRUG-CHRONIC'"))
                    .isEqualTo("MANUAL_AMOUNT");

            assertThat(scalarString(statement,
                    "select ms.pricing_mode from medical_services ms "
                            + "join medical_categories mc on mc.id = ms.category_id "
                            + "where ms.code = 'SYS-DRUG-ONCOLOGY' and mc.code = 'CAT-ONCOLOGY'"))
                    .isEqualTo("MANUAL_AMOUNT");

            assertThat(scalarString(statement,
                    "select ms.pricing_mode from medical_services ms "
                            + "join medical_categories mc on mc.id = ms.category_id "
                            + "where ms.code = 'SYS-OPTICAL-GLASSES' and mc.code = 'CAT-COV-EYE-OPTICAL'"))
                    .isEqualTo("MANUAL_AMOUNT");

            assertThat(scalarString(statement,
                    "select ms.pricing_mode from medical_services ms "
                            + "join medical_categories mc on mc.id = ms.category_id "
                            + "where ms.code = 'SYS-LAB-INVOICE' and mc.code = 'CAT-COV-DIAG-FEES'"))
                    .as("lab invoices reuse the diagnostics/professional-fees category; their ceiling comes from policy rules by context")
                    .isEqualTo("MANUAL_AMOUNT");

            assertThat(count(statement, "select count(*) from medical_services where pricing_mode = 'MANUAL_AMOUNT'"))
                    .as("no extra standard service and none of the pre-existing catalog switched mode")
                    .isEqualTo(5);

            assertThat(count(statement, "select count(*) from provider_service_defaults "
                    + "where provider_type = 'PHARMACY' and auto_apply = true and active = true"))
                    .isEqualTo(3);
            assertThat(count(statement, "select count(*) from provider_service_defaults "
                    + "where provider_type = 'OPTICS' and service_code = 'SYS-OPTICAL-GLASSES'"))
                    .isEqualTo(1);
            assertThat(count(statement, "select count(*) from provider_service_defaults "
                    + "where provider_type = 'LAB' and service_code = 'SYS-LAB-INVOICE' and auto_apply = true and active = true"))
                    .as("new lab providers receive the invoice service by default; other provider types can be provisioned manually")
                    .isEqualTo(1);

            assertThat(count(statement, "select count(*) from rbac_permissions "
                    + "where code = 'PROVIDER_STANDARD_SERVICES_MANAGE'"))
                    .isEqualTo(1);
            assertThat(count(statement, "select count(*) from rbac_role_permissions "
                    + "where role_code = 'SUPER_ADMIN' and permission_code = 'PROVIDER_STANDARD_SERVICES_MANAGE'"))
                    .isEqualTo(1);

            assertThat(scalarString(statement,
                    "select column_default from information_schema.columns "
                            + "where table_name = 'medical_services' and column_name = 'pricing_mode'"))
                    .as("any service the migration did not touch keeps CONTRACT_PRICE by column default")
                    .contains("CONTRACT_PRICE");
            assertThat(scalarString(statement,
                    "select is_nullable from information_schema.columns "
                            + "where table_name = 'medical_services' and column_name = 'pricing_mode'"))
                    .isEqualTo("NO");
        }
    }

    private String scalarString(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).as("expected exactly one row for: " + sql).isTrue();
            return rs.getString(1);
        }
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
