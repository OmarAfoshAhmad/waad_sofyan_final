package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberDuplicateServiceIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired private MemberDuplicateService service;
    @Autowired private MemberIdentityResolver identityResolver;
    @Autowired private MemberStatusTransitionService transitions;
    @Autowired private MemberRepository members;
    @Autowired private EmployerRepository employers;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private VisitRepository visits;
    @Autowired private ClaimRepository claims;
    @Autowired private ProviderRepository providers;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    @Transactional
    void mergeRetiresDuplicateButKeepsItAndItsDependentsAndResolvesNewOperationsToPrimary() {
        Fixture f = fixture();
        Member primary = members.findById(f.primary().getId()).orElseThrow();
        Member duplicate = members.findById(f.duplicate().getId()).orElseThrow();
        Provider provider = providers.save(Provider.builder().name("Historical Provider " + UUID.randomUUID())
                .licenseNumber("LIC-" + UUID.randomUUID()).providerType(ProviderType.CLINIC).active(true).build());
        Visit historicalVisit = visits.saveAndFlush(Visit.builder().member(duplicate).employer(f.employer())
                .providerId(provider.getId()).visitDate(LocalDate.now().minusDays(2)).build());
        Claim historicalClaim = Claim.builder().member(duplicate).visit(historicalVisit)
                .providerId(provider.getId()).serviceDate(historicalVisit.getVisitDate())
                .requestedAmount(new BigDecimal("10.00")).approvedAmount(new BigDecimal("10.00"))
                .status(ClaimStatus.DRAFT).build();
        historicalClaim.addLine(ClaimLine.builder().serviceCode("HIST").serviceName("Historical")
                .quantity(1).unitPrice(new BigDecimal("10.00")).build());
        historicalClaim = claims.saveAndFlush(historicalClaim);
        service.mergeDuplicates(primary.getId(), List.of(duplicate.getId()), "نفس الشخص",
                Map.of(primary.getId(), primary.getVersion(), duplicate.getId(), duplicate.getVersion()));

        Member retired = members.findById(duplicate.getId()).orElseThrow();
        assertThat(retired.getStatus()).isEqualTo(Member.MemberStatus.DUPLICATE_MERGED);
        assertThat(retired.getActive()).isFalse();
        assertThat(members.findByParentId(duplicate.getId())).extracting(Member::getId)
                .containsExactly(f.dependent().getId());
        assertThat(identityResolver.resolveCanonicalId(duplicate.getId())).isEqualTo(primary.getId());
        assertThat(visits.findById(historicalVisit.getId()).orElseThrow().getMember().getId())
                .isEqualTo(duplicate.getId());
        assertThat(claims.findById(historicalClaim.getId()).orElseThrow().getMember().getId())
                .isEqualTo(duplicate.getId());
        assertThat(jdbc.queryForObject("select count(*) from member_merge_records where duplicate_member_id=?",
                Long.class, duplicate.getId())).isEqualTo(1L);
    }

    @Test
    void incompatibleFamiliesFailWithoutRetiringEitherIdentity() {
        Fixture f = fixture();
        Member primary = members.findById(f.primary().getId()).orElseThrow();
        Member dependent = members.findById(f.dependent().getId()).orElseThrow();
        assertThatThrownBy(() -> service.mergeDuplicates(primary.getId(), List.of(dependent.getId()), "خطأ",
                Map.of(primary.getId(), primary.getVersion(), dependent.getId(), dependent.getVersion())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("رئيس أسرة");
        assertThat(members.findById(dependent.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from member_merge_records where duplicate_member_id=?",
                Long.class, dependent.getId())).isZero();
    }

    @Test
    void failureOnLaterDuplicateRollsBackEarlierMergeCompletely() {
        Fixture f = fixture();
        Member primary = members.findById(f.primary().getId()).orElseThrow();
        Member duplicate = members.findById(f.duplicate().getId()).orElseThrow();
        Member incompatibleDependent = members.findById(f.dependent().getId()).orElseThrow();

        assertThatThrownBy(() -> service.mergeDuplicates(primary.getId(),
                List.of(duplicate.getId(), incompatibleDependent.getId()), "دفعة غير متجانسة",
                Map.of(primary.getId(), primary.getVersion(),
                        duplicate.getId(), duplicate.getVersion(),
                        incompatibleDependent.getId(), incompatibleDependent.getVersion())))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(members.findById(duplicate.getId()).orElseThrow().getStatus())
                .isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from member_merge_records where duplicate_member_id=?",
                Long.class, duplicate.getId())).isZero();
    }

    @Test
    void mergedIdentityCannotBeReactivatedOrTransitionedThroughStatusBackdoors() {
        Fixture f = fixture();
        Member primary = members.findById(f.primary().getId()).orElseThrow();
        Member duplicate = members.findById(f.duplicate().getId()).orElseThrow();
        service.mergeDuplicates(primary.getId(), List.of(duplicate.getId()), "نفس الشخص",
                Map.of(primary.getId(), primary.getVersion(), duplicate.getId(), duplicate.getVersion()));

        assertThatThrownBy(() -> transitions.restoreFromSuspended(duplicate.getId(), "محاولة استعادة", 1L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("سجل مدموج");
        assertThatThrownBy(() -> transitions.terminateMembership(duplicate.getId(), "محاولة إنهاء", 1L,
                com.waad.tba.modules.member.entity.StatusSource.MANUAL))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("سجل مدموج");
        assertThat(members.findById(duplicate.getId()).orElseThrow().getStatus())
                .isEqualTo(Member.MemberStatus.DUPLICATE_MERGED);
    }

    @Test
    void concurrentMergeRequestsCreateExactlyOneCanonicalLink() throws Exception {
        Fixture f = fixture();
        Member primary = members.findById(f.primary().getId()).orElseThrow();
        Member duplicate = members.findById(f.duplicate().getId()).orElseThrow();
        Map<Long, Long> versions = Map.of(primary.getId(), primary.getVersion(),
                duplicate.getId(), duplicate.getVersion());
        CyclicBarrier start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(f.username(), "x", List.of()));
                start.await();
                try {
                    service.mergeDuplicates(primary.getId(), List.of(duplicate.getId()), "طلبان متزامنان", versions);
                    return true;
                } catch (RuntimeException expectedConflict) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Future<Boolean> first = pool.submit(attempt);
            Future<Boolean> second = pool.submit(attempt);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from member_merge_records where duplicate_member_id=?",
                Long.class, duplicate.getId())).isEqualTo(1L);
    }

    private Fixture fixture() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(User.builder().username("merge-" + s).password("x").fullName("Merge Admin")
                .email("merge-" + s + "@test.local").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), "x", List.of()));
        Employer employer = employers.save(Employer.builder().name("Merge " + s).code("MG-" + s).active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder().name("P " + s).policyCode("P-" + s)
                .employer(employer).annualLimit(new BigDecimal("60000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        Member primary = save(employer, policy, null, null, "P" + s);
        Member duplicate = save(employer, policy, null, null, "D" + s);
        Member dependent = save(employer, policy, duplicate, Member.Relationship.SON, "C" + s);
        return new Fixture(primary, duplicate, dependent, employer, user.getUsername());
    }
    private Member save(Employer e, BenefitPolicy p, Member parent, Member.Relationship relation, String card) {
        return members.saveAndFlush(Member.builder().fullName(card).employer(e).benefitPolicy(p).parent(parent)
                .relationship(relation).cardNumber(card).barcode(card).status(Member.MemberStatus.ACTIVE).active(true).build());
    }
    private record Fixture(Member primary, Member duplicate, Member dependent, Employer employer, String username) {}
}
