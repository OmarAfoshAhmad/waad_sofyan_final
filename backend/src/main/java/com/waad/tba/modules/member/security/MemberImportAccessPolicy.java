package com.waad.tba.modules.member.security;

import java.util.Collection;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/**
 * Decides who may import member data, and over which employers.
 *
 * An import is the widest write in the system: one file can create hundreds
 * of members and, with clearOldMembers, end hundreds more. So the file is
 * judged as a whole -- one row outside the caller's reach refuses the entire
 * operation. Importing the permitted rows and skipping the rest is the
 * dangerous outcome: the caller is told it worked and never learns which
 * people were left out.
 *
 * Preview and execute apply the SAME rules, and execute re-checks inside its
 * transaction. A preview that succeeded a minute ago is not evidence about
 * now: the user's role or employer may have changed in between.
 */
@Component
@RequiredArgsConstructor
public class MemberImportAccessPolicy {

    private final MemberAccessScopeResolver scopeResolver;
    private final AuthorizationService authorizationService;

    /**
     * Authorises an import over the employers the file's rows belong to.
     *
     * @param rowEmployerIds every distinct employer appearing in the file --
     *                       including the default applied to rows that name
     *                       none, since a row with no employer is not
     *                       automatically the caller's
     * @param clearAbsentMembers whether the operation will end members absent
     *                       from the file
     */
    @Transactional(readOnly = true)
    public AuthorizedImportScope require(Collection<Long> rowEmployerIds, boolean clearAbsentMembers) {
        User user = authorizationService.getCurrentUser();
        MemberAccessScope scope = scopeResolver.resolveFor(user);

        if (scope.isDenied()) {
            throw new MemberAccessDeniedException(MemberOperation.IMPORT_EXECUTE, scope.reason());
        }

        boolean superAdmin = authorizationService.isSuperAdmin(user);
        boolean dataEntry = authorizationService.isDataEntry(user);
        if (!superAdmin && !dataEntry) {
            // Matches the endpoint's own contract: importing is a data-entry
            // function, not something every administrator does.
            throw new MemberAccessDeniedException(MemberOperation.IMPORT_EXECUTE,
                    "استيراد المستفيدين غير مسموح لهذا الدور");
        }

        if (rowEmployerIds == null || rowEmployerIds.isEmpty()) {
            // A file whose employer could not be determined is not an empty
            // success; it is an operation whose target is unknown.
            throw new MemberAccessDeniedException(MemberOperation.IMPORT_EXECUTE,
                    "تعذر تحديد جهة العمل لصفوف الملف");
        }

        for (Long employerId : rowEmployerIds) {
            if (employerId == null) {
                throw new MemberAccessDeniedException(MemberOperation.IMPORT_EXECUTE,
                        "يوجد صف بلا جهة عمل محددة");
            }
            if (!scope.covers(employerId)) {
                // The whole file, not just this row. Partial import leaves a
                // silently incomplete roster that nobody can distinguish from
                // a complete one.
                throw new MemberAccessDeniedException(MemberOperation.IMPORT_EXECUTE,
                        "الملف يحتوي صفوفاً خارج نطاق المستخدم؛ رُفضت العملية بالكامل");
            }
        }

        // Ending the absentees is a mass status change. Data entry may create
        // and correct records; it may not end coverage, and doing it through
        // an import checkbox would be exactly the route around that rule.
        if (clearAbsentMembers && !superAdmin) {
            throw new MemberAccessDeniedException(MemberOperation.IMPORT_EXECUTE,
                    "إنهاء المستفيدين الغائبين عن الملف يتطلب صلاحية مدير النظام");
        }

        return new AuthorizedImportScope(scope, superAdmin);
    }
}
