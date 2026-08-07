package com.waad.tba.modules.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;


/**
 * Proves the V137 constraints are enforced by PostgreSQL itself, not merely by
 * application convention.
 *
 * These are written against a real database on purpose: the whole point of
 * pushing these rules into the schema is that application-level invariants drift
 * (this codebase already had a documented case of two copies of one rule going
 * out of sync). A test that mocked the repository would prove nothing about
 * whether the constraint actually exists.
 *
 * Not @Transactional: the allocation-sum triggers are DEFERRABLE INITIALLY
 * DEFERRED, so they only fire at COMMIT. Wrapping these in a rolled-back test
 * transaction would mean the constraint under test never runs at all.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderPaymentConstraintsIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ProviderPaymentRepository paymentRepository;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private EmployerRepository employerRepository;

    private Long providerId;
    private Long employerId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        providerId = providerRepository.save(Provider.builder()
                .name("Payment Constraints Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix)
                .allowAllEmployers(true)
                .active(true)
                .build()).getId();

        employerId = employerRepository.save(Employer.builder()
                .name("Payment Constraints Co " + suffix)
                .code("EMP-" + suffix)
                .active(true)
                .build()).getId();
    }

    private ProviderPayment.ProviderPaymentBuilder draft(String amount) {
        return ProviderPayment.builder()
                .providerId(providerId)
                .amount(new BigDecimal(amount))
                .paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .status(ProviderPayment.Status.DRAFT);
    }

    private ProviderPaymentAllocation.ProviderPaymentAllocationBuilder allocation(String amount, int month) {
        return ProviderPaymentAllocation.builder()
                .employerId(employerId)
                .targetYear(2026)
                .targetMonth(month)
                .amount(new BigDecimal(amount));
    }

    // ── الثابت الأساسي: التخصيص لا يتجاوز مبلغ الدفعة ────────────────────────

    @Test
    void allocationsWithinThePaymentAmountAreAccepted() {
        ProviderPayment payment = draft("1000.00").build();
        payment.addAllocation(allocation("600.00", 1).build());
        payment.addAllocation(allocation("400.00", 2).build());

        assertThatCode(() -> {
            paymentRepository.saveAndFlush(payment);
        }).doesNotThrowAnyException();

        ProviderPayment reloaded = paymentRepository.findByIdWithAllocations(payment.getId()).orElseThrow();
        assertThat(reloaded.getAllocatedAmount()).isEqualByComparingTo("1000.00");
        assertThat(reloaded.getUnallocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(reloaded.isFullyAllocated()).isTrue();
    }

    @Test
    void partiallyAllocatedDraftIsAllowed() {
        // A draft is mid-preparation: enforcing full allocation here would make
        // drafts impossible. The completeness rule belongs to POST, not DRAFT.
        ProviderPayment payment = draft("1000.00").build();
        payment.addAllocation(allocation("250.00", 1).build());

        paymentRepository.saveAndFlush(payment);

        ProviderPayment reloaded = paymentRepository.findByIdWithAllocations(payment.getId()).orElseThrow();
        assertThat(reloaded.getAllocatedAmount()).isEqualByComparingTo("250.00");
        assertThat(reloaded.getUnallocatedAmount()).isEqualByComparingTo("750.00");
        assertThat(reloaded.isFullyAllocated()).isFalse();
    }

    @Test
    void allocationsExceedingThePaymentAmountAreRejectedByTheDatabase() {
        ProviderPayment payment = draft("500.00").build();
        payment.addAllocation(allocation("300.00", 1).build());
        payment.addAllocation(allocation("300.00", 2).build()); // 600 > 500

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("يتجاوز مبلغها");
    }

    @Test
    void loweringThePaymentAmountBelowItsAllocationsIsRejected() {
        // The invariant must hold from both directions: guarding only the
        // allocation side would leave an obvious way around it.
        ProviderPayment payment = draft("1000.00").build();
        payment.addAllocation(allocation("900.00", 1).build());
        paymentRepository.saveAndFlush(payment);

        ProviderPayment stored = paymentRepository.findById(payment.getId()).orElseThrow();
        stored.setAmount(new BigDecimal("500.00")); // below the allocated 900

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(stored);
        }).hasStackTraceContaining("لا يمكن خفض مبلغ الدفعة");
    }

    // ── قيود الحالة ──────────────────────────────────────────────────────────

    @Test
    void postedPaymentWithoutLedgerLinkIsRejected() {
        // A posted payment with no ledger entry is exactly the disconnect this
        // whole redesign exists to eliminate, so the schema forbids the state.
        ProviderPayment payment = draft("100.00")
                .status(ProviderPayment.Status.POSTED)
                .postedAt(java.time.LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("chk_provider_payments_posted_complete");
    }

    @Test
    void reversedPaymentWithoutReasonIsRejected() {
        ProviderPayment payment = draft("100.00")
                .status(ProviderPayment.Status.REVERSED)
                .reversedAt(java.time.LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("chk_provider_payments_reversed_complete");
    }

    @Test
    void negativeOrZeroAmountIsRejected() {
        ProviderPayment payment = draft("0.00").build();

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("provider_payments_amount_check");
    }

    // ── منع التكرار والازدواج ────────────────────────────────────────────────

    @Test
    void duplicateIdempotencyKeyIsRejected() {
        String key = "IDEMP-" + UUID.randomUUID();
        paymentRepository.saveAndFlush(draft("100.00").idempotencyKey(key).build());

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(draft("200.00").idempotencyKey(key).build());
        }).hasStackTraceContaining("ux_provider_payments_idempotency");
    }

    @Test
    void severalPaymentsMayShareABankReferenceWhenIdempotencyKeysDiffer() {
        // reference_number is a commercial reference, not a request key. The old
        // path conflated the two and rejected legitimate transfers that happened
        // to share a reference.
        String sharedReference = "BANK-REF-" + UUID.randomUUID();

        assertThatCode(() -> {
            paymentRepository.saveAndFlush(draft("100.00").referenceNumber(sharedReference).build());
            paymentRepository.saveAndFlush(draft("200.00").referenceNumber(sharedReference).build());
        }).doesNotThrowAnyException();
    }

    @Test
    void duplicateAllocationTargetWithinOnePaymentIsRejected() {
        ProviderPayment payment = draft("1000.00").build();
        payment.addAllocation(allocation("100.00", 3).build());
        payment.addAllocation(allocation("200.00", 3).build()); // same employer/year/month

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("ux_allocation_payment_target");
    }

    @Test
    void manualAllocationWithoutOverrideReasonIsRejected() {
        // Departing from the FIFO proposal is an accounting decision and must be
        // justified, so the reason is required by the schema rather than the UI.
        ProviderPayment payment = draft("500.00").build();
        payment.addAllocation(allocation("100.00", 4)
                .allocationMethod(ProviderPaymentAllocation.AllocationMethod.MANUAL)
                .build());

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("chk_allocation_manual_needs_reason");
    }

    @Test
    void manualAllocationWithOverrideReasonIsAccepted() {
        ProviderPayment payment = draft("500.00").build();
        payment.addAllocation(allocation("100.00", 5)
                .allocationMethod(ProviderPaymentAllocation.AllocationMethod.MANUAL)
                .overrideReason("تسوية متفق عليها مع المزود")
                .build());

        assertThatCode(() -> {
            paymentRepository.saveAndFlush(payment);
        }).doesNotThrowAnyException();
    }

    @Test
    void invalidTargetMonthIsRejected() {
        ProviderPayment payment = draft("500.00").build();
        payment.addAllocation(allocation("100.00", 13).build());

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment);
        }).hasStackTraceContaining("target_month");
    }
}
