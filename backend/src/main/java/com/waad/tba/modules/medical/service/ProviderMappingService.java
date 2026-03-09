package com.waad.tba.modules.medical.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medical.dto.RawServiceDto;
import com.waad.tba.modules.medical.entity.ProviderMappingAudit;
import com.waad.tba.modules.medical.entity.ProviderRawService;
import com.waad.tba.modules.medical.entity.ProviderServiceMapping;
import com.waad.tba.modules.medical.enums.MappingStatus;
import com.waad.tba.modules.medical.repository.ProviderMappingAuditRepository;
import com.waad.tba.modules.medical.repository.ProviderRawServiceRepository;
import com.waad.tba.modules.medical.repository.ProviderServiceMappingRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.ServiceAlias;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.ServiceAliasRepository;
import com.waad.tba.modules.provider.dto.ProviderSelectorDto;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core business logic for the Provider Mapping Center.
 *
 * <p>
 * Three operations:
 * <ol>
 * <li>{@link #autoMatch(Long)} — exact-match raw name → medical_services (code
 * / name / alias)</li>
 * <li>{@link #manualMap(Long, Long, Long)} — operator confirms a specific
 * mapping</li>
 * <li>{@link #rejectMapping(Long, Long)} — operator rejects an unmappable
 * service name</li>
 * </ol>
 *
 * <p>
 * Architectural constraints:
 * <ul>
 * <li>No modification to provider_contract_pricing_items</li>
 * <li>No modification to claim or pre-auth flow</li>
 * <li>medical_services is read-only from this service</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderMappingService {

        private final ProviderRawServiceRepository rawServiceRepo;
        private final ProviderServiceMappingRepository mappingRepo;
        private final ProviderMappingAuditRepository auditRepo;
        private final MedicalServiceRepository medicalServiceRepo;
        private final ServiceAliasRepository aliasRepo;
        private final UserRepository userRepo;
        private final ProviderRepository providerRepository;
        private final ProviderContractRepository contractRepository;

        // ═══════════════════════════════════════════════════════════════════════
        // PROVIDERS WITH ACTIVE CONTRACTS
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Returns providers that have at least one currently ACTIVE contract.
         * Used by the Mapping Center selector to prevent linking services to
         * providers without a valid contract.
         */
        @Transactional(readOnly = true)
        public List<ProviderSelectorDto> getProvidersWithActiveContracts() {
                List<Long> ids = contractRepository.findDistinctProviderIdsWithActiveContracts();
                return providerRepository.findAllById(ids).stream()
                                .filter(Provider::getActive)
                                .sorted(Comparator.comparing(Provider::getName,
                                                Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(p -> ProviderSelectorDto.builder()
                                                .id(p.getId())
                                                .code(p.getLicenseNumber())
                                                .name(p.getName())
                                                .providerType(p.getProviderType() != null
                                                                ? p.getProviderType().name()
                                                                : null)
                                                .build())
                                .collect(Collectors.toList());
        }

        // ═══════════════════════════════════════════════════════════════════════
        // READ
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Returns raw services for a provider filtered by status.
         * Each DTO is enriched with the existing mapping if one exists.
         */
        @Transactional(readOnly = true)
        public List<RawServiceDto> getRawServices(Long providerId, MappingStatus status) {
                List<ProviderRawService> rows = (status != null)
                                ? rawServiceRepo.findByProviderIdAndStatus(providerId, status)
                                : rawServiceRepo.findByProviderId(providerId);

                return rows.stream()
                                .map(this::toDto)
                                .collect(Collectors.toList());
        }

        // ═══════════════════════════════════════════════════════════════════════
        // AUTO MATCH
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Attempts an exact-match auto-mapping for the given raw service.
         *
         * <p>
         * Match order:
         * <ol>
         * <li>medical_services.code (case-insensitive)</li>
         * <li>medical_services.name (case-insensitive)</li>
         * <li>ent_service_aliases.alias_text (any locale)</li>
         * </ol>
         *
         * <p>
         * On match: creates/updates the mapping, sets status AUTO_MATCHED, confidence
         * 100.
         * <p>
         * No match: leaves status PENDING (no exception thrown).
         *
         * @return updated raw service DTO
         */
        @Transactional
        public RawServiceDto autoMatch(Long rawServiceId) {
                ProviderRawService raw = loadRawService(rawServiceId);

                if (raw.getStatus() == MappingStatus.MANUAL_CONFIRMED
                                || raw.getStatus() == MappingStatus.REJECTED) {
                        throw new BusinessRuleException(
                                        "Cannot auto-match a service with status: " + raw.getStatus());
                }

                String normalized = normalize(raw.getRawName());
                raw.setNormalizedName(normalized);

                Optional<MedicalService> matchOpt = findExactMatch(normalized);

                if (matchOpt.isPresent()) {
                        MedicalService matched = matchOpt.get();
                        MappingStatus oldStatus = raw.getStatus();

                        // Create or update the mapping record
                        ProviderServiceMapping mapping = mappingRepo
                                        .findByProviderRawServiceId(raw.getId())
                                        .orElseGet(() -> ProviderServiceMapping.builder()
                                                        .providerRawService(raw)
                                                        .build());

                        mapping.setMedicalService(matched);
                        mapping.setMappingStatus(MappingStatus.AUTO_MATCHED);
                        mapping.setConfidenceScore(BigDecimal.valueOf(100));
                        mapping.setMappedAt(LocalDateTime.now());
                        mapping.setMappedBy(null); // system action
                        mappingRepo.save(mapping);

                        // Register raw name as alias in unified catalog (if new)
                        saveAliasIfAbsent(matched.getId(), raw.getRawName(), "system");

                        // Update raw service status
                        raw.setStatus(MappingStatus.AUTO_MATCHED);
                        raw.setConfidenceScore(BigDecimal.valueOf(100));
                        rawServiceRepo.save(raw);

                        // Write audit
                        writeAudit(raw, null, "AUTO_MATCH",
                                        oldStatus.name(),
                                        MappingStatus.AUTO_MATCHED.name() + " → " + matched.getCode());

                        log.info("AUTO_MATCH: raw={} → medicalService={}", raw.getId(), matched.getCode());
                } else {
                        rawServiceRepo.save(raw); // persist normalized_name update
                        log.debug("AUTO_MATCH: no match found for raw={} normalizedName={}", raw.getId(), normalized);
                }

                return toDto(raw);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // MANUAL MAP
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Operator explicitly maps a raw service to a specific medical service.
         *
         * @param rawId            ID of the raw service
         * @param medicalServiceId ID of the target medical service
         * @param userId           ID of the operator performing the action
         * @return updated raw service DTO
         */
        @Transactional
        public RawServiceDto manualMap(Long rawId, Long medicalServiceId, Long userId) {
                ProviderRawService raw = loadRawService(rawId);

                if (raw.getStatus() == MappingStatus.REJECTED) {
                        throw new BusinessRuleException(
                                        "Cannot map a rejected service. Restore it first.");
                }

                MedicalService service = medicalServiceRepo.findById(medicalServiceId)
                                .orElseThrow(() -> new BusinessRuleException(
                                                "Medical service not found: " + medicalServiceId));

                User operator = loadUser(userId);
                MappingStatus oldStatus = raw.getStatus();

                ProviderServiceMapping mapping = mappingRepo
                                .findByProviderRawServiceId(raw.getId())
                                .orElseGet(() -> ProviderServiceMapping.builder()
                                                .providerRawService(raw)
                                                .build());

                mapping.setMedicalService(service);
                mapping.setMappingStatus(MappingStatus.MANUAL_CONFIRMED);
                mapping.setMappedBy(operator);
                mapping.setMappedAt(LocalDateTime.now());
                mapping.setConfidenceScore(BigDecimal.valueOf(100));
                mappingRepo.save(mapping);

                // Register raw name as alias in unified catalog (if new)
                saveAliasIfAbsent(service.getId(), raw.getRawName(), "user:" + userId);

                raw.setStatus(MappingStatus.MANUAL_CONFIRMED);
                raw.setConfidenceScore(BigDecimal.valueOf(100));
                rawServiceRepo.save(raw);

                writeAudit(raw, operator, "MANUAL_MAP",
                                oldStatus.name(),
                                MappingStatus.MANUAL_CONFIRMED.name() + " → " + service.getCode());

                log.info("MANUAL_MAP: raw={} → medicalService={} by user={}",
                                raw.getId(), service.getCode(), userId);

                return toDto(raw);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // REJECT
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Marks a raw service as rejected — the name cannot be meaningfully mapped.
         *
         * @param rawId  ID of the raw service to reject
         * @param userId ID of the operator performing the action
         * @return updated raw service DTO
         */
        @Transactional
        public RawServiceDto rejectMapping(Long rawId, Long userId) {
                ProviderRawService raw = loadRawService(rawId);

                if (raw.getStatus() == MappingStatus.REJECTED) {
                        throw new BusinessRuleException("Service is already rejected.");
                }

                User operator = loadUser(userId);
                MappingStatus oldStatus = raw.getStatus();

                raw.setStatus(MappingStatus.REJECTED);
                rawServiceRepo.save(raw);

                writeAudit(raw, operator, "REJECT",
                                oldStatus.name(),
                                MappingStatus.REJECTED.name());

                log.info("REJECT: raw={} by user={}", raw.getId(), userId);

                return toDto(raw);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // PRIVATE HELPERS
        // ═══════════════════════════════════════════════════════════════════════

        private ProviderRawService loadRawService(Long id) {
                return rawServiceRepo.findById(id)
                                .orElseThrow(() -> new BusinessRuleException(
                                                "Provider raw service not found: " + id));
        }

        private User loadUser(Long userId) {
                return userRepo.findById(userId)
                                .orElseThrow(() -> new BusinessRuleException(
                                                "User not found: " + userId));
        }

        /**
         * Exact-match search: code → name → alias (case-insensitive, in order).
         */
        private Optional<MedicalService> findExactMatch(String normalized) {
                // 1. Try exact code match
                Optional<MedicalService> byCode = medicalServiceRepo.findByCode(normalized.toUpperCase());
                if (byCode.isPresent())
                        return byCode;

                // 2. Try exact name match
                Optional<MedicalService> byName = medicalServiceRepo.findFirstByName(normalized);
                if (byName.isEmpty()) {
                        // case-insensitive name probe using LIKE via existing methods
                        byName = medicalServiceRepo.findAll().stream()
                                        .filter(ms -> ms.getName() != null
                                                        && ms.getName().trim().equalsIgnoreCase(normalized))
                                        .findFirst();
                }
                if (byName.isPresent())
                        return byName;

                // 3. Try alias match
                Optional<ServiceAlias> alias = aliasRepo.findFirstByAliasTextIgnoreCase(normalized);
                if (alias.isPresent()) {
                        return medicalServiceRepo.findById(alias.get().getMedicalServiceId());
                }

                return Optional.empty();
        }

        /** Lower-case and collapse whitespace */
        private String normalize(String raw) {
                if (raw == null)
                        return "";
                return raw.trim().toLowerCase().replaceAll("\\s+", " ");
        }

        /**
         * Adds the raw service name as an alias for the confirmed medical service
         * in the unified catalog (ent_service_aliases), skipping duplicates.
         */
        private void saveAliasIfAbsent(Long medicalServiceId, String rawName, String createdBy) {
                if (rawName == null || rawName.isBlank())
                        return;
                String trimmed = rawName.trim();
                if (!aliasRepo.existsByMedicalServiceIdAndAliasTextIgnoreCase(medicalServiceId, trimmed)) {
                        aliasRepo.save(ServiceAlias.builder()
                                        .medicalServiceId(medicalServiceId)
                                        .aliasText(trimmed)
                                        .locale("ar")
                                        .createdBy(createdBy)
                                        .build());
                        log.debug("ALIAS_ADDED: medicalServiceId={} aliasText={}", medicalServiceId, trimmed);
                }
        }

        private void writeAudit(ProviderRawService raw, User operator,
                        String action, String oldValue, String newValue) {
                auditRepo.save(ProviderMappingAudit.builder()
                                .providerRawService(raw)
                                .action(action)
                                .oldValue(oldValue)
                                .newValue(newValue)
                                .performedBy(operator)
                                .performedAt(LocalDateTime.now())
                                .build());
        }

        // ═══════════════════════════════════════════════════════════════════════
        // MAPPING TO DTO
        // ═══════════════════════════════════════════════════════════════════════

        private RawServiceDto toDto(ProviderRawService raw) {
                RawServiceDto.RawServiceDtoBuilder builder = RawServiceDto.builder()
                                .id(raw.getId())
                                .providerId(raw.getProviderId())
                                .rawName(raw.getRawName())
                                .normalizedName(raw.getNormalizedName())
                                .code(raw.getCode())
                                .encounterType(raw.getEncounterType())
                                .source(raw.getSource())
                                .status(raw.getStatus())
                                .confidenceScore(raw.getConfidenceScore())
                                .createdAt(raw.getCreatedAt());

                mappingRepo.findByProviderRawServiceId(raw.getId()).ifPresent(m -> {
                        MedicalService ms = m.getMedicalService();
                        builder.mapping(RawServiceDto.MappingDto.builder()
                                        .mappingId(m.getId())
                                        .medicalServiceId(ms.getId())
                                        .medicalServiceCode(ms.getCode())
                                        .medicalServiceName(ms.getName())
                                        .mappingStatus(m.getMappingStatus())
                                        .confidenceScore(m.getConfidenceScore())
                                        .mappedAt(m.getMappedAt())
                                        .build());
                });

                return builder.build();
        }
}
