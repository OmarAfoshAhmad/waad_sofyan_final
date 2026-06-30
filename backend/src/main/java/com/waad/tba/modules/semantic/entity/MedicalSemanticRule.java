package com.waad.tba.modules.semantic.entity;

import com.waad.tba.modules.semantic.entity.enums.BodySystem;
import com.waad.tba.modules.semantic.entity.enums.MedicalSpecialty;
import com.waad.tba.modules.semantic.entity.enums.ProcedureComplexity;
import com.waad.tba.modules.semantic.entity.enums.ProcedureType;
import com.waad.tba.modules.semantic.entity.enums.SemanticEncounterType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "medical_semantic_rules")
@Data
public class MedicalSemanticRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(nullable = false)
    private String language;

    @Column(name = "keyword_pattern", nullable = false, length = 1000)
    private String keywordPattern;

    @Column(name = "regex_enabled")
    private Boolean regexEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_system")
    private BodySystem bodySystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "medical_specialty")
    private MedicalSpecialty medicalSpecialty;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_type")
    private ProcedureType procedureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_complexity")
    private ProcedureComplexity procedureComplexity;

    @Enumerated(EnumType.STRING)
    @Column(name = "likely_encounter_type")
    private SemanticEncounterType likelyEncounterType;

    @Column(name = "suggested_category_code")
    private String suggestedCategoryCode;

    @Column(name = "confidence_boost")
    private Double confidenceBoost;

    @Column(name = "requires_review")
    private Boolean requiresReview = false;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    private Integer priority = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
