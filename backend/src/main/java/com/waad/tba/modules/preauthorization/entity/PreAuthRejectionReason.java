package com.waad.tba.modules.preauthorization.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Dynamic list of Rejection Reasons for Pre-Authorizations.
 * Configurable by Insurance Admin.
 */
@Entity
@Table(name = "pre_auth_rejection_reasons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthRejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "arabic_label", nullable = false, length = 200)
    private String arabicLabel;

    @Column(name = "english_label", length = 200)
    private String englishLabel;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;
}
