package com.waad.tba.modules.member.entity;

/**
 * Where a member-to-policy assignment came from. BACKFILL specifically means
 * "inferred by V171 from the old single-pointer model", not observed -- see
 * MemberPolicyAssignment's Javadoc.
 */
public enum PolicyAssignmentSource {
    MANUAL,
    IMPORT,
    BACKFILL,
    EMPLOYER_DEFAULT,
    SYSTEM
}
