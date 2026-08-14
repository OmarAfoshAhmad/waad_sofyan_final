package com.waad.tba.modules.preauthorization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.service.EffectiveLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes what an approval WOULD record, and records nothing.
 *
 * Deliberately free of side effects: no status change, no snapshot, no hold.
 * That is what lets the approval service run it TWICE -- once to see whether
 * the request is viable at all, and again under locks to decide for real --
 * without the first run leaving anything behind. A balance read before a lock
 * is advisory: between reading it and taking the lock, another approval for
 * the same member can consume the same limit. Only the second run decides.
 *
 * Every temporal resolution here keys on expectedServiceDate, never on today.
 * A pre-authorization is by definition about a future service, so "the current
 * policy" and "the current contract terms" are the wrong questions; the right
 * one is what will be in force when the service happens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreAuthorizationDecisionBuilder {

    private final PreAuthorizationRepository preauthRepository;
    private final MemberRepository memberRepository;
    private final MemberPolicyResolver memberPolicyResolver;
    private final ProviderContractTermRepository contractTermRepository;
    private final EffectiveLimitResolver effectiveLimitResolver;
    private final LimitBalanceReader limitBalanceReader;
    private final BenefitLimitBucketRepository bucketRepository;
    private final WaadFinancialEngine financialEngine;
    private final com.waad.tba.modules.benefitpolicy.service.CoverageDecisionService coverageDecisionService;

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    /**
     * Takes an id rather than an entity on purpose. The lines collection is
     * lazy, so an entity loaded outside this transaction cannot be walked
     * here; and when the approval service calls this after locking the row,
     * the load below returns that same managed instance from the persistence
     * context rather than a second copy.
     */
    @Transactional(readOnly = true)
    public PreAuthorizationDecision build(Long preauthId, int calculationVersion) {
        if (preauthId == null) {
            throw new IllegalArgumentException("preauthId is required");
        }
        PreAuthorization preauth = preauthRepository.findById(preauthId)
                .orElseThrow(() -> new BusinessRuleException("الموافقة المسبقة غير موجودة."));

        // ── the basis, resolved on the date the service is expected ─────
        LocalDate serviceDate = preauth.getExpectedServiceDate();
        if (serviceDate == null) {
            // Fail closed. Substituting today would price a future service
            // with today's policy and today's contract -- the precise defect
            // the dated-resolution work removed from the claim path.
            throw new BusinessRuleException(
                    "لا يمكن اعتماد موافقة مسبقة بدون تاريخ الخدمة المتوقع.");
        }

        Member member = memberRepository.findById(Objects.requireNonNull(preauth.getMemberId(),
                        "pre-authorization has no member"))
                .orElseThrow(() -> new BusinessRuleException("المستفيد المرتبط بالموافقة غير موجود."));

        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, serviceDate)
                .orElseThrow(() -> new BusinessRuleException(
                        "لا توجد وثيقة سارية للمستفيد في تاريخ الخدمة المتوقع."));
        Long assignmentId = memberPolicyResolver.resolveAssignmentFor(member, serviceDate)
                .map(a -> a.getId()).orElse(null);

        ProviderContractTerm terms = preauth.getContractId() == null ? null
                : contractTermRepository.findEffective(preauth.getContractId(), serviceDate).orElse(null);
        if (preauth.getContractId() != null && terms == null) {
            // A contracted provider whose terms cannot be resolved on the
            // service date has no discount we are entitled to assume. Zero
            // would silently overpay; proceeding without the basis would make
            // the decision unreproducible.
            throw new BusinessRuleException(
                    "لا توجد شروط عقد سارية لمقدم الخدمة في تاريخ الخدمة المتوقع.");
        }

        PreAuthorizationDecision.Basis basis = new PreAuthorizationDecision.Basis(
                assignmentId, policy.getId(), policy.getVersion(), serviceDate,
                preauth.getProviderId(), preauth.getContractId(),
                terms == null ? null : terms.getId(),
                terms == null ? null : terms.getDiscountPercent(),
                terms == null ? null : terms.getDiscountBeforeRejection());

        // ── the money, line by line ─────────────────────────────────────
        List<PreAuthorizationDecision.Line> lines = new ArrayList<>();
        BigDecimal requestedTotal = BigDecimal.ZERO;
        BigDecimal authorizedServiceTotal = BigDecimal.ZERO;
        BigDecimal settlementTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal limitExcessTotal = BigDecimal.ZERO;
        BigDecimal rejectedTotal = BigDecimal.ZERO;
        BigDecimal patientTotal = BigDecimal.ZERO;
        BigDecimal companyTotal = BigDecimal.ZERO;

        for (PreAuthorizationLine line : preauth.getLines()) {
            PreAuthorizationDecision.Line decided = decideLine(preauth, line, member, policy, serviceDate, terms);
            lines.add(decided);
            requestedTotal = requestedTotal.add(decided.requestedAmount());
            authorizedServiceTotal = authorizedServiceTotal.add(decided.authorizedServiceAmount());
            settlementTotal = settlementTotal.add(decided.settlementAmount());
            discountTotal = discountTotal.add(decided.providerDiscount());
            limitExcessTotal = limitExcessTotal.add(decided.limitExcess());
            rejectedTotal = rejectedTotal.add(decided.rejectedAmount());
            patientTotal = patientTotal.add(decided.patientShare());
            companyTotal = companyTotal.add(decided.companyShare());
        }

        if (lines.isEmpty()) {
            throw new BusinessRuleException("لا توجد بنود في الموافقة المسبقة.");
        }

        // ── the outcome: two separate questions ─────────────────────────
        // Was the SERVICE approved? -- decided by the REVIEWER, from the
        //   per-line decisions above. The engine never manufactures a refusal
        //   to make its own totals balance.
        // Did the INSURER cover it? -- decided by the ceilings.
        //
        // Folding these together is what made a fully authorised service whose
        // ceiling was reached report as "partially approved", telling a
        // patient their request was cut down when it was not.
        boolean anyRefusal = rejectedTotal.compareTo(BigDecimal.ZERO) > 0;

        PreAuthorizationDecision.Outcome outcome;
        String rejectionReason = null;
        if (authorizedServiceTotal.compareTo(BigDecimal.ZERO) == 0) {
            outcome = PreAuthorizationDecision.Outcome.REJECTED;
            rejectionReason = lines.stream()
                    .map(PreAuthorizationDecision.Line::rejectionReason)
                    .filter(r -> r != null && !r.isBlank())
                    .findFirst().orElse("رُفضت جميع بنود الطلب.");
        } else if (anyRefusal) {
            outcome = PreAuthorizationDecision.Outcome.PARTIALLY_APPROVED;
        } else {
            outcome = PreAuthorizationDecision.Outcome.APPROVED;
        }

        // A spent ceiling is not "zero coverage": the policy still covers its
        // percentage, and what reached zero is the payable amount. The two
        // are separate facts, so they get separate names.
        // A ceiling reached in EITHER dimension caps the coverage: an
        // occurrence limit that stops the insurer paying is no less real than
        // a monetary one.
        boolean anyTimesExcess = lines.stream().anyMatch(l -> l.limitExcessTimes() > 0);
        boolean limitCapped = limitExcessTotal.compareTo(BigDecimal.ZERO) > 0 || anyTimesExcess;
        PreAuthorizationDecision.CoverageOutcome coverageOutcome;
        if (limitCapped && companyTotal.compareTo(BigDecimal.ZERO) == 0) {
            coverageOutcome = PreAuthorizationDecision.CoverageOutcome.LIMIT_EXHAUSTED;
        } else if (limitCapped) {
            coverageOutcome = PreAuthorizationDecision.CoverageOutcome.LIMIT_CAPPED;
        } else if (anyRefusal) {
            coverageOutcome = PreAuthorizationDecision.CoverageOutcome.PARTIALLY_COVERED;
        } else {
            coverageOutcome = PreAuthorizationDecision.CoverageOutcome.FULLY_COVERED;
        }

        return new PreAuthorizationDecision(outcome, preauth.getId(), member.getId(), calculationVersion,
                basis, List.copyOf(lines), coverageOutcome,
                scaled(requestedTotal), scaled(authorizedServiceTotal), scaled(settlementTotal),
                scaled(discountTotal), scaled(limitExcessTotal), limitCapped,
                scaled(rejectedTotal), scaled(patientTotal), scaled(companyTotal), rejectionReason);
    }

    private PreAuthorizationDecision.Line decideLine(PreAuthorization preauth, PreAuthorizationLine line,
            Member member, BenefitPolicy policy, LocalDate serviceDate, ProviderContractTerm terms) {

        BigDecimal requested = Optional.ofNullable(line.getRequestedAmount()).orElse(BigDecimal.ZERO);

        // The reviewer's decision arrives as data. An unreviewed line is
        // treated as fully requested -- never as silently refused.
        int requestedQuantity = Optional.ofNullable(line.getRequestedQuantity()).orElse(1);
        int approvedQuantity = Optional.ofNullable(line.getApprovedQuantity()).orElse(requestedQuantity);
        BigDecimal explicitRejected = Optional.ofNullable(line.getExplicitRejectedAmount())
                .orElse(BigDecimal.ZERO);
        var reviewDecision = line.getReviewDecision();
        boolean fullyRejected = reviewDecision == PreAuthorizationLine.ReviewDecision.REJECT
                || approvedQuantity == 0;

        if (fullyRejected) {
            approvedQuantity = 0;
        }
        if ((fullyRejected || explicitRejected.signum() > 0 || approvedQuantity < requestedQuantity)
                && (line.getRejectionReason() == null || line.getRejectionReason().isBlank())) {
            // A refusal nobody explained cannot be appealed by the member or
            // answered by the provider.
            throw new BusinessRuleException("رفض بند الموافقة يتطلب سبباً صريحاً.");
        }
        if (explicitRejected.compareTo(requested) > 0) {
            throw new BusinessRuleException("المبلغ المرفوض يتجاوز المبلغ المطلوب للبند.");
        }
        BigDecimal contractPrice = Optional.ofNullable(line.getContractPrice())
                .orElse(Optional.ofNullable(line.getManualPrice()).orElse(requested));
        int coveragePercent = Optional.ofNullable(line.getCoveragePercentage())
                .orElse(Optional.ofNullable(policy.getDefaultCoveragePercent()).orElse(100));

        // WHICH benefit rule this service falls under decides which buckets
        // apply at all, so it is resolved through the same component the claim
        // path uses -- a pre-authorization and the claim it becomes must agree
        // on the rule, or the hold and the later consumption would land on
        // different limits.
        EncounterType encounterType = encounterTypeOf(line);
        var coverageDecision = coverageDecisionService.resolve(
                com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionRequest.builder()
                        .policyId(policy.getId()).memberId(member.getId())
                        .serviceId(line.getMedicalServiceId())
                        // The LINE's classification. Resolving from the head
                        // would price every line of a mixed request against
                        // one category -- the wrong buckets for all but one.
                        // providerServiceId identifies the provider's price
                        // list entry, not a medical classification.
                        .serviceCategoryId(requiredCategoryId(line))
                        .serviceDate(serviceDate).encounterType(encounterType)
                        .requestedAmount(requested).build());
        Long benefitRuleId = coverageDecision.appliedRuleOptional()
                .map(r -> r.getId()).orElse(null);
        if (benefitRuleId == null) {
            // Fail closed. With no rule, no bucket applies, and the limit
            // resolver would report "unlimited" -- so an unclassifiable
            // service would be approved against no ceiling at all and hold
            // nothing, leaving the claim that follows to overdraw silently.
            throw new BusinessRuleException(
                    "لا توجد قاعدة منفعة مطابقة لهذه الخدمة في تاريخ الخدمة المتوقع؛ "
                            + "لا يمكن تحديد الأوعية المنطبقة.");
        }

        // Every limit that applies to this line, including the ones that will
        // not bind -- a decision must remain explainable ("why did this stop
        // at 500, and where did the others stand?").
        List<EffectiveLimitResolver.EffectiveLimit> limits = effectiveLimitResolver.resolve(
                policy.getId(), benefitRuleId, member.getId(), serviceDate, encounterType);
        LimitBalanceReader.BalanceSet balances = limitBalanceReader.read(member.getId(), limits, null);

        // The binding constraint is reservableAvailable, NOT actualRemaining:
        // a hold placed by another approval has already spoken for part of the
        // balance even though nothing has been consumed yet. Deciding against
        // actualRemaining is exactly how the same limit gets promised twice.
        BigDecimal minimumReservable = balances.limits().stream()
                .map(LimitBalanceReader.LimitBalance::reservableAvailable)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);

        WaadFinancialEngine.LimitMode limitMode = minimumReservable == null
                ? WaadFinancialEngine.LimitMode.UNLIMITED
                : WaadFinancialEngine.LimitMode.LIMITED;

        WaadFinancialEngine.Result result = financialEngine.evaluate(new WaadFinancialEngine.Input(
                requested,
                contractPrice,
                limitMode,
                minimumReservable,
                coveragePercent,
                terms == null ? BigDecimal.ZERO : terms.getDiscountPercent(),
                terms != null && Boolean.TRUE.equals(terms.getDiscountBeforeRejection()),
                // The reviewer's refusal, passed through rather than invented.
                // Its position relative to the discount is what makes
                // discountBeforeRejection matter at all.
                explicitRejected,
                fullyRejected,
                approvedQuantity));

        // ── the occurrence dimension ────────────────────────────────────
        // A ceiling on OCCURRENCES constrains the decision independently of
        // the money, and the two are never compared: min() across a visit
        // count and a currency amount is meaningless.
        //
        // It is applied to what the REVIEWER approved, and never reduces it:
        // a service authorised for 4 units stays authorised for 4 even when
        // the policy covers only 2. The other 2 become the patient's, exactly
        // as an amount above the ceiling does.
        rejectUnsupportedDayLimit(balances);
        int requiredTimes = requiredTimesFor(balances, approvedQuantity);
        Integer reservableTimes = balances.limits().stream()
                .map(LimitBalanceReader.LimitBalance::reservableTimes)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);

        int coveredTimes = reservableTimes == null
                ? requiredTimes
                : Math.min(requiredTimes, Math.max(0, reservableTimes));
        int limitExcessTimes = Math.max(0, requiredTimes - coveredTimes);

        // ONE reservable company share for the line. Recorded against each
        // applicable scope below -- never added across them: a line mapped to a
        // service bucket, its group, its parent and the general ceiling holds
        // one amount that four scopes each measure, not four amounts.
        BigDecimal companyShare = scaled(result.insurerFinalPayment());
        BigDecimal patientShare = scaled(result.patientTotalResponsibility());

        // Storing reservedTimes without changing the money would let the
        // insurer pay for occurrences the policy does not cover.
        if (limitExcessTimes > 0) {
            if (requiredTimes > 0 && countingMethod(balances) == CountingMethod.EACH_UNIT) {
                // Divisible: the insurer pays for the covered units, and the
                // uncovered ones move to the patient.
                BigDecimal coveredFraction = BigDecimal.valueOf(coveredTimes)
                        .divide(BigDecimal.valueOf(requiredTimes), 6, RoundingMode.HALF_UP);
                BigDecimal payable = scaled(companyShare.multiply(coveredFraction));
                patientShare = scaled(patientShare.add(companyShare.subtract(payable)));
                companyShare = payable;
            } else {
                // Indivisible: a visit is not half-attended. Either the
                // occurrence is available or the insurer pays nothing.
                patientShare = scaled(patientShare.add(companyShare));
                companyShare = BigDecimal.ZERO.setScale(2);
            }
        }

        // What the policy WOULD have paid had the ceiling not intervened.
        // Reporting only the post-ceiling figure is what makes an exhausted
        // bucket look like "0% coverage" -- the percentage never changed.
        WaadFinancialEngine.Result uncapped = financialEngine.evaluate(new WaadFinancialEngine.Input(
                requested, contractPrice, WaadFinancialEngine.LimitMode.UNLIMITED, null,
                coveragePercent,
                terms == null ? BigDecimal.ZERO : terms.getDiscountPercent(),
                terms != null && Boolean.TRUE.equals(terms.getDiscountBeforeRejection()),
                explicitRejected, fullyRejected, approvedQuantity));
        BigDecimal companyShareBeforeLimit = scaled(uncapped.insurerFinalPayment());

        BigDecimal rejectedForLine = fullyRejected
                ? requested
                : explicitRejected.add(requested
                        .multiply(BigDecimal.valueOf(requestedQuantity - approvedQuantity))
                        .divide(BigDecimal.valueOf(requestedQuantity), 2, RoundingMode.HALF_UP));

        List<PreAuthorizationDecision.LimitHold> holds = new ArrayList<>();
        for (LimitBalanceReader.LimitBalance balance : balances.limits()) {
            var definition = balance.limit().definition();

            // What THIS scope measures. A bucket that counts the eligible
            // amount does not count the company share, and holding the wrong
            // quantity would leave the hold and the later claim consumption
            // measuring different things -- so the release at conversion would
            // not cancel what the claim then commits.
            Measure measure = measureFor(definition.bucketId(), companyShare, result, 1);

            // A bucket may cap BOTH money and occurrences. The two are
            // recorded side by side, never added: they answer different
            // questions and are measured in different units.
            Integer heldTimes = balance.timesLimit() == null ? null : coveredTimes;

            boolean binding = minimumReservable != null
                    && balance.reservableAvailable() != null
                    && balance.reservableAvailable().compareTo(minimumReservable) == 0;

            holds.add(new PreAuthorizationDecision.LimitHold(
                    definition.semanticKey(),
                    definition.benefitScopeType() == BenefitScopeType.POLICY_GENERAL
                            ? "POLICY_GENERAL" : "BUCKET",
                    definition.bucketId(),
                    definition.policyId(),
                    definition.periodType(),
                    definition.periodStart(),
                    definition.periodEnd(),
                    scaled(balance.limit().effectiveLimit()),
                    scaled(balance.committed()),
                    scaled(balance.reserved()),
                    scaled(balance.actualRemaining()),
                    scaled(balance.reservableAvailable()),
                    balance.timesLimit(), balance.committedTimes(), balance.reservedTimes(),
                    balance.actualRemainingTimes(), balance.reservableTimes(),
                    measure.basis(), measure.unit(),
                    measure.amount(), heldTimes, measure.days(),
                    binding));
        }

        return new PreAuthorizationDecision.Line(
                line.getId(), line.getProviderServiceId(), line.getProviderServiceCode(), line.getServiceName(),
                1, scaled(contractPrice), scaled(requested), coveragePercent,
                BigDecimal.ZERO.setScale(2),
                // What the REVIEWER refused. Never inferred from the ceiling:
                // a ceiling decides who pays, a refusal decides what was
                // authorised, and conflating them misreports both.
                scaled(rejectedForLine),
                line.getMedicalServiceId(), line.getMedicalCategoryId(), benefitRuleId,
                requestedQuantity, approvedQuantity, coveredTimes, limitExcessTimes,
                PreAuthorizationDecision.LimitExcessDisposition.PATIENT_RESPONSIBILITY,
                reviewDecision == null ? null : reviewDecision.name(),
                line.getRejectionReason(),
                scaled(companyShareBeforeLimit),
                scaled(requested.subtract(rejectedForLine).max(BigDecimal.ZERO)),
                scaled(companyShare.add(patientShare)),
                scaled(result.providerContractDiscount()),
                scaled(result.patientLimitExcess()),
                patientShare, companyShare,
                List.copyOf(holds));
    }

    /** A scope's own measure of the single line decision, in its own unit. */
    private record Measure(String basis, PreAuthorizationDecision.ReservedUnit unit,
            BigDecimal amount, Integer times, Integer days) {}

    /**
     * What a given scope will record for this line. One decision, but each
     * scope measures it independently: money for an amount ceiling,
     * occurrences for a visit limit, days for a stay limit. Mirrors what the
     * claim ledger later consumes for the same bucket, so that releasing the
     * hold and committing the actual consumption cancel out instead of
     * leaving a residue.
     *
     * These are never summed or compared across scopes -- a visit count and a
     * currency amount are not the same kind of number.
     */
    private Measure measureFor(Long bucketId, BigDecimal companyShare, WaadFinancialEngine.Result result,
            int quantity) {
        if (bucketId == null) {
            // The general ceiling measures the insurer's money.
            return new Measure("COMPANY_SHARE", PreAuthorizationDecision.ReservedUnit.CURRENCY,
                    companyShare, null, null);
        }
        ConsumptionBasis basis = bucketRepository.findById(bucketId)
                .map(BenefitLimitBucket::getConsumptionBasis)
                .orElse(ConsumptionBasis.COMPANY_SHARE);

        return switch (basis) {
            case COMPANY_SHARE -> new Measure("COMPANY_SHARE",
                    PreAuthorizationDecision.ReservedUnit.CURRENCY, companyShare, null, null);
            // The claim ledger consumes (total - limitRefused) for such a
            // bucket, which at pre-authorization time is the part of the line
            // that fits inside the ceiling.
            case ELIGIBLE_AMOUNT -> new Measure("ELIGIBLE_AMOUNT",
                    PreAuthorizationDecision.ReservedUnit.CURRENCY,
                    scaled(Optional.ofNullable(result.insideLimit()).orElse(result.settlementBase())),
                    null, null);
        };
    }


    /**
     * How many occurrences this line consumes, derived the same way the claim
     * ledger derives them so a hold and the consumption that follows measure
     * the same thing. Uses the APPROVED quantity: a refused unit is not an
     * occurrence.
     *
     * PER_DAY here counts occurrences within a day -- it is a counting method
     * for TIMES, not a day limit. The two are unrelated despite the name.
     */
    private int requiredTimesFor(LimitBalanceReader.BalanceSet balances, int approvedQuantity) {
        CountingMethod method = countingMethod(balances);
        return switch (method) {
            case EACH_UNIT -> Math.max(0, approvedQuantity);
            case EACH_LINE, PER_VISIT, PER_DAY -> approvedQuantity > 0 ? 1 : 0;
        };
    }

    private CountingMethod countingMethod(LimitBalanceReader.BalanceSet balances) {
        return balances.limits().stream()
                .filter(b -> b.timesLimit() != null)
                .map(b -> b.limit().definition().bucketId())
                .filter(Objects::nonNull)
                .findFirst()
                .flatMap(bucketRepository::findById)
                .map(BenefitLimitBucket::getCountingMethod)
                .orElse(CountingMethod.EACH_LINE);
    }

    /**
     * A day limit counts DISTINCT SERVICE DATES, and a pre-authorization
     * carries a single expected date with no admission/discharge behind it.
     * Reserving one day from one date would understate a multi-day stay;
     * treating quantity as days would invent data; ignoring the limit would
     * let it be overdrawn. All three are worse than stopping.
     */
    private void rejectUnsupportedDayLimit(LimitBalanceReader.BalanceSet balances) {
        boolean hasDayLimit = balances.limits().stream()
                .map(b -> b.limit().definition().bucketId())
                .filter(Objects::nonNull)
                .flatMap(id -> bucketRepository.findById(id).stream())
                .anyMatch(bucket -> bucket.getDaysLimit() != null);

        if (hasDayLimit) {
            throw new BusinessRuleException(
                    "الوعاء المنطبق يحمل حد أيام، والموافقة المسبقة لا تحمل جدول أيام "
                            + "(تاريخ خدمة متوقع واحد بلا تاريخ دخول/خروج) يمكن حجز الحد منه.");
        }
    }

    private Long requiredCategoryId(PreAuthorizationLine line) {
        if (line.getMedicalCategoryId() == null) {
            throw new BusinessRuleException(
                    "بند الموافقة بلا تصنيف طبي؛ لا يمكن تحديد قاعدة المنفعة والأوعية المنطبقة.");
        }
        return line.getMedicalCategoryId();
    }

    private EncounterType encounterTypeOf(PreAuthorizationLine line) {
        if (line.getEncounterType() == null || line.getEncounterType().isBlank()) {
            return EncounterType.OUTPATIENT;
        }
        try {
            return EncounterType.valueOf(line.getEncounterType());
        } catch (IllegalArgumentException ignored) {
            return EncounterType.OUTPATIENT;
        }
    }

    private BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
