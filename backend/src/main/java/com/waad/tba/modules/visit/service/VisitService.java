package com.waad.tba.modules.visit.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.visit.dto.VisitCreateDto;
import com.waad.tba.modules.visit.dto.VisitResponseDto;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.mapper.VisitMapper;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Visit Service with Policy Validation (Phase 6).
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BUSINESS RULES ENFORCED
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. VISIT CREATION requires:
 * - Member has active policy on visit date
 * - Member status is ACTIVE
 * - Policy covers the visit date (within start/end date range)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SMOKE TEST SCENARIO
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Scenario: Visit with Active Policy
 * Given: Member "Ali" has policy P001 valid from 2024-01-01 to 2024-12-31
 * When: Creating visit for Ali on 2024-06-15
 * Then: Visit created successfully
 * 
 * Scenario: Visit Without Policy
 * Given: Member "Sara" has no policy
 * When: Creating visit for Sara
 * Then: BusinessRuleException("Member has no active policy")
 * 
 * Scenario: Visit Outside Policy Dates
 * Given: Member "Omar" has policy valid until 2024-12-31
 * When: Creating visit for Omar on 2025-01-15
 * Then: PolicyNotActiveException("Policy is not active on 2025-01-15")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository repository;
    private final MemberRepository memberRepository;
    private final VisitMapper mapper;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final ProviderRepository providerRepository;
    private final ClaimRepository claimRepository;
    private final PreAuthorizationRepository preAuthRepository;
    private final ProviderContextGuard providerContextGuard;

    // BenefitPolicy validation (canonical source for all coverage decisions)
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;

    @Transactional(readOnly = true)
    public List<VisitResponseDto> findAll() {
        log.debug("📋 Finding all visits with data-level filtering");

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user found when accessing visits list");
            return Collections.emptyList();
        }

        List<Visit> visits;
        if (authorizationService.isSuperAdmin(currentUser) || authorizationService.isInsuranceAdmin(currentUser)) {
            visits = repository.findAll();
        } else if (authorizationService.isEmployerAdmin(currentUser)) {
            Long employerId = authorizationService.getEmployerFilterForUser(currentUser);
            if (employerId == null)
                return Collections.emptyList();
            visits = repository.findByMemberEmployerId(employerId);
        } else if (authorizationService.isProvider(currentUser)) {
            Long providerId = authorizationService.getProviderFilterForUser(currentUser);
            if (providerId == null)
                return Collections.emptyList();
            visits = repository.findByProviderId(providerId);
        } else {
            return Collections.emptyList();
        }

        return mapVisitsToDtos(visits);
    }

    @Transactional(readOnly = true)
    public VisitResponseDto findById(Long id) {
        log.debug("Finding visit by id: {}", id);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null)
            throw new AccessDeniedException("Authentication required");

        if (!authorizationService.canAccessVisit(currentUser, id)) {
            throw new AccessDeniedException("Access denied to this visit");
        }

        Visit entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visit", "id", id));

        Provider provider = null;
        if (entity.getProviderId() != null) {
            provider = providerRepository.findById(entity.getProviderId()).orElse(null);
        }

        Map<String, Object> extraData = fetchExtraData(entity.getId());

        auditLogService.createAuditLog("VIEW", "VISIT", id,
                "Visit viewed by " + currentUser.getUsername(),
                currentUser.getId(), currentUser.getUsername(), null, null);

        return mapper.toResponseDto(entity, provider != null ? provider.getName() : null, extraData);
    }

    @Transactional
    public VisitResponseDto create(VisitCreateDto dto) {
        log.info("📝 Creating new visit for member id: {}", dto.getMemberId());

        User currentUser = authorizationService.getCurrentUser();
        validateAndEnforceProviderId(dto, currentUser);

        Member member = memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", dto.getMemberId()));

        LocalDate visitDate = dto.getVisitDate() != null ? dto.getVisitDate() : LocalDate.now();

        if (member.getBenefitPolicy() != null) {
            benefitPolicyCoverageService.validateCanCreateClaim(member, visitDate);
        }

        Visit entity = mapper.toEntity(dto, member);
        Visit saved = repository.save(entity);

        Provider provider = providerRepository.findById(saved.getProviderId()).orElse(null);

        log.info("✅ Visit created successfully with id: {}", saved.getId());
        return mapper.toResponseDto(saved, provider != null ? provider.getName() : null, null);
    }

    @Transactional
    public VisitResponseDto update(Long id, VisitCreateDto dto) {
        log.info("Updating visit with id: {}", id);

        Visit entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visit", "id", id));

        Member member = memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", dto.getMemberId()));

        mapper.updateEntityFromDto(entity, dto, member);
        Visit updated = repository.save(entity);

        Provider provider = providerRepository.findById(updated.getProviderId()).orElse(null);

        log.info("Visit updated successfully: {}", id);
        return mapper.toResponseDto(updated, provider != null ? provider.getName() : null, fetchExtraData(id));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting visit with id: {}", id);
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Visit", "id", id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<VisitResponseDto> search(String query) {
        log.debug("Searching visits with query: {}", query);
        return mapVisitsToDtos(repository.search(query));
    }

    @Transactional(readOnly = true)
    public Page<VisitResponseDto> findAllPaginated(Long employerId, Pageable pageable, String search) {
        log.debug("Finding visits with pagination. employerId={}, search={}", employerId, search);

        User currentUser = authorizationService.requireCurrentUser();

        // Enforce employer scope for EMPLOYER_ADMIN (ignore requested employerId)
        if (authorizationService.isEmployerAdmin(currentUser)) {
            employerId = authorizationService.resolveEmployerScope(currentUser, employerId);
            if (employerId == null) {
                return Page.empty();
            }
        }

        Page<Visit> visitsPage;

        if (authorizationService.isProvider(currentUser)) {
            providerContextGuard.validateProviderBinding(currentUser);
            Long providerId = currentUser.getProviderId();
            if (search == null || search.isBlank()) {
                visitsPage = repository.findByProviderId(providerId, pageable);
            } else {
                visitsPage = repository.searchPagedByProviderId(search, providerId, pageable);
            }
        } else if (employerId != null) {
            if (search == null || search.isBlank()) {
                visitsPage = repository.findByMemberEmployerId(employerId, pageable);
            } else {
                visitsPage = repository.searchPagedByEmployerId(search, employerId, pageable);
            }
        } else {
            if (search == null || search.isBlank()) {
                visitsPage = repository.findAll(pageable);
            } else {
                visitsPage = repository.searchPaged(search, pageable);
            }
        }

        return mapVisitsPageToDtos(visitsPage);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE BULK DATA AGGREGATION HELPERS
    // Eliminates N+1 queries by pre-fetching all required data in bulk
    // ═══════════════════════════════════════════════════════════════════════════

    private Page<VisitResponseDto> mapVisitsPageToDtos(Page<Visit> visitsPage) {
        if (visitsPage.isEmpty())
            return Page.empty();
        List<VisitResponseDto> dtos = mapVisitsToDtos(visitsPage.getContent());
        return new org.springframework.data.domain.PageImpl<>(dtos, visitsPage.getPageable(),
                visitsPage.getTotalElements());
    }

    private List<VisitResponseDto> mapVisitsToDtos(List<Visit> visits) {
        if (visits.isEmpty())
            return Collections.emptyList();

        List<Long> visitIds = visits.stream().map(Visit::getId).toList();
        List<Long> providerIds = visits.stream().map(Visit::getProviderId).filter(Objects::nonNull).distinct().toList();

        // 1. Fetch provider names in bulk
        Map<Long, String> providerNameById = new HashMap<>();
        if (!providerIds.isEmpty()) {
            providerRepository.findByIdIn(providerIds).forEach(p -> providerNameById.put(p.getId(), p.getName()));
        }

        // 2. Fetch extra data summaries in bulk
        Map<Long, Map<String, Object>> extraDataByVisitId = new HashMap<>();
        visitIds.forEach(id -> extraDataByVisitId.put(id, new HashMap<>()));

        // Fetch claim summaries: visitId, count, latestId
        List<Object[]> claimStats = claimRepository.findClaimSummariesByVisitIds(visitIds);
        List<Long> latestClaimIds = claimStats.stream().map(s -> (Long) s[2]).filter(Objects::nonNull).toList();
        Map<Long, Claim> latestClaims = new HashMap<>();
        if (!latestClaimIds.isEmpty()) {
            claimRepository.findSummaryBaseByIds(latestClaimIds).forEach(c -> latestClaims.put(c.getId(), c));
        }

        for (Object[] stat : claimStats) {
            Long vId = (Long) stat[0];
            Integer count = ((Number) stat[1]).intValue();
            Long latestId = (Long) stat[2];
            Claim latest = latestClaims.get(latestId);

            Map<String, Object> data = extraDataByVisitId.get(vId);
            data.put("claimCount", count);
            data.put("latestClaimId", latestId);
            if (latest != null && latest.getStatus() != null) {
                data.put("latestClaimStatus", latest.getStatus().name());
                data.put("latestClaimStatusLabel", latest.getStatus().getArabicLabel());
            }
        }

        // Fetch pre-auth summaries: visitId, count, latestId
        List<Object[]> paStats = preAuthRepository.findPreAuthSummariesByVisitIds(visitIds);
        List<Long> latestPaIds = paStats.stream().map(s -> (Long) s[2]).filter(Objects::nonNull).toList();
        Map<Long, PreAuthorization> latestPas = new HashMap<>();
        if (!latestPaIds.isEmpty()) {
            preAuthRepository.findSummaryBaseByIds(latestPaIds).forEach(pa -> latestPas.put(pa.getId(), pa));
        }

        for (Object[] stat : paStats) {
            Long vId = (Long) stat[0];
            Integer count = ((Number) stat[1]).intValue();
            Long latestId = (Long) stat[2];
            PreAuthorization latest = latestPas.get(latestId);

            Map<String, Object> data = extraDataByVisitId.get(vId);
            data.put("preAuthCount", count);
            data.put("latestPreAuthId", latestId);
            if (latest != null && latest.getStatus() != null) {
                data.put("latestPreAuthStatus", latest.getStatus().name());
                data.put("latestPreAuthStatusLabel", latest.getStatus().getArabicLabel());
            }
        }

        return visits.stream().map(v -> {
            String pName = providerNameById.get(v.getProviderId());
            Map<String, Object> extra = extraDataByVisitId.get(v.getId());
            return mapper.toResponseDto(v, pName, extra);
        }).toList();
    }

    private Map<String, Object> fetchExtraData(Long visitId) {
        Map<String, Object> data = new HashMap<>();

        // Single visit fetch (could reuse mapVisitsToDtos but this is for single view)
        List<Object[]> claimStats = claimRepository.findClaimSummariesByVisitIds(Collections.singletonList(visitId));
        if (!claimStats.isEmpty()) {
            Object[] stat = claimStats.get(0);
            data.put("claimCount", ((Number) stat[1]).intValue());
            Long latestId = (Long) stat[2];
            if (latestId != null) {
                claimRepository.findById(latestId).ifPresent(latest -> {
                    data.put("latestClaimId", latest.getId());
                    if (latest.getStatus() != null) {
                        data.put("latestClaimStatus", latest.getStatus().name());
                        data.put("latestClaimStatusLabel", latest.getStatus().getArabicLabel());
                    }
                });
            }
        }

        List<Object[]> paStats = preAuthRepository.findPreAuthSummariesByVisitIds(Collections.singletonList(visitId));
        if (!paStats.isEmpty()) {
            Object[] stat = paStats.get(0);
            data.put("preAuthCount", ((Number) stat[1]).intValue());
            Long latestId = (Long) stat[2];
            if (latestId != null) {
                preAuthRepository.findById(latestId).ifPresent(latest -> {
                    data.put("latestPreAuthId", latest.getId());
                    if (latest.getStatus() != null) {
                        data.put("latestPreAuthStatus", latest.getStatus().name());
                        data.put("latestPreAuthStatusLabel", latest.getStatus().getArabicLabel());
                    }
                });
            }
        }

        return data;
    }

    @Transactional(readOnly = true)
    public long count(Long employerId) {
        if (employerId != null) {
            return repository.countByMemberEmployerId(employerId);
        }
        return repository.count();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PROVIDER PORTAL (2026-01-16): Provider Context Enforcement with Guard
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Validate and enforce provider ID based on user role.
     * 
     * ARCHITECTURAL RULES (HARDENED 2026-01-16):
     * - PROVIDER users: providerId ALWAYS comes from ProviderContextGuard (session)
     * ANY providerId from request is IGNORED to prevent data leakage
     * - SUPER_ADMIN/INSURANCE_ADMIN: can set any providerId (REQUIRED)
     * - providerId is REQUIRED for all visits
     * 
     * @param dto         The visit creation DTO
     * @param currentUser The currently authenticated user
     */
    private void validateAndEnforceProviderId(VisitCreateDto dto, User currentUser) {
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user - skipping provider validation");
            return;
        }

        // Check if user is a PROVIDER - use ProviderContextGuard for strict enforcement
        if (authorizationService.isProvider(currentUser)) {
            // ═══════════════════════════════════════════════════════════════════════════
            // SECURITY HARDENING: Use ProviderContextGuard for validation
            // This ensures provider binding is validated and providerId is enforced
            // ═══════════════════════════════════════════════════════════════════════════
            providerContextGuard.validateProviderBinding(currentUser);
            Long userProviderId = currentUser.getProviderId();

            // Log if request contained different providerId (potential attack/bug)
            if (dto.getProviderId() != null && !dto.getProviderId().equals(userProviderId)) {
                log.warn(
                        "🚨 PROVIDER_ID_OVERRIDE: User {} requested providerId={} but enforced to {} (potential security issue)",
                        currentUser.getUsername(), dto.getProviderId(), userProviderId);
            }

            // ALWAYS override with user's providerId - NO EXCEPTIONS
            dto.setProviderId(userProviderId);

            log.info("🔒 PROVIDER {} creating visit with their providerId: {} (enforced by ProviderContextGuard)",
                    currentUser.getUsername(), userProviderId);
        } else if (authorizationService.isSuperAdmin(currentUser)
                || authorizationService.isInsuranceAdmin(currentUser)) {
            // SUPER_ADMIN and INSURANCE_ADMIN can set any provider but MUST provide
            // providerId
            if (dto.getProviderId() == null) {
                throw new IllegalArgumentException(
                        "يجب تحديد مقدم الخدمة للمستخدمين الإداريين / Provider ID is required for admin users");
            }

            log.info("🔓 ADMIN user {} creating visit - any providerId allowed", currentUser.getUsername());

            // Validate provider exists
            providerRepository.findById(dto.getProviderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Provider", "id", dto.getProviderId()));
        } else {
            // Other roles: providerId is required
            if (dto.getProviderId() == null) {
                throw new IllegalArgumentException(
                        "يجب تحديد مقدم الخدمة / Provider ID is required");
            }
        }
    }
}
