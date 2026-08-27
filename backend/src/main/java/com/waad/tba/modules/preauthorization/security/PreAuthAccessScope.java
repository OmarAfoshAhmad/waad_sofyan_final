package com.waad.tba.modules.preauthorization.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * WHICH pre-authorizations a caller may reach. Nothing about what they may do
 * to them -- that is the permission's job, and conflating the two is how a
 * PREAUTH_CREATE holder ended up able to write in any provider's name.
 *
 * A pre-authorization sits on two axes at once: the provider that submitted it
 * and the employer whose member it is for. A provider's staff are bounded by
 * the first, an employer's administrator by the second, and the TPA's own
 * reviewers by neither -- reviewing across every provider is the work, not a
 * privilege escalation.
 *
 * Modelled on MemberAccessScope deliberately: it is the component that stopped
 * the S-01 chain from reaching member data, and a second scope model with
 * different rules would be a second thing to reason about under pressure.
 *
 * An unestablished scope is DENIED, never "everything" and never an empty set
 * that renders as "this provider has no requests".
 */
public record PreAuthAccessScope(Kind kind, Set<Long> providerIds, Set<Long> employerIds, String reason) {

    public enum Kind { GLOBAL, PROVIDERS, EMPLOYERS, DENIED }

    public static PreAuthAccessScope global() {
        return new PreAuthAccessScope(Kind.GLOBAL, Set.of(), Set.of(), null);
    }

    public static PreAuthAccessScope providers(Set<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return denied("لا يوجد مقدم خدمة مرتبط بالمستخدم");
        }
        return new PreAuthAccessScope(Kind.PROVIDERS,
                Collections.unmodifiableSet(new LinkedHashSet<>(providerIds)), Set.of(), null);
    }

    public static PreAuthAccessScope employers(Set<Long> employerIds) {
        if (employerIds == null || employerIds.isEmpty()) {
            return denied("لا توجد جهة عمل مرتبطة بالمستخدم");
        }
        return new PreAuthAccessScope(Kind.EMPLOYERS, Set.of(),
                Collections.unmodifiableSet(new LinkedHashSet<>(employerIds)), null);
    }

    public static PreAuthAccessScope denied(String reason) {
        return new PreAuthAccessScope(Kind.DENIED, Set.of(), Set.of(), reason);
    }

    public boolean isGlobal() {
        return kind == Kind.GLOBAL;
    }

    public boolean isDenied() {
        return kind == Kind.DENIED;
    }

    /**
     * @param providerId the provider that submitted the request
     * @param employerId the employer of the member it was raised for; may be
     *                   null when the member has no employer resolved, which is
     *                   not the same as "belongs to everyone"
     */
    public boolean covers(Long providerId, Long employerId) {
        return switch (kind) {
            case GLOBAL -> true;
            case PROVIDERS -> providerId != null && providerIds.contains(providerId);
            case EMPLOYERS -> employerId != null && employerIds.contains(employerId);
            case DENIED -> false;
        };
    }

    /**
     * The single provider a caller writes as, when their scope names exactly
     * one. Empty for GLOBAL (an administrator must say which provider out
     * loud), for DENIED, and for any scope covering more than one -- in none of
     * those cases can the answer be inferred, and inferring it is precisely the
     * bug this class exists to remove.
     */
    public java.util.Optional<Long> singleProviderId() {
        if (kind != Kind.PROVIDERS || providerIds.size() != 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(providerIds.iterator().next());
    }
}
