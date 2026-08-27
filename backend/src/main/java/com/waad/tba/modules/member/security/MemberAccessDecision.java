package com.waad.tba.modules.member.security;

/**
 * The answer a member access policy gives.
 *
 * Deliberately not a boolean. A bare false tells the caller nothing about
 * WHICH operation was refused or WHY, so each call site invents its own
 * message and its own status code -- and a refusal ends up indistinguishable
 * from an empty result. Carrying the operation and the reason makes every
 * refusal render as the same auditable 403, and makes the audit log say
 * something an investigator can act on.
 */
public record MemberAccessDecision(
        boolean allowed,
        MemberOperation operation,
        MemberAccessScope scope,
        String reason) {

    public static MemberAccessDecision allow(MemberOperation operation, MemberAccessScope scope) {
        return new MemberAccessDecision(true, operation, scope, null);
    }

    public static MemberAccessDecision deny(MemberOperation operation, MemberAccessScope scope,
            String reason) {
        return new MemberAccessDecision(false, operation, scope, reason);
    }

    /**
     * Converts a refusal into the exception the API layer turns into a 403.
     * Callers that forget to check the decision therefore fail closed rather
     * than proceeding on a value they ignored.
     */
    public MemberAccessDecision orThrow() {
        if (!allowed) {
            throw new MemberAccessDeniedException(operation, reason);
        }
        return this;
    }
}
