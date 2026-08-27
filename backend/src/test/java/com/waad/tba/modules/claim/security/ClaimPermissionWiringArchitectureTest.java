package com.waad.tba.modules.claim.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ClaimPermissionWiringArchitectureTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java");
    private static final Path ATTACHMENTS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimAttachmentController.java");
    private static final Path DRAFTS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimDraftController.java");
    private static final Path REPORTS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ReportsController.java");
    private static final Path BATCHES = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimBatchController.java");
    private static final Path PENDING_SERVICES = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimPendingServiceController.java");
    private static final Path REJECTION_REASONS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimRejectionReasonController.java");
    private static final Path REVIEWER_ASSIGNMENTS = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/MedicalReviewerProviderAssignmentController.java");
    private static final Path REVIEWER_SCOPE = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ReviewerScopeController.java");
    private static final Path LEGACY_RECONCILIATION = Path.of(
            "src/main/java/com/waad/tba/modules/claim/controller/ClaimLegacyReconciliationController.java");

    @Test
    void resourceMutationsUseClaimAccessGuardInsteadOfRoles() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source).contains("@claimAccessGuard.canCreateFromVisit(#apiRequest.visitId)")
                .contains("@claimAccessGuard.canEdit(#id)")
                .contains("@claimAccessGuard.canReview(#id)")
                .contains("@claimAccessGuard.canApprove(#id)")
                .contains("@claimAccessGuard.canReverse(#id)")
                .contains("@claimAccessGuard.canHardDelete(#id)");
    }

    @Test
    void deprecatedGenericUpdateIsDeniedBeforeExecution() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        assertThat(source).contains("@PutMapping(\"/{id:\\\\d+}\")\n    @PreAuthorize(\"denyAll()\")");
    }

    @Test
    void controllerContainsNoRoleBasedFallbacks() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(");
    }

    @Test
    void attachmentsAreBoundToClaimScopeAndDraftsRequireCreateCapability() throws Exception {
        String attachments = Files.readString(ATTACHMENTS);
        String drafts = Files.readString(DRAFTS);
        assertThat(attachments)
                .contains("@claimAccessGuard.canRead(#claimId)")
                .contains("@claimAccessGuard.canEdit(#claimId)")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
        assertThat(drafts)
                .contains("@permissionGuard.has('CLAIM_CREATE')")
                .doesNotContain("isAuthenticated()");
    }

    @Test
    void reportsUseFinancialCapabilitiesAndMemberStatementsUseMemberScope() throws Exception {
        String source = Files.readString(REPORTS);
        assertThat(source)
                .contains("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
                .contains("@permissionGuard.has('SETTLEMENT_VIEW')")
                .contains("@claimAccessGuard.canReadMemberFor('FINANCIAL_REPORT_VIEW', #memberId)")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
    }

    @Test
    void batchesIntersectCapabilitiesWithRequestedProviderAndEmployerScope() throws Exception {
        String source = Files.readString(BATCHES);
        assertThat(source)
                .contains("@claimAccessGuard.canAccessBatch('CLAIM_VIEW', #providerId, #employerId)")
                .contains("@claimAccessGuard.canAccessBatch('CLAIM_CREATE', #providerId, #employerId)")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
    }

    @Test
    void remainingClaimWorkflowsUseCapabilitiesAndClaimScopeInsteadOfRoles() throws Exception {
        String pending = Files.readString(PENDING_SERVICES);
        String reasons = Files.readString(REJECTION_REASONS);
        String assignments = Files.readString(REVIEWER_ASSIGNMENTS);
        String scope = Files.readString(REVIEWER_SCOPE);
        String reconciliation = Files.readString(LEGACY_RECONCILIATION);

        assertThat(pending)
                .contains("@claimAccessGuard.canReview(#claimId)")
                .contains("@claimAccessGuard.canApprove(#claimId)")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
        assertThat(reasons)
                .contains("@permissionGuard.has('CLAIM_REVIEW')")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
        assertThat(assignments)
                .contains("@permissionGuard.has('USER_MANAGE')")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
        assertThat(scope)
                .contains("@permissionGuard.has('CLAIM_REVIEW')")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
        assertThat(reconciliation)
                .contains("@permissionGuard.has('DANGER_ZONE_EXECUTE')")
                .doesNotContain("hasRole(", "hasAnyRole(", "isAuthenticated()");
    }
}
