package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

/** Which limit axis actually constrained the decision (P1.3 §1.3). */
public enum BindingConstraintType {
    AMOUNT,
    TIMES,
    DAYS,
    NONE
}
