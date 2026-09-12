package com.waad.tba.modules.member.exception;

import com.waad.tba.common.exception.ResourceNotFoundException;

/**
 * No member matches the eligibility lookup. A ResourceNotFoundException so
 * GlobalExceptionHandler maps it to 404 / MEMBER_NOT_FOUND; the message does
 * not echo the identifier that was searched for.
 */
public class MemberNotFoundException extends ResourceNotFoundException {

    private static final String DEFAULT_MESSAGE = "المستفيد غير موجود.";

    public MemberNotFoundException() {
        this(DEFAULT_MESSAGE);
    }

    public MemberNotFoundException(String message) {
        super(message != null ? message : DEFAULT_MESSAGE);
    }
}
