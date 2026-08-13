package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.BusinessRuleException;

/**
 * A single Excel import row rejected the DATA it was given: a missing
 * required field, an unresolvable employer/policy reference, an
 * unrecognized member_status value. This is the ONLY exception type
 * MemberExcelImportService.executeImport's row loop catches as an expected,
 * per-row outcome (recorded and skipped, nothing rolled back).
 *
 * Extends BusinessRuleException (not RuntimeException directly) so existing
 * broad `catch (BusinessRuleException e)` call sites elsewhere in the
 * codebase keep working unchanged -- this narrows what the IMPORT ROW LOOP
 * specifically catches, without narrowing anything else.
 *
 * Deliberately NOT used for java.lang.IllegalArgumentException or any other
 * general-purpose JDK exception: those can just as easily indicate a
 * programming bug as bad row data, and must be free to propagate and abort
 * the whole batch like any other technical failure.
 */
public class MemberImportRowValidationException extends BusinessRuleException {
    private static final long serialVersionUID = 1L;

    public MemberImportRowValidationException(String message) {
        super(message);
    }
}
