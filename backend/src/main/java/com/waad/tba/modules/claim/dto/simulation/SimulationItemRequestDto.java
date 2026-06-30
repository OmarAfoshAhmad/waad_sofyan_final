package com.waad.tba.modules.claim.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationItemRequestDto {
    private String serviceName;
    private String serviceCode;
    private BigDecimal contractPrice;
    private String mainCategory;
    private String subCategory;
}
