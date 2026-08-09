package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.service.BusinessDaysCalculatorService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.service.AuditLogWriteRequest;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.PendingServiceStatus;
import com.waad.tba.modules.claim.event.ClaimApprovalRequestedEvent;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.repository.ClaimPendingServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.settlement.event.ClaimApprovedEvent;
import com.waad.tba.modules.settlement.event.ClaimSettledEvent;
import com.waad.tba.modules.settlement.service.ProviderAccountService;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service dedicated to the review, approval, and settlement of claims.
 * Refactored from ClaimService to improve maintainability and follow SRP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClaimReviewService {

    private final ClaimRepository claimRepository;
    private final ClaimPendingServiceRepository pendingServiceRepository;
    private final ClaimMapper claimMapper;
    private final MemberRepository memberRepository;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final ClaimReversalOrchestrator claimReversalOrchestrator;
    private final ClaimStateMachine claimStateMachine;
    private final BusinessDaysCalculatorService businessDaysCalculator;
    private final ClaimAuditService claimAuditService;
    private final MedicalAuditLogService medicalAuditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProviderAccountService providerAccountService;
    private final ClaimApprovalRecoveryWorker approvalRecoveryWorker;
    private final ClaimFinancialSnapshotService financialSnapshotService;

    /**
     * Start review of a submitted claim.
     */
    @Transactional
    public ClaimViewDto startReview(Long id) {
        log.info("📋 Starting review of claim {}", id);
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();
        BigDecimal previousApprovedAmount = claim.getApprovedAmount();
        BigDecimal previousNetProviderAmount = claim.getNetProviderAmount();

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    String.format("لا يمكن بدء المراجعة. الحالة الحالية: %s، المطلوب: SUBMITTED", claim.getStatus()));
        }

        claimStateMachine.transition(
                claim,
                ClaimStatus.UNDER_REVIEW,
                currentUser,
                buildTransitionContext(claim, ClaimStatus.UNDER_REVIEW, null, "تم استلام المطالبة للمراجعة", false,
                        null));
        claim.setReviewedById(currentUser != null ? currentUser.getId() : null);
        claim.setReviewedBy(currentUser != null ? currentUser.getUsername() : "system");

        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.IN_PROGRESS);
        }

        Claim savedClaim = claimRepository.save(claim);
        claimAuditService.recordStatusChange(savedClaim, previousStatus, currentUser, "تم استلام المطالبة للمراجعة");
        recordMedicalAudit(
                savedClaim,
                AuditAction.STATUS_CHANGE,
                "تم استلام المطالبة للمراجعة",
                snapshot(previousStatus, previousApprovedAmount, previousNetProviderAmount),
                snapshot(savedClaim.getStatus(), savedClaim.getApprovedAmount(), savedClaim.getNetProviderAmount()),
                AuditSource.USER);
        return claimMapper.toViewDto(savedClaim);
    }

    /** Pause an internal review without returning the claim to the provider. */
    @Transactional
    public ClaimViewDto pauseReview(Long id, String reason) {
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        User currentUser = authorizationService.getCurrentUser();
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());

        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("يمكن تعليق المراجعة فقط عندما تكون المطالبة قيد المراجعة");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب تعليق المراجعة مطلوب");
        }

        claim.setReviewPaused(true);
        claim.setReviewPauseReason(reason.trim());
        claim.setReviewPausedAt(LocalDateTime.now());
        claim.setReviewPausedBy(currentUser != null ? currentUser.getUsername() : "system");
        Claim saved = claimRepository.save(claim);
        claimAuditService.recordStatusChange(saved, ClaimStatus.UNDER_REVIEW, currentUser,
                "تعليق داخلي للمراجعة: " + reason.trim());
        return claimMapper.toViewDto(saved);
    }

    /** Resume a previously paused internal review. */
    @Transactional
    public ClaimViewDto resumeReview(Long id) {
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        User currentUser = authorizationService.getCurrentUser();
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());

        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW || !Boolean.TRUE.equals(claim.getReviewPaused())) {
            throw new BusinessRuleException("المطالبة ليست مراجعة معلقة");
        }

        claim.setReviewPaused(false);
        claim.setReviewPauseReason(null);
        claim.setReviewPausedAt(null);
        claim.setReviewPausedBy(null);
        Claim saved = claimRepository.save(claim);
        claimAuditService.recordStatusChange(saved, ClaimStatus.UNDER_REVIEW, currentUser, "استئناف المراجعة");
        return claimMapper.toViewDto(saved);
    }

    /**
     * Re-open an already approved internal claim for correction.
     *
     * This is a financial reversal, not an internal review pause: the benefit
     * consumption and provider credit are neutralized before the claim becomes
     * editable. Re-approval creates a fresh, auditable financial cycle.
     */
    @Transactional
    public ClaimViewDto requestCorrection(Long id, String reason) {
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        User currentUser = authorizationService.getCurrentUser();
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BusinessRuleException("يمكن إعادة فتح المطالبة للتصحيح فقط عندما تكون معتمدة");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب إعادة فتح المطالبة للتصحيح مطلوب");
        }
        ClaimStatus previousStatus = claim.getStatus();
        BigDecimal previousApprovedAmount = claim.getApprovedAmount();
        BigDecimal previousNetProviderAmount = claim.getNetProviderAmount();

        claimReversalOrchestrator.reverseClaim(
                id, currentUser != null ? currentUser.getId() : null);
        claim.setReviewerComment(reason.trim());
        claim.setReviewPaused(false);
        claim.setReviewPauseReason(null);
        claim.setReviewPausedAt(null);
        claim.setReviewPausedBy(null);
        claimStateMachine.transition(claim, ClaimStatus.NEEDS_CORRECTION, currentUser);

        // A correction is not a payable financial state. Keep the line-level
        // adjudication trail, but remove claim-level payable figures until the
        // corrected claim is recalculated and approved again.
        claim.setApprovedAmount(null);
        claim.setPatientCoPay(null);
        claim.setNetProviderAmount(null);
        claim.setCompanyDiscountAmount(null);
        claim.setRefusedAmount(null);
        Claim saved = claimRepository.save(claim);

        claimAuditService.recordStatusChange(saved, previousStatus, currentUser,
                "إعادة فتح للتصحيح: " + reason.trim());
        recordMedicalAudit(saved, AuditAction.STATUS_CHANGE, "إعادة فتح المطالبة للتصحيح",
                snapshot(previousStatus, previousApprovedAmount, previousNetProviderAmount),
                snapshot(saved.getStatus(), saved.getApprovedAmount(), saved.getNetProviderAmount()),
                AuditSource.USER);

        return claimMapper.toViewDto(saved);
    }

    /**
     * Request approval (Split-Phase Phase 1).
     */
    @Transactional
    public ClaimViewDto requestApproval(Long id, ClaimApproveDto dto) {
        log.info("🚀 [SPLIT-PHASE] Phase 1: Requesting approval for claim {}", id);
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
        ClaimStatus previousStatus = claim.getStatus();
        BigDecimal previousApprovedAmount = claim.getApprovedAmount();
        BigDecimal previousNetProviderAmount = claim.getNetProviderAmount();

        User currentUser = resolveWorkflowUser(authorizationService.getCurrentUser());

        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW && claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    "لا يمكن الموافقة على المطالبة في الحالة الحالية: " + claim.getStatus().getArabicLabel());
        }

        if (Boolean.TRUE.equals(claim.getReviewPaused())) {
            throw new BusinessRuleException("يجب استئناف المراجعة المعلقة قبل اعتماد المطالبة");
        }

        if (pendingServiceRepository.existsByClaimIdAndStatusIn(claim.getId(), List.of(
                PendingServiceStatus.PRELIMINARY,
                PendingServiceStatus.NEEDS_INFO,
                PendingServiceStatus.SPLIT_REQUIRED))) {
            throw new BusinessRuleException(
                    "لا يمكن اعتماد المطالبة قبل حسم جميع الخدمات الجديدة من رئيس قسم المراجعين أو مدير التأمين");
        }

        applyLineReviewDecisions(claim, dto.getLineDecisions());

        if (claim.getStatus() == ClaimStatus.SUBMITTED) {
            claimStateMachine.transition(
                    claim,
                    ClaimStatus.UNDER_REVIEW,
                    currentUser,
                    buildTransitionContext(claim, ClaimStatus.UNDER_REVIEW, null,
                            "Auto transition before approval request", false, null));
        }

        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            claim.setReviewerComment(dto.getNotes());
        }

        claimStateMachine.transition(
                claim,
                ClaimStatus.APPROVAL_IN_PROGRESS,
                currentUser,
                buildTransitionContext(claim, ClaimStatus.APPROVAL_IN_PROGRESS, null, dto.getNotes(), false, null));
        Claim savedClaim = claimRepository.save(claim);

        recordMedicalAudit(
                savedClaim,
                AuditAction.STATUS_CHANGE,
                "انتقال إلى APPROVAL_IN_PROGRESS",
                snapshot(previousStatus, previousApprovedAmount, previousNetProviderAmount),
                snapshot(savedClaim.getStatus(), savedClaim.getApprovedAmount(), savedClaim.getNetProviderAmount()),
                AuditSource.USER);

        eventPublisher.publishEvent(new ClaimApprovalRequestedEvent(
                id,
                dto,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "system-async",
                currentUser != null ? currentUser.getUserType() : "ACCOUNTANT"));
        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Persist the reviewer's line decisions before financial adjudication.  The
     * request must cover every persisted line exactly once; otherwise the claim
     * cannot be approved. Amounts remain backend-owned.
     */
    private void applyLineReviewDecisions(Claim claim, List<ClaimApproveDto.LineDecision> decisions) {
        List<ClaimLine> lines = claim.getLines() == null ? List.of() : claim.getLines();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("لا يمكن اعتماد مطالبة بدون بنود");
        }
        if (decisions == null || decisions.size() != lines.size()) {
            throw new BusinessRuleException("يجب اتخاذ قرار واضح لكل بند قبل اعتماد المطالبة");
        }

        Map<Long, ClaimApproveDto.LineDecision> byLineId = new HashMap<>();
        Set<Long> duplicates = new HashSet<>();
        for (ClaimApproveDto.LineDecision decision : decisions) {
            if (decision == null || decision.getLineId() == null || decision.getDecision() == null) {
                throw new BusinessRuleException("بيانات قرار أحد البنود غير مكتملة");
            }
            if (byLineId.put(decision.getLineId(), decision) != null) {
                duplicates.add(decision.getLineId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BusinessRuleException("يوجد قرار مكرر لبند في المطالبة");
        }

        for (ClaimLine line : lines) {
            ClaimApproveDto.LineDecision decision = byLineId.remove(line.getId());
            if (decision == null) {
                throw new BusinessRuleException("لم يُتخذ قرار للبند رقم " + line.getId());
            }

            if (decision.getDecision() == ClaimLineReviewDecision.REJECT) {
                if (decision.getReason() == null || decision.getReason().isBlank()) {
                    throw new BusinessRuleException("سبب رفض البند رقم " + line.getId() + " مطلوب");
                }
                line.setRejected(true);
                line.setRejectionReason(decision.getReason().trim());
                line.setReviewerNotes(decision.getReason().trim());
            } else {
                // Approval accepts the engine result; it never clears an existing
                // system rejection or changes a system-calculated amount.
                line.setReviewerNotes(decision.getReason() == null ? null : decision.getReason().trim());
            }
        }

        if (!byLineId.isEmpty()) {
            throw new BusinessRuleException("توجد قرارات لبنود لا تنتمي إلى هذه المطالبة");
        }
    }

    /**
     * Process approval (Split-Phase Phase 2 - Async).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public void processApproval(
            Long id,
            ClaimApproveDto dto,
            Long actorId,
            String actorUsername,
            String actorType) {
        log.info("⚙️ [SPLIT-PHASE] Phase 2: Starting async approval processing for claim {}", id);

        try {
            Claim claim = claimRepository.findByIdForFinancialUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));
            BigDecimal previousApprovedAmount = claim.getApprovedAmount();
            BigDecimal previousNetProviderAmount = claim.getNetProviderAmount();

            User currentUser = User.builder()
                    .id(actorId)
                    .username(actorUsername != null ? actorUsername : "system-async")
                    .userType(actorType != null ? actorType : "ACCOUNTANT")
                    .build();

            LocalDate serviceDate = claim.getServiceDate() != null ? claim.getServiceDate() : LocalDate.now();
            benefitPolicyCoverageService.validateMemberHasActivePolicy(claim.getMember(), serviceDate);

            // One canonical source for contract pricing, coverage, limits and the
            // reviewer's line decisions. This must run before any claim-level
            // deductible adjustment reads the financial fields.
            claimMapper.recalculateForApproval(claim);

            boolean allLinesRejected = claim.getLines().stream()
                    .allMatch(line -> Boolean.TRUE.equals(line.getRejected())
                            || line.getCompanyShare() == null
                            || line.getCompanyShare().signum() == 0);
            if (allLinesRejected) {
                claim.setApprovedAmount(BigDecimal.ZERO);
                claim.setNetProviderAmount(BigDecimal.ZERO);
                claim.setPatientCoPay(BigDecimal.ZERO);
                claim.setReviewerComment(dto.getNotes() != null && !dto.getNotes().isBlank()
                        ? dto.getNotes()
                        : "جميع بنود المطالبة مرفوضة");
                claimStateMachine.transition(
                        claim,
                        ClaimStatus.REJECTED,
                        currentUser,
                        buildTransitionContext(claim, ClaimStatus.REJECTED, BigDecimal.ZERO,
                                claim.getReviewerComment(), false, true));
                Claim rejectedClaim = claimRepository.save(claim);
                claimAuditService.recordStatusChange(rejectedClaim, ClaimStatus.APPROVAL_IN_PROGRESS,
                        currentUser, claim.getReviewerComment());
                recordMedicalAudit(rejectedClaim, AuditAction.REJECTED, claim.getReviewerComment(),
                        snapshot(ClaimStatus.APPROVAL_IN_PROGRESS, previousApprovedAmount, previousNetProviderAmount),
                        snapshot(ClaimStatus.REJECTED, BigDecimal.ZERO, BigDecimal.ZERO), AuditSource.SYSTEM);
                log.info("✅ Claim {} rejected because all lines were rejected", id);
                return;
            }

            BigDecimal approvedAmount = financialSnapshotService.finalizeSnapshot(claim);

            claimStateMachine.transition(
                    claim,
                    ClaimStatus.APPROVED,
                    currentUser,
                    buildTransitionContext(claim, ClaimStatus.APPROVED, approvedAmount, dto.getNotes(), false, true));

            if (claim.getVisit() != null) {
                claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.COMPLETED);
            }

            LocalDate completionDate = LocalDate.now();
            claim.setActualCompletionDate(completionDate);

            if (claim.getExpectedCompletionDate() != null && claim.getSlaDaysConfigured() != null) {
                LocalDate submissionDate = claim.getCreatedAt().toLocalDate();
                int daysTaken = businessDaysCalculator.calculateBusinessDays(submissionDate, completionDate);
                claim.setBusinessDaysTaken(daysTaken);
                claim.setWithinSla(daysTaken <= claim.getSlaDaysConfigured());
            }

            Claim savedClaim = claimRepository.save(claim);

            if (savedClaim.getProviderId() != null) {
                eventPublisher.publishEvent(new ClaimApprovedEvent(this, savedClaim.getId(), savedClaim.getProviderId(),
                        currentUser != null ? currentUser.getId() : null));
            }

            claimAuditService.recordApproval(savedClaim, ClaimStatus.APPROVAL_IN_PROGRESS, null, currentUser,
                    dto.getNotes());
            recordMedicalAudit(
                    savedClaim,
                    AuditAction.APPROVED,
                    dto.getNotes(),
                    snapshot(ClaimStatus.APPROVAL_IN_PROGRESS, previousApprovedAmount, previousNetProviderAmount),
                    snapshot(savedClaim.getStatus(), savedClaim.getApprovedAmount(), savedClaim.getNetProviderAmount()),
                    AuditSource.SYSTEM);
            log.info("✅ [SPLIT-PHASE] Phase 2 complete: Claim {} approved successfully", id);

        } catch (Exception e) {
            log.error("❌ [SPLIT-PHASE] Phase 2 failed for claim {}: {}", id, e.getMessage(), e);
            revertToUnderReview(id, e.getMessage());
        }
    }

    private void revertToUnderReview(Long id, String errorMessage) {
        try {
            approvalRecoveryWorker.recover(id);
        } catch (Exception recoveryError) {
            log.error("CRITICAL: approval failed and claim {} could not be recovered. Original error: {}",
                    id, errorMessage, recoveryError);
        }
    }

    /**
     * Reject a claim.
     */
    @Transactional
    public ClaimViewDto rejectClaim(Long id, ClaimRejectDto dto) {
        log.info("❌ Rejecting claim {}", id);
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId());

        ClaimStatus previousStatus = claim.getStatus();
        BigDecimal previousApprovedAmount = claim.getApprovedAmount();
        BigDecimal previousNetProviderAmount = claim.getNetProviderAmount();
        if (previousStatus != ClaimStatus.SUBMITTED && previousStatus != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("لا يمكن رفض المطالبة في حالتها الحالية");
        }
        if (Boolean.TRUE.equals(claim.getReviewPaused())) {
            throw new BusinessRuleException("يجب استئناف المراجعة المعلقة قبل رفض المطالبة");
        }

        if (dto.getRejectionReason() == null || dto.getRejectionReason().trim().isEmpty()) {
            throw new BusinessRuleException("سبب الرفض مطلوب");
        }

        claim.setReviewerComment(dto.getRejectionReason());
        claim.setApprovedAmount(BigDecimal.ZERO);
        claim.setNetProviderAmount(BigDecimal.ZERO);

        // B-01 FIX: Zero out the deductible snapshot on the rejected claim.
        // The system calculates remaining deductible via sumDeductibleForYear()
        // which only sums APPROVED/SETTLED claims. Rejecting automatically excludes
        // this claim from future deductible calculations. We zero out the field
        // here for audit trail clarity and to prevent stale data if the claim
        // is ever re-submitted via NEEDS_CORRECTION flow.
        if (claim.getDeductibleApplied() != null
                && claim.getDeductibleApplied().compareTo(BigDecimal.ZERO) > 0) {
            log.info("↩️ Zeroing deductible on rejection: claim={}, previousDeductible={}",
                    id, claim.getDeductibleApplied());
            claim.setDeductibleApplied(BigDecimal.ZERO);
        }
        claim.setPatientCoPay(BigDecimal.ZERO);

        claimStateMachine.transition(
                claim,
                ClaimStatus.REJECTED,
                currentUser,
                buildTransitionContext(claim, ClaimStatus.REJECTED, null, dto.getRejectionReason(), false, null));

        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.CANCELLED);
        }

        LocalDate completionDate = LocalDate.now();
        claim.setActualCompletionDate(completionDate);

        if (claim.getExpectedCompletionDate() != null && claim.getSlaDaysConfigured() != null) {
            LocalDate submissionDate = claim.getCreatedAt().toLocalDate();
            int daysTaken = businessDaysCalculator.calculateBusinessDays(submissionDate, completionDate);
            claim.setBusinessDaysTaken(daysTaken);
            claim.setWithinSla(daysTaken <= claim.getSlaDaysConfigured());
        }

        Claim savedClaim = claimRepository.save(claim);
        claimAuditService.recordRejection(savedClaim, previousStatus, currentUser, dto.getRejectionReason());
        recordMedicalAudit(
                savedClaim,
                AuditAction.REJECTED,
                dto.getRejectionReason(),
                snapshot(previousStatus, previousApprovedAmount, previousNetProviderAmount),
                snapshot(savedClaim.getStatus(), savedClaim.getApprovedAmount(), savedClaim.getNetProviderAmount()),
                AuditSource.USER);
        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Settle a claim.
     */
    @Transactional
    public ClaimViewDto settleClaim(Long id, ClaimSettleDto dto) {
        log.info("💳 Settling claim {}", id);
        Claim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();
        BigDecimal previousApprovedAmount = claim.getApprovedAmount();
        BigDecimal previousNetProviderAmount = claim.getNetProviderAmount();

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BusinessRuleException("لا يمكن تسوية المطالبة. يجب أن تكون: APPROVED");
        }

        if (dto.getPaymentReference() == null || dto.getPaymentReference().trim().isEmpty()) {
            throw new BusinessRuleException("رقم مرجع الدفع مطلوب");
        }

        BigDecimal netProviderAmount = claim.getNetProviderAmount() != null ? claim.getNetProviderAmount()
                : claim.getApprovedAmount();
        if (dto.getSettlementAmount() != null) {
            if (dto.getSettlementAmount().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessRuleException("مبلغ التسوية يجب أن يكون أكبر من صفر");
            if (dto.getSettlementAmount().compareTo(netProviderAmount) > 0)
                throw new BusinessRuleException("مبلغ التسوية يتجاوز المبلغ المستحق للمقدم");
        }

        claim.setPaymentReference(dto.getPaymentReference());
        claim.setSettledAt(LocalDateTime.now());
        if (dto.getNotes() != null)
            claim.setSettlementNotes(dto.getNotes());

        // M4: Record the amount actually paid for audit/reporting purposes
        BigDecimal settledAmount = dto.getSettlementAmount() != null ? dto.getSettlementAmount() : netProviderAmount;
        claim.setPaidAmount(settledAmount);

        claimStateMachine.transition(
                claim,
                ClaimStatus.SETTLED,
                currentUser,
                buildTransitionContext(claim, ClaimStatus.SETTLED, claim.getApprovedAmount(), dto.getNotes(), false,
                        null));

        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.COMPLETED);
        }

        Claim savedClaim = claimRepository.save(claim);

        // FIX #8 (Critical): Debit MUST succeed for settlement to be valid.
        // If debitOnClaimSettlement() throws, the @Transactional on this method will
        // roll back the entire settlement — claim stays APPROVED, no orphaned SETTLED
        // state.
        Long userId = currentUser != null ? currentUser.getId() : null;
        providerAccountService.debitOnClaimSettlement(savedClaim.getId(), userId);
        log.info("✅ Provider account debited successfully for settled claim {}", savedClaim.getId());

        // Publish settlement event (AFTER_COMMIT) so downstream listeners react
        // to a durably-settled claim and its provider-account debit.
        eventPublisher.publishEvent(new ClaimSettledEvent(
                this,
                savedClaim.getId(),
                savedClaim.getProviderId(),
                userId,
                settledAmount));

        claimAuditService.recordSettlement(savedClaim, currentUser);
        recordMedicalAudit(
                savedClaim,
                AuditAction.STATUS_CHANGE,
                "تمت التسوية برقم المرجع: " + dto.getPaymentReference(),
                snapshot(previousStatus, previousApprovedAmount, previousNetProviderAmount),
                snapshot(savedClaim.getStatus(), savedClaim.getApprovedAmount(), savedClaim.getNetProviderAmount()),
                AuditSource.USER);
        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Inbox access for pending claims.
     */
    @Transactional(readOnly = true)
    public Page<ClaimViewDto> getPendingClaims(int page, int size, String sortBy, String sortDir, Long providerId) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        List<ClaimStatus> pendingStatuses = List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);

        User currentUser = authorizationService.getCurrentUser();
        Page<Claim> claims;

        // PROVIDER_STAFF must never see another provider's inbox: the client-supplied providerId
        // is IGNORED and overridden with the caller's own. Without this, omitting providerId
        // returned every pending claim in the system, and passing another provider's ID returned
        // that provider's inbox instead of a 403.
        Long enforcedProviderId = authorizationService.getProviderFilterForUser(currentUser);
        if (enforcedProviderId != null) {
            providerId = enforcedProviderId;
        }

        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            if (providerId == null)
                throw new BusinessRuleException("providerId is required");
            reviewerIsolationService.validateReviewerAccess(currentUser, providerId);
            claims = claimRepository.findByStatusInAndReviewerProviders(List.of(providerId), pendingStatuses, pageable);
        } else {
            if (providerId != null)
                claims = claimRepository.findByStatusInAndReviewerProviders(List.of(providerId), pendingStatuses,
                        pageable);
            else
                claims = claimRepository.findByStatusIn(pendingStatuses, pageable);
        }

        return claims.map(claimMapper::toViewDto);
    }

    /**
     * Get claims ready for settlement (APPROVED status).
     */
    @Transactional(readOnly = true)
    public Page<ClaimViewDto> getApprovedClaims(int page, int size, String sortBy, String sortDir, Long providerId) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        User currentUser = authorizationService.getCurrentUser();
        Page<Claim> claims;

        // Same enforcement as getPendingClaims(): a provider must never see another provider's
        // settlement inbox, whether by omitting providerId (saw everyone's) or passing someone
        // else's ID (saw theirs instead of being denied).
        Long enforcedProviderId = authorizationService.getProviderFilterForUser(currentUser);
        if (enforcedProviderId != null) {
            providerId = enforcedProviderId;
        }

        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            if (providerId == null)
                throw new BusinessRuleException("providerId is required");
            reviewerIsolationService.validateReviewerAccess(currentUser, providerId);
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

    private User resolveWorkflowUser(User currentUser) {
        if (currentUser != null)
            return currentUser;
        return User.builder().username("system-async").userType("ACCOUNTANT").build();
    }

    private void recordMedicalAudit(
            Claim claim,
            AuditAction action,
            String reason,
            Map<String, Object> before,
            Map<String, Object> after,
            AuditSource source) {
        medicalAuditLogService.record(AuditLogWriteRequest.builder()
                .entityType(EntityType.CLAIM)
                .entityId(String.valueOf(claim.getId()))
                .action(action)
                .reason(reason)
                .beforeState(before)
                .afterState(after)
                .source(source)
                .build());
    }

    private AuditAction mapAction(ClaimStatus status) {
        if (status == ClaimStatus.APPROVED) {
            return AuditAction.APPROVED;
        }
        if (status == ClaimStatus.REJECTED) {
            return AuditAction.REJECTED;
        }
        return AuditAction.STATUS_CHANGE;
    }

    private Map<String, Object> snapshot(ClaimStatus status, BigDecimal approvedAmount, BigDecimal netProviderAmount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status != null ? status.name() : null);
        data.put("approvedAmount", approvedAmount);
        data.put("netProviderAmount", netProviderAmount);
        return data;
    }

    private ClaimStateMachine.TransitionContext buildTransitionContext(
            Claim claim,
            ClaimStatus targetStatus,
            BigDecimal totalApproved,
            String reason,
            boolean pendingRecalculation,
            Boolean coverageUpToDateOverride) {

        boolean claimComplete = isClaimComplete(claim);
        boolean allLinesCalculated = areAllLinesCalculated(claim);
        boolean coverageUpToDate = coverageUpToDateOverride != null
                ? coverageUpToDateOverride
                : isCoverageUpToDate(claim);

        BigDecimal effectiveTotalApproved = totalApproved != null ? totalApproved : claim.getApprovedAmount();

        boolean effectivePendingRecalculation = Boolean.TRUE.equals(claim.getPendingRecalculation())
                || pendingRecalculation;

        // For non-approval transitions these values are informational only.
        if (targetStatus != ClaimStatus.APPROVED) {
            effectivePendingRecalculation = false;
        }

        return new ClaimStateMachine.TransitionContext(
                effectiveTotalApproved,
                claimComplete,
                allLinesCalculated,
                effectivePendingRecalculation,
                coverageUpToDate,
                reason);
    }

    private boolean isClaimComplete(Claim claim) {
        return claim != null
                && claim.getMember() != null
                && claim.getProviderId() != null
                && claim.getServiceDate() != null
                && claim.getLines() != null
                && !claim.getLines().isEmpty();
    }

    private boolean areAllLinesCalculated(Claim claim) {
        if (claim == null || claim.getLines() == null || claim.getLines().isEmpty()) {
            return false;
        }
        return claim.getLines().stream().allMatch(this::isLineCalculated);
    }

    private boolean isCoverageUpToDate(Claim claim) {
        if (claim == null || claim.getLines() == null || claim.getLines().isEmpty()) {
            return false;
        }
        return claim.getLines().stream().allMatch(line -> line.getCoveragePercentSnapshot() != null);
    }

    private boolean isLineCalculated(ClaimLine line) {
        return line != null
                && line.getQuantity() != null
                && line.getQuantity() > 0
                && line.getUnitPrice() != null
                && line.getTotalPrice() != null;
    }
}
