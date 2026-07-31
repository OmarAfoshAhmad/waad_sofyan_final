package com.waad.tba.modules.medicaldictionary.entity;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "medical_dictionary_synonyms", uniqueConstraints = {
        @UniqueConstraint(name = "uk_med_dict_synonym_normalized", columnNames = "normalized_synonym")
}, indexes = {
        @Index(name = "idx_med_dict_synonym_entry", columnList = "entry_id"),
        @Index(name = "idx_med_dict_synonym_normalized", columnList = "normalized_synonym"),
        @Index(name = "idx_med_dict_synonym_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalDictionarySynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private MedicalDictionaryEntry entry;

    @Column(name = "synonym", nullable = false, length = 300)
    private String synonym;

    @Column(name = "normalized_synonym", nullable = false, length = 300)
    private String normalizedSynonym;

    @Enumerated(EnumType.STRING)
    @Column(name = "synonym_type", nullable = false, length = 30)
    @Builder.Default
    private DictionarySynonymType synonymType = DictionarySynonymType.COMMON;

    @Column(name = "language", nullable = false, length = 10)
    @Builder.Default
    private String language = "ar";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;

    @Column(name = "lifecycle_status", nullable = false, length = 30)
    @Builder.Default
    private String lifecycleStatus = "REVIEWER_APPROVED";

    @Column(name = "learned_from_source", length = 50)
    private String learnedFromSource;

    @Column(name = "source_reference", length = 200)
    private String sourceReference;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "disabled_by")
    private Long disabledBy;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "governance_note", length = 1000)
    private String governanceNote;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
