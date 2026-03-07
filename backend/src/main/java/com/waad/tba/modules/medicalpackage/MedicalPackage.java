package com.waad.tba.modules.medicalpackage;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "medical_packages", uniqueConstraints = {
    @UniqueConstraint(columnNames = "code", name = "uk_medical_package_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MedicalPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Package name (unified - Arabic-only system)
     */
    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * Many-to-Many relationship with MedicalService
     * A package can include multiple services, and a service can be in multiple packages
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "medical_package_services",
        joinColumns = @JoinColumn(name = "package_id"),
        inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    @Builder.Default
    private Set<MedicalService> services = new HashSet<>();

    /**
     * Total coverage limit for this package (optional)
     * Sum of all included services coverage limits
     */
    @Column()
    private java.math.BigDecimal totalCoverageLimit;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Transient field for services count
    @Transient
    private Integer servicesCount;

    @PostLoad
    private void calculateServicesCount() {
        if (services != null) {
            this.servicesCount = services.size();
        }
    }
}
