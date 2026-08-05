package com.waad.tba.modules.benefitpolicy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for benefit policy selectors/dropdowns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPolicySelectorDto {
    
    private Long id;
    
    /**
     * Display label (policy name)
     */
    private String label;
    
    /**
     * Policy code for reference
     */
    private String policyCode;
    
    /**
     * Whether the policy is currently effective
     */
    private boolean effective;

    /**
     * Whether the policy has at least one active coverage rule
     */
    private boolean hasRules;
}
