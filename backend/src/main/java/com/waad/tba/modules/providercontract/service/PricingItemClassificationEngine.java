package com.waad.tba.modules.providercontract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.dto.ClassificationResult;
import com.waad.tba.modules.providercontract.entity.ServiceSpecialtyInsuranceMap;
import com.waad.tba.modules.providercontract.repository.ServiceSpecialtyInsuranceMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingItemClassificationEngine {

    private final ServiceSpecialtyInsuranceMapRepository mapRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public ClassificationResult classify(String serviceName, String mainCategory, String subCategory, Long providerId) {
        List<ServiceSpecialtyInsuranceMap> rules = mapRepository.findActiveByProviderOrGlobal(providerId);

        for (ServiceSpecialtyInsuranceMap rule : rules) {
            if (matchesRule(rule, serviceName, subCategory)) {
                MedicalCategory category = categoryRepository.findByCode(rule.getInsuranceCategoryCode()).orElse(null);
                if (category != null) {
                    return ClassificationResult.builder()
                            .category(category)
                            .encounterType(rule.getDefaultEncounterType())
                            .confidenceLevel(rule.getConfidenceLevel())
                            .requiresReview(rule.getRequiresReview())
                            .reviewReason(rule.getReviewReason())
                            .classificationSource("SPECIALTY_MAP_RULE_" + rule.getId())
                            .build();
                }
            }
        }

        // Fallback based on mainCategory
        if ("إيواء".equals(mainCategory) || "inpatient".equalsIgnoreCase(mainCategory)) {
            MedicalCategory fallbackCategory = categoryRepository.findByCode("SUB-INPAT-GENERAL").orElse(null);
            return ClassificationResult.inpatientGeneral(fallbackCategory, "MAIN_CATEGORY_FALLBACK");
        }

        return ClassificationResult.unclassified();
    }

    private boolean matchesRule(ServiceSpecialtyInsuranceMap rule, String serviceName, String specialty) {
        boolean specialtyMatch = false;
        if (rule.getSourceSpecialtyNameAr() != null && specialty != null) {
            specialtyMatch = specialty.contains(rule.getSourceSpecialtyNameAr());
        }

        boolean keywordMatch = false;
        if (rule.getKeywordPatterns() != null && serviceName != null) {
            keywordMatch = matchesKeywords(rule.getKeywordPatterns(), serviceName);
        }

        return switch (rule.getMatchField()) {
            case "SPECIALTY" -> specialtyMatch;
            case "SERVICE_NAME" -> keywordMatch;
            default -> specialtyMatch || keywordMatch; // "BOTH"
        };
    }

    private boolean matchesKeywords(String jsonPatterns, String text) {
        try {
            List<String> patterns = objectMapper.readValue(jsonPatterns, new TypeReference<>() {});
            for (String patternStr : patterns) {
                // simple contains or regex depending on the pattern complexity.
                // using case insensitive regex match
                Pattern p = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (p.matcher(text).find()) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse keyword patterns JSON: {}", jsonPatterns, e);
        }
        return false;
    }
}
