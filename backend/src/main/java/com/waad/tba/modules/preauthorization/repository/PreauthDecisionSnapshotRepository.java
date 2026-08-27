package com.waad.tba.modules.preauthorization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.waad.tba.modules.preauthorization.entity.PreauthDecisionSnapshot;

public interface PreauthDecisionSnapshotRepository extends JpaRepository<PreauthDecisionSnapshot, Long> {

    Optional<PreauthDecisionSnapshot> findByIdempotencyKey(String idempotencyKey);

    /** The approval a release must act on: the latest decision for this pre-authorization. */
    Optional<PreauthDecisionSnapshot> findFirstByPreauthIdOrderByCalculationVersionDesc(Long preauthId);
}
