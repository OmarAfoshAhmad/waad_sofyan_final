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
    /**
     * The general ceiling and its buckets, for a page of members or for one.
     *
     * Separate from VIEW_FINANCIALS because it answers to a different grant:
     * a ceiling is what may still be committed, which the people entering
     * claims need, while VIEW_FINANCIALS opens claim history and the
     * insurer's wider position. Folding them together forced one permission
     * to stand for two decisions and made the role the tie-breaker.
     */
    VIEW_LIMITS,
    /**
     * The same ceilings for a page of members at once.
     *
     * A different act from reading one. Checking the patient in front of you
     * is care; pulling a page of balances is a view of the book, with a
     * different blast radius and a different data volume. Giving them one
     * permission forced a provider's legitimate single read to carry the bulk
     * one with it.
     */
    LIST_LIMITS,

    /**
     * Granting or ending an exceptional increase to one member's general
     * ceiling. Not a read of the ceiling and not an edit of the record: it
     * commits the insurer's money for one person against the policy their
     * colleagues share.
     */
    MANAGE_LIMIT_UPLIFT,
    /** Bulk extraction: the whole result set leaves the system as a file. */
    EXPORT,

    CREATE_MEMBER,
    /** Name, phone, descriptive fields. NOT employer or policy. */
    EDIT_DEMOGRAPHICS,
    ADD_DEPENDENT,
    /** Move an existing dependent to another principal with dated context. */
    TRANSFER_DEPENDENT,
    /** Move a principal and their whole family to another employer, dated, all-or-nothing. */
    TRANSFER_EMPLOYER,
    /** Correct the declared kinship without changing family ownership. */
    CORRECT_RELATIONSHIP,
    CHANGE_POLICY,
    REORDER_FAMILY,
    CHANGE_STATUS,
    TERMINATE,
    REINSTATE,
    /**
     * Reviving a membership that was deliberately ended -- a different act
     * from lifting a suspension, and carrying its own grant.
     */
    REINSTATE_TERMINATED,
    /** Physical removal, allowed only where no trace exists at all. */
    HARD_DELETE,
    BULK_OPERATION,

    /** System-wide operations that cross every employer. */
    RESET_KINSHIP,
    RESOLVE_DUPLICATES,

    IMPORT_PREVIEW,
    IMPORT_EXECUTE,
    IMPORT_HISTORY,
    IMPORT_ROLLBACK,

    /** An audited eligibility decision, which writes. */
    EVALUATE_ELIGIBILITY
}
