package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads committed/reserved balances without deciding which limits apply. */
@Service
@RequiredArgsConstructor
public class LimitBalanceReader {
    private final BenefitBucketConsumptionRepository consumptionRepository;
    private final ClaimRepository claimRepository;

    public record LimitBalance(
            EffectiveLimitResolver.EffectiveLimit limit,
            BigDecimal committed,
            BigDecimal reserved,
            BigDecimal signedAvailable) {}

    public record BalanceSet(
            Long memberId,
            List<LimitBalance> limits,
            BigDecimal bindingAvailableLimit,
            List<String> bindingSemanticKeys) {}

    private record BalanceKey(Long bucketId, LocalDate start, LocalDate end, Status status) {}

    @Transactional(readOnly = true)
    public BalanceSet read(Long memberId, List<EffectiveLimitResolver.EffectiveLimit> limits,
                           Long excludeClaimId) {
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (limits == null) throw new IllegalArgumentException("limits are required");
        if (limits.isEmpty()) return new BalanceSet(memberId, List.of(), null, List.of());

        for (var limit : limits) {
            if (limit.definition().beneficiaryScopeType() != BeneficiaryScopeType.MEMBER) {
                throw new IllegalStateException("UNSUPPORTED_BENEFICIARY_SCOPE: "
                        + limit.definition().beneficiaryScopeType());
            }
        }

        Collection<Long> bucketIds = limits.stream().map(l -> l.definition().bucketId())
                .filter(Objects::nonNull).distinct().toList();
        Map<BalanceKey, BigDecimal> bucketBalances = new HashMap<>();
        if (!bucketIds.isEmpty()) {
            for (var row : consumptionRepository.aggregateAmountBalances(memberId, bucketIds, excludeClaimId)) {
                bucketBalances.put(new BalanceKey(row.getBucketId(), row.getPeriodStart(), row.getPeriodEnd(),
                        Status.valueOf(row.getStatus())), row.getAmount());
            }
        }

        List<LimitBalance> balances = new ArrayList<>();
        for (var limit : limits) {
            var definition = limit.definition();
            BigDecimal committed;
            BigDecimal reserved;
            if (definition.benefitScopeType() == BenefitScopeType.POLICY_GENERAL) {
                committed = claimRepository.sumApprovedAmountsByMemberAndYearExcludingClaim(
                        memberId, definition.periodStart(), definition.periodEnd(), excludeClaimId);
                // General reservations will remain zero until the pre-authorization reservation
                // ledger is connected; manufacturing them from bucket rows would double count.
                reserved = BigDecimal.ZERO;
            } else {
                committed = amount(bucketBalances, definition, Status.COMMITTED);
                reserved = amount(bucketBalances, definition, Status.RESERVED);
            }
            BigDecimal signedAvailable = limit.effectiveLimit().subtract(committed).subtract(reserved);
            balances.add(new LimitBalance(limit, committed, reserved, signedAvailable));
        }

        BigDecimal binding = balances.stream().map(LimitBalance::signedAvailable)
                .min(BigDecimal::compareTo).orElse(null);
        List<String> bindingKeys = balances.stream()
                .filter(balance -> binding != null && balance.signedAvailable().compareTo(binding) == 0)
                .map(balance -> balance.limit().definition().semanticKey()).toList();
        return new BalanceSet(memberId, List.copyOf(balances), binding, bindingKeys);
    }

    private BigDecimal amount(Map<BalanceKey, BigDecimal> values,
                              ApplicableLimitResolver.ApplicableLimitDefinition definition,
                              Status status) {
        return values.getOrDefault(new BalanceKey(definition.bucketId(), definition.periodStart(),
                definition.periodEnd(), status), BigDecimal.ZERO);
    }
}
