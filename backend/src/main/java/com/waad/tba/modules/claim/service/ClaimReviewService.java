package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.service.BusinessDaysCalculatorService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private final ClaimMapper claimMapper;
    private final MemberRepository memberRepository;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;
    private final AtomicFinancialService atomicFinancialService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final ClaimStateMachine claimStateMachine;
    private final BusinessDaysCalculatorService businessDaysCalculator;
    private final ClaimAuditService claimAuditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProviderAccountService providerAccountService;

    /**
     * Generic review action (Phase 0).
     */
    @Transactional
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
     * Start review of a submitted claim.
     */
    @Transactional
    public ClaimViewDto startReview(Long id) {
        log.info("📋 Starting review of claim {}", id);
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = authorizationService.getCurrentUser();
        ClaimStatus previousStatus = claim.getStatus();

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    String.format("لا يمكن بدء المراجعة. الحالة الحالية: %s، المطلوب: SUBMITTED", claim.getStatus()));
        }

        claimStateMachine.transition(claim, ClaimStatus.UNDER_REVIEW, currentUser);

        if (claim.getVisit() != null) {
            claim.getVisit().setStatus(com.waad.tba.modules.visit.entity.VisitStatus.IN_PROGRESS);
        }

        Claim savedClaim = claimRepository.save(claim);
        claimAuditService.recordStatusChange(savedClaim, previousStatus, currentUser, "تم استلام المطالبة للمراجعة");
        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Request approval (Split-Phase Phase 1).
     */
    @Transactional
    public ClaimViewDto requestApproval(Long id, ClaimApproveDto dto) {
        log.info("🚀 [SPLIT-PHASE] Phase 1: Requesting approval for claim {}", id);
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

        User currentUser = resolveWorkflowUser(authorizationService.getCurrentUser());

        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW && claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    "لا يمكن الموافقة على المطالبة في الحالة الحالية: " + claim.getStatus().getArabicLabel());
        }

        if (claim.getStatus() == ClaimStatus.SUBMITTED) {
            claimStateMachine.transition(claim, ClaimStatus.UNDER_REVIEW, currentUser);
        }

        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            claim.setReviewerComment(dto.getNotes());
        }

        claimStateMachine.transition(claim, ClaimStatus.APPROVAL_IN_PROGRESS, currentUser);
        Claim savedClaim = claimRepository.save(claim);

        processApprovalAsync(id, dto);
        return claimMapper.toViewDto(savedClaim);
    }

    /**
     * Process approval (Split-Phase Phase 2 - Async).
     */
    @Async("approvalTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public void processApprovalAsync(Long id, ClaimApproveDto dto) {
        log.info("⚙️ [SPLIT-PHASE] Phase 2: Starting async approval processing for claim {}", id);

        try {
            Claim claim = claimRepository.findByIdForFinancialUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", id));

            User currentUser = resolveWorkflowUser(authorizationService.getCurrentUser());

            CostCalculationService.CostBreakdown breakdown = atomicFinancialService
                    .calculateCostsWithAtomicDeductible(claim);

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

            BigDecimal approvedAmount;
            if (Boolean.TRUE.equals(dto.getUseSystemCalculation()) || dto.getApprovedAmount() == null) {
                approvedAmount = systemNetProvider;
            } else {
                approvedAmount = dto.getApprovedAmount();
            }

            atomicFinancialService.validatePositiveAmount(approvedAmount, "المبلغ المعتمد (Approved Amount)");
            atomicFinancialService.validateApprovedAmount(approvedAmount, netAcceptedAmount);

            BigDecimal patientCoPay = netAcceptedAmount.subtract(approvedAmount);
            BigDecimal netProviderAmount = approvedAmount;

            if (patientCoPay.add(netProviderAmount).compareTo(netAcceptedAmount) != 0) {
                netProviderAmount = netAcceptedAmount.subtract(patientCoPay).max(BigDecimal.ZERO);
                approvedAmount = netProviderAmount;
            }

            Member member = claim.getMember();
            LocalDate serviceDate = claim.getServiceDate() != null ? claim.getServiceDate() : LocalDate.now();

            if (member.getBenefitPolicy() != null) {
                benefitPolicyCoverageService.validateAmountLimits(member, member.getBenefitPolicy(), approvedAmount,
                        serviceDate);
            }

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

            claimStateMachine.transition(claim, ClaimStatus.APPROVED, currentUser);

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
            log.info("✅ [SPLIT-PHASE] Phase 2 complete: Claim {} approved successfully", id);

        } catch (Exception e) {
            log.error("❌ [SPLIT-PHASE] Phase 2 failed for claim {}: {}", id, e.getMessage(), e);
            revertToUnderReview(id, e.getMessage());
        }
    }

    private void revertToUnderReview(Long id, String errorMessage) {
        try {
            Claim failedClaim = claimRepository.findById(id).orElse(null);
            if (failedClaim != null && failedClaim.getStatus() == ClaimStatus.APPROVAL_IN_PROGRESS) {
                failedClaim.setReviewerComment("فشل تقني في المعالجة: " + errorMessage);
                failedClaim.setStatus(ClaimStatus.UNDER_REVIEW);
                failedClaim.setUpdatedBy("system-async");
                claimRepository.save(failedClaim);
            }
        } catch (Exception ignore) {
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
        if (previousStatus != ClaimStatus.SUBMITTED && previousStatus != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("لا يمكن رفض المطالبة في حالتها الحالية");
        }

        if (dto.getRejectionReason() == null || dto.getRejectionReason().trim().isEmpty()) {
            throw new BusinessRuleException("سبب الرفض مطلوب");
        }

        claim.setReviewerComment(dto.getRejectionReason());
        claim.setApprovedAmount(BigDecimal.ZERO);
        claim.setNetProviderAmount(BigDecimal.ZERO);

        claimStateMachine.transition(claim, ClaimStatus.REJECTED, currentUser);

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

        claimStateMachine.transition(claim, ClaimStatus.SETTLED, currentUser);

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
}
