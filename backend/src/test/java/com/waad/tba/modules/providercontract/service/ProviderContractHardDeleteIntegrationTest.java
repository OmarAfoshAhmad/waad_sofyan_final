package com.waad.tba.modules.providercontract.service;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Permanent deletion of a provider contract.
 *
 * <p>The reason this test exists: hard delete removed the contract's pricing
 * items and nothing else, while {@code provider_contract_terms} holds a row for
 * every contract that has ever existed -- written by {@code createInitial} on
 * creation, and backfilled for all pre-existing contracts by V136 -- behind an
 * {@code ON DELETE RESTRICT} foreign key. The result was that no contract could
 * ever be permanently deleted, not even an empty one, and the refusal said
 * "related data exists" while pointing at a row the system had written itself.
 *
 * <p>Both halves of the behaviour are pinned here: an empty contract must go,
 * and a contract a claim points at must not.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@WithMockUser(username = "hard-delete-tester", roles = {"SUPER_ADMIN"})
class ProviderContractHardDeleteIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ProviderContractService contractService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermRepository termRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private VisitRepository visitRepository;

    @PersistenceContext private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void deletesAContractThatOnlyCarriesTheRowsTheSystemWroteForIt() {
        ProviderContract contract = softDeletedContract();
        savePricingItem(contract);
        saveInitialTerms(contract);

        contractService.hardDelete(contract.getId());

        assertThat(contractRepository.findById(contract.getId())).isEmpty();
        assertThat(pricingRepository.findAll())
                .noneMatch(item -> item.getContract().getId().equals(contract.getId()));
        assertThat(termRepository.findEffective(contract.getId(), LocalDate.of(2026, 6, 1)))
                .isEmpty();
    }

    @Test
    void refusesToDeleteAContractAClaimStillPointsAt() {
        ProviderContract contract = softDeletedContract();
        saveInitialTerms(contract);
        insertClaimAgainst(contract);

        assertThatThrownBy(() -> contractService.hardDelete(contract.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("مطالبات");

        assertThat(contractRepository.findById(contract.getId())).isPresent();
    }

    @Test
    void stillRefusesToDeleteAContractThatWasNeverMovedToTheRecycleBin() {
        ProviderContract contract = softDeletedContract();
        contract.setActive(true);
        contractRepository.saveAndFlush(contract);

        assertThatThrownBy(() -> contractService.hardDelete(contract.getId()))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(contractRepository.findById(contract.getId())).isPresent();
    }

    private ProviderContract softDeletedContract() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Provider provider = providerRepository.save(Provider.builder()
                .name("Hard Delete Provider " + suffix)
                .licenseNumber("HD-" + suffix)
                .providerType(ProviderType.PHARMACY)
                .active(true)
                .build());
        return contractRepository.saveAndFlush(ProviderContract.builder()
                .contractCode("HDC-" + suffix)
                .provider(provider)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.EXPIRED)
                .discountPercent(new BigDecimal("10.00"))
                .active(false)
                .build());
    }

    /** Exactly what ProviderContractTermsService.createInitial writes on creation. */
    private void saveInitialTerms(ProviderContract contract) {
        termRepository.saveAndFlush(ProviderContractTerm.builder()
                .contract(contract)
                .effectiveFrom(contract.getStartDate())
                .effectiveTo(null)
                .discountPercent(contract.getDiscountPercent())
                .discountBeforeRejection(Boolean.TRUE)
                .changeReason("Initial terms")
                .build());
    }

    private void savePricingItem(ProviderContract contract) {
        pricingRepository.saveAndFlush(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode("SRV-HD-1")
                .serviceName("خدمة")
                .basePrice(new BigDecimal("50.00"))
                .contractPrice(new BigDecimal("45.00"))
                .effectiveFrom(contract.getStartDate())
                .effectiveTo(null)
                .active(true)
                .build());
    }

    /**
     * Written as SQL on purpose. A claim built through the domain would drag in a
     * policy, a category and a priced service, none of which this test is about;
     * the only thing that matters here is a row in {@code claims} whose
     * {@code provider_contract_id} points at the contract.
     *
     * <p>Runs through a TransactionTemplate rather than {@code @Transactional}:
     * an annotation on a method called from inside the same class is not proxied,
     * so the write would run with no transaction at all.
     */
    private void insertClaimAgainst(ProviderContract contract) {
        transactionTemplate.executeWithoutResult(status -> writeClaim(contract));
    }

    private void writeClaim(ProviderContract contract) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employer employer = employerRepository.save(Employer.builder()
                .code("EMP-" + suffix)
                .name("Employer " + suffix)
                .active(true)
                .build());
        // Terminated on purpose: chk_active_member_requires_policy would drag a
        // benefit policy into a test about a foreign key, and
        // chk_member_status_active_consistency requires the status to agree with
        // the flag. All this member has to be is a row a claim can point at.
        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + suffix)
                .barcode("BC-" + suffix)
                .cardNumber("BC-" + suffix)
                .nationalNumber("NAT-" + suffix)
                .employer(employer)
                .status(Member.MemberStatus.TERMINATED)
                .active(false)
                .build());
        Visit visit = visitRepository.save(Visit.builder()
                .member(member)
                .providerId(contract.getProvider().getId())
                .visitDate(LocalDate.of(2026, 6, 1))
                .status(VisitStatus.REGISTERED)
                .build());

        entityManager.createNativeQuery("""
                INSERT INTO claims (member_id, visit_id, provider_id, provider_contract_id,
                                    requested_amount, status, submission_source, review_paused,
                                    encounter_type, claim_context_code, pending_recalculation,
                                    coverage_version, active, service_date, created_at, updated_at)
                VALUES (:memberId, :visitId, :providerId, :contractId,
                        100.00, 'DRAFT', 'DIRECT_ENTRY', false,
                        'OUTPATIENT', 'OUTPATIENT', false,
                        1, true, DATE '2026-06-01', NOW(), NOW())
                """)
                .setParameter("memberId", member.getId())
                .setParameter("visitId", visit.getId())
                .setParameter("providerId", contract.getProvider().getId())
                .setParameter("contractId", contract.getId())
                .executeUpdate();
    }
}
