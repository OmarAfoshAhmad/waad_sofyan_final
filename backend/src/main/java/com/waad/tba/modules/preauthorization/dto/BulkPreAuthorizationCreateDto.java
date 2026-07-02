package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkPreAuthorizationCreateDto {

    @NotNull(message = "Visit ID is required")
    @Positive(message = "Visit ID must be positive")
    private Long visitId;

    @NotEmpty(message = "At least one service is required")
    @Valid
    private List<PreAuthorizationCreateDto> requests;
}
