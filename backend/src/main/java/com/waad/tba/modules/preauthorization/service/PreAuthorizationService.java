package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.dto.*;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.Priority;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.service.ArchitecturalGuardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for PreAuthorization business logic
 * Integrates with ProviderContract for price lookup and validation
 * 
 * ARCHITECTURAL RULE (2026-01-14):
 * Pre-authorizations MUST be created via Visit (Visit-Centric Architecture).
 * visitId is REQUIRED on all create operations.
 * 
 * SECURITY HARDENING (2026-01-16):
 * Provider data isolation enforced via ProviderContextGuard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationService {

    private final PreAuthorizationRepository preAuthorizationRepository;
    private final ProviderRepository providerRepository;
    private final MemberRepository memberRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final VisitRepository visitRepository;
    private final ProviderContractService providerContractService;
    private final PreAuthorizationAuditService auditService;
    private final AuthorizationService authorizationService;
    private final ProviderContextGuard providerContextGuard;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final ArchitecturalGuardService architecturalGuard;
    private final com.waad.tba.modules.claim.service.ReviewerProviderIsolationService reviewerIsolationService;

    // ==================== CREATE ====================

    /**
     * Create a new pre-authorization with contract price lookup (CANONICAL REBUILD
     * 2026-01-16)
     * 
     * ARCHITECTURAL LAWS:
     * 1. Pre-authorization MUST be linked to an existing Visit
     * 2. Medical Service MUST be selected from Provider Contract (no free-text)
     * 3. Price is AUTO-RESOLVED from Provider Contract (no manual entry)
     * 4. Provider ID is validated for PROVIDER users (must match their session)
     * 
     * Data Flow: Visit → MedicalService (from Contract) → ContractPrice (auto)
     */
    @Transactional
    public PreAuthorizationResponseDto createPreAuthorization(PreAuthorizationCreateDto dto, String createdBy) {
        log.info("[PRE-AUTH] Creating pre-authorization: visitId={}, medicalServiceId={}",
                dto.getVisitId(), dto.getMedicalServiceId());

        // ═══════════════════════════════════════════════════════════════════════════
        // PROVIDER PORTAL: Validate and enforce provider ID from JWT
        // ═══════════════════════════════════════════════════════════════════════════
        User currentUser = authorizationService.getCurrentUser();
        validateAndEnforceProviderId(dto, currentUser);

        // ═══════════════════════════════════════════════════════════════════════════
        // ARCHITECTURAL GUARD: Validate system invariants before processing
        // ═══════════════════════════════════════════════════════════════════════════
        architecturalGuard.guardPreAuthCreation(dto.getVisitId(), dto.getMedicalServiceId());

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Validate Visit exists (ARCHITECTURAL LAW: Visit-Centric)
        // ═══════════════════════════════════════════════════════════════════════════
        Visit visit = visitRepository.findById(dto.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ARCHITECTURAL VIOLATION: Visit not found with ID: " + dto.getVisitId() +
                                ". Pre-authorization MUST be created from an existing Visit."));

        if (visit.getStatus() == com.waad.tba.modules.visit.entity.VisitStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot create pre-authorization for a cancelled visit");
        }

        // Get member from visit
        Member member = visit.getMember();
        if (member == null) {
            throw new IllegalArgumentException("Visit has no associated member");
        }

        if (!member.getActive()) {
            throw new IllegalArgumentException("Member is not active");
        }

        log.info("[PRE-AUTH] Visit {} validated. Member: {}", dto.getVisitId(), member.getId());

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: Validate Provider
        // ═══════════════════════════════════════════════════════════════════════════
        Provider provider = providerRepository.findById(dto.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + dto.getProviderId()));

        if (!provider.getActive()) {
            throw new IllegalArgumentException("Provider is not active");
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 3: Validate MedicalService (ARCHITECTURAL LAW: No free-text services)
        // ═══════════════════════════════════════════════════════════════════════════
        MedicalService service = medicalServiceRepository.findById(dto.getMedicalServiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ARCHITECTURAL VIOLATION: Medical Service not found with ID: " + dto.getMedicalServiceId() +
                                ". Service MUST be selected from catalog."));

        if (!service.isActive()) {
            throw new IllegalArgumentException("Medical service is not active");
        }

        // NOTE: requiresPA check removed from MedicalService.
        // PA requirement is now determined by BenefitPolicyRule.requiresPreApproval.
        // Providers can submit PreAuthorization for ANY service - the insurance company
        // will decide whether to approve based on policy rules.

        log.info("[PRE-AUTH] Medical Service validated: {} ({})", service.getCode(), service.getName());

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 4: Get Contract Price (ARCHITECTURAL LAW: No manual pricing)
        // ═══════════════════════════════════════════════════════════════════════════
        LocalDate requestDate = dto.getRequestDate() != null ? dto.getRequestDate() : LocalDate.now();
        BigDecimal contractPrice = null;

        try {
            EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                    dto.getProviderId(),
                    service.getCode(),
                    requestDate);

            if (priceResponse.isHasContract()) {
                contractPrice = priceResponse.getContractPrice();
                log.info("[PRE-AUTH] Contract price resolved: {} LYD for service {}",
                        contractPrice, service.getCode());
            } else {
                // ARCHITECTURAL LAW: Service MUST be in Provider Contract
                throw new IllegalArgumentException(
                        "ARCHITECTURAL VIOLATION: Service '" + service.getCode() +
                                "' is not covered by Provider's contract. Select a covered service.");
            }
        } catch (ResourceNotFoundException e) {
            throw new IllegalArgumentException(
                    "ARCHITECTURAL VIOLATION: No active contract found for provider " + dto.getProviderId() +
                            ". Provider must have an active contract to create pre-authorizations.");
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 5: Build PreAuthorization Entity
        // ═══════════════════════════════════════════════════════════════════════════
        String referenceNumber = generateUniqueReferenceNumber();
        LocalDate expiryDate = requestDate.plusDays(dto.getExpiryDays() != null ? dto.getExpiryDays() : 30);

        Priority priority = Priority.NORMAL;
        if (dto.getPriority() != null) {
            try {
                priority = Priority.valueOf(dto.getPriority().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[PRE-AUTH] Invalid priority: {}, using NORMAL", dto.getPriority());
            }
        }

        // Determine service type from category or use default
        String serviceType = "MEDICAL";
        if (service.getCategoryId() != null) {
            serviceType = "CATEGORY_" + service.getCategoryId();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // CANONICAL: Category resolution - prefer DTO, fallback to service.categoryId
        // This enables correct coverage resolution when same service exists in multiple
        // categories
        // ═══════════════════════════════════════════════════════════════════════════
        Long serviceCategoryId = dto.getServiceCategoryId() != null
                ? dto.getServiceCategoryId()
                : service.getCategoryId();
        String serviceCategoryName = dto.getServiceCategoryName();

        // ═══════════════════════════════════════════════════════════════════════════
        // ARCHITECTURAL GUARD: Validate that service belongs to selected category
        // This is a HARD FAILURE - protects against Postman attacks or frontend bugs
        // ═══════════════════════════════════════════════════════════════════════════
        if (dto.getServiceCategoryId() != null && service.getCategoryId() != null) {
            if (!dto.getServiceCategoryId().equals(service.getCategoryId())) {
                log.error(
                        "🚫 ARCHITECTURAL VIOLATION: Service {} does not belong to category {}. Service's actual category: {}",
                        service.getCode(), dto.getServiceCategoryId(), service.getCategoryId());
                throw new IllegalArgumentException(
                        "الخدمة الطبية '" + service.getName() + "' (" + service.getCode() +
                                ") لا تنتمي للتصنيف الطبي المختار. يرجى التأكد من اختيار التصنيف الصحيح.");
            }
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // FINANCIAL SNAPSHOT: Get coverage percentage at creation time (IMMUTABLE)
        // This ensures audit trail is preserved even if policy rules change later
        // ═══════════════════════════════════════════════════════════════════════════
        var coverageInfoOpt = benefitPolicyCoverageService.getCoverageForService(member, service.getId());
        Integer coveragePercentSnapshot = coverageInfoOpt.map(c -> c.getCoveragePercent()).orElse(null);
        Integer patientCopayPercentSnapshot = coveragePercentSnapshot != null ? (100 - coveragePercentSnapshot) : null;
        log.info("[PRE-AUTH] Coverage snapshot: coverage={}%, copay={}%", coveragePercentSnapshot,
                patientCopayPercentSnapshot);

        PreAuthorization preAuth = PreAuthorization.builder()
                .preAuthNumber(referenceNumber) // Legacy column (required by database)
                .referenceNumber(referenceNumber)
                .memberId(member.getId())
                .providerId(dto.getProviderId())
                .visit(visit) // FK to Visit
                .medicalService(service) // FK to MedicalService (NO FREE-TEXT)
                .serviceCode(service.getCode()) // Denormalized snapshot
                .serviceName(service.getName()) // Denormalized snapshot
                .serviceType(serviceType) // Legacy column (required by database)
                .serviceCategoryId(serviceCategoryId) // CANONICAL: From DTO or service
                .serviceCategoryName(serviceCategoryName) // CANONICAL: For display
                .requestDate(requestDate)
                .expectedServiceDate(requestDate) // Default: same as request date
                .expiryDate(expiryDate)
                .contractPrice(contractPrice) // AUTO-RESOLVED from contract
                .requiresPA(true) // PreAuthorization always requires PA (that's why it exists)
                .coveragePercentSnapshot(coveragePercentSnapshot) // SNAPSHOT for financial audit
                .patientCopayPercentSnapshot(patientCopayPercentSnapshot) // SNAPSHOT for financial audit
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "LYD")
                .status(PreAuthStatus.PENDING)
                .priority(priority)
                .diagnosisCode(dto.getDiagnosisCode() != null ? dto.getDiagnosisCode() : "Z00.0")
                .diagnosisDescription(dto.getDiagnosisDescription())
                .notes(dto.getNotes())
                .active(true)
                .createdBy(createdBy)
                .build();

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 6: Save and Return
        // ═══════════════════════════════════════════════════════════════════════════
        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Created pre-authorization: id={}, ref={}, contractPrice={}",
                preAuth.getId(), preAuth.getReferenceNumber(), contractPrice);

        // Log audit trail
        auditService.logCreate(preAuth.getId(), preAuth.getReferenceNumber(), createdBy,
                "Created with contract price: " + contractPrice + " LYD");

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== UPDATE ====================

    /**
     * Update pre-authorization (only if PENDING)
     */
    @Transactional
    public PreAuthorizationResponseDto updatePreAuthorization(Long id, PreAuthorizationUpdateDto dto,
            String updatedBy) {
        log.info("[PRE-AUTH] Updating pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        if (preAuth.getStatus() != PreAuthStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING pre-authorizations can be updated");
        }

        // Capture old state for audit
        String oldDiagnosisCode = preAuth.getDiagnosisCode();
        String oldDiagnosisDescription = preAuth.getDiagnosisDescription();
        String oldNotes = preAuth.getNotes();

        // Update allowed fields (price CANNOT be changed - canonical law)
        if (dto.getPriority() != null) {
            try {
                preAuth.setPriority(Priority.valueOf(dto.getPriority().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[PRE-AUTH] Invalid priority: {}", dto.getPriority());
            }
        }
        if (dto.getDiagnosisCode() != null) {
            preAuth.setDiagnosisCode(dto.getDiagnosisCode());
        }
        if (dto.getDiagnosisDescription() != null) {
            preAuth.setDiagnosisDescription(dto.getDiagnosisDescription());
        }
        if (dto.getNotes() != null) {
            preAuth.setNotes(dto.getNotes());
        }
        if (dto.getExpiryDays() != null) {
            preAuth.setExpiryDate(preAuth.getRequestDate().plusDays(dto.getExpiryDays()));
        }

        preAuth.setUpdatedBy(updatedBy);

        // Audit logging
        if (dto.getDiagnosisCode() != null && !dto.getDiagnosisCode().equals(oldDiagnosisCode)) {
            auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy,
                    "diagnosisCode", oldDiagnosisCode, dto.getDiagnosisCode());
        }
        if (dto.getDiagnosisDescription() != null && !dto.getDiagnosisDescription().equals(oldDiagnosisDescription)) {
            auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy,
                    "diagnosisDescription", oldDiagnosisDescription, dto.getDiagnosisDescription());
        }

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Updated pre-authorization {}", id);

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * Update pre-authorization DATA only (for PROVIDER and EMPLOYER_ADMIN).
     * SECURITY: Only allowed in PENDING and NEEDS_CORRECTION statuses.
     * 
     * @param id        PreAuth ID
     * @param dto       Data update DTO (no status/financial fields)
     * @param updatedBy Username
     * @return Updated pre-authorization
     * @since Provider Portal Security Fix (Phase 3)
     */
    @Transactional
    public PreAuthorizationResponseDto updatePreAuthData(Long id, PreAuthDataUpdateDto dto, String updatedBy) {
        log.info("[PRE-AUTH] Updating pre-authorization DATA: id={}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        // SECURITY: Verify status allows editing
        if (!preAuth.allowsEdit()) {
            throw new BusinessRuleException(
                    String.format(
                            "Cannot edit pre-authorization in %s status. Only PENDING and NEEDS_CORRECTION allow edits.",
                            preAuth.getStatus()));
        }

        // Update data fields only
        if (dto.getExpectedServiceDate() != null) {
            preAuth.setExpectedServiceDate(dto.getExpectedServiceDate());
        }
        // clinicalJustification doesn't exist in PreAuthorization - skip
        if (dto.getNotes() != null) {
            preAuth.setNotes(dto.getNotes());
        }
        if (dto.getPriority() != null) {
            try {
                preAuth.setPriority(Priority.valueOf(dto.getPriority().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[PRE-AUTH] Invalid priority: {}", dto.getPriority());
            }
        }

        preAuth.setUpdatedBy(updatedBy);
        preAuth = preAuthorizationRepository.save(preAuth);

        // Audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy, "data", "updated", "data_updated");

        log.info("✅ [PRE-AUTH] Data updated: id={}", id);

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * Review pre-authorization (for REVIEWER and INSURANCE_ADMIN only).
     * SECURITY: Reviewers can ONLY change status/comment/approvedAmount.
     * 
     * @param id         PreAuth ID
     * @param dto        Review DTO (status, comment, approvedAmount only)
     * @param reviewedBy Username
     * @return Updated pre-authorization
     * @since Provider Portal Security Fix (Phase 3)
     */
    @Transactional
    public PreAuthorizationResponseDto reviewPreAuth(Long id, PreAuthReviewDto dto, String reviewedBy) {
        log.info("[PRE-AUTH] Reviewing pre-authorization: id={}, newStatus={}", id, dto.getStatus());

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        // Cannot review a cancelled pre-authorization
        if (preAuth.getStatus() == PreAuthStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot review a cancelled pre-authorization. ID: " + id);
        }
        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.isReviewer(currentUser) &&
                !authorizationService.isInsuranceAdmin(currentUser) &&
                !authorizationService.isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("Only reviewers can perform review actions");
        }

        // SECURITY: Apply reviewer-provider isolation
        if (authorizationService.isReviewer(currentUser)) {
            reviewerIsolationService.validateReviewerAccess(currentUser, preAuth.getProviderId());
        }

        PreAuthStatus previousStatus = preAuth.getStatus();

        // Validate status transition
        if (dto.getStatus() != previousStatus) {
            // Validation for specific statuses
            if (dto.getStatus() == PreAuthStatus.REJECTED || dto.getStatus() == PreAuthStatus.NEEDS_CORRECTION) {
                if (dto.getReviewerComment() == null || dto.getReviewerComment().isBlank()) {
                    throw new BusinessRuleException(
                            dto.getStatus() + " status requires a reviewer comment");
                }
            }

            if (dto.getStatus() == PreAuthStatus.APPROVED) {
                if (dto.getApprovedAmount() == null || dto.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("APPROVED status requires approved amount > 0");
                }
                preAuth.setApprovedAmount(dto.getApprovedAmount());

                if (dto.getCopayPercentage() != null) {
                    preAuth.setCopayPercentage(dto.getCopayPercentage());
                }
            }

            // Set rejection reason (using existing field)
            if (dto.getReviewerComment() != null) {
                if (dto.getStatus() == PreAuthStatus.REJECTED) {
                    preAuth.setRejectionReason(dto.getReviewerComment());
                } else if (dto.getStatus() == PreAuthStatus.NEEDS_CORRECTION) {
                    // Store in notes for NEEDS_CORRECTION
                    preAuth.setNotes(dto.getReviewerComment());
                }
            }

            // Perform status transition
            preAuth.setStatus(dto.getStatus());
        }

        preAuth.setUpdatedBy(reviewedBy);
        if (dto.getStatus() == PreAuthStatus.APPROVED) {
            preAuth.setApprovedBy(reviewedBy);
            preAuth.setApprovedAt(LocalDateTime.now());
        }
        preAuth = preAuthorizationRepository.save(preAuth);

        // Audit trail
        if (dto.getStatus() == PreAuthStatus.APPROVED) {
            auditService.logApprove(id, preAuth.getReferenceNumber(), reviewedBy,
                    dto.getReviewerComment() != null ? dto.getReviewerComment() : "Approved");
        } else if (dto.getStatus() == PreAuthStatus.REJECTED) {
            auditService.logReject(id, preAuth.getReferenceNumber(), reviewedBy,
                    dto.getReviewerComment() != null ? dto.getReviewerComment() : "Rejected");
        } else {
            // For NEEDS_CORRECTION and other status changes
            auditService.logUpdate(id, preAuth.getReferenceNumber(), reviewedBy,
                    previousStatus.name(), dto.getStatus().name(),
                    dto.getReviewerComment() != null ? dto.getReviewerComment() : "Status changed");
        }

        log.info("✅ [PRE-AUTH] Reviewed: id={}, status={}", id, preAuth.getStatus());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * Submit pre-authorization for review.
     * Transitions from PENDING or NEEDS_CORRECTION to UNDER_REVIEW.
     * 
     * @param id          PreAuth ID
     * @param submittedBy Username
     * @return Updated pre-authorization
     * @since Provider Portal Draft-First Model (Phase 3)
     */
    @Transactional
    public PreAuthorizationResponseDto submitPreAuth(Long id, String submittedBy) {
        log.info("[PRE-AUTH] Submitting pre-authorization: id={}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        // Validate current status allows submission
        if (preAuth.getStatus() != PreAuthStatus.PENDING && preAuth.getStatus() != PreAuthStatus.NEEDS_CORRECTION) {
            throw new BusinessRuleException(
                    String.format(
                            "Cannot submit pre-authorization in %s status. Only PENDING and NEEDS_CORRECTION can be submitted.",
                            preAuth.getStatus()));
        }

        PreAuthStatus previousStatus = preAuth.getStatus();

        // Transition to UNDER_REVIEW
        preAuth.setStatus(PreAuthStatus.UNDER_REVIEW);
        preAuth.setUpdatedBy(submittedBy);
        preAuth = preAuthorizationRepository.save(preAuth);

        // Audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), submittedBy,
                previousStatus.name(), PreAuthStatus.UNDER_REVIEW.name(), "Pre-authorization submitted for review");

        log.info("✅ [PRE-AUTH] Submitted: id={}, status={}", id, preAuth.getStatus());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== APPROVE ====================

    /**
     * Approve pre-authorization with copay calculation
     */
    @Transactional
    public PreAuthorizationResponseDto approvePreAuthorization(Long id, PreAuthorizationApproveDto dto,
            String approvedBy) {
        log.info("[PRE-AUTH] Approving pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.canBeApproved()) {
            throw new IllegalStateException(
                    "PreAuthorization cannot be approved in current status: " + preAuth.getStatus());
        }

        BigDecimal approvedAmount = resolveApprovedAmount(preAuth, dto);
        BigDecimal copayPercentage = resolveCopayPercentage(preAuth, dto);

        // Validate approved amount against contract price
        if (preAuth.getContractPrice() != null && approvedAmount.compareTo(preAuth.getContractPrice()) > 0) {
            log.warn("[PRE-AUTH] Approved amount {} exceeds contract price {}",
                    approvedAmount, preAuth.getContractPrice());
        }

        // Calculate copay
        BigDecimal copayAmount = preAuth.calculateCopay(approvedAmount, copayPercentage);

        // Approve
        preAuth.approve(approvedAmount, copayAmount, approvedBy);
        preAuth.setCopayPercentage(copayPercentage);

        if (dto.getApprovalNotes() != null) {
            preAuth.setNotes((preAuth.getNotes() != null ? preAuth.getNotes() + "\n" : "") +
                    "Approval Notes: " + dto.getApprovalNotes());
        }

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Approved pre-authorization {} with amount {} and copay {}",
                id, approvedAmount, copayAmount);

        // Note: visit status is NOT auto-set to COMPLETED here.
        // A visit may have multiple pre-authorizations and claims.
        // Visit completion is managed by the provider or when all linked items are
        // settled.
        log.debug("[PRE-AUTH] Pre-auth {} approved; visit status unchanged (may have other pending items)", id);

        // Log audit trail
        auditService.logApprove(id, preAuth.getReferenceNumber(), approvedBy,
                "Approved amount: " + approvedAmount +
                        ", Copay: " + copayPercentage + "%");

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: PHASE 1 - Request Approval (Fast, Non-Blocking)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * This is the NEW approval endpoint that returns immediately.
     * It transitions the pre-auth to APPROVAL_IN_PROGRESS and triggers async
     * processing.
     * 
     * REPLACES: approvePreAuthorization() for production use (old method kept for
     * backward compatibility)
     * 
     * FLOW:
     * 1. Validate pre-auth exists and is in valid state
     * 2. Change status to APPROVAL_IN_PROGRESS (< 1 second)
     * 3. Trigger async background processing
     * 4. Return immediately with status "APPROVAL_IN_PROGRESS"
     * 
     * @param id         PreAuthorization ID
     * @param dto        Approval details
     * @param approvedBy User approving
     * @return PreAuth with APPROVAL_IN_PROGRESS status
     */
    @Transactional
    public PreAuthorizationResponseDto requestApproval(Long id, PreAuthorizationApproveDto dto, String approvedBy) {
        log.info("🚀 [SPLIT-PHASE] Phase 1: Requesting approval for pre-auth {}", id);

        // Quick validation without heavy locks
        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.canBeApproved()) {
            throw new IllegalStateException(
                    "PreAuthorization cannot be approved in current status: " + preAuth.getStatus());
        }

        // Store approval metadata for async processing
        if (dto.getApprovalNotes() != null) {
            preAuth.setNotes((preAuth.getNotes() != null ? preAuth.getNotes() + "\n" : "") +
                    "Approval Notes: " + dto.getApprovalNotes());
        }

        // Transition to APPROVAL_IN_PROGRESS
        preAuth.setStatus(PreAuthStatus.APPROVAL_IN_PROGRESS);
        PreAuthorization savedPreAuth = preAuthorizationRepository.save(preAuth);

        log.info("✅ [SPLIT-PHASE] Phase 1 complete: PreAuth {} marked as APPROVAL_IN_PROGRESS", id);

        // Trigger async phase 2 processing
        processApprovalAsync(id, dto, approvedBy);

        // Fetch related entities for response
        Member member = memberRepository.findById(savedPreAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(savedPreAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(savedPreAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(savedPreAuth, member, provider, service);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: PHASE 2 - Process Approval (Heavy, Background)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * This method executes in the background using @Async.
     * It performs all heavy calculations including copay, coverage validation, etc.
     * 
     * EXECUTES:
     * 1. Copay calculation
     * 2. Coverage validation (BenefitPolicy)
     * 3. Contract price validation
     * 4. Transition to APPROVED or REJECTED
     * 
     * ISOLATION: REQUIRES_NEW to avoid deadlocks with Phase 1
     * 
     * @param id         PreAuthorization ID
     * @param dto        Approval details (from Phase 1)
     * @param approvedBy User approving
     */
    @org.springframework.scheduling.annotation.Async("approvalTaskExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void processApprovalAsync(Long id, PreAuthorizationApproveDto dto, String approvedBy) {
        log.info("⚙️ [SPLIT-PHASE] Phase 2: Starting async approval processing for pre-auth {}", id);

        try {
            PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

            log.info("🔒 [ASYNC-PROCESSING] Processing pre-auth {}", id);

            if (preAuth.getStatus() != PreAuthStatus.APPROVAL_IN_PROGRESS) {
                throw new IllegalStateException(
                        "PreAuthorization is not in APPROVAL_IN_PROGRESS status: " + preAuth.getStatus());
            }

            BigDecimal approvedAmount = resolveApprovedAmount(preAuth, dto);
            BigDecimal copayPercentage = resolveCopayPercentage(preAuth, dto);

            // Validate approved amount against contract price
            if (preAuth.getContractPrice() != null && approvedAmount.compareTo(preAuth.getContractPrice()) > 0) {
                log.warn("[PRE-AUTH] Approved amount {} exceeds contract price {}",
                        approvedAmount, preAuth.getContractPrice());
            }

            // Calculate copay
            BigDecimal copayAmount = preAuth.calculateCopay(approvedAmount, copayPercentage);

            // Validate coverage if member has benefit policy
            Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
            if (member != null && member.getBenefitPolicy() != null) {
                try {
                    benefitPolicyCoverageService.validateAmountLimits(
                            member, member.getBenefitPolicy(), approvedAmount,
                            preAuth.getRequestDate() != null ? preAuth.getRequestDate() : LocalDate.now());
                    log.debug("✅ BenefitPolicy amount validation passed");
                } catch (Exception e) {
                    log.error("❌ BenefitPolicy coverage validation failed: {}", e.getMessage());
                    throw new IllegalStateException("فشل التحقق من التغطية: " + e.getMessage());
                }
            }

            // Approve
            preAuth.approve(approvedAmount, copayAmount, approvedBy);
            preAuth.setCopayPercentage(copayPercentage);

            preAuth = preAuthorizationRepository.save(preAuth);
            log.info("[PRE-AUTH] Approved pre-authorization {} with amount {} and copay {}",
                    id, approvedAmount, copayAmount);

            // Note: visit status is NOT auto-set to COMPLETED here.
            // A visit may have multiple pre-authorizations and claims.
            // Visit completion is managed by the provider or when all linked items are
            // settled.
            log.debug("[PRE-AUTH] Pre-auth {} approved; visit status unchanged (may have other pending items)", id);

            // Log audit trail
            auditService.logApprove(id, preAuth.getReferenceNumber(), approvedBy,
                    "Approved amount: " + approvedAmount +
                            ", Copay: " + copayPercentage + "%");

            log.info("✅ [SPLIT-PHASE] Phase 2 complete: PreAuth {} approved successfully", id);

        } catch (Exception e) {
            log.error("❌ [SPLIT-PHASE] Phase 2 failed for pre-auth {}: {}", id, e.getMessage(), e);

            // On failure, transition to REJECTED with error message
            try {
                PreAuthorization failedPreAuth = preAuthorizationRepository.findById(id).orElse(null);
                if (failedPreAuth != null && failedPreAuth.getStatus() == PreAuthStatus.APPROVAL_IN_PROGRESS) {
                    failedPreAuth.setStatus(PreAuthStatus.REJECTED);
                    failedPreAuth.setRejectionReason("فشل في المعالجة: " + e.getMessage());
                    failedPreAuth.setReservedAmount(BigDecimal.ZERO);
                    failedPreAuth.setUpdatedBy(approvedBy);
                    preAuthorizationRepository.save(failedPreAuth);
                    log.info("🔄 PreAuth {} transitioned to REJECTED due to processing failure", id);
                }
            } catch (Exception rollbackError) {
                log.error("❌ Failed to rollback pre-auth {} to REJECTED: {}", id, rollbackError.getMessage());
            }
        }
    }

    private BigDecimal resolveApprovedAmount(PreAuthorization preAuth, PreAuthorizationApproveDto dto) {
        if (dto != null && dto.getApprovedAmount() != null) {
            if (dto.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Approved amount must be greater than zero");
            }
            return dto.getApprovedAmount();
        }

        if (preAuth.getContractPrice() != null && preAuth.getContractPrice().compareTo(BigDecimal.ZERO) > 0) {
            return preAuth.getContractPrice();
        }

        throw new IllegalStateException("Cannot resolve approved amount: missing contract price");
    }

    private BigDecimal resolveCopayPercentage(PreAuthorization preAuth, PreAuthorizationApproveDto dto) {
        BigDecimal resolved;

        if (dto != null && dto.getCopayPercentage() != null) {
            resolved = dto.getCopayPercentage();
        } else if (preAuth.getPatientCopayPercentSnapshot() != null) {
            resolved = BigDecimal.valueOf(preAuth.getPatientCopayPercentSnapshot());
        } else if (preAuth.getCoveragePercentSnapshot() != null) {
            resolved = BigDecimal.valueOf(100 - preAuth.getCoveragePercentSnapshot());
        } else {
            resolved = BigDecimal.ZERO;
        }

        if (resolved.compareTo(BigDecimal.ZERO) < 0 || resolved.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("Resolved copay percentage is out of range: " + resolved);
        }

        return resolved;
    }

    // ==================== REJECT ====================

    /**
     * Reject pre-authorization
     */
    @Transactional
    public PreAuthorizationResponseDto rejectPreAuthorization(Long id, PreAuthorizationRejectDto dto,
            String rejectedBy) {
        log.info("[PRE-AUTH] Rejecting pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        preAuth.reject(dto.getRejectionReason(), rejectedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Rejected pre-authorization {} with reason: {}", id, dto.getRejectionReason());

        // Update visit status if linked
        if (preAuth.getVisit() != null) {
            Visit visit = preAuth.getVisit();
            visit.setStatus(com.waad.tba.modules.visit.entity.VisitStatus.CANCELLED);
            visitRepository.save(visit);
            log.info("✅ Updated visit {} status to CANCELLED after pre-auth rejection", visit.getId());
        }

        // Log audit trail
        auditService.logReject(id, preAuth.getReferenceNumber(), rejectedBy, dto.getRejectionReason());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== CANCEL ====================

    /**
     * Cancel pre-authorization
     */
    @Transactional
    public PreAuthorizationResponseDto cancelPreAuthorization(Long id, String cancelReason, String cancelledBy) {
        log.info("[PRE-AUTH] Cancelling pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        preAuth.cancel(cancelReason, cancelledBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Cancelled pre-authorization {}", id);

        // Log audit trail
        auditService.logCancel(id, preAuth.getReferenceNumber(), cancelledBy, cancelReason);

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== ACKNOWLEDGE ====================

    /**
     * Acknowledge pre-authorization (Provider viewed the approval)
     * Lifecycle: APPROVED → ACKNOWLEDGED
     */
    @Transactional
    public PreAuthorizationResponseDto acknowledgePreAuthorization(Long id, String acknowledgedBy) {
        log.info("[PRE-AUTH] Provider acknowledging pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        // Validation: Only APPROVED pre-auths can be acknowledged
        if (preAuth.getStatus() != PreAuthStatus.APPROVED) {
            throw new IllegalStateException(
                    String.format("Only APPROVED pre-authorizations can be acknowledged. Current status: %s",
                            preAuth.getStatus()));
        }

        // Transition to ACKNOWLEDGED
        PreAuthStatus oldStatus = preAuth.getStatus();
        preAuth.setStatus(PreAuthStatus.ACKNOWLEDGED);
        preAuth.setUpdatedBy(acknowledgedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("✅ Pre-authorization {} acknowledged by provider", id);

        // Log audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), acknowledgedBy,
                "status", oldStatus.toString(), PreAuthStatus.ACKNOWLEDGED.toString());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== MARK AS USED ====================

    /**
     * Mark pre-authorization as USED (called when linked to a claim)
     * Lifecycle: APPROVED/ACKNOWLEDGED → USED
     * 
     * This is typically called automatically by ClaimService when a claim is
     * created with a pre-auth.
     */
    @Transactional
    public PreAuthorizationResponseDto markAsUsed(Long id, String claimNumber, String updatedBy) {
        log.info("[PRE-AUTH] Marking pre-authorization {} as USED (linked to claim {})", id, claimNumber);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        // Validation: Only APPROVED or ACKNOWLEDGED pre-auths can be marked as USED
        if (preAuth.getStatus() != PreAuthStatus.APPROVED && preAuth.getStatus() != PreAuthStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    String.format(
                            "Only APPROVED or ACKNOWLEDGED pre-authorizations can be marked as USED. Current status: %s",
                            preAuth.getStatus()));
        }

        // Transition to USED
        PreAuthStatus oldStatus = preAuth.getStatus();
        preAuth.setStatus(PreAuthStatus.USED);
        preAuth.setUpdatedBy(updatedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("✅ Pre-authorization {} marked as USED (claim: {})", id, claimNumber);

        // Log audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy,
                "status", oldStatus.toString(), PreAuthStatus.USED.toString());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== DELETE ====================

    /**
     * Soft delete pre-authorization
     */
    @Transactional
    public void deletePreAuthorization(Long id, String deletedBy) {
        log.info("[PRE-AUTH] Deleting pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        preAuth.setActive(false);
        preAuth.setUpdatedBy(deletedBy);

        preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Deleted pre-authorization {}", id);

        // Log audit trail
        auditService.logDelete(id, preAuth.getReferenceNumber(), deletedBy);
    }

    // ==================== QUERIES ====================

    /**
     * Get pre-authorization by ID
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto getPreAuthorizationById(Long id) {
        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * Get pre-authorization by reference number
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto getPreAuthorizationByReference(String referenceNumber) {
        PreAuthorization preAuth = preAuthorizationRepository.findByReferenceNumberAndActiveTrue(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PreAuthorization not found with reference: " + referenceNumber));

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * Get all pre-authorizations (paginated)
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getAllPreAuthorizations(Pageable pageable) {
        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByActiveTrue(pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pre-authorizations by member
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPreAuthorizationsByMember(Long memberId, Pageable pageable) {
        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByMemberIdAndActiveTrue(memberId, pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pre-authorizations by provider
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPreAuthorizationsByProvider(Long providerId, Pageable pageable) {
        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByProviderIdAndActiveTrue(providerId,
                pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pre-authorizations by status
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPreAuthorizationsByStatus(PreAuthStatus status, Pageable pageable) {
        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByStatusAndActiveTrue(status, pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pending pre-authorizations for inbox (Operations Queue) - CANONICAL
     * 2026-01-26
     * 
     * Returns pre-authorizations with PENDING or UNDER_REVIEW status for
     * processing.
     * Mirrors ClaimService.getPendingClaims() behavior.
     * 
     * FIFO pattern - oldest first for fair processing.
     * 
     * Status Logic:
     * - PENDING: Newly created, awaiting initial review
     * - UNDER_REVIEW: Currently being reviewed by operations staff
     * 
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of PreAuthorizationResponseDto with all required fields for
     *         inbox display
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPendingInbox(Pageable pageable) {
        log.info("[SERVICE] Fetching pending pre-authorizations for inbox (PENDING + UNDER_REVIEW)");

        // CANONICAL: Include both PENDING and UNDER_REVIEW statuses (like Claims)
        List<PreAuthStatus> inboxStatuses = List.of(PreAuthStatus.PENDING, PreAuthStatus.UNDER_REVIEW);

        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByStatusIn(
                inboxStatuses,
                pageable);

        log.info("[SERVICE] Found {} pre-authorizations in inbox", preAuths.getTotalElements());
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Find valid pre-authorization for claim submission
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto findValidPreAuthorization(Long memberId, Long providerId, String serviceCode) {
        List<PreAuthorization> validPreAuths = preAuthorizationRepository.findValidPreAuthorizations(
                memberId, providerId, serviceCode, LocalDate.now());

        if (validPreAuths.isEmpty()) {
            throw new ResourceNotFoundException("No valid pre-authorization found for member " + memberId +
                    ", provider " + providerId + ", service " + serviceCode);
        }

        // Return the most recent one
        PreAuthorization preAuth = validPreAuths.get(0);

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== START REVIEW ====================

    /**
     * Start review of a pre-authorization (PENDING → UNDER_REVIEW)
     * This is typically called by a reviewer taking ownership of the request.
     */
    @Transactional
    public PreAuthorizationResponseDto startReview(Long id, String reviewedBy) {
        log.info("[PRE-AUTH] Starting review for pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        if (preAuth.getStatus() != PreAuthStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING pre-authorizations can be started for review. Current status: "
                            + preAuth.getStatus());
        }

        // Transition to UNDER_REVIEW
        preAuth.setStatus(PreAuthStatus.UNDER_REVIEW);
        preAuth.setUpdatedBy(reviewedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Pre-authorization {} is now UNDER_REVIEW by {}", id, reviewedBy);

        // Update visit status if linked
        if (preAuth.getVisit() != null) {
            Visit visit = preAuth.getVisit();
            visit.setStatus(com.waad.tba.modules.visit.entity.VisitStatus.IN_PROGRESS);
            visitRepository.save(visit);
            log.info("✅ Updated visit {} status to IN_PROGRESS", visit.getId());
        }

        // Log audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), reviewedBy,
                "status", "PENDING", "UNDER_REVIEW");

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== CHECK VALIDITY ====================

    /**
     * Check if a member has a valid pre-authorization for a specific service.
     * Returns the valid pre-authorization if found, null otherwise.
     * 
     * @param memberId    The member ID
     * @param serviceCode The medical service code
     * @return Valid PreAuthorizationResponseDto or null if not found
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto checkValidity(Long memberId, String serviceCode) {
        log.info("[PRE-AUTH] Checking validity for member {} and service {}", memberId, serviceCode);

        // Find approved and valid pre-authorizations for this member and service
        List<PreAuthorization> validPreAuths = preAuthorizationRepository
                .findValidByMemberAndService(memberId, serviceCode, LocalDate.now());

        if (validPreAuths.isEmpty()) {
            log.info("[PRE-AUTH] No valid pre-authorization found for member {} and service {}", memberId, serviceCode);
            return null;
        }

        // Return the most recent valid one
        PreAuthorization preAuth = validPreAuths.stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElse(validPreAuths.get(0));

        log.info("[PRE-AUTH] Found valid pre-authorization {} for member {} and service {}",
                preAuth.getReferenceNumber(), memberId, serviceCode);

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, service);
    }

    // ==================== MAINTENANCE ====================

    /**
     * Mark expired pre-authorizations
     */
    @Transactional
    public int markExpiredPreAuthorizations() {
        log.info("[PRE-AUTH] Marking expired pre-authorizations");

        List<PreAuthorization> expiredList = preAuthorizationRepository.findExpiredPreAuthorizations(LocalDate.now());

        for (PreAuthorization preAuth : expiredList) {
            preAuth.markAsExpired();
        }

        if (!expiredList.isEmpty()) {
            preAuthorizationRepository.saveAll(expiredList);
        }

        log.info("[PRE-AUTH] Marked {} expired pre-authorizations", expiredList.size());
        return expiredList.size();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate unique reference number
     */
    private String generateUniqueReferenceNumber() {
        String referenceNumber;
        int attempts = 0;
        do {
            referenceNumber = PreAuthorization.generateReferenceNumber();
            attempts++;
        } while (preAuthorizationRepository.existsByReferenceNumber(referenceNumber) && attempts < 10);

        if (attempts >= 10) {
            throw new RuntimeException("Failed to generate unique reference number after 10 attempts");
        }

        return referenceNumber;
    }

    /**
     * Map to response DTO with full details (CANONICAL REBUILD 2026-01-16)
     */
    private PreAuthorizationResponseDto mapToResponseDto(PreAuthorization preAuth, Member member,
            Provider provider, MedicalService service) {
        Integer daysUntilExpiry = null;
        if (preAuth.getExpiryDate() != null) {
            daysUntilExpiry = (int) ChronoUnit.DAYS.between(LocalDate.now(), preAuth.getExpiryDate());
        }

        // Get visit info
        Visit visit = preAuth.getVisit();

        return PreAuthorizationResponseDto.builder()
                .id(preAuth.getId())
                .referenceNumber(preAuth.getReferenceNumber())
                // Visit info
                .visitId(visit != null ? visit.getId() : null)
                .visitDate(visit != null ? visit.getVisitDate() : null)
                .visitType(visit != null && visit.getVisitType() != null ? visit.getVisitType().toString() : null)
                // Member info
                .memberId(preAuth.getMemberId())
                .memberName(member != null ? member.getFullName() : null)
                .memberCardNumber(member != null ? member.getCardNumber() : null)
                .memberNationalNumber(member != null ? member.getNationalNumber() : null)
                // Employer info (جهة العمل)
                .employerId(member != null && member.getEmployer() != null ? member.getEmployer().getId() : null)
                .employerName(member != null && member.getEmployer() != null ? member.getEmployer().getName() : null)
                .employerCode(member != null && member.getEmployer() != null ? member.getEmployer().getCode() : null)
                // Provider info
                .providerId(preAuth.getProviderId())
                .providerName(provider != null ? provider.getName() : null)
                .providerLicense(provider != null ? provider.getLicenseNumber() : null)
                // Medical Service info (from Contract)
                .medicalServiceId(service != null ? service.getId() : null)
                .serviceCode(preAuth.getServiceCode())
                .serviceName(service != null ? service.getName() : null)
                .serviceCategoryId(preAuth.getServiceCategoryId())
                .requiresPA(preAuth.getRequiresPA())
                // Diagnosis
                .diagnosisCode(preAuth.getDiagnosisCode())
                .diagnosisDescription(preAuth.getDiagnosisDescription())
                // Dates
                .requestDate(preAuth.getRequestDate())
                .expiryDate(preAuth.getExpiryDate())
                .daysUntilExpiry(daysUntilExpiry)
                // Pricing (Contract-Driven)
                .contractPrice(preAuth.getContractPrice())
                .approvedAmount(preAuth.getApprovedAmount())
                .copayAmount(preAuth.getCopayAmount())
                .copayPercentage(preAuth.getCopayPercentage())
                .insuranceCoveredAmount(preAuth.getInsuranceCoveredAmount())
                .currency(preAuth.getCurrency())
                // Status
                .status(preAuth.getStatus().toString())
                .priority(preAuth.getPriority().toString())
                // Additional
                .notes(preAuth.getNotes())
                .rejectionReason(preAuth.getRejectionReason())
                // Flags
                .hasContract(preAuth.getContractPrice() != null)
                .isValid(preAuth.isValid())
                .isExpired(preAuth.isExpired())
                .canBeApproved(preAuth.canBeApproved())
                .canBeRejected(preAuth.canBeRejected())
                .canBeCancelled(preAuth.canBeCancelled())
                // Audit
                .createdAt(preAuth.getCreatedAt())
                .updatedAt(preAuth.getUpdatedAt())
                .createdBy(preAuth.getCreatedBy())
                .updatedBy(preAuth.getUpdatedBy())
                .approvedAt(preAuth.getApprovedAt())
                .approvedBy(preAuth.getApprovedBy())
                .active(preAuth.getActive())
                .build();
    }

    /**
     * Map to response DTO (lightweight - for lists)
     * Fetches member, provider and service info for complete display
     */
    private PreAuthorizationResponseDto mapToResponseDtoLight(PreAuthorization preAuth) {
        // Fetch related entities for complete display
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        MedicalService service = preAuth.getMedicalService();

        // Fallback: try to find service by code if not loaded
        if (service == null && preAuth.getServiceCode() != null) {
            service = medicalServiceRepository.findByCode(preAuth.getServiceCode()).orElse(null);
        }

        return mapToResponseDto(preAuth, member, provider, service);
    }

    /**
     * PROVIDER PORTAL (2026-01-14):
     * Validate and enforce provider ID based on user role.
     * 
     * Rules (HARDENED 2026-01-16):
     * - PROVIDER users: providerId ALWAYS comes from ProviderContextGuard (session)
     * ANY providerId from request is IGNORED to prevent data leakage
     * - SUPER_ADMIN/INSURANCE_ADMIN can set any providerId
     * - Other users can set any providerId
     * 
     * @param dto         The pre-authorization creation DTO
     * @param currentUser The currently authenticated user
     */
    private void validateAndEnforceProviderId(PreAuthorizationCreateDto dto, User currentUser) {
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

            log.info("🔒 PROVIDER {} creating pre-auth with their providerId: {} (enforced by ProviderContextGuard)",
                    currentUser.getUsername(), userProviderId);
        } else if (authorizationService.isSuperAdmin(currentUser)
                || authorizationService.isInsuranceAdmin(currentUser)) {
            // SUPER_ADMIN and INSURANCE_ADMIN can set any provider
            log.info("🔓 ADMIN user {} creating pre-auth - any providerId allowed", currentUser.getUsername());
        }
        // Other roles: no restriction on providerId
    }
    /**
     * Search pre-authorizations
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> search(String query, Pageable pageable) {
        log.debug("Searching pre-authorizations with query: {}", query);
        if (query == null || query.isBlank()) {
            return getAllPreAuthorizations(pageable);
        }
        return preAuthorizationRepository.search(query, pageable)
                .map(pa -> mapToResponseDto(pa, 
                    memberRepository.findById(pa.getMemberId()).orElse(null),
                    providerRepository.findById(pa.getProviderId()).orElse(null),
                    pa.getMedicalService()));
    }
}
