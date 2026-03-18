package com.waad.tba.modules.medicaltaxonomy.entity;

import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Medical Category Entity (Reference Data)
 *
 * Hierarchical classification of medical services.
 * Scope: Pure reference data — no coverage, claim, or provider logic here.
 */
@Entity
@Table(name = "medical_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique business identifier (immutable). E.g. "CAT-OP", "CAT-IP-NURSE" */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Primary display name (Arabic). */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Arabic display name (bilingual support). */
    @Column(name = "name_ar", length = 200)
    private String nameAr;

    /** English display name (bilingual support). */
    @Column(name = "name_en", length = 200)
    private String nameEn;

    /** Parent category ID — NULL means root category. */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * Multi-root association for cross-context categories.
     * Allows a category (e.g. Lab) to belong to multiple roots (OP, IP, etc.)
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "medical_category_roots", joinColumns = @JoinColumn(name = "category_id"), inverseJoinColumns = @JoinColumn(name = "root_id"))
    @Builder.Default
    private Set<MedicalCategory> roots = new HashSet<>();

    /** Clinical context — controls where this category is applicable. */
    @Enumerated(EnumType.STRING)
    @Column(name = "context", length = 20, nullable = false)
    @Builder.Default
    private CategoryContext context = CategoryContext.ANY;

    /** Admin-configured coverage percentage (0–100). NULL = not yet set. */
    @Column(name = "coverage_percent", precision = 5, scale = 2)
    private BigDecimal coveragePercent;

    // ── Soft-delete ──────────────────────────────────────────────────────────

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    // ── Status ───────────────────────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // ── Audit ────────────────────────────────────────────────────────────────

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
