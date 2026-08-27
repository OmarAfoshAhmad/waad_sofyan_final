package com.waad.tba.modules.preauthorization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.waad.tba.modules.preauthorization.entity.PreauthLineLimitSnapshot;

public interface PreauthLineLimitSnapshotRepository extends JpaRepository<PreauthLineLimitSnapshot, Long> {

    List<PreauthLineLimitSnapshot> findByLineSnapshotId(Long lineSnapshotId);
}
