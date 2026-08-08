package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure claim-level coordinator. It evaluates lines in their declared order and
 * immediately reserves each result in memory against every applicable limit,
 * preventing later lines in the same calculation from reusing the same balance.
 * Database locking/persistence deliberately belong to the approval stage.
 */
@Component
@RequiredArgsConstructor
public class MultiLineMultiBucketEngine {
    private final WaadFinancialEngine financialEngine;

    public record LineInput(
            String lineKey,
            BigDecimal requestedAmount,
            BigDecimal contractualPrice,
            int coveragePercent,
            BigDecimal providerDiscountPercent,
            BigDecimal providerRejectedAmount,
            boolean fullyRejected,
            int quantity,
            LimitBalanceReader.BalanceSet balances) {}

    public record LimitAllocation(
            String semanticKey,
            BigDecimal effectiveLimit,
            BigDecimal committedBeforeClaim,
            BigDecimal reservedBeforeClaim,
            BigDecimal availableBeforeLine,
            BigDecimal consumption,
            BigDecimal availableAfterLine,
            boolean binding) {}

    public record LineResult(String lineKey, WaadFinancialEngine.Result financial,
                             List<LimitAllocation> limitAllocations) {}

    public record ClaimResult(Long memberId, List<LineResult> lines,
                              Map<String, BigDecimal> signedRemainingByLimit) {}

    public ClaimResult evaluate(Long memberId, List<LineInput> lines) {
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("at least one line is required");

        Set<String> lineKeys = new HashSet<>();
        Map<String, BigDecimal> originalAvailable = new HashMap<>();
        Map<String, BigDecimal> currentAvailable = new LinkedHashMap<>();
        List<LineResult> results = new ArrayList<>();

        for (LineInput line : lines) {
            if (line == null || line.lineKey() == null || line.lineKey().isBlank()) {
                throw new IllegalArgumentException("every line requires a non-blank lineKey");
            }
            if (!lineKeys.add(line.lineKey())) {
                throw new IllegalArgumentException("DUPLICATE_LINE_KEY: " + line.lineKey());
            }
            LimitBalanceReader.BalanceSet set = line.balances();
            if (set == null) throw new IllegalArgumentException("balances are required for line " + line.lineKey());
            if (!memberId.equals(set.memberId())) {
                throw new IllegalStateException("LINE_BALANCE_MEMBER_MISMATCH: line=" + line.lineKey()
                        + " expected member=" + memberId + " but balances belong to member=" + set.memberId());
            }

            for (var balance : set.limits()) {
                String key = balance.limit().definition().semanticKey();
                BigDecimal baseline = balance.signedAvailable();
                BigDecimal previousBaseline = originalAvailable.putIfAbsent(key, baseline);
                if (previousBaseline != null && previousBaseline.compareTo(baseline) != 0) {
                    throw new IllegalStateException("INCONSISTENT_LIMIT_SNAPSHOT: " + key
                            + " has balances " + previousBaseline + " and " + baseline);
                }
                currentAvailable.putIfAbsent(key, baseline);
            }

            BigDecimal binding = set.limits().stream()
                    .map(balance -> currentAvailable.get(balance.limit().definition().semanticKey()))
                    .min(BigDecimal::compareTo).orElse(null);
            boolean limited = binding != null;
            BigDecimal usableBinding = limited ? binding.max(BigDecimal.ZERO) : null;
            WaadFinancialEngine.Result financial = financialEngine.evaluate(new WaadFinancialEngine.Input(
                    line.requestedAmount(), line.contractualPrice(),
                    limited ? WaadFinancialEngine.LimitMode.LIMITED : WaadFinancialEngine.LimitMode.UNLIMITED,
                    usableBinding, line.coveragePercent(), line.providerDiscountPercent(),
                    line.providerRejectedAmount(), line.fullyRejected(), line.quantity()));

            List<LimitAllocation> allocations = new ArrayList<>();
            BigDecimal consumption = limited ? financial.limitConsumption() : BigDecimal.ZERO;
            for (var balance : set.limits()) {
                String key = balance.limit().definition().semanticKey();
                BigDecimal before = currentAvailable.get(key);
                BigDecimal after = before.subtract(consumption);
                if (after.signum() < 0) {
                    throw new IllegalStateException("MULTI_BUCKET_OVERDRAW: " + key + " before=" + before
                            + " consumption=" + consumption);
                }
                boolean isBinding = binding != null && before.compareTo(binding) == 0;
                allocations.add(new LimitAllocation(key, balance.limit().effectiveLimit(),
                        balance.committed(), balance.reserved(), before, consumption, after, isBinding));
                currentAvailable.put(key, after);
            }
            results.add(new LineResult(line.lineKey(), financial, List.copyOf(allocations)));
        }
        return new ClaimResult(memberId, List.copyOf(results), Map.copyOf(currentAvailable));
    }
}
