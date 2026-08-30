package com.waad.tba.modules.claim.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One HTTP command for the visit and claim created by internal direct entry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectClaimEntryRequest {

    @NotNull(message = "Employer ID is required")
    @Positive(message = "Employer ID must be positive")
    private Long employerId;

    @Valid
    @NotNull(message = "Claim data is required")
    private CreateClaimRequest claim;
}
