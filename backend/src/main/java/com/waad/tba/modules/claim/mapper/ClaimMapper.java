package com.waad.tba.modules.claim.mapper;

import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final com.waad.tba.modules.claim.repository.ClaimBatchRepository claimBatchRepository;

    /**
     * Resolve the effective BenefitPolicy for a member.
     * Direct link first; falls back to employer's active effective policy.
     */
    private com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy resolvePolicy(
            com.waad.tba.modules.member.entity.Member member) {
        if (member == null)
            return null;
        var direct = member.getBenefitPolicy();
        if (direct != null)
            return direct;
        if (member.getEmployer() != null) {
            return benefitPolicyRepository
                    .findActiveEffectivePolicyForEmployer(member.getEmployer().getId(), java.time.LocalDate.now())
                    .orElse(null);
        }
        return null;
    }

    /**
     * Maps CreateClaimDto (from Visit) to a new Claim entity.
     * Enforces contract-first pricing and policy-first coverage.
     */
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
                .manualCategoryEnabled(dto.getManualCategoryEnabled() != null ? dto.getManualCategoryEnabled() : false)
                .primaryCategoryCode(dto.getPrimaryCategoryCode())
                .fullCoverage(dto.getFullCoverage() != null ? dto.getFullCoverage() : false)
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
            // ARCHITECTURAL LAW: Capture what was requested vs what is allowed by contract.
            BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal resolvedUnitPrice = null;

            boolean isBacklog = visit.getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG;

            String serviceCode = lineDto.getServiceCode();
            String serviceName = lineDto.getServiceName();

            log.info("🔍 [MAPPER] Processing line for service '{}'. VisitType: {}, isBacklog: {}, Entered Price: {}",
                    serviceCode, visit.getVisitType(), isBacklog, enteredUnitPrice);

            Long resolvedPricingItemId = lineDto.getPricingItemId();

            // دائماً نبحث عن سعر العقد بغض النظر عن نوع الزيارة (BACKLOG أو غيره)
            // لضمان حساب المبلغ المرفوض بشكل صحيح عند تجاوز سعر العقد
            String codeToLookup = lineDto.getServiceCode();

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

                    log.info("✅ [MAPPER] Contract price resolved: {} → {} (entered: {})",
                            codeToLookup, resolvedUnitPrice, enteredUnitPrice);
                }
            }

            // FALLBACK: إذا لم يُوجَد عقد بالتاريخ لكن pricingItemId محدد (مثلاً تاريخ
            // الخدمة قبل بداية العقد)
            // نأخذ سعر العقد مباشرة من الـ pricing_item لأن المستخدم اختار الخدمة بوعي
            if (resolvedUnitPrice == null && lineDto.getPricingItemId() != null) {
                resolvedUnitPrice = pricingItemRepository.findById(lineDto.getPricingItemId())
                        .map(com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem::getContractPrice)
                        .orElse(null);
                if (resolvedUnitPrice != null) {
                    log.info("🔄 [MAPPER] Fallback to pricingItemId={} contractPrice={}",
                            lineDto.getPricingItemId(), resolvedUnitPrice);
                }
            }

            if (resolvedUnitPrice == null) {
                // لا يوجد عقد ولا خدمة طبية موجودة → نستخدم السعر المدخل
                resolvedUnitPrice = enteredUnitPrice;
                log.info("⚠️ [MAPPER] No contract price found for '{}', using entered price: {}",
                        codeToLookup, enteredUnitPrice);
            }

            // Resolve coverage snapshot
            Integer coveragePercentSnapshot = lineDto.getCoveragePercent();
            boolean requiresPA = lineDto.getRequiresPA() != null ? lineDto.getRequiresPA() : false;

            // For all services, infer the category from the pricing item's
            // medical_category_id. This ensures subcategory rules
            // (amount/times limits) are matched instead of falling back to POLICY_DEFAULT.
            Long pricingItemCategoryId = null;
            if (resolvedPricingItemId != null) {
                pricingItemCategoryId = pricingItemRepository.findById(resolvedPricingItemId)
                        .map(item -> item.getMedicalCategory() != null ? item.getMedicalCategory().getId() : null)
                        .orElse(null);
                if (pricingItemCategoryId != null) {
                    log.info("📂 [MAPPER] Inferred category from pricingItem {}: catId={}",
                            resolvedPricingItemId, pricingItemCategoryId);
                }
            }

            // Resolve the best category for coverage lookup:
            // 1. Manual override (manualCategoryEnabled=true)
            // 2. PricingItem's category
            // 3. DTO's serviceCategoryId (from DTO or previous save)
            Long serviceCatIdForCoverage = pricingItemCategoryId != null ? pricingItemCategoryId
                    : lineDto.getAppliedCategoryId();

            // Resolve coverage:
            // When fullCoverage=true → 100% coverage, skip benefit rule lookup
            BenefitPolicyCoverageService.ResolvedCoverage coverageResult = null;
            if (Boolean.TRUE.equals(claim.getFullCoverage())) {
                coveragePercentSnapshot = 100;
                log.info("✅ [MAPPER] Full coverage override → 100%");
            } else {
                // Pass both serviceCatIdForCoverage AND categoryOverrideId so the repository
                // can find the most specific matching rule:
                // priority 0 = exact service category (e.g. CAT-IP-PHYSIO)
                // priority 1 = child of override context (e.g. CAT-OP-PHYSIO when
                // context=CAT-OP)
                // priority 2 = exact override (e.g. CAT-OP root)
                coverageResult = benefitPolicyCoverageService.resolveCoverage(
                        resolvePolicy(claim.getMember()) != null ? resolvePolicy(claim.getMember()).getId() : null,
                        null,
                        serviceCatIdForCoverage,
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
            }

            // Fetch applied category info.
            Long rawCategoryId = pricingItemCategoryId != null ? pricingItemCategoryId
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

            // lineRefused = max(priceExcessRefusal, clientRefusedAmount)
            // The client computes limit-based refusals (timesLimit / amountLimit) that the
            // backend mapper cannot independently recompute here. We trust the larger
            // value.
            BigDecimal clientRefused = lineDto.getRefusedAmount() != null
                    ? lineDto.getRefusedAmount()
                    : BigDecimal.ZERO;
            BigDecimal manualRefused = lineDto.getManualRefusedAmount() != null
                    ? lineDto.getManualRefusedAmount()
                    : BigDecimal.ZERO;

            BigDecimal lineRefused = isRejected
                    ? lineRequestedTotal
                    : priceExcessRefusal.max(clientRefused);

            ClaimLine line = ClaimLine.builder()
                    .claim(claim)
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
                    .manualRefusedAmount(manualRefused)
                    .priceExcessRefused(isRejected ? BigDecimal.ZERO : priceExcessRefusal)
                    .limitRefused(isRejected ? BigDecimal.ZERO
                            : lineRefused.subtract(priceExcessRefusal).max(BigDecimal.ZERO))
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

        // Pre-calculate financial snapshots if created AS-APPROVED or AS-SETTLED
        if (claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.SETTLED) {
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
            // Patient CoPay = Total Requested - Company Approved
            // This includes: price-excess refused + limit-refused + co-pay on net portion.
            // For fully rejected claims this equals totalRequestedAmount.
            claim.setPatientCoPay(totalRequestedAmount.subtract(totalNetProvider.max(BigDecimal.ZERO)));
            claim.setNetProviderAmount(totalNetProvider); // Insurance share
            // calculateFields() will be called automatically by @PrePersist/@PreUpdate
        } else {
            // احتساب المبلغ المرفوض حتى للمطالبات في حالة DRAFT حتى تظهر الأرقام الصحيحة في
            // العرض
            BigDecimal totalRefusedDraft = lines.stream()
                    .map(l -> l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            claim.setRefusedAmount(totalRefusedDraft);
            // Keep non-settled claims nullable to allow lifecycle hooks to derive
            // snapshots consistently from line data.
            claim.setApprovedAmount(null);
        }

        return claim;
    }

    /**
     * Re-applies pricing and coverage to claim lines during draft edit.
     */
    public void replaceClaimLinesForDraft(Claim claim, List<ClaimLineDto> lineDtos) {
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
            // ARCHITECTURAL LAW: Capture what was requested vs what is allowed by contract.
            BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal resolvedUnitPrice = null;

            boolean isBacklog = claim.getVisit() != null
                    && claim.getVisit().getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG;

            String serviceCode = lineDto.getServiceCode();
            String serviceName = lineDto.getServiceName();

            Long resolvedPricingItemId = lineDto.getPricingItemId();

            // دائماً نبحث عن سعر العقد بغض النظر عن نوع الزيارة
            String codeToLookup = lineDto.getServiceCode();

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
                }
            }

            // FALLBACK: إذا لم يُوجَد عقد بالتاريخ لكن pricingItemId محدد
            if (resolvedUnitPrice == null && lineDto.getPricingItemId() != null) {
                resolvedUnitPrice = pricingItemRepository.findById(lineDto.getPricingItemId())
                        .map(com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem::getContractPrice)
                        .orElse(null);
                if (resolvedUnitPrice != null) {
                    log.info("🔄 [REPLACE_DRAFT] Fallback to pricingItemId={} contractPrice={}",
                            lineDto.getPricingItemId(), resolvedUnitPrice);
                }
            }

            if (resolvedUnitPrice == null) {
                resolvedUnitPrice = enteredUnitPrice;
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

            // Infer the category from the pricing item's medical_category_id.
            Long pricingItemCategoryId = null;
            if (resolvedPricingItemId != null) {
                pricingItemCategoryId = pricingItemRepository.findById(resolvedPricingItemId)
                        .map(item -> item.getMedicalCategory() != null ? item.getMedicalCategory().getId() : null)
                        .orElse(null);
                if (pricingItemCategoryId != null) {
                    log.info("📂 [REPLACE_MAPPER] Inferred category from pricingItem {}: catId={}",
                            resolvedPricingItemId, pricingItemCategoryId);
                }
            }

            Long serviceCatIdForCoverage = pricingItemCategoryId != null ? pricingItemCategoryId
                    : lineDto.getAppliedCategoryId();

            // When fullCoverage=true → 100% coverage, skip benefit rule lookup
            BenefitPolicyCoverageService.ResolvedCoverage coverageResult = null;
            if (Boolean.TRUE.equals(claim.getFullCoverage())) {
                coveragePercentSnapshot = 100;
                log.info("✅ [REPLACE_MAPPER] Full coverage override → 100%");
            } else {
                // Pass both serviceCatIdForCoverage AND categoryOverrideId so the repository
                // finds the most specific rule (exact service cat beats child-of-override).
                coverageResult = benefitPolicyCoverageService.resolveCoverage(
                        resolvePolicy(claim.getMember()) != null ? resolvePolicy(claim.getMember()).getId() : null,
                        null,
                        serviceCatIdForCoverage,
                        categoryOverrideId,
                        claim.getMember().getId(),
                        claim.getServiceDate(),
                        claim.getId());

                if (coverageResult != null) {
                    requiresPA = coverageResult.isRequiresPreApproval();
                    coveragePercentSnapshot = coverageResult.getCoveragePercent();
                }
            }

            // Fetch applied category info.
            Long rawCategoryId = pricingItemCategoryId != null ? pricingItemCategoryId
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

            // Declare outside if/else so builder can reference them in both branches
            BigDecimal clientRefused = lineDto.getRefusedAmount() != null
                    ? lineDto.getRefusedAmount()
                    : BigDecimal.ZERO;
            BigDecimal manualRefused = lineDto.getManualRefusedAmount() != null
                    ? lineDto.getManualRefusedAmount()
                    : BigDecimal.ZERO;

            if (Boolean.TRUE.equals(lineDto.getRejected())) {
                lineRefused = lineRequestedTotal;
                // Company pays nothing → patient bears the full amount
                linePatientShare = lineRequestedTotal;
            } else {
                // lineRefused = max(priceExcessRefusal, clientRefusedAmount)
                // The client computes limit-based refusals (timesLimit / amountLimit) that
                // cannot be recomputed independently here; take the larger value.

                lineRefused = priceExcessRefusal.max(clientRefused);

                // Net Available (Allowed base) = resolvedTotal - (any other refusal beyond
                // price)
                BigDecimal netAvailable = lineApprovedTotal.subtract(lineRefused.subtract(priceExcessRefusal))
                        .max(BigDecimal.ZERO);

                if (coveragePercentSnapshot != null) {
                    // Company Share (Approved) = Net Available * Coverage%
                    lineApproved = netAvailable.multiply(BigDecimal.valueOf(coveragePercentSnapshot))
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

                    // Patient Share = Everything the company did NOT approve
                    // = refused portion (price excess + limit) + co-pay on net available
                    linePatientShare = lineRequestedTotal.subtract(lineApproved);
                } else {
                    lineApproved = netAvailable;
                    // No coverage info → company pays all net, patient bears only the refused
                    linePatientShare = lineRefused;
                }
            }

            ClaimLine line = ClaimLine.builder()
                    .claim(claim)
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
                    .manualRefusedAmount(manualRefused)
                    .priceExcessRefused(
                            Boolean.TRUE.equals(lineDto.getRejected()) ? BigDecimal.ZERO : priceExcessRefusal)
                    .limitRefused(Boolean.TRUE.equals(lineDto.getRejected()) ? BigDecimal.ZERO
                            : lineRefused.subtract(priceExcessRefusal).max(BigDecimal.ZERO))
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
                .expectedCompletionDate(claim.getExpectedCompletionDate())
                .actualCompletionDate(claim.getActualCompletionDate())
                .withinSla(claim.getWithinSla())
                .businessDaysTaken(claim.getBusinessDaysTaken())
                .slaDaysConfigured(claim.getSlaDaysConfigured())
                .slaStatus(calculateSlaStatus(claim))
                .manualCategoryEnabled(claim.getManualCategoryEnabled())
                .primaryCategoryCode(claim.getPrimaryCategoryCode())
                .fullCoverage(claim.getFullCoverage())
                .primaryCategoryName(claim.getPrimaryCategoryCode() != null
                        ? medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                                .map(com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory::getName).orElse(null)
                        : null)
                .claimBatchId(claim.getClaimBatch() != null ? claim.getClaimBatch().getId() : null)
                .claimBatchCode(claim.getClaimBatch() != null ? claim.getClaimBatch().getBatchCode() : null)
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
            dto.setPreApprovalReferenceNumber(claim.getPreAuthorization().getReferenceNumber());
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
                .medicalServiceId(null)
                .pricingItemId(line.getPricingItemId())
                .serviceCode(line.getServiceCode())
                .serviceName(line.getServiceName())
                .serviceCategoryId(line.getServiceCategoryId())
                .serviceCategoryName(line.getServiceCategoryName())
                .requiresPA(line.getRequiresPA())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .requestedUnitPrice(line.getRequestedUnitPrice())
                .totalPrice(line.getTotalPrice())
                .rejected(line.getRejected())
                .rejectionReason(line.getRejectionReason())
                .refusedAmount(line.getRefusedAmount())
                .manualRefusedAmount(line.getManualRefusedAmount())
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
