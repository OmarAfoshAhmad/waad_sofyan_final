package com.waad.tba.modules.benefitpolicy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "benefit_definitions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BenefitDefinition {
    public enum BenefitType { MEDICAL_SERVICE, SPECIAL_EXPENSE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(name = "name_ar", nullable = false, length = 255) private String nameAr;
    @Enumerated(EnumType.STRING) @Column(name = "benefit_type", nullable = false, length = 20)
    private BenefitType benefitType;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
