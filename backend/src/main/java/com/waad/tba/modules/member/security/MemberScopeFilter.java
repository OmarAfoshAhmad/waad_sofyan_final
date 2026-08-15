package com.waad.tba.modules.member.security;

import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Turns an authorised scope into the constraint a query must carry.
 *
 * The point is that callers never interpret the scope themselves. Two call
 * sites reading {@code employerIds()} and deciding what an empty set means will
 * eventually disagree, and the disagreement is silent: one returns nothing, the
 * other returns everything, and only the second is a breach.
 *
 * So the mapping lives here once and is exhaustive over the scope's states:
 *
 * <pre>
 *   GLOBAL     no employer restriction
 *   EMPLOYERS  employer_id IN (a non-empty set)
 *   DENIED     unrepresentable -- it never reaches a query at all, because
 *              AuthorizedMemberScope cannot be constructed from one
 * </pre>
 *
 * Nothing here returns null. A nullable "no constraint" has to be tested for at
 * every call site, and the one place that forgets does not fail -- it queries
 * every tenant. An always-true predicate composes the same way as a restrictive
 * one, so there is no branch left to omit.
 */
public final class MemberScopeFilter {

    private MemberScopeFilter() {
    }

    /**
     * The employer restriction for the given scope, as a JPA predicate.
     *
     * @param employerIdPath the employer id reached from the query root, e.g.
     *                       {@code root.get("employer").get("id")}
     */
    public static Predicate toPredicate(AuthorizedMemberScope scope, Path<?> employerIdPath,
            CriteriaBuilder builder) {

        if (scope == null) {
            // Defensive rather than expected: a null scope means the caller
            // skipped authorisation entirely, and the safe reading of that is
            // "nothing", never "everything".
            return builder.disjunction();
        }
        if (scope.isGlobal()) {
            return builder.conjunction();
        }

        Set<Long> employerIds = scope.employerIds();
        if (employerIds.isEmpty()) {
            // Unreachable through MemberAccessScope, which turns an empty set
            // into DENIED. Kept because the failure mode of getting this wrong
            // is a cross-tenant read, and an impossible branch is cheaper than
            // that.
            return builder.disjunction();
        }
        return employerIdPath.in(employerIds);
    }

    /**
     * The same restriction as a SQL fragment, for the native and JPQL queries
     * that cannot take a Criteria predicate. Always a valid boolean expression,
     * so it can be appended with AND unconditionally.
     *
     * @param column the qualified employer id column, e.g. "m.employer_id"
     */
    public static String toSqlFragment(AuthorizedMemberScope scope, String column) {
        if (scope == null) {
            return "1 = 0";
        }
        if (scope.isGlobal()) {
            return "1 = 1";
        }
        Set<Long> employerIds = scope.employerIds();
        if (employerIds.isEmpty()) {
            return "1 = 0";
        }

        StringBuilder sql = new StringBuilder(column).append(" in (");
        boolean first = true;
        for (Long id : employerIds) {
            if (!first) {
                sql.append(", ");
            }
            // Ids are Longs taken from the resolved scope, never strings taken
            // from a request, so there is nothing here to inject.
            sql.append(id.longValue());
            first = false;
        }
        return sql.append(')').toString();
    }
}
