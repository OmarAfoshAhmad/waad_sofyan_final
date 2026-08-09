package com.waad.tba.modules.claim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.FinancialOutboxEvent;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.repository.FinancialOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class ClaimApprovalOutboxService {
    public static final String EVENT_TYPE = "CLAIM_FINANCIALLY_APPROVED";
    private final ClaimRepository claimRepository;
    private final FinancialOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public FinancialOutboxEvent record(Long claimId, Long actorId) {
        Claim claim = claimRepository.findById(claimId).orElseThrow();
        int calculationVersion = claim.getLines().stream()
                .map(line -> line.getCalculationVersion() == null ? 1 : line.getCalculationVersion())
                .max(Integer::compareTo).orElse(1);
        return repository.findByAggregateTypeAndAggregateIdAndEventTypeAndCalculationVersion(
                        "CLAIM", claimId, EVENT_TYPE, calculationVersion)
                .orElseGet(() -> repository.save(FinancialOutboxEvent.builder()
                        .aggregateType("CLAIM").aggregateId(claimId).eventType(EVENT_TYPE)
                        .calculationVersion(calculationVersion)
                        .payload(payload(claim, actorId, calculationVersion))
                        .occurredAt(LocalDateTime.now()).build()));
    }

    private String payload(Claim claim, Long actorId, int calculationVersion) {
        var value = new LinkedHashMap<String, Object>();
        value.put("claimId", claim.getId());
        value.put("providerId", claim.getProviderId());
        value.put("memberId", claim.getMember() != null ? claim.getMember().getId() : null);
        value.put("serviceDate", claim.getServiceDate());
        value.put("approvedAmount", claim.getApprovedAmount());
        value.put("limitConsumption", claim.getLines().stream()
                .map(line -> line.getLimitConsumption() == null ? java.math.BigDecimal.ZERO : line.getLimitConsumption())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        value.put("calculationVersion", calculationVersion);
        value.put("actorId", actorId);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("CLAIM_APPROVAL_OUTBOX_SERIALIZATION_FAILED", error);
        }
    }
}
