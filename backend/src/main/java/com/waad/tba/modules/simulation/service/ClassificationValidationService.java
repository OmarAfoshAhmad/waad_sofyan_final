package com.waad.tba.modules.simulation.service;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.simulation.dto.ClassificationValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationValidationService {

    private final MedicalCategoryRepository categoryRepository;

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "تجميل", "عقم", "خصوبة", "زرع", "تخدير فقط", "سعر صفر", "misc", "others",
            "cosmetic", "infertility", "fertility", "transplant", "anesthesia only", "free"
    );

    public ClassificationValidationResult validate(
            String proposedCategoryCode,
            String serviceName,
            String sourceMainCategory,
            String sourceSubCategory,
            String encounterType,
            Double frontendConfidence) {

        List<String> warnings = new ArrayList<>();
        boolean categoryExists = false;
        boolean categoryActive = false;
        double backendConfidence = frontendConfidence != null ? frontendConfidence : 0.0;
        boolean requiresReview = false;
        String validationStatus = "VALID";
        String validationReason = "تصنيف سليم";
        
        MedicalCategory category = null;
        if (proposedCategoryCode != null && !proposedCategoryCode.isBlank()) {
            category = categoryRepository.findByCode(proposedCategoryCode).orElse(null);
        }

        if (category != null) {
            categoryExists = true;
            categoryActive = category.isActive();
            
            if (!categoryActive) {
                requiresReview = true;
                validationStatus = "INVALID_CATEGORY";
                validationReason = "التصنيف المقترح غير فعال حالياً";
                warnings.add("التصنيف موجود لكنه معطل");
            }

            // Check encounter type mismatch
            if (encounterType != null && category.getContext() != null && category.getContext() != CategoryContext.ANY) {
                boolean isContextInpatient = category.getContext() == CategoryContext.INPATIENT || category.getContext() == CategoryContext.OPERATING_ROOM;
                boolean isRequestedInpatient = encounterType.equalsIgnoreCase("INPATIENT");
                
                if (isContextInpatient != isRequestedInpatient) {
                    requiresReview = true;
                    validationStatus = "CONTEXT_MISMATCH";
                    validationReason = "التصنيف لا يناسب نوع اللقاء المختار";
                    warnings.add("تعارض بين نوع اللقاء وسياق التصنيف");
                }
            }
        } else {
            requiresReview = true;
            validationStatus = "INVALID_CATEGORY";
            validationReason = "التصنيف المقترح غير موجود في النظام";
            warnings.add("كود التصنيف مفقود أو غير صحيح");
        }

        // Check for sensitive keywords requiring review
        if (serviceName != null) {
            for (String keyword : SENSITIVE_KEYWORDS) {
                if (Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(serviceName).find()) {
                    requiresReview = true;
                    backendConfidence = Math.min(backendConfidence, 0.4);
                    warnings.add("تحذير: الخدمة تحتوي على كلمات حساسة تحتاج لمراجعة يدوية (" + keyword + ")");
                    if (validationStatus.equals("VALID")) {
                        validationStatus = "LOW_CONFIDENCE";
                        validationReason = "الخدمة تتطلب مراجعة بشرية لوجود كلمات حساسة";
                    }
                    break;
                }
            }
        }

        if (backendConfidence < 0.6 && validationStatus.equals("VALID")) {
            requiresReview = true;
            validationStatus = "LOW_CONFIDENCE";
            validationReason = "ثقة التصنيف منخفضة جداً";
        }

        return ClassificationValidationResult.builder()
                .categoryExists(categoryExists)
                .categoryActive(categoryActive)
                .backendConfidence(backendConfidence)
                .requiresReview(requiresReview)
                .validationStatus(validationStatus)
                .validationReason(validationReason)
                .recommendedCategoryCode(category != null ? category.getCode() : null)
                .warnings(warnings)
                .build();
    }
}
