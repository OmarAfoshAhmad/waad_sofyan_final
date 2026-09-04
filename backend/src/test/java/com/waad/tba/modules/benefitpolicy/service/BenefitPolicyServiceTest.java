package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyCreateDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyResponseDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyUpdateDto;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class BenefitPolicyServiceTest {

    @Mock
    private BenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Mock
    private BenefitLimitBucketRepository benefitLimitBucketRepository;
    @Mock
    private com.waad.tba.modules.claim.repository.ClaimRepository claimRepository;
    @Mock
    private com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository preAuthorizationRepository;
    @Mock
    private com.waad.tba.modules.systemadmin.service.AuditLogService auditLogService;
    @Mock
    private com.waad.tba.security.AuthorizationService authorizationService;
    @Mock
    private com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyStatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private BenefitPolicyService benefitPolicyService;

    private Employer employer;
    private BenefitPolicy policy;

    @BeforeEach
    void setUp() {
        employer = Employer.builder()
                .id(1L)
                .name("Test Employer")
                .active(true)
                .build();

        policy = BenefitPolicy.builder()
                .id(10L)
                .name("Standard Plan 2026")
                .policyCode("POL-2026-001")
                .employer(employer)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .annualLimit(new BigDecimal("10000"))
                .status(BenefitPolicyStatus.DRAFT)
                .active(true)
                .build();
    }

    @Test
    void create_validDraft_shouldSaveAndReturnDto() {
        // Arrange
        BenefitPolicyCreateDto dto = BenefitPolicyCreateDto.builder()
                .name("New Policy")
                .employerOrgId(1L)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .annualLimit(new BigDecimal("5000"))
                .status("DRAFT")
                .build();

        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(benefitPolicyRepository.save(any(BenefitPolicy.class))).thenAnswer(i -> {
            BenefitPolicy saved = i.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        // Act
        BenefitPolicyResponseDto result = benefitPolicyService.create(dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Policy");
        assertThat(result.getPolicyCode()).startsWith("POL-");
        verify(benefitPolicyRepository, times(1)).save(any(BenefitPolicy.class));
    }

    @Test
    void create_invalidDates_shouldThrowException() {
        // Arrange
        BenefitPolicyCreateDto dto = BenefitPolicyCreateDto.builder()
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31)) // End before start
                .build();

        // Act & Assert
        assertThatThrownBy(() -> benefitPolicyService.create(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Start date must be before end date");
    }

    @Test
    void create_activePolicy_shouldRequireDraftFirst() {
        // Arrange
        BenefitPolicyCreateDto dto = BenefitPolicyCreateDto.builder()
                .name("Active Policy")
                .employerOrgId(1L)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status("ACTIVE")
                .build();

        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));

        // Act & Assert
        assertThatThrownBy(() -> benefitPolicyService.create(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("يجب إنشاء وثيقة التغطية كمسودة");
    }

    @Test
    void activate_shouldChangeStatusToActive() {
        // Arrange
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(benefitPolicyRepository.existsOverlappingActivePolicy(eq(1L), any(), any(), eq(10L)))
                .thenReturn(false);
        when(benefitPolicyRuleRepository.countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(1L);
        when(benefitLimitBucketRepository.findByPolicyIdOrderByCode(10L))
                .thenReturn(java.util.List.of());
        when(benefitPolicyRepository.save(any(BenefitPolicy.class))).thenReturn(policy);

        // Act
        BenefitPolicyResponseDto result = benefitPolicyService.activate(10L);

        // Assert
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.ACTIVE);
        verify(benefitPolicyRepository).save(policy);
    }

    /**
     * Lifecycle transitions and the create/update paths change what claims and
     * coverage resolution see for a policy's members, but were never recorded
     * anywhere queryable -- only a log line. Proves each mutation actually
     * reaches AuditLogService.createAuditLog, not merely that its own dependency
     * exists (a stub with no assertion would pass even if the audit call were
     * silently removed).
     */
    @Test
    void create_activate_andDelete_eachWriteToTheAuditTrail() {
        BenefitPolicyCreateDto createDto = BenefitPolicyCreateDto.builder()
                .name("Audited Plan")
                .employerOrgId(1L)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .annualLimit(new BigDecimal("5000"))
                .status("DRAFT")
                .build();
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(benefitPolicyRepository.save(any(BenefitPolicy.class))).thenAnswer(i -> i.getArgument(0));

        benefitPolicyService.create(createDto);

        verify(auditLogService).createAuditLog(
                eq("CREATED"), eq("BENEFIT_POLICY"), any(), any(), any(), any(), any(), any());

        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(benefitPolicyRepository.existsOverlappingActivePolicy(eq(1L), any(), any(), eq(10L)))
                .thenReturn(false);
        when(benefitPolicyRuleRepository.countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(1L);
        when(benefitLimitBucketRepository.findByPolicyIdOrderByCode(10L)).thenReturn(java.util.List.of());

        benefitPolicyService.activate(10L);

        verify(auditLogService).createAuditLog(
                eq("ACTIVATED"), eq("BENEFIT_POLICY"), eq(10L), any(), any(), any(), any(), any());

        when(memberRepository.countByBenefitPolicyIdAndActiveTrue(10L)).thenReturn(0L);

        benefitPolicyService.delete(10L);

        verify(auditLogService).createAuditLog(
                eq("DELETED"), eq("BENEFIT_POLICY"), eq(10L), any(), any(), any(), any(), any());
    }

    @Test
    void activate_groupWithoutLimit_shouldBeAllowedForOrganizationalGroup() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(benefitPolicyRepository.existsOverlappingActivePolicy(eq(1L), any(), any(), eq(10L))).thenReturn(false);
        when(benefitPolicyRuleRepository.countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(1L);
        when(benefitLimitBucketRepository.findByPolicyIdOrderByCode(10L)).thenReturn(java.util.List.of(
                BenefitLimitBucket.builder().code("AUTO-GRP-GRP-0001").active(true).build()));
        when(benefitPolicyRepository.save(any(BenefitPolicy.class))).thenReturn(policy);

        benefitPolicyService.activate(10L);

        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.ACTIVE);
    }

    @Test
    void activate_emptyAdvancedLimit_shouldBeRejectedWithUserFacingMessage() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(benefitPolicyRuleRepository.countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(1L);
        when(benefitLimitBucketRepository.findByPolicyIdOrderByCode(10L)).thenReturn(java.util.List.of(
                BenefitLimitBucket.builder().code("ADVANCED-001").active(true).build()));

        assertThatThrownBy(() -> benefitPolicyService.activate(10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("سياسة سقف متقدمة");
    }

    @Test
    void delete_withMembers_shouldThrowException() {
        // Arrange
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(memberRepository.countByBenefitPolicyIdAndActiveTrue(10L)).thenReturn(5L);

        // Act & Assert
        assertThatThrownBy(() -> benefitPolicyService.delete(10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("أنهِ تسجيل المستفيدين");
    }

    @Test
    void delete_noMembers_shouldSoftDelete() {
        // Arrange
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(memberRepository.countByBenefitPolicyIdAndActiveTrue(10L)).thenReturn(0L);

        // Act
        benefitPolicyService.delete(10L);

        // Assert
        assertThat(policy.isActive()).isFalse();
        assertThat(policy.getStatus()).isEqualTo(BenefitPolicyStatus.CANCELLED);
        verify(benefitPolicyRepository).save(policy);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Regression coverage: a policy must stay permanently locked once ANY
    // claim/pre-auth was ever created against it — including cancelled
    // (soft-deleted) ones. Previously canPolicyBeEdited counted only
    // active=true rows, so cancelling the one claim that existed would
    // silently re-open a live policy's rules/limits for editing.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void update_policyWithAnyEverLinkedClaim_isBlockedEvenIfThatClaimWasCancelled() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        // countByPolicyId now counts regardless of active/cancelled status —
        // simulate a policy whose only claim was later cancelled.
        when(claimRepository.countByPolicyId(10L)).thenReturn(1L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);

        BenefitPolicyUpdateDto dto = BenefitPolicyUpdateDto.builder().name("Renamed").build();

        assertThatThrownBy(() -> benefitPolicyService.update(10L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يمكن تعديل");
    }

    // ═══════════════════════════════════════════════════════════════════
    // V219: a claim whose historical policy_id could never be attributed
    // (LEGACY_UNRESOLVED) names no policy_id, so countByPolicyId cannot see
    // it against ANY policy -- including the one it might actually belong
    // to. Editing that policy would look safe purely because the blocking
    // claim is unattributed, not because it doesn't exist. Every policy of
    // the affected employer must stay locked until that claim is reviewed.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void update_policyWithAnUnresolvedLegacyClaimSomewhereInTheEmployer_isBlocked() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(0L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);
        when(claimRepository.existsUnresolvedLegacyClaimForEmployer(employer.getId())).thenReturn(true);

        BenefitPolicyUpdateDto dto = BenefitPolicyUpdateDto.builder().name("Renamed").build();

        assertThatThrownBy(() -> benefitPolicyService.update(10L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("مطالبات تاريخية غير محسومة");
    }

    @Test
    void update_policyNeverLinkedToAnyClaim_isEditable() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(0L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);
        when(benefitPolicyRepository.save(any(BenefitPolicy.class))).thenAnswer(i -> i.getArgument(0));

        BenefitPolicyUpdateDto dto = BenefitPolicyUpdateDto.builder().name("Renamed").build();

        BenefitPolicyResponseDto result = benefitPolicyService.update(10L, dto);

        assertThat(result.getName()).isEqualTo("Renamed");
    }

    @Test
    void update_attemptingStatusTransition_isRejectedRegardlessOfLinkage() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(0L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);

        BenefitPolicyUpdateDto dto = BenefitPolicyUpdateDto.builder().status("ACTIVE").build();

        assertThatThrownBy(() -> benefitPolicyService.update(10L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("التعديل المباشر");
    }

    @Test
    void update_statusFieldEqualToCurrentStatus_isANoOpNotAnError() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(0L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);
        when(benefitPolicyRepository.save(any(BenefitPolicy.class))).thenAnswer(i -> i.getArgument(0));

        // policy is DRAFT in setUp(); round-tripping the same value must not throw.
        BenefitPolicyUpdateDto dto = BenefitPolicyUpdateDto.builder().status("DRAFT").build();

        BenefitPolicyResponseDto result = benefitPolicyService.update(10L, dto);

        assertThat(result).isNotNull();
    }

    @Test
    void assertDraftConfiguration_blocksEvenWhenPolicyStatusIsStillDraft_ifEverFinanciallyLinked() {
        // Financial-linkage check must be unconditional, not gated on
        // status != DRAFT — a policy that somehow carries claim history
        // while still marked DRAFT must not be exempted.
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(1L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);

        assertThatThrownBy(() -> benefitPolicyService.assertDraftConfiguration(10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void assertDraftConfiguration_allowsWhenNeverLinked() {
        when(benefitPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(claimRepository.countByPolicyId(10L)).thenReturn(0L);
        when(preAuthorizationRepository.countByPolicyId(10L)).thenReturn(0L);

        benefitPolicyService.assertDraftConfiguration(10L);
        // no exception
    }
}
