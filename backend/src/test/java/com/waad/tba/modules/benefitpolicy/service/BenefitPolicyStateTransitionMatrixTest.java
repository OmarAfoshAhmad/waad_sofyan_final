package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyStatusHistoryRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * P-02: the full state-transition matrix for BenefitPolicyStatus, proved
 * rather than assumed -- every {source status} x {action} combination this
 * service actually implements, not just the handful exercised by other
 * tests in passing.
 *
 * This does not judge whether the current matrix is the RIGHT one (e.g.
 * whether reactivating an EXPIRED policy directly, without passing through
 * DRAFT, is a deliberate business rule or a gap) -- that is a product
 * decision outside this closure round's authority. It records the actual,
 * current behavior precisely enough that any future change to it is a
 * visible, intentional diff here, not a silent regression.
 */
@ExtendWith(MockitoExtension.class)
class BenefitPolicyStateTransitionMatrixTest {

    @Mock private BenefitPolicyRepository benefitPolicyRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Mock private BenefitLimitBucketRepository benefitLimitBucketRepository;
    @Mock private com.waad.tba.modules.claim.repository.ClaimRepository claimRepository;
    @Mock private com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository preAuthorizationRepository;
    @Mock private com.waad.tba.modules.systemadmin.service.AuditLogService auditLogService;
    @Mock private com.waad.tba.security.AuthorizationService authorizationService;
    @Mock private BenefitPolicyStatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private BenefitPolicyService benefitPolicyService;

    private Employer employer;

    @BeforeEach
    void setUp() {
        employer = Employer.builder().id(1L).name("Employer").active(true).build();
        // Lenient: only the "allowed" cases in this matrix ever reach save();
        // the "denied" cases throw first, which would otherwise fail Mockito's
        // strict-stubbing check for an unused stub.
        org.mockito.Mockito.lenient().when(benefitPolicyRepository.save(any(BenefitPolicy.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private BenefitPolicy policyWith(BenefitPolicyStatus status) {
        return BenefitPolicy.builder()
                .id(10L).name("Policy").policyCode("POL-2026-001").employer(employer)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .annualLimit(new BigDecimal("10000")).defaultCoveragePercent(80)
                .status(status).active(true).build();
    }

    private void stubReadyForActivation(BenefitPolicy policy) {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(benefitPolicyRepository.existsOverlappingActivePolicy(eq(1L), any(), any(), eq(10L)))
                .thenReturn(false);
        when(benefitPolicyRuleRepository.countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(1L);
        when(benefitLimitBucketRepository.findByPolicyIdOrderByCode(10L))
                .thenReturn(java.util.List.<BenefitLimitBucket>of());
    }

    private void stubNoFinancialLinkage() {
        when(claimRepository.countByPolicyId(10L)).thenReturn(0L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);
    }

    // ── activate(): denied only from CANCELLED ──────────────────────────────

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
    void activateIsAllowedFromEveryStatusExceptCancelled(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        stubReadyForActivation(policy);

        assertThatCode(() -> benefitPolicyService.activate(10L)).doesNotThrowAnyException();
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.ACTIVE);
    }

    @Test
    void activateIsDeniedFromCancelled() {
        BenefitPolicy policy = policyWith(BenefitPolicyStatus.CANCELLED);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> benefitPolicyService.activate(10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── deactivate(): allowed only from ACTIVE or SUSPENDED ─────────────────

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = {"ACTIVE", "SUSPENDED"})
    void deactivateIsAllowedFromActiveOrSuspended(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatCode(() -> benefitPolicyService.deactivate(10L)).doesNotThrowAnyException();
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.EXPIRED);
    }

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = {"ACTIVE", "SUSPENDED"}, mode = EnumSource.Mode.EXCLUDE)
    void deactivateIsDeniedFromEverythingElse(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> benefitPolicyService.deactivate(10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── suspend(): allowed only from ACTIVE ─────────────────────────────────

    @Test
    void suspendIsAllowedFromActive() {
        BenefitPolicy policy = policyWith(BenefitPolicyStatus.ACTIVE);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatCode(() -> benefitPolicyService.suspend(10L)).doesNotThrowAnyException();
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.SUSPENDED);
    }

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void suspendIsDeniedFromEverythingElse(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> benefitPolicyService.suspend(10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── revertToDraft(): allowed only from ACTIVE or SUSPENDED, and only
    //    when the policy has no financial linkage ───────────────────────────

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = {"ACTIVE", "SUSPENDED"})
    void revertToDraftIsAllowedFromActiveOrSuspendedWithNoLinkage(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        stubNoFinancialLinkage();

        assertThatCode(() -> benefitPolicyService.revertToDraft(10L)).doesNotThrowAnyException();
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.DRAFT);
    }

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = {"ACTIVE", "SUSPENDED"}, mode = EnumSource.Mode.EXCLUDE)
    void revertToDraftIsDeniedFromEverythingElse(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> benefitPolicyService.revertToDraft(10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void revertToDraftIsDeniedWhenFinanciallyLinkedEvenFromActive() {
        BenefitPolicy policy = policyWith(BenefitPolicyStatus.ACTIVE);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(1L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);

        assertThatThrownBy(() -> benefitPolicyService.revertToDraft(10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── cancel(): denied only when already CANCELLED ────────────────────────

    @ParameterizedTest
    @EnumSource(value = BenefitPolicyStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
    void cancelIsAllowedFromEveryStatusExceptAlreadyCancelled(BenefitPolicyStatus source) {
        BenefitPolicy policy = policyWith(source);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatCode(() -> benefitPolicyService.cancel(10L)).doesNotThrowAnyException();
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.CANCELLED);
    }

    @Test
    void cancelIsDeniedWhenAlreadyCancelled() {
        BenefitPolicy policy = policyWith(BenefitPolicyStatus.CANCELLED);
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> benefitPolicyService.cancel(10L))
                .isInstanceOf(BusinessRuleException.class);
    }
}
