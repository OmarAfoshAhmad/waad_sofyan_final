package com.waad.tba.modules.claim.draft.repository;

import com.waad.tba.modules.claim.draft.entity.ClaimDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimDraftRepository extends JpaRepository<ClaimDraft, Long> {
    Optional<ClaimDraft> findByUserIdAndBatchId(Long userId, Long batchId);

    void deleteByUserIdAndBatchId(Long userId, Long batchId);
}
