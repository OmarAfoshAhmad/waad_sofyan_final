package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.*;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Provider Contracts (New standalone module).
 * 
 * Business Rules:
 * 1. Only ONE active contract per provider at any time
 * 2. Cannot activate an expired contract
 * 3. Cannot have overlapping date ranges for same provider
 * 4. Status transitions follow state machine rules
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Slf4j
@Service("providerContractModuleService")
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ProviderContractService {

    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final ProviderRepository providerRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all contracts (paginated)
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> findAll(Pageable pageable) {
        log.debug("Finding all provider contracts, page: {}", pageable.getPageNumber());
        return contractRepository.findByActiveTrue(pageable)
                .map(ProviderContractResponseDto::fromEntity);
    }

    /**
     * Get soft-deleted contracts (paginated)
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> findDeleted(Pageable pageable) {
        log.debug("Finding soft-deleted provider contracts, page: {}", pageable.getPageNumber());
        return contractRepository.findByActiveFalse(pageable)
                .map(ProviderContractResponseDto::fromEntity);
    }

    /**
     * Get contract by ID
     */
    @Transactional(readOnly = true)
    public ProviderContractResponseDto findById(Long id) {
        log.debug("Finding provider contract by ID: {}", id);
        ProviderContract contract = contractRepository.findById(id)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));
        return ProviderContractResponseDto.fromEntity(contract);
    }

    /**
     * Get contract by code
     */
    @Transactional(readOnly = true)
    public ProviderContractResponseDto findByCode(String contractCode) {
        log.debug("Finding provider contract by code: {}", contractCode);
        ProviderContract contract = contractRepository.findByContractCodeAndActiveTrue(contractCode)
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + contractCode));
        return ProviderContractResponseDto.fromEntity(contract);
    }

    /**
     * Get all contracts for a provider
     */
    @Transactional(readOnly = true)
    public List<ProviderContractResponseDto> findByProvider(Long providerId) {
        log.debug("Finding contracts for provider: {}", providerId);
        return contractRepository.findByProviderIdAndActiveTrue(providerId)
                .stream()
                .map(ProviderContractResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get contracts for a provider (paginated)
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> findByProvider(Long providerId, Pageable pageable) {
        log.debug("Finding contracts for provider: {}, page: {}", providerId, pageable.getPageNumber());
        return contractRepository.findByProviderIdAndActiveTrue(providerId, pageable)
                .map(ProviderContractResponseDto::fromEntity);
    }

    /**
     * Get active contract for a provider
     */
    @Transactional(readOnly = true)
    public ProviderContractResponseDto findActiveByProvider(Long providerId) {
        log.debug("Finding active contract for provider: {}", providerId);
        return contractRepository.findActiveContractByProvider(providerId)
                .map(ProviderContractResponseDto::fromEntity)
                .orElse(null);
    }

    /**
     * Get contracts by status
     */
    @Transactional(readOnly = true)
    public List<ProviderContractResponseDto> findByStatus(ContractStatus status) {
        log.debug("Finding contracts with status: {}", status);
        return contractRepository.findByStatusAndActiveTrue(status)
                .stream()
                .map(ProviderContractResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get contracts by status (paginated)
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> findByStatus(ContractStatus status, Pageable pageable) {
        log.debug("Finding contracts with status: {}, page: {}", status, pageable.getPageNumber());
        return contractRepository.findByStatusAndActiveTrue(status, pageable)
                .map(ProviderContractResponseDto::fromEntity);
    }

    /**
     * Search contracts
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> search(String query, ContractStatus status, Pageable pageable) {
        log.debug("Searching contracts: query={}, status={}", query, status);

        if (query == null || query.trim().isEmpty()) {
            if (status != null) {
                return findByStatus(status, pageable);
            }
            return findAll(pageable);
        }

        if (status != null) {
            return contractRepository.searchByCodeOrProviderNameWithStatus(query, status, pageable)
                    .map(ProviderContractResponseDto::fromEntity);
        }

        return contractRepository.searchByCodeOrProviderName(query, pageable)
                .map(ProviderContractResponseDto::fromEntity);
    }

    /**
     * Get contracts expiring within N days
     */
    @Transactional(readOnly = true)
    public List<ProviderContractResponseDto> findExpiringWithinDays(int days) {
        log.debug("Finding contracts expiring within {} days", days);
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        return contractRepository.findExpiringBetween(startDate, endDate)
                .stream()
                .map(ProviderContractResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get contract statistics
     */
    @Transactional(readOnly = true)
    public ProviderContractStatsDto getStatistics() {
        log.debug("Getting contract statistics");

        return ProviderContractStatsDto.builder()
                .totalContracts(contractRepository.countByActiveTrue())
                .activeContracts(contractRepository.countByStatusAndActiveTrue(ContractStatus.ACTIVE))
                .draftContracts(contractRepository.countByStatusAndActiveTrue(ContractStatus.DRAFT))
                .expiredContracts(contractRepository.countByStatusAndActiveTrue(ContractStatus.EXPIRED))
                .suspendedContracts(contractRepository.countByStatusAndActiveTrue(ContractStatus.SUSPENDED))
                .terminatedContracts(contractRepository.countByStatusAndActiveTrue(ContractStatus.TERMINATED))
                .totalActiveValue(contractRepository.getTotalValueByStatus(ContractStatus.ACTIVE))
                .totalExpiredValue(contractRepository.getTotalValueByStatus(ContractStatus.EXPIRED))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new contract
     */
    @Transactional
    public ProviderContractResponseDto create(ProviderContractCreateDto dto) {
        log.info("Creating new provider contract for provider: {}", dto.getProviderId());

        // Validate provider exists
        Provider provider = providerRepository.findById(dto.getProviderId())
                .orElseThrow(() -> new BusinessRuleException("Provider not found: " + dto.getProviderId()));

        // Validate dates
        if (dto.getEndDate() != null && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessRuleException("Start date must be before end date");
        }

        // Generate contract code if not provided
        String contractCode = dto.getContractCode();
        if (contractCode == null || contractCode.isBlank()) {
            contractCode = generateContractCode();
        } else if (contractRepository.existsByContractCode(contractCode)) {
            throw new BusinessRuleException("Contract code already exists: " + contractCode);
        }

        // Build entity
        ProviderContract contract = ProviderContract.builder()
                .contractCode(contractCode)
                .contractNumber(contractCode) // Legacy compatibility
                .provider(provider)
                .status(dto.getStatus() != null ? dto.getStatus() : ContractStatus.DRAFT)
                .pricingModel(dto.getPricingModel() != null ? dto.getPricingModel() : PricingModel.DISCOUNT)
                .discountPercent(dto.getDiscountPercent() != null ? dto.getDiscountPercent() : BigDecimal.ZERO)
                .discountBeforeRejection(dto.getDiscountBeforeRejection() != null ? dto.getDiscountBeforeRejection() : false)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .signedDate(dto.getSignedDate())
                .totalValue(dto.getTotalValue())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "LYD")
                .paymentTerms(dto.getPaymentTerms())
                .autoRenew(dto.getAutoRenew() != null ? dto.getAutoRenew() : false)
                .contactPerson(dto.getContactPerson())
                .contactPhone(dto.getContactPhone())
                .contactEmail(dto.getContactEmail())
                .notes(dto.getNotes())
                .active(true)
                .createdBy(getCurrentUsername())
                .build();

        contract = contractRepository.save(contract);
        log.info("Created provider contract: {}", contract.getContractCode());

        return ProviderContractResponseDto.fromEntity(contract);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update an existing contract
     */
    @Transactional
    public ProviderContractResponseDto update(Long id, ProviderContractUpdateDto dto) {
        log.info("Updating provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        // Cannot update terminated contracts
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BusinessRuleException("Cannot update a terminated contract");
        }

        // Validate dates if changed
        LocalDate startDate = dto.getStartDate() != null ? dto.getStartDate() : contract.getStartDate();
        LocalDate endDate = dto.getEndDate() != null ? dto.getEndDate() : contract.getEndDate();

        if (endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessRuleException("Start date must be before end date");
        }

        // Check for overlapping contracts if dates changed
        if ((dto.getStartDate() != null || dto.getEndDate() != null) && contract.getStatus() != ContractStatus.DRAFT) {
            checkForOverlappingContracts(contract.getProvider().getId(), contract.getId(), startDate, endDate);
        }

        // Apply updates
        if (dto.getPricingModel() != null) {
            contract.setPricingModel(dto.getPricingModel());
        }
        if (dto.getDiscountPercent() != null) {
            contract.setDiscountPercent(dto.getDiscountPercent());
        }
        if (dto.getDiscountBeforeRejection() != null) {
            contract.setDiscountBeforeRejection(dto.getDiscountBeforeRejection());
        }
        if (dto.getStartDate() != null) {
            contract.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            contract.setEndDate(dto.getEndDate());
        }
        if (dto.getSignedDate() != null) {
            contract.setSignedDate(dto.getSignedDate());
        }
        if (dto.getTotalValue() != null) {
            contract.setTotalValue(dto.getTotalValue());
        }
        if (dto.getCurrency() != null) {
            contract.setCurrency(dto.getCurrency());
        }
        if (dto.getPaymentTerms() != null) {
            contract.setPaymentTerms(dto.getPaymentTerms());
        }
        if (dto.getAutoRenew() != null) {
            contract.setAutoRenew(dto.getAutoRenew());
        }
        if (dto.getContactPerson() != null) {
            contract.setContactPerson(dto.getContactPerson());
        }
        if (dto.getContactPhone() != null) {
            contract.setContactPhone(dto.getContactPhone());
        }
        if (dto.getContactEmail() != null) {
            contract.setContactEmail(dto.getContactEmail());
        }
        if (dto.getNotes() != null) {
            contract.setNotes(dto.getNotes());
        }

        contract.setUpdatedBy(getCurrentUsername());
        contract = contractRepository.save(contract);

        log.info("Updated provider contract: {}", contract.getContractCode());
        return ProviderContractResponseDto.fromEntity(contract);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Activate a contract
     */
    @Transactional
    public ProviderContractResponseDto activate(Long id) {
        log.info("Activating provider contract: {}", id);

        ProviderContract contractToActivate = contractRepository.findById(id)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        // Validate can activate (supports reactivation from TERMINATED)
        if (!contractToActivate.canActivate() && contractToActivate.getStatus() != ContractStatus.TERMINATED) {
            throw new BusinessRuleException("Cannot activate contract with status: " + contractToActivate.getStatus());
        }

        // Cannot activate expired contract
        if (contractToActivate.hasExpired()) {
            throw new BusinessRuleException("Cannot activate an expired contract");
        }

        final Long providerId = contractToActivate.getProvider().getId();
        final Long contractId = contractToActivate.getId();

        // Check for existing active contract for same provider
        contractRepository.findActiveContractByProvider(providerId)
                .filter(existing -> !existing.getId().equals(contractId))
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "يوجد عقد نشط مسبقاً لمقدم الخدمة: " + existing.getContractCode());
                });

        // Check for overlapping contracts
        checkForOverlappingContracts(providerId, contractId,
                contractToActivate.getStartDate(), contractToActivate.getEndDate());

        contractToActivate.setStatus(ContractStatus.ACTIVE);
        contractToActivate.setUpdatedBy(getCurrentUsername());
        ProviderContract savedContract = contractRepository.save(contractToActivate);

        log.info("Activated provider contract: {}", savedContract.getContractCode());
        return ProviderContractResponseDto.fromEntity(savedContract);
    }

    /**
     * Suspend a contract
     */
    @Transactional
    public ProviderContractResponseDto suspend(Long id, String reason) {
        log.info("Suspending provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        if (!contract.canSuspend()) {
            throw new BusinessRuleException("Cannot suspend contract with status: " + contract.getStatus());
        }

        contract.setStatus(ContractStatus.SUSPENDED);
        if (reason != null && !reason.isBlank()) {
            String notes = contract.getNotes() != null ? contract.getNotes() + "\n" : "";
            notes += "[" + LocalDate.now() + "] Suspended: " + reason;
            contract.setNotes(notes);
        }
        contract.setUpdatedBy(getCurrentUsername());
        contract = contractRepository.save(contract);

        log.info("Suspended provider contract: {}", contract.getContractCode());
        return ProviderContractResponseDto.fromEntity(contract);
    }

    /**
     * Terminate a contract
     */
    @Transactional
    public ProviderContractResponseDto terminate(Long id, String reason) {
        log.info("Terminating provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        if (!contract.canTerminate()) {
            throw new BusinessRuleException("Cannot terminate contract with status: " + contract.getStatus());
        }

        contract.setStatus(ContractStatus.TERMINATED);
        if (reason != null && !reason.isBlank()) {
            String notes = contract.getNotes() != null ? contract.getNotes() + "\n" : "";
            notes += "[" + LocalDate.now() + "] Terminated: " + reason;
            contract.setNotes(notes);
        }
        contract.setUpdatedBy(getCurrentUsername());
        contract = contractRepository.save(contract);

        log.info("Terminated provider contract: {}", contract.getContractCode());
        return ProviderContractResponseDto.fromEntity(contract);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Soft delete a contract
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        // Cannot delete active contract
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            throw new BusinessRuleException("Cannot delete an active contract. Suspend or terminate it first.");
        }

        contract.setActive(false);
        contract.setUpdatedBy(getCurrentUsername());
        contractRepository.save(contract);

        log.info("Soft deleted provider contract: {}", contract.getContractCode());
    }

    /**
     * Restore a soft-deleted contract
     */
    @Transactional
    public ProviderContractResponseDto restore(Long id) {
        log.info("Restoring provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .filter(c -> Boolean.FALSE.equals(c.getActive()))
                .orElseThrow(() -> new BusinessRuleException("Deleted provider contract not found: " + id));

        contract.setActive(true);
        contract.setUpdatedBy(getCurrentUsername());
        contract = contractRepository.save(contract);

        log.info("Restored provider contract: {}", contract.getContractCode());
        return ProviderContractResponseDto.fromEntity(contract);
    }

    /**
     * Permanently delete a soft-deleted contract and all of its pricing items.
     */
    @Transactional
    public void hardDelete(Long id) {
        log.info("Hard deleting provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        if (Boolean.TRUE.equals(contract.getActive())) {
            throw new BusinessRuleException("لا يمكن الحذف النهائي قبل النقل إلى سجل المحذوفات أولاً.");
        }

        try {
            pricingItemRepository.hardDeleteByContractId(id);
            contractRepository.delete(contract);
            contractRepository.flush();
            log.info("Hard deleted provider contract: {}", contract.getContractCode());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("تعذر الحذف النهائي للعقد لوجود بيانات مرتبطة به.");
        } catch (RuntimeException ex) {
            log.error("Unexpected hard delete failure for contract {}", id, ex);
            throw new BusinessRuleException(
                    "تعذر الحذف النهائي للعقد. يرجى المحاولة لاحقاً أو مراجعة الربط مع البيانات التابعة.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEDULED TASKS SUPPORT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Mark expired contracts (for scheduled job)
     */
    @Transactional
    public int markExpiredContracts() {
        log.info("Marking expired contracts");

        List<ProviderContract> expiredContracts = contractRepository.findExpiredButStillActive(LocalDate.now());
        int count = 0;

        for (ProviderContract contract : expiredContracts) {
            contract.setStatus(ContractStatus.EXPIRED);
            contract.setUpdatedBy("SYSTEM");
            contractRepository.save(contract);
            count++;
            log.info("Marked contract as expired: {}", contract.getContractCode());
        }

        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate unique contract code
     */
    private String generateContractCode() {
        String year = String.valueOf(Year.now().getValue());
        long count = contractRepository.countByActiveTrue() + 1;
        String code;
        do {
            code = String.format("CON-%s-%04d", year, count++);
        } while (contractRepository.existsByContractCode(code));
        return code;
    }

    /**
     * Check for overlapping contracts
     */
    private void checkForOverlappingContracts(Long providerId, Long excludeId, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) {
            endDate = LocalDate.of(9999, 12, 31); // Far future date for open-ended contracts
        }

        if (contractRepository.hasOverlappingContract(providerId, excludeId, startDate, endDate)) {
            throw new BusinessRuleException("Provider has overlapping contract dates");
        }
    }

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
