package com.waad.tba.modules.simulation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class CoverageSimulationRequestDto {
    private Long contractId;
    private Long policyId;
    private LocalDate effectiveDate;
    private String encounterType;
    private Boolean includeInactiveServices;
    private Boolean includeZeroPrice;
    private Boolean onlyProblems;
    private Boolean dryRun;
    private Boolean saveSnapshot;
}
