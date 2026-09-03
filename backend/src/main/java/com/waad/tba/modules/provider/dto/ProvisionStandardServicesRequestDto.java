package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One request body shape for both preview and apply: the operation is
 * deterministic and cheap to recompute, so there is no uploaded-file/session
 * state to keep between the two calls (unlike EmployerImportService's
 * preview/confirm, which exists to avoid re-parsing a file).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionStandardServicesRequestDto {

    public enum Scope {
        PROVIDER_TYPES, ALL_ACTIVE, SELECTED_PROVIDERS
    }

    @NotEmpty(message = "At least one service code is required")
    private List<String> serviceCodes;

    private Scope scope;

    /** Required when scope = PROVIDER_TYPES. */
    private List<String> providerTypes;

    /** Required when scope = SELECTED_PROVIDERS. */
    private List<Long> providerIds;
}
