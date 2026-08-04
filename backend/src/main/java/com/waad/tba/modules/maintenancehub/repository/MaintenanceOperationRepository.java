package com.waad.tba.modules.maintenancehub.repository;

import com.waad.tba.modules.maintenancehub.entity.MaintenanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceOperationRepository extends JpaRepository<MaintenanceOperation, Long> {
    List<MaintenanceOperation> findByIssueIdOrderByPerformedAtDesc(Long issueId);
}
