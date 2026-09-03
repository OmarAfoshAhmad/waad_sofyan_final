package com.waad.tba.modules.provider.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Which standard service is suggested/auto-applied for a facility type.
 * This picks a service; it does not decide coverage -- the benefit policy
 * rule engine owns that, unchanged, keyed by the service's category.
 */
@Entity
@Table(name = "provider_service_defaults",
        uniqueConstraints = @UniqueConstraint(columnNames = { "provider_type", "service_code" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderServiceDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Provider.ProviderType providerType;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "auto_apply", nullable = false)
    @Builder.Default
    private boolean autoApply = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
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
