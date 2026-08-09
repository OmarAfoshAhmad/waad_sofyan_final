package com.waad.tba.modules.medicaldictionary.enums;

public enum V50ClassificationStatus {
    AUTO_APPROVED,
    STRONG_SUGGESTION,
    REVIEW_REQUIRED,
    SPLIT_REQUIRED,
    QUARANTINED_NON_SERVICE,
    EXCLUDED_COSMETIC;

    public boolean mayPostToContract() {
        return this == AUTO_APPROVED;
    }
}
