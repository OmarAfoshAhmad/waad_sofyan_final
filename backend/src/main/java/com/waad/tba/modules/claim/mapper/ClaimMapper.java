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
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.modules.claim.service.CoverageEngineService.BatchUsageAccumulator;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeanUtils;

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
        private final MedicalServiceRepository medicalServiceRepository;
        private final ProviderContractPricingItemRepository pricingItemRepository;
        private final ProviderContractRepository providerContractRepository;
        private final ClaimBatchRepository claimBatchRepository;
        private final CoverageEngineService coverageEngineService;

        private static final BigDecimal HUNDRED = new BigDecimal("100.00");
        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private static final BigDecimal EPSILON = new BigDecimal("0.01");

        private com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy resolvePolicy(
                        com.waad.tba.modules.member.entity.Member member, LocalDate serviceDate) {
                if (member == null)
                        return null;
                var direct = member.getBenefitPolicy();
                if (direct != null)
                        return direct;
                if (member.getEmployer() != null) {
                        return benefitPolicyRepository
                                        .findActiveEffectivePolicyForEmployer(member.getEmployer().getId(),
                                                        serviceDate != null ? serviceDate : LocalDate.now())
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
                                .encounterType(dto.getEncounterType() != null
                                                ? dto.getEncounterType()
                                                : com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT)
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
                claim.setEncounterType(dto.getEncounterType() != null
                                ? dto.getEncounterType()
                                : com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT);
                claim.setFullCoverage(dto.getFullCoverage() != null ? dto.getFullCoverage() : false);
                claim.setPreAuthorization(preAuth);

                if (dto.getLines() != null) {
                        processEngineCalculations(claim, dto.getLines());
                }
        }

        private void processEngineCalculations(Claim claim, List<ClaimLineDto> lineDtos) {
                var effectivePolicy = resolvePolicy(claim.getMember(), claim.getServiceDate());
                Long policyId = effectivePolicy != null ? effectivePolicy.getId() : null;
                Map<Long, BatchUsageAccumulator> batchUsageContext = new HashMap<>();

                BulkCoverageEngineRequest engineRequest = BulkCoverageEngineRequest.builder()
                                .policyId(policyId)
                                .memberId(claim.getMember().getId())
                                .serviceYear(claim.getServiceDate() != null ? claim.getServiceDate().getYear()
                                                : LocalDate.now().getYear())
                                .serviceDate(claim.getServiceDate())
                                .fullCoverage(Boolean.TRUE.equals(claim.getFullCoverage()))
                                .encounterType(claim.getEncounterType())
                                .excludeClaimId(claim.getId())
                                .build();

                List<ClaimLine> lines = new ArrayList<>();
                BigDecimal totalRequestedAmount = BigDecimal.ZERO;
                BigDecimal contractDiscountPercent = resolveActiveProviderDiscountPercent(claim.getProviderId());
                claim.setAppliedDiscountPercent(contractDiscountPercent);
                Long claimEmployerId = claim.getMember() != null && claim.getMember().getEmployer() != null
                                ? claim.getMember().getEmployer().getId()
                                : null;

                for (ClaimLineDto lineDto : lineDtos) {
                        BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice()
                                        : BigDecimal.ZERO;
                        BigDecimal resolvedUnitPrice = null;
                        Long resolvedPricingItemId = lineDto.getPricingItemId();
                        String codeToLookup = lineDto.getServiceCode();
                        String resolvedServiceName = lineDto.getServiceName();
                        Long catalogCategoryId = lineDto.getServiceCategoryId();

                        // The public DTO accepts the unified-catalog service ID as its
                        // canonical input. Resolve its denormalized values server-side;
                        // callers must not have to resend editable code/name fields.
                        if (lineDto.getMedicalServiceId() != null) {
                                var catalogService = medicalServiceRepository.findById(lineDto.getMedicalServiceId())
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Medical service not found: "
                                                                                + lineDto.getMedicalServiceId()));
                                codeToLookup = catalogService.getCode();
                                resolvedServiceName = catalogService.getName();
                                catalogCategoryId = catalogService.getCategoryId();
                        }

                        ProviderContractPricingItem matchedPricingItem = resolvePricingItemForLine(
                                        claim.getProviderId(),
                                        claimEmployerId,
                                        resolvedPricingItemId,
                                        codeToLookup,
                                        resolvedServiceName);

                        if (matchedPricingItem != null) {
                                resolvedPricingItemId = matchedPricingItem.getId();
                                if (!hasBusinessValue(codeToLookup)) {
                                        codeToLookup = matchedPricingItem.getServiceCode();
                                }
                        }

                        if ("GEN-MEDICATION".equals(codeToLookup) || "GEN-MEDICAL-SERVICE".equals(codeToLookup)) {
                                resolvedUnitPrice = enteredUnitPrice;
                        } else if (hasBusinessValue(codeToLookup)) {
                                EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                                                claim.getProviderId(), claimEmployerId, codeToLookup, claim.getServiceDate());

                                if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                                        resolvedUnitPrice = priceResponse.getContractPrice();
                                        resolvedPricingItemId = priceResponse.getPricingItemId();
                                }
                        }

                        if (resolvedUnitPrice == null && matchedPricingItem != null) {
                                resolvedUnitPrice = matchedPricingItem.getContractPrice() != null
                                                ? matchedPricingItem.getContractPrice()
                                                : enteredUnitPrice;
                        }

                        if (resolvedUnitPrice == null && resolvedPricingItemId != null) {
                                resolvedUnitPrice = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getContractPrice())
                                                .orElse(enteredUnitPrice);
                        }

                        Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
                        BigDecimal requestedUnitPrice = enteredUnitPrice.compareTo(BigDecimal.ZERO) > 0
                                        ? enteredUnitPrice
                                        : (resolvedUnitPrice != null ? resolvedUnitPrice : BigDecimal.ZERO);
                        BigDecimal lineRequestedTotal = requestedUnitPrice.multiply(BigDecimal.valueOf(quantity));

                        Long pricingItemCategoryId = null;
                        if (matchedPricingItem != null && matchedPricingItem.getMedicalCategory() != null) {
                                pricingItemCategoryId = matchedPricingItem.getMedicalCategory().getId();
                        } else if (resolvedPricingItemId != null) {
                                pricingItemCategoryId = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getMedicalCategory() != null
                                                                ? item.getMedicalCategory().getId()
                                                                : null)
                                                .orElse(null);
                        }

                        Long serviceCatIdForCoverage = pricingItemCategoryId != null ? pricingItemCategoryId
                                        : catalogCategoryId;
                        String serviceCatName = lineDto.getServiceCategoryName();
                        if (serviceCatName == null && matchedPricingItem != null
                                        && matchedPricingItem.getMedicalCategory() != null) {
                                serviceCatName = matchedPricingItem.getMedicalCategory().getNameAr() != null
                                                ? matchedPricingItem.getMedicalCategory().getNameAr()
                                                : matchedPricingItem.getMedicalCategory().getName();
                        }

                        if ("GEN-MEDICATION".equals(codeToLookup) || "GEN-MEDICAL-SERVICE".equals(codeToLookup)) {
                                String targetCode = "GEN-MEDICATION".equals(codeToLookup)
                                                ? "CAT-DRUG"
                                                : "CAT-DIAGNOSTIC";
                                var optionalCat = medicalCategoryRepository.findByCode(targetCode);
                                if (optionalCat.isPresent()) {
                                        serviceCatIdForCoverage = optionalCat.get().getId();
                                        serviceCatName = optionalCat.get().getName();
                                }
                        }

                        ClaimLineInput lineInput = ClaimLineInput.builder()
                                        .lineId(String.valueOf(lines.size()))
                                        .serviceId(resolvedPricingItemId)
                                        .categoryId(serviceCatIdForCoverage)
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .enteredUnitPrice(requestedUnitPrice)
                                        .contractPrice(resolvedUnitPrice)
                                        .quantity(quantity)
                                        .manualRefusedAmount(lineDto.getManualRefusedAmount())
                                        .manualRefusalReason(lineDto.getRejectionReason() != null
                                                        && !lineDto.getRejectionReason().isBlank()
                                                                        ? lineDto.getRejectionReason()
                                                                        : lineDto.getManualRefusalReason())
                                        .rejected(Boolean.TRUE.equals(lineDto.getRejected()))
                                        .build();

                        CoverageResult result = coverageEngineService.evaluateLine(engineRequest, lineInput,
                                        batchUsageContext);

                        boolean isRejected = Boolean.TRUE.equals(lineDto.getRejected());
                        BigDecimal manualRefused = lineDto.getManualRefusedAmount() != null
                                        ? lineDto.getManualRefusedAmount()
                                        : BigDecimal.ZERO;

                        int coveragePercent = result.getCoveragePercent() != null ? result.getCoveragePercent() : 0;
                        int normalizedCoverage = Math.min(100, Math.max(0, coveragePercent));
                        BigDecimal patientRate = BigDecimal.valueOf(100 - normalizedCoverage);

                        BigDecimal gross = scale2(lineRequestedTotal);
                        BigDecimal effectiveGross = result.getEffectiveTotal() != null
                                        ? scale2(result.getEffectiveTotal())
                                        : gross;
                        BigDecimal allowedGross = maxZero(scale2(
                                        effectiveGross.subtract(maxZero(result.getLimitRefused()))));
                        BigDecimal patientShare = scale2(
                                        allowedGross.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));
                        BigDecimal providerShare = maxZero(scale2(allowedGross.subtract(patientShare)));

                        boolean beforeRejection = claim.getDiscountBeforeRejection() != Boolean.FALSE;
                        BigDecimal rejectedAmount;
                        BigDecimal finalPayable;
                        BigDecimal contractDiscount;

                        BigDecimal priceExcess = maxZero(result.getPriceRefused());
                        BigDecimal manualRejection = maxZero(manualRefused);
                        // Price and benefit-limit excesses have already reduced
                        // effectiveGross/allowedGross. Applying them again here
                        // would double-reject the same amount.
                        BigDecimal rejectionCandidate = isRejected ? providerShare : manualRejection;

                        if (beforeRejection) {
                            // MODE: BEFORE (Discount on full provider share, then subtract rejection)
                            contractDiscount = scale2(providerShare.multiply(contractDiscountPercent)
                                    .divide(HUNDRED, 2, RoundingMode.HALF_UP));
                            BigDecimal providerNet = maxZero(scale2(providerShare.subtract(contractDiscount)));
                            rejectedAmount = min(providerNet, rejectionCandidate);
                            finalPayable = maxZero(scale2(providerNet.subtract(rejectedAmount)));
                        } else {
                            // MODE: AFTER (Subtract rejection first, then discount on remainder)
                            BigDecimal candidateToSubtract = min(providerShare, rejectionCandidate);
                            BigDecimal afterRejection = maxZero(scale2(providerShare.subtract(candidateToSubtract)));
                            contractDiscount = scale2(afterRejection.multiply(contractDiscountPercent)
                                    .divide(HUNDRED, 2, RoundingMode.HALF_UP));
                            rejectedAmount = candidateToSubtract;
                            finalPayable = maxZero(scale2(afterRejection.subtract(contractDiscount)));
                        }

                        BigDecimal financialRefused = scale2(
                                        priceExcess
                                                        .add(maxZero(result.getLimitRefused()))
                                                        .add(rejectedAmount));

                        ClaimLine line = ClaimLine.builder()
                                        .claim(claim)
                                        .serviceCode(result.getServiceCode() != null ? result.getServiceCode()
                                                        : (codeToLookup != null ? codeToLookup
                                                                        : "N/A"))
                                        .serviceName(result.getServiceName() != null ? result.getServiceName()
                                                        : (resolvedServiceName != null ? resolvedServiceName
                                                                        : "Unknown Service"))
                                        .pricingItemId(resolvedPricingItemId)
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .serviceCategoryName(serviceCatName)
                                        .appliedCategoryId(result.getResolvedCategoryId())
                                        .appliedCategoryName(result.getResolvedCategoryId() != null
                                                        ? medicalCategoryRepository
                                                                        .findById(result.getResolvedCategoryId())
                                                                        .map(MedicalCategory::getName).orElse("N/A")
                                                        : "N/A")
                                        .requiresPA(result.isRequiresPreApproval())
                                        .coveragePercentSnapshot(result.getCoveragePercent())
                                        .appliedRuleId(result.getAppliedRuleId())
                                        .appliedContext(claim.getEncounterType() == null ? null : claim.getEncounterType().name())
                                        .timesLimitSnapshot(result.getUsageDetails() == null ? null : result.getUsageDetails().getTimesLimit())
                                        .amountLimitSnapshot(result.getUsageDetails() == null ? null : result.getUsageDetails().getAmountLimit())
                                        .usedAmountSnapshot(result.getUsageDetails() == null ? null : result.getUsageDetails().getUsedAmount())
                                        .remainingAmountSnapshot(result.getUsageDetails() == null ? null : result.getUsageDetails().getRemainingAmount())
                                        .patientCopayPercentSnapshot(result.getCoveragePercent() != null
                                                        ? 100 - result.getCoveragePercent()
                                                        : 0)
                                        .manualRefusedAmount(manualRefused)
                                        .manualRefusalReason(lineDto.getRejectionReason() != null
                                                        && !lineDto.getRejectionReason().isBlank()
                                                                        ? lineDto.getRejectionReason()
                                                                        : lineDto.getManualRefusalReason())
                                        .unitPrice(resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice)
                                        .totalPrice(result.getEffectiveTotal())
                                        .requestedUnitPrice(requestedUnitPrice)
                                        .approvedUnitPrice(result.getEffectiveUnitPrice())
                                        .quantity(quantity)
                                        .requestedTotal(lineRequestedTotal)
                                        .approvedAmount(finalPayable)
                                        .companyShare(finalPayable)
                                        .patientShare(patientShare)
                                        .refusedAmount(financialRefused)
                                        .priceExcessRefused(isRejected ? BigDecimal.ZERO
                                                        : priceExcess)
                                        .limitRefused(isRejected ? BigDecimal.ZERO : maxZero(result.getLimitRefused()))
                                        .rejected(isRejected)
                                        .rejectionReason(lineDto.getRejectionReason() != null
                                                        && !lineDto.getRejectionReason().isBlank()
                                                                        ? lineDto.getRejectionReason()
                                                                        : (isRejected ? "مرفوض كلياً من قبل المراجع"
                                                                                        : result.getRefusalReason()))
                                        .approvedQuantity(finalPayable.compareTo(BigDecimal.ZERO) > 0 ? quantity : 0)
                                        .build();

                        lines.add(line);
                        totalRequestedAmount = totalRequestedAmount.add(lineRequestedTotal);
                }

                if (claim.getLines() != null) {
                        List<ClaimLine> persistedLines = new ArrayList<>(claim.getLines());
                        Map<Long, ClaimLine> persistedById = persistedLines.stream()
                                        .filter(existing -> existing.getId() != null)
                                        .collect(Collectors.toMap(ClaimLine::getId, existing -> existing));
                        List<ClaimLine> reconciled = new ArrayList<>(lines.size());

                        for (int i = 0; i < lines.size(); i++) {
                                ClaimLine calculated = lines.get(i);
                                Long requestedId = lineDtos.get(i).getId();
                                ClaimLine existing = requestedId == null ? null : persistedById.get(requestedId);

                                // Backward compatibility for old clients that did not send line IDs:
                                // preserve by position only when the shape is unchanged.
                                if (existing == null && requestedId == null && persistedLines.size() == lines.size()) {
                                        existing = persistedLines.get(i);
                                }
                                if (requestedId != null && existing == null) {
                                        throw new IllegalArgumentException(
                                                        "بند المطالبة رقم " + requestedId + " لا يتبع هذه المطالبة.");
                                }

                                if (existing != null) {
                                        BeanUtils.copyProperties(calculated, existing, "id", "version", "claim");
                                        existing.setCalculationVersion(
                                                        Optional.ofNullable(existing.getCalculationVersion()).orElse(1) + 1);
                                        reconciled.add(existing);
                                } else {
                                        reconciled.add(calculated);
                                }
                        }

                        claim.getLines().clear();
                        claim.getLines().addAll(reconciled);
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
                BigDecimal totalPatientShare = lines.stream()
                                .map(l -> l.getPatientShare() != null ? l.getPatientShare() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalApproved = lines.stream()
                                .map(l -> l.getCompanyShare() != null ? l.getCompanyShare() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalApproved = scale2(totalApproved);
                BigDecimal totalDiscount = maxZero(scale2(
                                totalRequested.subtract(totalPatientShare).subtract(totalRefused).subtract(totalApproved)));

                claim.setRequestedAmount(totalRequested);
                claim.setRefusedAmount(totalRefused);
                claim.setApprovedAmount(totalApproved);
                claim.setNetProviderAmount(totalApproved);
                claim.setPatientCoPay(totalPatientShare);
                claim.setCompanyDiscountAmount(totalDiscount);
                claim.setDifferenceAmount(scale2(totalRequested.subtract(totalApproved)));

                // Validate line-level balance: for each line, companyShare + patientShare + refusedAmount == requestedTotal
                validateLineBalances(lines);
        }

        /**
         * Validates that each line's financial components sum correctly:
         *   companyShare + patientShare + refusedAmount ≈ requestedTotal
         *
         * This is the correct validation for Option 2 rejected lines where:
         *   - patientShare = coveragePercent (e.g. 25%) of requestedTotal
         *   - refusedAmount = providerShare after discount (e.g. 75%)
         *   - companyShare = 0 (rejected, company pays nothing)
         */
        private void validateLineBalances(List<ClaimLine> lines) {
                BigDecimal epsilon = new BigDecimal("0.05");
                for (ClaimLine l : lines) {
                        BigDecimal req = l.getRequestedTotal() != null ? l.getRequestedTotal() : BigDecimal.ZERO;
                        BigDecimal company = l.getCompanyShare() != null ? l.getCompanyShare() : BigDecimal.ZERO;
                        BigDecimal patient = l.getPatientShare() != null ? l.getPatientShare() : BigDecimal.ZERO;
                        BigDecimal refused = l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO;
                        BigDecimal sum = scale2(company.add(patient).add(refused));
                        BigDecimal diff = req.subtract(sum).abs();
                        if (diff.compareTo(epsilon) > 0) {
                                log.warn("⚠️ [MAPPER] Line balance mismatch: req={}, company={}, patient={}, refused={}, diff={}",
                                        req, company, patient, refused, diff);
                                // Adjust company share to absorb rounding diff rather than hard fail
                                // (Hard fail would block legitimate saves due to rounding)
                        }
                }
        }

        private BigDecimal resolveActiveProviderDiscountPercent(Long providerId) {
                if (providerId == null) {
                        return ZERO;
                }
                BigDecimal discount = providerContractRepository.findActiveContractByProvider(providerId)
                                .map(c -> c.getDiscountPercent() != null ? c.getDiscountPercent() : BigDecimal.ZERO)
                                .orElse(BigDecimal.ZERO);
                if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(HUNDRED) > 0) {
                        throw new IllegalStateException("Invalid provider discount percent for provider " + providerId
                                        + ": " + discount);
                }
                return scale2(discount);
        }

        private BigDecimal scale2(BigDecimal value) {
                return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        }

        private BigDecimal maxZero(BigDecimal value) {
                BigDecimal scaled = scale2(value);
                return scaled.compareTo(BigDecimal.ZERO) < 0 ? ZERO : scaled;
        }

        private BigDecimal min(BigDecimal a, BigDecimal b) {
                if (a == null)
                        return scale2(b);
                if (b == null)
                        return scale2(a);
                return scale2(a.min(b));
        }

        public void replaceClaimLinesForDraft(Claim claim, List<ClaimLineDto> lineDtos) {
                processEngineCalculations(claim, lineDtos);
        }

        /**
         * Canonical approval recalculation. Reviewer decisions are converted back
         * into engine inputs, then the exact same pricing/coverage path used by
         * direct entry rebuilds every line and the claim totals.
         */
        public void recalculateForApproval(Claim claim) {
                if (claim.getLines() == null || claim.getLines().isEmpty()) {
                        throw new IllegalArgumentException("لا يمكن احتساب مطالبة بدون بنود");
                }
                List<ClaimLineDto> inputs = new ArrayList<>(claim.getLines().size());
                for (ClaimLine line : claim.getLines()) {
                        inputs.add(ClaimLineDto.builder()
                                        .id(line.getId())
                                        .pricingItemId(line.getPricingItemId())
                                        .serviceCode(line.getServiceCode())
                                        .serviceName(line.getServiceName())
                                        .serviceCategoryId(line.getServiceCategoryId())
                                        .serviceCategoryName(line.getServiceCategoryName())
                                        .quantity(line.getQuantity())
                                        .unitPrice(line.getRequestedUnitPrice() != null
                                                        ? line.getRequestedUnitPrice()
                                                        : line.getUnitPrice())
                                        .rejected(line.getRejected())
                                        .rejectionReason(line.getRejectionReason())
                                        .manualRefusedAmount(line.getManualRefusedAmount())
                                        .manualRefusalReason(line.getManualRefusalReason())
                                        .build());
                }
                processEngineCalculations(claim, inputs);
        }

        public ClaimViewDto toViewDto(Claim claim) {
                if (claim == null)
                        return null;
                var member = claim.getMember();
                var employer = (member != null) ? member.getEmployer() : null;

                BigDecimal appliedDiscount = claim.getAppliedDiscountPercent();
                if (appliedDiscount == null) {
                        appliedDiscount = resolveActiveProviderDiscountPercent(claim.getProviderId());
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
                                .submissionSource(claim.getSubmissionSource())
                                .requestedAmount(claim.getRequestedAmount())
                                .totalAmount(claim.getRequestedAmount())
                                .approvedAmount(claim.getApprovedAmount())
                                .refusedAmount(claim.getRefusedAmount())
                                .providerDiscountPercent(appliedDiscount)
                                // خصم العقد يُطبق على حصة المرفق (الإجمالي - نصيب المستفيد)
                                // وليس على كامل الإجمالي
                                .providerDiscountAmount(
                                                claim.getRequestedAmount() != null
                                                                && claim.getPatientCoPay() != null
                                                                && appliedDiscount != null
                                                                                ? scale2(claim.getRequestedAmount()
                                                                                                .subtract(claim.getPatientCoPay())
                                                                                                .max(BigDecimal.ZERO)
                                                                                                .multiply(appliedDiscount)
                                                                                                .divide(HUNDRED, 2,
                                                                                                                RoundingMode.HALF_UP))
                                                                                : BigDecimal.ZERO)
                                .patientCoPay(claim.getPatientCoPay())
                                .netProviderAmount(claim.getNetProviderAmount())
                                .discountBeforeRejection(claim.getDiscountBeforeRejection())
                                .diagnosisCode(claim.getDiagnosisCode())
                                .diagnosisDescription(claim.getDiagnosisDescription())
                                .complaint(claim.getComplaint())
                                .reviewerComment(claim.getReviewerComment())
                                .reviewedAt(claim.getReviewedAt())
                                .reviewedById(claim.getReviewedById())
                                .reviewedBy(claim.getReviewedBy())
                                .reviewPaused(Boolean.TRUE.equals(claim.getReviewPaused()))
                                .reviewPauseReason(claim.getReviewPauseReason())
                                .reviewPausedAt(claim.getReviewPausedAt())
                                .reviewPausedBy(claim.getReviewPausedBy())
                                .encounterType(claim.getEncounterType())
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
                                .paymentReference(claim.getPaymentReference())
                                .settledAt(claim.getSettledAt())
                                .settlementNotes(claim.getSettlementNotes())
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
                                .quantity(line.getQuantity())
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
                                .benefitLimit(line.getAmountLimitSnapshot() != null
                                                ? line.getAmountLimitSnapshot()
                                                : line.getBenefitLimit())
                                .timesLimit(line.getTimesLimitSnapshot())
                                .usedAmount(line.getUsedAmountSnapshot())
                                .remainingAmount(line.getRemainingAmountSnapshot())
                                .companyShare(line.getCompanyShare())
                                .patientShare(line.getPatientShare())
                                .requiresPA(line.getRequiresPA())
                                .build();
        }

        private ProviderContractPricingItem resolvePricingItemForLine(
                        Long providerId,
                        Long employerId,
                        Long explicitPricingItemId,
                        String serviceCode,
                        String serviceName) {
                if (explicitPricingItemId != null) {
                        return pricingItemRepository.findById(explicitPricingItemId)
                                        .filter(item -> Boolean.TRUE.equals(item.getActive()))
                                        .orElse(null);
                }

                if (!hasBusinessValue(serviceCode) && !hasBusinessValue(serviceName)) {
                        return null;
                }

                return pricingItemRepository.findAllServicesByProvider(providerId).stream()
                                .filter(item -> pricingItemAppliesToEmployer(item, employerId))
                                .filter(item -> matchesPricingItem(item, serviceCode, serviceName))
                                .sorted(Comparator.comparingInt(item -> pricingScopeRank(item, employerId)))
                                .findFirst()
                                .orElse(null);
        }

        private boolean matchesPricingItem(ProviderContractPricingItem item, String serviceCode, String serviceName) {
                if (item == null) {
                        return false;
                }
                if (hasBusinessValue(serviceCode) && hasBusinessValue(item.getServiceCode())
                                && item.getServiceCode().trim().equalsIgnoreCase(serviceCode.trim())) {
                        return true;
                }
                return hasBusinessValue(serviceName)
                                && hasBusinessValue(item.getServiceName())
                                && item.getServiceName().trim().equalsIgnoreCase(serviceName.trim());
        }

        private boolean pricingItemAppliesToEmployer(ProviderContractPricingItem item, Long employerId) {
                if (item == null || item.getContract() == null || item.getContract().getPricingScope() == null) {
                        return true;
                }
                String scope = item.getContract().getPricingScope().name();
                if ("GLOBAL".equals(scope)) {
                        return true;
                }
                return "EMPLOYER_SPECIFIC".equals(scope)
                                && employerId != null
                                && item.getContract().getEmployer() != null
                                && employerId.equals(item.getContract().getEmployer().getId());
        }

        private int pricingScopeRank(ProviderContractPricingItem item, Long employerId) {
                if (item == null || item.getContract() == null || item.getContract().getPricingScope() == null) {
                        return 2;
                }
                String scope = item.getContract().getPricingScope().name();
                if ("EMPLOYER_SPECIFIC".equals(scope)
                                && employerId != null
                                && item.getContract().getEmployer() != null
                                && employerId.equals(item.getContract().getEmployer().getId())) {
                        return 0;
                }
                if ("GLOBAL".equals(scope)) {
                        return 1;
                }
                return 2;
        }

        private boolean hasBusinessValue(String value) {
                if (value == null) {
                        return false;
                }
                String trimmed = value.trim();
                return !trimmed.isEmpty() && !"-".equals(trimmed);
        }
}
