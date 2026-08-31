package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecision;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionSource;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionRequest;
import com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService.LimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.CoverageDecisionService;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Acceptance scenarios derived from the July 2026 field observations.
 *
 * Monetary limits here are fixtures proving configurability; they are not
 * production defaults and must remain policy/bucket data rather than constants.
 */
@ExtendWith(MockitoExtension.class)
class CoverageEngineServiceTest {

    @Mock CoverageDecisionService decisionService;
    @Mock ProviderContractPricingItemRepository pricingItemRepository;

    private CoverageEngineService engine;
    private Long configuredRuleId;
    private int configuredPercent;
    private boolean configuredPreApproval;
    private List<CoverageLimitSnapshot> configuredLimits = List.of();

    @BeforeEach
    void setUp() {
        engine = new CoverageEngineService(decisionService, pricingItemRepository);
    }

    @Test
    @DisplayName("عيادات خارجية: تغطية 80% ينتج عنها تحمل 20%")
    void outpatientCoverageAppliesTwentyPercentMemberShare() {
        coveredByRule(10L, 80, false);

        CoverageResult result = calculate(line("OP-LAB", "100.00"), EncounterType.OUTPATIENT);

        assertMoney("80.00", result.getCompanyShare());
        assertMoney("20.00", result.getPatientShare());
        assertFalse(result.isNotCovered());
    }

    @Test
    @DisplayName("إيواء: التغطية الكاملة لا تطبق تحمل العيادات الخارجية")
    void inpatientFullCoverageDoesNotApplyOutpatientCopay() {
        coveredByRule(11L, 100, false);

        CoverageResult result = calculate(line("IP-LAB", "100.00"), EncounterType.INPATIENT);

        assertMoney("100.00", result.getCompanyShare());
        assertMoney("0.00", result.getPatientShare());
    }

    @Test
    @DisplayName("دواء: دفع مشترك 25% يحسب من المبلغ المسموح")
    void medicationTwentyFivePercentCopay() {
        coveredByRule(12L, 75, false);

        CoverageResult result = calculate(line("MED-25", "100.00"), EncounterType.OUTPATIENT);

        assertMoney("75.00", result.getCompanyShare());
        assertMoney("25.00", result.getPatientShare());
    }

    @Test
    @DisplayName("خدمة غير مصنفة أو دون قاعدة تفشل بأمان ولا تعتمد مالياً")
    void unclassifiedServiceFailsClosed() {
        when(decisionService.resolve(any())).thenReturn(CoverageDecision.builder()
                .covered(false).coveragePercent(0).source(CoverageDecisionSource.NO_BENEFIT_RULE)
                .reasonCode("NO_BENEFIT_RULE").build());

        CoverageResult result = calculate(line("AMBIGUOUS", "250.00"), EncounterType.OUTPATIENT);

        assertTrue(result.isNotCovered());
        assertMoney("0.00", result.getCompanyShare());
        assertMoney("250.00", result.getPatientShare());
        assertEquals("لا توجد قاعدة تغطية فعالة للتصنيف في سياق المطالبة OUTPATIENT",
                result.getRefusalReason());
    }

    @Test
    @DisplayName("MRI: سقف 1500 والمستهلك 1300 يقبل 200 فقط من طلب 400")
    void mriSubLimitPartiallyCapsTheLine() {
        coveredByRule(20L, 100, false);
        useLimits(limit(201L, "سقف الرنين", "1500.00", "1300.00"));

        CoverageResult result = calculate(line("MRI", "400.00"), EncounterType.OUTPATIENT);

        assertMoney("200.00", result.getCompanyShare());
        assertMoney("200.00", result.getLimitRefused());
        assertMoney("0.00", result.getPatientShare());
        assertTrue(result.getUsageDetails().isAmountExceeded());
    }

    @Test
    @DisplayName("السقف يطبق قبل التحمل حتى لا يدفع المستفيد عن الجزء المتجاوز")
    void benefitCapIsAppliedBeforeCopaySplit() {
        coveredByRule(21L, 80, false);
        useLimits(limit(202L, "سقف فرعي", "100.00", "0.00"));

        CoverageResult result = calculate(line("PARTIAL-CAP", "140.00"), EncounterType.OUTPATIENT);

        assertMoney("80.00", result.getCompanyShare());
        assertMoney("20.00", result.getPatientShare());
        assertMoney("40.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("عدة خدمات في المطالبة تتشارك الرصيد ولا تتجاوز السقف")
    void bulkLinesShareOneRemainingBalance() {
        coveredByRule(30L, 100, false);
        useLimits(limit(301L, "السقف السنوي", "100.00", "0.00"));

        List<CoverageResult> results = calculateBulk(
                List.of(line("ANNUAL-1", "70.00"), line("ANNUAL-2", "70.00")),
                EncounterType.OUTPATIENT);

        assertMoney("70.00", results.get(0).getCompanyShare());
        assertMoney("30.00", results.get(1).getCompanyShare());
        assertMoney("40.00", results.get(1).getLimitRefused());
        assertMoney("100.00", results.stream()
                .map(CoverageResult::getCompanyShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    @DisplayName("أسنان: السقف المستنفد يرفض حصة الشركة بالكامل")
    void exhaustedDentalLimitRejectsCompanyShare() {
        coveredByRule(40L, 50, false);
        useLimits(limit(401L, "سقف الأسنان لكل مستفيد", "2000.00", "2000.00"));

        CoverageResult result = calculate(line("DENTAL", "300.00"), EncounterType.OUTPATIENT);

        assertMoney("0.00", result.getCompanyShare());
        assertMoney("0.00", result.getPatientShare());
        assertMoney("300.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("العلاج الطبيعي: مثال سقف 10000 يعمل كبيانات وثيقة")
    void physiotherapyLimitIsPolicyData() {
        coveredByRule(50L, 100, false);
        useLimits(limit(501L, "سقف العلاج الطبيعي", "10000.00", "9800.00"));

        CoverageResult result = calculate(line("PHYSIO", "500.00"), EncounterType.OUTPATIENT);

        assertMoney("200.00", result.getCompanyShare());
        assertMoney("300.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("الولادة: مثال سقف 4000 يقبل المتبقي فقط")
    void maternityLimitAcceptsOnlyRemainingAmount() {
        coveredByRule(60L, 100, true);
        useLimits(limit(601L, "سقف الولادة", "4000.00", "3500.00"));

        CoverageResult result = calculate(line("MATERNITY", "1000.00"), EncounterType.INPATIENT);

        assertMoney("500.00", result.getCompanyShare());
        assertMoney("500.00", result.getLimitRefused());
        assertTrue(result.isRequiresPreApproval());
    }

    @Test
    @DisplayName("الولادة: سقف 4000 على إجمالي الخدمة يسبق تحمل 25% في مثال دار الشفاء")
    void darAlShifaMaternityExampleCapsEligibleAmountBeforeCopay() {
        coveredByRule(61L, 75, false);
        useLimits(limit(602L, "سقف الولادة", "4000.00", "0.00"));

        List<CoverageResult> results = calculateBulk(List.of(
                line("F-006", "1650.00"),
                line("WE-001-X10", "3000.00")), EncounterType.INPATIENT);

        assertMoney("1237.50", results.get(0).getCompanyShare());
        assertMoney("412.50", results.get(0).getPatientShare());
        assertMoney("1762.50", results.get(1).getCompanyShare());
        assertMoney("587.50", results.get(1).getPatientShare());
        assertMoney("650.00", results.get(1).getLimitRefused());
        assertMoney("3000.00", results.stream().map(CoverageResult::getCompanyShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals("ELIGIBLE_AMOUNT", results.get(1).getUsageDetails().getConsumptionBasis());
        assertMoney("1650.00", results.get(1).getUsageDetails().getUsedAmountBeforeLine());
        assertMoney("3000.00", results.get(1).getUsageDetails().getRequestedAmountForLimit());
        assertMoney("2350.00", results.get(1).getUsageDetails().getApprovedAmountForLimit());
    }

    @Test
    @DisplayName("العمليات والإيواء: مثال سقف 15000 يمنع التجاوز")
    void inpatientAndSurgeryLimitStopsExcess() {
        coveredByRule(70L, 100, false);
        useLimits(limit(701L, "العمليات والإيواء", "15000.00", "14500.00"));

        CoverageResult result = calculate(line("IP-SURGERY", "2000.00"), EncounterType.INPATIENT);

        assertMoney("500.00", result.getCompanyShare());
        assertMoney("1500.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("فرق سعر العقد يرفض على المزود ولا يرفع تحمل المستفيد")
    void contractPriceExcessDoesNotIncreasePatientShare() {
        coveredByRule(80L, 80, false);
        ClaimLineInput line = line("CONTRACT-PRICE", "120.00");
        line.setContractPrice(new BigDecimal("100.00"));

        CoverageResult result = calculate(line, EncounterType.OUTPATIENT);

        assertMoney("20.00", result.getPriceRefused());
        assertMoney("20.00", result.getPatientShare());
        assertMoney("80.00", result.getCompanyShare());
    }

    @Test
    @DisplayName("التغطية الكاملة تلغي التحمل ولا تتجاوز سقف المنفعة")
    void explicitFullCoverageKeepsBenefitLimits() {
        coveredByRule(92L, 75, false);
        useLimits(new LimitSnapshot(
                921L, "سقف الاستثناء", new BigDecimal("500.00"), null, null,
                new BigDecimal("400.00"), 0, 0, false,
                CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT));
        BulkCoverageEngineRequest request = request(
                List.of(line("FULL-COVERAGE", "200.00")), EncounterType.INPATIENT);
        request.setFullCoverage(true);
        request.setClaimContextCode("FULL_COVERAGE");

        CoverageResult result = engine.calculateBulk(request).get(0);

        assertMoney("100.00", result.getCompanyShare());
        assertMoney("0.00", result.getPatientShare());
        assertMoney("100.00", result.getLimitRefused());
        ArgumentCaptor<CoverageDecisionRequest> decisionRequest = ArgumentCaptor.forClass(CoverageDecisionRequest.class);
        verify(decisionService).resolve(decisionRequest.capture());
        assertEquals("INPATIENT", decisionRequest.getValue().claimContextCode());
    }

    @Test
    @DisplayName("تجاوز عدد الجلسات يوقف الخدمة بالكامل")
    void timesLimitHardStopsTheLine() {
        coveredByRule(90L, 100, false);
        useLimits(new LimitSnapshot(
                901L, "عدد جلسات العلاج الطبيعي", null, 5, null,
                BigDecimal.ZERO, 5, 0, false,
                CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT));

        CoverageResult result = calculate(line("SESSION-6", "120.00"), EncounterType.OUTPATIENT);

        assertMoney("0.00", result.getCompanyShare());
        assertMoney("120.00", result.getLimitRefused());
        assertTrue(result.getUsageDetails().isTimesExceeded());
    }

    @Test
    @DisplayName("بنود العلاج الطبيعي المتعددة في المطالبة الواحدة تستهلك زيارة واحدة")
    void perVisitCountsTheWholeClaimOnce() {
        coveredByRule(96L, 75, false);
        useLimits(new LimitSnapshot(
                961L, "العلاج الطبيعي", null, 20, null,
                BigDecimal.ZERO, 0, 0, false,
                CountingMethod.PER_VISIT, ConsumptionBasis.COMPANY_SHARE));
        BulkCoverageEngineRequest request = request(
                java.util.stream.IntStream.range(0, 21)
                        .mapToObj(index -> line("PHYSIO-" + index, "160.00"))
                        .toList(), EncounterType.OUTPATIENT);

        List<CoverageResult> results = engine.calculateBulk(request);

        assertEquals(1, results.get(20).getUsageDetails().getUsedCount());
        assertFalse(results.get(20).getUsageDetails().isTimesExceeded());
        assertMoney("120.00", results.get(20).getCompanyShare());
    }

    @Test
    @DisplayName("الزيارة الحادية والعشرون للعلاج الطبيعي تُرفض بعد عشرين زيارة سابقة")
    void perVisitRejectsTheTwentyFirstHistoricalVisit() {
        coveredByRule(97L, 75, false);
        useLimits(new LimitSnapshot(
                971L, "العلاج الطبيعي", null, 20, null,
                BigDecimal.ZERO, 20, 0, false,
                CountingMethod.PER_VISIT, ConsumptionBasis.COMPANY_SHARE));

        CoverageResult result = calculate(line("PHYSIO-21", "160.00"), EncounterType.OUTPATIENT);

        assertTrue(result.getUsageDetails().isTimesExceeded());
        assertMoney("0.00", result.getCompanyShare());
        assertMoney("160.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("كمية جلسات العلاج الطبيعي تستهلك حد المرات وحدة بوحدة")
    void eachUnitQuantityConsumesSessionLimit() {
        coveredByRule(95L, 100, false);
        useLimits(new LimitSnapshot(
                951L, "جلسات العلاج الطبيعي", null, 20, null,
                BigDecimal.ZERO, 0, 0, false,
                CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE));
        ClaimLineInput input = line("PHYSIO-15", "100.00");
        input.setQuantity(15);

        CoverageResult result = calculate(input, EncounterType.OUTPATIENT);

        assertEquals(15, result.getUsageDetails().getUsedCount());
        assertEquals(20, result.getUsageDetails().getTimesLimit());
        assertFalse(result.getUsageDetails().isTimesExceeded());
    }

    @Test
    @DisplayName("وعاء المرات المنفصل لا يخفي سقف مبلغ الخدمة")
    void separateTimesBucketDoesNotHideAmountLimit() {
        coveredByRule(93L, 100, false);
        useLimits(
                new LimitSnapshot(931L, "مرات العلاج", null, 20, null,
                        BigDecimal.ZERO, 0, 0, false,
                        CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT),
                new LimitSnapshot(932L, "سقف العلاج", new BigDecimal("10000.00"), null, null,
                        new BigDecimal("100.00"), 0, 0, false,
                        CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT));

        CoverageResult result = calculate(line("SEPARATE-LIMITS", "100.00"), EncounterType.OUTPATIENT);

        assertMoney("10000.00", result.getUsageDetails().getAmountLimit());
        assertEquals(20, result.getUsageDetails().getTimesLimit());
        assertMoney("9800.00", result.getUsageDetails().getRemainingAmount());
    }

    @Test
    @DisplayName("السقف العام يطبق حسابيا ولا يظهر كسقف منفعة للخدمة")
    void annualParentBucketIsEnforcedButNotDisplayedAsServiceLimit() {
        coveredByRule(94L, 100, false);
        useLimits(
                new LimitSnapshot(941L, "مرات الخدمة", null, 20, null,
                        BigDecimal.ZERO, 0, 0, false,
                        CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT, true),
                new LimitSnapshot(942L, "السقف السنوي العام", new BigDecimal("60000.00"), null, null,
                        new BigDecimal("59950.00"), 0, 0, false,
                        CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT, false));

        CoverageResult result = calculate(line("PARENT-GENERAL", "100.00"), EncounterType.OUTPATIENT);

        assertNull(result.getUsageDetails().getAmountLimit());
        assertNull(result.getUsageDetails().getRemainingAmount());
        assertEquals(20, result.getUsageDetails().getTimesLimit());
        assertMoney("50.00", result.getCompanyShare());
        assertMoney("50.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("سقف الخدمة المباشر يظهر وحده دون السقف العام")
    void directServiceLimitIsDisplayedWithoutGeneralParentLimit() {
        coveredByRule(95L, 100, false);
        useLimits(
                new LimitSnapshot(951L, "سقف الرنين", new BigDecimal("1500.00"), null, null,
                        new BigDecimal("100.00"), 0, 0, false,
                        CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT, true),
                new LimitSnapshot(952L, "السقف السنوي العام", new BigDecimal("60000.00"), null, null,
                        new BigDecimal("1000.00"), 0, 0, false,
                        CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT, false));

        CoverageResult result = calculate(line("DIRECT-WITH-PARENT", "100.00"), EncounterType.OUTPATIENT);

        assertMoney("1500.00", result.getUsageDetails().getAmountLimit());
        assertMoney("1300.00", result.getUsageDetails().getRemainingAmount());
        assertMoney("100.00", result.getCompanyShare());
        assertMoney("0.00", result.getLimitRefused());
    }

    @Test
    @DisplayName("تجاوز عدد أيام الإيواء يوقف اليوم الإضافي")
    void daysLimitHardStopsNewServiceDay() {
        coveredByRule(91L, 100, false);
        useLimits(new LimitSnapshot(
                911L, "أيام الإيواء", null, null, 10,
                BigDecimal.ZERO, 0, 10, false,
                CountingMethod.PER_DAY, ConsumptionBasis.ELIGIBLE_AMOUNT));

        CoverageResult result = calculate(line("IP-DAY-11", "300.00"), EncounterType.INPATIENT);

        assertMoney("0.00", result.getCompanyShare());
        assertMoney("300.00", result.getLimitRefused());
        assertTrue(result.getUsageDetails().isDaysExceeded());
    }

    @Test
    @DisplayName("الرفض اليدوي يخصم من حصة الشركة فقط ويحفظ سببه")
    void manualRefusalOnlyReducesCompanyShare() {
        coveredByRule(92L, 80, false);
        ClaimLineInput input = line("MANUAL-REFUSAL", "100.00");
        input.setManualRefusedAmount(new BigDecimal("10.00"));
        input.setManualRefusalReason("مستند ناقص");

        CoverageResult result = calculate(input, EncounterType.OUTPATIENT);

        assertMoney("70.00", result.getCompanyShare());
        assertMoney("20.00", result.getPatientShare());
        assertMoney("10.00", result.getManualRefusedAmount());
        assertEquals("مستند ناقص", result.getManualRefusalReason());
    }

    private void coveredByRule(Long ruleId, int percent, boolean preApproval) {
        configuredRuleId = ruleId;
        configuredPercent = percent;
        configuredPreApproval = preApproval;
        configuredLimits = List.of();
        stubConfiguredDecision();
    }

    private void stubConfiguredDecision() {
        var rule = BenefitPolicyRuleResponseDto.builder().id(configuredRuleId)
                .effectiveCoveragePercent(configuredPercent).requiresPreApproval(configuredPreApproval)
                .medicalCategoryId(900L).build();
        when(decisionService.resolve(any())).thenReturn(CoverageDecision.builder()
                .covered(true).coveragePercent(configuredPercent).resolvedCategoryId(900L).matchingCategoryId(900L)
                .source(CoverageDecisionSource.EXACT_CATEGORY_RULE).reasonCode("COVERED")
                .appliedRule(rule).limits(configuredLimits).build());
    }

    private void useLimits(LimitSnapshot... limits) {
        configuredLimits = java.util.Arrays.stream(limits).map(limit -> CoverageLimitSnapshot.builder()
                .bucketId(limit.bucketId()).bucketName(limit.bucketName())
                .amountLimit(limit.amountLimit()).timesLimit(limit.timesLimit()).daysLimit(limit.daysLimit())
                .usedAmount(limit.usedAmount()).usedTimes(limit.usedTimes()).usedDays(limit.usedDays())
                .serviceDayAlreadyUsed(limit.serviceDayAlreadyUsed()).countingMethod(limit.countingMethod())
                .consumptionBasis(limit.consumptionBasis()).directlyLinked(limit.directlyLinked()).build())
                .toList();
        stubConfiguredDecision();
    }

    private LimitSnapshot limit(Long id, String name, String amountLimit, String usedAmount) {
        return new LimitSnapshot(
                id, name, new BigDecimal(amountLimit), null, null,
                new BigDecimal(usedAmount), 0, 0, false,
                CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT);
    }

    private ClaimLineInput line(String id, String price) {
        return ClaimLineInput.builder()
                .lineId(id)
                .serviceId(100L)
                .serviceCategoryId(900L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal(price))
                .contractPrice(BigDecimal.ZERO)
                .build();
    }

    private CoverageResult calculate(ClaimLineInput line, EncounterType encounterType) {
        return calculateBulk(List.of(line), encounterType).get(0);
    }

    private List<CoverageResult> calculateBulk(List<ClaimLineInput> lines, EncounterType encounterType) {
        return engine.calculateBulk(request(lines, encounterType));
    }

    private BulkCoverageEngineRequest request(List<ClaimLineInput> lines, EncounterType encounterType) {
        return BulkCoverageEngineRequest.builder()
                .policyId(1L)
                .memberId(100L)
                .serviceYear(2026)
                .serviceDate(LocalDate.of(2026, 7, 20))
                .encounterType(encounterType)
                .lines(lines)
                .build();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
