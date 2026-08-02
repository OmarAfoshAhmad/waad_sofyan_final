package com.waad.tba.modules.audit.service;

import com.waad.tba.modules.audit.dto.AuditLogViewDto;
import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the provider (facility) and employer (company) a given audit log entry belongs to,
 * at read time, without adding any column to the immutable {@code medical_audit_logs} table.
 *
 * Batches lookups per page (one query per entity type involved) instead of resolving row by row,
 * to keep this a small handful of queries regardless of page size.
 */
@Service
@RequiredArgsConstructor
public class AuditLogEnrichmentService {

    private final ClaimRepository claimRepository;
    private final VisitRepository visitRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final MemberRepository memberRepository;
    private final ProviderRepository providerRepository;
    private final EmployerRepository employerRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogViewDto> enrich(Page<AuditLog> page) {
        List<AuditLog> logs = page.getContent();

        Map<EntityType, Set<Long>> idsByType = new HashMap<>();
        for (AuditLog log : logs) {
            Long numericId = parseLongOrNull(log.getEntityId());
            if (numericId == null) continue;
            idsByType.computeIfAbsent(log.getEntityType(), k -> new HashSet<>()).add(numericId);
        }

        // entityType -> entityId -> providerId
        Map<Long, Long> claimProvider = idsByType.containsKey(EntityType.CLAIM)
                ? claimRepository.findAllById(idsByType.get(EntityType.CLAIM)).stream()
                        .collect(Collectors.toMap(Claim::getId, Claim::getProviderId, (a, b) -> a))
                : Map.of();
        Map<Long, Long> visitProvider = idsByType.containsKey(EntityType.VISIT)
                ? visitRepository.findAllById(idsByType.get(EntityType.VISIT)).stream()
                        .collect(Collectors.toMap(Visit::getId, Visit::getProviderId, (a, b) -> a))
                : Map.of();
        Map<Long, Long> preAuthProvider = idsByType.containsKey(EntityType.PREAUTHORIZATION)
                ? preAuthorizationRepository.findAllById(idsByType.get(EntityType.PREAUTHORIZATION)).stream()
                        .collect(Collectors.toMap(PreAuthorization::getId, PreAuthorization::getProviderId, (a, b) -> a))
                : Map.of();
        Map<Long, Long> memberEmployer = idsByType.containsKey(EntityType.MEMBER)
                ? memberRepository.findAllById(idsByType.get(EntityType.MEMBER)).stream()
                        .filter(m -> m.getEmployer() != null)
                        .collect(Collectors.toMap(Member::getId, m -> m.getEmployer().getId(), (a, b) -> a))
                : Map.of();

        Set<Long> allProviderIds = new HashSet<>();
        allProviderIds.addAll(claimProvider.values());
        allProviderIds.addAll(visitProvider.values());
        allProviderIds.addAll(preAuthProvider.values());
        if (idsByType.containsKey(EntityType.PROVIDER)) allProviderIds.addAll(idsByType.get(EntityType.PROVIDER));
        allProviderIds.remove(null);

        Set<Long> allEmployerIds = new HashSet<>(memberEmployer.values());
        if (idsByType.containsKey(EntityType.EMPLOYER)) allEmployerIds.addAll(idsByType.get(EntityType.EMPLOYER));
        allEmployerIds.remove(null);

        Map<Long, String> providerNames = allProviderIds.isEmpty() ? Map.of()
                : providerRepository.findAllById(allProviderIds).stream()
                        .collect(Collectors.toMap(Provider::getId, Provider::getName, (a, b) -> a));
        Map<Long, String> employerNames = allEmployerIds.isEmpty() ? Map.of()
                : employerRepository.findAllById(allEmployerIds).stream()
                        .collect(Collectors.toMap(Employer::getId, Employer::getName, (a, b) -> a));

        return page.map(log -> {
            Long numericId = parseLongOrNull(log.getEntityId());
            String facilityName = null;
            String companyName = null;

            if (numericId != null) {
                switch (log.getEntityType()) {
                    case CLAIM -> facilityName = providerNames.get(claimProvider.get(numericId));
                    case VISIT -> facilityName = providerNames.get(visitProvider.get(numericId));
                    case PREAUTHORIZATION -> facilityName = providerNames.get(preAuthProvider.get(numericId));
                    case PROVIDER -> facilityName = providerNames.get(numericId);
                    case MEMBER -> companyName = employerNames.get(memberEmployer.get(numericId));
                    case EMPLOYER -> companyName = employerNames.get(numericId);
                    default -> { }
                }
            }
            return AuditLogViewDto.of(log, facilityName, companyName);
        });
    }

    private Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
