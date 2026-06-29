package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
    
    List<PaymentAuditLog> findByPaymentIdOrderByTimestampDesc(Long paymentId);
}
