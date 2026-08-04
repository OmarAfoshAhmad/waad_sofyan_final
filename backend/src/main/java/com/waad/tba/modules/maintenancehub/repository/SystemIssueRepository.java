package com.waad.tba.modules.maintenancehub.repository;

import com.waad.tba.modules.maintenancehub.entity.IssueSeverity;
import com.waad.tba.modules.maintenancehub.entity.IssueStatus;
import com.waad.tba.modules.maintenancehub.entity.SystemIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SystemIssueRepository
        extends JpaRepository<SystemIssue, Long>, JpaSpecificationExecutor<SystemIssue> {

    /**
     * Locked lookup for the registry's read-modify-write on occurrence_count/status —
     * without this, two detectors racing on the same fingerprint could both read the
     * same occurrence_count and one increment would be lost.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SystemIssue i WHERE i.fingerprint = :fingerprint")
    Optional<SystemIssue> findByFingerprintForUpdate(@Param("fingerprint") String fingerprint);

    long countByStatus(IssueStatus status);

    long countByStatusAndSeverity(IssueStatus status, IssueSeverity severity);
}
