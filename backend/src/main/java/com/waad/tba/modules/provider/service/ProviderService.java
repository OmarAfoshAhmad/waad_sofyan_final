package com.waad.tba.modules.provider.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.guard.DeletionGuard;
import com.waad.tba.modules.provider.repository.ProviderContractRepository;

import com.waad.tba.modules.provider.dto.AllowedEmployerDto;
import com.waad.tba.modules.provider.dto.ProviderCreateDto;
import com.waad.tba.modules.provider.dto.ProviderSelectorDto;
import com.waad.tba.modules.provider.dto.ProviderUpdateDto;
import com.waad.tba.modules.provider.dto.ProviderViewDto;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.mapper.ProviderMapper;
import com.waad.tba.modules.provider.repository.ProviderRepository;

import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.ProviderAllowedEmployer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final EmployerRepository employerRepository;
    private final ProviderContractRepository providerContractRepository;

    /**
     * Get provider selector options with pagination
     * 
     * PHASE 3 REVIEW (Issue D): Added pagination support.
     * 
     * @param page Page number (0-indexed)
     * @param size Items per page
     * @return Page of provider selector DTOs
     */
    public Page<ProviderSelectorDto> getSelectorOptions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return providerRepository.findAllActivePaged(pageable)
                .map(providerMapper::toSelectorDto);
    }

    public List<ProviderViewDto> search(String query) {
        return providerRepository.search(query).stream()
                .map(providerMapper::toViewDto)
                .collect(Collectors.toList());
    }

    public ProviderViewDto createProvider(ProviderCreateDto dto) {
        if (providerRepository.existsByLicenseNumber(dto.getLicenseNumber())) {
            throw new RuntimeException("Provider with license number already exists: " + dto.getLicenseNumber());
        }

        Provider provider = providerMapper.toEntity(dto);
        provider = providerRepository.save(provider);
        return providerMapper.toViewDto(provider);
    }

    /**
     * Update provider details
     * 
     * PHASE 3 REVIEW (Issue F): Object-level validation enforced at controller layer.
     * ProviderController uses AuthorizationService to ensure users can only
     * access providers they are authorized for (based on providerId in JWT).
     * 
     * @param id Provider ID
     * @param dto Update data
     * @return Updated provider view
     * @throws RuntimeException if provider not found
     */
    public ProviderViewDto updateProvider(Long id, ProviderUpdateDto dto) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + id));

        providerMapper.updateEntityFromDto(provider, dto);
        provider = providerRepository.save(provider);
        return providerMapper.toViewDto(provider);
    }

    @Transactional(readOnly = true)
    public ProviderViewDto getProvider(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + id));
        return providerMapper.toViewDto(provider);
    }

    @Transactional(readOnly = true)
    public Page<ProviderViewDto> listProviders(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Provider> providers;
        
        if (search != null && !search.isEmpty()) {
            // Search ALL providers (active AND inactive)
            providers = providerRepository.searchPagedAll(search, pageable);
        } else {
            // Return ALL providers (active AND inactive)  
            providers = providerRepository.findAll(pageable);
        }
        
        return providers.map(providerMapper::toViewDto);
    }

    /**
     * Deactivate provider (soft delete only)
     * 
     * PHASE 3 REVIEW: Hard delete prohibited due to FK RESTRICT constraints.
     * This method only sets active=false to preserve referential integrity.
     * 
     * @param id Provider ID
     * @throws RuntimeException if provider not found
     */
    public void deactivateProvider(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("مقدم الخدمة غير موجود: " + id));

        DeletionGuard.of("مقدم الخدمة")
                .check("عقود نشطة", providerContractRepository.countByProviderIdAndActive(id, true))
                .throwIfBlocked("أنهِ العقود النشطة المرتبطة بمقدم الخدمة أولاً.");

        provider.setActive(false);
        providerRepository.save(provider);
        log.info("Provider {} deactivated (soft delete)", id);
    }

    @Transactional(readOnly = true)
    public List<ProviderViewDto> getAllActiveProviders() {
        return providerRepository.findAllActive().stream()
                .map(providerMapper::toViewDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countProviders() {
        return providerRepository.countActive();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDER-PARTNER ISOLATION (Phase 5.5)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update allowed employers for a provider.
     * 
     * @param providerId Provider ID
     * @param employerIds List of allowed employer IDs
     */
    @Transactional
    public void updateAllowedEmployers(Long providerId, List<Long> employerIds) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + providerId));
        
        // Clear existing
        provider.getAllowedEmployers().clear();
        
        // Add new
        if (employerIds != null && !employerIds.isEmpty()) {
            List<Employer> employers = employerRepository.findAllById(employerIds);
            for (Employer emp : employers) {
                provider.getAllowedEmployers().add(ProviderAllowedEmployer.builder()
                    .provider(provider)
                    .employer(emp)
                    .active(true)
                    .build());
            }
        }
        
        providerRepository.save(provider);
    }

    /**
     * Get list of allowed employers for a provider.
     * 
     * Returns employers from BOTH:
     * 1. TPA Model: provider_allowed_employers table (explicit partnerships)
     * 2. Contract Model: provider_contracts (formal contracts - FUTURE, not currently used)
     * 
     * If provider has allowAllEmployers=true, returns a "Global Network" entry.
     * 
     * @param providerId Provider ID
     * @return List of allowed employers/partners
     */
    @Transactional(readOnly = true)
    public List<AllowedEmployerDto> getAllowedEmployers(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + providerId));
        
        Set<AllowedEmployerDto> distinctEmployers = new HashSet<>();

        // 1. Check Global Network flag
        if (Boolean.TRUE.equals(provider.getAllowAllEmployers())) {
            log.debug("Provider {} has allowAllEmployers=true, adding global network entry", providerId);
            distinctEmployers.add(AllowedEmployerDto.builder()
                .id(-1L)
                .name("الشبكة العامة")
                .nameEn("Global Network")
                .isGlobal(true)
                .isActive(true)
                .build());
        }

        // 2. Add TPA Model Employers (provider_allowed_employers table)
        if (provider.getAllowedEmployers() != null) {
            provider.getAllowedEmployers().stream()
                .filter(pae -> Boolean.TRUE.equals(pae.getActive()) && pae.getEmployer() != null)
                .forEach(pae -> {
                    distinctEmployers.add(AllowedEmployerDto.builder()
                        .id(pae.getEmployer().getId())
                        .name(pae.getEmployer().getName())
                        .nameEn(pae.getEmployer().getName())
                        .isGlobal(false)
                        .isActive(true)
                        .build());
                });
        }

        // 3. Future: Add Contract Model Employers (provider_contracts table)
        // NOTE: ProviderContract entity doesn't have employer relationship yet.
        // This section will be activated when the contract model is enhanced.
        // For now, contracts represent provider-wide pricing agreements.

        // 4. Return sorted list (Global first, then alphabetically)
        return distinctEmployers.stream()
            .sorted((a, b) -> {
                if (Boolean.TRUE.equals(a.getIsGlobal())) return -1;
                if (Boolean.TRUE.equals(b.getIsGlobal())) return 1;
                return a.getName().compareTo(b.getName());
            })
            .collect(Collectors.toList());
    }

    /**
     * Get list of allowed employer IDs for a provider (for filtering queries).
     * 
     * @param providerId Provider ID
     * @return List of employer IDs (empty list if global network)
     */
    @Transactional(readOnly = true)
    public List<Long> getAllowedEmployerIds(Long providerId) {
        return getAllowedEmployers(providerId).stream()
                .filter(e -> !Boolean.TRUE.equals(e.getIsGlobal()))
                .map(AllowedEmployerDto::getId)
                .filter(id -> id > 0)
                .collect(Collectors.toList());
    }

    /**
     * Get all providers allowed for a specific employer.
     * Used by Claims Batch System to show potential providers for their monthly claims.
     * 
     * @param employerId Employer ID
     * @return List of providers
     */
    @Transactional(readOnly = true)
    public List<ProviderViewDto> getProvidersByEmployer(Long employerId) {
        log.debug("[PROVIDER] Fetching providers for employer: {}", employerId);
        return providerRepository.findByAllowedEmployer(employerId).stream()
                .map(providerMapper::toViewDto)
                .collect(Collectors.toList());
    }
}

