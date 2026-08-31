package com.waad.tba.modules.claimcontext.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "claim_context_source_aliases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimContextSourceAlias {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_alias", nullable = false, length = 255) private String sourceAlias;
    @Column(name = "normalized_alias", nullable = false, length = 255) private String normalizedAlias;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_context_code", nullable = false) private ClaimContextDefinition claimContext;
    @Column(name = "medical_category_code", length = 100) private String medicalCategoryCode;
    @Column(name = "provider_id") private Long providerId;
    @Column(name = "requires_review", nullable = false) @Builder.Default private boolean requiresReview = false;
    @Column(nullable = false) @Builder.Default private boolean active = true;
}
