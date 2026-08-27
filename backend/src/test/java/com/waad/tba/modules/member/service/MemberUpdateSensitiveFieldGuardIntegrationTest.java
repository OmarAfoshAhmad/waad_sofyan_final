package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberUpdateDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The generic update path (PUT /{id}) is the back door that would otherwise
 * make every centralized service optional: MemberUpdateDto carries 24 fields,
 * among them status, active, benefitPolicyId, employerId, relationship and
 * cardNumber. Before this guard, employerId and benefitPolicyId were applied
 * directly (no assignment record, no reason, no effective date) and
 * status/active were silently DROPPED by the mapper -- the caller was told the
 * save succeeded and never learned the change was discarded.
 *
 * The rule is applied to CHANGES, not to presence, so an edit form that
 * round-trips a member's current employerId unchanged keeps working.
 */
@SpringBootTest(classes = com.waad.tba.TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberUpdateSensitiveFieldGuardIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private UnifiedMemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository policyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository assignmentRepository;
    @Autowired private com.waad.tba.modules.member.repository.MemberStatusHistoryRepository statusHistoryRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void ensureAdmin() {
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                User.builder().username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));
    }

    private record Fixture(Member member, Employer employer, BenefitPolicy policy) {}

    private Fixture newMember(String s) {
        Employer employer = employerRepository.save(Employer.builder()
                .name("Guard Co " + s).code("GC-" + s).active(true).build());
        BenefitPolicy policy = policyRepository.save(BenefitPolicy.builder()
                .name("Plan " + s).policyCode("POL-G-" + s).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        Member member = memberRepository.save(Member.builder()
                .fullName("Guarded " + s).employer(employer).benefitPolicy(policy)
                .cardNumber("GCARD" + s).barcode("GCARD" + s)
                .status(Member.MemberStatus.ACTIVE).active(true).build());
        return new Fixture(member, employer, policy);
    }

    @Test
    @WithMockUser(username = "admin")
    void descriptiveFieldsStillUpdateNormally() {
        ensureAdmin();
        Fixture f = newMember(suffix());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setFullName("اسم محدَّث");
        dto.setPhone("0910000000");

        memberService.updateMember(f.member().getId(), dto);

        Member reloaded = memberRepository.findById(f.member().getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("اسم محدَّث");
        assertThat(reloaded.getPhone()).isEqualTo("0910000000");
    }

    /** Echoing the CURRENT values back is harmless and must keep working. */
    @Test
    @WithMockUser(username = "admin")
    void sendingSensitiveFieldsUnchangedIsAccepted() {
        ensureAdmin();
        Fixture f = newMember(suffix());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setFullName("اسم آخر");
        dto.setEmployerId(f.employer().getId());
        dto.setBenefitPolicyId(f.policy().getId());
        dto.setStatus(Member.MemberStatus.ACTIVE);
        dto.setActive(true);
        dto.setCardNumber(f.member().getCardNumber());

        memberService.updateMember(f.member().getId(), dto);

        assertThat(memberRepository.findById(f.member().getId()).orElseThrow().getFullName())
                .isEqualTo("اسم آخر");
    }

    @Test
    @WithMockUser(username = "admin")
    void changingTheBenefitPolicyThroughTheGenericPathIsRefusedAndChangesNothing() {
        ensureAdmin();
        String s = suffix();
        Fixture f = newMember(s);
        BenefitPolicy other = policyRepository.save(BenefitPolicy.builder()
                .name("Other " + s).policyCode("POL-O-" + s).employer(f.employer())
                .annualLimit(new BigDecimal("99999")).defaultCoveragePercent(50)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setFullName("محاولة تغيير الوثيقة");
        dto.setBenefitPolicyId(other.getId());

        assertThatThrownBy(() -> memberService.updateMember(f.member().getId(), dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("وثيقة المنافع");

        Member reloaded = memberRepository.findById(f.member().getId()).orElseThrow();
        assertThat(reloaded.getBenefitPolicy().getId()).isEqualTo(f.policy().getId());
        assertThat(reloaded.getFullName())
                .as("the whole update is refused -- no partial application")
                .isNotEqualTo("محاولة تغيير الوثيقة");
    }

    @Test
    @WithMockUser(username = "admin")
    void changingTheEmployerThroughTheGenericPathIsRefused() {
        ensureAdmin();
        String s = suffix();
        Fixture f = newMember(s);
        Employer other = employerRepository.save(Employer.builder()
                .name("Other Co " + s).code("OC-" + s).active(true).build());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setEmployerId(other.getId());

        assertThatThrownBy(() -> memberService.updateMember(f.member().getId(), dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("جهة العمل");

        assertThat(memberRepository.findById(f.member().getId()).orElseThrow()
                .getEmployer().getId()).isEqualTo(f.employer().getId());
    }

    /**
     * status/active used to be silently dropped by the mapper. Now the request
     * is refused so the caller actually learns their change did not happen.
     */
    @Test
    @WithMockUser(username = "admin")
    void changingStatusThroughTheGenericPathIsRefusedRatherThanSilentlyDropped() {
        ensureAdmin();
        Fixture f = newMember(suffix());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setStatus(Member.MemberStatus.TERMINATED);

        assertThatThrownBy(() -> memberService.updateMember(f.member().getId(), dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("حالة العضوية");

        Member reloaded = memberRepository.findById(f.member().getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(reloaded.getActive()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin")
    void changingTheCardNumberThroughTheGenericPathIsRefused() {
        ensureAdmin();
        Fixture f = newMember(suffix());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setCardNumber("HIJACKED" + suffix());

        assertThatThrownBy(() -> memberService.updateMember(f.member().getId(), dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("رقم البطاقة");
    }

    /** Every offending field is named in one response, not just the first. */
    @Test
    @WithMockUser(username = "admin")
    void allOffendingFieldsAreReportedTogether() {
        ensureAdmin();
        String s = suffix();
        Fixture f = newMember(s);
        Employer other = employerRepository.save(Employer.builder()
                .name("Other Co " + s).code("OC2-" + s).active(true).build());

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setStatus(Member.MemberStatus.SUSPENDED);
        dto.setEmployerId(other.getId());
        dto.setCardNumber("X" + s);

        assertThatThrownBy(() -> memberService.updateMember(f.member().getId(), dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("حالة العضوية")
                .hasMessageContaining("جهة العمل")
                .hasMessageContaining("رقم البطاقة");
    }

    /**
     * The "reject on CHANGE, not on presence" compromise is only safe if an
     * unchanged echo is genuinely inert. Proves it writes nothing at all: no
     * new policy assignment, no new status history row, and no @Version bump
     * on the member -- so a form that round-trips current values cannot
     * quietly generate audit noise or lose a concurrent edit.
     */
    @Test
    @WithMockUser(username = "admin")
    void echoingCurrentValuesCreatesNoAssignmentNoHistoryAndNoVersionBump() {
        ensureAdmin();
        Fixture f = newMember(suffix());
        Long id = f.member().getId();

        Member before = memberRepository.findById(id).orElseThrow();
        Long versionBefore = before.getVersion();
        int assignmentsBefore = assignmentRepository.findByMemberIdOrderByAssignmentStartDateDesc(id).size();
        int historyBefore = statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(id).size();

        MemberUpdateDto dto = new MemberUpdateDto();
        dto.setEmployerId(f.employer().getId());
        dto.setBenefitPolicyId(f.policy().getId());
        dto.setStatus(before.getStatus());
        dto.setActive(before.getActive());
        // Whitespace around an unchanged card number is a representation
        // difference, not an edit -- normalized comparison must accept it.
        dto.setCardNumber("  " + before.getCardNumber() + "  ");

        memberService.updateMember(id, dto);

        Member after = memberRepository.findById(id).orElseThrow();
        assertThat(after.getVersion())
                .as("an update that changes nothing must not bump the optimistic-lock version")
                .isEqualTo(versionBefore);
        assertThat(assignmentRepository.findByMemberIdOrderByAssignmentStartDateDesc(id))
                .as("no policy assignment may be created by an unchanged echo")
                .hasSize(assignmentsBefore);
        assertThat(statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(id))
                .as("no status history row may be created by an unchanged echo")
                .hasSize(historyBefore);
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getBenefitPolicy().getId()).isEqualTo(f.policy().getId());
    }
}
