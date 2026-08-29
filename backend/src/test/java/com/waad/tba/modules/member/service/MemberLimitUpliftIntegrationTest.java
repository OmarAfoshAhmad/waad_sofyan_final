package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberLimitUpliftDto;
import com.waad.tba.modules.member.dto.MemberLimitUpliftRequest;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberGeneralLimitUplift;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * An exceptional uplift raises one member's general ceiling without touching
 * the policy their colleagues share.
 *
 * Money and dates, so the boundaries are asserted rather than assumed. The
 * property that matters most is the one in
 * {@link #theUpliftReachesTheCeilingWithoutMovingThePolicy()}: the raised
 * ceiling arrives through the same reader the approval engine consults, and
 * the member beside them on the same policy is unchanged. An uplift that only
 * showed up on the screen that granted it would be worse than none.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberLimitUpliftIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberLimitUpliftService upliftService;
    @Autowired private BenefitPolicyCoverageService coverageService;
    @Autowired private MemberRepository members;
    @Autowired private EmployerRepository employers;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader limitBalanceReader;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A super admin, because granting an uplift is SUPER_ADMIN-only by default. */
    private void actingAsSuperAdmin() {
        String username = "uplift-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Uplift Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    private record Fixture(Employer employer, BenefitPolicy policy, Member member, Member colleague) {}

    private Fixture fixture(BigDecimal annualLimit) {
        String s = suffix();
        Employer employer = employers.save(Employer.builder()
                .code("UP-" + s).name("Uplift " + s).active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("Uplift Policy " + s).policyCode("UP-POL-" + s).employer(employer)
                .startDate(LocalDate.now().withDayOfYear(1))
                .endDate(LocalDate.now().withMonth(12).withDayOfMonth(31))
                .annualLimit(annualLimit).defaultCoveragePercent(100)
                .status(BenefitPolicyStatus.ACTIVE).build());
        Member member = members.save(Member.builder().fullName("Raised " + s)
                .cardNumber("UP-M-" + s).employer(employer).benefitPolicy(policy).build());
        Member colleague = members.save(Member.builder().fullName("Colleague " + s)
                .cardNumber("UP-C-" + s).employer(employer).benefitPolicy(policy).build());
        assignPolicy(member, policy);
        assignPolicy(colleague, policy);
        return new Fixture(employer, policy, member, colleague);
    }

    /** The dated assignment the resolver reads; the denormalised pointer is not enough. */
    private void assignPolicy(Member member, BenefitPolicy policy) {
        jdbc.update("INSERT INTO member_policy_assignments "
                + "(member_id, policy_id, assignment_start_date) VALUES (?, ?, ?)",
                member.getId(), policy.getId(), policy.getStartDate());
        jdbc.update("INSERT INTO member_employer_assignments "
                + "(member_id, employer_id, assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, ?, 'تجهيز اختبار', 'MANUAL')",
                member.getId(), member.getEmployer().getId(), policy.getStartDate());
    }

    private MemberLimitUpliftRequest request(BigDecimal amount, LocalDate from, LocalDate to) {
        return new MemberLimitUpliftRequest(amount, from, to,
                MemberGeneralLimitUplift.Source.EMPLOYER_REQUEST, "بطلب من جهة العمل، حالة علاجية خاصة");
    }

    @Test
    @DisplayName("the uplift reaches the ceiling the approval engine reads, and only for that member")
    void theUpliftReachesTheCeilingWithoutMovingThePolicy() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));

        upliftService.grant(f.member().getId(), request(new BigDecimal("15000.00"), null, null));

        GeneralCeilingReading raised = coverageService.readGeneralCeiling(f.member(), LocalDate.now());
        assertThat(raised.mode()).isEqualTo(GeneralCeilingReading.Mode.FOUND);
        assertThat(raised.limit()).isEqualByComparingTo("75000.00");
        assertThat(raised.policyLimit()).as("the group's entitlement is untouched")
                .isEqualByComparingTo("60000.00");
        assertThat(raised.uplift()).isEqualByComparingTo("15000.00");
        assertThat(raised.actualRemaining()).as("limit minus committed, and nothing committed yet")
                .isEqualByComparingTo("75000.00");

        GeneralCeilingReading colleague = coverageService.readGeneralCeiling(f.colleague(), LocalDate.now());
        assertThat(colleague.limit()).as("an exception for one person is not a raise for the policy")
                .isEqualByComparingTo("60000.00");
        assertThat(colleague.hasUplift()).isFalse();
    }

    /**
     * THE RULE: uplifts in force on a date SUM. They do not replace one
     * another and the newest does not win.
     *
     * Stated because it is a choice, not an inevitability. Two employer
     * requests for the same person -- one for a chronic condition, one for a
     * surgery -- are two separate decisions, each with its own reason, amount
     * and window, and each has to be revocable without disturbing the other.
     * A replace rule would make the second silently cancel the first, and the
     * first's reason would still be on the record describing cover that no
     * longer existed.
     *
     * The consequence to watch is that the ceiling is the sum of everything
     * currently open, so granting rather than extending is the operator's
     * decision to make deliberately.
     */
    @Test
    @DisplayName("uplifts in force on a date sum; they do not replace one another")
    void concurrentUpliftsSum() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));

        upliftService.grant(f.member().getId(), request(new BigDecimal("10000.00"), null, null));
        upliftService.grant(f.member().getId(), request(new BigDecimal("5000.00"), null, null));

        assertThat(coverageService.readGeneralCeiling(f.member(), LocalDate.now()).limit())
                .isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("only the ones open on the date count, and revoking one leaves the other")
    void overlappingWindowsAreCountedByDateAndIndependently() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        Long id = f.member().getId();
        LocalDate today = LocalDate.now();

        // A short one that closes in three days, and an open-ended one that
        // only opens in two. Their windows overlap for one day.
        MemberLimitUpliftDto shortLived = upliftService.grant(id,
                request(new BigDecimal("4000.00"), today, today.plusDays(3)));
        upliftService.grant(id, request(new BigDecimal("6000.00"), today.plusDays(2), null));

        assertThat(upliftService.inForceFor(id, today))
                .as("day 0: only the short one has opened").isEqualByComparingTo("4000.00");
        assertThat(upliftService.inForceFor(id, today.plusDays(2)))
                .as("day 2: both are open, and they sum").isEqualByComparingTo("10000.00");
        assertThat(upliftService.inForceFor(id, today.plusDays(3)))
                .as("day 3: the short one's end date is exclusive").isEqualByComparingTo("6000.00");

        // Revoking one must not touch the other.
        upliftService.revoke(shortLived.id(), "لم تعد الحالة قائمة");
        assertThat(upliftService.inForceFor(id, today.plusDays(2)))
                .as("the second uplift is a separate decision and survives")
                .isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("the window is half-open: in force on its first day, gone on its end date")
    void theWindowBoundaries() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        LocalDate today = LocalDate.now();

        // Starts today, ends tomorrow: today counts, tomorrow does not.
        upliftService.grant(f.member().getId(),
                request(new BigDecimal("9000.00"), today, today.plusDays(1)));

        assertThat(upliftService.inForceFor(f.member().getId(), today))
                .as("the first day of the window is inside it")
                .isEqualByComparingTo("9000.00");
        assertThat(upliftService.inForceFor(f.member().getId(), today.plusDays(1)))
                .as("the end date is exclusive")
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a future uplift is not in force yet, and says so")
    void aScheduledUpliftIsNotCountedUntilItStarts() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        LocalDate nextWeek = LocalDate.now().plusWeeks(1);

        MemberLimitUpliftDto granted = upliftService.grant(f.member().getId(),
                request(new BigDecimal("8000.00"), nextWeek, null));

        assertThat(granted.state()).isEqualTo(MemberLimitUpliftDto.State.SCHEDULED);
        assertThat(coverageService.readGeneralCeiling(f.member(), LocalDate.now()).limit())
                .isEqualByComparingTo("60000.00");
        assertThat(upliftService.inForceFor(f.member().getId(), nextWeek))
                .isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("revoking ends the uplift and keeps the record, with who and why")
    void revokingClosesTheWindowWithoutDeletingAnything() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        MemberLimitUpliftDto granted = upliftService.grant(f.member().getId(),
                request(new BigDecimal("12000.00"), null, null));

        MemberLimitUpliftDto revoked = upliftService.revoke(granted.id(), "انتهت الحالة العلاجية");

        assertThat(revoked.revokedReason()).isEqualTo("انتهت الحالة العلاجية");
        assertThat(revoked.revokedByUsername()).isNotBlank();
        assertThat(upliftService.historyFor(f.member().getId(), LocalDate.now()))
                .as("the row stays; only its window closes")
                .hasSize(1);
        assertThat(upliftService.inForceFor(f.member().getId(), LocalDate.now().plusDays(2)))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a second revocation is refused AND leaves the record exactly as it was")
    void revokingTwiceIsRefusedAndChangesNothing() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        MemberLimitUpliftDto granted = upliftService.grant(f.member().getId(),
                request(new BigDecimal("12000.00"), null, null));
        upliftService.revoke(granted.id(), "سبب أول");

        // Read back from the database BEFORE the second attempt, and compare
        // against a read from the database after it.
        //
        // The obvious version of this compared the DTO revoke() returned with
        // the one read back afterwards, and was flaky: the returned object
        // carries LocalDateTime.now() at nanosecond precision while Postgres
        // stores microseconds, so the two agreed only when the nanoseconds
        // happened to be zero. Comparing two reads of the same stored row also
        // states the claim more exactly -- the refused write changed nothing
        // IN THE DATABASE, which is what "changes nothing" has to mean.
        MemberLimitUpliftDto before = upliftService.historyFor(f.member().getId(), LocalDate.now()).get(0);

        assertThatThrownBy(() -> upliftService.revoke(granted.id(), "سبب ثانٍ"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("مسبقاً");

        // Refusing is half of it. The refusal must also not have moved the end
        // date, overwritten the reason, or renamed who ended it -- a rejected
        // write that still writes is worse than one that succeeds, because
        // nothing on screen says it happened.
        MemberLimitUpliftDto after = upliftService.historyFor(f.member().getId(), LocalDate.now()).get(0);
        assertThat(after.effectiveTo()).isEqualTo(before.effectiveTo());
        assertThat(after.revokedReason()).isEqualTo("سبب أول");
        assertThat(after.revokedByUsername()).isEqualTo(before.revokedByUsername());
        assertThat(after.revokedAt()).isEqualTo(before.revokedAt());
    }

    @Test
    @DisplayName("what is refused: no reason, no amount, a past start, and a ceiling that does not exist")
    void theRefusals() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        Long id = f.member().getId();

        assertThatThrownBy(() -> upliftService.grant(id, new MemberLimitUpliftRequest(
                new BigDecimal("1000"), null, null,
                MemberGeneralLimitUplift.Source.SPECIAL_CONSIDERATION, "   ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("سبب رفع السقف إلزامي");

        assertThatThrownBy(() -> upliftService.grant(id, request(BigDecimal.ZERO, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("أكبر من صفر");

        // Backdating would change what a past decision should have been
        // without changing what it was.
        assertThatThrownBy(() -> upliftService.grant(id,
                request(new BigDecimal("1000"), LocalDate.now().minusDays(1), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ماضٍ");

        assertThatThrownBy(() -> upliftService.grant(id,
                request(new BigDecimal("1000"), LocalDate.now(), LocalDate.now())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("بعد تاريخ بدايته");
    }

    @Test
    @DisplayName("a policy with no monetary ceiling has nothing to raise")
    void anUnlimitedPolicyRefusesAnUplift() {
        actingAsSuperAdmin();
        // The schema has stored "unlimited" as 0.00 since V33, not as null.
        Fixture f = fixture(new BigDecimal("0.00"));

        assertThatThrownBy(() -> upliftService.grant(f.member().getId(),
                request(new BigDecimal("5000"), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("بلا سقف عام");
    }

    @Test
    @DisplayName("an uplift entered by mistake and cancelled the same day never applied at all")
    void aSameDayMistakeRaisesNobodysCeiling() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));

        MemberLimitUpliftDto mistake = upliftService.grant(f.member().getId(),
                request(new BigDecimal("500000.00"), null, null));
        assertThat(coverageService.readGeneralCeiling(f.member(), LocalDate.now()).limit())
                .as("it did apply while it stood")
                .isEqualByComparingTo("560000.00");

        upliftService.revoke(mistake.id(), "أُدخل بالخطأ: رقم زائد");

        assertThat(coverageService.readGeneralCeiling(f.member(), LocalDate.now()).limit())
                .as("and today, the day it was entered, it raises nothing")
                .isEqualByComparingTo("60000.00");
        assertThat(upliftService.inForceFor(f.member().getId(), LocalDate.now()))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("both accounts are on the record: who granted it and who took it back")
    void bothAccountsAreRecorded() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        MemberLimitUpliftDto granted = upliftService.grant(f.member().getId(),
                request(new BigDecimal("7000.00"), null, null));
        String grantedBy = granted.grantedByUsername();

        // A different account ends it, which is the case the record exists for.
        actingAsSuperAdmin();
        MemberLimitUpliftDto revoked = upliftService.revoke(granted.id(), "قرار مراجعة");

        assertThat(revoked.grantedByUsername()).isEqualTo(grantedBy);
        assertThat(revoked.revokedByUsername()).isNotEqualTo(grantedBy);
        assertThat(revoked.reason()).as("the reason it was granted survives the revocation")
                .isNotBlank();
        assertThat(revoked.revokedReason()).isEqualTo("قرار مراجعة");
        assertThat(revoked.state()).isEqualTo(MemberLimitUpliftDto.State.REVOKED);
    }

    // ── the gate that mattered most ────────────────────────────────────────

    /**
     * The uplift has to be spendable, not merely visible.
     *
     * It was not. LimitBalanceReader has two entry points: the bulk one the
     * members list uses, and the single one every DECISION uses -- what a
     * claim may consume, what a pre-authorization may hold, whether an
     * approval still fits. The uplift was wired into the first only, so a
     * member granted an exception saw a raised ceiling on every screen and was
     * refused the moment they tried to spend past the policy figure. An
     * exception that looks granted and is never honoured is worse than no
     * feature at all.
     *
     * This asserts the single path directly, because that is the one the
     * approval engine consults.
     */
    @Test
    @DisplayName("the raised ceiling is what a claim, a hold and an approval are measured against")
    void theUpliftIsSpendableAndNotJustVisible() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        LocalDate today = LocalDate.now();

        upliftService.grant(f.member().getId(), request(new BigDecimal("15000.00"), null, null));

        var decision = limitBalanceReader.readGeneralCeiling(
                f.member().getId(), f.policy().getId(), f.policy().getAnnualLimit(),
                LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31), null);

        assertThat(decision).isNotNull();
        assertThat(decision.annualLimit())
                .as("the effective ceiling, which is what every decision compares against")
                .isEqualByComparingTo("75000.00");
        assertThat(decision.policyLimit()).isEqualByComparingTo("60000.00");
        assertThat(decision.uplift()).isEqualByComparingTo("15000.00");
        assertThat(decision.actualRemaining()).isEqualByComparingTo("75000.00");

        // And the colleague on the same policy is measured against 60,000.
        var colleague = limitBalanceReader.readGeneralCeiling(
                f.colleague().getId(), f.policy().getId(), f.policy().getAnnualLimit(),
                LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31), null);
        assertThat(colleague.annualLimit()).isEqualByComparingTo("60000.00");
        assertThat(colleague.uplift()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an uplift cannot create a ceiling where the policy set none")
    void anUpliftNeverManufacturesACeiling() {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("0.00"));
        LocalDate today = LocalDate.now();

        // Granting is refused, but the reader must also be safe if a row ever
        // reached the table another way: raising nothing must stay nothing,
        // not become a limit the policy deliberately declined to set.
        jdbc.update("INSERT INTO member_general_limit_uplifts "
                + "(member_id, amount, effective_from, source, reason, granted_by_username) "
                + "VALUES (?, 5000.00, ?, 'SPECIAL_CONSIDERATION', 'صف مباشر لاختبار الحد', 'test')",
                f.member().getId(), today);

        var reading = limitBalanceReader.readGeneralCeiling(
                f.member().getId(), f.policy().getId(), f.policy().getAnnualLimit(),
                LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31), null);

        assertThat(reading).as("no policy ceiling means no ceiling, whatever uplifts exist").isNull();
    }

    // ── concurrency ────────────────────────────────────────────────────────

    /**
     * Two administrators ending the same uplift at the same moment.
     *
     * Revoking is read-check-write, so without a lock both transactions read a
     * live uplift, both pass "already revoked?", and both write. The loser
     * would overwrite the winner's reason and the name recorded against it,
     * and the refusal that should have happened would never have happened --
     * leaving a record that names the wrong person as the one who ended it.
     *
     * Exactly one must succeed. The other must be refused, and the row must
     * carry the winner's reason.
     */
    @Test
    @DisplayName("two simultaneous revocations: one succeeds, one is refused, the record is the winner's")
    void simultaneousRevocationsDoNotBothWrite() throws Exception {
        actingAsSuperAdmin();
        Fixture f = fixture(new BigDecimal("60000.00"));
        MemberLimitUpliftDto granted = upliftService.grant(f.member().getId(),
                request(new BigDecimal("20000.00"), null, null));

        // Each thread needs its own authenticated context; the security
        // context does not cross threads.
        var contextA = SecurityContextHolder.getContext();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var outcomes = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

        Runnable attempt = () -> {
            SecurityContextHolder.setContext(contextA);
            try {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                upliftService.revoke(granted.id(), "سبب " + Thread.currentThread().getName());
                outcomes.add("SUCCEEDED:" + Thread.currentThread().getName());
            } catch (BusinessRuleException expected) {
                outcomes.add("REFUSED");
            } catch (Exception other) {
                outcomes.add("ERROR:" + other.getClass().getSimpleName());
            }
        };

        Thread one = new Thread(attempt, "أول");
        Thread two = new Thread(attempt, "ثانٍ");
        one.start();
        two.start();
        one.join(30_000);
        two.join(30_000);

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(o -> o.startsWith("SUCCEEDED")).count())
                .as("exactly one revocation may take effect")
                .isEqualTo(1);
        assertThat(outcomes).contains("REFUSED");

        var record = upliftService.historyFor(f.member().getId(), LocalDate.now()).get(0);
        String winner = outcomes.stream().filter(o -> o.startsWith("SUCCEEDED")).findFirst().orElseThrow()
                .substring("SUCCEEDED:".length());
        assertThat(record.revokedReason())
                .as("the reason on the row belongs to the revocation that actually happened")
                .isEqualTo("سبب " + winner);
    }
}
