package com.waad.tba.modules.member.security;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.waad.tba.modules.rbac.permission.SystemPermission;

/**
 * The one place that says which permission an operation on a member answers
 * to.
 *
 * Three questions decide a member operation, and they are separate on purpose:
 *
 *   permission  -- may this user perform this act at all?
 *   scope       -- on whose members?
 *   domain rule -- does this member's state and history allow it now?
 *
 * A role is a default set of permissions and nothing else. It used to be the
 * first question's answer, which made two things impossible at once: the front
 * end could not tell what the server would allow, because it can only read the
 * permission catalogue; and an administrator could not grant an exception,
 * because a rule written as a role has nowhere to record one.
 *
 * Every operation is listed, including the ones that need no grant beyond
 * reach. An operation absent from a map is an operation whose gate someone
 * forgot, and the absence looks identical to a deliberate "open" -- so the
 * map is exhaustive and a test fails when a new operation is added without a
 * decision about it.
 */
public final class MemberOperationPermissions {

    private MemberOperationPermissions() {
    }

    /**
     * Operations that need nothing beyond reaching the member. Reading a
     * record you are already scoped to is not a separate grant; the scope
     * resolver has already answered the only question there was.
     */
    private static final SystemPermission REACH_IS_ENOUGH = null;

    private static final Map<MemberOperation, SystemPermission> BY_OPERATION =
            new EnumMap<>(MemberOperation.class);

    static {
        // ── reading ────────────────────────────────────────────────────────
        BY_OPERATION.put(MemberOperation.LIST, REACH_IS_ENOUGH);
        BY_OPERATION.put(MemberOperation.SEARCH, REACH_IS_ENOUGH);
        BY_OPERATION.put(MemberOperation.VIEW_DETAILS, REACH_IS_ENOUGH);
        BY_OPERATION.put(MemberOperation.VIEW_COVERAGE_BALANCE, REACH_IS_ENOUGH);
        BY_OPERATION.put(MemberOperation.VIEW_FINANCIALS, SystemPermission.MEMBER_FINANCIAL_VIEW);
        // One member's ceiling, read while treating or entering a claim.
        BY_OPERATION.put(MemberOperation.VIEW_LIMITS, SystemPermission.MEMBER_LIMIT_VIEW);
        // A page of them at once, which is a different act: it exposes a whole
        // book of balances rather than the patient in front of you.
        BY_OPERATION.put(MemberOperation.LIST_LIMITS, SystemPermission.MEMBER_LIMIT_LIST_VIEW);
        BY_OPERATION.put(MemberOperation.EXPORT, SystemPermission.MEMBER_EXPORT);

        // ── writing ────────────────────────────────────────────────────────
        BY_OPERATION.put(MemberOperation.CREATE_MEMBER, SystemPermission.MEMBER_CREATE);
        BY_OPERATION.put(MemberOperation.ADD_DEPENDENT, SystemPermission.MEMBER_CREATE);
        BY_OPERATION.put(MemberOperation.EDIT_DEMOGRAPHICS, SystemPermission.MEMBER_EDIT_IDENTITY);
        BY_OPERATION.put(MemberOperation.CORRECT_RELATIONSHIP, SystemPermission.MEMBER_EDIT_IDENTITY);
        BY_OPERATION.put(MemberOperation.REORDER_FAMILY, SystemPermission.MEMBER_EDIT_IDENTITY);
        BY_OPERATION.put(MemberOperation.CHANGE_POLICY, SystemPermission.MEMBER_EDIT_IDENTITY);

        BY_OPERATION.put(MemberOperation.CHANGE_STATUS, SystemPermission.MEMBER_CHANGE_STATUS);
        BY_OPERATION.put(MemberOperation.TERMINATE, SystemPermission.MEMBER_CHANGE_STATUS);
        BY_OPERATION.put(MemberOperation.REINSTATE, SystemPermission.MEMBER_CHANGE_STATUS);
        BY_OPERATION.put(MemberOperation.BULK_OPERATION, SystemPermission.MEMBER_CHANGE_STATUS);

        BY_OPERATION.put(MemberOperation.TRANSFER_EMPLOYER, SystemPermission.MEMBER_TRANSFER_EMPLOYER);
        BY_OPERATION.put(MemberOperation.TRANSFER_DEPENDENT, SystemPermission.MEMBER_TRANSFER_EMPLOYER);

        BY_OPERATION.put(MemberOperation.HARD_DELETE, SystemPermission.MEMBER_HARD_DELETE);

        // Reviving a membership that was deliberately ended is not the same
        // act as suspending one, and carries its own grant.
        BY_OPERATION.put(MemberOperation.REINSTATE_TERMINATED,
                SystemPermission.MEMBER_REINSTATE_TERMINATED);

        // ── system-wide repair ─────────────────────────────────────────────
        // These cross every employer and rewrite relationships in bulk. They
        // answer to the danger-zone grant rather than to a member permission,
        // because what they endanger is the shape of the data, not one record.
        BY_OPERATION.put(MemberOperation.RESET_KINSHIP, SystemPermission.DANGER_ZONE_EXECUTE);
        BY_OPERATION.put(MemberOperation.RESOLVE_DUPLICATES, SystemPermission.DANGER_ZONE_EXECUTE);

        // ── import ─────────────────────────────────────────────────────────
        BY_OPERATION.put(MemberOperation.IMPORT_PREVIEW, SystemPermission.MEMBER_IMPORT);
        BY_OPERATION.put(MemberOperation.IMPORT_EXECUTE, SystemPermission.MEMBER_IMPORT);
        BY_OPERATION.put(MemberOperation.IMPORT_HISTORY, SystemPermission.MEMBER_IMPORT);
        BY_OPERATION.put(MemberOperation.IMPORT_ROLLBACK, SystemPermission.MEMBER_IMPORT);

        // Writes an audited decision about someone's eligibility, but the act
        // is reading their cover; the scope is the only question.
        BY_OPERATION.put(MemberOperation.EVALUATE_ELIGIBILITY, REACH_IS_ENOUGH);
    }

    /**
     * The permission this operation answers to, or empty when reaching the
     * member is the whole question.
     *
     * @throws IllegalStateException for an operation nobody has decided about,
     *         which is louder than defaulting either way -- defaulting to open
     *         ships a hole, and defaulting to closed ships an outage that
     *         looks like a permission problem
     */
    public static Optional<SystemPermission> requiredFor(MemberOperation operation) {
        if (!BY_OPERATION.containsKey(operation)) {
            throw new IllegalStateException("MEMBER_OPERATION_HAS_NO_PERMISSION_DECISION: " + operation);
        }
        return Optional.ofNullable(BY_OPERATION.get(operation));
    }

    /** Every operation and its permission, for the tests that keep this honest. */
    public static Map<MemberOperation, SystemPermission> all() {
        return java.util.Collections.unmodifiableMap(BY_OPERATION);
    }
}
