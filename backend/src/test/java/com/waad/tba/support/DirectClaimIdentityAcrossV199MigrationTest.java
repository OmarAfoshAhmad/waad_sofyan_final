package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Proves V199 against an inhabited V198 database, including write-once identity. */
class DirectClaimIdentityAcrossV199MigrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v199").withUsername("test_user").withPassword("test_password");

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test
    void existingClaimsSurviveAndDirectEntryIdentityIsUniquePairedAndWriteOnce() throws Exception {
        migrateTo("198");
        try (Connection connection = DriverManager.getConnection(
                     POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            long existing = worldAndClaim(statement, "existing");
            migrateTo(null);

            assertThat(value(statement, existing, "direct_entry_idempotency_key")).isNull();
            assertThat(value(statement, existing, "direct_entry_request_fingerprint")).isNull();
            assertThatCode(() -> statement.executeUpdate(identity(existing, "direct-1", "a")))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> statement.executeUpdate(identity(existing, "direct-2", "b")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("write-once");

            long second = worldAndClaim(statement, "second");
            assertThatThrownBy(() -> statement.executeUpdate(identity(second, "direct-1", "c")))
                    .isInstanceOf(SQLException.class);

            long third = worldAndClaim(statement, "third");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE claims SET direct_entry_idempotency_key='half' WHERE id=" + third))
                    .isInstanceOf(SQLException.class);
        }
    }

    private String identity(long id, String key, String fingerprintCharacter) {
        return "UPDATE claims SET direct_entry_idempotency_key='" + key
                + "', direct_entry_request_fingerprint='" + fingerprintCharacter.repeat(64) + "' WHERE id=" + id;
    }

    private long worldAndClaim(Statement statement, String tag) throws Exception {
        long employer = id(statement, "INSERT INTO employers(code,name) VALUES('V199-" + tag
                + "','V199 " + tag + "') RETURNING id");
        long policy = id(statement, "INSERT INTO benefit_policies(name,policy_code,employer_id,annual_limit,"
                + "default_coverage_percent,start_date,end_date,status,active) VALUES('Policy " + tag
                + "','POL-V199-" + tag + "'," + employer
                + ",1000,100,CURRENT_DATE-1,CURRENT_DATE+1,'ACTIVE',true) RETURNING id");
        long member = id(statement, "INSERT INTO members(employer_id,benefit_policy_id,full_name,card_number,barcode,status,active) "
                + "VALUES(" + employer + "," + policy + ",'Member " + tag + "','CARD-" + tag + "','CARD-" + tag
                + "','ACTIVE',true) RETURNING id");
        long provider = id(statement, "INSERT INTO providers(name,license_number,provider_type) VALUES('Provider "
                + tag + "','LIC-" + tag + "','CLINIC') RETURNING id");
        long visit = id(statement, "INSERT INTO visits(member_id,provider_id,visit_date) VALUES(" + member + ","
                + provider + ",CURRENT_DATE) RETURNING id");
        return id(statement, "INSERT INTO claims(claim_number,member_id,provider_id,visit_id,service_date,"
                + "requested_amount,status) VALUES('CLM-V199-" + tag + "'," + member + "," + provider + ","
                + visit + ",CURRENT_DATE,100,'DRAFT') RETURNING id");
    }

    private long id(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }

    private String value(Statement statement, long claimId, String column) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT " + column + " FROM claims WHERE id=" + claimId)) {
            result.next(); return result.getString(1);
        }
    }

    private void migrateTo(String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").baselineOnMigrate(true);
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }
}
