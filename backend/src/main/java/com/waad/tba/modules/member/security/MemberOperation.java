package com.waad.tba.modules.member.security;

/**
 * What a caller is trying to do with member data.
 *
 * Named separately from the role because the two answer different questions:
 * a role says who someone is, an operation says what is being attempted, and
 * the policy decides whether this who may do this what to these records.
 * Reading a name and reading a financial balance are not the same act even
 * when they concern the same member.
 */
public enum MemberOperation {

    LIST,
    SEARCH,
    /** A single member's own record. */
    VIEW_DETAILS,
    /** Current coverage balance only, used while treating/entering a claim. */
    VIEW_COVERAGE_BALANCE,
    /** Balances, limits and consumption -- a narrower grant than VIEW_DETAILS. */
    VIEW_FINANCIALS,
    /** Bulk extraction: the whole result set leaves the system as a file. */
    EXPORT,

    CREATE_MEMBER,
    /** Name, phone, descriptive fields. NOT employer or policy. */
    EDIT_DEMOGRAPHICS,
    ADD_DEPENDENT,
    /** Move an existing dependent to another principal with dated context. */
    TRANSFER_DEPENDENT,
    /** Correct the declared kinship without changing family ownership. */
    CORRECT_RELATIONSHIP,
    CHANGE_POLICY,
    REORDER_FAMILY,
    CHANGE_STATUS,
    TERMINATE,
    REINSTATE,
    /** Physical removal, allowed only where no trace exists at all. */
    HARD_DELETE,
    BULK_OPERATION,

    /** System-wide operations that cross every employer. */
    RESET_KINSHIP,
    RESOLVE_DUPLICATES,

    IMPORT_PREVIEW,
    IMPORT_EXECUTE,

    /** An audited eligibility decision, which writes. */
    EVALUATE_ELIGIBILITY
}
