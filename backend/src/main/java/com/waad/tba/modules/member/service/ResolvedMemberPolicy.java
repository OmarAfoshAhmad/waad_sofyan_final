package com.waad.tba.modules.member.service;

/**
 * What the dated policy resolution found for one member, said out loud.
 *
 * The reason this is not simply a nullable policy id: the outcomes below
 * are different facts with different consequences, and collapsing them loses
 * the one thing a reader of a financial screen needs to know -- whether a
 * missing number means "nothing applies here" or "we could not find out".
 *
 * Nothing here falls back to Member.benefitPolicy. That pointer is the
 * member's policy *now*; it says nothing about the date being asked about,
 * and substituting it would answer a question nobody asked with a number that
 * looks equally authoritative.
 */
public record ResolvedMemberPolicy(Outcome outcome, Long policyId, Long assignmentId, String detail) {

    public enum Outcome {
        /** Exactly one assignment covers the date. */
        FOUND,
        /** No assignment covers the date. Not an error, and not zero either. */
        NOT_ASSIGNED,
        /**
         * More than one assignment covers the date, which V171's exclusion
         * constraint is supposed to make impossible. Refused rather than
         * resolved: picking one would be a guess about which policy priced a
         * person's care.
         */
        AMBIGUOUS,
        /**
         * An assignment covers the date, but the policy itself was not in
         * force then -- expired, suspended, or outside its own window.
         *
         * A real answer, like NOT_ASSIGNED: no ceiling applied. Assignments
         * are normally left open-ended, closed only when a new one starts, so
         * without this check an assignment would keep answering with a policy
         * that expired years ago and silently extend coverage nobody granted.
         */
        POLICY_NOT_IN_FORCE,
        /**
         * The assigned policy belongs to a different employer than the one
         * that owned the member on that date.
         *
         * Not a real answer -- a data-integrity alarm. Reported as unknown
         * rather than as "no coverage", because the two demand different
         * responses from whoever reads the screen.
         */
        EMPLOYER_MISMATCH,
        /** The read itself failed. Never to be shown as a balance. */
        UNAVAILABLE
    }

    public static ResolvedMemberPolicy found(Long policyId, Long assignmentId) {
        return new ResolvedMemberPolicy(Outcome.FOUND, policyId, assignmentId, null);
    }

    public static ResolvedMemberPolicy notAssigned() {
        return new ResolvedMemberPolicy(Outcome.NOT_ASSIGNED, null, null, null);
    }

    public static ResolvedMemberPolicy ambiguous(String detail) {
        return new ResolvedMemberPolicy(Outcome.AMBIGUOUS, null, null, detail);
    }

    public static ResolvedMemberPolicy policyNotInForce(String detail) {
        return new ResolvedMemberPolicy(Outcome.POLICY_NOT_IN_FORCE, null, null, detail);
    }

    public static ResolvedMemberPolicy employerMismatch(String detail) {
        return new ResolvedMemberPolicy(Outcome.EMPLOYER_MISMATCH, null, null, detail);
    }

    public static ResolvedMemberPolicy unavailable(String detail) {
        return new ResolvedMemberPolicy(Outcome.UNAVAILABLE, null, null, detail);
    }

    public boolean isFound() {
        return outcome == Outcome.FOUND;
    }
}
