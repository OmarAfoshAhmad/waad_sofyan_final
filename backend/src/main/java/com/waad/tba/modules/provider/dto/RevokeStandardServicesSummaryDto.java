package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Same shape for revoke-preview (what would be deactivated) and
 * revoke-apply (what actually was) -- mirrors
 * ProvisionStandardServicesSummaryDto's preview/apply symmetry.
 *
 * assignmentsBlockedByClaimHistory / blockedAssignments: a standard-service
 * assignment that already has at least one claim line against it is never
 * revoked, in preview or apply -- deactivating it would make it look, in
 * retrospect, like the provider was never allowed to bill that service, when
 * claims against it already exist. Each blocked pair is named explicitly
 * (which provider, which service, why) so the refusal is never a bare count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokeStandardServicesSummaryDto {
    private long providersMatched;
    private long providersAffected;
    private long assignmentsToRevoke;
    private long assignmentsAlreadyInactive;
    private long assignmentsBlockedByClaimHistory;
    private List<BlockedAssignment> blockedAssignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlockedAssignment {
        private Long providerId;
        private String providerName;
        private String serviceCode;
        private String serviceName;
        private String reason;
    }
}
