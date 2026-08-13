package com.waad.tba.modules.claim.mapper;

import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.dto.engine.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.common.exception.BusinessRuleException;
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
import com.waad.tba.modules.providercontract.service.EffectiveProviderContractResolver;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.modules.claim.service.CoverageEngineService.BatchUsageAccumulator;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialAdjudicationService;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialInvariantGuard;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialTotals;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.repository.ClaimPendingServiceRepository;
import com.waad.tba.security.AuthorizationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.AccessDeniedException;

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
        private final com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
        private final BenefitPolicyRepository benefitPolicyRepository;
        private final MedicalCategoryRepository medicalCategoryRepository;
        private final MedicalServiceRepository medicalServiceRepository;
        private final ProviderContractPricingItemRepository pricingItemRepository;
        private final EffectiveProviderContractResolver effectiveContractResolver;
        private final ClaimBatchRepository claimBatchRepository;
        private final ClaimPendingServiceRepository pendingServiceRepository;
        private final CoverageEngineService coverageEngineService;
        private final AuthorizationService authorizationService;
        private final ClaimFinancialAdjudicationService financialAdjudicationService;
        private final ClaimFinancialInvariantGuard claimFinancialInvariantGuard;

        private static final BigDecimal HUNDRED = new BigDecimal("100.00");
        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private static final BigDecimal EPSILON = new BigDecimal("0.01");

        /**
         * The policy in force ON THE SERVICE DATE. This is what selects the
         * BenefitPolicyRule applied to each line, so reading the member's
         * current pointer here made the rule come from one policy while the
         * limit machinery resolved another -- surfacing as
         * BENEFIT_RULE_POLICY_MISMATCH once the limit side was converted.
         * Both sides now ask the same resolver the same dated question.
         */
        private com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy resolvePolicy(
                        com.waad.tba.modules.member.entity.Member member, LocalDate serviceDate) {
                if (member == null)
                        return null;
                return memberPolicyResolver.resolveFor(member, serviceDate).orElse(null);
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
                                // DRAFT, never APPROVED: the mapper only builds data. Financial approval
                                // (amount limits, totalApproved > 0) is decided later by ClaimService via
                                // ClaimStateMachine, after finalizeSnapshot has computed the real amount.
                                .status(dto.getStatus() != null ? dto.getStatus() : ClaimStatus.DRAFT)
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
                Long claimEmployerId = claim.getMember() != null && claim.getMember().getEmployer() != null
                                ? claim.getMember().getEmployer().getId()
                                : null;
                var resolvedContract = effectiveContractResolver.resolve(
                                claim.getProviderId(), claimEmployerId, claim.getServiceDate());
                BigDecimal contractDiscountPercent = scale2(resolvedContract.terms().getDiscountPercent());
                claim.setProviderContractId(resolvedContract.contract().getId());
                claim.setContractTermsId(resolvedContract.terms().getId());
                claim.setAppliedDiscountPercent(contractDiscountPercent);
                claim.setDiscountBeforeRejection(
                                Boolean.TRUE.equals(resolvedContract.terms().getDiscountBeforeRejection()));
                claim.setFinancialCalculatedAt(LocalDateTime.now());

                for (ClaimLineDto lineDto : lineDtos) {
                        BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice()
                                        : BigDecimal.ZERO;
                        BigDecimal resolvedUnitPrice = null;
                        BigDecimal resolvedMaxUnitPrice = null;
                        Long resolvedPricingItemId = lineDto.getPricingItemId();
                        String codeToLookup = lineDto.getServiceCode();
                        String resolvedServiceName = lineDto.getServiceName();
                        Long catalogCategoryId = lineDto.getServiceCategoryId();
                        ClaimPendingService pendingService = null;
                        boolean pendingDirectPrice = false;
                        boolean pendingRejected = false;

                        if (lineDto.getPendingServiceId() != null) {
                                if (claim.getId() == null) {
                                        throw new BusinessRuleException(
                                                        "يجب حفظ المطالبة واستلامها للمراجعة قبل إضافة خدمة جديدة إليها");
                                }
                                pendingService = pendingServiceRepository
                                                .findByIdAndClaimId(lineDto.getPendingServiceId(), claim.getId())
                                                .orElseThrow(() -> new BusinessRuleException(
                                                                "الخدمة المقترحة لا تتبع هذه المطالبة"));
                                if (!Objects.equals(pendingService.getProviderId(), claim.getProviderId())) {
                                        throw new BusinessRuleException(
                                                        "الخدمة المقترحة لا تتبع مقدم خدمة المطالبة");
                                }

                                if (pendingService.getStatus() == PendingServiceStatus.LINKED_EXISTING) {
                                        resolvedPricingItemId = pendingService.getLinkedPricingItemId();
                                } else {
                                        pendingDirectPrice = true;
                                        enteredUnitPrice = pendingService.effectiveUnitPrice();
                                        resolvedUnitPrice = enteredUnitPrice;
                                        resolvedMaxUnitPrice = enteredUnitPrice;
                                        codeToLookup = pendingService.getFinalServiceCode() != null
                                                        ? pendingService.getFinalServiceCode()
                                                        : pendingService.getProposedServiceCode();
                                        resolvedServiceName = pendingService.getFinalServiceName() != null
                                                        ? pendingService.getFinalServiceName()
                                                        : pendingService.getProposedServiceName();
                                        catalogCategoryId = pendingService.effectiveCategoryId();
                                        pendingRejected = pendingService.getStatus() == PendingServiceStatus.REJECTED;
                                }
                        }

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

                        ProviderContractPricingItem matchedPricingItem = pendingDirectPrice ? null
                                        : resolvePricingItemForLine(
                                                        resolvedContract.contract().getId(), claim.getServiceDate(),
                                                        resolvedPricingItemId,
                                                        codeToLookup,
                                                        resolvedServiceName);

                        if (resolvedPricingItemId != null && matchedPricingItem == null) {
                                throw new BusinessRuleException(
                                                "سعر الخدمة المحدد غير ساري في تاريخ الخدمة أو لا يتبع عقد مقدم الخدمة الفعال");
                        }

                        if (matchedPricingItem != null) {
                                resolvedPricingItemId = matchedPricingItem.getId();
                                if (!hasBusinessValue(codeToLookup)) {
                                        codeToLookup = matchedPricingItem.getServiceCode();
                                }
                        }

                        if ("GEN-MEDICATION".equals(codeToLookup) || "GEN-MEDICAL-SERVICE".equals(codeToLookup)) {
                                resolvedUnitPrice = enteredUnitPrice;
                        } else if (!pendingDirectPrice && hasBusinessValue(codeToLookup)) {
                                EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                                                claim.getProviderId(), claimEmployerId, codeToLookup, claim.getServiceDate());

                                if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                                        resolvedUnitPrice = priceResponse.getContractPrice();
                                        resolvedMaxUnitPrice = priceResponse.getMaxContractPrice() != null
                                                        ? priceResponse.getMaxContractPrice()
                                                        : priceResponse.getContractPrice();
                                        resolvedPricingItemId = priceResponse.getPricingItemId();
                                }
                        }

                        if (resolvedUnitPrice == null && matchedPricingItem != null) {
                                resolvedUnitPrice = matchedPricingItem.getContractPrice() != null
                                                ? matchedPricingItem.getContractPrice()
                                                : enteredUnitPrice;
                                resolvedMaxUnitPrice = matchedPricingItem.getMaxContractPrice() != null
                                                ? matchedPricingItem.getMaxContractPrice()
                                                : resolvedUnitPrice;
                        }

                        Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
                        BigDecimal requestedUnitPrice = enteredUnitPrice.compareTo(BigDecimal.ZERO) > 0
                                        ? enteredUnitPrice
                                        : (resolvedUnitPrice != null ? resolvedUnitPrice : BigDecimal.ZERO);
                        BigDecimal lineRequestedTotal = requestedUnitPrice.multiply(BigDecimal.valueOf(quantity));

                        Long pricingItemCategoryId = null;
                        String pricingItemCategoryName = null;
                        if (matchedPricingItem != null && matchedPricingItem.getMedicalCategory() != null) {
                                pricingItemCategoryId = matchedPricingItem.getMedicalCategory().getId();
                                pricingItemCategoryName = matchedPricingItem.getMedicalCategory().getNameAr() != null
                                                ? matchedPricingItem.getMedicalCategory().getNameAr()
                                                : matchedPricingItem.getMedicalCategory().getName();
                        }

                        var currentUser = authorizationService.getCurrentUser();
                        boolean canOverrideClassification = currentUser != null
                                        && (authorizationService.isSuperAdmin(currentUser)
                                                        || authorizationService.isReviewer(currentUser)
                                                        || authorizationService.canAccessInternalOperations(currentUser));
                        Long requestedCategoryOverride = lineDto.getServiceCategoryId();
                        boolean hasCategoryOverride = requestedCategoryOverride != null
                                        && pricingItemCategoryId != null
                                        && !Objects.equals(requestedCategoryOverride, pricingItemCategoryId);
                        if (hasCategoryOverride && !canOverrideClassification) {
                                throw new AccessDeniedException("لا تملك صلاحية تغيير التصنيف التأميني لبند المطالبة");
                        }

                        Long serviceCatIdForCoverage = hasCategoryOverride ? requestedCategoryOverride
                                        : (pricingItemCategoryId != null ? pricingItemCategoryId : catalogCategoryId);
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
                        if (serviceCatName == null && serviceCatIdForCoverage != null) {
                                serviceCatName = medicalCategoryRepository.findById(serviceCatIdForCoverage)
                                                .map(cat -> cat.getNameAr() != null ? cat.getNameAr() : cat.getName())
                                                .orElse(null);
                        }

                        boolean claimRejected = claim.getStatus() == ClaimStatus.REJECTED;
                        String effectiveLineRejectionReason = lineDto.getRejectionReason() != null
                                        && !lineDto.getRejectionReason().isBlank()
                                                        ? lineDto.getRejectionReason()
                                                        : claim.getReviewerComment();
                        boolean isRejected = claimRejected || pendingRejected || Boolean.TRUE.equals(lineDto.getRejected());
                        BigDecimal contractCapForEngine = resolvedMaxUnitPrice != null
                                        ? resolvedMaxUnitPrice
                                        : resolvedUnitPrice;

                        ClaimLineInput lineInput = ClaimLineInput.builder()
                                        .lineId(String.valueOf(lines.size()))
                                        .serviceId(resolvedPricingItemId)
                                        .categoryId(serviceCatIdForCoverage)
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .enteredUnitPrice(requestedUnitPrice)
                                        .contractPrice(contractCapForEngine)
                                        .quantity(quantity)
                                        .manualRefusedAmount(lineDto.getManualRefusedAmount())
                                        .manualRefusalReason(effectiveLineRejectionReason != null
                                                        && !effectiveLineRejectionReason.isBlank()
                                                                        ? effectiveLineRejectionReason
                                                                        : lineDto.getManualRefusalReason())
                                        .rejected(isRejected)
                                        .build();

                        CoverageResult result = coverageEngineService.evaluateLine(engineRequest, lineInput,
                                        batchUsageContext);

                        // Covers two call sites: (a) a direct manual edit to an already-
                        // APPROVED claim (updateEntityFromDto), and (b) the async
                        // approval transition itself (recalculateForApproval), where
                        // the claim's status is still APPROVAL_IN_PROGRESS — the final
                        // ClaimStateMachine.transition(..., APPROVED) hasn't run yet.
                        // Checking only APPROVED here would silently let (b) approve a
                        // line whose coverage rule was disabled/changed after the claim
                        // was first submitted.
                        boolean isBeingApprovedOrAlreadyApproved = claim.getStatus() == ClaimStatus.APPROVED
                                        || claim.getStatus() == ClaimStatus.APPROVAL_IN_PROGRESS;
                        if (isBeingApprovedOrAlreadyApproved && result.isNotCovered() && !isRejected) {
                                String serviceLabel = resolvedServiceName != null && !resolvedServiceName.isBlank()
                                                ? resolvedServiceName
                                                : (lineDto.getServiceCode() != null ? lineDto.getServiceCode() : "الخدمة");
                                throw new BusinessRuleException(
                                                "لا يمكن اعتماد مطالبة تحتوي خدمة غير مغطاة: " + serviceLabel
                                                                + ". غيّر سياق المطالبة أو ارفض البند/المطالبة بسبب واضح.");
                        }
                        BigDecimal manualRefused = lineDto.getManualRefusedAmount() != null
                                        ? lineDto.getManualRefusedAmount()
                                        : BigDecimal.ZERO;

                        ClaimLine line = ClaimLine.builder()
                                        .claim(claim)
                                        .serviceCode(result.getServiceCode() != null ? result.getServiceCode()
                                                        : (codeToLookup != null ? codeToLookup
                                                                        : "N/A"))
                                        .serviceName(result.getServiceName() != null ? result.getServiceName()
                                                        : (resolvedServiceName != null ? resolvedServiceName
                                                                        : "Unknown Service"))
                                        .pricingItemId(resolvedPricingItemId)
                                        .pendingServiceId(pendingService == null ? null : pendingService.getId())
                                        .contractTermsId(resolvedContract.terms().getId())
                                        .contractUnitPrice(resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice)
                                        .pricingEffectiveFrom(matchedPricingItem == null ? null : matchedPricingItem.getEffectiveFrom())
                                        .pricingEffectiveTo(matchedPricingItem == null ? null : matchedPricingItem.getEffectiveTo())
                                        .dictionaryReleaseId(pendingService != null ? pendingService.getDictionaryReleaseId()
                                                        : matchedPricingItem == null ? null : matchedPricingItem.getDictionaryReleaseId())
                                        .dictionaryVersion(pendingService != null ? pendingService.getDictionaryVersion()
                                                        : matchedPricingItem == null ? null : matchedPricingItem.getDictionaryVersion())
                                        .dictionaryConceptCode(pendingService != null ? pendingService.getDictionaryConceptCode()
                                                        : matchedPricingItem == null ? null : matchedPricingItem.getDictionaryConceptCode())
                                        .classificationMethodV50(pendingService != null ? pendingService.getClassificationMethod()
                                                        : matchedPricingItem == null ? null : matchedPricingItem.getClassificationMethodV50())
                                        .classificationEvidenceId(pendingService != null ? pendingService.getClassificationEvidenceId()
                                                        : matchedPricingItem == null ? null : matchedPricingItem.getClassificationEvidenceId())
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .serviceCategoryName(serviceCatName)
                                        .originalServiceCategoryId(pricingItemCategoryId)
                                        .originalServiceCategoryName(pricingItemCategoryName)
                                        .classificationReviewed(hasCategoryOverride)
                                        .classificationReviewSource(hasCategoryOverride ? "CLAIM_REVIEWER" : null)
                                        .classificationReviewedBy(hasCategoryOverride ? currentUser.getId() : null)
                                        .classificationReviewedAt(hasCategoryOverride ? LocalDateTime.now() : null)
                                        .classificationReviewNote(hasCategoryOverride
                                                        ? "Reviewer changed claim line category before coverage calculation"
                                                        : null)
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
                                        .manualRefusalReason(effectiveLineRejectionReason != null
                                                        && !effectiveLineRejectionReason.isBlank()
                                                                        ? effectiveLineRejectionReason
                                                                        : lineDto.getManualRefusalReason())
                                        .unitPrice(resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice)
                                        .totalPrice(result.getEffectiveTotal())
                                        .requestedUnitPrice(requestedUnitPrice)
                                        .approvedUnitPrice(result.getEffectiveUnitPrice())
                                        .quantity(quantity)
                                        .requestedTotal(lineRequestedTotal)
                                        .approvedAmount(ZERO)
                                        .companyShare(ZERO)
                                        .patientShare(ZERO)
                                        .refusedAmount(ZERO)
                                        .priceExcessRefused(isRejected ? BigDecimal.ZERO
                                                        : maxZero(result.getPriceRefused()))
                                        .limitRefused(isRejected ? BigDecimal.ZERO : maxZero(result.getLimitRefused()))
                                        .rejected(isRejected)
                                        .rejectionReason(effectiveLineRejectionReason != null
                                                        && !effectiveLineRejectionReason.isBlank()
                                                                        ? effectiveLineRejectionReason
                                                                        : (isRejected ? "مرفوض كلياً من قبل المراجع"
                                                                                        : result.getRefusalReason()))
                                        .approvedQuantity(null)
                                        .build();

                        lines.add(line);
                        totalRequestedAmount = totalRequestedAmount.add(lineRequestedTotal);
                }

                if (claim.getLines() != null) {
                        List<ClaimLine> persistedLines = new ArrayList<>(claim.getLines());
                        boolean correctionCycle = claim.getStatus() == ClaimStatus.NEEDS_CORRECTION;
                        int nextCalculationVersion = persistedLines.stream()
                                        .map(ClaimLine::getCalculationVersion)
                                        .filter(Objects::nonNull)
                                        .max(Integer::compareTo)
                                        .orElse(1) + (correctionCycle ? 1 : 0);
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

                                if (correctionCycle) {
                                        calculated.setCalculationVersion(nextCalculationVersion);
                                        calculated.setCurrentLine(true);
                                        reconciled.add(calculated);
                                } else if (existing != null) {
                                        BeanUtils.copyProperties(calculated, existing,
                                                        "id", "version", "claim", "calculationVersion",
                                                        "currentLine", "supersededAt",
                                                        "supersededByCalculationVersion");
                                        reconciled.add(existing);
                                } else {
                                        calculated.setCalculationVersion(nextCalculationVersion);
                                        reconciled.add(calculated);
                                }
                        }

                        if (correctionCycle) {
                                persistedLines.forEach(line -> line.supersedeBy(nextCalculationVersion));
                        }
                        claim.getLines().clear();
                        claim.getLines().addAll(reconciled);
                } else {
                        claim.setLines(lines);
                }
                financialAdjudicationService.adjudicate(claim);
                claim.setRequestedAmount(totalRequestedAmount);
                calculateClaimTotals(claim);
        }

        private void calculateClaimTotals(Claim claim) {
                ClaimFinancialTotals.aggregate(claim);

                // GUARD 1 (finance-00 step 3): fails closed here, at the moment the
                // claim's aggregate fields are derived from its own lines. This proves
                // the aggregation is correct right now -- it does NOT prove nothing
                // rewrites these fields afterward; see GUARD 2 at the approval gate
                // in ClaimFinancialSnapshotService.finalizeSnapshot for that half.
                claimFinancialInvariantGuard.assertConsistent(claim);
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
                                        .pendingServiceId(line.getPendingServiceId())
                                        .pricingEffectiveFrom(line.getPricingEffectiveFrom())
                                        .pricingEffectiveTo(line.getPricingEffectiveTo())
                                        .dictionaryReleaseId(line.getDictionaryReleaseId())
                                        .dictionaryVersion(line.getDictionaryVersion())
                                        .dictionaryConceptCode(line.getDictionaryConceptCode())
                                        .classificationMethodV50(line.getClassificationMethodV50())
                                        .classificationEvidenceId(line.getClassificationEvidenceId())
                                        .serviceCode(line.getServiceCode())
                                        .serviceName(line.getServiceName())
                                        .serviceCategoryId(line.getServiceCategoryId())
                                        .serviceCategoryName(line.getServiceCategoryName())
                                        .originalServiceCategoryId(line.getOriginalServiceCategoryId())
                                        .originalServiceCategoryName(line.getOriginalServiceCategoryName())
                                        .classificationReviewed(line.getClassificationReviewed())
                                        .classificationReviewSource(line.getClassificationReviewSource())
                                        .classificationReviewedBy(line.getClassificationReviewedBy())
                                        .classificationReviewedAt(line.getClassificationReviewedAt())
                                        .classificationReviewNote(line.getClassificationReviewNote())
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
                                // القيمة المحفوظة فعلياً على المطالبة (companyDiscountAmount)، لا إعادة
                                // حساب من النسبة — إعادة الحساب كانت تتجاهل refusedAmount وتوقيت الخصم
                                // (قبل/بعد المرفوض)، فتنحرف عن اللقطة المالية الحقيقية للمطالبة.
                                .companyDiscountAmount(claim.getCompanyDiscountAmount())
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
                                .pendingServiceId(line.getPendingServiceId())
                                .pricingEffectiveFrom(line.getPricingEffectiveFrom())
                                .pricingEffectiveTo(line.getPricingEffectiveTo())
                                .dictionaryReleaseId(line.getDictionaryReleaseId())
                                .dictionaryVersion(line.getDictionaryVersion())
                                .dictionaryConceptCode(line.getDictionaryConceptCode())
                                .classificationMethodV50(line.getClassificationMethodV50())
                                .classificationEvidenceId(line.getClassificationEvidenceId())
                                .serviceCode(line.getServiceCode())
                                .serviceName(line.getServiceName())
                                .serviceCategoryId(line.getServiceCategoryId())
                                .serviceCategoryName(line.getServiceCategoryName())
                                .originalServiceCategoryId(line.getOriginalServiceCategoryId())
                                .originalServiceCategoryName(line.getOriginalServiceCategoryName())
                                .classificationReviewed(line.getClassificationReviewed())
                                .classificationReviewSource(line.getClassificationReviewSource())
                                .classificationReviewedBy(line.getClassificationReviewedBy())
                                .classificationReviewedAt(line.getClassificationReviewedAt())
                                .classificationReviewNote(line.getClassificationReviewNote())
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

        ProviderContractPricingItem resolvePricingItemForLine(
                        Long contractId,
                        LocalDate serviceDate,
                        Long explicitPricingItemId,
                        String serviceCode,
                        String serviceName) {
                if (explicitPricingItemId != null) {
                        var exactVersion = pricingItemRepository.findEffectiveInContractById(
                                        contractId, explicitPricingItemId, serviceDate);
                        if (exactVersion.isPresent()) {
                                return exactVersion.get();
                        }

                        // A draft may retain the ID of an older price version after an
                        // identical service price is versioned/reposted. Only recover when
                        // that stale ID belongs to the SAME resolved contract; an ID from
                        // another contract must remain fail-closed.
                        var staleVersion = pricingItemRepository.findById(explicitPricingItemId).orElse(null);
                        if (staleVersion == null || staleVersion.getContract() == null
                                        || !contractId.equals(staleVersion.getContract().getId())) {
                                return null;
                        }
                        if (!hasBusinessValue(serviceCode)) {
                                serviceCode = staleVersion.getServiceCode();
                        }
                        if (!hasBusinessValue(serviceName)) {
                                serviceName = staleVersion.getServiceName();
                        }
                }

                if (!hasBusinessValue(serviceCode) && !hasBusinessValue(serviceName)) {
                        return null;
                }

                if (hasBusinessValue(serviceCode)) {
                        var byCode = pricingItemRepository.findEffectiveInContractByCode(contractId, serviceCode, serviceDate);
                        if (byCode.isPresent()) return byCode.get();
                }
                return hasBusinessValue(serviceName)
                                ? pricingItemRepository.findEffectiveInContractByName(contractId, serviceName, serviceDate).orElse(null)
                                : null;
        }

        private boolean hasBusinessValue(String value) {
                if (value == null) {
                        return false;
                }
                String trimmed = value.trim();
                return !trimmed.isEmpty() && !"-".equals(trimmed);
        }
}
