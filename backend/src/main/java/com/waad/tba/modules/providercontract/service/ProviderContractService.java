package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.*;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingScope;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import com.waad.tba.modules.providercontract.repository.ServiceSpecialtyInsuranceMapRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
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
    private final EmployerRepository employerRepository;
    private final AuditLogService auditLogService;
    private final ProviderContractTermsService termsService;
    private final ProviderContractTermRepository termRepository;
    private final ServiceSpecialtyInsuranceMapRepository specialtyMapRepository;
    private final ClaimRepository claimRepository;

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
        return search(query, status, null, null, null, pageable);
    }

    /**
     * Search contracts with optional status, pricing scope and rejection-timing filters.
     * All filtering happens server-side via {@link ProviderContractRepository#searchWithFilters}.
     */
    @Transactional(readOnly = true)
    public Page<ProviderContractResponseDto> search(String query, ContractStatus status, PricingScope pricingScope,
            Boolean discountBeforeRejection, BigDecimal discountPercent, Pageable pageable) {
        log.debug("Searching contracts: query={}, status={}, pricingScope={}, discountBeforeRejection={}",
                query, status, pricingScope, discountBeforeRejection);

        if ((query == null || query.trim().isEmpty()) && status == null && pricingScope == null
                && discountBeforeRejection == null && discountPercent == null) {
            return findAll(pageable);
        }

        return contractRepository.searchWithFilters(query, status, pricingScope, discountBeforeRejection, discountPercent, pageable)
                .map(ProviderContractResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<BigDecimal> findDistinctDiscountPercentages() {
        return contractRepository.findDistinctActiveDiscountPercentages();
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

        ContractStatus targetStatus = dto.getStatus() != null ? dto.getStatus() : ContractStatus.DRAFT;
        PricingScope pricingScope = dto.getPricingScope() != null ? dto.getPricingScope() : PricingScope.GLOBAL;
        Employer employer = resolveEmployerForScope(pricingScope, dto.getEmployerId());
        
        // Enforce single active contract if creating as ACTIVE
        if (targetStatus == ContractStatus.ACTIVE) {
            findActiveContractForScope(dto.getProviderId(), pricingScope, dto.getEmployerId())
                    .ifPresent(existing -> {
                        throw new BusinessRuleException(
                                "يوجد عقد نشط مسبقاً لنفس مقدم الخدمة ونطاق التسعير: " + existing.getContractCode());
                    });
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
                .pricingScope(pricingScope)
                .employer(employer)
                .status(targetStatus)
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
        termsService.createInitial(contract, getCurrentUsername());
        log.info("Created provider contract: {}", contract.getContractCode());
        logAudit("CREATE", contract.getId(), "Created contract " + contract.getContractCode()
                + " for providerId=" + contract.getProvider().getId() + ", status=" + contract.getStatus());

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

        BigDecimal currentDiscount = contract.getDiscountPercent() == null
                ? BigDecimal.ZERO : contract.getDiscountPercent();
        BigDecimal requestedDiscount = dto.getDiscountPercent() == null
                ? currentDiscount : dto.getDiscountPercent();
        boolean requestedTiming = dto.getDiscountBeforeRejection() == null
                ? Boolean.TRUE.equals(contract.getDiscountBeforeRejection())
                : Boolean.TRUE.equals(dto.getDiscountBeforeRejection());
        boolean financialTermsChanged = currentDiscount.compareTo(requestedDiscount) != 0
                || Boolean.TRUE.equals(contract.getDiscountBeforeRejection()) != requestedTiming;

        // Cannot update terminated contracts
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BusinessRuleException("Cannot update a terminated contract");
        }

        PricingScope targetScope = dto.getPricingScope() != null
                ? dto.getPricingScope()
                : (contract.getPricingScope() != null ? contract.getPricingScope() : PricingScope.GLOBAL);
        Long targetEmployerId = dto.getEmployerId() != null
                ? dto.getEmployerId()
                : (contract.getEmployer() != null ? contract.getEmployer().getId() : null);
        PricingScope currentScope = contract.getPricingScope() != null
                ? contract.getPricingScope() : PricingScope.GLOBAL;
        Long currentEmployerId = contract.getEmployer() != null ? contract.getEmployer().getId() : null;
        boolean scopeChanged = targetScope != currentScope
                || !java.util.Objects.equals(targetEmployerId, currentEmployerId);
        Employer targetEmployer = scopeChanged
                ? resolveEmployerForScope(targetScope, targetEmployerId)
                : contract.getEmployer();

        if (scopeChanged && contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessRuleException("لا يمكن تغيير نطاق التسعير أو جهة العمل إلا في حالة المسودة.");
        }

        // Validate dates if changed
        LocalDate previousStartDate = contract.getStartDate();
        LocalDate previousEndDate = contract.getEndDate();
        LocalDate startDate = dto.getStartDate() != null ? dto.getStartDate() : contract.getStartDate();
        LocalDate endDate = dto.getEndDate() != null ? dto.getEndDate() : contract.getEndDate();

        if (endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessRuleException("Start date must be before end date");
        }

        // Check for overlapping contracts if dates changed
        if ((dto.getStartDate() != null || dto.getEndDate() != null || scopeChanged)
                && contract.getStatus() != ContractStatus.DRAFT) {
            checkForOverlappingContracts(contract.getProvider().getId(), contract.getId(),
                    targetScope, targetEmployerId, startDate, endDate);
        }

        // Apply updates
        if (scopeChanged) {
            contract.setPricingScope(targetScope);
            contract.setEmployer(targetEmployer);
        }
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
        alignActivePricingPeriodIfContractPeriodMoved(
                contract, previousStartDate, previousEndDate, startDate, endDate);
        if (financialTermsChanged) {
            if (contract.getStatus() != ContractStatus.DRAFT
                    && (dto.getTermsChangeReason() == null || dto.getTermsChangeReason().isBlank())) {
                throw new BusinessRuleException("سبب تعديل شروط الخصم مطلوب للتدقيق المالي");
            }
            LocalDate termsFrom = contract.getStatus() == ContractStatus.DRAFT
                    ? contract.getStartDate()
                    : (dto.getTermsEffectiveFrom() != null ? dto.getTermsEffectiveFrom() : LocalDate.now());
            if (contract.getStatus() != ContractStatus.DRAFT && termsFrom.isBefore(LocalDate.now())) {
                throw new BusinessRuleException("لا يمكن تطبيق تعديل شروط العقد بأثر رجعي؛ اختر تاريخ اليوم أو تاريخاً لاحقاً");
            }
            termsService.amend(contract, termsFrom, contract.getDiscountPercent(),
                    contract.getDiscountBeforeRejection(), dto.getTermsChangeReason(), getCurrentUsername());
        }

        log.info("Updated provider contract: {}", contract.getContractCode());
        logAudit("UPDATE", contract.getId(), "Updated contract " + contract.getContractCode());
        return ProviderContractResponseDto.fromEntity(contract);
    }

    private void alignActivePricingPeriodIfContractPeriodMoved(ProviderContract contract,
            LocalDate previousStartDate, LocalDate previousEndDate,
            LocalDate newStartDate, LocalDate newEndDate) {
        if (contract == null || contract.getId() == null || previousStartDate == null || newStartDate == null) {
            return;
        }
        LocalDate oldPriceEndExclusive = toPriceEndExclusive(previousEndDate);
        LocalDate newPriceEndExclusive = toPriceEndExclusive(newEndDate);
        if (previousStartDate.equals(newStartDate)
                && java.util.Objects.equals(oldPriceEndExclusive, newPriceEndExclusive)) {
            return;
        }

        long alignedActiveItems = pricingItemRepository.countActiveAlignedToPeriod(
                contract.getId(), previousStartDate, oldPriceEndExclusive);
        if (alignedActiveItems == 0) {
            return;
        }

        int updated = pricingItemRepository.alignActivePricePeriod(
                contract.getId(), previousStartDate, oldPriceEndExclusive, newStartDate, newPriceEndExclusive);
        log.info("Aligned {} active pricing items for contract {} from {}..{} to {}..{}",
                updated, contract.getContractCode(), previousStartDate, oldPriceEndExclusive,
                newStartDate, newPriceEndExclusive);
    }

    private LocalDate toPriceEndExclusive(LocalDate contractEndDate) {
        return contractEndDate == null ? null : contractEndDate.plusDays(1);
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
        PricingScope pricingScope = contractToActivate.getPricingScope() != null
                ? contractToActivate.getPricingScope()
                : PricingScope.GLOBAL;
        Long employerId = contractToActivate.getEmployer() != null ? contractToActivate.getEmployer().getId() : null;
        resolveEmployerForScope(pricingScope, employerId);

        // Check for existing active contract for same provider and pricing scope
        findActiveContractForScope(providerId, pricingScope, employerId)
                .filter(existing -> !existing.getId().equals(contractId))
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "يوجد عقد نشط مسبقاً لنفس مقدم الخدمة ونطاق التسعير: " + existing.getContractCode());
                });

        // Check for overlapping contracts
        checkForOverlappingContracts(providerId, contractId, pricingScope, employerId,
                contractToActivate.getStartDate(), contractToActivate.getEndDate());

        ContractStatus previousStatus = contractToActivate.getStatus();
        contractToActivate.setStatus(ContractStatus.ACTIVE);
        contractToActivate.setUpdatedBy(getCurrentUsername());
        ProviderContract savedContract = contractRepository.save(contractToActivate);

        log.info("Activated provider contract: {}", savedContract.getContractCode());
        logAudit("ACTIVATE", savedContract.getId(),
                "Contract " + savedContract.getContractCode() + " status " + previousStatus + " -> ACTIVE");
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

        ContractStatus previousStatus = contract.getStatus();
        contract.setStatus(ContractStatus.SUSPENDED);
        if (reason != null && !reason.isBlank()) {
            String notes = contract.getNotes() != null ? contract.getNotes() + "\n" : "";
            notes += "[" + LocalDate.now() + "] Suspended: " + reason;
            contract.setNotes(notes);
        }
        contract.setUpdatedBy(getCurrentUsername());
        contract = contractRepository.save(contract);

        log.info("Suspended provider contract: {}", contract.getContractCode());
        logAudit("SUSPEND", contract.getId(), "Contract " + contract.getContractCode() + " status " + previousStatus
                + " -> SUSPENDED. Reason: " + (reason != null ? reason : "-"));
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

        ContractStatus previousStatus = contract.getStatus();
        contract.setStatus(ContractStatus.TERMINATED);
        if (reason != null && !reason.isBlank()) {
            String notes = contract.getNotes() != null ? contract.getNotes() + "\n" : "";
            notes += "[" + LocalDate.now() + "] Terminated: " + reason;
            contract.setNotes(notes);
        }
        contract.setUpdatedBy(getCurrentUsername());
        contract = contractRepository.save(contract);

        log.info("Terminated provider contract: {}", contract.getContractCode());
        logAudit("TERMINATE", contract.getId(), "Contract " + contract.getContractCode() + " status "
                + previousStatus + " -> TERMINATED. Reason: " + (reason != null ? reason : "-"));
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
        logAudit("DELETE", contract.getId(), "Soft-deleted contract " + contract.getContractCode());
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
        logAudit("RESTORE", contract.getId(), "Restored contract " + contract.getContractCode() + " from trash");
        return ProviderContractResponseDto.fromEntity(contract);
    }

    /**
     * Permanently delete a soft-deleted contract together with the records that
     * belong to it and nothing else: its pricing items, its dated terms, and its
     * specialty-mapping rows. None of the three means anything once the contract
     * is gone.
     *
     * <p>This used to remove the pricing items alone, which made permanent
     * deletion impossible for <em>every</em> contract rather than merely
     * difficult. A row exists in {@code provider_contract_terms} for every
     * contract that has ever existed -- {@code createInitial} writes one at
     * creation, and V136 backfilled one for each pre-existing contract -- behind
     * an {@code ON DELETE RESTRICT} foreign key. The user was then told "related
     * data exists" about a row the system had written for itself, on a contract
     * whose services and claims both read zero.
     *
     * <p>What legitimately blocks deletion stays blocking: a claim or a
     * pre-authorization decision snapshot pointing at the contract holds a
     * financial history that must outlive any cleanup, and the database refuses
     * those on its own. Those refusals are now named rather than reported as an
     * unspecified integrity failure, and the underlying violation is logged so a
     * new reference can be identified instead of guessed at.
     */
    @Transactional
    public void hardDelete(Long id) {
        log.info("Hard deleting provider contract: {}", id);

        ProviderContract contract = contractRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Provider contract not found: " + id));

        if (Boolean.TRUE.equals(contract.getActive())) {
            throw new BusinessRuleException("لا يمكن الحذف النهائي قبل النقل إلى سجل المحذوفات أولاً.");
        }

        // Checked before touching anything, so the refusal names the real reason
        // instead of surfacing whichever constraint the database happened to hit
        // first once the owned rows were already deleted.
        long claimsOnContract = claimRepository.countByProviderContractId(id);
        if (claimsOnContract > 0) {
            throw new BusinessRuleException(
                    "تعذر الحذف النهائي: العقد مرتبط بـ " + claimsOnContract
                            + " مطالبة. السجل المالي للمطالبات لا يجوز فقدان عقده.");
        }

        try {
            String contractCode = contract.getContractCode();
            pricingItemRepository.hardDeleteByContractId(id);
            specialtyMapRepository.hardDeleteByContractId(id);
            termRepository.hardDeleteByContractId(id);
            contractRepository.delete(contract);
            contractRepository.flush();
            log.info("Hard deleted provider contract: {}", contractCode);
            logAudit("HARD_DELETE", id, "Permanently deleted contract " + contractCode);
        } catch (DataIntegrityViolationException ex) {
            // Logged with the violation attached: without it the refusal named no
            // table and no constraint, and nothing in the logs could tell an
            // operator which reference had blocked the delete.
            log.error("Hard delete of contract {} blocked by a remaining reference", id, ex);
            throw new BusinessRuleException(
                    "تعذر الحذف النهائي للعقد لارتباطه بسجلات أخرى في النظام.");
        } catch (RuntimeException ex) {
            log.error("Unexpected hard delete failure for contract {}", id, ex);
            throw new BusinessRuleException(
                    "تعذر الحذف النهائي للعقد. يرجى المحاولة لاحقاً أو مراجعة الربط مع البيانات التابعة.");
        }
    }

    /**
     * Bulk soft-delete: each contract is processed independently so one failure
     * (e.g. contract still ACTIVE) never blocks the rest of the batch.
     */
    @Transactional
    public BulkProviderContractResultDto bulkDelete(List<Long> contractIds) {
        List<BulkProviderContractResultDto.ContractResult> results = new ArrayList<>();
        int successCount = 0;

        for (Long id : contractIds) {
            try {
                delete(id);
                ProviderContract contract = contractRepository.findById(id).orElse(null);
                results.add(BulkProviderContractResultDto.ContractResult.builder()
                        .contractId(id)
                        .contractCode(contract != null ? contract.getContractCode() : null)
                        .success(true)
                        .message("تم الحذف بنجاح")
                        .build());
                successCount++;
            } catch (BusinessRuleException ex) {
                results.add(BulkProviderContractResultDto.ContractResult.builder()
                        .contractId(id)
                        .success(false)
                        .message(ex.getMessage())
                        .build());
            } catch (RuntimeException ex) {
                log.error("Unexpected bulk-delete failure for contract {}", id, ex);
                results.add(BulkProviderContractResultDto.ContractResult.builder()
                        .contractId(id)
                        .success(false)
                        .message("تعذر حذف العقد. يرجى المحاولة لاحقاً.")
                        .build());
            }
        }

        return BulkProviderContractResultDto.builder()
                .totalCount(contractIds.size())
                .successCount(successCount)
                .failedCount(contractIds.size() - successCount)
                .results(results)
                .build();
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
    // BULK UPDATE OPERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Bulk update contracts. Each contract is evaluated and saved independently — a
     * failure on one contract (invalid transition, overlapping active contract, bad
     * dates, ...) must never discard the other, valid, contracts in the batch. Callers
     * get a full per-contract breakdown via {@link BulkProviderContractResultDto}.
     */
    @Transactional
    public com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto bulkUpdateContracts(
            com.waad.tba.modules.providercontract.dto.BulkProviderContractUpdateDto dto) {
        log.info("Bulk updating provider contracts: {} contracts", dto.getContractIds().size());

        if (dto.getContractIds() == null || dto.getContractIds().isEmpty()) {
            throw new BusinessRuleException("يجب تحديد العقود المراد تحديثها");
        }

        List<ProviderContract> contracts = contractRepository.findAllById(dto.getContractIds());
        java.util.Map<Long, ProviderContract> byId = contracts.stream()
                .collect(Collectors.toMap(ProviderContract::getId, c -> c));

        List<com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult> results =
                new java.util.ArrayList<>();

        for (Long contractId : dto.getContractIds()) {
            ProviderContract contract = byId.get(contractId);
            if (contract == null) {
                results.add(com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult
                        .builder().contractId(contractId).success(false).message("العقد غير موجود").build());
                continue;
            }

            try {
                results.add(applyBulkUpdateToOneContract(contract, dto));
            } catch (BusinessRuleException ex) {
                results.add(com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult
                        .builder().contractId(contract.getId()).contractCode(contract.getContractCode())
                        .success(false).message(ex.getMessage()).build());
            } catch (Exception ex) {
                log.error("Unexpected error during bulk update of contract {}", contractId, ex);
                results.add(com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult
                        .builder().contractId(contract.getId()).contractCode(contract.getContractCode())
                        .success(false).message("حدث خطأ غير متوقع أثناء تحديث هذا العقد").build());
            }
        }

        int successCount = (int) results.stream().filter(r -> r.isSuccess()).count();
        return com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.builder()
                .totalCount(dto.getContractIds().size())
                .successCount(successCount)
                .failedCount(results.size() - successCount)
                .results(results)
                .build();
    }

    /**
     * Applies the requested field changes to a single contract as part of a bulk
     * operation and persists it immediately, so a later failure in the same batch
     * cannot roll this contract's change back.
     */
    private com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult applyBulkUpdateToOneContract(
            ProviderContract contract, com.waad.tba.modules.providercontract.dto.BulkProviderContractUpdateDto dto) {

        if (contract.getStatus() == ContractStatus.TERMINATED) {
            return com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult.builder()
                    .contractId(contract.getId()).contractCode(contract.getContractCode()).success(false)
                    .message("لا يمكن تحديث عقد ملغى").build();
        }

        boolean modified = false;
        ContractStatus previousStatus = contract.getStatus();

        if (dto.isUpdateStatus() && dto.getStatus() != null && contract.getStatus() != dto.getStatus()) {
            if (dto.getStatus() == ContractStatus.ACTIVE) {
                PricingScope pricingScope = contract.getPricingScope() != null ? contract.getPricingScope()
                        : PricingScope.GLOBAL;
                Long employerId = contract.getEmployer() != null ? contract.getEmployer().getId() : null;
                resolveEmployerForScope(pricingScope, employerId);

                final Long currentContractId = contract.getId();
                findActiveContractForScope(contract.getProvider().getId(), pricingScope, employerId)
                        .filter(existing -> !existing.getId().equals(currentContractId))
                        .ifPresent(existing -> {
                            throw new BusinessRuleException(
                                    "يوجد عقد نشط مسبقاً لنفس مقدم الخدمة ونطاق التسعير: " + existing.getContractCode());
                        });
            }
            contract.setStatus(dto.getStatus());
            if ((dto.getStatus() == ContractStatus.SUSPENDED || dto.getStatus() == ContractStatus.TERMINATED)
                    && dto.getReason() != null && !dto.getReason().isBlank()) {
                String notes = contract.getNotes() != null ? contract.getNotes() + "\n" : "";
                notes += "[" + LocalDate.now() + "] " + dto.getStatus() + " (bulk): " + dto.getReason();
                contract.setNotes(notes);
            }
            modified = true;
        }

        if (dto.isUpdatePricingModel() && dto.getPricingModel() != null) {
            contract.setPricingModel(dto.getPricingModel());
            modified = true;
        }

        if (dto.isUpdateDiscountPercent()) {
            contract.setDiscountPercent(dto.getDiscountPercent() != null ? dto.getDiscountPercent() : BigDecimal.ZERO);
            modified = true;
        }

        if (dto.isUpdateDiscountTiming() && dto.getDiscountBeforeRejection() != null) {
            contract.setDiscountBeforeRejection(dto.getDiscountBeforeRejection());
            modified = true;
        }

        if (dto.isUpdateStartDate() && dto.getStartDate() != null) {
            contract.setStartDate(dto.getStartDate());
            modified = true;
        }

        if (dto.isUpdateEndDate()) {
            contract.setEndDate(dto.getEndDate());
            modified = true;
        }

        if (contract.getEndDate() != null && contract.getStartDate().isAfter(contract.getEndDate())) {
            throw new BusinessRuleException(
                    "تاريخ البدء يجب أن يكون قبل تاريخ الانتهاء للعقد: " + contract.getContractCode());
        }

        if (modified) {
            contract.setUpdatedBy(getCurrentUsername());
            contract = contractRepository.save(contract);
            logAudit("BULK_UPDATE", contract.getId(), "Bulk-updated contract " + contract.getContractCode()
                    + (previousStatus != contract.getStatus() ? " (status " + previousStatus + " -> " + contract.getStatus() + ")" : ""));
        }

        return com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto.ContractResult.builder()
                .contractId(contract.getId()).contractCode(contract.getContractCode()).success(true)
                .message(modified ? "تم التحديث بنجاح" : "لا يوجد تغيير")
                .build();
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
    private void checkForOverlappingContracts(Long providerId, Long excludeId, PricingScope pricingScope,
            Long employerId, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) {
            endDate = LocalDate.of(9999, 12, 31); // Far future date for open-ended contracts
        }

        if (contractRepository.hasOverlappingContract(providerId, pricingScope, employerId, excludeId, startDate, endDate)) {
            throw new BusinessRuleException("Provider has overlapping contract dates for the same pricing scope");
        }
    }

    private Employer resolveEmployerForScope(PricingScope pricingScope, Long employerId) {
        PricingScope effectiveScope = pricingScope != null ? pricingScope : PricingScope.GLOBAL;
        if (effectiveScope == PricingScope.GLOBAL) {
            if (employerId != null) {
                throw new BusinessRuleException("العقد العام لا يجب أن يحتوي على جهة عمل.");
            }
            return null;
        }

        if (employerId == null) {
            throw new BusinessRuleException("يجب تحديد جهة العمل عند اختيار عقد خاص بجهة عمل.");
        }

        return employerRepository.findById(employerId)
                .orElseThrow(() -> new BusinessRuleException("Employer not found: " + employerId));
    }

    private java.util.Optional<ProviderContract> findActiveContractForScope(Long providerId,
            PricingScope pricingScope, Long employerId) {
        PricingScope effectiveScope = pricingScope != null ? pricingScope : PricingScope.GLOBAL;
        if (effectiveScope == PricingScope.EMPLOYER_SPECIFIC) {
            return contractRepository.findActiveContractByProviderAndEmployer(providerId, employerId);
        }
        return contractRepository.findActiveContractByProvider(providerId);
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

    /**
     * Best-effort audit trail entry for a provider-contract mutation. Never allowed to
     * fail the surrounding business transaction — logging problems must not block the
     * actual contract operation.
     */
    private void logAudit(String action, Long contractId, String details) {
        try {
            String username = getCurrentUsername();
            HttpServletRequest request = currentHttpRequest();
            String ip = request != null ? request.getRemoteAddr() : null;
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            auditLogService.createAuditLog(action, "ProviderContract", contractId, details, null, username, ip,
                    userAgent);
        } catch (Exception e) {
            log.warn("Failed to write audit log for provider contract {} action {}: {}", contractId, action,
                    e.getMessage());
        }
    }

    private HttpServletRequest currentHttpRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
