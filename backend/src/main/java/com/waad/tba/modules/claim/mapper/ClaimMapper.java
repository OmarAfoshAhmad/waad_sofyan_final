package com.waad.tba.modules.claim.mapper;

import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
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
public class ClaimMapper {

    private final ProviderContractService providerContractService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;

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
                .status(ClaimStatus.DRAFT)
                .preAuthorization(preAuth)
                .build();

        BigDecimal totalRequestedAmount = BigDecimal.ZERO;
        List<ClaimLine> lines = new ArrayList<>();

        for (ClaimLineDto lineDto : dto.getLines()) {
            MedicalService medicalService = medicalServiceMap.get(lineDto.getMedicalServiceId());
            if (medicalService == null)
                continue;

            // Resolve contract price
            EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                    provider.getId(), medicalService.getCode(), dto.getServiceDate());

            BigDecimal unitPrice;
            if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                unitPrice = priceResponse.getContractPrice();
            } else {
                // POLICY: Use base price as fallback for DRAFT claims, but log as warning
                unitPrice = medicalService.getBasePrice() != null ? medicalService.getBasePrice() : BigDecimal.ZERO;
                log.warn("⚠️ [NO_CONTRACT] No contract price for service '{}' (provider={}, date={}). Using base price: {}. Review required.",
                        medicalService.getCode(), provider.getId(), dto.getServiceDate(), unitPrice);
            }

            // Resolve coverage snapshot
            var coverageInfoOpt = benefitPolicyCoverageService.getCoverageForService(claim.getMember(),
                    medicalService.getId());
            boolean requiresPA = coverageInfoOpt.map(c -> c.isRequiresPreApproval()).orElse(false);
            Integer coveragePercentSnapshot = coverageInfoOpt.map(c -> c.getCoveragePercent()).orElse(null);
            Integer patientCopayPercentSnapshot = coveragePercentSnapshot != null ? (100 - coveragePercentSnapshot)
                    : null;

            Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            ClaimLine line = ClaimLine.builder()
                    .claim(claim)
                    .medicalService(medicalService)
                    .serviceCode(medicalService.getCode())
                    .serviceName(medicalService.getName())
                    .serviceCategoryId(lineDto.getServiceCategoryId())
                    .serviceCategoryName(lineDto.getServiceCategoryName())
                    .requiresPA(requiresPA)
                    .coveragePercentSnapshot(coveragePercentSnapshot)
                    .patientCopayPercentSnapshot(patientCopayPercentSnapshot)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build();

            lines.add(line);
            totalRequestedAmount = totalRequestedAmount.add(lineTotal);
        }

        claim.setLines(lines);
        claim.setRequestedAmount(totalRequestedAmount);

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

        for (ClaimLineDto lineDto : lineDtos) {
            MedicalService medicalService = medicalServiceMap.get(lineDto.getMedicalServiceId());
            if (medicalService == null) {
                throw new IllegalArgumentException("MedicalService not found for ID: " + lineDto.getMedicalServiceId());
            }

            EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                    claim.getProviderId(), medicalService.getCode(), serviceDate);

            if (!priceResponse.isHasContract() || priceResponse.getContractPrice() == null) {
                throw new IllegalArgumentException("No contract price found for service " + medicalService.getCode());
            }

            var coverageInfoOpt = benefitPolicyCoverageService.getCoverageForService(claim.getMember(),
                    medicalService.getId());
            boolean requiresPA = coverageInfoOpt.map(c -> c.isRequiresPreApproval()).orElse(false);
            Integer coveragePercentSnapshot = coverageInfoOpt.map(c -> c.getCoveragePercent()).orElse(null);
            Integer patientCopayPercentSnapshot = coveragePercentSnapshot != null ? (100 - coveragePercentSnapshot)
                    : null;

            BigDecimal unitPrice = priceResponse.getContractPrice();
            Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            
            BigDecimal lineApproved = BigDecimal.ZERO;
            BigDecimal linePatientShare = BigDecimal.ZERO;
            BigDecimal lineRefused = lineDto.getRefusedAmount() != null ? lineDto.getRefusedAmount() : BigDecimal.ZERO;

            if (Boolean.TRUE.equals(lineDto.getRejected())) {
                lineRefused = lineTotal;
            } else {
                // Approved = LineTotal * Coverage%
                if (coveragePercentSnapshot != null) {
                    lineApproved = lineTotal.multiply(BigDecimal.valueOf(coveragePercentSnapshot)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    linePatientShare = lineTotal.subtract(lineApproved);
                } else {
                    lineApproved = lineTotal;
                }
            }

            ClaimLine line = ClaimLine.builder()
                    .claim(claim)
                    .medicalService(medicalService)
                    .serviceCode(medicalService.getCode())
                    .serviceName(medicalService.getName())
                    .serviceCategoryId(lineDto.getServiceCategoryId())
                    .serviceCategoryName(lineDto.getServiceCategoryName())
                    .requiresPA(requiresPA)
                    .coveragePercentSnapshot(coveragePercentSnapshot)
                    .patientCopayPercentSnapshot(patientCopayPercentSnapshot)
                    .rejected(lineDto.getRejected() != null ? lineDto.getRejected() : false)
                    .rejectionReason(lineDto.getRejectionReason())
                    .refusedAmount(lineRefused)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build();

            newLines.add(line);
            totalRequestedAmount = totalRequestedAmount.add(lineTotal);
            totalRefusedAmount = totalRefusedAmount.add(lineRefused);
            totalApprovedAmount = totalApprovedAmount.add(lineApproved);
            totalPatientShare = totalPatientShare.add(linePatientShare);
        }

        claim.getLines().clear();
        newLines.forEach(claim::addLine);
        claim.setRequestedAmount(totalRequestedAmount);
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
                        (claim.getStatus() == ClaimStatus.REJECTED && (claim.getRefusedAmount() == null || claim.getRefusedAmount().compareTo(BigDecimal.ZERO) == 0))
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
                .build();
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
