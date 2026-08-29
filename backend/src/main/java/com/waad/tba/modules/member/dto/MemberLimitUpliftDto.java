package com.waad.tba.modules.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.waad.tba.modules.member.entity.MemberGeneralLimitUplift;

/**
 * One exceptional ceiling increase, as the screen that reviews them needs it.
 *
 * {@code state} is derived rather than stored, for the same reason the import
 * history derives "interrupted": a row does not stop being in force by having
 * something written to it, it stops by the calendar moving. Storing a state
 * would need something to keep it true.
 */
public record MemberLimitUpliftDto(
        Long id,
        Long memberId,
        BigDecimal amount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String source,
        Long requestedByEmployerId,
        String reason,
        String grantedByUsername,
        LocalDateTime createdAt,
        LocalDateTime revokedAt,
        String revokedByUsername,
        String revokedReason,
        State state) {

    public enum State {
        /** Counts towards the ceiling on the date this was read for. */
        IN_FORCE,
        /** Granted, but its window has not opened yet. */
        SCHEDULED,
        /** Its window closed on its own end date. */
        EXPIRED,
        /** Ended early by a person, who is named along with why. */
        REVOKED
    }

    public static MemberLimitUpliftDto from(MemberGeneralLimitUplift uplift, LocalDate asOfDate) {
        return new MemberLimitUpliftDto(
                uplift.getId(),
                uplift.getMemberId(),
                uplift.getAmount(),
                uplift.getEffectiveFrom(),
                uplift.getEffectiveTo(),
                uplift.getSource() == null ? null : uplift.getSource().name(),
                uplift.getRequestedByEmployerId(),
                uplift.getReason(),
                uplift.getGrantedByUsername(),
                uplift.getCreatedAt(),
                uplift.getRevokedAt(),
                uplift.getRevokedByUsername(),
                uplift.getRevokedReason(),
                stateOf(uplift, asOfDate));
    }

    private static State stateOf(MemberGeneralLimitUplift uplift, LocalDate asOfDate) {
        if (uplift.isInForceOn(asOfDate)) {
            return State.IN_FORCE;
        }
        if (asOfDate.isBefore(uplift.getEffectiveFrom())) {
            return State.SCHEDULED;
        }
        // Past its window. Revoked and expired both end it; only one of them
        // was a decision, and the reader needs to tell them apart.
        return uplift.getRevokedAt() != null ? State.REVOKED : State.EXPIRED;
    }
}
