package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyStatusHistory;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyStatusHistoryRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimUpdateDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.entity.ProviderService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.service.ProviderContractTermsService;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Independent finding, opened alongside V217/P0 (2026-09-03): analysing six
 * unresolved legacy claims in waad_production_review_20260903 raised the
 * question of whether the current codebase (main) already rejects a claim
 * whose serviceDate falls outside its policy's coverage window, or whether
 * that gate was added after those six claims existed.
 *
 * This class proves current main's actual behaviour scenario by scenario. It
 * does not change the engine -- any gap found here is a separate follow-up,
 * not part of P0.
 *
 * Findings: BOTH the creation and update paths already fail closed for all
 * six scenarios asked for, though through two different mechanisms:
 *  - CREATION path: MemberContextResolver.resolveForOrFail, called from
 *    ClaimService.createClaim before ClaimMapper ever runs, throws directly
 *    for: service before policy start, service after policy end, policy not
 *    ACTIVE per status history at that date, and an assignment that does
 *    not cover the service date.
 *  - UPDATE path: ClaimMapper.processEngineCalculations itself resolves the
 *    policy leniently, via MemberPolicyResolver#resolveFor (Optional,
 *    silently empty) rather than #resolveForOrFail -- reading that one
 *    method in isolation suggested editing a draft's serviceDate outside
 *    its policy's window would slip through. It does not: the same
 *    processEngineCalculations call chain ends in
 *    ClaimFinancialAdjudicationService#adjudicate, which resolves the
 *    policy a SECOND time, strictly, via #resolveForOrFail, and throws
 *    before the update can persist. See
 *    {@link #editingADraftsServiceDateOutsideThePolicyWindowIsRejected()}.
 *    (This second, strict resolution is itself a small duplication of the
 *    "policy by serviceDate" question within one request -- worth a look
 *    separately from this note, but not a coverage gap.)
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ServiceDateCoverageGateIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberContextResolver contextResolver;
    @Autowired private ClaimService claimService;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private BenefitPolicyStatusHistoryRepository statusHistoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private com.waad.tba.modules.member.service.MemberEmployerResolver memberEmployerResolver;
    @Autowired private com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermsService termsService;
    @Autowired private MedicalServiceRepository medicalServiceRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private ProviderServiceRepository providerServiceRepository;
    @Autowired private VisitRepository visitRepository;

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Employer employer(String s) {
        return employerRepository.save(Employer.builder().name("Co " + s).code("EMP-" + s).active(true).build());
    }

    private BenefitPolicy policy(Employer employer, String s, LocalDate start, LocalDate end) {
        return benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Policy " + s).policyCode("POL-" + s).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(start).endDate(end).status(BenefitPolicyStatus.ACTIVE).active(true).build());
    }

    private Member enrolledMember(Employer employer, BenefitPolicy policy, LocalDate effectiveFrom, String s) {
        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + s).barcode("BC-" + s).nationalNumber("NAT-" + s)
                .employer(employer).benefitPolicy(policy).active(true).build());
        memberEmployerResolver.assignEmployer(member, employer, effectiveFrom, "test",
                com.waad.tba.modules.member.entity.EmployerAssignmentSource.MANUAL, 1L);
        member = memberRepository.findById(member.getId()).orElseThrow();
        memberPolicyResolver.assignPolicy(member, policy, effectiveFrom, "test",
                com.waad.tba.modules.member.entity.PolicyAssignmentSource.MANUAL, 1L);
        return memberRepository.findById(member.getId()).orElseThrow();
    }

    // ── 1: service date before policy.startDate ─────────────────────────

    @Test
    void serviceBeforePolicyStartIsRejectedAtResolution() {
        String s = suffix();
        Employer employer = employer(s);
        LocalDate policyStart = LocalDate.now().minusDays(10);
        BenefitPolicy policy = policy(employer, s, policyStart, LocalDate.now().plusYears(1));
        Member member = enrolledMember(employer, policy, policyStart, s);

        assertThatThrownBy(() -> contextResolver.resolveForOrFail(member, policyStart.minusDays(1)))
                .as("a service date before the policy's own start must fail closed")
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── 2: service date after policy.endDate ─────────────────────────────

    @Test
    void serviceAfterPolicyEndIsRejectedAtResolution() {
        String s = suffix();
        Employer employer = employer(s);
        LocalDate policyStart = LocalDate.now().minusYears(1);
        LocalDate policyEnd = LocalDate.now().minusDays(5);
        BenefitPolicy policy = policy(employer, s, policyStart, policyEnd);
        Member member = enrolledMember(employer, policy, policyStart, s);

        assertThatThrownBy(() -> contextResolver.resolveForOrFail(member, policyEnd.plusDays(1)))
                .as("a service date after the policy's own end must fail closed")
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── 3: policy not ACTIVE per status history at the service date ─────

    @Test
    void policyNotActiveOnTheServiceDatePerStatusHistoryIsRejected() {
        String s = suffix();
        Employer employer = employer(s);
        LocalDate policyStart = LocalDate.now().minusYears(1);
        BenefitPolicy policy = policy(employer, s, policyStart, LocalDate.now().plusYears(1));
        Member member = enrolledMember(employer, policy, policyStart, s);

        LocalDate suspendedFrom = LocalDate.now().minusMonths(6);
        LocalDate suspendedUntil = LocalDate.now().minusMonths(3);
        // A real status transition record, not the live `status` column --
        // resolveFor deliberately never trusts the live column for a past
        // date (see MemberPolicyResolver#resolveFor).
        statusHistoryRepository.save(BenefitPolicyStatusHistory.builder()
                .policyId(policy.getId()).status(BenefitPolicyStatus.ACTIVE)
                .validFrom(policyStart).validTo(suspendedFrom).build());
        statusHistoryRepository.save(BenefitPolicyStatusHistory.builder()
                .policyId(policy.getId()).status(BenefitPolicyStatus.SUSPENDED)
                .validFrom(suspendedFrom).validTo(suspendedUntil).build());
        statusHistoryRepository.save(BenefitPolicyStatusHistory.builder()
                .policyId(policy.getId()).status(BenefitPolicyStatus.ACTIVE)
                .validFrom(suspendedUntil).validTo(null).build());

        LocalDate duringSuspension = suspendedFrom.plusDays(15);
        assertThatThrownBy(() -> contextResolver.resolveForOrFail(member, duringSuspension))
                .as("a service date while the policy was SUSPENDED must fail closed, "
                        + "even though the policy's live status is ACTIVE today")
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── 4: assignment starts after the service date ─────────────────────

    @Test
    void anAssignmentStartingAfterTheServiceDateIsRejected() {
        String s = suffix();
        Employer employer = employer(s);
        LocalDate policyStart = LocalDate.now().minusYears(1);
        BenefitPolicy policy = policy(employer, s, policyStart, LocalDate.now().plusYears(1));
        LocalDate assignedFrom = LocalDate.now().minusDays(5);
        Member member = enrolledMember(employer, policy, assignedFrom, s);

        assertThatThrownBy(() -> contextResolver.resolveForOrFail(member, assignedFrom.minusDays(1)))
                .as("a service date before the member's own assignment starts must fail closed "
                        + "-- exactly the pattern found in the six unresolved legacy claims")
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── 5: end-to-end at claim CREATION, bypassing any frontend check ───

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void claimCreationRejectsAServiceDateOutsideThePolicyWindowServerSide() {
        String s = suffix();
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employer(s);
        LocalDate policyStart = LocalDate.now().minusDays(3);
        BenefitPolicy policy = policy(employer, s, policyStart, LocalDate.now().plusYears(1));
        Member member = enrolledMember(employer, policy, policyStart, s);

        MedicalCategory category = medicalCategoryRepository.save(
                MedicalCategory.builder().code("CAT-" + s).name("Cat " + s).active(true).build());
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(80).active(true).deleted(false).build());
        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SYS-GATE-" + s.toUpperCase()).name("Gate Test Service")
                .categoryId(category.getId()).pricingMode(PricingMode.MANUAL_AMOUNT).active(true).build());
        Provider provider = providerRepository.save(Provider.builder()
                .name("Provider " + s).providerType(ProviderType.PHARMACY)
                .licenseNumber("LIC-" + s).allowAllEmployers(true).active(true).build());
        providerServiceRepository.save(ProviderService.builder()
                .providerId(provider.getId()).serviceCode(service.getCode()).active(true).build());
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + s).contractNumber("CNT-" + s).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());
        termsService.ensureEffectiveTerms(contract, "TEST");

        LocalDate outsideWindow = policyStart.minusDays(1);
        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(outsideWindow)
                .status(VisitStatus.REGISTERED).build());

        ClaimCreateDto dto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(outsideWindow)
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(service.getId()).manualAmount(new BigDecimal("50.00")).quantity(1)
                        .build()))
                .status(ClaimStatus.SUBMITTED)
                .build();

        assertThatThrownBy(() -> claimService.createClaim(dto))
                .as("createClaim must reject server-side regardless of what the entry screen allowed through")
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── 6: editing a DRAFT's serviceDate to move it outside the window ──

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void editingADraftsServiceDateOutsideThePolicyWindowIsRejected() {
        String s = suffix();
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employer(s);
        LocalDate policyStart = LocalDate.now().minusMonths(2);
        BenefitPolicy policy = policy(employer, s, policyStart, LocalDate.now().plusYears(1));
        Member member = enrolledMember(employer, policy, policyStart, s);

        MedicalCategory category = medicalCategoryRepository.save(
                MedicalCategory.builder().code("CAT-" + s).name("Cat " + s).active(true).build());
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(80).active(true).deleted(false).build());
        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SYS-GATE2-" + s.toUpperCase()).name("Gate Test Service 2")
                .categoryId(category.getId()).pricingMode(PricingMode.MANUAL_AMOUNT).active(true).build());
        Provider provider = providerRepository.save(Provider.builder()
                .name("Provider " + s).providerType(ProviderType.PHARMACY)
                .licenseNumber("LIC-" + s).allowAllEmployers(true).active(true).build());
        providerServiceRepository.save(ProviderService.builder()
                .providerId(provider.getId()).serviceCode(service.getCode()).active(true).build());
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + s).contractNumber("CNT-" + s).provider(provider)
                .startDate(LocalDate.now().minusMonths(3)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());
        termsService.ensureEffectiveTerms(contract, "TEST");

        LocalDate insideWindow = LocalDate.now();
        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(insideWindow)
                .status(VisitStatus.REGISTERED).build());

        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(insideWindow)
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(service.getId()).manualAmount(new BigDecimal("50.00")).quantity(1)
                        .build()))
                .status(ClaimStatus.DRAFT)
                .build();
        ClaimViewDto created = claimService.createClaim(createDto);
        assertThat(claimRepository.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(ClaimStatus.DRAFT);

        // Now edit the DRAFT's serviceDate to a date before the policy even
        // started -- the same shape of error found in the six legacy claims,
        // reproduced live against current main.
        LocalDate outsideWindow = policyStart.minusDays(1);
        ClaimUpdateDto updateDto = ClaimUpdateDto.builder()
                .serviceDate(outsideWindow)
                .diagnosisCode("A00")
                .diagnosisDescription("test")
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(service.getId()).manualAmount(new BigDecimal("50.00")).quantity(1)
                        .build()))
                .build();

        // ClaimMapper#processEngineCalculations itself resolves the policy
        // leniently (MemberPolicyResolver#resolveFor, Optional.empty on no
        // match) -- but ClaimFinancialAdjudicationService#adjudicate, which
        // that same call chain reaches before returning, resolves it a
        // second time via #resolveForOrFail and throws. Net effect: the
        // update is rejected, same as creation, just by a different step.
        assertThatThrownBy(() -> claimService.updateClaim(created.getId(), updateDto))
                .as("the update path rejects a serviceDate outside the policy's own window too, "
                        + "via ClaimFinancialAdjudicationService's strict re-resolution")
                .isInstanceOf(BusinessRuleException.class);
    }
}
