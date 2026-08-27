package com.waad.tba.modules.member.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which members a caller may reach, stated explicitly.
 *
 * The system used to express this as a nullable employer id, where null meant
 * "no filter" -- which reads as "unscoped" and behaves as "every employer".
 * That is the wrong default for a multi-tenant record: an absent scope is not
 * a global one, and the two must not share a representation.
 *
 * So there are three states and no null: GLOBAL is granted deliberately,
 * EMPLOYERS names exactly which tenants, and DENIED carries the reason. A
 * caller whose scope cannot be established lands on DENIED, never on GLOBAL.
 */
public record MemberAccessScope(Kind kind, Set<Long> employerIds, String reason) {

    public enum Kind { GLOBAL, EMPLOYERS, DENIED }

    public static MemberAccessScope global() {
        return new MemberAccessScope(Kind.GLOBAL, Set.of(), null);
    }

    /**
     * An empty set is DENIED rather than EMPLOYERS-of-nothing. "Scoped to no
     * employer" and "scoped to all employers" are one careless SQL clause
     * apart, so the empty case never becomes a scope object at all.
     */
    public static MemberAccessScope employers(Set<Long> employerIds) {
        if (employerIds == null || employerIds.isEmpty()) {
            return denied("لا توجد جهة عمل مرتبطة بالمستخدم");
        }
        return new MemberAccessScope(Kind.EMPLOYERS,
                Collections.unmodifiableSet(new LinkedHashSet<>(employerIds)), null);
    }

    public static MemberAccessScope denied(String reason) {
        return new MemberAccessScope(Kind.DENIED, Set.of(), reason);
    }

    public boolean isGlobal() {
        return kind == Kind.GLOBAL;
    }

    public boolean isDenied() {
        return kind == Kind.DENIED;
    }

    /** Whether a member belonging to this employer is within reach. */
    public boolean covers(Long employerId) {
        return switch (kind) {
            case GLOBAL -> true;
            // A member with no employer is not "everyone's"; it is nobody's
            // until someone with global reach looks at it.
            case EMPLOYERS -> employerId != null && employerIds.contains(employerId);
            case DENIED -> false;
        };
    }
}
