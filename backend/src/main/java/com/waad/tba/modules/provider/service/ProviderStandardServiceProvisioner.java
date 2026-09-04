package com.waad.tba.modules.provider.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesRequestDto;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesSummaryDto;
import com.waad.tba.modules.provider.dto.RevokeStandardServicesSummaryDto;
import com.waad.tba.modules.provider.dto.StandardServiceCreateDto;
import com.waad.tba.modules.provider.dto.StandardServiceDto;
import com.waad.tba.modules.provider.dto.StandardServiceUpdateDto;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.ProviderService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceDefaultRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bulk-assigns standard (MANUAL_AMOUNT) services across many providers, and
 * auto-applies them to a single newly created provider. This picks which
 * providers get which service; it never decides coverage or pricing -- that
 * stays exactly where it already lives (BenefitPolicyRule / MedicalService).
 *
 * Preview and apply share one computation: preview never writes, apply
 * writes inside one transaction and reports what it actually did. Both read
 * every matching provider and every existing assignment in one query each --
 * bulk provisioning across thousands of providers must not become a
 * per-provider round trip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderStandardServiceProvisioner {

    private final ProviderRepository providerRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderServiceDefaultRepository providerServiceDefaultRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final ClaimRepository claimRepository;

    @Transactional(readOnly = true)
    public List<StandardServiceDto> listStandardServices() {
        return toDtos(medicalServiceRepository.findByPricingModeAndActiveTrue(PricingMode.MANUAL_AMOUNT));
    }

    /** Includes inactive services -- for the admin catalog-management screen, not the assignment picker. */
    @Transactional(readOnly = true)
    public List<StandardServiceDto> listAllStandardServices() {
        return toDtos(medicalServiceRepository.findByPricingMode(PricingMode.MANUAL_AMOUNT));
    }

    private List<StandardServiceDto> toDtos(List<MedicalService> services) {
        Map<Long, MedicalCategory> categoriesById = medicalCategoryRepository
                .findAllById(services.stream().map(MedicalService::getCategoryId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MedicalCategory::getId, c -> c));
        Map<String, List<Provider.ProviderType>> defaultsByServiceCode = providerServiceDefaultRepository
                .findByServiceCodeInAndActiveTrue(services.stream().map(MedicalService::getCode).toList())
                .stream().collect(Collectors.groupingBy(
                        com.waad.tba.modules.provider.entity.ProviderServiceDefault::getServiceCode,
                        Collectors.mapping(
                                com.waad.tba.modules.provider.entity.ProviderServiceDefault::getProviderType,
                                Collectors.toList())));

        return services.stream().map(s -> toDto(s, categoriesById.get(s.getCategoryId()),
                defaultsByServiceCode.getOrDefault(s.getCode(), List.of()))).toList();
    }

    private StandardServiceDto toDto(MedicalService s, MedicalCategory category,
            List<Provider.ProviderType> defaultProviderTypes) {
        return StandardServiceDto.builder()
                .id(s.getId())
                .code(s.getCode())
                .name(s.getName())
                .nameAr(s.getNameAr())
                .nameEn(s.getNameEn())
                .categoryId(s.getCategoryId())
                .categoryCode(category != null ? category.getCode() : null)
                .categoryName(category != null
                        ? (category.getNameAr() != null ? category.getNameAr() : category.getName())
                        : null)
                .active(s.isActive())
                .defaultProviderTypes(defaultProviderTypes)
                .build();
    }

    /**
     * Creates a new standard (MANUAL_AMOUNT) professional service and its
     * default facility-type suggestions in one transaction -- a service
     * with no defaults still exists validly (defaultProviderTypes may be
     * empty), but a partially-created service/defaults pair from a failure
     * mid-way would not.
     */
    @Transactional
    public StandardServiceDto createStandardService(StandardServiceCreateDto dto) {
        String code = dto.getCode().trim();
        if (medicalServiceRepository.existsByCode(code)) {
            throw new BusinessRuleException("رمز الخدمة \"" + code + "\" مستخدم مسبقاً");
        }
        MedicalCategory category = medicalCategoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new BusinessRuleException("التصنيف الطبي المحدد غير موجود"));

        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code(code)
                .name(dto.getNameAr().trim())
                .nameAr(dto.getNameAr().trim())
                .nameEn(dto.getNameEn())
                .categoryId(category.getId())
                .pricingMode(PricingMode.MANUAL_AMOUNT)
                .isMaster(true)
                .active(true)
                .build());

        List<Provider.ProviderType> defaults = reconcileDefaults(code, dto.getDefaultProviderTypes());
        return toDto(service, category, defaults);
    }

    /** code and pricingMode are immutable; everything else may change. */
    @Transactional
    public StandardServiceDto updateStandardService(Long id, StandardServiceUpdateDto dto) {
        MedicalService service = medicalServiceRepository.findById(id)
                .filter(s -> s.getPricingMode() == PricingMode.MANUAL_AMOUNT)
                .orElseThrow(() -> new BusinessRuleException("الخدمة المهنية القياسية غير موجودة"));
        MedicalCategory category = medicalCategoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new BusinessRuleException("التصنيف الطبي المحدد غير موجود"));

        service.setName(dto.getNameAr().trim());
        service.setNameAr(dto.getNameAr().trim());
        service.setNameEn(dto.getNameEn());
        service.setCategoryId(category.getId());
        service.setActive(dto.getActive());
        service = medicalServiceRepository.save(service);

        List<Provider.ProviderType> defaults = reconcileDefaults(service.getCode(), dto.getDefaultProviderTypes());
        return toDto(service, category, defaults);
    }

    /**
     * Makes the active ProviderServiceDefault rows for this service exactly
     * match {@code wantedTypes}: reactivates/creates the ones now wanted,
     * deactivates the ones no longer wanted. Never deletes a row -- the
     * unique (provider_type, service_code) constraint means a soft-deleted
     * row must be reused, not left to collide with a fresh insert.
     */
    private List<Provider.ProviderType> reconcileDefaults(String serviceCode, List<Provider.ProviderType> wantedTypes) {
        Set<Provider.ProviderType> wanted = new HashSet<>(wantedTypes == null ? List.of() : wantedTypes);
        List<com.waad.tba.modules.provider.entity.ProviderServiceDefault> existing =
                providerServiceDefaultRepository.findByServiceCode(serviceCode);
        Map<Provider.ProviderType, com.waad.tba.modules.provider.entity.ProviderServiceDefault> existingByType =
                existing.stream().collect(Collectors.toMap(
                        com.waad.tba.modules.provider.entity.ProviderServiceDefault::getProviderType, d -> d));

        List<com.waad.tba.modules.provider.entity.ProviderServiceDefault> toSave = new ArrayList<>();
        for (Provider.ProviderType type : wanted) {
            var row = existingByType.get(type);
            if (row == null) {
                toSave.add(com.waad.tba.modules.provider.entity.ProviderServiceDefault.builder()
                        .providerType(type).serviceCode(serviceCode).autoApply(true).active(true).build());
            } else if (!row.isActive()) {
                row.setActive(true);
                toSave.add(row);
            }
        }
        for (var row : existing) {
            if (!wanted.contains(row.getProviderType()) && row.isActive()) {
                row.setActive(false);
                toSave.add(row);
            }
        }
        if (!toSave.isEmpty()) {
            providerServiceDefaultRepository.saveAll(toSave);
        }
        return List.copyOf(wanted);
    }

    @Transactional(readOnly = true)
    public ProvisionStandardServicesSummaryDto preview(ProvisionStandardServicesRequestDto request) {
        return compute(request, false);
    }

    @Transactional
    public ProvisionStandardServicesSummaryDto apply(ProvisionStandardServicesRequestDto request) {
        return compute(request, true);
    }

    /**
     * The exact inverse of apply(): deactivates the same rows apply would
     * have created or reactivated, for a provider mistakenly included in an
     * earlier bulk apply. Never touches a provider or a service code outside
     * the requested scope, and never re-activates anything -- an assignment
     * that is already inactive is reported, not written to.
     */
    @Transactional(readOnly = true)
    public RevokeStandardServicesSummaryDto previewRevoke(ProvisionStandardServicesRequestDto request) {
        return computeRevoke(request, false);
    }

    @Transactional
    public RevokeStandardServicesSummaryDto revoke(ProvisionStandardServicesRequestDto request) {
        return computeRevoke(request, true);
    }

    private RevokeStandardServicesSummaryDto computeRevoke(
            ProvisionStandardServicesRequestDto request, boolean apply) {
        List<String> serviceCodes = validateServiceCodes(request.getServiceCodes());
        List<Provider> providers = resolveProviders(request);

        if (providers.isEmpty() || serviceCodes.isEmpty()) {
            return RevokeStandardServicesSummaryDto.builder().build();
        }

        List<Long> providerIds = providers.stream().map(Provider::getId).toList();
        List<ProviderService> existing = providerServiceRepository
                .findAllByProviderIdInAndServiceCodeIn(providerIds, serviceCodes);

        // A blanket "no financial effect" rule: any pair with even one claim
        // line ever recorded (any status -- a rejected claim still had a
        // line entered and priced) is refused, not silently deactivated.
        Set<String> withClaimHistory = claimRepository
                .findProviderServiceCodePairsWithClaimHistory(providerIds, serviceCodes).stream()
                .map(p -> p.getProviderId() + "|" + p.getServiceCode())
                .collect(Collectors.toSet());

        Map<Long, String> providerNames = providers.stream()
                .collect(Collectors.toMap(Provider::getId, Provider::getName));
        Map<String, String> serviceNames = medicalServiceRepository.findByCodeIn(serviceCodes).stream()
                .collect(Collectors.toMap(MedicalService::getCode, MedicalService::getName));

        List<ProviderService> toRevoke = new ArrayList<>();
        List<RevokeStandardServicesSummaryDto.BlockedAssignment> blocked = new ArrayList<>();
        long alreadyInactive = 0;
        Set<Long> providersAffected = new HashSet<>();

        for (ProviderService assignment : existing) {
            if (!Boolean.TRUE.equals(assignment.getActive())) {
                alreadyInactive++;
                continue;
            }
            String key = assignment.getProviderId() + "|" + assignment.getServiceCode();
            if (withClaimHistory.contains(key)) {
                blocked.add(RevokeStandardServicesSummaryDto.BlockedAssignment.builder()
                        .providerId(assignment.getProviderId())
                        .providerName(providerNames.get(assignment.getProviderId()))
                        .serviceCode(assignment.getServiceCode())
                        .serviceName(serviceNames.get(assignment.getServiceCode()))
                        .reason("توجد مطالبات مسجّلة سابقاً بهذه الخدمة لهذا المرفق — لا يمكن سحبها لوجود أثر مالي؛ السحب مسموح فقط لما لم يُستخدَم بعد في أي مطالبة")
                        .build());
                continue;
            }
            toRevoke.add(assignment);
            providersAffected.add(assignment.getProviderId());
        }

        if (apply && !toRevoke.isEmpty()) {
            toRevoke.forEach(ps -> ps.setActive(false));
            providerServiceRepository.saveAll(toRevoke);
        }

        return RevokeStandardServicesSummaryDto.builder()
                .providersMatched(providers.size())
                .providersAffected(providersAffected.size())
                .assignmentsToRevoke(toRevoke.size())
                .assignmentsAlreadyInactive(alreadyInactive)
                .assignmentsBlockedByClaimHistory(blocked.size())
                .blockedAssignments(blocked)
                .build();
    }

    /**
     * Called once, right after a new provider is persisted, inside the same
     * transaction as its creation -- a failure here must roll the provider
     * back too, not leave it created without its standard services.
     */
    @Transactional
    public void autoApplyOnNewProvider(Provider provider) {
        if (provider.getProviderType() == null) {
            return;
        }
        List<String> serviceCodes = providerServiceDefaultRepository
                .findByProviderTypeAndActiveTrueAndAutoApplyTrueOrderBySortOrder(provider.getProviderType())
                .stream().map(d -> d.getServiceCode()).toList();
        if (serviceCodes.isEmpty()) {
            return;
        }
        for (String code : serviceCodes) {
            providerServiceRepository.save(ProviderService.builder()
                    .providerId(provider.getId()).serviceCode(code).active(true).build());
        }
    }

    private ProvisionStandardServicesSummaryDto compute(
            ProvisionStandardServicesRequestDto request, boolean apply) {
        List<String> serviceCodes = validateServiceCodes(request.getServiceCodes());
        List<Provider> providers = resolveProviders(request);

        if (providers.isEmpty() || serviceCodes.isEmpty()) {
            return ProvisionStandardServicesSummaryDto.builder().build();
        }

        List<Long> providerIds = providers.stream().map(Provider::getId).toList();
        Map<String, ProviderService> existingByKey = providerServiceRepository
                .findAllByProviderIdInAndServiceCodeIn(providerIds, serviceCodes)
                .stream().collect(Collectors.toMap(
                        ps -> ps.getProviderId() + "|" + ps.getServiceCode(), ps -> ps));

        List<ProviderService> toInsert = new ArrayList<>();
        List<ProviderService> toReactivate = new ArrayList<>();
        long alreadyActive = 0;
        Set<Long> providersNeedingChanges = new HashSet<>();

        for (Provider provider : providers) {
            for (String code : serviceCodes) {
                ProviderService existing = existingByKey.get(provider.getId() + "|" + code);
                if (existing == null) {
                    toInsert.add(ProviderService.builder()
                            .providerId(provider.getId()).serviceCode(code).active(true).build());
                    providersNeedingChanges.add(provider.getId());
                } else if (!Boolean.TRUE.equals(existing.getActive())) {
                    toReactivate.add(existing);
                    providersNeedingChanges.add(provider.getId());
                } else {
                    alreadyActive++;
                }
            }
        }

        if (apply) {
            if (!toInsert.isEmpty()) {
                providerServiceRepository.saveAll(toInsert);
            }
            if (!toReactivate.isEmpty()) {
                toReactivate.forEach(ps -> ps.setActive(true));
                providerServiceRepository.saveAll(toReactivate);
            }
        }

        return ProvisionStandardServicesSummaryDto.builder()
                .providersMatched(providers.size())
                .providersAlreadyComplete(providers.size() - providersNeedingChanges.size())
                .providersNeedingChanges(providersNeedingChanges.size())
                .assignmentsToCreate(toInsert.size())
                .assignmentsToReactivate(toReactivate.size())
                .assignmentsAlreadyActive(alreadyActive)
                .build();
    }

    private List<String> validateServiceCodes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BusinessRuleException("يجب اختيار خدمة واحدة على الأقل");
        }
        List<String> normalized = requested.stream().map(c -> c.trim().toUpperCase()).distinct().toList();
        List<MedicalService> found = medicalServiceRepository.findByCodeIn(normalized);
        if (found.size() != normalized.size()) {
            Set<String> foundCodes = found.stream().map(MedicalService::getCode).collect(Collectors.toSet());
            List<String> missing = normalized.stream().filter(c -> !foundCodes.contains(c)).toList();
            throw new BusinessRuleException("خدمات غير موجودة في الفهرس الطبي: " + String.join(", ", missing));
        }
        boolean anyNotManual = found.stream().anyMatch(s -> s.getPricingMode() != PricingMode.MANUAL_AMOUNT);
        if (anyNotManual) {
            throw new BusinessRuleException(
                    "هذا التوفير الجماعي مخصص فقط للخدمات المهنية القياسية (المبلغ اليدوي)");
        }
        return normalized;
    }

    private List<Provider> resolveProviders(ProvisionStandardServicesRequestDto request) {
        if (request.getScope() == null) {
            throw new BusinessRuleException("يجب تحديد نطاق التطبيق");
        }
        return switch (request.getScope()) {
            case ALL_ACTIVE -> providerRepository.findByActiveTrue();
            case PROVIDER_TYPES -> {
                if (request.getProviderTypes() == null || request.getProviderTypes().isEmpty()) {
                    throw new BusinessRuleException("يجب تحديد نوع مرفق واحد على الأقل");
                }
                List<Provider.ProviderType> types = request.getProviderTypes().stream()
                        .map(t -> Provider.ProviderType.valueOf(t.trim().toUpperCase())).toList();
                yield providerRepository.findByActiveTrueAndProviderTypeIn(types);
            }
            case SELECTED_PROVIDERS -> {
                if (request.getProviderIds() == null || request.getProviderIds().isEmpty()) {
                    throw new BusinessRuleException("يجب تحديد مرفق واحد على الأقل");
                }
                yield providerRepository.findAllById(request.getProviderIds()).stream()
                        .filter(Provider::getActive).toList();
            }
        };
    }
}
