package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * The members list is about to carry each member's current general ceiling.
 * Read the naive way -- resolve the dated policy assignment per row, then the
 * committed and reserved balances per row -- that is three queries per member,
 * so a page of twenty-five costs seventy-five round trips and a page of two
 * hundred costs six hundred.
 *
 * The rule this pins is not "few queries" but "a number of queries that does
 * not depend on how many rows the page holds". A test that merely asserted an
 * upper bound would keep passing while the cost quietly grew with page size.
 *
 * Red on purpose: MemberPolicyAssignmentRepository has findCovering(memberId,
 * date) and nothing that answers for a set of members, so today the count
 * scales with the page.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MemberLimitOverviewQueryCountIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberRepository members;
    @Autowired private EmployerRepository employers;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private MemberPolicyAssignmentRepository policyAssignments;
    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 1);

    @Test
    @Transactional
    void resolvingCurrentPolicyForAPageCostsTheSameWhateverThePageHolds() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employer employer = employers.save(Employer.builder()
                .code("LIMIT-" + suffix).name("Limit Overview Employer").active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("Limit Overview Policy").policyCode("LIM-" + suffix).employer(employer)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .annualLimit(new BigDecimal("60000")).defaultCoveragePercent(75)
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Member member = members.save(Member.builder()
                    .fullName("Limit Member " + i)
                    .cardNumber("LIM-" + suffix + "-" + i)
                    .employer(employer).benefitPolicy(policy)
                    .status(Member.MemberStatus.ACTIVE).active(true)
                    .build());
            policyResolver.assignPolicy(member, policy, LocalDate.of(2026, 1, 1),
                    "fixture", PolicyAssignmentSource.SYSTEM, null);
            memberIds.add(member.getId());
        }
        entityManager.flush();
        entityManager.clear();

        long forTen = statementsResolvingPolicies(memberIds.subList(0, 10));
        long forSixty = statementsResolvingPolicies(memberIds);

        assertThat(forSixty)
                .as("resolving the dated policy for 60 members must not cost six times "
                        + "what it costs for 10; the query count belongs to the page, not to its rows")
                .isEqualTo(forTen);
    }

    /**
     * Resolves the covering assignment for every id, the way a list endpoint
     * would have to, and reports how many statements that took.
     */
    private long statementsResolvingPolicies(List<Long> memberIds) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Map<Long, ResolvedMemberPolicy> resolved = resolveCoveringPolicies(memberIds, AS_OF);

        assertThat(resolved)
                .as("every member in the page must get an answer, even if that answer is 'none'")
                .hasSize(memberIds.size());

        return statistics.getPrepareStatementCount();
    }

    /**
     * Constant is the claim, so state the constant. Left as an upper bound
     * only, this would keep passing at ten queries per page as easily as one.
     */
    @Test
    @Transactional
    void resolvingCurrentPolicyForAPageCostsASingleQuery() {
        Fixture fixture = seed(12);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        policyResolver.resolveForMembers(fixture.memberIds(), AS_OF);

        assertThat(statistics.getPrepareStatementCount())
                .as("one query for the whole page, not one per row")
                .isEqualTo(1L);
    }

    @Test
    @Transactional
    void aMemberWithNoAssignmentOnThatDateIsReportedNotAssignedRatherThanDropped() {
        Fixture fixture = seed(3);
        Member unassigned = members.save(Member.builder()
                .fullName("No Assignment")
                .cardNumber("LIM-NONE-" + UUID.randomUUID().toString().substring(0, 8))
                .employer(fixture.employer()).benefitPolicy(fixture.policy())
                .status(Member.MemberStatus.ACTIVE).active(true)
                .build());
        entityManager.flush();

        List<Long> ids = new ArrayList<>(fixture.memberIds());
        ids.add(unassigned.getId());

        Map<Long, ResolvedMemberPolicy> resolved = policyResolver.resolveForMembers(ids, AS_OF);

        assertThat(resolved)
                .as("nobody may fall out of the map; a silent absence is easy to iterate past")
                .containsKey(unassigned.getId());
        assertThat(resolved.get(unassigned.getId()).outcome())
                .isEqualTo(ResolvedMemberPolicy.Outcome.NOT_ASSIGNED);
        assertThat(resolved.get(unassigned.getId()).policyId())
                .as("and it must not fall back to the member's current pointer")
                .isNull();
    }

    @Test
    @Transactional
    void aDateBeforeTheAssignmentStartsResolvesToNotAssignedNotToTodaysPolicy() {
        Fixture fixture = seed(2);

        Map<Long, ResolvedMemberPolicy> resolved =
                policyResolver.resolveForMembers(fixture.memberIds(), LocalDate.of(2025, 6, 1));

        assertThat(resolved.values())
                .allSatisfy(entry -> assertThat(entry.outcome())
                        .as("the assignment begins in 2026; asking about 2025 must not "
                                + "answer with the policy that applies now")
                        .isEqualTo(ResolvedMemberPolicy.Outcome.NOT_ASSIGNED));
    }

    @Test
    @Transactional
    void anEmptyPageAsksTheDatabaseNothing() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        assertThat(policyResolver.resolveForMembers(List.of(), AS_OF)).isEmpty();

        assertThat(statistics.getPrepareStatementCount())
                .as("no rows, no query")
                .isZero();
    }

    private record Fixture(Employer employer, BenefitPolicy policy, List<Long> memberIds) {}

    private Fixture seed(int count) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employer employer = employers.save(Employer.builder()
                .code("LIMIT-" + suffix).name("Limit Overview Employer").active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("Limit Overview Policy").policyCode("LIM-" + suffix).employer(employer)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .annualLimit(new BigDecimal("60000")).defaultCoveragePercent(75)
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Member member = members.save(Member.builder()
                    .fullName("Limit Member " + i)
                    .cardNumber("LIM-" + suffix + "-" + i)
                    .employer(employer).benefitPolicy(policy)
                    .status(Member.MemberStatus.ACTIVE).active(true)
                    .build());
            policyResolver.assignPolicy(member, policy, LocalDate.of(2026, 1, 1),
                    "fixture", PolicyAssignmentSource.SYSTEM, null);
            ids.add(member.getId());
        }
        entityManager.flush();
        entityManager.clear();
        return new Fixture(employer, policy, ids);
    }

    /** The call the list needs: one query for the whole page. */
    private Map<Long, ResolvedMemberPolicy> resolveCoveringPolicies(List<Long> memberIds, LocalDate asOf) {
        return policyResolver.resolveForMembers(memberIds, asOf);
    }
}
