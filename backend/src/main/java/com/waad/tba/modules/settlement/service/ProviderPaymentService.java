package com.waad.tba.modules.settlement.service;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.api.request.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.ProviderPaymentDTO;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.SettlementBatch;
import com.waad.tba.modules.settlement.entity.SettlementBatchItem;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.repository.SettlementBatchItemRepository;
import com.waad.tba.modules.settlement.repository.SettlementBatchRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderPaymentService {

    private final ProviderPaymentRepository providerPaymentRepository;
    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementBatchItemRepository settlementBatchItemRepository;
    private final ClaimRepository claimRepository;
    private final ProviderRepository providerRepository;
    private final ProviderAccountService providerAccountService;
    private final AccountTransactionService accountTransactionService;

    @Transactional(readOnly = true)
    public Page<SettlementBatch> getConfirmedBatches(Pageable pageable) {
        return settlementBatchRepository.findByStatus(SettlementBatch.BatchStatus.CONFIRMED, pageable);
    }

    @Transactional(readOnly = true)
    public BigDecimal getOutstandingConfirmedUnpaidTotal() {
        BigDecimal total = settlementBatchRepository.getOutstandingConfirmedNotPaidAmount();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional
    public ProviderPaymentDTO createPayment(Long batchId, CreateProviderPaymentRequest request, Long userId) {
        SettlementBatch batch = settlementBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found: " + batchId));

        if (batch.getStatus() != SettlementBatch.BatchStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED batches can be paid. Current status: " + batch.getStatus());
        }

        if (providerPaymentRepository.existsBySettlementBatchId(batchId)) {
            throw new IllegalStateException("Payment already recorded for batch: " + batchId);
        }

        String reference = request.getPaymentReference() != null ? request.getPaymentReference().trim() : null;
        if (reference == null || reference.isBlank()) {
            throw new IllegalStateException("Payment reference is required");
        }
        if (providerPaymentRepository.existsByPaymentReference(reference)) {
            throw new IllegalStateException("Payment reference already exists: " + reference);
        }

        if (request.getAmount() == null || batch.getTotalNetAmount() == null
                || request.getAmount().compareTo(batch.getTotalNetAmount()) != 0) {
            throw new IllegalStateException(
                    "Payment amount must exactly match batch total: " + batch.getTotalNetAmount());
        }

        // --- Step 1: Load and verify items ---
        List<SettlementBatchItem> items = settlementBatchItemRepository.findBySettlementBatchId(batchId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Batch " + batchId + " has no items. Cannot process payment.");
        }

        List<Long> claimIds = items.stream()
                .map(SettlementBatchItem::getClaimId)
                .collect(java.util.stream.Collectors.toList());

        // --- Step 2: Load claims with lock ---
        List<com.waad.tba.modules.claim.entity.Claim> claims = claimRepository.findAllByIdWithLock(claimIds);
        if (claims.size() != claimIds.size()) {
            java.util.Set<Long> found = claims.stream()
                    .map(com.waad.tba.modules.claim.entity.Claim::getId)
                    .collect(java.util.stream.Collectors.toSet());
            Long missing = claimIds.stream().filter(id -> !found.contains(id)).findFirst().orElse(null);
            throw new EntityNotFoundException("Claim not found: " + missing);
        }

        // --- Step 3: Mark claims as SETTLED (fail fast before any financial change)
        // ---
        LocalDateTime settledAt = LocalDateTime.now();
        String settlementNote = "تمت التسوية عبر دفعة #" + batch.getBatchNumber();
        for (com.waad.tba.modules.claim.entity.Claim claim : claims) {
            if (claim.getStatus() != ClaimStatus.APPROVED) {
                throw new IllegalStateException(
                        "Claim " + claim.getId() + " cannot be settled: expected APPROVED but found "
                                + claim.getStatus() + ". Batch may contain stale or modified claims.");
            }
            claim.setStatus(ClaimStatus.SETTLED);
            claim.setSettledAt(settledAt);
            claim.setPaymentReference(reference);
            claim.setSettlementNotes(settlementNote);
            claim.setUpdatedAt(settledAt);
        }
        claimRepository.saveAll(claims);

        // --- Step 4: Debit the provider account ---
        providerAccountService.debitOnBatchPayment(
                batch.getProviderAccountId(),
                batch.getId(),
                batch.getBatchNumber(),
                batch.getTotalNetAmount(),
                userId);

        // --- Step 5: Record the payment ---
        ProviderPayment payment = ProviderPayment.builder()
                .settlementBatchId(batch.getId())
                .providerId(batch.getProviderId())
                .amount(batch.getTotalNetAmount())
                .paymentReference(reference)
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDateTime.now())
                .notes(request.getNotes())
                .createdBy(userId)
                .build();
        try {
            payment = providerPaymentRepository.save(payment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "Payment reference '" + reference
                            + "' was used by a concurrent request. Please try again with a unique reference.",
                    e);
        }

        batch.pay(userId);
        settlementBatchRepository.save(batch);

        Provider provider = providerRepository.findById(batch.getProviderId()).orElse(null);

        log.info("Provider payment recorded: paymentId={}, batchId={}, amount={}", payment.getId(), batchId,
                payment.getAmount());

        return ProviderPaymentDTO.builder()
                .paymentId(payment.getId())
                .batchId(batch.getId())
                .batchNumber(batch.getBatchNumber())
                .providerId(batch.getProviderId())
                .providerName(provider != null ? provider.getName() : ("Provider #" + batch.getProviderId()))
                .amount(payment.getAmount())
                .paymentReference(payment.getPaymentReference())
                .paymentMethod(payment.getPaymentMethod())
                .paymentDate(payment.getPaymentDate())
                .notes(payment.getNotes())
                .createdBy(payment.getCreatedBy())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    @Transactional
    public ProviderPaymentDTO createInstallmentPayment(Long providerId, CreateProviderPaymentRequest request,
            Long userId) {
        String reference = request.getPaymentReference() != null ? request.getPaymentReference().trim() : null;
        if (reference == null || reference.isBlank()) {
            throw new IllegalStateException("Payment reference is required");
        }
        if (providerPaymentRepository.existsByPaymentReference(reference)) {
            throw new IllegalStateException("Payment reference already exists: " + reference);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Payment amount must be greater than zero");
        }

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new EntityNotFoundException("Provider not found: " + providerId));

        // Get or create account to avoid hard failures when legacy providers have no
        // account row yet.
        com.waad.tba.modules.settlement.entity.ProviderAccount account = providerAccountService
                .getOrCreateAccount(providerId);
        BigDecimal runningBalance = account.getRunningBalance() != null ? account.getRunningBalance() : BigDecimal.ZERO;

        if (runningBalance.compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException(
                    "Insufficient balance for installment. Provider balance: " + runningBalance +
                            ", Payment amount: " + request.getAmount());
        }

        // Debit the provider account: validates balance, persists, and creates the
        // account_transaction record in one atomic operation
        String adjustmentNote = "دفعة قسطية - مرجع: " + reference
                + (request.getNotes() != null && !request.getNotes().isBlank()
                        ? " / " + request.getNotes()
                        : "");
        providerAccountService.debitOnInstallmentPayment(providerId, request.getAmount(), adjustmentNote, userId);

        ProviderPayment payment = ProviderPayment.builder()
                .settlementBatchId(null) // Unlinked to a specific batch
                .providerId(providerId)
                .amount(request.getAmount())
                .paymentReference(reference)
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDateTime.now())
                .notes(request.getNotes())
                .createdBy(userId)
                .build();
        try {
            payment = providerPaymentRepository.save(payment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate installment payment reference '{}' for provider {}. "
                    + "Transaction rolled back cleanly — no financial change occurred.", reference, providerId);
            throw new IllegalStateException(
                    "Payment reference '" + reference
                            + "' was used by a concurrent request. Please try again with a unique reference.",
                    e);
        }

        log.info("Provider installment recorded: paymentId={}, providerId={}, amount={}", payment.getId(), providerId,
                payment.getAmount());

        return ProviderPaymentDTO.builder()
                .paymentId(payment.getId())
                .batchId(null)
                .batchNumber(null)
                .providerId(providerId)
                .providerName(provider.getName())
                .amount(payment.getAmount())
                .paymentReference(payment.getPaymentReference())
                .paymentMethod(payment.getPaymentMethod())
                .paymentDate(payment.getPaymentDate())
                .notes(payment.getNotes())
                .createdBy(payment.getCreatedBy())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
