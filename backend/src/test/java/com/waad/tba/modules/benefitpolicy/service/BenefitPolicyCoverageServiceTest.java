package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecision;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionSource;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitPolicyCoverageServiceTest {

    @Mock
    private BenefitPolicyRepository policyRepository;
    @Mock
    private BenefitPolicyRuleRepository ruleRepository;
    @Mock
    private MedicalServiceRepository serviceRepository;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MedicalCategoryRepository categoryRepository;
    @Mock
    private MedicalServiceCategoryRepository serviceCategoryRepository;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private CoverageDecisionService coverageDecisionService;

    @InjectMocks
    private BenefitPolicyCoverageService coverageService;

    private Member testMember;
    private BenefitPolicy testPolicy;
    private MedicalService testService;

    @BeforeEach
    void setUp() {
        testPolicy = BenefitPolicy.builder()
                .id(1L)
                .name("Standard Plan")
                .status(BenefitPolicyStatus.ACTIVE)
                .active(true)
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().plusMonths(6))
                .annualLimit(new BigDecimal("10000.00"))
                .defaultCoveragePercent(80)
                .build();

        testMember = Member.builder()
                .id(1L)
                .fullName("John Doe")
                .benefitPolicy(testPolicy)
                .build();

        testService = MedicalService.builder()
                .id(101L)
                .code("SRV001")
                .name("Consultation")
                .build();
    }

    @Test
    @DisplayName("Should validate active policy successfully")
    void validateMemberHasActivePolicy_Success() {
        assertDoesNotThrow(() -> coverageService.validateMemberHasActivePolicy(testMember, LocalDate.now()));
    }

    @Test
    @DisplayName("Should throw exception when member has no policy and auto-resolve fails")
    void validateMemberHasActivePolicy_NoPolicy() {
        testMember.setBenefitPolicy(null);
        
        assertThrows(BusinessRuleException.class, () -> 
            coverageService.validateMemberHasActivePolicy(testMember, LocalDate.now()));
    }

    @Test
    @DisplayName("Should get coverage from specific service rule")
    void getCoverageForService_ServiceRuleMatch() {
        // Arrange
        MedicalCategory category = MedicalCategory.builder().id(55L).code("CAT-55").active(true).build();
        BenefitPolicyRule serviceRule = BenefitPolicyRule.builder()
                .id(10L)
                .medicalCategory(category)
                .coveragePercent(90)
                .active(true)
                .requiresPreApproval(true)
                .build();

        when(serviceRepository.findById(101L)).thenReturn(Optional.of(testService));
        when(coverageDecisionService.resolve(any())).thenReturn(coveredDecision(serviceRule, 55L));

        // Act
        Optional<BenefitPolicyCoverageService.CoverageInfo> result = coverageService.getCoverageForService(testMember, 101L, 55L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(90, result.get().getCoveragePercent());
        assertTrue(result.get().isRequiresPreApproval());
        assertEquals("EXACT_CATEGORY_RULE", result.get().getRuleType());
    }

    @Test
    @DisplayName("Should fail closed when no contextual benefit rule exists")
    void getCoverageForService_NoRuleIsNotCovered() {
        // Arrange
        MedicalCategory category = MedicalCategory.builder().id(55L).code("CAT-55").active(true).build();
        when(serviceRepository.findById(101L)).thenReturn(Optional.of(testService));
        when(coverageDecisionService.resolve(any())).thenReturn(notCoveredDecision());

        // Act
        Optional<BenefitPolicyCoverageService.CoverageInfo> result = coverageService.getCoverageForService(testMember, 101L, 55L);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should select different rules for outpatient and inpatient context")
    void getCoverageForCategory_SelectsRuleByEncounterContext() {
        MedicalCategory category = MedicalCategory.builder().id(55L).code("CAT-55").active(true).build();
        BenefitPolicyRule outpatientRule = BenefitPolicyRule.builder()
                .id(20L).medicalCategory(category).coveragePercent(80).active(true).build();
        BenefitPolicyRule inpatientRule = BenefitPolicyRule.builder()
                .id(21L).medicalCategory(category).coveragePercent(100).active(true).build();

        when(coverageDecisionService.resolve(argThat(request -> request != null
                && request.encounterType() == EncounterType.OUTPATIENT)))
                .thenReturn(coveredDecision(outpatientRule, 55L));
        when(coverageDecisionService.resolve(argThat(request -> request != null
                && request.encounterType() == EncounterType.INPATIENT)))
                .thenReturn(coveredDecision(inpatientRule, 55L));

        var outpatient = coverageService.getCoverageForCategory(
                testMember, 55L, EncounterType.OUTPATIENT, LocalDate.now());
        var inpatient = coverageService.getCoverageForCategory(
                testMember, 55L, EncounterType.INPATIENT, LocalDate.now());

        assertEquals(80, outpatient.orElseThrow().getCoveragePercent());
        assertEquals(100, inpatient.orElseThrow().getCoveragePercent());
    }

    @Test
    @DisplayName("Batch category coverage must fail closed instead of using policy default")
    void batchCategoryCoverage_NoContextualRuleReturnsZero() {
        MedicalCategory category = MedicalCategory.builder().id(55L).code("CAT-55").active(true).build();
        when(coverageDecisionService.resolve(any())).thenReturn(notCoveredDecision());

        var result = coverageService.batchGetCoveragePercentsByCategory(
                testMember, java.util.List.of(55L), EncounterType.INPATIENT, LocalDate.now());

        assertEquals(0, result.get(55L));
    }

    @Test
    @DisplayName("Free-text service without canonical ID must never receive policy default coverage")
    void validateClaimCoverage_FreeTextServiceFailsClosed() {
        var input = BenefitPolicyCoverageService.ServiceCoverageInput.builder()
                .serviceName("خدمة مكتوبة يدويا")
                .amount(new BigDecimal("100.00"))
                .build();

        var result = coverageService.validateClaimCoverage(testMember, java.util.List.of(input), LocalDate.now());

        assertFalse(result.isValid());
        assertFalse(result.getServiceResults().getFirst().isCovered());
        assertEquals(0, result.getServiceResults().getFirst().getCoveragePercent());
        assertTrue(result.getServiceResults().getFirst().getReason().contains("غير مرتبطة"));
    }

    @Test
    @DisplayName("Should throw exception if annual limit exceeded")
    void validateAmountLimits_Exceeded() {
        // Arrange
        when(claimRepository.sumApprovedAmountByMemberAndYear(anyLong(), anyInt(), anyList()))
                .thenReturn(new BigDecimal("9500.00")); // Spent 9500 of 10000

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> 
            coverageService.validateAmountLimits(testMember, testPolicy, new BigDecimal("600.00"), LocalDate.now()));
    }

    @Test
    @DisplayName("Should respect waiting period if not met")
    void validateWaitingPeriods_NotMet() {
        // Arrange
        testPolicy.setDefaultWaitingPeriodDays(30);
        testMember.setStartDate(LocalDate.now().minusDays(10)); // Only 10 days since enrollment

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> 
            coverageService.validateWaitingPeriods(testMember, testPolicy, null, LocalDate.now()));
    }

    private CoverageDecision coveredDecision(BenefitPolicyRule rule, Long categoryId) {
        return CoverageDecision.builder().covered(true).coveragePercent(rule.getEffectiveCoveragePercent())
                .resolvedCategoryId(categoryId).matchingCategoryId(categoryId)
                .source(CoverageDecisionSource.EXACT_CATEGORY_RULE).reasonCode("COVERED")
                .appliedRule(BenefitPolicyRuleResponseDto.fromEntity(rule)).build();
    }

    private CoverageDecision notCoveredDecision() {
        return CoverageDecision.builder().covered(false).coveragePercent(0)
                .source(CoverageDecisionSource.NO_BENEFIT_RULE).reasonCode("NO_BENEFIT_RULE").build();
    }
}
