package com.waad.tba.modules.medicaldictionary.entity;

import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "medical_dictionary_entries", indexes = {
        @Index(name = "idx_med_dict_entry_normalized", columnList = "normalized_canonical_name"),
        @Index(name = "idx_med_dict_entry_category", columnList = "medical_category_id"),
        @Index(name = "idx_med_dict_entry_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalDictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_name", nullable = false, length = 300)
    private String canonicalName;

    @Column(name = "normalized_canonical_name", nullable = false, length = 300)
    private String normalizedCanonicalName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_category_id", nullable = false)
    private MedicalCategory medicalCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DictionaryEntryStatus status = DictionaryEntryStatus.DRAFT;

    @Column(name = "default_confidence", nullable = false)
    @Builder.Default
    private Integer defaultConfidence = 80;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<MedicalDictionarySynonym> synonyms = new HashSet<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
