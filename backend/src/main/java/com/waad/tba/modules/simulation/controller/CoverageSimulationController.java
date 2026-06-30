package com.waad.tba.modules.simulation.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.simulation.SimulationItemRequestDto;
import com.waad.tba.modules.simulation.dto.CoverageSimulationRequestDto;
import com.waad.tba.modules.simulation.dto.CoverageSimulationResultDto;
import com.waad.tba.modules.simulation.service.CoverageSimulationService;
import com.waad.tba.modules.simulation.service.SimulationComparisonService;
import com.waad.tba.modules.simulation.service.SimulationExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulation/coverage")
@RequiredArgsConstructor
public class CoverageSimulationController {

    private final CoverageSimulationService simulationService;
    private final SimulationExportService exportService;
    private final SimulationComparisonService comparisonService;

    @PostMapping("/contract")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CoverageSimulationResultDto>> simulateCoverage(
            @RequestBody CoverageSimulationRequestDto request) {
        
        // Ensure dryRun and saveSnapshot are true as per Phase 1 requirements
        request.setDryRun(true);
        request.setSaveSnapshot(true);
        
        CoverageSimulationResultDto result = simulationService.simulateCoverage(request);
        return ResponseEntity.ok(ApiResponse.success("تم إكمال المحاكاة بنجاح", result));
    }

    @PostMapping("/raw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CoverageSimulationResultDto>> simulateRawCoverage(
            @RequestParam Long policyId,
            @RequestParam(required = false, defaultValue = "ANY") String encounterType,
            @RequestBody List<SimulationItemRequestDto> items) {
        
        CoverageSimulationRequestDto request = CoverageSimulationRequestDto.builder()
                .policyId(policyId)
                .encounterType(encounterType)
                .dryRun(true)
                .saveSnapshot(true)
                .build();
                
        CoverageSimulationResultDto result = simulationService.simulateRawCoverage(request, items);
        return ResponseEntity.ok(ApiResponse.success("تم إكمال المحاكاة بنجاح", result));
    }

    @GetMapping("/export/{simulationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportSimulation(@PathVariable String simulationId) {
        byte[] excelData = exportService.exportSimulationToExcel(simulationId);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"simulation_" + simulationId + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @GetMapping("/compare")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareSimulations(
            @RequestParam String baseId,
            @RequestParam String compareId) {
        
        Map<String, Object> comparison = comparisonService.compareSnapshots(baseId, compareId);
        return ResponseEntity.ok(ApiResponse.success("تم جلب المقارنة بنجاح", comparison));
    }
}
