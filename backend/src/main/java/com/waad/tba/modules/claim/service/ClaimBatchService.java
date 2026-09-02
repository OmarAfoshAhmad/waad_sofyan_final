package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.modules.claim.entity.ClaimBatch;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Service for managing monthly claim batches.
 * Automatic creation and closing of batches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimBatchService {

    private final ClaimBatchRepository claimBatchRepository;
    private final ProviderRepository providerRepository;
    private final EmployerRepository employerRepository;
    private final SystemSettingsService systemSettingsService;

    /**
     * Search batches for a specific period, optionally filtered by employer
     * and/or provider. Both filters are resolved server-side by the caller
     * (see ClaimBatchController) so a provider-scoped or employer-scoped user
     * can never see another organization's batches by omitting a filter.
     */
    public List<ClaimBatch> findBatches(Long providerId, Long employerId, int year, int month) {
        if (providerId != null && employerId != null) {
            return claimBatchRepository.searchByProviderAndEmployerAndPeriod(providerId, employerId, year, month);
        }
        if (providerId != null) {
            return claimBatchRepository.findByProviderIdAndBatchYearAndBatchMonth(providerId, year, month);
        }
        if (employerId != null) {
            return claimBatchRepository.findByEmployerIdAndBatchYearAndBatchMonth(employerId, year, month);
        }
        return claimBatchRepository.findByBatchYearAndBatchMonth(year, month);
    }

    /**
     * Get existing batch — does NOT create a new one.
     * Returns null if no batch exists yet for this period.
     */
    @Transactional(readOnly = true)
    public ClaimBatch getExistingBatch(Long providerId, Long employerId, int year, int month) {
        return claimBatchRepository
                .findByProviderIdAndEmployerIdAndBatchYearAndBatchMonth(providerId, employerId, year, month)
                .orElse(null);
    }

    /**
     * Get existing batch or create a new one if it doesn't exist yet.
     * Only allowed for current month or up to 3 months in the past.
     */
    @Transactional
    public ClaimBatch getOrCreateBatch(Long providerId, Long employerId, int year, int month) {
        log.debug("🔄 Fetching/Creating batch for provider {}, employer {}, period {}/{}", providerId, employerId,
                month, year);

        return claimBatchRepository
                .findByProviderIdAndEmployerIdAndBatchYearAndBatchMonth(providerId, employerId, year, month)
                .orElseGet(() -> createBatch(providerId, employerId, year, month));
    }

    /**
     * Internal: Actually creates a batch record.
     */
    @Transactional
    public ClaimBatch createBatch(Long providerId, Long employerId, int year, int month) {
        // Validation: No future batches
        YearMonth requested = YearMonth.of(year, month);
        YearMonth current = YearMonth.now();

        if (requested.isAfter(current)) {
            throw new BusinessRuleException("لا يمكن فتح دفعة لشهر مستقبلي: " + month + "/" + year);
        }

        int allowedMonths = systemSettingsService.getClaimBackdatedMonths();
        log.info("🔍 Validating backdated claim: requested={}/{}, current={}, allowedMonths={}", 
                month, year, current, allowedMonths);

        if (allowedMonths == 0 && !requested.equals(current)) {
            throw new BusinessRuleException(
                    "يُسمح بإدخال مطالبات الشهر الحالي فقط. الفترة المطلوبة: " + month + "/" + year);
        }
        if (allowedMonths > 0 && requested.isBefore(current.minusMonths(allowedMonths))) {
            log.warn("🚨 Backdated limit exceeded: requested {} is before limit {}", 
                    requested, current.minusMonths(allowedMonths));
            throw new BusinessRuleException(
                    "لا يمكن فتح دفعات لفترات تتجاوز " + allowedMonths + " أشهر سابقة. الفترة المطلوبة: " + month + "/"
                            + year);
        }

        // Validate provider and employer exist
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider", "id", providerId);
        }
        if (!employerRepository.existsById(employerId)) {
            throw new ResourceNotFoundException("Employer", "id", employerId);
        }

        // Generate Batch Code (safe serial count from existing records)
        String batchCode = generateBatchCode(employerId, year, month);

        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        ClaimBatch batch = ClaimBatch.builder()
                .batchCode(batchCode)
                .providerId(providerId)
                .employerId(employerId)
                .batchYear(year)
                .batchMonth(month)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(ClaimBatch.ClaimBatchStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();

        log.info("✨ Created new claim batch: {} for month {}/{}", batchCode, month, year);
        return claimBatchRepository.save(batch);
    }

    /**
     * Ensures batch is open before permitting activity.
     */
    public void validateBatchIsOpen(Long batchId) {
        if (batchId == null)
            return;

        ClaimBatch batch = claimBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("ClaimBatch", "id", batchId));

        if (batch.getStatus() != ClaimBatch.ClaimBatchStatus.OPEN) {
            throw new BusinessRuleException(
                    "الدفعة [" + batch.getBatchCode() + "] مغلقة. لا يمكن إضافة مطالبات جديدة إليها.");
        }
    }

    /**
     * A claim batch represents the service period, not the entry month. It is
     * therefore valid to enter August services in September only when the batch
     * itself is the August batch. Using createdAt/current month here would bind
     * coverage to clerical timing instead of the date the patient was treated.
     */
    public void validateServiceDateInsideBatch(ClaimBatch batch, LocalDate serviceDate) {
        if (batch == null || serviceDate == null) {
            return;
        }
        if (batch.getPeriodStart() != null && serviceDate.isBefore(batch.getPeriodStart())
                || batch.getPeriodEnd() != null && serviceDate.isAfter(batch.getPeriodEnd())) {
            throw new BusinessRuleException(
                    "تاريخ خدمة المطالبة " + serviceDate
                            + " خارج فترة الدفعة [" + batch.getBatchCode() + "] "
                            + batch.getPeriodStart() + " إلى " + batch.getPeriodEnd()
                            + ". افتح دفعة شهر الخدمة الحقيقي لا شهر الإدخال.");
        }
    }

    /**
     * Auto-close batches from previous months that are still OPEN.
     * Runs at 23:59:00 on the last day of every month.
     * FIX: Uses efficient @Modifying JPQL bulk update instead of loading all
     * records into memory.
     */
    @Scheduled(cron = "0 59 23 L * ?")
    @Transactional
    public void autoCloseExpiredBatches() {
        log.info("🕒 Claim batch auto-close skipped: batches represent service periods, "
                + "and late entry for a previous service month is a normal workflow. "
                + "Close batches explicitly after operational review.");
    }

    /**
     * Format: [EMP_CODE][YY]-[MM]-[SERIAL]
     */
    private String generateBatchCode(Long employerId, int year, int month) {
        String empCode = employerRepository.findById(employerId)
                .map(e -> e.getCode() != null ? e.getCode() : "EMP")
                .orElse("EMP");

        String yy = String.valueOf(year).substring(2);
        String mm = String.format("%02d", month);

        // Count existing batches for this employer+period to get next serial
        long seq = claimBatchRepository.countByEmployerIdAndBatchYearAndBatchMonth(employerId, year, month) + 1;
        String serial = String.format("%05d", seq);

        return empCode.toUpperCase() + yy + "-" + mm + "-" + serial;
    }
}
