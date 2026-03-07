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
            throw new IllegalStateException("Payment amount must exactly match batch total: " + batch.getTotalNetAmount());
        }

        providerAccountService.debitOnBatchPayment(
                batch.getProviderAccountId(),
                batch.getId(),
                batch.getBatchNumber(),
                batch.getTotalNetAmount(),
                userId
        );

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
        payment = providerPaymentRepository.save(payment);

        List<SettlementBatchItem> items = settlementBatchItemRepository.findBySettlementBatchId(batchId);
        LocalDateTime settledAt = LocalDateTime.now();

        // Batch-load all claims in one query, update in memory, then saveAll in one query
        List<Long> claimIds = items.stream().map(SettlementBatchItem::getClaimId).collect(java.util.stream.Collectors.toList());
        List<com.waad.tba.modules.claim.entity.Claim> claims = claimRepository.findAllById(claimIds);
        if (claims.size() != claimIds.size()) {
            // Detect any missing claims before committing
            java.util.Set<Long> found = claims.stream().map(com.waad.tba.modules.claim.entity.Claim::getId).collect(java.util.stream.Collectors.toSet());
            claimIds.stream().filter(id -> !found.contains(id)).findFirst().ifPresent(id -> {
                throw new jakarta.persistence.EntityNotFoundException("Claim not found: " + id);
            });
        }
        String settlementNote = "Settled via payment #" + payment.getId() + " for batch " + batch.getBatchNumber();
        for (com.waad.tba.modules.claim.entity.Claim claim : claims) {
            claim.setStatus(ClaimStatus.SETTLED);
            claim.setSettledAt(settledAt);
            claim.setPaymentReference(reference);
            claim.setSettlementNotes(settlementNote);
            claim.setUpdatedAt(settledAt);
        }
        claimRepository.saveAll(claims);

        batch.pay(userId);
        settlementBatchRepository.save(batch);

        Provider provider = providerRepository.findById(batch.getProviderId()).orElse(null);

        log.info("Provider payment recorded: paymentId={}, batchId={}, amount={}", payment.getId(), batchId, payment.getAmount());

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
}
