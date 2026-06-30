package com.waad.tba.modules.semantic.controller;

import com.waad.tba.modules.semantic.dto.MedicalClassificationRequestDto;
import com.waad.tba.modules.semantic.dto.MedicalClassificationResultDto;
import com.waad.tba.modules.semantic.entity.MedicalSemanticRule;
import com.waad.tba.modules.semantic.repository.MedicalSemanticRuleRepository;
import com.waad.tba.modules.semantic.service.MedicalSemanticClassificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/medical-classification")
@RequiredArgsConstructor
public class MedicalSemanticController {

    private final MedicalSemanticClassificationService classificationService;
    private final MedicalSemanticRuleRepository ruleRepository;

    @PostMapping("/classify")
    public ResponseEntity<MedicalClassificationResultDto> classify(@RequestBody MedicalClassificationRequestDto request) {
        return ResponseEntity.ok(classificationService.classifyService(request));
    }

    @PostMapping("/classify-batch")
    public ResponseEntity<List<MedicalClassificationResultDto>> classifyBatch(@RequestBody List<MedicalClassificationRequestDto> requests) {
        List<MedicalClassificationResultDto> results = requests.stream()
                .map(classificationService::classifyService)
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<MedicalSemanticRule>> getRules() {
        return ResponseEntity.ok(ruleRepository.findAll());
    }

    @PostMapping("/rules")
    public ResponseEntity<MedicalSemanticRule> createRule(@RequestBody MedicalSemanticRule rule) {
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<MedicalSemanticRule> updateRule(@PathVariable Long id, @RequestBody MedicalSemanticRule ruleDetails) {
        MedicalSemanticRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        
        rule.setRuleName(ruleDetails.getRuleName());
        rule.setKeywordPattern(ruleDetails.getKeywordPattern());
        rule.setLanguage(ruleDetails.getLanguage());
        rule.setBodySystem(ruleDetails.getBodySystem());
        rule.setMedicalSpecialty(ruleDetails.getMedicalSpecialty());
        rule.setProcedureType(ruleDetails.getProcedureType());
        rule.setProcedureComplexity(ruleDetails.getProcedureComplexity());
        rule.setLikelyEncounterType(ruleDetails.getLikelyEncounterType());
        rule.setSuggestedCategoryCode(ruleDetails.getSuggestedCategoryCode());
        rule.setRequiresReview(ruleDetails.getRequiresReview());
        rule.setReviewReason(ruleDetails.getReviewReason());
        rule.setPriority(ruleDetails.getPriority());
        rule.setIsActive(ruleDetails.getIsActive());

        return ResponseEntity.ok(ruleRepository.save(rule));
    }
}
