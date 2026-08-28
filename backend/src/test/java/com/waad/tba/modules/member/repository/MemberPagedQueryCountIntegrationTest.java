package com.waad.tba.modules.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
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
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MemberPagedQueryCountIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberRepository members;
    @Autowired private EmployerRepository employers;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional
    void queryCountIsConstantForPagesOfTenAndTwoHundredIncludingLazyDtoRelations() {
        Employer employer = employers.save(Employer.builder().code("PERF-EMP")
                .name("Performance Employer").active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("Performance Policy").policyCode("PERF-POL").employer(employer)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .annualLimit(new BigDecimal("60000")).defaultCoveragePercent(75)
                .status(BenefitPolicyStatus.ACTIVE).build());

        for (int i = 0; i < 200; i++) {
            Member principal = members.save(Member.builder().fullName("Principal " + i)
                    .cardNumber("PERF-P-" + i).employer(employer).benefitPolicy(policy).build());
            members.save(Member.builder().fullName("Dependent " + i)
                    .cardNumber("PERF-D-" + i).employer(employer).benefitPolicy(policy)
                    .parent(principal).relationship(Member.Relationship.SON).build());
        }
        entityManager.flush();
        entityManager.clear();

        long ten = statementsForDependentPage(10);
        long twoHundred = statementsForDependentPage(200);
        assertThat(ten).isLessThanOrEqualTo(2);
        assertThat(twoHundred).isEqualTo(ten);
    }

    private long statementsForDependentPage(int size) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Specification<Member> dependentsOnly = (root, query, cb) -> cb.isNotNull(root.get("parent"));
        var page = members.findAll(dependentsOnly, PageRequest.of(0, size));
        assertThat(page.getContent()).hasSize(size);
        page.getContent().forEach(member -> {
            assertThat(member.getEmployer().getName()).isNotBlank();
            assertThat(member.getBenefitPolicy().getName()).isNotBlank();
            assertThat(member.getParent().getFullName()).isNotBlank();
        });
        return statistics.getPrepareStatementCount();
    }
}
