package com.waad.tba.modules.preauthorization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitStatus;
import com.waad.tba.modules.providercontract.enums.EncounterType;
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
    private final BucketLimitSnapshotAdapter bucketLimitSnapshotAdapter;
    private final PreAuthLimitHoldMapper preAuthLimitHoldMapper;
    private final WaadFinancialEngine financialEngine;
    private final com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository consumptionRepository;
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
        // Spans the whole decision: PER_VISIT counts one occurrence per
        // bucket for the APPROVAL, not one per line. Three lines of the same
        // encounter are still one visit.
        Set<com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator.CountedKey> countedOnce = new HashSet<>();
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
            PreAuthorizationDecision.Line decided = decideLine(preauth, line, member, policy, serviceDate, terms, countedOnce);
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
            Member member, BenefitPolicy policy, LocalDate serviceDate, ProviderContractTerm terms,
            Set<com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator.CountedKey> countedOnce) {

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

        // ── the canonical limit decision (P1.12.3) ──────────────────────
        // The SAME three-stage pipeline P1.12.2 proved in isolation, now the
        // ONLY source of what applies, what is available, and what is
        // approved -- EffectiveLimitResolver/ApplicableLimitResolver/
        // ApplicableCountingLimitResolver/LimitBalanceReader.read are never
        // called from here again.
        var evaluation = bucketLimitSnapshotAdapter.buildForPreauthReservation(
                policy.getId(), benefitRuleId, member.getId(), serviceDate, encounterType);
        if (evaluation.evaluation().blocked()) {
            // P1.12.3: the ONE intentional behavior change from the legacy
            // path (P1.12.1's PA7) -- a structural block (a corrupted
            // bucket/policy relationship, or a days-limited bucket P1.12.1's
            // PA4 established stays unsupported) now fails the same way
            // every other fail-closed guard in this method already does: a
            // safe, translated BusinessRuleException, never the raw
            // IllegalStateException the retired resolver stack used to leak.
            throw new BusinessRuleException(translateBlockReason(evaluation.evaluation().blockReason()));
        }

        // effectiveUnitPrice is genuinely PER UNIT (UnifiedLimitResolver's
        // own "money buys whole units" cross-check, DivisibleLimitSplitter's
        // contract) -- contractPrice itself is the LINE's full total
        // (P1.11.4's finding), never a per-unit price.
        BigDecimal effectiveUnitPrice = approvedQuantity > 0
                ? contractPrice.divide(BigDecimal.valueOf(approvedQuantity), 2, RoundingMode.HALF_UP)
                : contractPrice;
        int resolverRequestedQuantity = requestedQuantityForResolver(
                evaluation.evaluation().snapshots(), approvedQuantity, serviceDate, countedOnce);
        UnifiedLimitInput input = new UnifiedLimitInput(policy.getId(), benefitRuleId, member.getId(), serviceDate,
                encounterType, resolverRequestedQuantity, 0, effectiveUnitPrice, contractPrice, null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, evaluation.evaluation().snapshots());

        WaadFinancialEngine.LimitMode limitMode = decision.status() == UnifiedLimitStatus.UNLIMITED
                ? WaadFinancialEngine.LimitMode.UNLIMITED
                : WaadFinancialEngine.LimitMode.LIMITED;
        BigDecimal bindingAvailableLimit = limitMode == WaadFinancialEngine.LimitMode.LIMITED
                ? decision.bindingAvailableAmount().max(BigDecimal.ZERO) : null;

        WaadFinancialEngine.Result result = financialEngine.evaluate(new WaadFinancialEngine.Input(
                requested,
                contractPrice,
                limitMode,
                bindingAvailableLimit,
                coveragePercent,
                terms == null ? BigDecimal.ZERO : terms.getDiscountPercent(),
                terms != null && Boolean.TRUE.equals(terms.getDiscountBeforeRejection()),
                // The reviewer's refusal, passed through rather than invented.
                // Its position relative to the discount is what makes
                // discountBeforeRejection matter at all.
                explicitRejected,
                fullyRejected,
                // The engine's OWN quantity invariant (must be > 0, and never
                // used to derive an output -- see WaadFinancialEngine.Input's
                // javadoc): this is the line's shape, not the reviewer's
                // verdict. Passing approvedQuantity here sent 0 for every
                // fully-rejected line and made the engine refuse to evaluate
                // it at all, so a request could never mix a rejected line
                // with an approved one.
                requestedQuantity));

        // ── the occurrence dimension ────────────────────────────────────
        // The reviewer's authorisation is never reduced by the ceiling: a
        // service authorised for 4 units stays authorised for 4 even when
        // the policy covers only 2 -- decision.approvedQuantity() already
        // encodes exactly that (input.requestedQuantity() WAS the reviewer's
        // approvedQuantity above). No occurrence axis at all means nothing
        // was ever capped, matching the legacy shape exactly (coveredTimes
        // defaulted to 0 there too when no counting bucket existed).
        boolean hasTimesAxis = evaluation.evaluation().snapshots().stream()
                .anyMatch(s -> s.limitType() == com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType.TIMES);
        int coveredTimes = hasTimesAxis ? decision.approvedQuantity() : 0;
        int limitExcessTimes = hasTimesAxis ? decision.refusedQuantity() : 0;

        // ONE reservable company share for the line, and the eligible amount
        // an ELIGIBLE_AMOUNT bucket measures instead -- UnifiedLimitResolver
        // already folded any occurrence-driven money reduction into
        // bindingAvailableAmount (its own DivisibleLimitSplitter step), so
        // result.insurerFinalPayment()/insideLimit() need no further
        // adjustment here the way the legacy path's separate post-hoc split
        // used to require.
        BigDecimal companyShare = scaled(result.insurerFinalPayment());
        BigDecimal patientShare = scaled(result.patientTotalResponsibility());
        BigDecimal eligibleAmount = scaled(Optional.ofNullable(result.insideLimit()).orElse(result.settlementBase()));

        // What the policy WOULD have paid had the ceiling not intervened.
        // Reporting only the post-ceiling figure is what makes an exhausted
        // bucket look like "0% coverage" -- the percentage never changed.
        WaadFinancialEngine.Result uncapped = financialEngine.evaluate(new WaadFinancialEngine.Input(
                requested, contractPrice, WaadFinancialEngine.LimitMode.UNLIMITED, null,
                coveragePercent,
                terms == null ? BigDecimal.ZERO : terms.getDiscountPercent(),
                terms != null && Boolean.TRUE.equals(terms.getDiscountBeforeRejection()),
                explicitRejected, fullyRejected, requestedQuantity));
        BigDecimal companyShareBeforeLimit = scaled(uncapped.insurerFinalPayment());

        BigDecimal rejectedForLine = fullyRejected
                ? requested
                : explicitRejected.add(requested
                        .multiply(BigDecimal.valueOf(requestedQuantity - approvedQuantity))
                        .divide(BigDecimal.valueOf(requestedQuantity), 2, RoundingMode.HALF_UP));

        List<PreAuthorizationDecision.LimitHold> holds = preAuthLimitHoldMapper.map(decision,
                evaluation.evaluation().items(), evaluation.measures(), companyShare, eligibleAmount,
                policy.getId());

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

    /**
     * P1.12.3: mirrors {@code CoverageEngineService.requestedQuantity()} /
     * {@code ClaimFinancialAdjudicationService}'s own private helper of the
     * same name EXACTLY -- an indivisible counting method (EACH_LINE always,
     * PER_VISIT/PER_DAY once per batch) must reach
     * {@code UnifiedLimitResolver} as AT MOST ONE occurrence, never the raw
     * requested quantity; only EACH_UNIT is genuinely divisible. Without
     * this, a PER_VISIT bucket's raw {@code remaining} (a VISIT count) is
     * compared directly against a multi-unit quantity request -- a unit
     * mismatch that refuses the whole line instead of counting one visit.
     */
    private int requestedQuantityForResolver(
            List<com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot> snapshots,
            int approvedQuantity,
            LocalDate serviceDate,
            Set<com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator.CountedKey> countedOnce) {
        var primaryTimesSnapshot = snapshots.stream()
                .filter(s -> s.limitType() == com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType.TIMES)
                .findFirst();
        if (primaryTimesSnapshot.isEmpty()) {
            return approvedQuantity;
        }
        var snapshot = primaryTimesSnapshot.get();
        var method = snapshot.countingMethod() != null ? snapshot.countingMethod()
                : com.waad.tba.modules.benefitpolicy.enums.CountingMethod.EACH_LINE;
        if (method == com.waad.tba.modules.benefitpolicy.enums.CountingMethod.EACH_UNIT) {
            return approvedQuantity;
        }
        if (method == com.waad.tba.modules.benefitpolicy.enums.CountingMethod.PER_VISIT
                || method == com.waad.tba.modules.benefitpolicy.enums.CountingMethod.PER_DAY) {
            boolean alreadyThisBatch = !countedOnce.add(
                    new com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator.CountedKey(
                            snapshot.bucketId(), serviceDate));
            return approvedQuantity <= 0 ? 0 : (alreadyThisBatch ? 0 : 1);
        }
        return approvedQuantity <= 0 ? 0 : 1; // EACH_LINE
    }

    /**
     * P1.12.3: translates a canonical {@code BLOCKED} reason into the SAME
     * safe, Arabic, non-technical message the legacy path already used for
     * DAYS (word-for-word, so nothing user-facing changes there) -- and a
     * new one for a policy mismatch, which legacy code never phrased safely
     * at all (it leaked a raw {@code IllegalStateException} instead,
     * P1.12.1's PA7 finding). Fails closed on an unrecognized reason rather
     * than ever surfacing the technical code itself to a caller.
     */
    private String translateBlockReason(String blockReason) {
        if (blockReason != null && blockReason.startsWith("PREAUTH_DAY_LIMIT_UNSUPPORTED")) {
            return "الوعاء المنطبق يحمل حد أيام، والموافقة المسبقة لا تحمل جدول أيام "
                    + "(تاريخ خدمة متوقع واحد بلا تاريخ دخول/خروج) يمكن حجز الحد منه.";
        }
        if (blockReason != null && blockReason.startsWith("BUCKET_POLICY_MISMATCH")) {
            return "تعذر تحديد الأوعية المنطبقة على هذا البند بسبب تعارض في إعدادات السياسة. "
                    + "يرجى التواصل مع الدعم الفني.";
        }
        return "تعذر تقييم حدود المنفعة لهذا البند. يرجى التواصل مع الدعم الفني.";
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
