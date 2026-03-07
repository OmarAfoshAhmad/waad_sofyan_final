package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceCreateDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceResponseDto;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceUpdateDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for managing Medical Services (Reference Data).
 * 
 * Business Rules:
 * 1. Code must be unique and immutable
 * 2. Category must exist and be active
 * 3. Base price must be >= 0 (if provided)
 * 4. No coverage, policy, provider, or network logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MedicalTaxonomyServiceService {

    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalServiceResponseDto create(MedicalServiceCreateDto dto) {
        log.info("Creating medical service: {}", dto.getCode());

        // Validate code uniqueness
        if (serviceRepository.existsByCode(dto.getCode())) {
            throw new BusinessRuleException("Service code already exists: " + dto.getCode());
        }

        // Validate category exists and is active
        MedicalCategory category = categoryRepository.findActiveById(dto.getCategoryId())
                .orElseThrow(() -> new BusinessRuleException("Category not found or inactive: " + dto.getCategoryId()));

        // Validate base price (if provided)
        if (dto.getBasePrice() != null && dto.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Base price must be >= 0");
        }

        // Create entity
        MedicalService service = MedicalService.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .categoryId(dto.getCategoryId())
                .basePrice(dto.getBasePrice())
                .requiresPA(dto.getRequiresPA() != null ? dto.getRequiresPA() : false)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        service = serviceRepository.save(service);
        log.info("✅ Created medical service: {} (ID: {})", service.getCode(), service.getId());

        return toDto(service, category);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public MedicalServiceResponseDto findById(Long id) {
        log.debug("Finding medical service by ID: {}", id);
        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));
        return toDto(service);
    }

    @Transactional(readOnly = true)
    public MedicalServiceResponseDto findByCode(String code) {
        log.debug("Finding medical service by code: {}", code);
        MedicalService service = serviceRepository.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + code));
        return toDto(service);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findAll(Pageable pageable) {
        log.debug("Finding all medical services, page: {}", pageable.getPageNumber());
        return serviceRepository.findByActiveTrue(pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findByCategory(Long categoryId, Pageable pageable) {
        log.debug("Finding services by category: {}", categoryId);
        return serviceRepository.findActiveByCategoryId(categoryId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> findServicesRequiringPA(Pageable pageable) {
        log.debug("Finding services requiring PA");
        return serviceRepository.findServicesRequiringPA(pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MedicalServiceResponseDto> search(
            String searchTerm,
            Long categoryId,
            Boolean requiresPA,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        log.debug("Searching services: term={}, category={}, requiresPA={}, price=[{}, {}]",
                searchTerm, categoryId, requiresPA, minPrice, maxPrice);

        return serviceRepository.advancedSearch(searchTerm, categoryId, requiresPA, minPrice, maxPrice, pageable)
                .map(this::toDto);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalServiceResponseDto update(Long id, MedicalServiceUpdateDto dto) {
        log.info("Updating medical service: {}", id);

        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));

        // Update fields (only if provided)
        if (dto.getName() != null) {
            service.setName(dto.getName());
        }
        if (dto.getCategoryId() != null) {
            // Validate category exists and is active
            categoryRepository.findActiveById(dto.getCategoryId())
                    .orElseThrow(
                            () -> new BusinessRuleException("Category not found or inactive: " + dto.getCategoryId()));
            service.setCategoryId(dto.getCategoryId());
        }
        if (dto.getBasePrice() != null) {
            if (dto.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Base price must be >= 0");
            }
            service.setBasePrice(dto.getBasePrice());
        }
        if (dto.getRequiresPA() != null) {
            service.setRequiresPA(dto.getRequiresPA());
        }
        if (dto.getActive() != null) {
            service.setActive(dto.getActive());
        }

        service = serviceRepository.save(service);
        log.info("✅ Updated medical service: {}", id);

        return toDto(service);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(Long id) {
        log.info("Deleting (soft) medical service: {}", id);

        MedicalService service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Medical service not found: " + id));

        // Soft delete
        service.setActive(false);
        serviceRepository.save(service);

        log.info("✅ Deleted (soft) medical service: {}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTO MAPPING
    // ═══════════════════════════════════════════════════════════════════════════

    private MedicalServiceResponseDto toDto(MedicalService service) {
        MedicalCategory category = categoryRepository.findById(service.getCategoryId())
                .orElse(null);
        return toDto(service, category);
    }

    private MedicalServiceResponseDto toDto(MedicalService service, MedicalCategory category) {
        return MedicalServiceResponseDto.builder()
                .id(service.getId())
                .code(service.getCode())
                .name(service.getName())
                .categoryId(service.getCategoryId())
                .categoryName(category != null ? category.getName() : null)
                .categoryCode(category != null ? category.getCode() : null)
                .basePrice(service.getBasePrice())
                .requiresPA(service.isRequiresPA())
                .active(service.isActive())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
