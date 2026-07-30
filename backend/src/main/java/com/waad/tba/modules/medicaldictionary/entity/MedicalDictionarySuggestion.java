package com.waad.tba.modules.medicaldictionary.entity;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionSource;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "medical_dictionary_suggestions", indexes = {
        @Index(name = "idx_med_dict_suggestion_normalized", columnList = "normalized_original_text"),
        @Index(name = "idx_med_dict_suggestion_status", columnList = "status"),
        @Index(name = "idx_med_dict_suggestion_source", columnList = "source")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalDictionarySuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_text", nullable = false, length = 500)
    private String originalText;

    @Column(name = "normalized_original_text", nullable = false, length = 500)
    private String normalizedOriginalText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_entry_id")
    private MedicalDictionaryEntry suggestedEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_category_id")
    private MedicalCategory suggestedCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private DictionarySuggestionSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DictionarySuggestionStatus status = DictionarySuggestionStatus.PENDING;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "source_reference", length = 200)
    private String sourceReference;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

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
