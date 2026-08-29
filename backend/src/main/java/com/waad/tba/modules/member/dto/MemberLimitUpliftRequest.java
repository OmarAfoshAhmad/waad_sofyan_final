package com.waad.tba.modules.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.waad.tba.modules.member.entity.MemberGeneralLimitUplift;

/**
 * Asking for one member's general ceiling to be raised.
 *
 * {@code reason} has no default and is checked non-blank in the service as
 * well as by the schema. An exception to a rule with no recorded reason cannot
 * be told apart from a mistake once the person who made it has moved on, and
 * this is the field the whole feature exists to capture.
 *
 * @param effectiveFrom null means today. Never in the past -- see
 *                      MemberLimitUpliftService.validate
 * @param effectiveTo   null means open-ended, to be closed by a revocation
 */
public record MemberLimitUpliftRequest(
        BigDecimal amount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        MemberGeneralLimitUplift.Source source,
        String reason) {
}
