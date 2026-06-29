package com.waad.tba.modules.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType; // CREATE, UPDATE, DELETE

    @Column(name = "old_amount", precision = 12, scale = 3)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", precision = 12, scale = 3)
    private BigDecimal newAmount;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
