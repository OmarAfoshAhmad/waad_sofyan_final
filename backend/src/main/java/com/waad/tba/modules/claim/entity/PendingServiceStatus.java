package com.waad.tba.modules.claim.entity;

public enum PendingServiceStatus {
    PRELIMINARY,
    NEEDS_INFO,
    SPLIT_REQUIRED,
    APPROVED_CLAIM_ONLY,
    APPROVED_FOR_CONTRACT,
    LINKED_EXISTING,
    REJECTED;

    public boolean isResolved() {
        return this == APPROVED_CLAIM_ONLY || this == APPROVED_FOR_CONTRACT
                || this == LINKED_EXISTING || this == REJECTED;
    }
}
