package com.waad.tba.modules.semantic.service;

import com.waad.tba.modules.semantic.dto.MedicalClassificationRequestDto;
import com.waad.tba.modules.semantic.dto.MedicalClassificationResultDto;
import com.waad.tba.modules.semantic.entity.MedicalSemanticRule;
import com.waad.tba.modules.semantic.entity.enums.BodySystem;
import com.waad.tba.modules.semantic.entity.enums.MedicalSpecialty;
import com.waad.tba.modules.semantic.entity.enums.ProcedureComplexity;
import com.waad.tba.modules.semantic.entity.enums.ProcedureType;
import com.waad.tba.modules.semantic.repository.MedicalSemanticRuleRepository;
import com.waad.tba.common.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalSemanticClassificationService {

    private final MedicalSemanticRuleRepository ruleRepository;
    private final SystemSettingsService systemSettingsService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();

    public MedicalClassificationResultDto classifyService(MedicalClassificationRequestDto request) {
        String serviceName = request.getServiceName();
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return createUnknownResult(request);
        }

        String normalized = normalize(serviceName);
        MedicalClassificationResultDto result = new MedicalClassificationResultDto();
        result.setOriginalServiceName(serviceName);
        result.setNormalizedServiceName(normalized);
        result.setDetectedLanguage(detectLanguage(normalized));
        result.setRequiresReview(false);
        result.setReviewReasons(new ArrayList<>());
        result.setMatchedKeywords(new ArrayList<>());
        result.setWarnings(new ArrayList<>());

        // First pass: Try to match from database rules using Fuzzy String Matching
        List<MedicalSemanticRule> activeRules = ruleRepository.findByIsActiveTrueOrderByPriorityDesc();
        boolean matched = false;
        double bestScore = 0.0;
        MedicalSemanticRule bestRule = null;

        for (MedicalSemanticRule rule : activeRules) {
            double currentScore = calculatePatternMatchScore(normalized, rule.getKeywordPattern(), rule.getRegexEnabled());
            if (currentScore >= 0.85 && currentScore > bestScore) {
                bestScore = currentScore;
                bestRule = rule;
                if (bestScore == 1.0) {
                    break; // Exact match found, stop searching to save time
                }
            }
        }

        if (bestRule != null) {
            applyRuleToResult(bestRule, result);
            if (result.getConfidenceScore() == null || bestScore > result.getConfidenceScore()) {
                result.setConfidenceScore(bestScore);
            }
            if (bestScore < 1.0) {
                result.setClassificationSource("FUZZY_MATCH");
            }
            matched = true;
        }

        // Second pass: Hardcoded heuristics if no DB rule matches
        if (!matched) {
            applyHeuristics(normalized, result);
        }

        // Third pass: Call external BioBERT model if still unknown/low confidence
        if (result.getSuggestedInsuranceCategoryCode() == null) {
            callBioBertModel(normalized, result);
        }

        calculateConfidence(result, normalized);

        return result;
    }

    private void applyHeuristics(String s, MedicalClassificationResultDto result) {
        s = s.toLowerCase();
        
        // Minor Surgeries / Dermatology
        if (s.contains("sebaceous cyst") || s.contains("cyst removal") || s.contains("lipoma") || s.contains("abscess drainage")
            || s.contains("كيس دهني") || s.contains("كيس جلدي") || s.contains("خراج") || s.contains("استئصال كيس")) {
            
            result.setBodySystem(BodySystem.SKIN_SOFT_TISSUE.name());
            result.setMedicalSpecialty(MedicalSpecialty.DERMATOLOGY.name());
            result.setProcedureType(ProcedureType.MINOR_SURGERY.name());
            result.setLikelyEncounterType("OUTPATIENT");
            result.setSuggestedInsuranceCategoryCode("CAT023");
            result.setMedicalMeaningAr("استئصال كيس دهني جلدي أو تدخل جلدي بسيط");
            result.setExplanationAr("إجراء جلدي جراحي بسيط يصنف غالباً كعيادات خارجية ولا يعد أوراماً.");
            
            if (s.contains("sebaceous cyst")) {
                result.getMatchedKeywords().add("sebaceous cyst");
            }
            if (s.contains("كيس دهني")) {
                result.getMatchedKeywords().add("كيس دهني");
            }
        }
        // Imaging
        else if (s.contains("mri") || s.contains("ct scan") || s.contains("ultrasound") || s.contains("x-ray")
            || s.contains("رنين") || s.contains("مقطعي") || s.contains("تصوير") || s.contains("موجات")) {
            
            result.setBodySystem(BodySystem.GENERAL.name());
            result.setMedicalSpecialty(MedicalSpecialty.RADIOLOGY.name());
            result.setProcedureType(ProcedureType.IMAGING.name());
            result.setLikelyEncounterType("OUTPATIENT");
            result.setSuggestedInsuranceCategoryCode("CAT024");
            result.setMedicalMeaningAr("تصوير طبي تشخيصي");
            result.setExplanationAr("يصنف تحت خدمات الأشعة والتشخيص.");
        }
        // Dental
        else if (s.contains("tooth extraction") || s.contains("filling") || s.contains("scaling")
            || s.contains("خلع سن") || s.contains("خلع") || s.contains("حشو") || s.contains("تنظيف أسنان")) {
            
            result.setBodySystem(BodySystem.DENTAL_ORAL.name());
            result.setMedicalSpecialty(MedicalSpecialty.DENTISTRY.name());
            result.setProcedureType(ProcedureType.DENTAL_ROUTINE.name());
            result.setLikelyEncounterType("OUTPATIENT");
            result.setSuggestedInsuranceCategoryCode("CAT028");
            result.setMedicalMeaningAr("علاج أسنان روتيني");
            result.setExplanationAr("يصنف تحت خدمات الأسنان الروتينية.");
        }
        else if (s.contains("implant") || s.contains("orthodontic") || s.contains("crown") 
            || s.contains("زراعة أسنان") || s.contains("تقويم") || s.contains("تركيب")) {
            
            result.setBodySystem(BodySystem.DENTAL_ORAL.name());
            result.setMedicalSpecialty(MedicalSpecialty.DENTISTRY.name());
            result.setProcedureType(ProcedureType.DENTAL_ADVANCED.name());
            result.setLikelyEncounterType("OUTPATIENT");
            result.setSuggestedInsuranceCategoryCode("CAT031");
            result.setMedicalMeaningAr("علاج أسنان متقدم (تركيب/زراعة)");
            result.setExplanationAr("يصنف تحت خدمات الأسنان المتقدمة.");
        }
        // Eye
        else if (s.contains("cataract surgery") || s.contains("مياه بيضاء")) {
            result.setBodySystem(BodySystem.EYE.name());
            result.setMedicalSpecialty(MedicalSpecialty.OPHTHALMOLOGY.name());
            result.setProcedureType(ProcedureType.MAJOR_SURGERY.name());
            result.setLikelyEncounterType("INPATIENT");
            result.setSuggestedInsuranceCategoryCode("SUB-INPAT-GENERAL");
            result.setRequiresReview(true);
            result.getReviewReasons().add("Cataract surgery needs manual verification of coverage.");
            result.setMedicalMeaningAr("عملية إزالة المياه البيضاء");
            result.setExplanationAr("عملية عيون تحتاج مراجعة للتأكد من شروط وثيقة التغطية الخاصة بالعمليات.");
        }
        // General checks for Review Required
        if (s.contains("plastic") || s.contains("تجميل")) {
            result.setRequiresReview(true);
            result.getReviewReasons().add("Plastic surgery usually excluded.");
        }
        if (s.contains("ivf") || s.contains("infertility") || s.contains("عقم") || s.contains("أطفال أنابيب")) {
            result.setRequiresReview(true);
            result.getReviewReasons().add("Infertility treatments require special coverage check.");
        }
        if (s.contains("anesthesia") || (s.contains("تخدير") && !s.contains("عملية"))) {
            result.setRequiresReview(true);
            result.getReviewReasons().add("Anesthesia billed standalone needs review.");
        }
        if (s.contains("transplant") || s.contains("زرع أعضاء")) {
            result.setSuggestedInsuranceCategoryCode("CAT013");
            result.setRequiresReview(true);
            result.getReviewReasons().add("Organ transplant requires pre-approval and special checking.");
        }
    }

    private void applyRuleToResult(MedicalSemanticRule rule, MedicalClassificationResultDto result) {
        if (rule.getBodySystem() != null) result.setBodySystem(rule.getBodySystem().name());
        if (rule.getMedicalSpecialty() != null) result.setMedicalSpecialty(rule.getMedicalSpecialty().name());
        if (rule.getProcedureType() != null) result.setProcedureType(rule.getProcedureType().name());
        if (rule.getProcedureComplexity() != null) result.setProcedureComplexity(rule.getProcedureComplexity().name());
        if (rule.getLikelyEncounterType() != null) result.setLikelyEncounterType(rule.getLikelyEncounterType().name());
        
        result.setSuggestedInsuranceCategoryCode(rule.getSuggestedCategoryCode());
        result.setRequiresReview(rule.getRequiresReview());
        if (rule.getRequiresReview() && rule.getReviewReason() != null) {
            result.getReviewReasons().add(rule.getReviewReason());
        }
        result.setClassificationSource("RULE_ENGINE");
        result.getMatchedKeywords().add(rule.getKeywordPattern());
        
        // Calculate a base confidence boost from rule
        if (rule.getConfidenceBoost() != null) {
            result.setConfidenceScore(rule.getConfidenceBoost());
        }
    }

    private void callBioBertModel(String normalized, MedicalClassificationResultDto result) {
        try {
            String bioBertUrl = systemSettingsService.getBiobertApiUrl();
            if (bioBertUrl == null || bioBertUrl.isEmpty()) {
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> body = new HashMap<>();
            body.put("text", normalized);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
            
            // Assuming the python service returns { "cpt": "...", "confidence": 0.95, "category": "CAT023", "medicalMeaning": "..." }
            ResponseEntity<Map> response = restTemplate.postForEntity(bioBertUrl, requestEntity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> respBody = response.getBody();
                
                if (respBody.containsKey("category")) {
                    result.setSuggestedInsuranceCategoryCode((String) respBody.get("category"));
                    result.setClassificationSource("BIOBERT_MODEL");
                    
                    if (respBody.containsKey("confidence")) {
                        Object confObj = respBody.get("confidence");
                        double conf = (confObj instanceof Number) ? ((Number) confObj).doubleValue() : 0.85;
                        result.setConfidenceScore(conf);
                        
                        if (conf < 0.80) {
                            result.setRequiresReview(true);
                            result.getReviewReasons().add("BioBERT confidence is below 80%. Manual review required.");
                        }
                    }
                    
                    if (respBody.containsKey("medicalMeaning")) {
                        result.setMedicalMeaningAr((String) respBody.get("medicalMeaning"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to call BioBERT external model: {}", e.getMessage());
            result.getWarnings().add("BioBERT Model unreachable: " + e.getMessage());
        }
    }

    private double calculatePatternMatchScore(String normalized, String pattern, boolean isRegex) {
        if (pattern == null || pattern.isEmpty()) return 0.0;
        if (isRegex) {
            return normalized.matches(pattern) ? 1.0 : 0.0;
        }
        
        String[] keywords = pattern.split("\\|");
        double maxScore = 0.0;
        
        for (String kw : keywords) {
            kw = kw.trim().toLowerCase();
            if (kw.isEmpty()) continue;
            
            // Exact contain gives 1.0
            if (normalized.contains(kw)) {
                return 1.0; 
            }
            
            // Fuzzy sliding window match
            String[] normWords = normalized.split("\\s+");
            String[] kwWords = kw.split("\\s+");
            int kwLength = kwWords.length;
            
            if (kwLength > 0 && kwLength <= normWords.length) {
                for (int i = 0; i <= normWords.length - kwLength; i++) {
                    StringBuilder window = new StringBuilder();
                    for (int j = 0; j < kwLength; j++) {
                        if (j > 0) window.append(" ");
                        window.append(normWords[i+j]);
                    }
                    double windowScore = jaroWinkler.apply(window.toString(), kw);
                    if (windowScore > maxScore) {
                        maxScore = windowScore;
                    }
                }
            } else if (normWords.length < kwLength) {
                 double score = jaroWinkler.apply(normalized, kw);
                 if (score > maxScore) maxScore = score;
            }
        }
        return maxScore;
    }

    private void calculateConfidence(MedicalClassificationResultDto result, String normalized) {
        Double score = result.getConfidenceScore();
        if (score == null) {
            score = 0.0;
        }

        // If heuristics matched something
        if (result.getSuggestedInsuranceCategoryCode() != null && result.getClassificationSource() == null) {
            score = 0.7; // Base heuristic score
            result.setClassificationSource("HEURISTICS");
        }

        // Boost logic
        if (result.getRequiresReview()) {
            score = Math.min(score, 0.6); // Cannot be high confidence if review is forced
        }

        // Penalty logic
        if (normalized.length() < 5) {
            score -= 0.3;
            result.getWarnings().add("Service name is very short, confidence reduced.");
        }
        if (normalized.contains("misc") || normalized.contains("other") || normalized.contains("أخرى")) {
            score -= 0.4;
            result.getWarnings().add("Miscellaneous keyword detected.");
        }

        score = Math.max(0.0, Math.min(1.0, score));
        result.setConfidenceScore(score);

        if (score >= 0.9) result.setConfidenceLevel("HIGH");
        else if (score >= 0.7) result.setConfidenceLevel("MEDIUM");
        else if (score >= 0.5) result.setConfidenceLevel("LOW");
        else result.setConfidenceLevel("UNKNOWN");
    }

    private MedicalClassificationResultDto createUnknownResult(MedicalClassificationRequestDto request) {
        MedicalClassificationResultDto res = new MedicalClassificationResultDto();
        res.setOriginalServiceName(request.getServiceName());
        res.setConfidenceScore(0.0);
        res.setConfidenceLevel("UNKNOWN");
        res.setRequiresReview(true);
        res.setClassificationSource("UNKNOWN");
        return res;
    }

    private String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String detectLanguage(String s) {
        return s.matches(".*[\\u0600-\\u06FF].*") ? "AR" : "EN";
    }
}
