package com.waad.tba.modules.claim.draft.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClaimDraftResponse {
    private Long id;
    private Long userId;
    private Long batchId;
    private String status;
    private JsonNode data;
    private Long version;
    private LocalDateTime updatedAt;
    private boolean conflictResolved;
}
