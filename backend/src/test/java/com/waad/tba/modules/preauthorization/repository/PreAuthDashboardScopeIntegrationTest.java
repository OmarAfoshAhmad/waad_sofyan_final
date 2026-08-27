package com.waad.tba.modules.preauthorization.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreAuthDashboardScopeIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PreAuthorizationRepository repository;
    @Autowired private PreAuthorizationAuditRepository auditRepository;

    @Test
    void providerAndEmployerScopesFilterAggregatesQueuesAndAuditInsidePostgres() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long employerA = employer("EA-" + suffix);
        long employerB = employer("EB-" + suffix);
        long memberA = member(employerA, "MA-" + suffix);
        long memberB = member(employerB, "MB-" + suffix);
        long providerA = provider("PA-" + suffix);
        long providerB = provider("PB-" + suffix);
        long preauthA = preauth(memberA, providerA, "RA-" + suffix);
        long preauthB = preauth(memberB, providerB, "RB-" + suffix);
        audit(preauthA, "RA-" + suffix);
        audit(preauthB, "RB-" + suffix);

        Object[] providerSummary = unwrap(repository.getActiveSummaryScoped("PROVIDERS", Set.of(providerA)));
        assertThat(((Number) providerSummary[0]).longValue()).isEqualTo(1);
        assertThat(repository.findHighPriorityPendingScoped("PROVIDERS", Set.of(providerA)))
                .extracting(PreAuthorization::getId).containsExactly(preauthA);

        Object[] employerSummary = unwrap(repository.getActiveSummaryScoped("EMPLOYERS", Set.of(employerB)));
        assertThat(((Number) employerSummary[0]).longValue()).isEqualTo(1);
        assertThat(repository.findActiveFromDateScoped(LocalDate.now().minusDays(1), "EMPLOYERS", Set.of(employerB)))
                .extracting(PreAuthorization::getId).containsExactly(preauthB);

        assertThat(auditRepository.findRecentAuditsScoped(LocalDateTime.now().minusDays(1),
                        "PROVIDERS", Set.of(providerA), PageRequest.of(0, 10)).getContent())
                .extracting(a -> a.getPreAuthorizationId()).containsExactly(preauthA);
        assertThat(auditRepository.searchScoped("scope-test", "EMPLOYERS", Set.of(employerB),
                        PageRequest.of(0, 10)).getContent())
                .extracting(a -> a.getPreAuthorizationId()).containsExactly(preauthB);
        assertThat(auditRepository.countScoped(null, "PROVIDERS", Set.of(providerA))).isEqualTo(1);

        Object[] globalSummary = unwrap(repository.getActiveSummaryScoped("GLOBAL", Set.of(-1L)));
        assertThat(((Number) globalSummary[0]).longValue()).isGreaterThanOrEqualTo(2);
    }

    private long employer(String code) {
        return jdbc.queryForObject("INSERT INTO employers (code, name) VALUES (?, ?) RETURNING id",
                Long.class, code, code);
    }

    private long member(long employerId, String card) {
        return jdbc.queryForObject("INSERT INTO members (employer_id, full_name, card_number, barcode, status, active) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', false) RETURNING id",
                Long.class, employerId, card, card, card);
    }

    private long provider(String license) {
        return jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) " +
                        "VALUES (?, ?, 'CLINIC') RETURNING id",
                Long.class, license, license);
    }

    private long preauth(long memberId, long providerId, String reference) {
        return jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, provider_id, reference_number, " +
                        "status, priority, contract_price, request_date, expected_service_date, active, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'PENDING', 'URGENT', 100, CURRENT_DATE, CURRENT_DATE + 1, true, now(), now()) RETURNING id",
                Long.class, memberId, providerId, reference);
    }

    private void audit(long preauthId, String reference) {
        jdbc.update("INSERT INTO pre_authorization_audit (pre_authorization_id, reference_number, changed_by, " +
                        "change_date, action) VALUES (?, ?, 'scope-test', now(), 'CREATE')",
                preauthId, reference);
    }

    private Object[] unwrap(Object[] row) {
        if (row.length > 0 && row[0] instanceof Object[] nested) return nested;
        return row;
    }
}
