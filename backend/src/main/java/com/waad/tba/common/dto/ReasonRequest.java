package com.waad.tba.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Carries the justification for a state-changing operation in the request
 * body rather than the query string.
 *
 * These reasons are free text written by staff about a specific person: why a
 * membership was ended, why a pre-authorization was cancelled, why a contract
 * was suspended. In a health-insurance system that routinely names a
 * condition or a personal circumstance.
 *
 * A query string is the wrong place for it. It is written verbatim into the
 * servlet container's access log and every reverse proxy in front of it, kept
 * in browser history, and attached to outbound Referer headers -- all of them
 * outside the retention rules and access controls that govern the audit trail
 * this same reason is deliberately stored in.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReasonRequest {

    private String reason;

    /** Null-safe accessor for the common "body may be absent" case. */
    public static String reasonOf(ReasonRequest request) {
        return request == null ? null : request.getReason();
    }
}
