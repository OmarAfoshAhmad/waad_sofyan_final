package com.waad.tba.modules.preauthorization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.waad.tba.modules.preauthorization.entity.PreauthLineSnapshot;

public interface PreauthLineSnapshotRepository extends JpaRepository<PreauthLineSnapshot, Long> {

    List<PreauthLineSnapshot> findByDecisionSnapshotId(Long decisionSnapshotId);
}
