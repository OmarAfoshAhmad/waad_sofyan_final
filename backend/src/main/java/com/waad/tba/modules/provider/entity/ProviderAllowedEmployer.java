package com.waad.tba.modules.provider.entity;

import com.waad.tba.modules.employer.entity.Employer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * Provider-Partner Isolation - Junction Table
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * TPA MODEL: Provider has master contract with TPA (Waad Insurance).
 * This table defines WHICH specific employers/partners the provider can serve.
 * 
 * ARCHITECTURAL RULES:
 * 1. Provider with allowAllEmployers=true → Can access all partners (global
 * network)
 * 2. Provider with allowAllEmployers=false → Only partners in this table
 * 3. PROVIDER users NEVER see global employer selectors
 * 4. All Provider queries are scoped by providerId + allowed employer IDs
 * 
 * SECURITY:
 * - Backend enforces at service/repository layer
 * - Frontend hides UI elements for unauthorized partners
 * - No parameter tampering allowed
 * 
 * @since Phase 5.5 - Provider-Partner Isolation
 * @see Provider#allowAllEmployers
 */
@Entity
@Table(name = "provider_allowed_employers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_employer", columnNames = { "provider_id", "employer_id" })
}, indexes = {
        @Index(name = "idx_pae_provider", columnList = "provider_id"),
        @Index(name = "idx_pae_employer", columnList = "employer_id"),
        @Index(name = "idx_pae_active", columnList = "active")
})
@Getter
@Setter
@ToString(exclude = { "provider", "employer" })
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAllowedEmployer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Optional note about partnership terms or restrictions
     */
    @Column(name = "notes", length = 500)
    private String notes;
}
