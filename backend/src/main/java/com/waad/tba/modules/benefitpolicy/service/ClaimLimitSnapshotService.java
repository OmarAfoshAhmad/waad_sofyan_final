package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.repository.ClaimLineLimitSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists claim_line_limit_snapshots ONLY -- WAAD-FIN-1.0's interpretive
 * record of every applicable limit's state at approval time. This class
 * owns no transaction of its own: {@link Propagation#MANDATORY} makes it
 * fail loudly if a caller ever invokes it outside the approval transaction
 * that ClaimApprovalOrchestrator (not yet built -- tracked separately) is
 * responsible for opening. That is deliberate: a snapshot written outside
 * the same transaction as the line's financial result, the bucket ledger
 * entries, and the status change to APPROVED could persist while the rest
 * of the approval rolls back, or vice versa -- exactly the kind of partial
 * financial state this whole redesign exists to make impossible.
 *
 * This service does not build snapshot rows itself: it has no opinion on
 * which limits are applicable, what their balances are, or which one binds.
 * Resolving that is EffectiveLimitResolver's job (not yet built); this class
 * only ever receives already-complete entities and writes them, unedited,
 * exactly once.
 */
@Service
@RequiredArgsConstructor
public class ClaimLimitSnapshotService {

    private final ClaimLineLimitSnapshotRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ClaimLineLimitSnapshot> saveAll(List<ClaimLineLimitSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        for (ClaimLineLimitSnapshot snapshot : snapshots) {
            if (snapshot.getId() != null) {
                throw new IllegalArgumentException(
                        "claim_line_limit_snapshots is append-only: snapshot for claimLineId="
                                + (snapshot.getClaimLine() != null ? snapshot.getClaimLine().getId() : null)
                                + " already has an id (" + snapshot.getId()
                                + ") -- a correction must be a NEW row under a new calculationVersion, "
                                + "never a re-save of an existing one.");
            }
        }
        return repository.saveAll(snapshots);
    }
}
