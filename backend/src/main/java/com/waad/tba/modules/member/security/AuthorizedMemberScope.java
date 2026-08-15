package com.waad.tba.modules.member.security;

import java.util.Set;

/**
 * Proof that an operation was authorised, carrying the scope the query must
 * be constrained to.
 *
 * It exists so that forgetting the check is not possible rather than merely
 * discouraged. A decision object can be produced and ignored -- Java lets any
 * return value drop on the floor -- but a filter cannot be built without the
 * employer ids, and the only way to obtain them is to ask for authorisation
 * and be granted it. The permission and the data the query needs are the same
 * object.
 *
 * Only the policy package constructs one.
 */
public final class AuthorizedMemberScope {

    private final MemberOperation operation;
    private final MemberAccessScope scope;

    AuthorizedMemberScope(MemberOperation operation, MemberAccessScope scope) {
        this.operation = operation;
        this.scope = scope;
    }

    public MemberOperation operation() {
        return operation;
    }

    /** True when the caller may see every employer; no filter is then applied. */
    public boolean isGlobal() {
        return scope.isGlobal();
    }

    /**
     * The employers a query must be restricted to. Empty only when global, so
     * a caller that ignores isGlobal() and filters on an empty set returns
     * nothing rather than everything -- the safe direction to be wrong in.
     */
    public Set<Long> employerIds() {
        return scope.employerIds();
    }

    public boolean covers(Long employerId) {
        return scope.covers(employerId);
    }
}
