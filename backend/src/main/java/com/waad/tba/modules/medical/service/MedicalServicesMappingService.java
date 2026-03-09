package com.waad.tba.modules.medical.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medical.dto.CreateAndMapRequest;
import com.waad.tba.modules.medical.dto.LinkAndMapRequest;
import com.waad.tba.modules.medical.dto.MappingStatsDto;
import com.waad.tba.modules.medical.dto.RawServiceDto;
import com.waad.tba.modules.medical.entity.ProviderRawService;
import com.waad.tba.modules.medical.entity.ProviderServiceMapping;
import com.waad.tba.modules.medical.enums.MappingStatus;
import com.waad.tba.modules.medical.repository.ProviderRawServiceRepository;
import com.waad.tba.modules.medical.repository.ProviderServiceMappingRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalServiceCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.ServiceAlias;
import com.waad.tba.modules.medicaltaxonomy.enums.MedicalServiceStatus;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.ServiceAliasRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic for the Medical Services Mapping page.
 *
 * <p>
 * Three core operations:
 * <ol>
 * <li>{@link #createAndMap} — create new MedicalService (ACTIVE) and map raw
 * services</li>
 * <li>{@link #linkAndMap} — link existing MedicalService to raw services</li>
 * <li>{@link #getStats} — aggregate statistics for the dashboard</li>
 * </ol>
 *
 * <p>
 * Uses {@link ProviderMappingService} conventions (MappingStatus,
 * provider_service_mappings).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalServicesMappingService {

    private final MedicalServiceRepository medicalServiceRepo;
    private final MedicalCategoryRepository categoryRepo;
    private final MedicalServiceCategoryRepository mscRepo;
    private final ProviderRawServiceRepository rawServiceRepo;
    private final ProviderServiceMappingRepository mappingRepo;
    private final ServiceAliasRepository aliasRepo;
    private final AuthorizationService authorizationService;

    // ══════════════════════════════════════════════════════════════════════════
    // CREATE + MAP
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new ACTIVE {@link MedicalService} and maps the given raw services
     * to it.
     *
     * @param req the request body
     * @return list of updated {@link RawServiceDto}s
     */
    @Transactional
    public List<RawServiceDto> createAndMap(CreateAndMapRequest req) {
        User actor = authorizationService.getCurrentUser();

        // Validate code uniqueness
        String code = req.getCode().trim().toUpperCase();
        if (medicalServiceRepo.existsByCodeIgnoreCase(code)) {
            throw new BusinessRuleException("كود الخدمة موجود مسبقاً: " + code);
        }

        // Validate category
        categoryRepo.findActiveById(req.getCategoryId())
                .orElseThrow(() -> new BusinessRuleException("التصنيف غير موجود أو غير نشط: " + req.getCategoryId()));

        // Create medical service
        MedicalService service = MedicalService.builder()
                .code(code)
                .name(req.getName().trim())
                .categoryId(req.getCategoryId())
                .status(MedicalServiceStatus.ACTIVE)
                .active(true)
                .isMaster(true)
                .requiresPA(false)
                .build();
        service = medicalServiceRepo.save(service);
        log.info("CREATE_SERVICE: code={} by user={}", service.getCode(), actor.getId());

        // Persist category cross-reference in medical_service_categories
        MedicalServiceCategory msc = MedicalServiceCategory.builder()
                .serviceId(service.getId())
                .categoryId(req.getCategoryId())
                .build();
        mscRepo.save(msc);

        // Map raw services
        return mapRawServices(req.getRawServiceIds(), service, actor);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LINK + MAP
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Links a list of raw services to an existing {@link MedicalService}.
     *
     * @param req the request body
     * @return list of updated {@link RawServiceDto}s
     */
    @Transactional
    public List<RawServiceDto> linkAndMap(LinkAndMapRequest req) {
        User actor = authorizationService.getCurrentUser();

        MedicalService service = medicalServiceRepo.findById(req.getMedicalServiceId())
                .orElseThrow(() -> new BusinessRuleException("الخدمة الطبية غير موجودة: " + req.getMedicalServiceId()));

        if (!service.isActive()) {
            throw new BusinessRuleException("لا يمكن الربط بخدمة طبية غير نشطة");
        }

        log.info("LINK_MAP: medicalServiceId={} rawIds={} by user={}",
                service.getId(), req.getRawServiceIds(), actor.getId());

        return mapRawServices(req.getRawServiceIds(), service, actor);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATS
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public MappingStatsDto getStats() {
        long total = rawServiceRepo.count();
        long pending = rawServiceRepo.countByStatus(MappingStatus.PENDING);
        long mapped = rawServiceRepo.countByStatus(MappingStatus.MANUAL_CONFIRMED)
                + rawServiceRepo.countByStatus(MappingStatus.AUTO_MATCHED);
        long rejected = rawServiceRepo.countByStatus(MappingStatus.REJECTED);
        long providers = rawServiceRepo.countDistinctProviders();
        long servicesTotal = medicalServiceRepo.count();

        return MappingStatsDto.builder()
                .total(total)
                .pending(pending)
                .mapped(mapped)
                .rejected(rejected)
                .providersWithRawServices(providers)
                .medicalServicesTotal(servicesTotal)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private List<RawServiceDto> mapRawServices(List<Long> rawIds, MedicalService service, User actor) {
        // Batch-load all raw services in one query to avoid N+1
        Map<Long, ProviderRawService> rawMap = rawServiceRepo.findAllById(rawIds)
                .stream()
                .collect(Collectors.toMap(ProviderRawService::getId, Function.identity()));

        // Verify all requested IDs exist
        rawIds.forEach(id -> {
            if (!rawMap.containsKey(id)) {
                throw new BusinessRuleException("خدمة خام غير موجودة: " + id);
            }
        });

        return rawIds.stream().map(rawId -> {
            ProviderRawService raw = rawMap.get(rawId);

            if (raw.getStatus() == MappingStatus.REJECTED) {
                throw new BusinessRuleException("لا يمكن تعيين خدمة مرفوضة: " + rawId);
            }

            // Upsert mapping
            ProviderServiceMapping mapping = mappingRepo
                    .findByProviderRawServiceId(raw.getId())
                    .orElseGet(() -> ProviderServiceMapping.builder()
                            .providerRawService(raw)
                            .build());

            mapping.setMedicalService(service);
            mapping.setMappingStatus(MappingStatus.MANUAL_CONFIRMED);
            mapping.setMappedBy(actor);
            mapping.setMappedAt(LocalDateTime.now());
            mapping.setConfidenceScore(BigDecimal.valueOf(100));
            mappingRepo.save(mapping);

            // Register alias
            saveAliasIfAbsent(service.getId(), raw.getRawName(), "user:" + actor.getId());

            // Update raw service status
            raw.setStatus(MappingStatus.MANUAL_CONFIRMED);
            raw.setConfidenceScore(BigDecimal.valueOf(100));
            rawServiceRepo.save(raw);

            return toDto(raw, service);
        }).collect(Collectors.toList());
    }

    private void saveAliasIfAbsent(Long serviceId, String rawName, String createdBy) {
        if (rawName == null || rawName.isBlank())
            return;
        String trimmed = rawName.trim();
        if (!aliasRepo.existsByMedicalServiceIdAndAliasTextIgnoreCase(serviceId, trimmed)) {
            aliasRepo.save(ServiceAlias.builder()
                    .medicalServiceId(serviceId)
                    .aliasText(trimmed)
                    .locale("ar")
                    .createdBy(createdBy)
                    .build());
        }
    }

    private RawServiceDto toDto(ProviderRawService raw, MedicalService service) {
        return RawServiceDto.builder()
                .id(raw.getId())
                .providerId(raw.getProviderId())
                .rawName(raw.getRawName())
                .normalizedName(raw.getNormalizedName())
                .code(raw.getCode())
                .encounterType(raw.getEncounterType())
                .source(raw.getSource())
                .status(raw.getStatus())
                .confidenceScore(raw.getConfidenceScore())
                .createdAt(raw.getCreatedAt())
                .mapping(RawServiceDto.MappingDto.builder()
                        .medicalServiceId(service.getId())
                        .medicalServiceCode(service.getCode())
                        .medicalServiceName(service.getName())
                        .mappingStatus(MappingStatus.MANUAL_CONFIRMED)
                        .confidenceScore(BigDecimal.valueOf(100))
                        .mappedAt(LocalDateTime.now())
                        .build())
                .build();
    }
}
