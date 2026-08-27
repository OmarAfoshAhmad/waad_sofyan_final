package com.waad.tba.modules.member.entity;

/**
 * Where a member status transition came from. Stored (not free text) so
 * "why did this member's status change" can be queried and reported on,
 * and so a family cascade (FAMILY_CASCADE) is unambiguously distinguishable
 * from a member's own independent transition -- restoreFamily relies on
 * this distinction to avoid reinstating a dependent who was suspended for
 * their own reason.
 */
public enum StatusSource {
    MANUAL,
    IMPORT,
    FAMILY_CASCADE,
    POLICY_EXPIRY,
    EMPLOYER_SUSPENSION,
    SYSTEM
}
