package com.waad.tba.modules.preauthorization.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Explicit tenant scope. It grants reach, never an operation. */
public record PreAuthAccessScope(Kind kind, Set<Long> providerIds, Set<Long> employerIds, String reason) {
    public enum Kind { GLOBAL, PROVIDERS, EMPLOYERS, DENIED }

    public static PreAuthAccessScope global() {
        return new PreAuthAccessScope(Kind.GLOBAL, Set.of(), Set.of(), null);
    }
    public static PreAuthAccessScope providers(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return denied("لا يوجد مقدم خدمة مرتبط أو مسند للمستخدم");
        return new PreAuthAccessScope(Kind.PROVIDERS, immutable(ids), Set.of(), null);
    }
    public static PreAuthAccessScope employers(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return denied("لا توجد جهة عمل مرتبطة بالمستخدم");
        return new PreAuthAccessScope(Kind.EMPLOYERS, Set.of(), immutable(ids), null);
    }
    public static PreAuthAccessScope denied(String reason) {
        return new PreAuthAccessScope(Kind.DENIED, Set.of(), Set.of(), reason);
    }
    private static Set<Long> immutable(Set<Long> ids) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }
    public Set<Long> ids() {
        return kind == Kind.PROVIDERS ? providerIds : kind == Kind.EMPLOYERS ? employerIds : Set.of();
    }
    public boolean isGlobal() { return kind == Kind.GLOBAL; }
    public boolean isDenied() { return kind == Kind.DENIED; }
    public boolean covers(Long providerId, Long employerId) {
        return switch (kind) {
            case GLOBAL -> true;
            case PROVIDERS -> providerId != null && providerIds.contains(providerId);
            case EMPLOYERS -> employerId != null && employerIds.contains(employerId);
            case DENIED -> false;
        };
    }
    public Optional<Long> singleProviderId() {
        return kind == Kind.PROVIDERS && providerIds.size() == 1
                ? Optional.of(providerIds.iterator().next()) : Optional.empty();
    }
}
