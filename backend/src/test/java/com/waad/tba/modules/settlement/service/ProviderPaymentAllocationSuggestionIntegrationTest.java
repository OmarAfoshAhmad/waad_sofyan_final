package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/** Proves the preview query and service against real PostgreSQL, with no writes. */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ProviderPaymentAllocationSuggestionIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired ProviderPaymentAllocationSuggestionService service;
    @Autowired ProviderPaymentRepository payments;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderRepository providers;
    @Autowired EmployerRepository employers;
    @Autowired MemberRepository members;
    @Autowired VisitRepository visits;
    @Autowired ClaimRepository claims;
    @Autowired BenefitPolicyRepository benefitPolicies;

    private Long providerId;
    private Long employerA;
    private Long employerB;
    private ProviderAccount account;
    private String suffix;
    private final Map<Long, BenefitPolicy> policiesByEmployer = new HashMap<>();

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        providerId = providers.save(Provider.builder().name("FIFO Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("FIFO-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        employerA = newEmployer("A");
        employerB = newEmployer("B");
        account = accounts.save(ProviderAccount.builder().providerId(providerId)
                .runningBalance(new BigDecimal("1000.00")).totalApproved(new BigDecimal("1000.00"))
                .totalPaid(BigDecimal.ZERO).build());

        addClaim(employerA, LocalDate.of(2026, 1, 10), "100.00", ClaimStatus.APPROVED);
        addClaim(employerB, LocalDate.of(2026, 1, 20), "300.00", ClaimStatus.BATCHED);
        addClaim(employerA, LocalDate.of(2026, 2, 5), "200.00", ClaimStatus.APPROVED);
        addClaim(employerA, LocalDate.of(2026, 8, 1), "999.00", ClaimStatus.APPROVED); // after asOf

        createPayment(ProviderPayment.Status.POSTED, employerA, "40.00", 1);
        createPayment(ProviderPayment.Status.DRAFT, employerB, "250.00", 1); // ignored
    }

    @Test
    void postedAllocationsAreSubtractedDraftsAndFutureClaimsAreIgnoredAndPreviewDoesNotWrite() {
        long paymentsBefore = payments.count();
        long allocationsBefore = payments.findAll().stream().mapToLong(p -> p.getAllocations().size()).sum();

        var result = service.suggest(providerId, new BigDecimal("180.00"), LocalDate.of(2026, 7, 31));

        // January outstanding is A=60 and B=300. A partial 180 split is 30/150.
        assertThat(result.getAllocations()).hasSize(2);
        assertThat(result.getAllocations()).extracting(a -> a.getEmployerId())
                .containsExactly(employerA, employerB);
        assertThat(result.getAllocations()).extracting(a -> a.getSuggestedAmount())
                .containsExactly(new BigDecimal("30.00"), new BigDecimal("150.00"));
        assertThat(result.getOutstandingSnapshotTotal()).isEqualByComparingTo("560.00");
        assertThat(result.getAccountVersion()).isEqualTo(account.getVersion());
        assertThat(payments.count()).isEqualTo(paymentsBefore);
        assertThat(payments.findAll().stream().mapToLong(p -> p.getAllocations().size()).sum())
                .isEqualTo(allocationsBefore);
    }

    private Long newEmployer(String marker) {
        Employer employer = employers.save(Employer.builder().name("FIFO Employer " + marker + " " + suffix)
                .code("FIFO-" + marker + "-" + suffix).active(true).build());
        BenefitPolicy policy = benefitPolicies.save(BenefitPolicy.builder()
                .name("FIFO Policy " + marker + " " + suffix)
                .policyCode("FIFO-POL-" + marker + "-" + suffix)
                .employer(employer).annualLimit(new BigDecimal("100000.00"))
                .defaultCoveragePercent(100).startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31)).status(BenefitPolicyStatus.ACTIVE)
                .active(true).build());
        policiesByEmployer.put(employer.getId(), policy);
        return employer.getId();
    }

    private void addClaim(Long employerId, LocalDate date, String amount, ClaimStatus status) {
        Employer employer = employers.findById(employerId).orElseThrow();
        Member member = members.save(Member.builder().fullName("FIFO Member " + UUID.randomUUID())
                .barcode("BC-" + UUID.randomUUID()).employer(employer)
                .benefitPolicy(policiesByEmployer.get(employerId)).active(true).build());
        Visit visit = visits.save(Visit.builder().member(member).employer(employer).providerId(providerId)
                .visitDate(date).status(VisitStatus.REGISTERED).build());
        BigDecimal value = new BigDecimal(amount);
        Claim claim = Claim.builder().claimNumber("FIFO-CLM-" + UUID.randomUUID())
                .member(member).visit(visit).providerId(providerId).serviceDate(date).status(status)
                .requestedAmount(value).approvedAmount(value).netProviderAmount(value)
                .patientCoPay(BigDecimal.ZERO).refusedAmount(BigDecimal.ZERO)
                .companyDiscountAmount(BigDecimal.ZERO).active(true).build();
        ClaimLine line = ClaimLine.builder().claim(claim).serviceCode("FIFO-SVC")
                .serviceName("FIFO Service").quantity(1).unitPrice(value).totalPrice(value)
                .requestedTotal(value).approvedAmount(value).companyShare(value)
                .patientShare(BigDecimal.ZERO).build();
        claim.setLines(List.of(line));
        claims.saveAndFlush(claim);
    }

    private void createPayment(ProviderPayment.Status status, Long employerId, String amount, int month) {
        BigDecimal value = new BigDecimal(amount);
        ProviderPayment payment = ProviderPayment.builder().providerId(providerId).amount(value)
                .paymentDate(LocalDate.of(2026, 3, 1)).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .idempotencyKey("FIFO-PAY-" + UUID.randomUUID()).status(ProviderPayment.Status.DRAFT).build();
        payment.addAllocation(ProviderPaymentAllocation.builder().employerId(employerId)
                .targetYear(2026).targetMonth(month).amount(value).outstandingAtAllocation(value).build());
        payment = payments.saveAndFlush(payment);
        if (status == ProviderPayment.Status.POSTED) {
            AccountTransaction debit = transactions.saveAndFlush(AccountTransaction.builder()
                    .providerAccountId(account.getId()).transactionType(AccountTransaction.TransactionType.DEBIT)
                    .amount(value).balanceBefore(new BigDecimal("1000.00"))
                    .balanceAfter(new BigDecimal("1000.00").subtract(value))
                    .referenceType(AccountTransaction.ReferenceType.PROVIDER_PAYMENT).referenceId(payment.getId())
                    .transactionDate(LocalDate.of(2026, 3, 1)).build());
            payment.setLedgerTransactionId(debit.getId());
            payment.setPostedAt(LocalDateTime.now());
            payment.setPostedBy("test");
            payment.setStatus(ProviderPayment.Status.POSTED);
            payments.saveAndFlush(payment);
        }
    }
}
