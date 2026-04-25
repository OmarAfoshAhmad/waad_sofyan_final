package com.waad.tba.modules.claim.mapper;

import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.dto.engine.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.modules.claim.service.CoverageEngineService.BatchUsageAccumulator;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ClaimMapper (CANONICAL REBUILD 2026-04-24)
 * 
 * Maps between Claim entities and DTOs.
 * Enforces architectural laws for financial consistency.
 * 
 * LAW: All financial calculations flow through CoverageEngineService.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClaimMapper {

        private final ProviderContractService providerContractService;
        private final BenefitPolicyRepository benefitPolicyRepository;
        private final MedicalCategoryRepository medicalCategoryRepository;
        private final ProviderContractPricingItemRepository pricingItemRepository;
        private final ClaimBatchRepository claimBatchRepository;
        private final CoverageEngineService coverageEngineService;

        private com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy resolvePolicy(
                        com.waad.tba.modules.member.entity.Member member) {
                if (member == null)
                        return null;
                var direct = member.getBenefitPolicy();
                if (direct != null)
                        return direct;
                if (member.getEmployer() != null) {
                        return benefitPolicyRepository
                                        .findActiveEffectivePolicyForEmployer(member.getEmployer().getId(),
                                                        LocalDate.now())
                                        .orElse(null);
                }
                return null;
        }

        public Claim toEntity(ClaimCreateDto dto, Visit visit, Provider provider, PreAuthorization preAuth,
                        ClaimBatch claimBatch) {
                Claim claim = Claim.builder()
                                .visit(visit)
                                .member(visit.getMember())
                                .providerId(provider.getId())
                                .providerName(provider.getName())
                                .serviceDate(dto.getServiceDate())
                                .diagnosisCode(dto.getDiagnosisCode())
                                .diagnosisDescription(dto.getDiagnosisDescription())
                                .doctorName(dto.getDoctorName())
                                .status(dto.getStatus() != null ? dto.getStatus() : ClaimStatus.APPROVED)
                                .complaint(dto.getComplaint())
                                .reviewerComment(dto.getRejectionReason())
                                .preAuthorization(preAuth)
                                .claimBatch(claimBatch)
                                .manualCategoryEnabled(
                                                dto.getManualCategoryEnabled() != null ? dto.getManualCategoryEnabled()
                                                                : false)
                                .primaryCategoryCode(dto.getPrimaryCategoryCode())
                                .fullCoverage(dto.getFullCoverage() != null ? dto.getFullCoverage() : false)
                                .isBacklog(visit.getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG)
                                .build();

                processEngineCalculations(claim, dto.getLines());
                return claim;
        }

        public void updateEntityFromDto(Claim claim, ClaimUpdateDto dto, PreAuthorization preAuth) {
                claim.setServiceDate(dto.getServiceDate());
                claim.setDiagnosisCode(dto.getDiagnosisCode());
                claim.setDiagnosisDescription(dto.getDiagnosisDescription());
                claim.setDoctorName(dto.getDoctorName());
                claim.setComplaint(dto.getComplaint());
                claim.setReviewerComment(dto.getRejectionReason());
                claim.setManualCategoryEnabled(
                                dto.getManualCategoryEnabled() != null ? dto.getManualCategoryEnabled() : false);
                claim.setPrimaryCategoryCode(dto.getPrimaryCategoryCode());
                claim.setFullCoverage(dto.getFullCoverage() != null ? dto.getFullCoverage() : false);
                claim.setPreAuthorization(preAuth);

                if (dto.getLines() != null) {
                        processEngineCalculations(claim, dto.getLines());
                }
        }

        private void processEngineCalculations(Claim claim, List<ClaimLineDto> lineDtos) {
                Long policyId = resolvePolicy(claim.getMember()) != null ? resolvePolicy(claim.getMember()).getId()
                                : null;
                Map<Long, BatchUsageAccumulator> batchUsageContext = new HashMap<>();

                BulkCoverageEngineRequest engineRequest = BulkCoverageEngineRequest.builder()
                                .policyId(policyId)
                                .memberId(claim.getMember().getId())
                                .serviceYear(claim.getServiceDate() != null ? claim.getServiceDate().getYear()
                                                : LocalDate.now().getYear())
                                .fullCoverage(Boolean.TRUE.equals(claim.getFullCoverage()))
                                .excludeClaimId(claim.getId())
                                .build();

                Long contextCategoryId = null;
                if (claim.getPrimaryCategoryCode() != null) {
                        contextCategoryId = medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                                        .map(MedicalCategory::getId)
                                        .orElse(null);
                }

                List<ClaimLine> lines = new ArrayList<>();
                BigDecimal totalRequestedAmount = BigDecimal.ZERO;

                for (ClaimLineDto lineDto : lineDtos) {
                        BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice()
                                        : BigDecimal.ZERO;
                        BigDecimal resolvedUnitPrice = null;
                        Long resolvedPricingItemId = lineDto.getPricingItemId();
                        String codeToLookup = lineDto.getServiceCode();

                        if (codeToLookup == null && resolvedPricingItemId != null) {
                                codeToLookup = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getServiceCode())
                                                .orElse(null);
                        }

                        if (codeToLookup != null) {
                                EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                                                claim.getProviderId(), codeToLookup, claim.getServiceDate());

                                if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                                        resolvedUnitPrice = priceResponse.getContractPrice();
                                        resolvedPricingItemId = priceResponse.getPricingItemId();
                                }
                        }

                        if (resolvedUnitPrice == null && resolvedPricingItemId != null) {
                                resolvedUnitPrice = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getContractPrice())
                                                .orElse(enteredUnitPrice);
                        }

                        Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
                        BigDecimal lineRequestedTotal = enteredUnitPrice.multiply(BigDecimal.valueOf(quantity));

                        Long pricingItemCategoryId = null;
                        if (resolvedPricingItemId != null) {
                                pricingItemCategoryId = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getMedicalCategory() != null
                                                                ? item.getMedicalCategory().getId()
                                                                : null)
                                                .orElse(null);
                        }

                        Long serviceCatIdForCoverage = pricingItemCategoryId != null ? pricingItemCategoryId
                                        : lineDto.getServiceCategoryId();

                        ClaimLineInput lineInput = ClaimLineInput.builder()
                                        .lineId(String.valueOf(lines.size()))
                                        .serviceId(resolvedPricingItemId)
                                        .categoryId(contextCategoryId)
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .enteredUnitPrice(enteredUnitPrice)
                                        .contractPrice(resolvedUnitPrice)
                                        .quantity(quantity)
                                        .manualRefusedAmount(lineDto.getManualRefusedAmount())
                                        .manualRefusalReason(lineDto.getManualRefusalReason())
                                        .rejected(Boolean.TRUE.equals(lineDto.getRejected()))
                                        .build();

                        CoverageResult result = coverageEngineService.evaluateLine(engineRequest, lineInput,
                                        batchUsageContext);

                        boolean isRejected = Boolean.TRUE.equals(lineDto.getRejected());
                        BigDecimal manualRefused = lineDto.getManualRefusedAmount() != null
                                        ? lineDto.getManualRefusedAmount()
                                        : BigDecimal.ZERO;

                        ClaimLine line = ClaimLine.builder()
                                        .claim(claim)
                                        .serviceCode(result.getServiceCode() != null ? result.getServiceCode()
                                                        : (lineDto.getServiceCode() != null ? lineDto.getServiceCode()
                                                                        : "N/A"))
                                        .serviceName(result.getServiceName() != null ? result.getServiceName()
                                                        : (lineDto.getServiceName() != null ? lineDto.getServiceName()
                                                                        : "Unknown Service"))
                                        .pricingItemId(resolvedPricingItemId)
                                        .serviceCategoryId(lineDto.getServiceCategoryId())
                                        .serviceCategoryName(lineDto.getServiceCategoryName())
                                        .appliedCategoryId(result.getResolvedCategoryId())
                                        .appliedCategoryName(result.getResolvedCategoryId() != null
                                                        ? medicalCategoryRepository
                                                                        .findById(result.getResolvedCategoryId())
                                                                        .map(MedicalCategory::getName).orElse("N/A")
                                                        : "N/A")
                                        .requiresPA(result.isRequiresPreApproval())
                                        .coveragePercentSnapshot(result.getCoveragePercent())
                                        .patientCopayPercentSnapshot(result.getCoveragePercent() != null
                                                        ? 100 - result.getCoveragePercent()
                                                        : 0)
                                        .manualRefusedAmount(manualRefused)
                                        .manualRefusalReason(lineDto.getManualRefusalReason())
                                        .unitPrice(resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice)
                                        .totalPrice(result.getEffectiveTotal())
                                        .requestedUnitPrice(enteredUnitPrice)
                                        .approvedUnitPrice(result.getEffectiveUnitPrice())
                                        .quantity(quantity)
                                        .requestedTotal(lineRequestedTotal)
                                        .approvedAmount(isRejected ? BigDecimal.ZERO : result.getCompanyShare())
                                        .companyShare(isRejected ? BigDecimal.ZERO : result.getCompanyShare())
                                        .patientShare(isRejected ? lineRequestedTotal : result.getPatientShare())
                                        .refusedAmount(isRejected ? lineRequestedTotal
                                                        : (result.getSystemRefusedAmount().add(manualRefused)))
                                        .priceExcessRefused(isRejected ? BigDecimal.ZERO : result.getPriceRefused())
                                        .limitRefused(isRejected ? BigDecimal.ZERO : result.getLimitRefused())
                                        .rejected(isRejected)
                                        .rejectionReason(
                                                        isRejected ? "REJECTED_BY_REVIEWER" : result.getRefusalReason())
                                        .approvedQuantity(isRejected ? 0 : quantity)
                                        .build();

                        lines.add(line);
                        totalRequestedAmount = totalRequestedAmount.add(lineRequestedTotal);
                }

                if (claim.getLines() != null) {
                        claim.getLines().clear();
                        claim.getLines().addAll(lines);
                } else {
                        claim.setLines(lines);
                }
                claim.setRequestedAmount(totalRequestedAmount);
                calculateClaimTotals(claim);
        }

        private void calculateClaimTotals(Claim claim) {
                List<ClaimLine> lines = claim.getLines();
                BigDecimal totalRequested = lines.stream()
                                .map(l -> l.getRequestedTotal() != null ? l.getRequestedTotal() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalRefused = lines.stream()
                                .map(l -> l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalApproved = lines.stream()
                                .map(l -> l.getApprovedAmount() != null ? l.getApprovedAmount() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                claim.setRequestedAmount(totalRequested);
                claim.setRefusedAmount(totalRefused);
                claim.setApprovedAmount(totalApproved);
                claim.setNetProviderAmount(totalApproved);
                // B-02 FIX: patientCoPay = Σ(per-line patientShare), NOT (requested -
                // approved).
                // The old formula incorrectly included refused amounts in patient
                // responsibility.
                // Refused amounts are the insurer's refusal, NOT the patient's out-of-pocket.
                BigDecimal totalPatientShare = lines.stream()
                                .map(l -> l.getPatientShare() != null ? l.getPatientShare() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                claim.setPatientCoPay(totalPatientShare);
        }

        public void replaceClaimLinesForDraft(Claim claim, List<ClaimLineDto> lineDtos) {
                processEngineCalculations(claim, lineDtos);
        }

        public ClaimViewDto toViewDto(Claim claim) {
                if (claim == null)
                        return null;
                var member = claim.getMember();
                var employer = (member != null) ? member.getEmployer() : null;

                String primaryCategoryName = null;
                if (claim.getPrimaryCategoryCode() != null) {
                        primaryCategoryName = medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                                        .map(MedicalCategory::getName)
                                        .orElse(null);
                }

                if (Boolean.TRUE.equals(claim.getFullCoverage())) {
                        primaryCategoryName = (primaryCategoryName != null)
                                        ? primaryCategoryName + " (تغطية كاملة)"
                                        : "تغطية كاملة";
                }

                return ClaimViewDto.builder()
                                .id(claim.getId())
                                .claimNumber(claim.getClaimNumber() != null ? claim.getClaimNumber()
                                                : "CLM-" + claim.getId())
                                .memberId(member != null ? member.getId() : null)
                                .memberFullName(member != null ? member.getFullName() : null)
                                .memberName(member != null ? member.getFullName() : null)
                                .memberNationalNumber(member != null ? member.getNationalNumber() : null)
                                .employerId(employer != null ? employer.getId() : null)
                                .employerName(employer != null ? employer.getName() : null)
                                .providerId(claim.getProviderId())
                                .providerName(claim.getProviderName())
                                .doctorName(claim.getDoctorName())
                                .serviceDate(claim.getServiceDate())
                                .status(claim.getStatus())
                                .requestedAmount(claim.getRequestedAmount())
                                .totalAmount(claim.getRequestedAmount())
                                .approvedAmount(claim.getApprovedAmount())
                                .refusedAmount(claim.getRefusedAmount())
                                .patientCoPay(claim.getPatientCoPay())
                                .netProviderAmount(claim.getNetProviderAmount())
                                .diagnosisCode(claim.getDiagnosisCode())
                                .diagnosisDescription(claim.getDiagnosisDescription())
                                .complaint(claim.getComplaint())
                                .reviewerComment(claim.getReviewerComment())
                                .manualCategoryEnabled(claim.getManualCategoryEnabled())
                                .primaryCategoryCode(claim.getPrimaryCategoryCode())
                                .primaryCategoryName(primaryCategoryName)
                                .fullCoverage(claim.getFullCoverage())
                                .claimBatchId(claim.getClaimBatch() != null ? claim.getClaimBatch().getId() : null)
                                .claimBatchCode(claim.getClaimBatch() != null ? claim.getClaimBatch().getBatchCode()
                                                : null)
                                .lines(claim.getLines().stream().map(this::toLineDto).collect(Collectors.toList()))
                                .active(claim.getActive())
                                .createdAt(claim.getCreatedAt())
                                .updatedAt(claim.getUpdatedAt())
                                .createdBy(claim.getCreatedBy())
                                .updatedBy(claim.getUpdatedBy())
                                .deletedAt(claim.getDeletedAt())
                                .deletedBy(claim.getDeletedBy())
                                .voidReason(claim.getVoidReason())
                                .build();
        }

        private ClaimLineDto toLineDto(ClaimLine line) {
                return ClaimLineDto.builder()
                                .id(line.getId())
                                .pricingItemId(line.getPricingItemId())
                                .serviceCode(line.getServiceCode())
                                .serviceName(line.getServiceName())
                                .serviceCategoryId(line.getServiceCategoryId())
                                .serviceCategoryName(line.getServiceCategoryName())
                                .appliedCategoryId(line.getAppliedCategoryId())
                                .appliedCategoryName(line.getAppliedCategoryName())
                                .unitPrice(line.getUnitPrice())
                                .totalPrice(line.getTotalPrice())
                                .requestedUnitPrice(line.getRequestedUnitPrice())
                                .approvedUnitPrice(line.getApprovedUnitPrice())
                                .requestedQuantity(line.getRequestedQuantity())
                                .approvedQuantity(line.getApprovedQuantity())
                                .requestedTotal(line.getRequestedTotal())
                                .approvedAmount(line.getApprovedAmount())
                                .refusedAmount(line.getRefusedAmount())
                                .rejected(Boolean.TRUE.equals(line.getRejected()))
                                .rejectionReason(line.getRejectionReason())
                                .manualRefusedAmount(line.getManualRefusedAmount())
                                .manualRefusalReason(line.getManualRefusalReason())
                                .coveragePercent(line.getCoveragePercentSnapshot())
                                .patientSharePercent(line.getPatientCopayPercentSnapshot())
                                .benefitLimit(line.getBenefitLimit())
                                .companyShare(line.getCompanyShare())
                                .patientShare(line.getPatientShare())
                                .requiresPA(line.getRequiresPA())
                                .build();
        }
}
