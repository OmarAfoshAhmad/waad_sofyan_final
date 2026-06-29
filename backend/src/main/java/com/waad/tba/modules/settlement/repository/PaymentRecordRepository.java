package com.waad.tba.modules.settlement.repository;

import com.waad.tba.modules.settlement.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    
    List<PaymentRecord> findByEmployerIdAndProviderIdAndTargetYearAndTargetMonthAndDeletedFalse(
            Long employerId, Long providerId, Integer targetYear, Integer targetMonth);

    List<PaymentRecord> findByEmployerIdAndTargetYearAndTargetMonthAndDeletedFalse(
            Long employerId, Integer targetYear, Integer targetMonth);

    @Query("SELECT p FROM PaymentRecord p WHERE p.deleted = false")
    List<PaymentRecord> findAllActivePayments();
}
