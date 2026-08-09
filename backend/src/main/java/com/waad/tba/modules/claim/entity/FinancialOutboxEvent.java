package com.waad.tba.modules.claim.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_outbox_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FinancialOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;
    @Column(name = "calculation_version", nullable = false, updatable = false)
    private Integer calculationVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "delivery_attempts", nullable = false)
    @Builder.Default private Integer deliveryAttempts = 0;
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void create() {
        if (eventId == null) eventId = UUID.randomUUID();
        if (occurredAt == null) occurredAt = LocalDateTime.now();
        if (createdAt == null) createdAt = occurredAt;
    }
}
