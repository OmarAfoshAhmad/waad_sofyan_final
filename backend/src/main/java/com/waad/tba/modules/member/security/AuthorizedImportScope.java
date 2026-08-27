package com.waad.tba.modules.member.security;

import java.util.Set;

/**
 * Proof that an import was authorised, carrying the employers every row must
 * belong to.
 *
 * Like AuthorizedMemberScope, it exists only on success, so an importer
 * cannot begin writing rows without having been granted the operation. It
 * also carries whether the caller may end the absentees, because that is a
 * separate grant from importing: removing people from coverage is a status
 * decision, not a data-entry one.
 */
public final class AuthorizedImportScope {

    private final MemberAccessScope scope;
    private final boolean mayClearAbsentMembers;

    AuthorizedImportScope(MemberAccessScope scope, boolean mayClearAbsentMembers) {
        this.scope = scope;
        this.mayClearAbsentMembers = mayClearAbsentMembers;
    }

    public boolean isGlobal() {
        return scope.isGlobal();
    }

    public Set<Long> employerIds() {
        return scope.employerIds();
    }

    public boolean covers(Long employerId) {
        return scope.covers(employerId);
    }

    public boolean mayClearAbsentMembers() {
        return mayClearAbsentMembers;
    }
}
