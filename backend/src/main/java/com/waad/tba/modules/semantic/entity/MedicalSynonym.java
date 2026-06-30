package com.waad.tba.modules.semantic.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "medical_synonyms")
@Data
public class MedicalSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String term;

    @Column(name = "normalized_term", nullable = false)
    private String normalizedTerm;

    @Column(nullable = false)
    private String language;

    @Column(name = "term_type")
    private String termType;

    @Column(name = "mapped_concept")
    private String mappedConcept;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
