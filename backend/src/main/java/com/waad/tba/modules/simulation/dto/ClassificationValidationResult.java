package com.waad.tba.modules.simulation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClassificationValidationResult {
    private boolean categoryExists;
    private boolean categoryActive;
    private double backendConfidence;
    private boolean requiresReview;
    private String validationStatus;
    private String validationReason;
    private String recommendedCategoryCode;
    private List<String> warnings;
}
