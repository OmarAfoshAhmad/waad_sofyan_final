package com.waad.tba.modules.preauthorization.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Explicit tenant scope for pre-authorization reads and aggregates. */
public record PreAuthAccessScope(Kind kind, Set<Long> ids, String reason) {
    public enum Kind { GLOBAL, PROVIDERS, EMPLOYERS, DENIED }

    public static PreAuthAccessScope global() {
        return new PreAuthAccessScope(Kind.GLOBAL, Set.of(), null);
    }

    public static PreAuthAccessScope providers(Set<Long> providerIds) {
        return restricted(Kind.PROVIDERS, providerIds, "لا يوجد مقدم خدمة مرتبط أو مسند للمستخدم");
    }

    public static PreAuthAccessScope employers(Set<Long> employerIds) {
        return restricted(Kind.EMPLOYERS, employerIds, "لا توجد جهة عمل مرتبطة بالمستخدم");
    }

    public static PreAuthAccessScope denied(String reason) {
        return new PreAuthAccessScope(Kind.DENIED, Set.of(), reason);
    }

    private static PreAuthAccessScope restricted(Kind kind, Set<Long> ids, String emptyReason) {
        if (ids == null || ids.isEmpty()) return denied(emptyReason);
        return new PreAuthAccessScope(kind,
                Collections.unmodifiableSet(new LinkedHashSet<>(ids)), null);
    }

    public boolean isGlobal() {
        return kind == Kind.GLOBAL;
    }

    public boolean isDenied() {
        return kind == Kind.DENIED;
    }
}
