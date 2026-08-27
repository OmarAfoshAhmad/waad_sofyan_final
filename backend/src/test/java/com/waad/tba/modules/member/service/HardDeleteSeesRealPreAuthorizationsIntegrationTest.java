package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The hard-delete footprint guard counted pre-authorizations from
 * preauthorization_requests -- a dead model with no entity and no writer, so
 * the count was always zero. A member with real approvals in
 * pre_authorizations would therefore have passed the guard and been deleted,
 * taking their approval history with them.
 *
 * The distinction the guard exists to make: a member who has left keeps their
 * record. Termination is the answer for someone with history; hard delete is
 * only for a record created in error, and "in error" means nothing financial
 * or clinical ever attached to it.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class HardDeleteSeesRealPreAuthorizationsIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberStatusTransitionService statusTransitionService;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A member with a real pre-authorization, or none, as asked. */
    private long memberWithApproval(boolean withApproval) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('HD-" + s
                + "', 'HardDelete Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES "
                + "('HDP-" + s + "', 'HDPOL-" + s + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id",
                Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'HardDelete Member', "
                + policyId + ", 'HD" + s + "', 'HD" + s + "', 'ACTIVE', true) RETURNING id", Long.class);

        if (withApproval) {
            Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, "
                    + "provider_type) VALUES ('Prov " + s + "', 'HDLIC-" + s
                    + "', 'CLINIC') RETURNING id", Long.class);
            jdbc.update("INSERT INTO pre_authorizations (member_id, policy_id, provider_id, status, "
                    + "request_date, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', now(), "
                    + "now(), now())", memberId, policyId, providerId);
        }
        return memberId;
    }

    private long approvalsFor(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pre_authorizations WHERE member_id = ?",
                Long.class, memberId);
    }

    private boolean memberExists(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id = ?", Long.class, memberId) > 0;
    }

    @Test
    void aMemberWithARealPreAuthorizationCannotBeHardDeleted() {
        long memberId = memberWithApproval(true);

        // Counting the dead table returned zero and let this through. The
        // approval is a real financial commitment against this member's limit.
        assertThatThrownBy(() -> statusTransitionService.hardDelete(
                memberId, "أُنشئ بالخطأ", 1L, "admin", true))
                .hasMessageContaining("موافقات مسبقة");

        assertThat(memberExists(memberId)).as("the member survives the refusal").isTrue();
        assertThat(approvalsFor(memberId)).as("and so does the approval").isEqualTo(1L);
    }

    @Test
    void terminatingAMemberWithApprovalsIsStillAllowedAndKeepsThem() {
        long memberId = memberWithApproval(true);

        Member terminated = statusTransitionService.terminateMembership(
                memberId, "انتهاء العلاقة الوظيفية", 1L,
                com.waad.tba.modules.member.entity.StatusSource.MANUAL);

        // Termination is the answer for a member with history: the record and
        // everything attached to it stay readable.
        assertThat(terminated.getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        assertThat(memberExists(memberId)).isTrue();
        assertThat(approvalsFor(memberId)).as("the approval history survives termination").isEqualTo(1L);
    }

    @Test
    void aMemberWithNoFootprintAtAllCanStillBeHardDeleted() {
        long memberId = memberWithApproval(false);

        statusTransitionService.hardDelete(memberId, "أُنشئ بالخطأ", 1L, "admin", true);

        // The guard must not become a blanket refusal: a record created in
        // error, with nothing attached, is exactly what hard delete is for.
        assertThat(memberExists(memberId)).isFalse();
    }
}
