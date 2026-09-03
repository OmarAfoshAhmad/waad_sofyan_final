package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Same shape for preview (what would happen) and apply (what happened) --
 * the caller renders whichever one it just got the same way.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionStandardServicesSummaryDto {
    private long providersMatched;
    private long providersAlreadyComplete;
    private long providersNeedingChanges;
    private long assignmentsToCreate;
    private long assignmentsToReactivate;
    private long assignmentsAlreadyActive;
}
