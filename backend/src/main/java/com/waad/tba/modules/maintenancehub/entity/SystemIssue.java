package com.waad.tba.modules.maintenancehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per distinct problem instance, deduplicated by {@link #fingerprint}. Every
 * maintenance-center detector (existing and future) registers findings here through
 * {@code IssueRegistry} instead of running in isolation — this is what makes "how many
 * problems are open, who owns them, has this come back" answerable across problem types.
 */
@Entity
@Table(name = "system_issues")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_type", nullable = false, length = 60)
    private String issueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IssueStatus status = IssueStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IssueSeverity severity = IssueSeverity.MEDIUM;

    @Column(nullable = false, length = 200)
    private String fingerprint;

    @Column(name = "employer_id")
    private Long employerId;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "title_ar", nullable = false, length = 300)
    private String titleAr;

    @Column(name = "description_ar", length = 2000)
    private String descriptionAr;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "detected_by_rule", length = 120)
    private String detectedByRule;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private Integer occurrenceCount = 1;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "assigned_to", length = 150)
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;
}
