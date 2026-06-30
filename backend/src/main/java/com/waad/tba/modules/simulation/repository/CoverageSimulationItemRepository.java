package com.waad.tba.modules.simulation.repository;

import com.waad.tba.modules.simulation.entity.CoverageSimulationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoverageSimulationItemRepository extends JpaRepository<CoverageSimulationItem, Long> {
    List<CoverageSimulationItem> findBySimulationRunId(String simulationRunId);
}
