package com.waad.tba.modules.simulation.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService.CoverageSource;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService.ResolvedCoverage;
import com.waad.tba.modules.claim.dto.simulation.SimulationItemRequestDto;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.simulation.entity.CoverageSimulationItem;
import com.waad.tba.modules.simulation.entity.CoverageSimulationRun;
import com.waad.tba.modules.simulation.repository.CoverageSimulationItemRepository;
import com.waad.tba.modules.simulation.repository.CoverageSimulationRunRepository;
import com.waad.tba.modules.simulation.dto.ClassificationValidationResult;
import com.waad.tba.modules.simulation.dto.CoverageSimulationItemDto;
import com.waad.tba.modules.simulation.dto.CoverageSimulationRequestDto;
import com.waad.tba.modules.simulation.dto.CoverageSimulationResultDto;
import com.waad.tba.modules.simulation.enums.SimulationSeverity;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.service.AuditLogWriteRequest;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.waad.tba.modules.semantic.dto.MedicalClassificationRequestDto;
import com.waad.tba.modules.semantic.dto.MedicalClassificationResultDto;
import com.waad.tba.modules.semantic.service.MedicalSemanticClassificationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageSimulationService {

    private final BenefitPolicyCoverageService coverageEngine;
    private final ClassificationValidationService classificationValidationService;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final ProviderContractRepository providerContractRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final CoverageSimulationRunRepository runRepository;
    private final CoverageSimulationItemRepository runItemRepository;
    private final MedicalAuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final MedicalSemanticClassificationService semanticClassificationService;

    public CoverageSimulationResultDto simulateCoverage(CoverageSimulationRequestDto request) {
        ProviderContract contract = providerContractRepository.findById(request.getContractId())
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));
        
        BenefitPolicy policy = benefitPolicyRepository.findById(request.getPolicyId())
                .orElseThrow(() -> new IllegalArgumentException("Benefit Policy not found"));

        List<ProviderContractPricingItem> pricingItems = pricingItemRepository.findByContractIdAndActiveTrue(contract.getId());
        
        List<CoverageSimulationItemDto> results = new ArrayList<>();
        CoverageSimulationResultDto.AdvancedSimulationSummary.AdvancedSimulationSummaryBuilder summaryBuilder = CoverageSimulationResultDto.AdvancedSimulationSummary.builder();
        CoverageSimulationResultDto.SimulationFinancials.SimulationFinancialsBuilder financialsBuilder = CoverageSimulationResultDto.SimulationFinancials.builder();

        int totalItems = 0;
        BigDecimal totalRequestedValue = BigDecimal.ZERO;
        BigDecimal totalCoveredValue = BigDecimal.ZERO;
        BigDecimal totalPatientShare = BigDecimal.ZERO;
        
        // Counters
        int coveredExact = 0, coveredByParent = 0, coveredDefault = 0, excludedCategory = 0, noBenefitRule = 0;
        int invalidCategory = 0, contextMismatch = 0, needsReview = 0, lowConfidence = 0, priceZero = 0;
        int preApprovalRequired = 0, limitApplied = 0;

        for (ProviderContractPricingItem item : pricingItems) {
            if (request.getEncounterType() != null && !request.getEncounterType().equalsIgnoreCase("ALL")) {
                EncounterType expectedType = EncounterType.valueOf(request.getEncounterType().toUpperCase());
                if (item.getEncounterType() != null && item.getEncounterType() != EncounterType.ANY && item.getEncounterType() != expectedType) {
                    continue; // Skip due to filter
                }
            }

            totalItems++;
            
            String categoryCode = item.getMedicalCategory() != null ? item.getMedicalCategory().getCode() : null;
            Double confidence = item.getConfidenceLevel() == ConfidenceLevel.HIGH ? 1.0 : (item.getConfidenceLevel() == ConfidenceLevel.MEDIUM ? 0.7 : 0.4);
            CategoryContext requestedContext = request.getEncounterType() != null && !request.getEncounterType().equalsIgnoreCase("ALL") ? 
                (request.getEncounterType().equalsIgnoreCase("INPATIENT") ? CategoryContext.INPATIENT : CategoryContext.OUTPATIENT) : CategoryContext.ANY;

            CoverageSimulationItemDto dto = buildSimulationItem(
                    item.getId(),
                    item.getServiceName(),
                    item.getServiceCode(),
                    item.getContractPrice(),
                    item.getCategoryName(),
                    item.getSubCategoryName(),
                    categoryCode,
                    request.getEncounterType(),
                    item.getSpecialty(),
                    confidence,
                    "DATABASE",
                    policy,
                    requestedContext,
                    request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDate.now()
            );

            results.add(dto);

            // Update Financials
            if (item.getContractPrice() != null) totalRequestedValue = totalRequestedValue.add(item.getContractPrice());
            if (dto.getCoveredAmount() != null) totalCoveredValue = totalCoveredValue.add(dto.getCoveredAmount());
            if (dto.getPatientShare() != null) totalPatientShare = totalPatientShare.add(dto.getPatientShare());

            // Update Counters
            if (dto.isRequiresReview()) needsReview++;
            if (dto.isRequiresPreApproval()) preApprovalRequired++;
            if (dto.getCoverageStatus() != null) {
                switch(dto.getCoverageStatus()) {
                    case "COVERED_EXACT_RULE" -> coveredExact++;
                    case "COVERED_PARENT_RULE" -> coveredByParent++;
                    case "COVERED_DEFAULT" -> coveredDefault++;
                    case "EXCLUDED_CATEGORY" -> excludedCategory++;
                    case "NO_BENEFIT_RULE" -> noBenefitRule++;
                    case "INVALID_CATEGORY" -> invalidCategory++;
                    case "CONTEXT_MISMATCH" -> contextMismatch++;
                    case "LOW_CONFIDENCE" -> lowConfidence++;
                    case "PRICE_ZERO" -> priceZero++;
                }
            }
            if (dto.getAppliedLimit() != null && dto.getAppliedLimit().compareTo(dto.getCoveredAmount()) == 0 && dto.getAppliedLimit().compareTo(BigDecimal.ZERO) > 0) {
                limitApplied++;
            }
        }

        summaryBuilder.totalServices(totalItems)
            .coveredExact(coveredExact).coveredByParent(coveredByParent).coveredDefault(coveredDefault)
            .excludedCategory(excludedCategory).noBenefitRule(noBenefitRule).invalidCategory(invalidCategory)
            .contextMismatch(contextMismatch).needsReview(needsReview).lowConfidence(lowConfidence)
            .priceZero(priceZero).preApprovalRequired(preApprovalRequired).limitApplied(limitApplied);

        financialsBuilder.totalRequestedAmount(totalRequestedValue)
            .totalCoveredAmount(totalCoveredValue)
            .totalPatientShare(totalPatientShare)
            .totalCompanyShare(totalCoveredValue);

        CoverageSimulationResultDto result = CoverageSimulationResultDto.builder()
                .simulationId(UUID.randomUUID().toString())
                .contractId(contract.getId())
                .policyId(policy.getId())
                .effectiveDate(request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDate.now())
                .encounterType(request.getEncounterType())
                .generatedAt(LocalDateTime.now())
                .contractName(contract.getProvider() != null ? contract.getProvider().getName() : "")
                .contractReference(contract.getContractCode())
                .policyName(policy.getName())
                .policyCode(policy.getPolicyCode())
                .limitEvaluationMode("POLICY_THEORETICAL")
                .items(results)
                .summary(summaryBuilder.build())
                .financials(financialsBuilder.build())
                .build();

        if (Boolean.TRUE.equals(request.getSaveSnapshot())) {
            saveSnapshot(result);
        }

        return result;
    }

    public CoverageSimulationResultDto simulateRawCoverage(CoverageSimulationRequestDto request, List<SimulationItemRequestDto> items) {
        BenefitPolicy policy = benefitPolicyRepository.findById(request.getPolicyId())
                .orElseThrow(() -> new IllegalArgumentException("Benefit Policy not found"));

        List<CoverageSimulationItemDto> results = new ArrayList<>();
        CoverageSimulationResultDto.AdvancedSimulationSummary.AdvancedSimulationSummaryBuilder summaryBuilder = CoverageSimulationResultDto.AdvancedSimulationSummary.builder();
        CoverageSimulationResultDto.SimulationFinancials.SimulationFinancialsBuilder financialsBuilder = CoverageSimulationResultDto.SimulationFinancials.builder();

        int totalItems = 0;
        BigDecimal totalRequestedValue = BigDecimal.ZERO;
        BigDecimal totalCoveredValue = BigDecimal.ZERO;
        BigDecimal totalPatientShare = BigDecimal.ZERO;
        
        int coveredExact = 0, coveredByParent = 0, coveredDefault = 0, excludedCategory = 0, noBenefitRule = 0;
        int invalidCategory = 0, contextMismatch = 0, needsReview = 0, lowConfidence = 0, priceZero = 0;
        int preApprovalRequired = 0, limitApplied = 0;

        for (SimulationItemRequestDto item : items) {
            totalItems++;
            
            CategoryContext requestedContext = request.getEncounterType() != null && !request.getEncounterType().equalsIgnoreCase("ALL") ? 
                (request.getEncounterType().equalsIgnoreCase("INPATIENT") ? CategoryContext.INPATIENT : CategoryContext.OUTPATIENT) : CategoryContext.ANY;

            // Use Semantic Classification for RAW items
            MedicalClassificationRequestDto semanticReq = new MedicalClassificationRequestDto();
            semanticReq.setServiceName(item.getServiceName());
            semanticReq.setServiceCode(item.getServiceCode());
            semanticReq.setSourceMainCategory(item.getMainCategory());
            semanticReq.setSourceSubCategory(item.getSubCategory());
            semanticReq.setPrice(item.getContractPrice());
            semanticReq.setPreferredEncounterType(request.getEncounterType());

            MedicalClassificationResultDto semanticResult = semanticClassificationService.classifyService(semanticReq);

            CoverageSimulationItemDto dto = buildSimulationItem(
                    null,
                    item.getServiceName(),
                    item.getServiceCode(),
                    item.getContractPrice(),
                    item.getMainCategory(),
                    null,
                    semanticResult.getSuggestedInsuranceCategoryCode() != null ? semanticResult.getSuggestedInsuranceCategoryCode() : item.getMainCategory(), 
                    request.getEncounterType(),
                    semanticResult.getMedicalSpecialty(),
                    semanticResult.getConfidenceScore(),
                    semanticResult.getClassificationSource(),
                    policy,
                    requestedContext,
                    request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDate.now()
            );

            // Populate DTO with Semantic details
            dto.setMedicalMeaningAr(semanticResult.getMedicalMeaningAr());
            dto.setProcedureType(semanticResult.getProcedureType());
            dto.setBodySystem(semanticResult.getBodySystem());
            dto.setExplanationAr(semanticResult.getExplanationAr());
            if (Boolean.TRUE.equals(semanticResult.getRequiresReview())) {
                dto.setRequiresReview(true);
                if (dto.getWarnings() == null) dto.setWarnings(new ArrayList<>());
                if (semanticResult.getReviewReasons() != null) {
                    dto.getWarnings().addAll(semanticResult.getReviewReasons());
                }
            }

            results.add(dto);

            if (item.getContractPrice() != null) totalRequestedValue = totalRequestedValue.add(item.getContractPrice());
            if (dto.getCoveredAmount() != null) totalCoveredValue = totalCoveredValue.add(dto.getCoveredAmount());
            if (dto.getPatientShare() != null) totalPatientShare = totalPatientShare.add(dto.getPatientShare());

            if (dto.isRequiresReview()) needsReview++;
            if (dto.isRequiresPreApproval()) preApprovalRequired++;
            if (dto.getCoverageStatus() != null) {
                switch(dto.getCoverageStatus()) {
                    case "COVERED_EXACT_RULE" -> coveredExact++;
                    case "COVERED_PARENT_RULE" -> coveredByParent++;
                    case "COVERED_DEFAULT" -> coveredDefault++;
                    case "EXCLUDED_CATEGORY" -> excludedCategory++;
                    case "NO_BENEFIT_RULE" -> noBenefitRule++;
                    case "INVALID_CATEGORY" -> invalidCategory++;
                    case "CONTEXT_MISMATCH" -> contextMismatch++;
                    case "LOW_CONFIDENCE" -> lowConfidence++;
                    case "PRICE_ZERO" -> priceZero++;
                }
            }
            if (dto.getAppliedLimit() != null && dto.getAppliedLimit().compareTo(dto.getCoveredAmount()) == 0 && dto.getAppliedLimit().compareTo(BigDecimal.ZERO) > 0) {
                limitApplied++;
            }
        }

        summaryBuilder.totalServices(totalItems)
            .coveredExact(coveredExact).coveredByParent(coveredByParent).coveredDefault(coveredDefault)
            .excludedCategory(excludedCategory).noBenefitRule(noBenefitRule).invalidCategory(invalidCategory)
            .contextMismatch(contextMismatch).needsReview(needsReview).lowConfidence(lowConfidence)
            .priceZero(priceZero).preApprovalRequired(preApprovalRequired).limitApplied(limitApplied);

        financialsBuilder.totalRequestedAmount(totalRequestedValue)
            .totalCoveredAmount(totalCoveredValue)
            .totalPatientShare(totalPatientShare)
            .totalCompanyShare(totalCoveredValue);

        CoverageSimulationResultDto result = CoverageSimulationResultDto.builder()
                .simulationId(UUID.randomUUID().toString())
                .policyId(policy.getId())
                .effectiveDate(request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDate.now())
                .encounterType(request.getEncounterType())
                .generatedAt(LocalDateTime.now())
                .policyName(policy.getName())
                .policyCode(policy.getPolicyCode())
                .limitEvaluationMode("POLICY_THEORETICAL")
                .items(results)
                .summary(summaryBuilder.build())
                .financials(financialsBuilder.build())
                .build();

        if (Boolean.TRUE.equals(request.getSaveSnapshot())) {
            saveSnapshot(result);
        }

        return result;
    }

    private void saveSnapshot(CoverageSimulationResultDto result) {
        try {
            CoverageSimulationRun run = CoverageSimulationRun.builder()
                    .id(result.getSimulationId())
                    .contractId(result.getContractId())
                    .policyId(result.getPolicyId())
                    .effectiveDate(result.getEffectiveDate())
                    .encounterType(result.getEncounterType())
                    .limitEvaluationMode(result.getLimitEvaluationMode())
                    .summaryJson(objectMapper.writeValueAsString(result.getSummary()))
                    .totalServices(result.getSummary().getTotalServices())
                    .coveredCount(result.getSummary().getCoveredExact() + result.getSummary().getCoveredByParent() + result.getSummary().getCoveredDefault())
                    .excludedCount(result.getSummary().getExcludedCategory())
                    .noRuleCount(result.getSummary().getNoBenefitRule())
                    .needsReviewCount(result.getSummary().getNeedsReview())
                    .invalidCategoryCount(result.getSummary().getInvalidCategory())
                    .contextMismatchCount(result.getSummary().getContextMismatch())
                    .zeroPriceCount(result.getSummary().getPriceZero())
                    .build();

            run = runRepository.save(run);

            List<CoverageSimulationItem> items = new ArrayList<>();
            for (CoverageSimulationItemDto dto : result.getItems()) {
                CoverageSimulationItem item = CoverageSimulationItem.builder()
                        .simulationRun(run)
                        .providerServiceId(dto.getProviderServiceId())
                        .serviceName(dto.getServiceName())
                        .serviceCode(dto.getProviderServiceCode())
                        .price(dto.getRequestedAmount())
                        .sourceMainCategory(dto.getSourceMainCategory())
                        .sourceSubCategory(dto.getSourceSubCategory())
                        .categoryCode(dto.getInsuranceCategoryCode())
                        .categoryName(dto.getInsuranceCategoryName())
                        .coverageStatus(dto.getCoverageStatus())
                        .coverageReason(dto.getCoverageReason())
                        .recommendedAction(dto.getRecommendedAction())
                        .severity(dto.getSeverity())
                        .matchedRuleId(dto.getMatchedRuleId())
                        .coveragePercent(dto.getCoveragePercentage())
                        .patientShare(dto.getPatientShare())
                        .companyShare(dto.getCompanyShare())
                        .requiresReview(dto.isRequiresReview())
                        .requiresPreApproval(dto.isRequiresPreApproval())
                        .warningsJson(dto.getWarnings() != null ? objectMapper.writeValueAsString(dto.getWarnings()) : null)
                        .medicalMeaningAr(dto.getMedicalMeaningAr())
                        .procedureType(dto.getProcedureType())
                        .bodySystem(dto.getBodySystem())
                        .explanationAr(dto.getExplanationAr())
                        .classificationConfidence(dto.getClassificationConfidence())
                        .classificationSource(dto.getClassificationSource())
                        .build();
                items.add(item);
            }
            runItemRepository.saveAll(items);
            log.info("✅ Saved Snapshot for Simulation {}", result.getSimulationId());

            // Add Audit Log
            AuditLogWriteRequest auditReq = AuditLogWriteRequest.builder()
                    .entityType(EntityType.SIMULATION_RUN)
                    .entityId(run.getId())
                    .action(AuditAction.SIMULATION_EXECUTED)
                    .source(AuditSource.SYSTEM)
                    .reason("Simulation executed and snapshot saved")
                    .afterState(run.getSummaryJson())
                    .build();
            auditLogService.record(auditReq);

        } catch (Exception e) {
            log.error("❌ Failed to save simulation snapshot", e);
        }
    }

    private CoverageSimulationItemDto buildSimulationItem(
            Long serviceId,
            String serviceName,
            String serviceCode,
            BigDecimal price,
            String sourceMainCategory,
            String sourceSubCategory,
            String categoryCode,
            String encounterType,
            String specialty,
            Double confidence,
            String classificationSource,
            BenefitPolicy policy,
            CategoryContext encounterContext,
            LocalDate serviceDate
    ) {
        // 1. Validate Classification
        ClassificationValidationResult validation = classificationValidationService.validate(
                categoryCode, serviceName, sourceMainCategory, sourceSubCategory, encounterType, confidence
        );

        MedicalCategory category = null;
        if (validation.getRecommendedCategoryCode() != null) {
            category = medicalCategoryRepository.findByCode(validation.getRecommendedCategoryCode()).orElse(null);
        }

        // 2. Resolve Coverage using Canonical Engine (DRY_RUN = true)
        ResolvedCoverage coverage = null;
        
        // Skip coverage engine if confidence is too low from semantic layer
        if (confidence == null || confidence >= 0.5) {
            coverage = coverageEngine.resolveCoverage(
                    policy.getId(),
                    serviceId,
                    category != null ? category.getId() : null,
                    null,
                    null,
                    serviceDate,
                    null,
                    encounterContext,
                    validation.getBackendConfidence(),
                    price,
                    true // DRY_RUN ACTIVE
            );
        }

        // 3. Map to DTO
        CoverageSimulationItemDto.CoverageSimulationItemDtoBuilder builder = CoverageSimulationItemDto.builder()
                .providerServiceId(serviceId)
                .serviceName(serviceName)
                .providerServiceCode(serviceCode)
                .contractPrice(price)
                .sourceMainCategory(sourceMainCategory)
                .sourceSubCategory(sourceSubCategory)
                .insuranceCategoryCode(categoryCode)
                .insuranceCategoryName(category != null ? category.getNameAr() != null ? category.getNameAr() : category.getName() : null)
                .parentCategoryCode(category != null && category.getParentId() != null ? medicalCategoryRepository.findById(category.getParentId()).map(MedicalCategory::getCode).orElse(null) : null)
                .encounterType(encounterType)
                .medicalSpecialty(specialty)
                .classificationConfidence(validation.getBackendConfidence())
                .classificationSource(classificationSource)
                .requiresReview(validation.isRequiresReview())
                .warnings(validation.getWarnings());

        if (coverage != null) {
            builder.coveragePercentage(coverage.getCoveragePercent());
            builder.amountLimit(coverage.getAmountLimit());
            builder.requiresPreApproval(coverage.isRequiresPreApproval());
            builder.matchedRuleId(coverage.getRuleId());
            
            mapCoverageSourceDetails(builder, coverage.getSource());
            
            if (price != null && coverage.getCoveragePercent() >= 0) {
                BigDecimal covered = price.multiply(BigDecimal.valueOf(coverage.getCoveragePercent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (coverage.getAmountLimit() != null && covered.compareTo(coverage.getAmountLimit()) > 0) {
                    covered = coverage.getAmountLimit();
                    builder.appliedLimit(coverage.getAmountLimit());
                }
                builder.requestedAmount(price);
                builder.coveredAmount(covered);
                builder.companyShare(covered);
                builder.patientShare(price.subtract(covered));
            }
        } else if (confidence != null && confidence < 0.5) {
            builder.coverageStatus("LOW_CONFIDENCE");
            builder.coverageReason("مؤشر الثقة منخفض جداً لإعتماد التغطية تلقائياً");
            builder.recommendedAction("يحتاج مراجعة التصنيف الطبي");
            builder.severity(SimulationSeverity.ERROR.name());
            builder.requiresReview(true);
        } else {
            builder.coverageStatus("ERROR");
            builder.coverageReason("خطأ غير متوقع في محرك التغطية");
            builder.severity(SimulationSeverity.CRITICAL.name());
        }

        return builder.build();
    }

    private void mapCoverageSourceDetails(CoverageSimulationItemDto.CoverageSimulationItemDtoBuilder builder, CoverageSource source) {
        if (source == null) return;
        switch (source) {
            case EXACT_CATEGORY_RULE -> {
                builder.coverageStatus("COVERED_EXACT_RULE");
                builder.coverageReason("مغطاة بقاعدة مباشرة لهذا التصنيف");
                builder.severity(SimulationSeverity.INFO.name());
            }
            case PARENT_CATEGORY_RULE -> {
                builder.coverageStatus("COVERED_PARENT_RULE");
                builder.coverageReason("مغطاة بقاعدة عامة للتصنيف الأب");
                builder.severity(SimulationSeverity.INFO.name());
            }
            case POLICY_DEFAULT -> {
                builder.coverageStatus("COVERED_DEFAULT");
                builder.coverageReason("مغطاة بالنسبة الافتراضية للوثيقة لعدم وجود قاعدة");
                builder.severity(SimulationSeverity.WARNING.name());
                builder.recommendedAction("يُفضل إنشاء قاعدة صريحة لهذا التصنيف");
            }
            case EXCLUDED_CATEGORY -> {
                builder.coverageStatus("EXCLUDED_CATEGORY");
                builder.coverageReason("التصنيف مستثنى صراحة في الوثيقة");
                builder.severity(SimulationSeverity.CRITICAL.name());
                builder.recommendedAction("لا تعتمد الخدمة إلا بتصريح استثنائي");
            }
            case NO_BENEFIT_RULE -> {
                builder.coverageStatus("NO_BENEFIT_RULE");
                builder.coverageReason("التصنيف سليم لكن لا توجد قاعدة تغطية");
                builder.severity(SimulationSeverity.ERROR.name());
                builder.recommendedAction("أنشئ قاعدة تغطية جديدة لهذا التصنيف");
            }
            case INVALID_CATEGORY -> {
                builder.coverageStatus("INVALID_CATEGORY");
                builder.coverageReason("التصنيف غير موجود أو غير فعال");
                builder.severity(SimulationSeverity.ERROR.name());
                builder.recommendedAction("صحح التصنيف فوراً");
            }
            case CONTEXT_MISMATCH -> {
                builder.coverageStatus("CONTEXT_MISMATCH");
                builder.coverageReason("تعارض بين نوع اللقاء وسياق التصنيف");
                builder.severity(SimulationSeverity.WARNING.name());
                builder.recommendedAction("تأكد من نوع اللقاء المختار");
            }
            case LOW_CONFIDENCE -> {
                builder.coverageStatus("LOW_CONFIDENCE");
                builder.coverageReason("ثقة تصنيف الخدمة منخفضة أو تحتوي كلمات حساسة");
                builder.severity(SimulationSeverity.WARNING.name());
                builder.recommendedAction("راجع التصنيف يدوياً واعتمدها");
            }
            case PRICE_ZERO -> {
                builder.coverageStatus("PRICE_ZERO");
                builder.coverageReason("سعر الخدمة صفر أو غير صالح");
                builder.severity(SimulationSeverity.ERROR.name());
                builder.recommendedAction("حدد السعر المعتمد للخدمة");
            }
            default -> {
                builder.coverageStatus(source.name());
                builder.coverageReason("حالة تغطية: " + source.name());
                builder.severity(SimulationSeverity.INFO.name());
            }
        }
    }
}
