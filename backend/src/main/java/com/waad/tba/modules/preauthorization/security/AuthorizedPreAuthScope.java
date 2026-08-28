package com.waad.tba.modules.preauthorization.security;

import java.util.Set;

/** Proof that PREAUTH_VIEW was granted, carrying the mandatory query scope. */
public final class AuthorizedPreAuthScope {
    private final PreAuthAccessScope scope;

    AuthorizedPreAuthScope(PreAuthAccessScope scope) {
        this.scope = scope;
    }

    public PreAuthAccessScope.Kind kind() {
        return scope.kind();
    }

    public Set<Long> ids() {
        return scope.ids();
    }

    public boolean isGlobal() {
        return scope.isGlobal();
    }
}
