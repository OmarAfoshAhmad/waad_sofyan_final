package com.waad.tba.modules.claimcontext.entity;

import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "claim_contexts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimContextDefinition {
    @Id @Column(length = 60) private String code;
    @Column(name = "name_ar", nullable = false, length = 120) private String nameAr;
    @Enumerated(EnumType.STRING)
    @Column(name = "base_encounter_type", nullable = false, length = 20)
    private EncounterType baseEncounterType;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    @Column(name = "display_order", nullable = false) @Builder.Default private int displayOrder = 100;
}
