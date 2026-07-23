package com.waad.tba.modules.claim.event;

import com.waad.tba.modules.claim.dto.ClaimApproveDto;

/** Immutable command emitted after an approval request is durably committed. */
public record ClaimApprovalRequestedEvent(
        Long claimId,
        ClaimApproveDto request,
        Long actorId,
        String actorUsername,
        String actorType) {
}
