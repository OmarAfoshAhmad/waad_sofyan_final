package com.waad.tba.modules.medicalpackage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalPackageDTO {
    
    private Long id;
    private String code;
    private String name;
    private String description;
    private Set<Long> serviceIds;
    private BigDecimal totalCoverageLimit;
    private Boolean active;
}
