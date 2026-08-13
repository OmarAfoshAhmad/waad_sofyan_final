package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.EffectiveLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The only live adapter from claim entities to the canonical financial engine. */
@Service
@RequiredArgsConstructor
public class ClaimFinancialAdjudicationService {
    private final BenefitPolicyRepository policyRepository;
    private final com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
    private final EffectiveLimitResolver effectiveLimitResolver;
    private final LimitBalanceReader balanceReader;
    private final MultiLineMultiBucketEngine multiLineEngine;

    public record AdjudicationResult(MultiLineMultiBucketEngine.ClaimResult claimResult) {}

    @Transactional(readOnly = true)
    public AdjudicationResult adjudicate(Claim claim) {
        if (claim == null || claim.getMember() == null || claim.getMember().getId() == null) {
            throw new IllegalArgumentException("claim with a persisted member is required");
        }
        if (claim.getLines() == null || claim.getLines().isEmpty()) {
            throw new IllegalArgumentException("claim requires at least one line");
        }
        LocalDate serviceDate = Optional.ofNullable(claim.getServiceDate()).orElseThrow(
                () -> new IllegalArgumentException("serviceDate is required"));
        BenefitPolicy policy = resolvePolicy(claim, serviceDate);
        Long memberId = claim.getMember().getId();
        List<MultiLineMultiBucketEngine.LineInput> inputs = new ArrayList<>();

        for (int index = 0; index < claim.getLines().size(); index++) {
            ClaimLine line = claim.getLines().get(index);
            if (line.getAppliedRuleId() == null) {
                throw new IllegalStateException("COVERED_LINE_RULE_MISSING: line index=" + index);
            }
            if (line.getCoveragePercentSnapshot() == null || line.getCoveragePercentSnapshot() < 1) {
                throw new IllegalStateException("COVERAGE_ZERO_IS_NOT_A_VALID_BENEFIT: line index=" + index);
            }
            var effective = effectiveLimitResolver.resolve(policy.getId(), line.getAppliedRuleId(), memberId,
                    serviceDate, claim.getEncounterType());
            var balances = balanceReader.read(memberId, effective, claim.getId());
            BigDecimal requested = requiredPositive(line.getRequestedTotal(), "requestedTotal", index);
            BigDecimal contractualUnit = requiredPositive(line.getContractUnitPrice(), "contractUnitPrice", index);
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            BigDecimal contractualTotal = contractualUnit.multiply(BigDecimal.valueOf(quantity));
            inputs.add(new MultiLineMultiBucketEngine.LineInput(
                    lineKey(line, index), requested, contractualTotal,
                    line.getCoveragePercentSnapshot(), zero(claim.getAppliedDiscountPercent()),
                    Boolean.TRUE.equals(claim.getDiscountBeforeRejection()),
                    zero(line.getManualRefusedAmount()), Boolean.TRUE.equals(line.getRejected()),
                    quantity, balances));
        }

        MultiLineMultiBucketEngine.ClaimResult result = multiLineEngine.evaluate(memberId, inputs);
        Map<String, MultiLineMultiBucketEngine.LineResult> byKey = result.lines().stream()
                .collect(Collectors.toMap(MultiLineMultiBucketEngine.LineResult::lineKey, Function.identity()));
        for (int index = 0; index < claim.getLines().size(); index++) {
            ClaimLine line = claim.getLines().get(index);
            apply(line, byKey.get(lineKey(line, index)));
        }
        return new AdjudicationResult(result);
    }

    private void apply(ClaimLine line, MultiLineMultiBucketEngine.LineResult lineResult) {
        if (lineResult == null) throw new IllegalStateException("ADJUDICATION_LINE_RESULT_MISSING");
        WaadFinancialEngine.Result r = lineResult.financial();
        line.setContractualPrice(r.contractualPrice());
        line.setContractualPriceExcess(r.contractualPriceExcess());
        line.setSettlementBase(r.settlementBase());
        line.setLimitMode(r.limitMode().name());
        line.setBindingAvailableLimit(r.bindingAvailableLimit());
        line.setInsideLimit(r.insideLimit());
        line.setPatientLimitExcess(r.patientLimitExcess());
        line.setLimitConsumption(r.limitConsumption());
        line.setBindingRemainingLimit(r.bindingRemainingLimit());
        line.setPatientCoverageShare(r.patientCoverageShare());
        line.setPatientTotalResponsibility(r.patientTotalResponsibility());
        line.setInsurerGrossShare(r.insurerGrossShare());
        line.setProviderDiscountPercent(r.providerDiscountPercent());
        line.setProviderContractDiscount(r.providerContractDiscount());
        line.setProviderNetBeforeRejection(r.providerNetBeforeRejection());
        line.setProviderRejectedAmountV2(r.providerRejectedAmount());
        line.setInsurerFinalPayment(r.insurerFinalPayment());

        // Compatibility/reporting columns now mirror the canonical result; no
        // second formula is allowed here.
        line.setApprovedAmount(r.insurerFinalPayment());
        line.setCompanyShare(r.insurerFinalPayment());
        line.setPatientShare(r.patientTotalResponsibility());
        line.setPriceExcessRefused(r.contractualPriceExcess());
        line.setLimitRefused(r.patientLimitExcess());
        line.setRefusedAmount(r.contractualPriceExcess().add(r.providerRejectedAmount()));
        line.setApprovedQuantity(null); // not derivable from a monetary result

        if (!lineResult.limitAllocations().isEmpty()) {
            var binding = lineResult.limitAllocations().stream()
                    .filter(MultiLineMultiBucketEngine.LimitAllocation::binding).findFirst()
                    .orElse(lineResult.limitAllocations().get(0));
            line.setAmountLimitSnapshot(binding.effectiveLimit());
            line.setUsedAmountSnapshot(binding.committedBeforeClaim().add(binding.reservedBeforeClaim()));
            line.setRemainingAmountSnapshot(binding.availableAfterLine());
        }
    }

    /**
     * The policy that applied ON THE SERVICE DATE. This used to return the
     * member's current pointer whenever one existed, consulting the date only
     * as a fallback -- so a backdated claim was adjudicated against today's
     * limits and coverage percentages. Fails closed: an unresolvable policy
     * stops adjudication rather than flowing on as null.
     */
    private BenefitPolicy resolvePolicy(Claim claim, LocalDate date) {
        return memberPolicyResolver.resolveForOrFail(claim.getMember(), date);
    }

    private String lineKey(ClaimLine line, int index) {
        return line.getId() != null ? "ID:" + line.getId() : "INDEX:" + index;
    }

    private BigDecimal requiredPositive(BigDecimal value, String field, int index) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(field + " must be positive for line index=" + index);
        }
        return value;
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
