package com.waad.tba.modules.member.entity;

/** Origin of a dated member-to-employer assignment. */
public enum EmployerAssignmentSource {
    MANUAL,
    IMPORT,
    BACKFILL,
    FAMILY_CASCADE,
    SYSTEM
}
