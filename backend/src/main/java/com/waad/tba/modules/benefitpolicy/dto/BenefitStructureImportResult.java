package com.waad.tba.modules.benefitpolicy.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class BenefitStructureImportResult {
    boolean dryRun;
    int rules;
    int groups;
    int buckets;
    int links;
    int specialBenefits;
    int created;
    int updated;
    List<String> warnings;
    List<String> errors;
}
