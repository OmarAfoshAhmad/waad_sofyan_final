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
}
