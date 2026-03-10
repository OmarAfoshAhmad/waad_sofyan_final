package com.waad.tba.modules.claim.mapper;

import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ClaimMapper (CANONICAL REBUILD 2026-01-16)
 * 
 * Maps between Claim entities and DTOs.
 * Enforces architectural laws for financial consistency.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ClaimMapper {

    private final ProviderContractService providerContractService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;

    /**
     * Maps CreateClaimDto (from Visit) to a new Claim entity.
     * Enforces contract-first pricing and policy-first coverage.
     */
    public Claim toEntity(ClaimCreateDto dto, Visit visit, Provider provider, PreAuthorization preAuth,
            Map<Long, MedicalService> medicalServiceMap) {
        Claim claim = Claim.builder()
                .visit(visit)
                .member(visit.getMember())
                .providerId(provider.getId())
                .providerName(provider.getName())
                .serviceDate(dto.getServiceDate())
                .diagnosisCode(dto.getDiagnosisCode())
                .diagnosisDescription(dto.getDiagnosisDescription())
                .doctorName(dto.getDoctorName())
                .status(dto.getStatus() != null ? dto.getStatus() : ClaimStatus.SETTLED)
                .complaint(dto.getComplaint())
                .reviewerComment(dto.getRejectionReason())
                .preAuthorization(preAuth)
                .manualCategoryEnabled(dto.getManualCategoryEnabled() != null ? dto.getManualCategoryEnabled() : false)
                .primaryCategoryCode(dto.getPrimaryCategoryCode())
                .isBacklog(visit.getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG)
                .build();

        // Resolve category override from primaryCategoryCode.
        // When manualCategoryEnabled=true → used as hard override for coverage
        // resolution.
        // When manualCategoryEnabled=false → used ONLY as a fallback for unmapped
        // services
        // (medicalService=null) so that appliedCategoryId is never left NULL.
        Long categoryOverrideId = null;
        Long contextCategoryId = null; // fallback for unmapped services
        if (claim.getPrimaryCategoryCode() != null) {
            Long resolvedCatId = medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                    .map(com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory::getId)
                    .orElse(null);
            contextCategoryId = resolvedCatId;
            if (Boolean.TRUE.equals(claim.getManualCategoryEnabled())) {
                categoryOverrideId = resolvedCatId;
                log.info("\uD83C\uDFAF [MAPPER] Manual category override: {} (ID: {})",
                        claim.getPrimaryCategoryCode(), categoryOverrideId);
            }
        }

        BigDecimal totalRequestedAmount = BigDecimal.ZERO;
        List<ClaimLine> lines = new ArrayList<>();

        for (ClaimLineDto lineDto : dto.getLines()) {
            MedicalService medicalService = lineDto.getMedicalServiceId() != null
                    ? medicalServiceMap.get(lineDto.getMedicalServiceId())
                    : null;

            // ARCHITECTURAL LAW: Capture what was requested vs what is allowed by contract.
            BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal resolvedUnitPrice = null;

            boolean isBacklog = visit.getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG;

            String serviceCode = lineDto.getServiceCode();
            String serviceName = lineDto.getServiceName();

            if (medicalService != null) {
                serviceCode = medicalService.getCode();
                serviceName = medicalService.getName();
            }

            log.info("🔍 [MAPPER] Processing line for service '{}'. VisitType: {}, isBacklog: {}, Entered Price: {}",
                    serviceCode, visit.getVisitType(), isBacklog, enteredUnitPrice);

            Long resolvedPricingItemId = lineDto.getPricingItemId();

            if (isBacklog && enteredUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
                resolvedUnitPrice = enteredUnitPrice;
                log.info("ℹ️ [BACKLOG] Using user-provided price as approved base: {}", resolvedUnitPrice);
            } else {
                // Resolve contract price - Use code if medicalService is null
                String codeToLookup = (medicalService != null) ? medicalService.getCode() : lineDto.getServiceCode();

                // FALLBACK: If code is missing but pricingItemId is provided, fetch code from
                // DB
                if (codeToLookup == null && lineDto.getPricingItemId() != null) {
                    codeToLookup = pricingItemRepository.findById(lineDto.getPricingItemId())
                            .map(com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem::getServiceCode)
                            .orElse(null);
                }

                if (codeToLookup != null) {
                    EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                            provider.getId(), codeToLookup, dto.getServiceDate());

                    if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                        resolvedUnitPrice = priceResponse.getContractPrice();
                        resolvedPricingItemId = priceResponse.getPricingItemId();

                        // Capture canonical name/code for unmapped services if missing
                        if (serviceCode == null || "N/A".equals(serviceCode))
                            serviceCode = priceResponse.getServiceCode();
                        if (serviceName == null || "Unknown Service".equals(serviceName))
                            serviceName = priceResponse.getServiceName();
                    } else if (medicalService != null) {
                        // FALLBACK: Use base price
                        resolvedUnitPrice = medicalService.getBasePrice() != null ? medicalService.getBasePrice()
                                : BigDecimal.ZERO;
                    }
                }

                if (resolvedUnitPrice == null) {
                    resolvedUnitPrice = enteredUnitPrice;
                }
            }

            // Resolve coverage snapshot
            Integer coveragePercentSnapshot = lineDto.getCoveragePercent();
            boolean requiresPA = lineDto.getRequiresPA() != null ? lineDto.getRequiresPA() : false;

            // Resolve coverage (support unmapped services via category rules)
            Long targetCategoryId = (categoryOverrideId != null) ? categoryOverrideId
                    : (medicalService != null ? medicalService.getCategoryId() : lineDto.getAppliedCategoryId());

            var coverageResult = benefitPolicyCoverageService.resolveCoverage(
                    claim.getMember().getBenefitPolicy().getId(),
                    medicalService != null ? medicalService.getId() : null,
                    medicalService != null ? medicalService.getCategoryId() : lineDto.getAppliedCategoryId(),
                    categoryOverrideId,
                    claim.getMember().getId(),
                    claim.getServiceDate(),
                    claim.getId());

            if (coverageResult != null) {
                requiresPA = coverageResult.isRequiresPreApproval();
                coveragePercentSnapshot = coverageResult.getCoveragePercent();

                // POPULATE FINANCIAL SNAPSHOTS (Flyway V112)
                lineDto.setBenefitLimit(coverageResult.getAmountLimit());
                lineDto.setUsedAmount(coverageResult.getUsedAmount());
                lineDto.setRemainingAmount(coverageResult.getRemainingAmount());
            }

            // Fetch applied category info: Rule match is MOST specific and should be
            // prioritized
            // for correct limit tracking (e.g. tracking a physio limit even if in
            // 'Outpatient' context)
            // For UNMAPPED services (medicalService=null), fall back to contextCategoryId
            // from
            // primaryCategoryCode so that usage queries can always find these lines.
            Long rawCategoryId = (medicalService != null)
                    ? medicalService.getCategoryId()
                    : (lineDto.getAppliedCategoryId() != null ? lineDto.getAppliedCategoryId() : contextCategoryId);

            Long appliedCategoryId = (coverageResult != null && coverageResult.getMatchingCategoryId() != null)
                    ? coverageResult.getMatchingCategoryId()
                    : (categoryOverrideId != null ? categoryOverrideId : rawCategoryId);

            String appliedCategoryName = (appliedCategoryId != null)
                    ? medicalCategoryRepository.findById(appliedCategoryId).map(c -> c.getName())
                            .orElse("Unknown Category")
                    : lineDto.getServiceCategoryName();

            if (isBacklog && coveragePercentSnapshot == null) {
                coveragePercentSnapshot = 100;
            }

            Integer patientCopayPercentSnapshot = coveragePercentSnapshot != null ? (100 - coveragePercentSnapshot)
                    : null;

            Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
            BigDecimal quantityBd = BigDecimal.valueOf(quantity);

            BigDecimal lineRequestedTotal = enteredUnitPrice.multiply(quantityBd);
            BigDecimal lineApprovedBase = resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice;

            BigDecimal priceExcessRefusal = BigDecimal.ZERO;
            if (enteredUnitPrice.compareTo(lineApprovedBase) > 0) {
                priceExcessRefusal = enteredUnitPrice.subtract(lineApprovedBase).multiply(quantityBd);
            }

            boolean isRejected = Boolean.TRUE.equals(lineDto.getRejected());
            BigDecimal lineRefused = isRejected ? lineRequestedTotal
                    : (lineDto.getRefusedAmount() != null ? lineDto.getRefusedAmount().max(priceExcessRefusal)
                            : priceExcessRefusal);

            ClaimLine line = ClaimLine.builder()
                    .claim(claim)
                    .medicalService(medicalService)
                    .serviceCode(serviceCode != null ? serviceCode : "N/A")
                    .serviceName(serviceName != null ? serviceName : "Unknown Service")
                    .pricingItemId(resolvedPricingItemId)
                    .serviceCategoryId(lineDto.getServiceCategoryId())
                    .serviceCategoryName(lineDto.getServiceCategoryName())
                    .appliedCategoryId(appliedCategoryId)
                    .appliedCategoryName(appliedCategoryName)
                    .requiresPA(requiresPA)
                    .coveragePercentSnapshot(coveragePercentSnapshot)
                    .patientCopayPercentSnapshot(patientCopayPercentSnapshot)
                    .quantity(quantity)
                    .unitPrice(lineApprovedBase)
                    .totalPrice(lineApprovedBase.multiply(quantityBd))
                    .rejected(isRejected)
                    .rejectionReason(lineDto.getRejectionReason())
                    .refusedAmount(lineRefused)
                    .requestedUnitPrice(enteredUnitPrice)
                    .requestedQuantity(quantity)
                    .approvedUnitPrice(isRejected ? BigDecimal.ZERO : lineApprovedBase)
                    .approvedQuantity(isRejected ? 0 : quantity)
                    // Set the financial snapshots to the entity (Flyway V112)
                    .benefitLimit(lineDto.getBenefitLimit())
                    .usedAmountSnapshot(lineDto.getUsedAmount())
                    .remainingAmountSnapshot(lineDto.getRemainingAmount())
                    .build();

            lines.add(line);
            totalRequestedAmount = totalRequestedAmount.add(lineRequestedTotal);
        }

        claim.setLines(lines);
        claim.setRequestedAmount(totalRequestedAmount);

        // Pre-calculate financial snapshots if created AS-SETTLED
        if (claim.getStatus() == ClaimStatus.SETTLED) {
            BigDecimal totalRefused = lines.stream()
                    .map(l -> l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netAccepted = totalRequestedAmount.subtract(totalRefused);

            // Calculate patient share based on line snapshots
            // patientCoPay is applied only to the accepted portion (totalPrice -
            // refusedAmount)
            BigDecimal totalPatientShare = lines.stream()
                    .filter(l -> !Boolean.TRUE.equals(l.getRejected()))
                    .map(l -> {
                        if (l.getPatientCopayPercentSnapshot() == null)
                            return BigDecimal.ZERO;
                        BigDecimal requestedPrice = (l.getRequestedUnitPrice() != null ? l.getRequestedUnitPrice()
                                : l.getUnitPrice()).multiply(BigDecimal.valueOf(l.getQuantity()));
                        BigDecimal acceptedPrice = requestedPrice.subtract(
                                l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO)
                                .max(BigDecimal.ZERO);
                        return acceptedPrice.multiply(BigDecimal.valueOf(l.getPatientCopayPercentSnapshot()))
                                .divide(new BigDecimal(100), 2, java.math.RoundingMode.HALF_UP);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalNetProvider = netAccepted.subtract(totalPatientShare);

            claim.setRefusedAmount(totalRefused);
            // approvedAmount is the insurer payable amount (company share) across all
            // code paths.
            claim.setApprovedAmount(totalNetProvider);
            claim.setPatientCoPay(totalPatientShare);
            claim.setNetProviderAmount(totalNetProvider); // Insurance share
            // calculateFields() will be called automatically by @PrePersist/@PreUpdate
        } else {
            claim.setRefusedAmount(BigDecimal.ZERO);
            // Keep non-settled claims nullable to allow lifecycle hooks to derive
            // snapshots consistently from line data.
            claim.setApprovedAmount(null);
        }

        return claim;
    }

    /**
     * Re-applies pricing and coverage to claim lines during draft edit.
     */
    public void replaceClaimLinesForDraft(Claim claim, List<ClaimLineDto> lineDtos,
            Map<Long, MedicalService> medicalServiceMap) {
        BigDecimal totalRequestedAmount = BigDecimal.ZERO;
        BigDecimal totalRefusedAmount = BigDecimal.ZERO;
        BigDecimal totalApprovedAmount = BigDecimal.ZERO;
        BigDecimal totalPatientShare = BigDecimal.ZERO;

        List<ClaimLine> newLines = new ArrayList<>();
        LocalDate serviceDate = claim.getServiceDate() != null ? claim.getServiceDate() : LocalDate.now();

        // Same logic as the create mapper: always derive contextCategoryId from
        // primaryCategoryCode.
        Long categoryOverrideId = null;
        Long contextCategoryId = null;
        if (claim.getPrimaryCategoryCode() != null) {
            Long resolvedCatId = medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                    .map(com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory::getId)
                    .orElse(null);
            contextCategoryId = resolvedCatId;
            if (Boolean.TRUE.equals(claim.getManualCategoryEnabled())) {
                categoryOverrideId = resolvedCatId;
                log.info("\uD83C\uDFAF [REPLACE_DRAFT] Manual category override: {} (ID: {})",
                        claim.getPrimaryCategoryCode(), categoryOverrideId);
            }
        }

        for (ClaimLineDto lineDto : lineDtos) {
            MedicalService medicalService = lineDto.getMedicalServiceId() != null
                    ? medicalServiceMap.get(lineDto.getMedicalServiceId())
                    : null;

            // ARCHITECTURAL LAW: Capture what was requested vs what is allowed by contract.
            BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal resolvedUnitPrice = null;

            boolean isBacklog = claim.getVisit() != null
                    && claim.getVisit().getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG;

            String serviceCode = lineDto.getServiceCode();
            String serviceName = lineDto.getServiceName();

            if (medicalService != null) {
                serviceCode = medicalService.getCode();
                serviceName = medicalService.getName();
            }

            Long resolvedPricingItemId = lineDto.getPricingItemId();

            if (isBacklog && enteredUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
                resolvedUnitPrice = enteredUnitPrice;
            } else {
                String codeToLookup = (medicalService != null) ? medicalService.getCode() : lineDto.getServiceCode();

                // FALLBACK: If code is missing but pricingItemId is provided, fetch code from
                // DB
                if (codeToLookup == null && lineDto.getPricingItemId() != null) {
                    codeToLookup = pricingItemRepository.findById(lineDto.getPricingItemId())
                            .map(com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem::getServiceCode)
                            .orElse(null);
                }

                if (codeToLookup != null) {
                    EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                            claim.getProviderId(), codeToLookup, serviceDate);

                    if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                        resolvedUnitPrice = priceResponse.getContractPrice();
                        resolvedPricingItemId = priceResponse.getPricingItemId();

                        // Capture canonical name/code for unmapped services if missing
                        if (serviceCode == null || "N/A".equals(serviceCode))
                            serviceCode = priceResponse.getServiceCode();
                        if (serviceName == null || "Unknown Service".equals(serviceName))
                            serviceName = priceResponse.getServiceName();
                    } else if (medicalService != null) {
                        // FALLBACK: If draft + no contract, try base price
                        resolvedUnitPrice = medicalService.getBasePrice() != null ? medicalService.getBasePrice()
                                : BigDecimal.ZERO;
                    }
                }

                if (resolvedUnitPrice == null) {
                    resolvedUnitPrice = enteredUnitPrice;
                }
            }

            BigDecimal lineApprovedBase = resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice;
            Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
            BigDecimal quantityBd = BigDecimal.valueOf(quantity);

            // Refusal calculation
            BigDecimal lineRequestedTotal = enteredUnitPrice.multiply(quantityBd);
            BigDecimal lineApprovedTotal = lineApprovedBase.multiply(quantityBd);

            BigDecimal priceExcessRefusal = enteredUnitPrice.compareTo(lineApprovedBase) > 0
                    ? enteredUnitPrice.subtract(lineApprovedBase).multiply(quantityBd)
                    : BigDecimal.ZERO;

            Integer coveragePercentSnapshot = lineDto.getCoveragePercent();
            boolean requiresPA = lineDto.getRequiresPA() != null ? lineDto.getRequiresPA() : false;

            // Resolve coverage (support unmapped services via category rules)
            Long targetCategoryId = (categoryOverrideId != null) ? categoryOverrideId
                    : (medicalService != null ? medicalService.getCategoryId() : lineDto.getAppliedCategoryId());

            var coverageResult = benefitPolicyCoverageService.resolveCoverage(
                    claim.getMember().getBenefitPolicy().getId(),
                    medicalService != null ? medicalService.getId() : null,
                    medicalService != null ? medicalService.getCategoryId() : lineDto.getAppliedCategoryId(),
                    categoryOverrideId,
                    claim.getMember().getId(),
                    claim.getServiceDate(),
                    claim.getId());

            if (coverageResult != null) {
                requiresPA = coverageResult.isRequiresPreApproval();
                coveragePercentSnapshot = coverageResult.getCoveragePercent();
            }

            // Fetch applied category info (either manual or resolved).
            // Same fallback chain as the create mapper.
            Long rawCategoryId = (medicalService != null)
                    ? medicalService.getCategoryId()
                    : (lineDto.getAppliedCategoryId() != null ? lineDto.getAppliedCategoryId() : contextCategoryId);

            Long appliedCategoryId = (categoryOverrideId != null) ? categoryOverrideId : rawCategoryId;

            String appliedCategoryName = (appliedCategoryId != null)
                    ? medicalCategoryRepository.findById(appliedCategoryId).map(c -> c.getName()).orElse(null)
                    : lineDto.getServiceCategoryName();

            // Fallback for backlog claims
            if (claim.getVisit() != null
                    && claim.getVisit().getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG &&
                    coveragePercentSnapshot == null) {
                coveragePercentSnapshot = 100;
            }

            Integer patientCopayPercentSnapshot = coveragePercentSnapshot != null ? (100 - coveragePercentSnapshot)
                    : null;

            BigDecimal lineApproved = BigDecimal.ZERO;
            BigDecimal linePatientShare = BigDecimal.ZERO;
            BigDecimal lineRefused;

            if (Boolean.TRUE.equals(lineDto.getRejected())) {
                lineRefused = lineRequestedTotal;
            } else {
                // Total refusal = Price Excess + (Other refusals like limits if provided)
                lineRefused = lineDto.getRefusedAmount() != null
                        ? lineDto.getRefusedAmount().max(priceExcessRefusal)
                        : priceExcessRefusal;

                // Net Available (Allowed base) = resolvedTotal - (any other refusal beyond
                // price)
                BigDecimal netAvailable = lineApprovedTotal.subtract(lineRefused.subtract(priceExcessRefusal))
                        .max(BigDecimal.ZERO);

                if (coveragePercentSnapshot != null) {
                    // Company Share (Approved) = Net Available * Coverage%
                    lineApproved = netAvailable.multiply(BigDecimal.valueOf(coveragePercentSnapshot))
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

                    // Patient Share (Co-pay) = Net Available - Company Share
                    linePatientShare = netAvailable.subtract(lineApproved);
                } else {
                    lineApproved = netAvailable;
                    linePatientShare = BigDecimal.ZERO;
                }
            }

            ClaimLine line = ClaimLine.builder()
                    .claim(claim)
                    .medicalService(medicalService)
                    .serviceCode(serviceCode != null ? serviceCode : "N/A")
                    .serviceName(serviceName != null ? serviceName : "Unknown Service")
                    .pricingItemId(resolvedPricingItemId)
                    .serviceCategoryId(lineDto.getServiceCategoryId())
                    .serviceCategoryName(lineDto.getServiceCategoryName())
                    .appliedCategoryId(appliedCategoryId)
                    .appliedCategoryName(appliedCategoryName)
                    .requiresPA(requiresPA)
                    .coveragePercentSnapshot(coveragePercentSnapshot)
                    .patientCopayPercentSnapshot(patientCopayPercentSnapshot)
                    .benefitLimit(coverageResult != null ? coverageResult.getAmountLimit() : null)
                    .usedAmountSnapshot(coverageResult != null ? coverageResult.getUsedAmount() : null)
                    .remainingAmountSnapshot(coverageResult != null ? coverageResult.getRemainingAmount() : null)
                    .rejected(lineDto.getRejected() != null ? lineDto.getRejected() : false)
                    .rejectionReason(lineDto.getRejectionReason())
                    .refusedAmount(lineRefused)
                    .quantity(quantity)
                    .unitPrice(lineApprovedBase)
                    .totalPrice(lineApprovedTotal)
                    .requestedUnitPrice(enteredUnitPrice)
                    .requestedQuantity(quantity)
                    .approvedUnitPrice(Boolean.TRUE.equals(lineDto.getRejected()) ? BigDecimal.ZERO : lineApprovedBase)
                    .approvedQuantity(Boolean.TRUE.equals(lineDto.getRejected()) ? 0 : quantity)
                    .rejectionReasonCode(lineDto.getRejectionReasonCode())
                    .reviewerNotes(lineDto.getReviewerNotes())
                    .build();

            newLines.add(line);
            totalRequestedAmount = totalRequestedAmount.add(lineRequestedTotal);
            totalRefusedAmount = totalRefusedAmount.add(lineRefused);
            totalApprovedAmount = totalApprovedAmount.add(lineApproved);
            totalPatientShare = totalPatientShare.add(linePatientShare);
        }

        claim.getLines().clear();
        newLines.forEach(claim::addLine);

        // Ensure financial state is updated on the entity
        claim.setRequestedAmount(totalRequestedAmount);
        claim.setRefusedAmount(totalRefusedAmount);
        claim.setApprovedAmount(totalApprovedAmount);
        claim.setPatientCoPay(totalPatientShare);
        claim.setNetProviderAmount(totalApprovedAmount); // Net = Approved (amount payable by insurance)
    }

    public void updateEntityFromDto(Claim claim, ClaimUpdateDto dto, PreAuthorization preAuth) {
        if (dto.getDoctorName() != null)
            claim.setDoctorName(dto.getDoctorName());
        if (dto.getStatus() != null)
            claim.setStatus(dto.getStatus());
        if (dto.getApprovedAmount() != null)
            claim.setApprovedAmount(dto.getApprovedAmount());
        if (dto.getReviewerComment() != null)
            claim.setReviewerComment(dto.getReviewerComment());
        if (preAuth != null)
            claim.setPreAuthorization(preAuth);
    }

    public void updateAttachments(Claim claim, List<ClaimAttachmentDto> attachments) {
        if (attachments != null) {
            attachments.forEach(attDto -> {
                ClaimAttachment attachment = ClaimAttachment.builder()
                        .claim(claim)
                        .fileName(attDto.getFileName())
                        .fileUrl(attDto.getFileUrl())
                        .fileType(attDto.getFileType())
                        .build();
                claim.addAttachment(attachment);
            });
        }
    }

    public ClaimViewDto toViewDto(Claim claim) {
        return toViewDto(claim, null);
    }

    /**
     * PURE TRANSFORMATION: Maps Claim to View DTO.
     * Related data must be pre-loaded or passed as arguments.
     */
    public ClaimViewDto toViewDto(Claim claim, String settlementBatchNumber) {
        ClaimViewDto dto = ClaimViewDto.builder()
                .id(claim.getId())
                .claimNumber("CLM-" + claim.getId())
                .providerName(claim.getProviderName())
                .providerId(claim.getProviderId())
                .doctorName(claim.getDoctorName())
                .diagnosisCode(claim.getDiagnosisCode())
                .diagnosisDescription(claim.getDiagnosisDescription())
                .complaint(claim.getComplaint())
                .diagnosis(
                        claim.getDiagnosisCode() != null
                                ? claim.getDiagnosisCode() + " - "
                                        + (claim.getDiagnosisDescription() != null ? claim.getDiagnosisDescription()
                                                : "")
                                : null)
                .visitDate(claim.getServiceDate())
                .serviceDate(claim.getServiceDate())
                .requestedAmount(claim.getRequestedAmount())
                .totalAmount(claim.getRequestedAmount())
                .approvedAmount(claim.getApprovedAmount())
                .refusedAmount(
                        (claim.getStatus() == ClaimStatus.REJECTED && (claim.getRefusedAmount() == null
                                || claim.getRefusedAmount().compareTo(BigDecimal.ZERO) == 0))
                                        ? claim.getRequestedAmount()
                                        : claim.getRefusedAmount())
                .differenceAmount(claim.getDifferenceAmount())
                .status(claim.getStatus())
                .statusLabel(claim.getStatus() != null ? claim.getStatus().getArabicLabel() : null)
                .reviewerComment(claim.getReviewerComment())
                .reviewedAt(claim.getReviewedAt())
                .serviceCount(claim.getServiceCount())
                .attachmentsCount(claim.getAttachmentsCount())
                .active(claim.getActive())
                .createdAt(claim.getCreatedAt())
                .updatedAt(claim.getUpdatedAt())
                .createdBy(claim.getCreatedBy())
                .updatedBy(claim.getUpdatedBy())
                .patientCoPay(claim.getPatientCoPay())
                .netProviderAmount(claim.getNetProviderAmount())
                .coPayPercent(claim.getCoPayPercent())
                .deductibleApplied(claim.getDeductibleApplied())
                .paymentReference(claim.getPaymentReference())
                .settledAt(claim.getSettledAt())
                .settlementNotes(claim.getSettlementNotes())
                .settlementBatchId(claim.getSettlementBatchId())
                .settlementBatchNumber(settlementBatchNumber)
                .expectedCompletionDate(claim.getExpectedCompletionDate())
                .actualCompletionDate(claim.getActualCompletionDate())
                .withinSla(claim.getWithinSla())
                .businessDaysTaken(claim.getBusinessDaysTaken())
                .slaDaysConfigured(claim.getSlaDaysConfigured())
                .slaStatus(calculateSlaStatus(claim))
                .manualCategoryEnabled(claim.getManualCategoryEnabled())
                .primaryCategoryCode(claim.getPrimaryCategoryCode())
                .primaryCategoryName(claim.getPrimaryCategoryCode() != null
                        ? medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                                .map(com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory::getName).orElse(null)
                        : null)
                .build();

        if (claim.getVisit() != null) {
            dto.setVisitId(claim.getVisit().getId());
            dto.setVisitDate(claim.getVisit().getVisitDate());
            dto.setVisitType(claim.getVisit().getVisitType() != null ? claim.getVisit().getVisitType().name() : null);
        }

        if (claim.getMember() != null) {
            dto.setMemberId(claim.getMember().getId());
            dto.setMemberFullName(claim.getMember().getFullName());
            dto.setMemberName(claim.getMember().getFullName());
            dto.setMemberNationalNumber(claim.getMember().getNationalNumber());

            if (claim.getMember().getEmployer() != null) {
                dto.setEmployerId(claim.getMember().getEmployer().getId());
                dto.setEmployerName(claim.getMember().getEmployer().getName());
                dto.setEmployerCode(claim.getMember().getEmployer().getCode());
            }

            if (claim.getMember().getBenefitPolicy() != null) {
                dto.setBenefitPackageId(claim.getMember().getBenefitPolicy().getId());
                dto.setBenefitPackageName(claim.getMember().getBenefitPolicy().getName());
                dto.setBenefitPackageCode(claim.getMember().getBenefitPolicy().getPolicyCode());
            }
        }

        if (claim.getPreAuthorization() != null) {
            dto.setPreApprovalId(claim.getPreAuthorization().getId());
            dto.setPreApprovalStatus(
                    claim.getPreAuthorization().getStatus() != null ? claim.getPreAuthorization().getStatus().name()
                            : null);
        }

        dto.setLines(claim.getLines() != null
                ? claim.getLines().stream().map(this::toLineDto).collect(Collectors.toList())
                : new ArrayList<>());

        dto.setAttachments(claim.getAttachments() != null
                ? claim.getAttachments().stream().map(this::toAttachmentDto).collect(Collectors.toList())
                : new ArrayList<>());

        return dto;
    }

    private ClaimLineDto toLineDto(ClaimLine line) {
        return ClaimLineDto.builder()
                .id(line.getId())
                .medicalServiceId(line.getMedicalService() != null ? line.getMedicalService().getId() : null)
                .serviceCode(line.getServiceCode())
                .serviceName(line.getServiceName())
                .serviceCategoryId(line.getServiceCategoryId())
                .serviceCategoryName(line.getServiceCategoryName())
                .requiresPA(line.getRequiresPA())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .totalPrice(line.getTotalPrice())
                .rejected(line.getRejected())
                .rejectionReason(line.getRejectionReason())
                .refusedAmount(line.getRefusedAmount())
                .coveragePercent(line.getCoveragePercentSnapshot())
                .patientSharePercent(line.getPatientCopayPercentSnapshot())
                .companyShare(calculateCompanyShare(line))
                .patientShare(calculatePatientShare(line))
                .benefitLimit(line.getBenefitLimit())
                .usedAmount(line.getUsedAmountSnapshot())
                .remainingAmount(line.getRemainingAmountSnapshot())
                .appliedCategoryId(line.getAppliedCategoryId())
                .appliedCategoryName(line.getAppliedCategoryName())
                .build();
    }

    private BigDecimal calculateCompanyShare(ClaimLine line) {
        if (Boolean.TRUE.equals(line.getRejected()))
            return BigDecimal.ZERO;

        BigDecimal price = (line.getRequestedUnitPrice() != null ? line.getRequestedUnitPrice() : line.getUnitPrice())
                .multiply(BigDecimal.valueOf(line.getQuantity()));
        BigDecimal refused = line.getRefusedAmount() != null ? line.getRefusedAmount() : BigDecimal.ZERO;
        BigDecimal net = price.subtract(refused).max(BigDecimal.ZERO);

        if (line.getCoveragePercentSnapshot() == null)
            return net; // Default to full coverage
        return net.multiply(BigDecimal.valueOf(line.getCoveragePercentSnapshot())).divide(BigDecimal.valueOf(100), 2,
                java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePatientShare(ClaimLine line) {
        if (Boolean.TRUE.equals(line.getRejected()))
            return BigDecimal.ZERO;

        BigDecimal price = (line.getRequestedUnitPrice() != null ? line.getRequestedUnitPrice() : line.getUnitPrice())
                .multiply(BigDecimal.valueOf(line.getQuantity()));
        BigDecimal refused = line.getRefusedAmount() != null ? line.getRefusedAmount() : BigDecimal.ZERO;
        BigDecimal net = price.subtract(refused).max(BigDecimal.ZERO);

        BigDecimal company = calculateCompanyShare(line);
        return net.subtract(company);
    }

    private ClaimAttachmentDto toAttachmentDto(ClaimAttachment attachment) {
        return ClaimAttachmentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileType(attachment.getFileType())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    private String calculateSlaStatus(Claim claim) {
        if (claim.getExpectedCompletionDate() == null)
            return null;
        if (claim.getActualCompletionDate() != null) {
            return Boolean.TRUE.equals(claim.getWithinSla()) ? "MET" : "BREACHED";
        }
        LocalDate today = LocalDate.now();
        LocalDate expectedDate = claim.getExpectedCompletionDate();
        if (today.isAfter(expectedDate))
            return "BREACHED";
        if (today.plusDays(1).isAfter(expectedDate) || today.isEqual(expectedDate))
            return "AT_RISK";
        return "ON_TRACK";
    }
}
