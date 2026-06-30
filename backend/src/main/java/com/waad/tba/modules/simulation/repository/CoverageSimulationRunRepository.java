package com.waad.tba.modules.simulation.repository;

import com.waad.tba.modules.simulation.entity.CoverageSimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoverageSimulationRunRepository extends JpaRepository<CoverageSimulationRun, String> {
}
