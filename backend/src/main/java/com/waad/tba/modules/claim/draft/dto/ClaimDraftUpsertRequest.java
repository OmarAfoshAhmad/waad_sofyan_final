package com.waad.tba.modules.claim.draft.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClaimDraftUpsertRequest {

    @NotNull
    private Long batchId;

    @NotNull
    private JsonNode data;

    private Long version;
}
