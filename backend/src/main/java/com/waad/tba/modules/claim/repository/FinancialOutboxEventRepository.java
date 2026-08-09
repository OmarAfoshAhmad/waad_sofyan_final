package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.FinancialOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface FinancialOutboxEventRepository extends JpaRepository<FinancialOutboxEvent, Long> {
    boolean existsByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);

    Optional<FinancialOutboxEvent> findByAggregateTypeAndAggregateIdAndEventTypeAndCalculationVersion(
            String aggregateType, Long aggregateId, String eventType, Integer calculationVersion);

    List<FinancialOutboxEvent> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType, Long aggregateId, String eventType);
}
