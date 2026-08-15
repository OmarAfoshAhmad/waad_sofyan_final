package com.waad.tba.modules.member.security;

import lombok.Getter;

/**
 * A member operation refused on scope grounds.
 *
 * Distinct from a "not found": the record exists and the caller may not have
 * it. Collapsing the two into an empty result would tell an employer
 * administrator that another tenant's employer has no members, which is a
 * false statement about data they are not entitled to any statement about.
 */
@Getter
public class MemberAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient MemberOperation operation;

    public MemberAccessDeniedException(MemberOperation operation, String reason) {
        super(reason == null ? "العملية غير مسموح بها ضمن نطاق المستخدم" : reason);
        this.operation = operation;
    }
}
