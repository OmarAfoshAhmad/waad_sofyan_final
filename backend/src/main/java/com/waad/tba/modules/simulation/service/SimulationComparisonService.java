package com.waad.tba.modules.simulation.service;

import com.waad.tba.modules.simulation.entity.CoverageSimulationRun;
import com.waad.tba.modules.simulation.repository.CoverageSimulationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SimulationComparisonService {

    private final CoverageSimulationRunRepository runRepository;

    public Map<String, Object> compareSnapshots(String baseSnapshotId, String compareSnapshotId) {
        CoverageSimulationRun baseRun = runRepository.findById(baseSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Base snapshot not found"));
        
        CoverageSimulationRun compareRun = runRepository.findById(compareSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Compare snapshot not found"));

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("baseSimulationId", baseRun.getId());
        comparison.put("compareSimulationId", compareRun.getId());
        
        Map<String, Integer> delta = new HashMap<>();
        delta.put("coveredCountDiff", compareRun.getCoveredCount() - baseRun.getCoveredCount());
        delta.put("excludedCountDiff", compareRun.getExcludedCount() - baseRun.getExcludedCount());
        delta.put("needsReviewCountDiff", compareRun.getNeedsReviewCount() - baseRun.getNeedsReviewCount());
        delta.put("invalidCategoryCountDiff", compareRun.getInvalidCategoryCount() - baseRun.getInvalidCategoryCount());
        delta.put("zeroPriceCountDiff", compareRun.getZeroPriceCount() - baseRun.getZeroPriceCount());

        comparison.put("metricsDelta", delta);
        comparison.put("baseSummary", baseRun.getSummaryJson());
        comparison.put("compareSummary", compareRun.getSummaryJson());

        return comparison;
    }
}
