package com.waad.tba.modules.member.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/**
 * Computes WHICH members a caller may reach. Nothing else.
 *
 * Deliberately narrow: it does not know about searching, exporting or
 * importing, and must not learn. Those are separate policies that consume this
 * one answer, so the scope rule lives in a single place while each policy
 * keeps its own concerns.
 *
 * The rule it replaces returned a nullable employer id and clamped only
 * EMPLOYER_ADMIN; every other role passed whatever it asked for straight
 * through, and null meant "no filter" -- which is every employer. Here an
 * unestablished scope is DENIED.
 */
@Component
@RequiredArgsConstructor
public class MemberAccessScopeResolver {

    private final AuthorizationService authorizationService;
    private final ProviderRepository providerRepository;

    @Transactional(readOnly = true)
    public MemberAccessScope resolve() {
        return resolveFor(authorizationService.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public MemberAccessScope resolveFor(User user) {
        if (user == null) {
            return MemberAccessScope.denied("لا يوجد مستخدم مصادق عليه");
        }

        // Granted, not inferred.
        if (authorizationService.isSuperAdmin(user)) {
            return MemberAccessScope.global();
        }

        if (authorizationService.isEmployerAdmin(user)) {
            // An employer administrator with no employer is a misconfigured
            // account, not a system-wide one.
            return user.getEmployerId() == null
                    ? MemberAccessScope.denied("مدير جهة عمل بلا جهة مرتبطة")
                    : MemberAccessScope.employers(Set.of(user.getEmployerId()));
        }

        if (authorizationService.isProvider(user)) {
            return providerScope(user);
        }

        // Reviewers and data-entry staff are NOT global by default. Their
        // roles say what they may do, not whose records they may do it to,
        // and treating "no employer" as "all employers" is exactly the
        // conflation this class exists to remove. Until an explicit
        // cross-tenant grant exists, a reviewer is scoped to their own
        // employer or denied.
        if (user.getEmployerId() != null) {
            return MemberAccessScope.employers(Set.of(user.getEmployerId()));
        }
        return MemberAccessScope.denied(
                "لا يمكن تحديد نطاق المستخدم؛ يلزم ربطه بجهة عمل أو منحه صلاحية وصول عام صريحة");
    }

    /**
     * Narrows a caller's scope to the employer they asked for, and refuses if
     * that employer is outside it.
     *
     * A REJECTION, never an empty intersection. Quietly returning "no
     * employers" would render as an empty list -- a screen saying this
     * employer has no members, which is a different and false statement from
     * "you may not look here". Only SUPER_ADMIN reaches past its own scope,
     * and it does so because GLOBAL already covers everything.
     */
    @Transactional(readOnly = true)
    public MemberAccessScope resolveFor(User user, Long requestedEmployerId) {
        MemberAccessScope scope = resolveFor(user);
        if (scope.isDenied() || requestedEmployerId == null) {
            return scope;
        }
        if (!scope.covers(requestedEmployerId)) {
            return MemberAccessScope.denied(
                    "الجهة المطلوبة خارج نطاق المستخدم");
        }
        return MemberAccessScope.employers(java.util.Set.of(requestedEmployerId));
    }

    /**
     * A provider sees the employers it is contracted with. `allowAllEmployers`
     * is a real commercial arrangement -- an open network -- so it grants
     * global reach; an empty allow-list under a closed network grants nothing.
     */
    private MemberAccessScope providerScope(User user) {
        if (user.getProviderId() == null) {
            return MemberAccessScope.denied("مستخدم مقدم خدمة بلا مرفق مرتبط");
        }
        Provider provider = providerRepository.findById(user.getProviderId()).orElse(null);
        if (provider == null) {
            return MemberAccessScope.denied("مقدم الخدمة المرتبط بالمستخدم غير موجود");
        }
        if (Boolean.TRUE.equals(provider.getAllowAllEmployers())) {
            return MemberAccessScope.global();
        }

        Set<Long> employerIds = provider.getAllowedEmployers() == null ? Set.of()
                : provider.getAllowedEmployers().stream()
                        // A DEACTIVATED link is an ended commercial relationship,
                        // and it was being counted as a live one. The list is an
                        // unfiltered @OneToMany, so every employer this provider
                        // was ever contracted with stayed inside its scope --
                        // long after the contract that put it there was closed.
                        .filter(link -> Boolean.TRUE.equals(link.getActive()))
                        .map(link -> link.getEmployer() == null ? null : link.getEmployer().getId())
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return MemberAccessScope.employers(employerIds);
    }
}
