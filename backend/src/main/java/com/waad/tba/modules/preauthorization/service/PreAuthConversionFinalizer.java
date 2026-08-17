package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes a pre-authorization conversion after its claim posts consumption.
 *
 * It coordinates the lifecycle and writes no ledger movement itself: the
 * release is delegated to PreAuthReservationLedgerService, the same writer
 * cancellation and expiry use. A third writer of reservation movements would
 * be a third place for the compensating-movement rules to be re-implemented
 * slightly differently, and ConsumptionLedgerWriteGateArchitectureTest fails
 * the build if this class ever reaches the ledger directly.
 *
 * MANDATORY propagation, deliberately: linking, releasing and the status
 * change belong to the claim's own transaction. In a transaction of its own,
 * a hold could be released and committed while the claim that justified it
 * rolled back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthConversionFinalizer {

    private final ClaimRepository claimRepository;
    private final PreAuthReservationLedgerService reservationLedgerService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeConvertedClaim(Long claimId, Long actorId) {
        Claim claim = claimRepository.findById(claimId).orElseThrow();
        PreAuthorization preauth = claim.getPreAuthorization();
        if (preauth == null) {
            return;
        }

        // What decides is whether a hold is still outstanding -- a fact about
        // the ledger -- not the pre-authorization's status.
        //
        // Reading the status instead would be fail-open. Any path that sets
        // USED without moving the ledger (a manual mark-used, a data repair)
        // would make this step skip a release that is still owed, and the
        // claim's own consumption would then post ON TOP of a live hold:
        // the member charged twice for one service, with no way back, because
        // release() refuses a pre-authorization that is already USED.
        //
        // It also gets the ordinary re-run right for the same reason. A
        // rejected claim can be re-approved and a soft-deleted one restored,
        // and both re-enter this path; the second time there is simply nothing
        // left holding, so there is nothing to do.
        if (reservationLedgerService.hasOutstandingReservation(preauth.getId())) {
            int released = reservationLedgerService.releaseOnConversion(
                    preauth.getId(), claimId, actorId == null ? "SYSTEM" : String.valueOf(actorId));
            log.info("Pre-authorization {} converted by claim {}: {} reservation(s) released",
                    preauth.getId(), claimId, released);
            return;
        }

        // Nothing held. Either this already converted, or the hold was
        // released by cancellation or expiry before the claim arrived -- a
        // late claim for a service that really was delivered while the
        // approval was live. The claim's own consumption is what counts in
        // both cases, and failing the whole approval here would protect no
        // money: there is nothing left to hand back.
        log.info("Pre-authorization {} has no outstanding reservation ({}); claim {} posts on its own",
                preauth.getId(), preauth.getStatus(), claimId);
    }
}
