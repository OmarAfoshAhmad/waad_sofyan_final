package com.waad.tba.modules.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.dto.BatchItemDetailsDTO;
import com.waad.tba.modules.settlement.dto.BatchSummaryDTO;
import com.waad.tba.modules.settlement.dto.CreateBatchRequest;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.SettlementBatch;
import com.waad.tba.modules.settlement.entity.SettlementBatch.BatchStatus;
import com.waad.tba.modules.settlement.entity.SettlementBatchItem;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.repository.SettlementBatchItemRepository;
import com.waad.tba.modules.settlement.repository.SettlementBatchRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

    private final SettlementBatchRepository batchRepository;
    private final SettlementBatchItemRepository itemRepository;
    private final ProviderAccountRepository accountRepository;
    private final ProviderAccountService accountService;
    private final ProviderPaymentRepository providerPaymentRepository;
    private final ClaimRepository claimRepository;
    private final ProviderRepository providerRepository;

    @Transactional
    public SettlementBatch createBatch(Long providerId, String notes, Long userId) {
        ProviderAccount account = accountService.getOrCreateAccount(providerId);
        String batchNumber = generateBatchNumber();

        SettlementBatch batch = SettlementBatch.builder()
                .batchNumber(batchNumber)
                .providerId(providerId)
                .providerAccountId(account.getId())
                .settlementDate(LocalDate.now())
                .status(BatchStatus.DRAFT)
                .totalClaimsCount(0)
                .totalGrossAmount(BigDecimal.ZERO)
                .totalNetAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .totalPatientShare(BigDecimal.ZERO)
                .notes(notes)
                .createdBy(userId)
                .build();

        batch = batchRepository.save(batch);
        log.info("Batch created: id={}, number={}, provider={}", batch.getId(), batchNumber, providerId);
        return batch;
    }

    @Transactional
    public SettlementBatch createBatchWithClaims(CreateBatchRequest request) {
        SettlementBatch batch = createBatch(request.getProviderId(), request.getDescription(), request.getCreatedBy());
        if (request.getClaimIds() != null && !request.getClaimIds().isEmpty()) {
            addClaimsToBatch(batch.getId(), request.getClaimIds());
        }
        return batchRepository.findById(batch.getId()).orElseThrow();
    }

    @Transactional
    public List<Long> addClaimsToBatch(Long batchId, List<Long> claimIds) {
        SettlementBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found: " + batchId));

        if (batch.getStatus() == BatchStatus.PAID) {
            throw new IllegalStateException("Cannot modify a PAID settlement batch.");
        }

        if (!batch.isModifiable()) {
            throw new IllegalStateException(
                    "Cannot add claims to batch " + batchId + ". Status is: " + batch.getStatus());
        }

        ProviderAccount account = accountRepository.findById(batch.getProviderAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Provider account not found"));
        Long providerId = account.getProviderId();

        // 1. Filter out claims already in ANY batch
        List<Long> alreadyInBatch = itemRepository.findClaimIdsAlreadyInBatch(claimIds);
        List<Long> claimsToProcess = alreadyInBatch.isEmpty() ? claimIds
                : claimIds.stream().filter(id -> !alreadyInBatch.contains(id)).collect(Collectors.toList());

        if (claimsToProcess.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Fetch all claims in one query
        List<Claim> claims = claimRepository.findAllById(claimsToProcess);
        if (claims.size() != claimsToProcess.size()) {
            log.warn("Some claims were not found. Expected: {}, Found: {}", claimsToProcess.size(), claims.size());
        }

        // 3. Validate and prepare items in bulk
        List<SettlementBatchItem> itemsToSave = new ArrayList<>();
        List<Long> addedClaimIds = new ArrayList<>();

        for (Claim claim : claims) {
            try {
                validateClaimForBatch(claim, providerId);

                SettlementBatchItem item = SettlementBatchItem.createFromClaim(
                        batch.getId(),
                        claim.getId(),
                        claim.getRequestedAmount(),
                        claim.getNetPayableAmount(),
                        claim.getPatientCoPay());

                itemsToSave.add(item);
                claim.addToBatch(batch.getId());
                addedClaimIds.add(claim.getId());
            } catch (Exception e) {
                log.error("Failed to add claim {} to batch: {}", claim.getId(), e.getMessage());
                // Skip invalid claims but continue with others
            }
        }

        // 4. Batch save items and claims
        if (!itemsToSave.isEmpty()) {
            itemRepository.saveAll(itemsToSave);
            claimRepository.saveAll(claims);
        }

        recalculateBatchTotals(batch);
        batchRepository.save(batch);

        log.info("Successfully added {} claims to batch {}", addedClaimIds.size(), batchId);
        return addedClaimIds;
    }

    @Transactional
    public List<Long> removeClaimsFromBatch(Long batchId, List<Long> claimIds) {
        SettlementBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found: " + batchId));

        if (batch.getStatus() == BatchStatus.PAID) {
            throw new IllegalStateException("Cannot modify a PAID settlement batch.");
        }

        if (!batch.isModifiable()) {
            throw new IllegalStateException(
                    "Cannot remove claims from batch " + batchId + ". Status is: " + batch.getStatus());
        }

        // 1. Fetch claims in bulk
        List<Claim> claims = claimRepository.findAllById(claimIds);
        List<Long> idsToRemoveFromBatchItems = new ArrayList<>();
        List<Long> actuallyRemovedIds = new ArrayList<>();

        for (Claim claim : claims) {
            if (claim.getSettlementBatchId() != null && claim.getSettlementBatchId().equals(batchId)) {
                claim.removeFromBatch();
                idsToRemoveFromBatchItems.add(claim.getId());
                actuallyRemovedIds.add(claim.getId());
            }
        }

        // 2. Resolve 'No Physical Delete' rule for SettlementBatchItem
        // Previous hardening restricted physical deletion of financial records.
        // However, items in DRAFT batches must be removable.
        // For compliance, we will mark them as removed if possible, or allow deletion
        // only in DRAFT.
        if (!idsToRemoveFromBatchItems.isEmpty()) {
            // FIX: Allow deletion ONLY for DRAFT/PENDING batches to preserve modifiability
            // Financial hardening applies to CONFIRMED/PAID batches.
            if (batch.getStatus() == BatchStatus.DRAFT) {
                itemRepository.deleteByClaimIds(idsToRemoveFromBatchItems);
            } else {
                throw new IllegalStateException("Cannot remove items from non-DRAFT batch to maintain audit trail.");
            }

            claimRepository.saveAll(claims);
        }

        recalculateBatchTotals(batch);
        batchRepository.save(batch);

        log.info("Successfully removed {} claims from batch {}", actuallyRemovedIds.size(), batchId);
        return actuallyRemovedIds;
    }

    @Transactional
    public SettlementBatch confirmBatch(Long batchId, Long userId) {
        SettlementBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found: " + batchId));

        if (batch.getStatus() != BatchStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT batch can be confirmed. Current status: " + batch.getStatus());
        }

        if (!batch.canConfirm()) {
            throw new IllegalStateException("Cannot confirm batch " + batchId + ". Status is: " + batch.getStatus()
                    + ", claims: " + batch.getTotalClaimsCount());
        }

        batch.confirm(userId);
        batchRepository.save(batch);
        return batch;
    }

    @Transactional(readOnly = true)
    public SettlementBatch getBatchById(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found: " + batchId));
    }

    @Transactional(readOnly = true)
    public BatchSummaryDTO getBatchSummary(Long batchId) {
        SettlementBatch batch = getBatchById(batchId);
        ProviderAccount account = accountRepository.findById(batch.getProviderAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
        List<SettlementBatchItem> items = itemRepository.findBySettlementBatchId(batchId);

        String providerName = providerRepository.findById(account.getProviderId())
                .map(Provider::getName)
                .orElse("مقدم خدمة #" + account.getProviderId());

        return BatchSummaryDTO.builder()
                .batchId(batch.getId())
                .batchNumber(batch.getBatchNumber())
                .providerId(account.getProviderId())
                .providerName(providerName)
                .status(batch.getStatus().name())
                .statusArabic(batch.getStatus().getArabicLabel())
                .claimCount(batch.getTotalClaimsCount())
                .totalGrossAmount(batch.getTotalGrossAmount())
                .totalNetAmount(batch.getTotalNetAmount())
                .totalPatientShare(batch.getTotalPatientShare())
                .description(batch.getNotes())
                .paymentReference(providerPaymentRepository.findBySettlementBatchId(batch.getId())
                        .map(ProviderPayment::getPaymentReference).orElse(null))
                .items(items)
                .createdBy(batch.getCreatedBy())
                .createdAt(batch.getCreatedAt())
                .confirmedBy(batch.getConfirmedBy())
                .confirmedAt(batch.getConfirmedAt())
                .paidBy(batch.getPaidBy())
                .paidAt(batch.getPaidAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<SettlementBatch> getBatchesByProviderAccount(Long providerAccountId, Pageable pageable) {
        return batchRepository.findByProviderAccountId(providerAccountId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SettlementBatch> getBatchesByStatus(BatchStatus status, Pageable pageable) {
        return batchRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SettlementBatch> getAllBatches(Pageable pageable) {
        return batchRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Provider getProviderForBatch(SettlementBatch batch) {
        ProviderAccount account = accountRepository.findById(batch.getProviderAccountId()).orElse(null);
        if (account == null) {
            return null;
        }
        return providerRepository.findById(account.getProviderId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Claim> getAvailableClaimsForBatching(Long providerId) {
        List<Claim> approvedClaims = claimRepository.findByProviderIdAndStatusForSettlement(providerId,
                ClaimStatus.APPROVED);
        return approvedClaims.stream().filter(claim -> claim.getSettlementBatchId() == null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SettlementBatchItem> getBatchItems(Long batchId) {
        return itemRepository.findBySettlementBatchId(batchId);
    }

    @Transactional(readOnly = true)
    public List<BatchItemDetailsDTO> getBatchItemDetails(Long batchId) {
        List<SettlementBatchItem> items = itemRepository.findBySettlementBatchId(batchId);
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> claimIds = items.stream().map(SettlementBatchItem::getClaimId).filter(Objects::nonNull).toList();
        Map<Long, Claim> claimById = new HashMap<>();
        claimRepository.findAllById(claimIds).forEach(claim -> claimById.put(claim.getId(), claim));

        return items.stream().map(item -> {
            Claim claim = claimById.get(item.getClaimId());
            String memberName = "-";
            if (claim != null && claim.getMember() != null && claim.getMember().getFullName() != null) {
                memberName = claim.getMember().getFullName();
            }

            return BatchItemDetailsDTO.builder()
                    .id(item.getId())
                    .claimId(item.getClaimId())
                    .claimNumber("CLM-" + item.getClaimId())
                    .memberName(memberName)
                    .serviceDate(claim != null ? claim.getServiceDate() : null)
                    .approvedAmount(item.getNetAmountSnapshot() != null ? item.getNetAmountSnapshot() : BigDecimal.ZERO)
                    .claimStatus(claim != null && claim.getStatus() != null ? claim.getStatus().name() : "BATCHED")
                    .build();
        }).toList();
    }

    private void validateClaimForBatch(Claim claim, Long providerId) {
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new IllegalStateException(
                    "Claim " + claim.getId() + " must be APPROVED. Current status: " + claim.getStatus());
        }
        if (!claim.getProviderId().equals(providerId)) {
            throw new IllegalStateException("Claim " + claim.getId() + " belongs to provider " + claim.getProviderId()
                    + " but batch is for provider " + providerId);
        }
        if (claim.getSettlementBatchId() != null) {
            throw new IllegalStateException(
                    "Claim " + claim.getId() + " is already in batch " + claim.getSettlementBatchId());
        }
        BigDecimal amount = claim.getNetPayableAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Claim " + claim.getId() + " has invalid net amount: " + amount);
        }
    }

    private void recalculateBatchTotals(SettlementBatch batch) {
        List<Object[]> totals = itemRepository.getBatchTotals(batch.getId());
        if (totals != null && !totals.isEmpty()) {
            Object[] row = totals.get(0);
            batch.setTotalClaimsCount(((Number) row[0]).intValue());
            batch.setTotalGrossAmount((BigDecimal) row[1]);
            batch.setTotalNetAmount((BigDecimal) row[2]);
            batch.setTotalAmount((BigDecimal) row[2]);
            batch.setTotalPatientShare((BigDecimal) row[3]);
        } else {
            batch.setTotalClaimsCount(0);
            batch.setTotalGrossAmount(BigDecimal.ZERO);
            batch.setTotalNetAmount(BigDecimal.ZERO);
            batch.setTotalAmount(BigDecimal.ZERO);
            batch.setTotalPatientShare(BigDecimal.ZERO);
        }
    }

    private String generateBatchNumber() {
        String prefix = "STL-" + LocalDate.now().getYear() + "-";
        var latestNumber = batchRepository.findLatestBatchNumber(prefix);

        int sequence = 1;
        if (latestNumber.isPresent()) {
            try {
                String seqPart = latestNumber.get().substring(prefix.length());
                sequence = Integer.parseInt(seqPart) + 1;
            } catch (Exception e) {
                log.warn("Failed to parse batch sequence from: {}", latestNumber.get());
            }
        }

        return prefix + String.format("%06d", sequence);
    }
}
