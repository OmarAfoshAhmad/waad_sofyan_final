package com.waad.tba.modules.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.eligibility.entity.EligibilityCheck;
import com.waad.tba.modules.eligibility.repository.EligibilityCheckRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the fix for a real bug found while testing the family-eligibility
 * unification against real PostgreSQL (not Mockito): EligibilityEngineServiceImpl
 * used to save its audit record inside the CALLER's transaction. Both
 * production entry points (UnifiedMemberService and FamilyEligibilityService
 * checkFamilyEligibility) run inside @Transactional(readOnly = true) --
 * Hibernate defers the INSERT to flush/commit, past the audit-save's own
 * try/catch, so Postgres rejected the write (read-only transaction) and the
 * ENTIRE eligibility check failed with UnexpectedRollbackException. Every
 * real eligibility check would have failed against a real database.
 *
 * The fix: EligibilityAuditRecorder.record runs in its OWN
 * REQUIRES_NEW transaction on a separate Spring bean (self-invocation can't
 * be intercepted, hence the separate class) and flushes explicitly, so a
 * write failure is caught where it happens and can never roll back or hide
 * the eligibility decision it's describing.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class EligibilityAuditRecorderIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private EligibilityEngineService eligibilityService;
    @Autowired private EligibilityAuditRecorder auditRecorder;
    @Autowired private EligibilityCheckRepository eligibilityCheckRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Member persistMember(String suffix, LocalDate policyStart, LocalDate policyEnd) {
        Employer employer = employerRepository.save(Employer.builder()
                .name("Audit Test Co " + suffix).code("EMP-A-" + suffix).active(true).build());
        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-A-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(policyStart).endDate(policyEnd)
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        return memberRepository.save(Member.builder()
                .fullName("Audit Member " + suffix).barcode("BC-AUDIT-" + suffix)
                .nationalNumber("NAT-A-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());
    }

    /** Real reproduction of the bug: calling checkEligibility from inside a
     * caller-owned READ-ONLY transaction (exactly what both production entry
     * points do) must not throw, and the audit record must actually land in
     * Postgres. */
    @Test
    void eligibilityCheckInsideAReadOnlyTransactionSucceedsAndPersistsTheAuditRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = persistMember(suffix, LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));

        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);
        readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        EligibilityResult result = readOnlyTx.execute(status -> {
            EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                    .memberId(member.getId()).serviceDate(LocalDate.now()).build();
            return eligibilityService.checkEligibility(request);
        });

        assertThat(result).isNotNull();
        assertThat(result.isAuditRecorded()).isTrue();

        EligibilityCheck persisted = eligibilityCheckRepository.findByRequestId(result.getRequestId())
                .orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getMemberId()).isEqualTo(member.getId());
        assertThat(persisted.getEligible()).isEqualTo(result.isEligible());
    }

    /** A rejected eligibility decision must also be audited, with its reasons
     * and the rule count actually evaluated. */
    @Test
    void ineligibleDecisionIsAuditedWithReasonsAndRulesEvaluated() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // Policy not yet started -> PolicyCoveragePeriodRule hard-fails deterministically.
        Member member = persistMember(suffix, LocalDate.now().plusMonths(6), LocalDate.now().plusYears(1));

        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .memberId(member.getId()).serviceDate(LocalDate.now()).build();
        EligibilityResult result = eligibilityService.checkEligibility(request);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.isAuditRecorded()).isTrue();

        EligibilityCheck persisted = eligibilityCheckRepository.findByRequestId(result.getRequestId()).orElseThrow();
        assertThat(persisted.getEligible()).isFalse();
        assertThat(persisted.getReasons()).isNotBlank();
        assertThat(persisted.getRulesEvaluated()).isGreaterThan(0);
    }

    /** A failure persisting the audit record (simulated here via a duplicate
     * requestId, which the DB's own unique constraint rejects) must not alter
     * or hide the eligibility decision -- only mark it as unaudited. */
    @Test
    void auditPersistenceFailureDoesNotChangeTheEligibilityDecisionButIsFlagged() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = persistMember(suffix, LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));

        String duplicateRequestId = UUID.randomUUID().toString();
        EligibilityContext context = EligibilityContext.builder()
                .requestId(duplicateRequestId)
                .memberId(member.getId())
                .serviceDate(LocalDate.now())
                .checkTimestamp(java.time.LocalDateTime.now())
                .build();
        EligibilityResult firstResult = EligibilityResult.eligible(duplicateRequestId, null, 1L, 1);

        boolean firstRecorded = auditRecorder.record(context, firstResult);
        assertThat(firstRecorded).isTrue();

        // Same requestId again -> unique constraint violation inside record();
        // must be caught there and reported as "not recorded", never thrown.
        EligibilityResult secondResult = EligibilityResult.eligible(duplicateRequestId, null, 1L, 1);
        boolean secondRecorded = auditRecorder.record(context, secondResult);

        assertThat(secondRecorded).isFalse();
        // The decision object itself (built independently of the audit write)
        // is untouched -- still eligible, unchanged.
        assertThat(secondResult.isEligible()).isTrue();

        // No duplicate row was created for the requestId.
        long count = eligibilityCheckRepository.findByRequestId(duplicateRequestId).stream().count();
        assertThat(count).isEqualTo(1);
    }

    /** Because the audit write runs in its own REQUIRES_NEW transaction, a
     * rollback of the caller's transaction must not erase the already-committed
     * audit record -- the audit trail is intentionally independent of the
     * caller's business-transaction outcome. */
    @Test
    void callerTransactionRollbackDoesNotErasePersistedAuditRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = persistMember(suffix, LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));
        String requestId = UUID.randomUUID().toString();

        TransactionTemplate callerTx = new TransactionTemplate(transactionManager);
        callerTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            callerTx.execute(status -> {
                EligibilityContext context = EligibilityContext.builder()
                        .requestId(requestId)
                        .memberId(member.getId())
                        .serviceDate(LocalDate.now())
                        .checkTimestamp(java.time.LocalDateTime.now())
                        .build();
                EligibilityResult result = EligibilityResult.eligible(requestId, null, 1L, 1);
                auditRecorder.record(context, result);
                // Force the caller's own transaction to roll back after the
                // audit record has already been committed independently.
                throw new RuntimeException("simulated caller-side failure after audit write");
            });
        } catch (RuntimeException expected) {
            // expected: caller transaction intentionally failed
        }

        assertThat(eligibilityCheckRepository.findByRequestId(requestId)).isPresent();
    }

    @Test
    void distinctRequestIdsProduceDistinctAuditRecordsNoAccidentalDeduplication() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = persistMember(suffix, LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));

        EligibilityCheckRequest request1 = EligibilityCheckRequest.builder()
                .memberId(member.getId()).serviceDate(LocalDate.now()).build();
        EligibilityCheckRequest request2 = EligibilityCheckRequest.builder()
                .memberId(member.getId()).serviceDate(LocalDate.now()).build();

        EligibilityResult result1 = eligibilityService.checkEligibility(request1);
        EligibilityResult result2 = eligibilityService.checkEligibility(request2);

        assertThat(result1.getRequestId()).isNotEqualTo(result2.getRequestId());
        assertThat(eligibilityCheckRepository.findByRequestId(result1.getRequestId())).isPresent();
        assertThat(eligibilityCheckRepository.findByRequestId(result2.getRequestId())).isPresent();
    }
}
