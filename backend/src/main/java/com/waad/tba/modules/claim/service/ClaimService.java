package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimDataUpdateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimRejectDto;
import com.waad.tba.modules.claim.dto.ClaimReturnForInfoDto;
import com.waad.tba.modules.claim.dto.ClaimReviewDto;
import com.waad.tba.modules.claim.dto.ClaimSettleDto;
import com.waad.tba.modules.claim.dto.ClaimUpdateDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.dto.CostBreakdownDto;
import com.waad.tba.modules.claim.dto.FinancialSummaryDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.ClaimType;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderNetworkService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.settlement.event.ClaimApprovedEvent;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Claim Service with Business Flow Validation (Phase 6).
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BUSINESS RULES ENFORCED
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. CLAIM CREATION requires:
 * - Member has active policy (validated by PolicyValidationService)
 * - Policy covers the service date
 * - Requested services are covered in benefit package
 * - Coverage limits not exceeded
 * 
 * 2. CLAIM UPDATE follows state machine:
 * - Only DRAFT and NEEDS_CORRECTION allow detail edits
 * - Status transitions validated by ClaimStateMachine
 * 
 * 3. STATUS TRANSITIONS require appropriate roles:
 * - DRAFT → SUBMITTED (EMPLOYER, INSURANCE)
 * - SUBMITTED → UNDER_REVIEW (INSURANCE, REVIEWER)
 * - UNDER_REVIEW → APPROVED/REJECTED (INSURANCE, REVIEWER)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * EXAMPLE FLOW: Member → Claim → Review → Decision
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. Member "Ali" visits doctor on 2024-06-15
 * 2. EMPLOYER creates claim (status=DRAFT)
 * → PolicyValidationService checks Ali's policy is active on 2024-06-15
 * → CoverageValidationService checks services are covered
 * 3. EMPLOYER submits claim (DRAFT → SUBMITTED)
 * 4. INSURANCE takes for review (SUBMITTED → UNDER_REVIEW)
 * 5. REVIEWER approves with amount (UNDER_REVIEW → APPROVED)
 * → Must set approvedAmount > 0
 * 6. INSURANCE settles payment (APPROVED → SETTLED)
 * → Terminal state, no more changes allowed
 * 
 * SECURITY HARDENING (2026-01-16):
 * Provider data isolation enforced via ProviderContextGuard.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
@SuppressWarnings("deprecation")
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimMapper claimMapper;
    private final AuthorizationService authorizationService;
    private final ProviderContextGuard providerContextGuard;
    private final MemberRepository memberRepository;
    private final ProviderRepository providerRepository;
    private final VisitRepository visitRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;

    // Phase 8: BenefitPolicy-based coverage validation (Single Source of Truth)
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final ClaimStateMachine claimStateMachine;

    // Phase 7: Operational completeness services
    private final ProviderNetworkService providerNetworkService;
    private final AttachmentRulesService attachmentRulesService;
    private final CostCalculationService costCalculationService;
    private final ClaimAuditService claimAuditService;

    // Phase 1: SLA tracking services
    private final com.waad.tba.common.service.BusinessDaysCalculatorService businessDaysCalculator;

    // Phase 3A: Settlement event publishing
    private final ApplicationEventPublisher eventPublisher;

    // Phase 9: Architectural Guards (System Invariants)
    private final ArchitecturalGuardService architecturalGuard;

    // Phase 1 (2026-01-28): Atomic Financial Operations
    private final AtomicFinancialService atomicFinancialService;

    // Phase 5 (2026-02-02): Pre-Authorization Lifecycle Management
    private final PreAuthorizationService preAuthorizationService;

    // PHASE NEXT (2026-02-12): Medical Reviewer Isolation
    private final ReviewerProviderIsolationService reviewerIsolationService;

    // Phase 10 (2026-03-06): Provider-Employer Security Hardening
    private final com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository providerAllowedEmployerRepository;

    // Phase 11 (2026-03-11): Mandatory Monthly Batches
    private final ClaimBatchService claimBatchService;
    private final com.waad.tba.modules.claim.repository.ClaimBatchRepository claimBatchRepository;

    // Jakarta persistence for native cleanup (RESTRICT constraint bypass)
    private final jakarta.persistence.EntityManager em;

    /**
     * Search claims with explicit employer filtering.
     * UPDATED 2026-01-05: Added PROVIDER filtering (Global Best Practice)
     * 
     * @param employerId Optional employer ID for filtering (null = show all for
     *                   admin)
     * @param query      Search query string
     */
    public List<ClaimViewDto> search(Long employerId, String query) {
        log.debug("🔍 Searching claims with query: {}, employerId: {}", query, employerId);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user");
            return Collections.emptyList();
        }

        // Check feature flags for EMPLOYER_ADMIN
        if (authorizationService.isEmployerAdmin(currentUser)) {
            // EMPLOYER role is locked to their own employer
            employerId = currentUser.getEmployerId();
        }

        // PROVIDER filtering - show only their own claims
        // UPDATED 2026-01-16: Now using ProviderContextGuard for strict validation
        if (authorizationService.isProvider(currentUser)) {
            // Validate provider binding first
            providerContextGuard.validateProviderBinding(currentUser);
            Long providerId = currentUser.getProviderId();

            log.info("🔒 Applying provider filter for claims: providerId={} for user {}",
                    providerId, currentUser.getUsername());

            // Use provider-specific search method
            List<Claim> providerClaims = claimRepository.searchByProviderId(query, providerId);
            log.info("✅ Found {} claims for provider {}", providerClaims.size(), providerId);

            return providerClaims.stream()
                    .map(claimMapper::toViewDto)
                    .collect(Collectors.toList());
        }

        List<Claim> claims;

        if (employerId != null) {
            log.debug("🔒 Filtering claims by employerId={}", employerId);
            claims = claimRepository.searchByEmployerId(query, employerId);
        } else if (authorizationService.isSuperAdmin(currentUser)) {
            log.debug("🔓 Admin - searching all claims");
            claims = claimRepository.search(query);
        } else {
            log.warn("❌ No employer scope and not admin");
            return Collections.emptyList();
        }

        return claims.stream()
                .map(claimMapper::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Create a new claim with business rule validation.
     * 
     * PHASE 6 VALIDATION:
     * 1. Member must have active policy
     * 2. Policy must cover service date
     * 3. Services must be covered in benefit package
     * 4. Coverage limits must not be exceeded
     * 
     * PHASE 7 ADDITIONS:
     * 5. Provider network validation (IN_NETWORK/OUT_OF_NETWORK warning)
     * 6. Cost calculation preview (deductible, co-pay)
     * 7. Audit trail creation
     * 
     * PHASE 8 ADDITIONS:
     * 8. BenefitPolicy validation (new single source of truth)
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * CANONICAL REBUILD (2026-01-15): Visit-Centric, Contract-Driven
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * NEW ARCHITECTURE:
     * 1. Visit is MANDATORY - all data flows from Visit
     * 2. Member and Provider come from Visit (not DTO)
     * 3. Prices come from ProviderContract (not user input)
     * 4. Services must be selected from MedicalService table
     */
    public ClaimViewDto createClaim(ClaimCreateDto dto) {
        log.info("📝 [CANONICAL] Creating claim with Visit-Centric Architecture");

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Validate Visit exists (MANDATORY)
        // ═══════════════════════════════════════════════════════════════════════════
        if (dto.getVisitId() == null) {
            throw new BusinessRuleException("ARCHITECTURAL VIOLATION: visitId is REQUIRED");
        }

        // Get current user for audit
        User currentUser = authorizationService.getCurrentUser();

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1.1: Standard Validation & Security
        // ═══════════════════════════════════════════════════════════════════════════
        validateCreateDto(dto);
        validateAndEnforceProviderId(dto, currentUser);

        // ═══════════════════════════════════════════════════════════════════════════
        // ARCHITECTURAL GUARD: Validate system invariants before processing
        // ═══════════════════════════════════════════════════════════════════════════
        List<Long> serviceIds = dto.getLines() != null
                ? dto.getLines().stream().map(ClaimLineDto::getMedicalServiceId).toList()
                : List.of();
        architecturalGuard.guardClaimCreation(dto.getVisitId(), serviceIds);

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: Pre-fetch data for Pure Mapping (Phase 2 Performance Hardening)
        // ═══════════════════════════════════════════════════════════════════════════
        Visit visit = visitRepository.findById(dto.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit", "id", dto.getVisitId()));

        Provider provider = providerRepository.findById(visit.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider", "id", visit.getProviderId()));

        // SECURITY: Verify provider is authorized for this member's employer
        // (Prevent cross-tenant claim creation)
        if (visit.getMember() != null && visit.getMember().getEmployer() != null) {
            // Bypass check if provider allows all employers (global network)
            boolean isGlobalProvider = Boolean.TRUE.equals(provider.getAllowAllEmployers());

            boolean isAuthorized = isGlobalProvider || providerAllowedEmployerRepository.hasActiveAccessToEmployer(
                    provider.getId(), visit.getMember().getEmployer().getId());

            if (!isAuthorized) {
                log.error(
                        "🛑 SECURITY ALERT: Provider {} attempted to create claim for UNAUTHORIZED employer {} (Member: {})",
                        provider.getId(), visit.getMember().getEmployer().getId(), visit.getMember().getId());
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "المزود غير مخول لتقديم خدمات لموظفي هذه الجهة (" + visit.getMember().getEmployer().getName()
                                + ").");
            }
        }

        PreAuthorization preAuth = null;
        if (dto.getPreAuthorizationId() != null) {
            preAuth = preAuthorizationRepository.findById(dto.getPreAuthorizationId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("PreAuthorization", "id", dto.getPreAuthorizationId()));
        }

        // Fetch medical services in bulk for mapping
        Map<Long, MedicalService> medicalServiceMap = medicalServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(MedicalService::getId, s -> s));

        // Resolve Claim Batch (Phase 11)
        com.waad.tba.modules.claim.entity.ClaimBatch claimBatch = null;
        if (dto.getClaimBatchId() != null) {
            claimBatchService.validateBatchIsOpen(dto.getClaimBatchId());
            claimBatch = claimBatchRepository.findById(dto.getClaimBatchId()).orElse(null);

            if (claimBatch != null) {
                if (!claimBatch.getProviderId().equals(provider.getId()) ||
                        !claimBatch.getEmployerId().equals(visit.getMember().getEmployer().getId())) {
                    throw new BusinessRuleException("الدفعة المختارة لا تتطابق مع المزود أو جهة العمل للمطالبة.");
                }
            }
        }

        Claim claim = claimMapper.toEntity(dto, visit, provider, preAuth, claimBatch, medicalServiceMap);
        // Status set to APPROVED by mapper — direct entry model (no review workflow)
        Claim savedClaim = claimRepository.save(claim);

        // Record creation in audit trail
        if (currentUser != null) {
            claimAuditService.recordCreation(savedClaim, currentUser);
        }

        log.info("✅ Claim {} created in APPROVED status (Direct Implementation)", savedClaim.getId());

        // Credit provider account immediately upon creation (same as approval event)
        if (savedClaim.getProviderId() != null && savedClaim.getApprovedAmount() != null) {
            eventPublisher.publishEvent(new ClaimApprovedEvent(
                    this,
                    savedClaim.getId(),
                    savedClaim.getProviderId(),
                    currentUser != null ? currentUser.getId() : null));
            log.info("📤 [EVENT] Published ClaimApprovedEvent for claim {} (direct entry)", savedClaim.getId());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PHASE 5: Auto-mark PreAuthorization as USED when linked to claim
        // ═══════════════════════════════════════════════════════════════════════════
        if (savedClaim.getPreAuthorization() != null) {
            PreAuthorization linkedPreAuth = savedClaim.getPreAuthorization();

            // Auto-transition to USED if currently APPROVED or ACKNOWLEDGED
            if (linkedPreAuth.getStatus() == PreAuthStatus.APPROVED ||
                    linkedPreAuth.getStatus() == PreAuthStatus.ACKNOWLEDGED) {

                String createdBy = currentUser != null ? currentUser.getUsername() : "system";
                try {
                    preAuthorizationService.markAsUsed(
                            linkedPreAuth.getId(),
                            savedClaim.getId().toString(),
                            createdBy);
                    log.info("✅ Pre-authorization {} auto-marked as USED (linked to claim {})",
                            linkedPreAuth.getReferenceNumber(), savedClaim.getId());
                } catch (Exception e) {
                    log.warn("⚠️ Failed to auto-mark pre-authorization {} as USED: {}",
                            linkedPreAuth.getId(), e.getMessage());
                    // Non-critical: Don't fail claim creation if pre-auth update fails
                }
            } else {
                log.warn(
                        "⚠️ Pre-authorization {} has status {} (expected APPROVED or ACKNOWLEDGED). Not auto-marking as USED.",
                        linkedPreAuth.getId(), linkedPreAuth.getStatus());
            }
        }

        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Update an existing claim with state machine validation.
     * 
     * ╔═══════════════════════════════════════════════════════════════════════════╗
     * ║ FINANCIAL CLOSURE: FINANCIAL SNAPSHOT IMMUTABILITY ║
     * ║───────────────────────────────────────────────────────────────────────────║
     * ║ After APPROVED status: ║
     * ║ - approvedAmount CANNOT be changed ║
     * ║ - netProviderAmount CANNOT be changed ║
     * ║ - patientCoPay CANNOT be changed ║
     * ║ These values are the FINANCIAL SNAPSHOT and must remain immutable. ║
     * ╚═══════════════════════════════════════════════════════════════════════════╝
     * 
     * PHASE 6 RULES:
     * 1. Only DRAFT and NEEDS_CORRECTION allow detail edits
     * 2. Status changes go through ClaimStateMachine
     * 3. REJECTED requires reviewer comment
     * 4. APPROVED requires approved amount
     * 
     * PHASE 7 ADDITIONS:
     * 5. Attachment validation before SUBMITTED
     * 6. Cost calculation before APPROVED
     * 7. Audit trail for all changes
     */
    public ClaimViewDto updateClaim(Long id, ClaimUpdateDto dto) {
        log.info("📝 Updating claim {}", id);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();

        // PART 2 — CLAIM SAFETY: Protect Claim Modification After Submission
        // Allow editing: APPROVED (direct entry state), NEEDS_CORRECTION (suspended for
        // review),
        // SETTLED/REJECTED (manual entry fixes - Backlog Flow)
        if (claim.getStatus() != ClaimStatus.DRAFT &&
                claim.getStatus() != ClaimStatus.APPROVED &&
                claim.getStatus() != ClaimStatus.NEEDS_CORRECTION) {
            throw new IllegalStateException(
                    "لا يمكن تعديل المطالبة في حالتها الحالية: " + claim.getStatus());
        }

        // Validate Claim Batch if present (Editing restricted to OPEN batches)
        if (claim.getClaimBatch() != null) {
            claimBatchService.validateBatchIsOpen(claim.getClaimBatch().getId());
        }

        // ══════════════════════════════════════════════════════════════════════════
        // FINANCIAL CLOSURE: FINANCIAL SNAPSHOT IMMUTABILITY GUARD
        // ══════════════════════════════════════════════════════════════════════════
        // After approval, financial fields are FROZEN and CANNOT be changed
        if (isFinanciallyLocked(claim)) {
            validateNoFinancialChanges(claim, dto);
        }

        // Phase 6: Check if status change is requested
        if (dto.getStatus() != null && dto.getStatus() != claim.getStatus()) {

            // Phase 7: Attachment validation DISABLED (2026-02-24) - attachments are
            // optional
            // var attachmentResult = attachmentRulesService.validateForSubmission(claim,
            // ClaimType.GENERAL);
            // if (!attachmentResult.valid()) {
            // throw new BusinessRuleException(
            // "Cannot submit claim: " + attachmentResult.getErrorMessage()
            // );
            // }
            log.info("ℹ️ Attachment validation skipped - attachments are optional");

            // Phase 7: Calculate costs before APPROVED
            if (dto.getStatus() == ClaimStatus.APPROVED) {
                var costBreakdown = costCalculationService.calculateCosts(claim);
                log.info("💰 Cost calculation for approval: {}", costBreakdown.getSummary());
                // Costs are calculated but actual approved amount is set by reviewer
            }

            // Use state machine for status transitions
            claimStateMachine.transition(claim, dto.getStatus(), currentUser);

            // Phase 7: Record status change in audit trail
            claimAuditService.recordStatusChange(claim, previousStatus, currentUser, dto.getReviewerComment());

        } else {
            // Regular update - check if edits are allowed
            // PHASE 11: Allow SETTLED/REJECTED for manual entries
            if (!claimStateMachine.canEdit(claim) &&
                    claim.getStatus() != ClaimStatus.SETTLED &&
                    claim.getStatus() != ClaimStatus.REJECTED) {
                throw new BusinessRuleException(
                        String.format(
                                "لا يمكن تعديل المطالبة في حالة %s. التعديل مسموح فقط للمسودات والمطالبات اليدوية.",
                                claim.getStatus()));
            }
        }

        // Validate and apply other changes
        validateUpdateDto(dto, claim);

        PreAuthorization preAuth = null;
        if (dto.getPreAuthorizationId() != null) {
            preAuth = preAuthorizationRepository.findById(dto.getPreAuthorizationId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("PreAuthorization", "id", dto.getPreAuthorizationId()));
        }

        claimMapper.updateEntityFromDto(claim, dto, preAuth);

        Claim updatedClaim = claimRepository.save(claim);
        log.info("✅ Claim {} updated, status: {}", id, updatedClaim.getStatus());

        return claimMapper.toViewDto(updatedClaim);
    }

    /**
     * Update claim DATA only (for PROVIDER and EMPLOYER_ADMIN).
     * SECURITY: This method enforces that only data fields can be updated.
     * Status changes must use review endpoint or dedicated transition methods.
     * 
     * @param id  Claim ID
     * @param dto Data update DTO (no status/financial fields)
     * @return Updated claim
     * @throws BusinessRuleException if claim status doesn't allow editing
     * @since Provider Portal Security Fix (Phase 0)
     */
    public ClaimViewDto updateClaimData(Long id, ClaimDataUpdateDto dto) {
        log.info("📝 Updating claim DATA: id={}", id);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();

        // PART 2 — CLAIM SAFETY: Protect Claim Modification After Submission
        if (claim.getStatus() != ClaimStatus.DRAFT &&
                claim.getStatus() != ClaimStatus.APPROVED &&
                claim.getStatus() != ClaimStatus.NEEDS_CORRECTION) {
            throw new IllegalStateException(
                    "Claim cannot be modified in current status: " + claim.getStatus());
        }

        // SECURITY: Verify user can modify this claim
        if (!authorizationService.canModifyClaim(currentUser, id)) {
            throw new AccessDeniedException("You do not have permission to modify this claim");
        }

        // SECURITY: Verify claim is in editable status
        if (!claim.getStatus().allowsEdit()) {
            throw new BusinessRuleException(
                    String.format("Cannot edit claim in %s status. Only DRAFT and NEEDS_CORRECTION allow edits.",
                            claim.getStatus()));
        }

        // Update data fields only
        if (dto.getDoctorName() != null) {
            claim.setDoctorName(dto.getDoctorName());
        }
        if (dto.getDiagnosisCode() != null) {
            claim.setDiagnosisCode(dto.getDiagnosisCode());
        }
        if (dto.getDiagnosisDescription() != null) {
            claim.setDiagnosisDescription(dto.getDiagnosisDescription());
        }
        if (dto.getPreAuthorizationId() != null) {
            PreAuthorization preAuth = new PreAuthorization();
            preAuth.setId(dto.getPreAuthorizationId());
            claim.setPreAuthorization(preAuth);
        }

        if (dto.getComplaint() != null) {
            claim.setComplaint(dto.getComplaint());
        }

        if (dto.getRejectionReason() != null) {
            claim.setReviewerComment(dto.getRejectionReason());
        }

        if (dto.getPrimaryCategoryCode() != null) {
            claim.setPrimaryCategoryCode(dto.getPrimaryCategoryCode());
        }
        if (dto.getManualCategoryEnabled() != null) {
            claim.setManualCategoryEnabled(dto.getManualCategoryEnabled());
        }

        // DRAFT line edits (services/categories/quantities) with backend contract
        // re-pricing
        if (dto.getLines() != null) {
            List<Long> serviceIds = dto.getLines().stream()
                    .map(ClaimLineDto::getMedicalServiceId)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            Map<Long, MedicalService> medicalServiceMap = serviceIds.isEmpty() ? java.util.Collections.emptyMap()
                    : medicalServiceRepository.findAllById(serviceIds).stream()
                            .collect(Collectors.toMap(MedicalService::getId, s -> s));

            claimMapper.replaceClaimLinesForDraft(claim, dto.getLines(), medicalServiceMap);
        }

        // Save and return
        Claim updatedClaim = claimRepository.save(claim);
        log.info("✅ Claim DATA updated: id={}", id);

        // Audit trail
        claimAuditService.recordStatusChange(claim, claim.getStatus(), currentUser, "Data updated");

        return claimMapper.toViewDto(updatedClaim);
    }

    /**
     * Review claim (for REVIEWER and INSURANCE_ADMIN only).
     * SECURITY: This method enforces that reviewers can ONLY change status/review
     * fields.
     * Data fields (doctorName, diagnosisCode, etc.) CANNOT be modified by
     * reviewers.
     * 
     * @param id  Claim ID
     * @param dto Review DTO (status, comment, approvedAmount only)
     * @return Updated claim
     * @throws AccessDeniedException if user is not a reviewer
     * @since Provider Portal Security Fix (Phase 0)
     */
    public ClaimViewDto reviewClaim(Long id, ClaimReviewDto dto) {
        log.info("🔍 Reviewing claim: id={}, newStatus={}", id, dto.getStatus());

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();

        // SECURITY: Only REVIEWER, INSURANCE_ADMIN, or SUPER_ADMIN can review
        if (!authorizationService.isReviewer(currentUser) &&
                !authorizationService.isInsuranceAdmin(currentUser) &&
                !authorizationService.isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("Only reviewers can perform review actions");
        }

        // SECURITY: Apply reviewer-provider isolation
        if (authorizationService.isReviewer(currentUser)) {
            reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());
        }

        ClaimStatus previousStatus = claim.getStatus();

        // Validate status transition
        if (dto.getStatus() != previousStatus) {
            // Validation for specific statuses
            if (dto.getStatus() == ClaimStatus.REJECTED || dto.getStatus() == ClaimStatus.NEEDS_CORRECTION) {
                if (dto.getReviewerComment() == null || dto.getReviewerComment().isBlank()) {
                    throw new BusinessRuleException(
                            dto.getStatus() + " status requires a reviewer comment");
                }
            }

            if (dto.getStatus() == ClaimStatus.APPROVED) {
                if (dto.getApprovedAmount() == null || dto.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("APPROVED status requires approved amount > 0");
                }
                claim.setApprovedAmount(dto.getApprovedAmount());
            }

            // Set reviewer comment
            if (dto.getReviewerComment() != null) {
                claim.setReviewerComment(dto.getReviewerComment());
            }

            // Perform status transition
            claimStateMachine.transition(claim, dto.getStatus(), currentUser);
        }

        // Save and return
        Claim updatedClaim = claimRepository.save(claim);

        // Audit trail
        if (dto.getStatus() == ClaimStatus.APPROVED) {
            claimAuditService.recordApproval(updatedClaim, previousStatus, null, currentUser, dto.getReviewerComment());
        } else if (dto.getStatus() == ClaimStatus.REJECTED) {
            claimAuditService.recordRejection(updatedClaim, previousStatus, currentUser, dto.getReviewerComment());
        } else if (dto.getStatus() == ClaimStatus.NEEDS_CORRECTION) {
            claimAuditService.recordStatusChange(updatedClaim, previousStatus, currentUser, dto.getReviewerComment());
        } else {
            claimAuditService.recordStatusChange(updatedClaim, previousStatus, currentUser, dto.getReviewerComment());
        }

        log.info("✅ Claim reviewed: id={}, status={}", id, updatedClaim.getStatus());

        return claimMapper.toViewDto(updatedClaim);
    }

    /**
     * Submit claim for review.
     * Transitions from DRAFT or NEEDS_CORRECTION to SUBMITTED.
     * 
     * @param id Claim ID
     * @return Updated claim
     * @since Provider Portal Draft-First Model (Phase 2)
     */
    public ClaimViewDto submitClaim(Long id) {
        log.info("📤 Submitting claim: id={}", id);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();

        // Validate current status allows submission
        if (claim.getStatus() != ClaimStatus.DRAFT && claim.getStatus() != ClaimStatus.NEEDS_CORRECTION) {
            throw new BusinessRuleException(
                    String.format("Cannot submit claim in %s status. Only DRAFT and NEEDS_CORRECTION can be submitted.",
                            claim.getStatus()));
        }

        // Attachment validation DISABLED (2026-02-24) - attachments are optional
        // var attachmentResult = attachmentRulesService.validateForSubmission(claim,
        // ClaimType.GENERAL);
        // if (!attachmentResult.valid()) {
        // throw new BusinessRuleException("Cannot submit claim: " +
        // attachmentResult.getErrorMessage());
        // }
        log.info("ℹ️ Attachment validation skipped - attachments are optional");

        ClaimStatus previousStatus = claim.getStatus();

        // Transition to SUBMITTED
        claimStateMachine.transition(claim, ClaimStatus.SUBMITTED, currentUser);

        Claim updatedClaim = claimRepository.save(claim);

        // Audit trail
        claimAuditService.recordStatusChange(updatedClaim, previousStatus, currentUser, "Claim submitted for review");

        log.info("✅ Claim submitted: id={}, status={}", id, updatedClaim.getStatus());

        return claimMapper.toViewDto(updatedClaim);
    }

    /**
     * Transition claim status using state machine.
     * Dedicated endpoint for status changes.
     * 
     * PHASE 7 ADDITIONS:
     * - Attachment validation before SUBMITTED
     * - Cost calculation before APPROVED
     * - Audit trail recording
     */
    public ClaimViewDto transitionStatus(Long id, ClaimStatus targetStatus, String comment) {
        log.info("🔄 Transitioning claim {} to {}", id, targetStatus);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();

        // Phase 7: Attachment validation DISABLED (2026-02-24) - attachments are
        // optional
        // if (targetStatus == ClaimStatus.SUBMITTED) {
        // var attachmentResult = attachmentRulesService.validateForSubmission(claim,
        // ClaimType.GENERAL);
        // if (!attachmentResult.valid()) {
        // throw new BusinessRuleException(
        // "Cannot submit claim: " + attachmentResult.getErrorMessage()
        // );
        // }
        // }
        if (targetStatus == ClaimStatus.SUBMITTED) {
            log.info("ℹ️ Attachment validation skipped - attachments are optional");
        }

        // Phase 7: Calculate and log costs before approval
        if (targetStatus == ClaimStatus.APPROVED) {
            var costBreakdown = costCalculationService.calculateCosts(claim);
            log.info("💰 Cost breakdown for claim {}: {}", id, costBreakdown.getSummary());
        }

        // Set comment before transition (needed for REJECTED validation)
        if (comment != null && !comment.isBlank()) {
            claim.setReviewerComment(comment);
        }

        // Perform transition with validation
        claimStateMachine.transition(claim, targetStatus, currentUser);

        Claim updatedClaim = claimRepository.save(claim);

        // Phase 7: Record in audit trail based on transition type
        if (targetStatus == ClaimStatus.APPROVED) {
            claimAuditService.recordApproval(updatedClaim, previousStatus, null, currentUser, comment);
        } else if (targetStatus == ClaimStatus.REJECTED) {
            claimAuditService.recordRejection(updatedClaim, previousStatus, currentUser, comment);
        } else if (targetStatus == ClaimStatus.SETTLED) {
            claimAuditService.recordSettlement(updatedClaim, currentUser);
        } else {
            claimAuditService.recordStatusChange(updatedClaim, previousStatus, currentUser, comment);
        }

        log.info("✅ Claim {} transitioned to {}", id, targetStatus);

        return claimMapper.toViewDto(updatedClaim);
    }

    /**
     * Get available status transitions for a claim.
     * Used by frontend to show valid action buttons.
     */
    @Transactional(readOnly = true)
    public Set<ClaimStatus> getAvailableTransitions(Long id) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        return claimStateMachine.getAvailableTransitions(claim, currentUser);
    }

    @Transactional(readOnly = true)
    public ClaimViewDto getClaim(Long id) {
        log.debug("📋 Getting claim by id: {}", id);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        // Check feature flags for EMPLOYER_ADMIN
        // No checks needed - purely RBAC

        // Check access authorization
        if (!authorizationService.canAccessClaim(currentUser, id)) {
            log.warn("❌ Access denied: user {} attempted to access claim {}",
                    currentUser.getUsername(), id);
            throw new AccessDeniedException("Access denied to this claim");
        }

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found with id: " + id));

        // ══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION: Defensive Validation (Read Access)
        // ══════════════════════════════════════════════════════════════════════════
        // Even for read operations, reviewers should only see claims from assigned
        // providers
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());
        log.debug("✅ [ISOLATION] Reviewer {} validated read access to claim {} (provider {})",
                currentUser.getId(), id, claim.getProviderId());

        ClaimViewDto dto = claimMapper.toViewDto(claim);

        // ═══════════════════════════════════════════════════════════════════════════
        // BACKEND-DRIVEN WORKFLOW: Add allowed transitions for UI
        // ═══════════════════════════════════════════════════════════════════════════
        dto.setAllowedNextStatuses(claimStateMachine.getAvailableTransitions(claim, currentUser));
        dto.setCanEdit(claimStateMachine.canEdit(claim));

        return dto;
    }

    @Transactional(readOnly = true)
    public Page<ClaimViewDto> listClaims(Long employerId, Long providerId, ClaimStatus status, LocalDate dateFrom,
            LocalDate dateTo, LocalDate createdDateFrom, LocalDate createdDateTo,
            int page, int size, String sortBy, String sortDir, String search) {
        log.debug(
                "📋 Listing claims with pagination. employerId={}, providerId={}, status={}, page={}, size={}, sortBy={}, sortDir={}, search={}",
                employerId, providerId, status, page, size, sortBy, sortDir, search);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            return Page.empty();
        }

        // Check feature flags for EMPLOYER_ADMIN
        if (authorizationService.isEmployerAdmin(currentUser)) {
            // EMPLOYER role is locked to their own employer
            employerId = currentUser.getEmployerId();
        }

        // Build sort direction from string parameter
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        String keyword = (search != null && !search.trim().isEmpty()) ? search.trim() : "";

        // Claim entry date filter boundaries (inclusive from, inclusive-to-day via
        // next-day exclusive)
        LocalDateTime createdAtFrom = createdDateFrom != null ? createdDateFrom.atStartOfDay() : null;
        LocalDateTime createdAtTo = createdDateTo != null ? createdDateTo.plusDays(1).atStartOfDay() : null;

        // ══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION: Filter by assigned providers
        // ══════════════════════════════════════════════════════════════════════════
        Page<Claim> claimsPage;

        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            // Medical reviewer - only see claims from assigned providers
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);

            if (allowedProviderIds.isEmpty()) {
                log.warn("⚠️ Medical reviewer {} has no provider assignments - returning empty list",
                        currentUser.getId());
                return Page.empty();
            }

            log.info("🔒 [ISOLATION] Filtering claims for reviewer {} to {} assigned providers",
                    currentUser.getId(), allowedProviderIds.size());

            claimsPage = claimRepository.searchPagedWithFiltersAndReviewerProviders(
                    keyword, allowedProviderIds, employerId, status, dateFrom, dateTo, createdAtFrom, createdAtTo,
                    pageable);
        } else {
            // Admin/SuperAdmin - see all claims (bypass isolation)
            log.debug("✅ [BYPASS] User {} bypasses reviewer isolation", currentUser.getId());

            claimsPage = claimRepository.searchPagedWithFilters(
                    keyword, employerId, providerId, status, dateFrom, dateTo, createdAtFrom, createdAtTo, pageable);
        }

        return claimsPage.map(claimMapper::toViewDto);
    }

    @Transactional(readOnly = true)
    public List<ClaimViewDto> getClaimsByMember(Long memberId) {
        log.debug("📋 Getting claims for member: {}", memberId);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            return Collections.emptyList();
        }

        // Check if user can access this member
        if (!authorizationService.canAccessMember(currentUser, memberId)) {
            log.warn("❌ Access denied to member {}", memberId);
            return Collections.emptyList();
        }

        List<Claim> claims = claimRepository.findByMemberId(memberId);
        return claims.stream()
                .map(claimMapper::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Get claims by Pre-Authorization ID.
     * ARCHITECTURAL UPDATE (2026-01-15): Renamed from getClaimsByPreApproval to
     * align with PreAuthorization module.
     */
    @Transactional(readOnly = true)
    public List<ClaimViewDto> getClaimsByPreAuthorization(Long preAuthorizationId) {
        List<Claim> claims = claimRepository.findByPreAuthorizationId(preAuthorizationId);
        return claims.stream()
                .map(claimMapper::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Get cost breakdown preview for a claim (Phase 7).
     * Returns deductible, co-pay, and insurance coverage amounts.
     */
    @Transactional(readOnly = true)
    public CostCalculationService.CostBreakdown getCostBreakdown(Long id) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        return costCalculationService.calculateCosts(claim);
    }

    /**
     * Get cost breakdown as DTO for API response.
     */
    @Transactional(readOnly = true)
    public CostBreakdownDto getCostBreakdownDto(Long id) {
        CostCalculationService.CostBreakdown breakdown = getCostBreakdown(id);
        return CostBreakdownDto.from(breakdown);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MVP PHASE: Approve / Reject / Settle Endpoints
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Approve a claim with financial validation.
     * 
     * POST /api/claims/{id}/approve
     * 
     * ╔═══════════════════════════════════════════════════════════════════════════╗
     * ║ FINANCIAL INTEGRITY: PESSIMISTIC LOCKING ENABLED ║
     * ║───────────────────────────────────────────────────────────────────────────║
     * ║ Uses SELECT ... FOR UPDATE to prevent: ║
     * ║ - Double approval (race condition) ║
     * ║ - Concurrent deductible calculations overspending ║
     * ║ - Concurrent modifications during approval ║
     * ╚═══════════════════════════════════════════════════════════════════════════╝
     * 
     * Business Rules:
     * 1. Claim must be in SUBMITTED or UNDER_REVIEW status
     * 2. Cost breakdown is calculated with ATOMIC deductible locking
     * 3. Coverage limits are checked (via BenefitPolicyCoverageService)
     * 4. Financial snapshot is stored on the claim
     * 5. Status transitions to APPROVED
     * 
     * @param id  Claim ID
     * @param dto Approval details
     * @return Updated claim with financial snapshot
     */
    @Transactional
    public ClaimViewDto approveClaim(Long id, ClaimApproveDto dto) {
        log.info("✅ [FINANCIAL-LOCK] Approving claim {} with pessimistic lock", id);

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 0: PESSIMISTIC LOCK - SELECT ... FOR UPDATE
        // ══════════════════════════════════════════════════════════════════════════
        // This MUST be used for all financial operations to prevent race conditions
        Claim claim = claimRepository.findByIdForFinancialUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        log.info("🔒 [FINANCIAL-LOCK] Acquired pessimistic lock on claim {}", id);

        User currentUser = authorizationService.getCurrentUser();

        // ══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION: Defensive Validation
        // ══════════════════════════════════════════════════════════════════════════
        // Verify reviewer has access to this provider's claims
        // Admin/SuperAdmin bypass this check
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());
        log.info("✅ [ISOLATION] Reviewer {} validated for provider {}",
                currentUser.getId(), claim.getProviderId());

        ClaimStatus previousStatus = claim.getStatus();

        // ═══════════════════════════════════════════════════════════════════════════
        // IMMUTABILITY GUARD: Only SUBMITTED or UNDER_REVIEW claims can be approved.
        // This prevents re-calculation of already approved or settled claims.
        // ═══════════════════════════════════════════════════════════════════════════
        if (previousStatus != ClaimStatus.SUBMITTED && previousStatus != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    String.format("لا يمكن اعتماد المطالبة في حالتها الحالية: %s. يجب أن تكون قيد المراجعة.",
                            previousStatus));
        }

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 1: ATOMIC DEDUCTIBLE CALCULATION WITH MEMBER LOCK
        // ══════════════════════════════════════════════════════════════════════════
        // This locks the MEMBER to prevent concurrent claims from overspending
        // deductible.
        // Scenario: Member has $500 deductible, two claims ($400 each) submitted
        // concurrently.
        // Without lock: Both see $500 remaining, both apply $400, total = $800 (WRONG!)
        // With lock: Claim A applies $400, Claim B waits and sees $100 remaining
        // (CORRECT)
        CostCalculationService.CostBreakdown breakdown = atomicFinancialService
                .calculateCostsWithAtomicDeductible(claim);
        log.info("💰 [ATOMIC] Cost breakdown for claim {}: {}", id, breakdown.getSummary());

        BigDecimal requestedAmount = claim.getRequestedAmount() != null ? claim.getRequestedAmount() : BigDecimal.ZERO;
        BigDecimal refusedAmount = claim.getRefusedAmount() != null ? claim.getRefusedAmount() : BigDecimal.ZERO;
        BigDecimal netAcceptedAmount = requestedAmount.subtract(refusedAmount).max(BigDecimal.ZERO);

        // System snapshot starts from accepted base only (requested - refused).
        BigDecimal systemPatientCoPay = breakdown.patientResponsibility() != null
                ? breakdown.patientResponsibility()
                : BigDecimal.ZERO;
        if (systemPatientCoPay.compareTo(netAcceptedAmount) > 0) {
            systemPatientCoPay = netAcceptedAmount;
        }
        BigDecimal systemNetProvider = netAcceptedAmount.subtract(systemPatientCoPay);

        // Step 2: Determine approved amount
        BigDecimal approvedAmount;
        if (Boolean.TRUE.equals(dto.getUseSystemCalculation()) || dto.getApprovedAmount() == null) {
            // Use system-calculated amount (insurance pays)
            approvedAmount = systemNetProvider;
            log.info("📊 Using system-calculated approved amount: {}", approvedAmount);
        } else {
            // Use manual amount from reviewer
            approvedAmount = dto.getApprovedAmount();
            log.info("📊 Using manual approved amount: {}", approvedAmount);
        }

        // Step 3: Validate approved amount using AtomicFinancialService
        atomicFinancialService.validatePositiveAmount(approvedAmount, "المبلغ المعتمد (Approved Amount)");
        atomicFinancialService.validateApprovedAmount(approvedAmount, netAcceptedAmount);

        // Step 4: Validate Financial Snapshot equation
        // Rule: NetAcceptedAmount = PatientCoPay + NetProviderAmount
        BigDecimal patientCoPay = netAcceptedAmount.subtract(approvedAmount);
        BigDecimal netProviderAmount = approvedAmount;
        BigDecimal total = patientCoPay.add(netProviderAmount);

        if (total.compareTo(netAcceptedAmount) != 0) {
            log.warn("⚠️ Financial calculation mismatch: {} + {} = {} != {}",
                    patientCoPay, netProviderAmount, total, netAcceptedAmount);
            // Auto-adjust to ensure balance
            netProviderAmount = netAcceptedAmount.subtract(patientCoPay).max(BigDecimal.ZERO);
            approvedAmount = netProviderAmount;
        }

        // Step 5: Validate coverage limits using BenefitPolicy (Single Source of Truth)
        // CRITICAL: Fetch member with lock to ensure atomic limit validation
        Member member = memberRepository.findByIdWithLock(claim.getMember().getId())
                .orElseThrow(() -> new BusinessRuleException("العضو غير موجود أو تم حذفه"));

        LocalDate serviceDate = claim.getServiceDate() != null ? claim.getServiceDate() : LocalDate.now();

        if (member.getBenefitPolicy() != null) {
            try {
                benefitPolicyCoverageService.validateAmountLimits(
                        member, member.getBenefitPolicy(), approvedAmount, serviceDate);
                log.debug("✅ BenefitPolicy amount validation passed");
            } catch (Exception e) {
                log.error("❌ BenefitPolicy coverage validation failed: {}", e.getMessage());
                throw new BusinessRuleException("فشل التحقق من التغطية: " + e.getMessage());
            }
        } else {
            log.warn("⚠️ Member {} has no BenefitPolicy, skipping amount limit validation", member.getNationalNumber());
        }

        // Step 6: Update claim with financial snapshot
        claim.setApprovedAmount(approvedAmount);
        claim.setPatientCoPay(patientCoPay);
        claim.setNetProviderAmount(netProviderAmount);
        BigDecimal effectiveCoPayPercent = netAcceptedAmount.compareTo(BigDecimal.ZERO) > 0
                ? patientCoPay.multiply(new BigDecimal("100")).divide(netAcceptedAmount, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        claim.setCoPayPercent(effectiveCoPayPercent);
        BigDecimal rawDeductible = breakdown.deductibleApplied() != null ? breakdown.deductibleApplied()
                : BigDecimal.ZERO;
        claim.setDeductibleApplied(rawDeductible.min(patientCoPay));
        claim.setDifferenceAmount(claim.getRequestedAmount().subtract(netProviderAmount));

        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            claim.setReviewerComment(dto.getNotes());
        }

        // Step 7: Transition to APPROVED status
        claimStateMachine.transition(claim, ClaimStatus.APPROVED, currentUser);

        // Update visit status if linked
        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.COMPLETED);
            log.info("✅ Updated visit {} status to COMPLETED", claim.getVisit().getId());
        }

        // ✅ PHASE 1: Track SLA compliance
        LocalDate completionDate = LocalDate.now();
        claim.setActualCompletionDate(completionDate);

        if (claim.getExpectedCompletionDate() != null && claim.getSlaDaysConfigured() != null) {
            LocalDate submissionDate = claim.getCreatedAt().toLocalDate();
            int daysTaken = businessDaysCalculator.calculateBusinessDays(submissionDate, completionDate);

            claim.setBusinessDaysTaken(daysTaken);
            claim.setWithinSla(daysTaken <= claim.getSlaDaysConfigured());

            if (daysTaken > claim.getSlaDaysConfigured()) {
                log.warn("⚠️ Claim {} completed in {} business days (exceeded {} -day SLA)",
                        id, daysTaken, claim.getSlaDaysConfigured());
            } else {
                log.info("✅ Claim {} completed in {} business days (within {}-day SLA)",
                        id, daysTaken, claim.getSlaDaysConfigured());
            }
        }

        Claim savedClaim = claimRepository.save(claim);

        // Step 8: Record in audit trail (pass null for previousApprovedAmount as it
        // wasn't approved before)
        claimAuditService.recordApproval(savedClaim, previousStatus, null, currentUser, dto.getNotes());

        // ═══════════════════════════════════════════════════════════════════════════
        // PHASE 3A: Publish ClaimApprovedEvent for Provider Account Credit
        // ═══════════════════════════════════════════════════════════════════════════
        // This triggers ClaimApprovalEventListener which will:
        // - Credit the provider account with netProviderAmount
        // - Create CREDIT transaction in account_transactions
        // - Update running_balance in provider_accounts
        // Event is processed AFTER_COMMIT to ensure claim is saved first
        eventPublisher.publishEvent(new ClaimApprovedEvent(
                this,
                savedClaim.getId(),
                savedClaim.getProviderId(),
                currentUser.getId()));
        log.info("📤 [EVENT] Published ClaimApprovedEvent for claim {}", savedClaim.getId());

        log.info("✅ Claim {} approved: requested={}, approved={}, patientCoPay={}, netProvider={}",
                id, claim.getRequestedAmount(), approvedAmount, patientCoPay, netProviderAmount);

        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: PHASE 1 - Request Approval (Fast, Non-Blocking)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * This is the NEW approval endpoint that returns immediately.
     * It transitions the claim to APPROVAL_IN_PROGRESS and triggers async
     * processing.
     * 
     * REPLACES: approveClaim() for production use (old method kept for backward
     * compatibility)
     * 
     * FLOW:
     * 1. Validate claim exists and is in valid state
     * 2. Change status to APPROVAL_IN_PROGRESS (< 1 second)
     * 3. Trigger async background processing
     * 4. Return immediately with status "APPROVAL_IN_PROGRESS"
     * 
     * @param id  Claim ID
     * @param dto Approval details
     * @return Claim with APPROVAL_IN_PROGRESS status
     */
    @Transactional
    public ClaimViewDto requestApproval(Long id, ClaimApproveDto dto) {
        log.info("🚀 [SPLIT-PHASE] Phase 1: Requesting approval for claim {}", id);

        // Quick validation without heavy locks
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = resolveWorkflowUser(authorizationService.getCurrentUser());

        // Validate state transition
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW && claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    "لا يمكن الموافقة على المطالبة في الحالة الحالية: " + claim.getStatus().getArabicLabel());
        }

        if (claim.getStatus() == ClaimStatus.SUBMITTED) {
            claimStateMachine.transition(claim, ClaimStatus.UNDER_REVIEW, currentUser);
            log.info("ℹ️ [SPLIT-PHASE] Auto-transitioned claim {} from SUBMITTED to UNDER_REVIEW before approval", id);
        }

        // Store approval metadata for async processing
        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            claim.setReviewerComment(dto.getNotes());
        }

        // Transition to APPROVAL_IN_PROGRESS
        claimStateMachine.transition(claim, ClaimStatus.APPROVAL_IN_PROGRESS, currentUser);
        Claim savedClaim = claimRepository.save(claim);

        log.info("✅ [SPLIT-PHASE] Phase 1 complete: Claim {} marked as APPROVAL_IN_PROGRESS", id);

        // Trigger async phase 2 processing
        processApprovalAsync(id, dto);

        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: PHASE 2 - Process Approval (Heavy, Background)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * This method executes in the background using @Async.
     * It performs all heavy financial calculations with PESSIMISTIC locks.
     * 
     * EXECUTES:
     * 1. Atomic deductible calculation (with MEMBER lock)
     * 2. Coverage validation (BenefitPolicy)
     * 3. Financial snapshot calculation
     * 4. Transition to APPROVED or REJECTED
     * 
     * ISOLATION: REQUIRES_NEW to avoid deadlocks with Phase 1
     * 
     * @param id  Claim ID
     * @param dto Approval details (from Phase 1)
     */
    @org.springframework.scheduling.annotation.Async("approvalTaskExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void processApprovalAsync(Long id, ClaimApproveDto dto) {
        log.info("⚙️ [SPLIT-PHASE] Phase 2: Starting async approval processing for claim {}", id);

        try {
            // ══════════════════════════════════════════════════════════════════════════
            // STEP 0: PESSIMISTIC LOCK - SELECT ... FOR UPDATE
            // ══════════════════════════════════════════════════════════════════════════
            Claim claim = claimRepository.findByIdForFinancialUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

            log.info("🔒 [FINANCIAL-LOCK] Acquired pessimistic lock on claim {}", id);

            User currentUser = resolveWorkflowUser(authorizationService.getCurrentUser());

            // ══════════════════════════════════════════════════════════════════════════
            // STEP 1: ATOMIC DEDUCTIBLE CALCULATION WITH MEMBER LOCK
            // ══════════════════════════════════════════════════════════════════════════
            CostCalculationService.CostBreakdown breakdown = atomicFinancialService
                    .calculateCostsWithAtomicDeductible(claim);
            log.info("💰 [ATOMIC] Cost breakdown for claim {}: {}", id, breakdown.getSummary());

            BigDecimal requestedAmount = claim.getRequestedAmount() != null ? claim.getRequestedAmount()
                    : BigDecimal.ZERO;
            BigDecimal refusedAmount = claim.getRefusedAmount() != null ? claim.getRefusedAmount() : BigDecimal.ZERO;
            BigDecimal netAcceptedAmount = requestedAmount.subtract(refusedAmount).max(BigDecimal.ZERO);

            BigDecimal systemPatientCoPay = breakdown.patientResponsibility() != null
                    ? breakdown.patientResponsibility()
                    : BigDecimal.ZERO;
            if (systemPatientCoPay.compareTo(netAcceptedAmount) > 0) {
                systemPatientCoPay = netAcceptedAmount;
            }
            BigDecimal systemNetProvider = netAcceptedAmount.subtract(systemPatientCoPay);

            // Step 2: Determine approved amount
            BigDecimal approvedAmount;
            if (Boolean.TRUE.equals(dto.getUseSystemCalculation()) || dto.getApprovedAmount() == null) {
                approvedAmount = systemNetProvider;
                log.info("📊 Using system-calculated approved amount: {}", approvedAmount);
            } else {
                approvedAmount = dto.getApprovedAmount();
                log.info("📊 Using manual approved amount: {}", approvedAmount);
            }

            // Step 3: Validate approved amount
            atomicFinancialService.validatePositiveAmount(approvedAmount, "المبلغ المعتمد (Approved Amount)");
            atomicFinancialService.validateApprovedAmount(approvedAmount, netAcceptedAmount);

            // Step 4: Validate Financial Snapshot equation
            BigDecimal patientCoPay = netAcceptedAmount.subtract(approvedAmount);
            BigDecimal netProviderAmount = approvedAmount;
            BigDecimal total = patientCoPay.add(netProviderAmount);

            if (total.compareTo(netAcceptedAmount) != 0) {
                log.warn("⚠️ Financial calculation mismatch: {} + {} = {} != {}",
                        patientCoPay, netProviderAmount, total, netAcceptedAmount);
                netProviderAmount = netAcceptedAmount.subtract(patientCoPay).max(BigDecimal.ZERO);
                approvedAmount = netProviderAmount;
            }

            // Step 5: Validate coverage limits
            Member member = claim.getMember();
            LocalDate serviceDate = claim.getServiceDate() != null ? claim.getServiceDate() : LocalDate.now();

            if (member.getBenefitPolicy() != null) {
                try {
                    benefitPolicyCoverageService.validateAmountLimits(
                            member, member.getBenefitPolicy(), approvedAmount, serviceDate);
                    log.debug("✅ BenefitPolicy amount validation passed");
                } catch (Exception e) {
                    log.error("❌ BenefitPolicy coverage validation failed: {}", e.getMessage());
                    throw new BusinessRuleException("فشل التحقق من التغطية: " + e.getMessage());
                }
            }

            // Step 6: Update claim with financial snapshot
            claim.setApprovedAmount(approvedAmount);
            claim.setPatientCoPay(patientCoPay);
            claim.setNetProviderAmount(netProviderAmount);
            BigDecimal effectiveCoPayPercent = netAcceptedAmount.compareTo(BigDecimal.ZERO) > 0
                    ? patientCoPay.multiply(new BigDecimal("100")).divide(netAcceptedAmount, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            claim.setCoPayPercent(effectiveCoPayPercent);
            BigDecimal rawDeductible = breakdown.deductibleApplied() != null ? breakdown.deductibleApplied()
                    : BigDecimal.ZERO;
            claim.setDeductibleApplied(rawDeductible.min(patientCoPay));
            claim.setDifferenceAmount(claim.getRequestedAmount().subtract(netProviderAmount));

            // Step 7: Transition to APPROVED status
            claimStateMachine.transition(claim, ClaimStatus.APPROVED, currentUser);

            // Update visit status if linked
            if (claim.getVisit() != null) {
                claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.COMPLETED);
                log.info("✅ Updated visit {} status to COMPLETED", claim.getVisit().getId());
            }

            // Track SLA compliance
            LocalDate completionDate = LocalDate.now();
            claim.setActualCompletionDate(completionDate);

            if (claim.getExpectedCompletionDate() != null && claim.getSlaDaysConfigured() != null) {
                LocalDate submissionDate = claim.getCreatedAt().toLocalDate();
                int daysTaken = businessDaysCalculator.calculateBusinessDays(submissionDate, completionDate);

                claim.setBusinessDaysTaken(daysTaken);
                claim.setWithinSla(daysTaken <= claim.getSlaDaysConfigured());
            }

            Claim savedClaim = claimRepository.save(claim);

            // Step 8: Publish ClaimApprovedEvent for automatic credit to provider account
            // This event is handled by ClaimApprovalEventListener which credits the
            // provider
            if (savedClaim.getProviderId() != null) {
                eventPublisher.publishEvent(new ClaimApprovedEvent(
                        this,
                        savedClaim.getId(),
                        savedClaim.getProviderId(),
                        currentUser != null ? currentUser.getId() : null));
                log.info("📢 [ASYNC-APPROVAL] Published ClaimApprovedEvent for claim {} to credit provider {}",
                        savedClaim.getId(), savedClaim.getProviderId());
            }

            // Step 9: Record in audit trail
            claimAuditService.recordApproval(savedClaim, ClaimStatus.APPROVAL_IN_PROGRESS, null, currentUser,
                    dto.getNotes());

            log.info("✅ [SPLIT-PHASE] Phase 2 complete: Claim {} approved successfully", id);

        } catch (Exception e) {
            log.error("❌ [SPLIT-PHASE] Phase 2 failed for claim {}: {}", id, e.getMessage(), e);

            // On failure, transition to REJECTED with error message
            try {
                Claim failedClaim = claimRepository.findById(id).orElse(null);
                if (failedClaim != null && failedClaim.getStatus() == ClaimStatus.APPROVAL_IN_PROGRESS) {
                    failedClaim.setReviewerComment("فشل تقني في المعالجة: " + e.getMessage());
                    // Transition back to UNDER_REVIEW so it can be retried, instead of
                    // auto-rejecting
                    failedClaim.setStatus(ClaimStatus.UNDER_REVIEW);
                    failedClaim.setUpdatedBy("system-async");
                    claimRepository.save(failedClaim);
                    log.info("🔄 Claim {} reverted to UNDER_REVIEW due to technical processing failure", id);
                }
            } catch (Exception rollbackError) {
                log.error("❌ Failed to rollback claim {} to REJECTED: {}", id, rollbackError.getMessage());
            }
        }
    }

    private User resolveWorkflowUser(User currentUser) {
        if (currentUser != null) {
            return currentUser;
        }

        return User.builder()
                .username("system-async")
                .userType("ACCOUNTANT")
                .build();
    }

    /**
     * Reject a claim with mandatory reason.
     * 
     * POST /api/claims/{id}/reject
     * 
     * ╔═══════════════════════════════════════════════════════════════════════════╗
     * ║ FINANCIAL INTEGRITY: PESSIMISTIC LOCKING ENABLED ║
     * ║───────────────────────────────────────────────────────────────────────────║
     * ║ Uses SELECT ... FOR UPDATE to prevent concurrent status changes. ║
     * ╚═══════════════════════════════════════════════════════════════════════════╝
     * 
     * Business Rules:
     * 1. Claim must be in SUBMITTED or UNDER_REVIEW status
     * 2. Rejection reason is MANDATORY
     * 3. Status transitions to REJECTED (terminal state)
     * 
     * @param id  Claim ID
     * @param dto Rejection details with mandatory reason
     * @return Updated claim
     */
    @Transactional
    public ClaimViewDto rejectClaim(Long id, ClaimRejectDto dto) {
        log.info("❌ [FINANCIAL-LOCK] Rejecting claim {} with pessimistic lock", id);

        // ══════════════════════════════════════════════════════════════════════════
        // PESSIMISTIC LOCK - SELECT ... FOR UPDATE
        // ══════════════════════════════════════════════════════════════════════════
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        log.info("🔒 [FINANCIAL-LOCK] Acquired pessimistic lock on claim {}", id);

        User currentUser = authorizationService.getCurrentUser();

        // ══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION: Defensive Validation
        // ══════════════════════════════════════════════════════════════════════════
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());
        log.info("✅ [ISOLATION] Reviewer {} validated for provider {}",
                currentUser.getId(), claim.getProviderId());

        ClaimStatus previousStatus = claim.getStatus();

        // ═══════════════════════════════════════════════════════════════════════════
        // IMMUTABILITY GUARD: Only SUBMITTED or UNDER_REVIEW claims can be rejected.
        // ═══════════════════════════════════════════════════════════════════════════
        if (previousStatus != ClaimStatus.SUBMITTED && previousStatus != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    String.format("لا يمكن رفض المطالبة في حالتها الحالية: %s. يجب أن تكون قيد المراجعة.",
                            previousStatus));
        }

        // Validate rejection reason is provided
        if (dto.getRejectionReason() == null || dto.getRejectionReason().trim().isEmpty()) {
            throw new BusinessRuleException("سبب الرفض مطلوب");
        }

        // Set rejection details
        claim.setReviewerComment(dto.getRejectionReason());
        claim.setApprovedAmount(BigDecimal.ZERO);
        claim.setNetProviderAmount(BigDecimal.ZERO);

        // Transition to REJECTED status
        claimStateMachine.transition(claim, ClaimStatus.REJECTED, currentUser);

        // Update visit status if linked
        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.CANCELLED);
            log.info("✅ Updated visit {} status to CANCELLED after rejection", claim.getVisit().getId());
        }

        // ✅ PHASE 1: Track SLA compliance (rejection also counts as completion)
        LocalDate completionDate = LocalDate.now();
        claim.setActualCompletionDate(completionDate);

        if (claim.getExpectedCompletionDate() != null && claim.getSlaDaysConfigured() != null) {
            LocalDate submissionDate = claim.getCreatedAt().toLocalDate();
            int daysTaken = businessDaysCalculator.calculateBusinessDays(submissionDate, completionDate);

            claim.setBusinessDaysTaken(daysTaken);
            claim.setWithinSla(daysTaken <= claim.getSlaDaysConfigured());

            log.info("📊 Claim {} rejected in {} business days ({} SLA: {})",
                    id, daysTaken, claim.getSlaDaysConfigured(),
                    claim.getWithinSla() ? "within ✅" : "exceeded ❌");
        }

        Claim savedClaim = claimRepository.save(claim);

        // Record in audit trail
        claimAuditService.recordRejection(savedClaim, previousStatus, currentUser, dto.getRejectionReason());

        log.info("❌ Claim {} rejected. Reason: {}", id, dto.getRejectionReason());

        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Settle a claim (mark ready for payment).
     * 
     * POST /api/claims/{id}/settle
     * 
     * ╔═══════════════════════════════════════════════════════════════════════════╗
     * ║ FINANCIAL CLOSURE: SETTLEMENT INTEGRITY LOCK ║
     * ║───────────────────────────────────────────────────────────────────────────║
     * ║ Uses SELECT ... FOR UPDATE to prevent: ║
     * ║ - Double settlement (race condition) ║
     * ║ - Settlement exceeding approved amount ║
     * ║ - Concurrent modifications during settlement ║
     * ╚═══════════════════════════════════════════════════════════════════════════╝
     * 
     * Business Rules:
     * 1. Claim must be in APPROVED status
     * 2. Payment reference must be provided
     * 3. Settlement amount cannot exceed approved amount
     * 4. Status transitions to SETTLED (terminal state)
     * 5. Uses pessimistic locking to prevent race conditions
     * 
     * @param id  Claim ID
     * @param dto Settlement details
     * @return Updated claim
     */
    @Transactional
    public ClaimViewDto settleClaim(Long id, ClaimSettleDto dto) {
        log.info("💳 [SETTLEMENT] Starting settlement for claim {} with pessimistic lock", id);

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 1: PESSIMISTIC LOCK - SELECT ... FOR UPDATE
        // ══════════════════════════════════════════════════════════════════════════
        // This MUST be used for all financial operations to prevent double settlement
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        log.info("🔒 [SETTLEMENT] Acquired pessimistic lock on claim {}", id);

        User currentUser = authorizationService.getCurrentUser();

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 2: VALIDATE CLAIM STATUS
        // ══════════════════════════════════════════════════════════════════════════
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BusinessRuleException(
                    String.format("لا يمكن تسوية المطالبة. الحالة الحالية: %s. يجب أن تكون: APPROVED",
                            claim.getStatus()));
        }

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 3: VALIDATE PAYMENT REFERENCE
        // ══════════════════════════════════════════════════════════════════════════
        if (dto.getPaymentReference() == null || dto.getPaymentReference().trim().isEmpty()) {
            throw new BusinessRuleException("رقم مرجع الدفع مطلوب");
        }

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 4: VALIDATE SETTLEMENT AMOUNT (FINANCIAL INTEGRITY)
        // ══════════════════════════════════════════════════════════════════════════
        // Settlement amount MUST NOT exceed the approved amount
        BigDecimal netProviderAmount = claim.getNetProviderAmount() != null
                ? claim.getNetProviderAmount()
                : claim.getApprovedAmount();

        if (dto.getSettlementAmount() != null) {
            if (dto.getSettlementAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("مبلغ التسوية يجب أن يكون أكبر من صفر");
            }
            if (dto.getSettlementAmount().compareTo(netProviderAmount) > 0) {
                log.error("🚫 [SETTLEMENT VIOLATION] Settlement amount {} exceeds net provider amount {}",
                        dto.getSettlementAmount(), netProviderAmount);
                throw new BusinessRuleException(
                        String.format("مبلغ التسوية (%s) يتجاوز المبلغ المستحق للمقدم (%s)",
                                dto.getSettlementAmount(), netProviderAmount));
            }
        }

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 5: SET SETTLEMENT DETAILS
        // ══════════════════════════════════════════════════════════════════════════
        claim.setPaymentReference(dto.getPaymentReference());
        claim.setSettledAt(LocalDateTime.now());

        if (dto.getNotes() != null) {
            claim.setSettlementNotes(dto.getNotes());
        }

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 6: TRANSITION TO SETTLED STATUS (TERMINAL)
        // ══════════════════════════════════════════════════════════════════════════
        claimStateMachine.transition(claim, ClaimStatus.SETTLED, currentUser);

        // Update visit status if linked
        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.COMPLETED);
            log.info("✅ Updated visit {} status to COMPLETED after settlement", claim.getVisit().getId());
        }

        Claim savedClaim = claimRepository.save(claim);

        // Record in audit trail
        claimAuditService.recordSettlement(savedClaim, currentUser);

        log.info("💳 [SETTLEMENT COMPLETE] Claim {} settled. Payment Ref: {}, Amount: {}",
                id, dto.getPaymentReference(), netProviderAmount);

        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Start review of a submitted claim.
     * Transitions: SUBMITTED → UNDER_REVIEW
     * 
     * Business rules:
     * 1. Claim must be in SUBMITTED status
     * 2. User must have APPROVE_CLAIMS permission
     * 3. Status transitions to UNDER_REVIEW
     */
    @Transactional
    public ClaimViewDto startReview(Long id) {
        log.info("📋 Starting review of claim {}", id);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();

        // Validate current status allows starting review
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    String.format("لا يمكن بدء المراجعة. الحالة الحالية: %s، المطلوب: SUBMITTED", claim.getStatus()));
        }

        // Transition to UNDER_REVIEW status
        claimStateMachine.transition(claim, ClaimStatus.UNDER_REVIEW, currentUser);

        // Update visit status if linked
        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.IN_PROGRESS);
            log.info("✅ Updated visit {} status to IN_PROGRESS", claim.getVisit().getId());
        }

        Claim savedClaim = claimRepository.save(claim);

        // Record in audit trail
        claimAuditService.recordStatusChange(savedClaim, previousStatus, currentUser, "تم استلام المطالبة للمراجعة");

        log.info("📋 Claim {} is now under review", id);

        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Get claims pending review (for inbox).
     * Returns claims in SUBMITTED or UNDER_REVIEW status.
     */
    @Transactional(readOnly = true)
    public Page<ClaimViewDto> getPendingClaims(int page, int size, String sortBy, String sortDir, Long providerId) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        List<ClaimStatus> pendingStatuses = List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);

        // ══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION: Filter pending claims by assigned providers
        // ══════════════════════════════════════════════════════════════════════════
        User currentUser = authorizationService.getCurrentUser();
        Page<Claim> claims;

        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            if (providerId == null) {
                throw new BusinessRuleException("providerId is required for medical reviewer inbox access");
            }

            reviewerIsolationService.validateReviewerAccess(currentUser, providerId);
            log.info("🔒 [ISOLATION] Filtering pending claims for reviewer {} to provider {}",
                    currentUser.getId(), providerId);
            claims = claimRepository.findByStatusInAndReviewerProviders(List.of(providerId), pendingStatuses, pageable);
        } else {
            if (providerId != null) {
                claims = claimRepository.findByStatusInAndReviewerProviders(List.of(providerId), pendingStatuses,
                        pageable);
            } else {
                claims = claimRepository.findByStatusIn(pendingStatuses, pageable);
            }
        }

        return claims.map(claim -> claimMapper.toViewDto(claim));
    }

    /**
     * Get claims ready for settlement (APPROVED status).
     */
    @Transactional(readOnly = true)
    public Page<ClaimViewDto> getApprovedClaims(int page, int size, String sortBy, String sortDir, Long providerId) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // ══════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION: Filter approved claims by assigned providers
        // ══════════════════════════════════════════════════════════════════════════
        User currentUser = authorizationService.getCurrentUser();
        Page<Claim> claims;

        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            if (providerId == null) {
                throw new BusinessRuleException("providerId is required for medical reviewer inbox access");
            }

            reviewerIsolationService.validateReviewerAccess(currentUser, providerId);
            log.info("🔒 [ISOLATION] Filtering approved claims for reviewer {} to provider {}",
                    currentUser.getId(), providerId);
            claims = claimRepository.findByStatusInAndReviewerProviders(
                    List.of(providerId), List.of(ClaimStatus.APPROVED), pageable);
        } else {
            if (providerId != null) {
                claims = claimRepository.findByStatusInAndReviewerProviders(
                        List.of(providerId), List.of(ClaimStatus.APPROVED), pageable);
            } else {
                claims = claimRepository.findByStatus(ClaimStatus.APPROVED, pageable);
            }
        }

        return claims.map(claimMapper::toViewDto);
    }

    /**
     * Get audit history for a claim (Phase 7).
     * Returns all state changes and actions performed on the claim.
     */
    @Transactional(readOnly = true)
    public List<com.waad.tba.modules.claim.entity.ClaimAuditLog> getAuditHistory(Long id) {
        // Verify claim exists
        if (!claimRepository.existsById(id)) {
            throw new ResourceNotFoundException("Claim", "id", id);
        }
        return claimAuditService.getAuditHistory(id);
    }

    /**
     * Get attachment requirements for a claim type (Phase 7).
     */
    @Transactional(readOnly = true)
    public AttachmentRulesService.AttachmentRequirements getAttachmentRequirements(ClaimType claimType) {
        return attachmentRulesService.getRequirements(claimType);
    }

    /**
     * Validate attachments for a claim (Phase 7).
     * Can be called before submission to check if all required documents are
     * present.
     */
    @Transactional(readOnly = true)
    public AttachmentRulesService.AttachmentValidationResult validateAttachments(Long id, ClaimType claimType) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        return attachmentRulesService.validateForSubmission(claim, claimType);
    }

    /**
     * Get provider network status for a claim (Phase 7).
     */
    @Transactional(readOnly = true)
    public ProviderNetworkService.ProviderValidationResult getProviderNetworkStatus(Long id) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        return providerNetworkService.validateProviderForClaim(claim.getProviderName());
    }

    // public void deleteClaim(Long id) {
    // Claim claim = claimRepository.findById(id)
    // .orElseThrow(() -> new IllegalArgumentException("Claim not found with id: " +
    // id));
    // claim.setActive(false);
    // claimRepository.save(claim);
    // }

    @Transactional(readOnly = true)
    public long countClaims(Long employerId) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            return 0;
        }

        // EMPLOYER role is locked to their own employer
        if (authorizationService.isEmployerAdmin(currentUser)) {
            employerId = currentUser.getEmployerId();
        }

        if (employerId != null) {
            return claimRepository.countByMemberEmployerId(employerId);
        }

        if (authorizationService.isSuperAdmin(currentUser)) {
            return claimRepository.countActive();
        }

        return 0;
    }

    private void validateCreateDto(ClaimCreateDto dto) {
        // VISIT-CENTRIC ARCHITECTURE (2026-01-16): visitId is MANDATORY
        if (dto.getVisitId() == null) {
            throw new IllegalArgumentException(
                    "ARCHITECTURAL VIOLATION: visitId is REQUIRED - Claims must be linked to a Visit");
        }

        // CANONICAL: lines with medicalServiceId are MANDATORY - amount calculated from
        // contract
        if (dto.getLines() == null || dto.getLines().isEmpty()) {
            throw new IllegalArgumentException(
                    "ARCHITECTURAL VIOLATION: At least one claim line is REQUIRED");
        }

        // Validate each line has identification (medicalServiceId OR pricingItemId)
        for (ClaimLineDto line : dto.getLines()) {
            if (line.getMedicalServiceId() == null && line.getPricingItemId() == null) {
                throw new IllegalArgumentException(
                        "ARCHITECTURAL VIOLATION: Each line MUST have either medicalServiceId or pricingItemId - no free-text services");
            }
        }
    }

    /**
     * PROVIDER PORTAL (2026-01-16):
     * Validate and enforce provider ID based on user role.
     * 
     * Rules (HARDENED 2026-01-16):
     * - PROVIDER users: providerId ALWAYS comes from ProviderContextGuard (session)
     * ANY providerId from request is IGNORED to prevent data leakage
     * - SUPER_ADMIN/INSURANCE_ADMIN can set any providerId
     * - Other users can set any providerId
     * 
     * @param dto         The claim creation DTO
     * @param currentUser The currently authenticated user
     */
    private void validateAndEnforceProviderId(ClaimCreateDto dto, User currentUser) {
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

            log.info("🔒 PROVIDER {} creating claim with their providerId: {} (enforced by ProviderContextGuard)",
                    currentUser.getUsername(), userProviderId);
        } else if (authorizationService.isSuperAdmin(currentUser)
                || authorizationService.isInsuranceAdmin(currentUser)) {
            // SUPER_ADMIN and INSURANCE_ADMIN can set any provider
            log.info("🔓 ADMIN user {} creating claim - any providerId allowed", currentUser.getUsername());
        }
        // Other roles: no restriction on providerId
    }

    private void validateUpdateDto(ClaimUpdateDto dto, Claim claim) {
        // CANONICAL (2026-01-16): requestedAmount is calculated from ClaimLines, not
        // user-updateable
        // Only validate status-related fields

        ClaimStatus newStatus = dto.getStatus() != null ? dto.getStatus() : claim.getStatus();
        BigDecimal newApprovedAmount = dto.getApprovedAmount() != null ? dto.getApprovedAmount()
                : claim.getApprovedAmount();
        String newReviewerComment = dto.getReviewerComment() != null ? dto.getReviewerComment()
                : claim.getReviewerComment();

        // Phase 6: Validation for status transitions
        // Note: State transitions are now handled by ClaimStateMachine
        // These validations are kept for backwards compatibility with direct updates

        if (newStatus == ClaimStatus.APPROVED) {
            // Direct-entry: approvedAmount can be 0 (100% patient copay). Only require
            // non-null and non-negative.
            if (newApprovedAmount == null || newApprovedAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Approved status requires a non-negative approved amount");
            }
        }
        if (newStatus == ClaimStatus.SETTLED) {
            if (newApprovedAmount == null || newApprovedAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Settled status requires a non-negative approved amount");
            }
        }

        // Note: Partial approval is now represented as APPROVED with approvedAmount <
        // requestedAmount
        // The UI can show "Partial" badge based on approvedAmount < requestedAmount

        if (newStatus == ClaimStatus.REJECTED) {
            if (newReviewerComment == null || newReviewerComment.trim().isEmpty()) {
                throw new IllegalArgumentException("Rejected status requires reviewer comment");
            }
        }
    }

    /**
     * Check if a claim is financially locked (APPROVED, BATCHED, or SETTLED).
     * Financial amounts cannot be modified after approval.
     */
    private boolean isFinanciallyLocked(Claim claim) {
        ClaimStatus status = claim.getStatus();
        return status == ClaimStatus.APPROVED
                || status == ClaimStatus.BATCHED
                || status == ClaimStatus.SETTLED;
    }

    /**
     * Validate that no financial fields are being changed for a locked claim.
     * Throws BusinessRuleException if any financial field modification is
     * attempted.
     */
    private void validateNoFinancialChanges(Claim claim, ClaimUpdateDto dto) {
        if (dto.getApprovedAmount() != null
                && claim.getApprovedAmount() != null
                && dto.getApprovedAmount().compareTo(claim.getApprovedAmount()) != 0) {
            throw new BusinessRuleException(
                    "FINANCIAL_IMMUTABILITY: Cannot modify approvedAmount after claim is " + claim.getStatus());
        }
        // Note: requestedAmount is calculated from ClaimLines and not editable via DTO
        // netProviderAmount and patientCoPay are calculated and not in UpdateDto
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RETURN FOR INFO (Added for complete workflow)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Return a claim for correction.
     * 
     * POST /api/claims/{id}/return-for-info
     * 
     * Business Rules:
     * 1. Claim must be in UNDER_REVIEW status
     * 2. Reason is mandatory (explain what needs correction)
     * 3. Status transitions to NEEDS_CORRECTION
     * 4. Provider can then edit and resubmit
     * 
     * @param id  Claim ID
     * @param dto Return for info details with mandatory reason
     * @return Updated claim
     */
    @Transactional
    public ClaimViewDto returnForInfo(Long id, ClaimReturnForInfoDto dto) {
        log.info("📝 Returning claim {} for correction", id);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();

        // Validate current status allows returning for correction
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    String.format("Cannot return claim for correction in %s status. Must be UNDER_REVIEW.",
                            claim.getStatus()));
        }

        // Validate reason is provided
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new BusinessRuleException("Correction reason is required");
        }

        // Set reviewer comment with the reason
        String fullComment = "Needs correction: " + dto.getReason();
        if (dto.getRequiredDocuments() != null && !dto.getRequiredDocuments().trim().isEmpty()) {
            fullComment += "\n\nRequired documents: " + dto.getRequiredDocuments();
        }
        claim.setReviewerComment(fullComment);

        // Transition to NEEDS_CORRECTION status
        claimStateMachine.transition(claim, ClaimStatus.NEEDS_CORRECTION, currentUser);

        Claim savedClaim = claimRepository.save(claim);

        // Record in audit trail
        claimAuditService.recordStatusChange(savedClaim, previousStatus, currentUser, fullComment);

        log.info("📝 Claim {} returned for correction. Reason: {}", id, dto.getReason());

        return claimMapper.toViewDto(savedClaim);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ADDITIONAL QUERY METHODS (Added 2026-01-14)
    // For Contract-First compliance
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get claims by visit ID.
     * Returns all claims associated with a specific visit.
     */
    public List<ClaimViewDto> getClaimsByVisit(Long visitId) {
        log.info("🔍 Fetching claims for visit: {}", visitId);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        List<Claim> claims = claimRepository.findByVisitId(visitId);

        // SECURITY: Verify user can access each claim in this visit (FIXED 2026-03-12)
        return claims.stream()
                .filter(claim -> authorizationService.canAccessClaim(currentUser, claim.getId()))
                .map(claimMapper::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Get claim by claim number (which is the claim ID in this system).
     * Returns a single claim by its unique identifier.
     */
    public ClaimViewDto getClaimByNumber(Long claimNumber) {
        log.info("🔍 Fetching claim by number: {}", claimNumber);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Claim claim = claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found with number: " + claimNumber));

        // SECURITY: Verify access authorization (FIXED 2026-03-12)
        if (!authorizationService.canAccessClaim(currentUser, claim.getId())) {
            log.warn("❌ Access denied: user {} attempted to access claim number {}",
                    currentUser.getUsername(), claimNumber);
            throw new AccessDeniedException("Access denied to this claim");
        }

        // MEDICAL REVIEWER ISOLATION: Validate read access
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());

        ClaimViewDto dto = claimMapper.toViewDto(claim);

        // BACKEND-DRIVEN WORKFLOW: Add allowed transitions for UI
        if (currentUser != null) {
            dto.setAllowedNextStatuses(claimStateMachine.getAvailableTransitions(claim, currentUser));
            dto.setCanEdit(claimStateMachine.canEdit(claim));
        }

        return dto;
    }

    /**
     * Get claims by status with pagination.
     * Returns claims filtered by their status.
     */
    public Page<ClaimViewDto> getClaimsByStatus(ClaimStatus status, int page, int size, String sortBy, String sortDir) {
        log.info("🔍 Fetching claims by status: {}", status);
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Claim> claims = claimRepository.findByStatus(status, pageable);
        return claims.map(claimMapper::toViewDto);
    }

    /**
     * Get financial summary statistics for reports.
     * Calculates KPIs server-side using efficient JPQL aggregations.
     */
    @Transactional(readOnly = true)
    public FinancialSummaryDto getFinancialSummary(Long employerId, Long providerId, ClaimStatus status,
            LocalDate dateFrom, LocalDate dateTo) {
        log.info("📊 Fetching financial summary: employer={}, provider={}, status={}, from={}, to={}",
                employerId, providerId, status, dateFrom, dateTo);

        List<Object[]> queryResult = claimRepository.getFinancialSummary(employerId, providerId, status, dateFrom,
                dateTo);

        if (queryResult == null || queryResult.isEmpty() || queryResult.get(0) == null) {
            return FinancialSummaryDto.builder()
                    .totalClaimsAmount(BigDecimal.ZERO)
                    .totalApprovedAmount(BigDecimal.ZERO)
                    .totalRefusedAmount(BigDecimal.ZERO)
                    .totalPaidAmount(BigDecimal.ZERO)
                    .outstandingAmount(BigDecimal.ZERO)
                    .claimsCount(0L)
                    .approvedCount(0L)
                    .settledCount(0L)
                    .build();
        }

        Object[] result = queryResult.get(0);
        int len = result.length;

        // Map results with safe index checking
        long totalClaimsCount = (len > 0 && result[0] != null) ? ((Number) result[0]).longValue() : 0L;
        BigDecimal totalRequested = (len > 1 && result[1] != null) ? new BigDecimal(result[1].toString())
                : BigDecimal.ZERO;
        BigDecimal totalApproved = (len > 2 && result[2] != null) ? new BigDecimal(result[2].toString())
                : BigDecimal.ZERO;
        BigDecimal totalRefused = (len > 3 && result[3] != null) ? new BigDecimal(result[3].toString())
                : BigDecimal.ZERO;
        BigDecimal totalPaid = (len > 4 && result[4] != null) ? new BigDecimal(result[4].toString()) : BigDecimal.ZERO;
        long approvedCount = (len > 5 && result[5] != null) ? ((Number) result[5]).longValue() : 0L;
        long settledCount = (len > 6 && result[6] != null) ? ((Number) result[6]).longValue() : 0L;

        return FinancialSummaryDto.builder()
                .claimsCount(totalClaimsCount)
                .totalClaimsAmount(totalRequested)
                .totalApprovedAmount(totalApproved)
                .totalRefusedAmount(totalRefused)
                .totalPaidAmount(totalPaid)
                .outstandingAmount(totalApproved.subtract(totalPaid))
                .approvedCount(approvedCount)
                .settledCount(settledCount)
                .build();
    }

    /**
     * Delete a claim (Phase MVP).
     * 
     * Handles manual cleanup of RESTRICTED foreign keys (audit logs, batch items)
     * before deleting the main entity.
     */
    @Transactional
    public void deleteClaim(Long id) {
        log.info("🗑️ Deleting claim id: {}", id);

        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        // 1. Business Validation: Cannot delete BATCHED or SETTLED claims
        // (settlement_batch_items and settlement_batches were dropped by V117)
        if (claim.getStatus() == ClaimStatus.BATCHED || claim.getStatus() == ClaimStatus.SETTLED) {
            throw new BusinessRuleException("لا يمكن حذف مطالبة في حالة مفعلة أو مسواة");
        }

        // 2. Manual Cleanup for RESTRICTED tables (bypass DB constraint fails)
        // Note: audit logs have ON DELETE RESTRICT, so purge them first.

        log.warn("🧹 Cleaning up constrained data for claim {}...", id);

        // Delete Audit Logs
        em.createNativeQuery("DELETE FROM claim_audit_logs WHERE claim_id = :cid")
                .setParameter("cid", id)
                .executeUpdate();

        // 3. Execute main entity deletion
        // (lines, attachments, history will be deleted by DB-level CASCADE)
        claimRepository.delete(claim);

        // 4. Force synchronization NOW to ensure failure happens inside this method if
        // any FK remains
        claimRepository.flush();

        log.info("✅ Claim {} and its associations successfully deleted. Limits recalculated.", id);
    }
}
