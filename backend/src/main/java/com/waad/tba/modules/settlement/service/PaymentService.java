package com.waad.tba.modules.settlement.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.settlement.dto.MonthlySettlementSummaryDto;
import com.waad.tba.modules.settlement.dto.PaymentAuditLogDto;
import com.waad.tba.modules.settlement.dto.PaymentRecordDto;
import com.waad.tba.modules.settlement.dto.PaymentRequestDto;
import com.waad.tba.modules.settlement.entity.PaymentAuditLog;
import com.waad.tba.modules.settlement.entity.PaymentRecord;
import com.waad.tba.modules.settlement.repository.PaymentAuditLogRepository;
import com.waad.tba.modules.settlement.repository.PaymentRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<MonthlySettlementSummaryDto> getMonthlySettlementSummaries(
            Long employerId, Long providerId, Integer year, Integer month, String status) {

        StringBuilder jpql = new StringBuilder(
            "SELECT c.member.employer.id, c.member.employer.name, " +
            "c.providerId, p.name, " +
            "YEAR(c.serviceDate), MONTH(c.serviceDate), " +
            "SUM(COALESCE(c.netProviderAmount, COALESCE(c.approvedAmount, 0.0))) " +
            "FROM Claim c " +
            "LEFT JOIN Provider p ON p.id = c.providerId " +
            "WHERE c.active = true AND c.status IN ('APPROVED', 'SETTLED') "
        );

        if (employerId != null) {
            jpql.append(" AND c.member.employer.id = :employerId ");
        }
        if (providerId != null) {
            jpql.append(" AND c.providerId = :providerId ");
        }
        if (year != null) {
            jpql.append(" AND YEAR(c.serviceDate) = :year ");
        }
        if (month != null) {
            jpql.append(" AND MONTH(c.serviceDate) = :month ");
        }

        jpql.append(" GROUP BY c.member.employer.id, c.member.employer.name, c.providerId, p.name, YEAR(c.serviceDate), MONTH(c.serviceDate) ");
        jpql.append(" ORDER BY YEAR(c.serviceDate) DESC, MONTH(c.serviceDate) DESC ");

        Query query = entityManager.createQuery(jpql.toString());
        if (employerId != null) query.setParameter("employerId", employerId);
        if (providerId != null) query.setParameter("providerId", providerId);
        if (year != null) query.setParameter("year", year);
        if (month != null) query.setParameter("month", month);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        // Get all active payments
        List<PaymentRecord> allPayments = paymentRecordRepository.findAllActivePayments();

        List<MonthlySettlementSummaryDto> summaries = new ArrayList<>();

        for (Object[] row : results) {
            Long empId = (Long) row[0];
            String empName = (String) row[1];
            Long provId = (Long) row[2];
            String provName = (String) row[3];
            Integer targetYear = (Integer) row[4];
            Integer targetMonth = (Integer) row[5];
            BigDecimal totalAmount = row[6] != null ? BigDecimal.valueOf(((Number) row[6]).doubleValue()) : BigDecimal.ZERO;

            List<PaymentRecord> matchingPayments = allPayments.stream()
                .filter(p -> p.getEmployerId().equals(empId) &&
                             p.getProviderId().equals(provId) &&
                             p.getTargetYear().equals(targetYear) &&
                             p.getTargetMonth().equals(targetMonth))
                .collect(Collectors.toList());

            BigDecimal paidAmount = matchingPayments.stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal remainingAmount = totalAmount.subtract(paidAmount);
            if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
                remainingAmount = BigDecimal.ZERO;
            }

            LocalDate lastPaymentDate = matchingPayments.stream()
                .map(PaymentRecord::getPaymentDate)
                .max(LocalDate::compareTo)
                .orElse(null);

            String paymentStatus;
            String paymentStatusLabel;

            if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
                paymentStatus = "UNPAID";
                paymentStatusLabel = "غير مدفوع";
            } else if (paidAmount.compareTo(totalAmount) >= 0) {
                paymentStatus = "FULLY_PAID";
                paymentStatusLabel = "مدفوع بالكامل";
            } else {
                paymentStatus = "PARTIALLY_PAID";
                paymentStatusLabel = "مدفوع جزئياً";
            }

            // Filter by status if provided
            if (status != null && !status.isEmpty() && !status.equals("ALL") && !paymentStatus.equals(status)) {
                continue;
            }

            summaries.add(MonthlySettlementSummaryDto.builder()
                .employerId(empId)
                .employerName(empName)
                .providerId(provId)
                .providerName(provName)
                .targetYear(targetYear)
                .targetMonth(targetMonth)
                .totalAmount(totalAmount)
                .paidAmount(paidAmount)
                .remainingAmount(remainingAmount)
                .lastPaymentDate(lastPaymentDate)
                .paymentStatus(paymentStatus)
                .paymentStatusLabel(paymentStatusLabel)
                .build());
        }

        return summaries;
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordDto> getPaymentsForSettlement(Long employerId, Long providerId, Integer year, Integer month) {
        List<PaymentRecord> payments = paymentRecordRepository.findByEmployerIdAndProviderIdAndTargetYearAndTargetMonthAndDeletedFalse(
                employerId, providerId, year, month);
        return payments.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public PaymentRecordDto addPayment(PaymentRequestDto request, String userId) {
        // Validation: totalAmount vs paidAmount
        List<MonthlySettlementSummaryDto> summaries = getMonthlySettlementSummaries(
                request.getEmployerId(), request.getProviderId(), request.getTargetYear(), request.getTargetMonth(), null);

        if (summaries.isEmpty()) {
            throw new BusinessRuleException("لا توجد مطالبات معتمدة لهذا الشهر لتسديدها.");
        }

        MonthlySettlementSummaryDto summary = summaries.get(0);
        
        if (!request.isOverrideLimit()) {
            if (request.getAmount().compareTo(summary.getRemainingAmount()) > 0) {
                throw new BusinessRuleException("مبلغ الدفعة يتجاوز المتبقي. يرجى تفعيل (تجاوز الحد) إذا كان لديك الصلاحية.");
            }
        } else {
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                throw new BusinessRuleException("سبب التجاوز إلزامي عند إدخال مبلغ أكبر من المتبقي.");
            }
        }

        PaymentRecord payment = PaymentRecord.builder()
                .employerId(request.getEmployerId())
                .providerId(request.getProviderId())
                .targetYear(request.getTargetYear())
                .targetMonth(request.getTargetMonth())
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate())
                .paymentMethod(request.getPaymentMethod())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .attachmentPath(request.getAttachmentPath())
                .createdBy(userId)
                .build();

        paymentRecordRepository.save(payment);

        saveAuditLog(payment.getId(), userId, "CREATE", BigDecimal.ZERO, payment.getAmount(), 
                request.isOverrideLimit() ? "تجاوز الحد المسموح: " + request.getReason() : "إضافة دفعة جديدة");

        return mapToDto(payment);
    }

    @Transactional
    public PaymentRecordDto updatePayment(Long paymentId, PaymentRequestDto request, String userId) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessRuleException("سبب التعديل إلزامي لتحديث الدفعة.");
        }

        PaymentRecord payment = paymentRecordRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("الدفعة غير موجودة"));

        if (payment.isDeleted()) {
            throw new BusinessRuleException("لا يمكن تعديل دفعة محذوفة.");
        }

        BigDecimal oldAmount = payment.getAmount();

        // Check limits again minus the old payment amount
        List<MonthlySettlementSummaryDto> summaries = getMonthlySettlementSummaries(
                payment.getEmployerId(), payment.getProviderId(), payment.getTargetYear(), payment.getTargetMonth(), null);
        
        if (!summaries.isEmpty()) {
            MonthlySettlementSummaryDto summary = summaries.get(0);
            BigDecimal remainingWithoutThisPayment = summary.getRemainingAmount().add(oldAmount);
            
            if (!request.isOverrideLimit() && request.getAmount().compareTo(remainingWithoutThisPayment) > 0) {
                throw new BusinessRuleException("المبلغ الجديد يتجاوز المتبقي. يرجى تفعيل (تجاوز الحد) إذا كان لديك الصلاحية.");
            }
        }

        payment.setAmount(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setReferenceNumber(request.getReferenceNumber());
        payment.setNotes(request.getNotes());
        payment.setAttachmentPath(request.getAttachmentPath());
        payment.setUpdatedBy(userId);

        paymentRecordRepository.save(payment);

        saveAuditLog(payment.getId(), userId, "UPDATE", oldAmount, payment.getAmount(), request.getReason());

        return mapToDto(payment);
    }

    @Transactional
    public void deletePayment(Long paymentId, String reason, String userId) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleException("سبب الإلغاء إلزامي.");
        }

        PaymentRecord payment = paymentRecordRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("الدفعة غير موجودة"));

        if (payment.isDeleted()) {
            return;
        }

        BigDecimal oldAmount = payment.getAmount();
        
        payment.setDeleted(true);
        payment.setUpdatedBy(userId);
        paymentRecordRepository.save(payment);

        saveAuditLog(payment.getId(), userId, "DELETE", oldAmount, BigDecimal.ZERO, reason);
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditLogDto> getPaymentAuditLogs(Long paymentId) {
        return paymentAuditLogRepository.findByPaymentIdOrderByTimestampDesc(paymentId)
                .stream().map(log -> PaymentAuditLogDto.builder()
                        .id(log.getId())
                        .paymentId(log.getPaymentId())
                        .userId(log.getUserId())
                        .actionType(log.getActionType())
                        .oldAmount(log.getOldAmount())
                        .newAmount(log.getNewAmount())
                        .reason(log.getReason())
                        .timestamp(log.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    private void saveAuditLog(Long paymentId, String userId, String actionType, BigDecimal oldAmt, BigDecimal newAmt, String reason) {
        PaymentAuditLog auditLog = PaymentAuditLog.builder()
                .paymentId(paymentId)
                .userId(userId)
                .actionType(actionType)
                .oldAmount(oldAmt)
                .newAmount(newAmt)
                .reason(reason)
                .build();
        paymentAuditLogRepository.save(auditLog);
    }

    private PaymentRecordDto mapToDto(PaymentRecord payment) {
        // To fetch employerName and providerName properly, we would query them.
        // For simplicity, we can fetch them via EntityManager or leave them null if not directly needed 
        // because the caller usually has the context, or we can fetch them here.
        String empName = "وثيقة #" + payment.getEmployerId();
        String provName = "مقدم خدمة #" + payment.getProviderId();
        
        try {
            Query empQ = entityManager.createQuery("SELECT e.name FROM Employer e WHERE e.id = :id");
            empQ.setParameter("id", payment.getEmployerId());
            empName = (String) empQ.getSingleResult();
            
            Query provQ = entityManager.createQuery("SELECT p.name FROM Provider p WHERE p.id = :id");
            provQ.setParameter("id", payment.getProviderId());
            provName = (String) provQ.getSingleResult();
        } catch (Exception ignored) { }

        return PaymentRecordDto.builder()
                .id(payment.getId())
                .employerId(payment.getEmployerId())
                .employerName(empName)
                .providerId(payment.getProviderId())
                .providerName(provName)
                .targetYear(payment.getTargetYear())
                .targetMonth(payment.getTargetMonth())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMethod(payment.getPaymentMethod())
                .paymentMethodLabel(payment.getPaymentMethod().getArabicLabel())
                .referenceNumber(payment.getReferenceNumber())
                .notes(payment.getNotes())
                .attachmentPath(payment.getAttachmentPath())
                .createdAt(payment.getCreatedAt())
                .createdBy(payment.getCreatedBy())
                .updatedAt(payment.getUpdatedAt())
                .updatedBy(payment.getUpdatedBy())
                .build();
    }
}
