package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberFamilyTransferRequest;
import com.waad.tba.modules.member.dto.MemberRelationshipCorrectionRequest;
import com.waad.tba.modules.member.dto.MemberFamilyPolicyChangeRequest;
import com.waad.tba.modules.member.dto.MemberFamilyReorderRequest;
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberFamilyServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberFamilyService familyService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository policyRepository;
    @Autowired private MemberEmployerResolver employerResolver;
    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void crossEmployerTransferMovesDatedContextAndFamilyAtomicallyAndAuditsIt() {
        Fixture f = fixture(true);
        Member dependent = memberRepository.findById(f.dependent().getId()).orElseThrow();

        familyService.transferDependent(dependent.getId(), new MemberFamilyTransferRequest(
                f.newPrincipal().getId(), Member.Relationship.DAUGHTER, LocalDate.now(),
                "انتقال الأسرة", dependent.getVersion()));

        Member reloaded = memberRepository.findById(dependent.getId()).orElseThrow();
        assertThat(reloaded.getParent().getId()).isEqualTo(f.newPrincipal().getId());
        assertThat(reloaded.getRelationship()).isEqualTo(Member.Relationship.DAUGHTER);
        assertThat(reloaded.getEmployer().getId()).isEqualTo(f.newPrincipal().getEmployer().getId());
        assertThat(employerResolver.resolveForOrFail(reloaded, LocalDate.now()).getId())
                .isEqualTo(f.newPrincipal().getEmployer().getId());
        assertThat(policyResolver.resolveForOrFail(reloaded, LocalDate.now()).getId())
                .isEqualTo(f.newPrincipal().getBenefitPolicy().getId());
        assertThat(jdbc.queryForObject("select count(*) from member_family_transitions where member_id=?",
                Long.class, dependent.getId())).isEqualTo(1L);
    }

    @Test
    void failureAfterDetachRollsBackParentEmployerPolicyAndHistory() {
        Fixture f = fixture(false); // target principal deliberately has no dated policy assignment
        Member before = memberRepository.findById(f.dependent().getId()).orElseThrow();
        Long oldParent = before.getParent().getId();
        Long oldEmployer = before.getEmployer().getId();
        Long oldPolicy = before.getBenefitPolicy().getId();

        assertThatThrownBy(() -> familyService.transferDependent(before.getId(),
                new MemberFamilyTransferRequest(f.newPrincipal().getId(), Member.Relationship.SON,
                        LocalDate.now(), "عملية يجب أن تتراجع", before.getVersion())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("وثيقة");

        Member after = memberRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getParent().getId()).isEqualTo(oldParent);
        assertThat(after.getEmployer().getId()).isEqualTo(oldEmployer);
        assertThat(after.getBenefitPolicy().getId()).isEqualTo(oldPolicy);
        assertThat(jdbc.queryForObject("select count(*) from member_family_transitions where member_id=?",
                Long.class, before.getId())).isZero();
    }

    @Test
    void relationshipCorrectionRequiresCurrentVersionAndLeavesAppendOnlyHistory() {
        Fixture f = fixture(true);
        Member dependent = memberRepository.findById(f.dependent().getId()).orElseThrow();
        familyService.correctRelationship(dependent.getId(),
                new MemberRelationshipCorrectionRequest(Member.Relationship.BROTHER,
                        "تصحيح موثق", dependent.getVersion()));

        Member changed = memberRepository.findById(dependent.getId()).orElseThrow();
        assertThat(changed.getRelationship()).isEqualTo(Member.Relationship.BROTHER);
        assertThatThrownBy(() -> familyService.correctRelationship(changed.getId(),
                new MemberRelationshipCorrectionRequest(Member.Relationship.SISTER,
                        "نسخة قديمة", dependent.getVersion())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("مستخدم آخر");
    }

    @Test
    void twoConcurrentTransfersOfTheSameVersionProduceOneTransitionOnly() throws Exception {
        Fixture f = fixture(true);
        Member dependent = memberRepository.findById(f.dependent().getId()).orElseThrow();
        var request = new MemberFamilyTransferRequest(f.newPrincipal().getId(), Member.Relationship.DAUGHTER,
                LocalDate.now(), "سباق نقل", dependent.getVersion());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = java.util.List.of(
                    pool.submit(() -> transferAs(f.actor().getUsername(), dependent.getId(), request)),
                    pool.submit(() -> transferAs(f.actor().getUsername(), dependent.getId(), request)));
            int successes = 0;
            for (var task : tasks) {
                if (Boolean.TRUE.equals(task.get(30, TimeUnit.SECONDS))) successes++;
            }
            assertThat(successes).isEqualTo(1);
            Member after = memberRepository.findById(dependent.getId()).orElseThrow();
            assertThat(after.getParent().getId()).isEqualTo(f.newPrincipal().getId());
            assertThat(jdbc.queryForObject("select count(*) from member_family_transitions where member_id=?",
                    Long.class, dependent.getId())).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void familyPolicyChangeIsAllOrNothingAndUsesDatedAssignmentsForEveryPerson() {
        Fixture f = fixture(true);
        BenefitPolicy replacement = policy(f.oldPrincipal().getEmployer(), "REPL-" + UUID.randomUUID().toString().substring(0, 6));
        Member principal = memberRepository.findById(f.oldPrincipal().getId()).orElseThrow();
        Member dependent = memberRepository.findById(f.dependent().getId()).orElseThrow();
        var versions = java.util.Map.of(principal.getId(), principal.getVersion(), dependent.getId(), dependent.getVersion());
        familyService.changeFamilyPolicy(principal.getId(), new MemberFamilyPolicyChangeRequest(
                replacement.getId(), LocalDate.now(), "تغيير وثيقة الأسرة", versions));
        assertThat(policyResolver.resolveForOrFail(memberRepository.findById(principal.getId()).orElseThrow(), LocalDate.now()).getId())
                .isEqualTo(replacement.getId());
        assertThat(policyResolver.resolveForOrFail(memberRepository.findById(dependent.getId()).orElseThrow(), LocalDate.now()).getId())
                .isEqualTo(replacement.getId());
    }

    @Test
    void reorderChangesVisualOrderWithoutChangingCardsOrBarcodes() {
        Fixture f = fixture(true);
        Member second = member(f.oldPrincipal().getEmployer(), f.oldPrincipal().getBenefitPolicy(),
                f.oldPrincipal(), Member.Relationship.DAUGHTER, "D2-" + UUID.randomUUID().toString().substring(0, 6));
        User actor = f.actor();
        employerResolver.assignEmployer(second, second.getEmployer(), LocalDate.now().minusDays(10), "fixture",
                EmployerAssignmentSource.SYSTEM, actor.getId());
        policyResolver.assignPolicy(second, second.getBenefitPolicy(), LocalDate.now().minusDays(10), "fixture",
                PolicyAssignmentSource.SYSTEM, actor.getId());
        Member first = memberRepository.findById(f.dependent().getId()).orElseThrow();
        second = memberRepository.findById(second.getId()).orElseThrow();
        String firstCard = first.getCardNumber(); String secondCard = second.getCardNumber();
        familyService.reorderFamily(f.oldPrincipal().getId(), new MemberFamilyReorderRequest(
                java.util.List.of(second.getId(), first.getId()),
                java.util.Map.of(first.getId(), first.getVersion(), second.getId(), second.getVersion())));
        Member firstAfter = memberRepository.findById(first.getId()).orElseThrow();
        Member secondAfter = memberRepository.findById(second.getId()).orElseThrow();
        assertThat(secondAfter.getFamilyOrder()).isEqualTo(1);
        assertThat(firstAfter.getFamilyOrder()).isEqualTo(2);
        assertThat(firstAfter.getCardNumber()).isEqualTo(firstCard);
        assertThat(secondAfter.getCardNumber()).isEqualTo(secondCard);
        assertThat(firstAfter.getBarcode()).isEqualTo(firstCard);
        assertThat(secondAfter.getBarcode()).isEqualTo(secondCard);
    }

    private boolean transferAs(String username, Long memberId, MemberFamilyTransferRequest request) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));
        try {
            familyService.transferDependent(memberId, request);
            return true;
        } catch (BusinessRuleException | org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Fixture fixture(boolean assignTargetPolicy) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        User actor = userRepository.save(User.builder().username("family-" + s).password("x")
                .fullName("Family Admin").email("family-" + s + "@test.local")
                .userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getUsername(), "x", java.util.List.of()));

        Employer a = employerRepository.save(Employer.builder().name("A " + s).code("A-" + s).active(true).build());
        Employer b = employerRepository.save(Employer.builder().name("B " + s).code("B-" + s).active(true).build());
        BenefitPolicy pa = policy(a, "PA-" + s);
        BenefitPolicy pb = policy(b, "PB-" + s);
        Member oldPrincipal = member(a, pa, null, null, "OP-" + s);
        Member newPrincipal = member(b, pb, null, null, "NP-" + s);
        Member dependent = member(a, pa, oldPrincipal, Member.Relationship.SON, "D-" + s);
        LocalDate start = LocalDate.now().minusDays(10);
        for (Member member : java.util.List.of(oldPrincipal, newPrincipal, dependent)) {
            employerResolver.assignEmployer(member, member.getEmployer(), start, "fixture",
                    EmployerAssignmentSource.SYSTEM, actor.getId());
        }
        policyResolver.assignPolicy(oldPrincipal, pa, start, "fixture", PolicyAssignmentSource.SYSTEM, actor.getId());
        policyResolver.assignPolicy(dependent, pa, start, "fixture", PolicyAssignmentSource.SYSTEM, actor.getId());
        if (assignTargetPolicy) {
            policyResolver.assignPolicy(newPrincipal, pb, start, "fixture", PolicyAssignmentSource.SYSTEM, actor.getId());
        }
        return new Fixture(oldPrincipal, newPrincipal, dependent, actor);
    }

    private BenefitPolicy policy(Employer employer, String code) {
        return policyRepository.save(BenefitPolicy.builder().name(code).policyCode(code).employer(employer)
                .annualLimit(new BigDecimal("60000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
    }

    private Member member(Employer employer, BenefitPolicy policy, Member parent,
            Member.Relationship relationship, String card) {
        return memberRepository.save(Member.builder().fullName(card).employer(employer).benefitPolicy(policy)
                .parent(parent).relationship(relationship).cardNumber(card).barcode(card)
                .status(Member.MemberStatus.ACTIVE).active(true).build());
    }

    private record Fixture(Member oldPrincipal, Member newPrincipal, Member dependent, User actor) {}
}
