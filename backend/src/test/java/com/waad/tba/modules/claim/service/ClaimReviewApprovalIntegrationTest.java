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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.ClaimLineLimitSnapshotRepository;
import com.waad.tba.modules.claim.repository.FinancialOutboxEventRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimApproveDto.LineDecision;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimDataUpdateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimLineReviewDecision;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * finance-00: end-to-end proof of the REVIEWED approval path (DRAFT via
 * SUBMITTED -> UNDER_REVIEW -> APPROVAL_IN_PROGRESS -> APPROVED), which until
 * now was only proven by reading ClaimReviewService.java:397 (it calls the
 * same ClaimFinancialSnapshotService.finalizeSnapshot as the direct-entry
 * path) -- never exercised by an integration test. This closes that gap.
 *
 * requestApproval() only performs the synchronous phase-1 transition and
 * publishes ClaimApprovalRequestedEvent; the real financial work happens in
 * processApproval(), which runs via an @Async AFTER_COMMIT listener --
 * meaning it only fires once this test's accumulated transaction (fixture +
 * createClaim + requestApproval) actually commits. This class is
 * @Transactional so the fixture setup shares one transaction with the
 * request; TestTransaction.flagForCommit()/.end() forces that transaction to
 * commit for real partway through the test, and awaitClaimStatus() then
 * polls for the async listener's result -- the same convention already
 * established by ClaimLifecycleIntegrationTest.commitRequestAndAwaitApproval.
 * (An earlier version of this test called processApproval() directly in
 * addition to requestApproval(), racing the real async listener onto the
 * same SERIALIZABLE claim lock and failing with a genuine
 * "could not serialize access due to concurrent update" from PostgreSQL --
 * proof the two must not run concurrently.)
 *
 * Because the commit is real, BenefitBucketClaimEventListener and
 * ClaimApprovalEventListener (both plain @TransactionalEventListener
 * (AFTER_COMMIT), no @Async) also fire for real once processApproval's own
 * REQUIRES_NEW transaction commits -- exercising the real ledger-commit and
 * provider-credit paths, not a stub.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimReviewApprovalIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimReviewService claimReviewService;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermRepository contractTermRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalServiceRepository medicalServiceRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private BenefitGroupRepository benefitGroupRepository;
    @Autowired private BenefitLimitBucketRepository benefitLimitBucketRepository;
    @Autowired private BenefitRuleBucketRepository benefitRuleBucketRepository;
    @Autowired private BenefitBucketConsumptionRepository benefitBucketConsumptionRepository;
    @Autowired private ClaimLineLimitSnapshotRepository claimLineLimitSnapshotRepository;
    @Autowired private FinancialOutboxEventRepository financialOutboxEventRepository;
    @Autowired private AccountTransactionRepository accountTransactionRepository;
    @Autowired private ClaimApprovalOrchestrator claimApprovalOrchestrator;
    @Autowired private ClaimReversalOrchestrator claimReversalOrchestrator;
    @Autowired private ClaimFinancialSnapshotService claimFinancialSnapshotService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Polls until the async approval listener has moved the claim into one of
     * the given terminal statuses, then returns it fully loaded (lines eagerly
     * fetched, matching commitRequestAndAwaitApproval's convention elsewhere
     * in the claim test suite).
     */
    private com.waad.tba.modules.claim.entity.Claim awaitClaimStatus(Long claimId, ClaimStatus... terminal) {
        java.util.Set<ClaimStatus> terminalSet = java.util.Set.of(terminal);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        ClaimStatus status;
        do {
            status = claimRepository.findById(claimId).orElseThrow().getStatus();
            if (terminalSet.contains(status)) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting claim status", e);
            }
        } while (System.nanoTime() < deadline);

        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        return txTemplate.execute(tx -> claimRepository.findByIdForFinancialUpdate(claimId).orElseThrow());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void fullReviewCycleSeparatesLimitConsumptionFromPaymentAndCommitsEachExactlyOnce() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Review Path Co " + suffix).code("EMP-" + suffix).active(true).build());

        // 80% coverage -> patient co-pay is part of the proof too, not just the discount.
        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("1000000.00")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + suffix).barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

        ProviderAccount account = providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("General Services").active(true).build());

        BenefitPolicyRule rule = benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(80).active(true).deleted(false).build());

        // A real bucket linked to the rule, so the ledger actually commits --
        // proves the review path also drives the bucket ledger, not just the
        // direct-entry path already covered elsewhere.
        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + suffix).nameAr("مجموعة مسار المراجعة")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.SHARED)
                .active(true).build());
        BenefitLimitBucket bucket = benefitLimitBucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code("BUC-" + suffix).nameAr("سقف مسار المراجعة")
                .contextType(EncounterType.OUTPATIENT).amountLimit(new BigDecimal("100000.00"))
                .periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE)
                .benefitScopeType(com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType.GROUP)
                .shared(false).active(true).build());
        benefitRuleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(bucket).build());

        // 10% contract discount, BEFORE mode -- the same ordering rule that was
        // proven correct for direct entry must also hold through review.
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).discountPercent(new BigDecimal("10.00"))
                .discountBeforeRejection(true).active(true).build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(new BigDecimal("10.00")).discountBeforeRejection(true)
                .changeReason("Test initial terms").build());

        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix).name("Service " + suffix).categoryId(category.getId())
                .cost(new BigDecimal("1000.00")).active(true).build());

        Long dictionaryReleaseId = jdbcTemplate.queryForObject("""
                INSERT INTO medical_dictionary_releases
                    (version, source_filename, source_sha256, status,
                     category_count, concept_count, alias_count, exception_count)
                VALUES (?, 'claim-review-v50.json', ?, 'RETIRED', 0, 0, 0, 0)
                RETURNING id
                """, Long.class, "V50-CLAIM-" + suffix,
                (suffix + "0".repeat(64)).substring(0, 64));

        ProviderContractPricingItem selectedPrice = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal("1000.00"))
                .contractPrice(new BigDecimal("1000.00"))
                .effectiveFrom(contract.getStartDate())
                .dictionaryReleaseId(dictionaryReleaseId)
                .dictionaryVersion("V50-CLAIM-" + suffix)
                .dictionaryConceptCode("WAC-CLAIM-TEST")
                .classificationMethodV50("PROVIDER_CODE_NAME_EXACT")
                .classificationEvidenceId(900001L)
                .active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        // gross=1000, coverage=80% -> patient=200, providerShare=800. No rejection.
        // BEFORE mode with 10% discount: discount=80, company=720.
        ClaimViewDto created = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .status(ClaimStatus.SUBMITTED)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1).build()))
                .build());
        assertThat(created.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        Long claimId = created.getId();
        Long lineId = created.getLines().get(0).getId();

        // Phase 1: reviewer decision + synchronous transition to APPROVAL_IN_PROGRESS.
        ClaimApproveDto approveDto = ClaimApproveDto.builder()
                .lineDecisions(List.of(LineDecision.builder()
                        .lineId(lineId).decision(ClaimLineReviewDecision.APPROVE).build()))
                .notes("Reviewed and approved in test")
                .build();
        ClaimViewDto afterRequest = claimReviewService.requestApproval(claimId, approveDto);
        assertThat(afterRequest.getStatus()).isEqualTo(ClaimStatus.APPROVAL_IN_PROGRESS);

        // Phase 2 (processApproval) is @Async + @TransactionalEventListener(AFTER_COMMIT):
        // it only fires once this test's own accumulated transaction (fixture
        // setup + createClaim + requestApproval) actually commits. Calling
        // processApproval directly here as well -- instead of committing and
        // waiting -- caused two concurrent transactions to fight over the same
        // SERIALIZABLE claim lock (proven by a real PSQLException during this
        // test's development); committing and polling is the only correct way
        // to observe the real async path deterministically.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var approved = awaitClaimStatus(claimId, ClaimStatus.APPROVED, ClaimStatus.REJECTED);
        assertThat(approved.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // 1) The claim total equals the sum of its own lines' company share --
        // GUARD 2 already enforces this at the approval gate; this re-asserts
        // it as an end-to-end fact, not just "no exception was thrown".
        BigDecimal sumCompanyShare = approved.getLines().stream()
                .map(l -> l.getCompanyShare() == null ? BigDecimal.ZERO : l.getCompanyShare())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(approved.getApprovedAmount()).isEqualByComparingTo(sumCompanyShare);
        assertThat(approved.getApprovedAmount()).isEqualByComparingTo("720.00");
        assertThat(approved.getPatientCoPay()).isEqualByComparingTo("200.00");
        assertThat(approved.getLines().get(0).getPricingItemId()).isEqualTo(selectedPrice.getId());
        assertThat(approved.getLines().get(0).getPricingEffectiveFrom()).isEqualTo(contract.getStartDate());
        assertThat(approved.getLines().get(0).getDictionaryReleaseId()).isEqualTo(dictionaryReleaseId);
        assertThat(approved.getLines().get(0).getDictionaryVersion()).isEqualTo("V50-CLAIM-" + suffix);
        assertThat(approved.getLines().get(0).getDictionaryConceptCode()).isEqualTo("WAC-CLAIM-TEST");
        assertThat(approved.getLines().get(0).getClassificationMethodV50())
                .isEqualTo("PROVIDER_CODE_NAME_EXACT");
        assertThat(approved.getLines().get(0).getClassificationEvidenceId()).isEqualTo(900001L);

        // 2) The approval gate did not recompute: the BEFORE-mode discount
        // (80, not 65) is exactly what ClaimLineFinancialEngine produces.
        assertThat(approved.getRequestedAmount().subtract(approved.getApprovedAmount())
                .subtract(approved.getPatientCoPay()).subtract(approved.getRefusedAmount()))
                .isEqualByComparingTo("80.00");

        // 3) The bucket ledger commits settlementBase once (1000), deliberately
        // distinct from insurerFinalPayment (720).
        List<BenefitBucketConsumption> committed = benefitBucketConsumptionRepository
                .findByClaimIdAndStatus(claimId, BenefitBucketConsumption.Status.COMMITTED);
        assertThat(committed).hasSize(1);
        assertThat(committed.get(0).getApprovedAmount()).isEqualByComparingTo("1000.00");

        // 4) Every applicable monetary limit is explained by an append-only
        // approval-time snapshot using the same settlement consumption.
        var snapshots = claimLineLimitSnapshotRepository
                .findByClaimIdOrderByClaimLineIdAscConsumptionOrderAsc(claimId);
        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.getClaimLine().getId()).isEqualTo(lineId);
            assertThat(snapshot.getLineSettlementBase()).isEqualByComparingTo("1000.00");
            assertThat(snapshot.getLimitConsumption()).isEqualByComparingTo("1000.00");
            assertThat(snapshot.getAvailableAfter()).isEqualByComparingTo(
                    snapshot.getAvailableBefore().subtract(snapshot.getLimitConsumption()));
        });

        // 5) The provider account was credited the identical amount.
        ProviderAccount refreshedAccount = providerAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(refreshedAccount.getTotalApproved()).isEqualByComparingTo("720.00");
        assertThat(refreshedAccount.getRunningBalance()).isEqualByComparingTo("720.00");

        // The provider ledger is append-only at the database boundary, not just
        // by service convention. Both mutations must be rejected by PostgreSQL.
        Long approvalTransactionId = accountTransactionRepository
                .findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType.CLAIM_APPROVAL,
                        claimId)
                .orElseThrow().getId();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored ->
                jdbcTemplate.update("UPDATE account_transactions SET description = ? WHERE id = ?",
                        "forbidden rewrite", approvalTransactionId)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored ->
                jdbcTemplate.update("DELETE FROM account_transactions WHERE id = ?", approvalTransactionId)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        // 6) Durable integration event exists exactly once in the same committed
        // financial cycle; external delivery may happen later without data loss.
        var outbox = financialOutboxEventRepository
                .findByAggregateTypeAndAggregateIdAndEventType(
                        "CLAIM", claimId, ClaimApprovalOutboxService.EVENT_TYPE);
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getCalculationVersion())
                .isEqualTo(approved.getLines().get(0).getCalculationVersion());
        assertThat(outbox.get(0).getPayload()).contains("\"approvedAmount\": 720.00");

        // 7) Delivery/retry replay is harmless. The same approved cycle may be
        // presented more than once, but every ledger remains exactly-once.
        transactionTemplate.executeWithoutResult(ignored -> {
            claimApprovalOrchestrator.commitApprovedClaim(claimId, 1L);
            claimApprovalOrchestrator.commitApprovedClaim(claimId, 1L);
        });
        assertThat(benefitBucketConsumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED)).hasSize(1);
        assertThat(accountTransactionRepository.countByReferenceTypeAndReferenceId(
                com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType.CLAIM_APPROVAL,
                claimId)).isEqualTo(1);
        assertThat(financialOutboxEventRepository
                .findByAggregateTypeAndAggregateIdAndEventType(
                        "CLAIM", claimId, ClaimApprovalOutboxService.EVENT_TYPE)).hasSize(1);

        // 8) Use the actual application correction command, not the internal
        // orchestrator. This must reverse every financial effect before the claim
        // becomes editable.
        transactionTemplate.executeWithoutResult(ignored -> {
            var portalClaim = claimRepository.findByIdForFinancialUpdate(claimId).orElseThrow();
            portalClaim.setSubmissionSource(
                    com.waad.tba.modules.claim.entity.ClaimSubmissionSource.PROVIDER_PORTAL);
            claimRepository.saveAndFlush(portalClaim);
        });
        transactionTemplate.executeWithoutResult(ignored ->
                claimReviewService.requestCorrection(claimId, "تصحيح مالي اختباري"));
        var correction = claimRepository.findById(claimId).orElseThrow();
        assertThat(correction.getStatus()).isEqualTo(ClaimStatus.NEEDS_CORRECTION);
        assertThat(correction.getApprovedAmount()).isNull();
        assertThat(correction.getPatientCoPay()).isNull();
        assertThat(correction.getNetProviderAmount()).isNull();
        assertThat(correction.getCompanyDiscountAmount()).isNull();
        assertThat(correction.getRefusedAmount()).isNull();
        // Difference is derived (requested - payable), so while no payable
        // snapshot exists the full requested amount is intentionally exposed.
        assertThat(correction.getDifferenceAmount()).isEqualByComparingTo("1000.00");
        assertThat(benefitBucketConsumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED)).isEmpty();
        assertThat(benefitBucketConsumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.REVERSED)).hasSize(2);
        assertThat(accountTransactionRepository.countByReferenceTypeAndReferenceId(
                com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType.CLAIM_REVERSAL,
                claimId)).isEqualTo(1);
        assertThat(financialOutboxEventRepository
                .findByAggregateTypeAndAggregateIdAndEventType(
                        "CLAIM", claimId, ClaimReversalOutboxService.EVENT_TYPE)).hasSize(1);
        ProviderAccount reversedAccount = providerAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reversedAccount.getTotalApproved()).isEqualByComparingTo("0.00");
        assertThat(reversedAccount.getRunningBalance()).isEqualByComparingTo("0.00");

        // 9) A portal actor may edit but may never jump from NEEDS_CORRECTION to
        // APPROVED. It must submit, enter review, and receive a new reviewer decision.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored ->
                claimService.updateClaimData(claimId, ClaimDataUpdateDto.builder()
                        .diagnosisCode("Z00.0")
                        .status(ClaimStatus.APPROVED)
                        .build())))
                .hasMessageContaining("يجب إعادة إرسال مطالبة البوابة");

        LocalDate correctedServiceDate = LocalDate.now().minusDays(1);
        ClaimViewDto correctedPortalClaim = transactionTemplate.execute(ignored ->
                claimService.updateClaimData(claimId, ClaimDataUpdateDto.builder()
                        .diagnosisCode("Z00.0")
                        .serviceDate(correctedServiceDate)
                        .lines(List.of(ClaimLineDto.builder()
                                .id(lineId)
                                .medicalServiceId(service.getId())
                                .quantity(2)
                                .build()))
                        .build()));
        assertThat(correctedPortalClaim.getServiceDate()).isEqualTo(correctedServiceDate);
        assertThat(claimRepository.findById(claimId).orElseThrow().getServiceDate())
                .isEqualTo(correctedServiceDate);
        Long correctedLineId = correctedPortalClaim.getLines().get(0).getId();
        transactionTemplate.executeWithoutResult(ignored -> claimService.submitClaim(claimId));
        transactionTemplate.executeWithoutResult(ignored -> claimService.startReview(claimId));
        transactionTemplate.executeWithoutResult(ignored -> claimReviewService.requestApproval(
                claimId,
                ClaimApproveDto.builder()
                        .lineDecisions(List.of(LineDecision.builder()
                                .lineId(correctedLineId)
                                .decision(ClaimLineReviewDecision.APPROVE)
                                .build()))
                        .notes("Corrected portal claim reviewed again")
                        .build()));
        assertThat(awaitClaimStatus(claimId, ClaimStatus.APPROVED, ClaimStatus.REJECTED).getStatus())
                .isEqualTo(ClaimStatus.APPROVED);
        assertThat(claimRepository.findById(claimId).orElseThrow().getServiceDate())
                .isEqualTo(correctedServiceDate);
        assertThat(benefitBucketConsumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED)).hasSize(1);
        assertThat(benefitBucketConsumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED).get(0).getApprovedAmount())
                .isEqualByComparingTo("2000.00");
        assertThat(claimRepository.findById(claimId).orElseThrow().getApprovedAmount())
                .isEqualByComparingTo("1440.00");
        assertThat(accountTransactionRepository.countByReferenceTypeAndReferenceId(
                com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType.CLAIM_APPROVAL,
                claimId)).isEqualTo(2);
        assertThat(financialOutboxEventRepository
                .findByAggregateTypeAndAggregateIdAndEventType(
                        "CLAIM", claimId, ClaimApprovalOutboxService.EVENT_TYPE)).hasSize(2);
        var allSnapshots = claimLineLimitSnapshotRepository
                .findByClaimIdOrderByClaimLineIdAscConsumptionOrderAsc(claimId);
        assertThat(allSnapshots).hasSize(snapshots.size() * 2);
        assertThat(allSnapshots.stream().map(s -> s.getClaimLine().getId()).distinct().count())
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM claim_lines WHERE claim_id = ? AND current_line = true",
                Long.class, claimId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM claim_lines WHERE claim_id = ? AND current_line = false "
                        + "AND superseded_at IS NOT NULL AND superseded_by_calculation_version = 2",
                Long.class, claimId)).isEqualTo(1L);
        ProviderAccount reapprovedAccount = providerAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reapprovedAccount.getTotalApproved()).isEqualByComparingTo("1440.00");
        assertThat(reapprovedAccount.getRunningBalance()).isEqualByComparingTo("1440.00");
    }
}
