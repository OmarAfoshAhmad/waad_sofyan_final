package com.waad.tba.modules.claim.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PauseClaimReviewRequest {
    @NotBlank(message = "سبب تعليق المراجعة مطلوب")
    @Size(max = 1000, message = "سبب التعليق يجب ألا يتجاوز 1000 حرف")
    private String reason;
}
