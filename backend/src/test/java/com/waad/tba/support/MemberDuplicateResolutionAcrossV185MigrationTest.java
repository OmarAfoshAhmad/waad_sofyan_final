package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class MemberDuplicateResolutionAcrossV185MigrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v185").withUsername("test_user").withPassword("test_password");
    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test
    void v185KeepsBothIdentitiesPreventsCyclesAndMakesMergeHistoryAppendOnly() throws Exception {
        migrateTo("184");
        try (Connection c = connection(); Statement s = c.createStatement()) {
            long employer = returned(s, "insert into employers(code,name) values('M-A','Merge') returning id");
            long a = returned(s, "insert into members(employer_id,full_name,card_number,status,active) "
                    + "values(" + employer + ",'A','M-A','TERMINATED',false) returning id");
            long b = returned(s, "insert into members(employer_id,full_name,card_number,status,active) "
                    + "values(" + employer + ",'B','M-B','TERMINATED',false) returning id");
            migrateTo(null);
            long record = returned(s, "insert into member_merge_records(merge_id,duplicate_member_id,primary_member_id,reason) "
                    + "values(gen_random_uuid()," + b + "," + a + ",'duplicate') returning id");
            assertThat(returned(s, "select count(*) from members where id in (" + a + "," + b + ")"))
                    .isEqualTo(2);
            assertThatThrownBy(() -> s.executeUpdate("insert into member_merge_records(merge_id,duplicate_member_id,primary_member_id,reason) "
                    + "values(gen_random_uuid()," + a + "," + b + ",'cycle')"))
                    .hasMessageContaining("MEMBER_MERGE_CYCLE");
            assertThatThrownBy(() -> s.executeUpdate("update member_merge_records set reason='rewrite' where id=" + record))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.executeUpdate("delete from member_merge_records where id=" + record))
                    .hasMessageContaining("append-only");
        }
    }

    private void migrateTo(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }
    private Connection connection() throws Exception { return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); }
    private long returned(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }
}
