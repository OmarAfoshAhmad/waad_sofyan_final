package com.waad.tba.modules.claim.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the three consolidations in place.
 *
 * <p>Each of them removed a second copy of something, and a second copy is easy
 * to reintroduce by accident -- a new caller resolves the member again "just to
 * be safe", a new check is written inline because it is two lines. These
 * assertions read the source rather than run it, because what they protect is
 * the shape of the code and not a behaviour a test could observe.
 */
class ClaimEntrySingleContextArchitectureTest {

    private static final Path SERVICES =
            Path.of("src/main/java/com/waad/tba/modules/claim/service");

    private String source(String name) throws IOException {
        return Files.readString(SERVICES.resolve(name));
    }

    /**
     * Direct entry resolves the member's dated context and hands it to claim
     * creation. Before this, one request resolved it twice -- and two answers to
     * the same dated question are two chances to disagree about which employer
     * or policy applied.
     */
    @Test
    void directEntryResolvesTheDatedContextOnceAndPassesItOn() throws IOException {
        String directEntry = source("DirectClaimEntryService.java");

        assertThat(directEntry)
                .as("still resolves once, to check the batch's employer")
                .contains("memberContextResolver.resolveForOrFail(");
        assertThat(directEntry)
                .as("and hands that resolution to claim creation rather than letting it resolve again")
                .contains("claimService.createClaim(claimDto, datedMember)");
    }

    @Test
    void claimCreationAcceptsAnAlreadyResolvedContext() throws IOException {
        String claimService = source("ClaimService.java");

        assertThat(claimService)
                .as("the overload exists and the single-argument entry point still works")
                .contains("createClaim(ClaimCreateDto dto,")
                .contains("return createClaim(dto, null);");
        assertThat(claimService)
                .as("and it reuses what it was given instead of resolving unconditionally")
                .contains("resolvedMemberContext != null");
    }

    /**
     * Both employer questions live in one service. The temporal one was asked in
     * two places with two different messages; the network one was inline in the
     * middle of claim creation.
     */
    @Test
    void bothEmployerChecksGoThroughTheOneService() throws IOException {
        assertThat(source("ClaimEntryContextService.java"))
                .contains("employerAccess.requireMemberBelongsToEmployer(");
        assertThat(source("DirectClaimEntryService.java"))
                .contains("employerAccess.requireMemberBelongsToEmployer(");
        assertThat(source("ClaimService.java"))
                .contains("employerAccess.requireProviderServesEmployer(");
    }

    @Test
    void theNetworkCheckIsNotReimplementedInline() throws IOException {
        assertThat(source("ClaimService.java"))
                .as("claim creation asks the service instead of querying the network table itself")
                .doesNotContain("providerAllowedEmployerRepository");
    }

    /**
     * The fingerprint must not go back to hashing serialised JSON: that made a
     * retry's identity depend on field order, so an identical command could be
     * refused as a different one -- and the operator, seeing no claim, enters it
     * again by hand.
     */
    @Test
    void theIdempotencyFingerprintIsNotJsonSerialisation() throws IOException {
        String directEntry = source("DirectClaimEntryService.java");

        assertThat(directEntry).contains("fingerprints.of(request)");
        assertThat(directEntry)
                .doesNotContain("writeValueAsBytes")
                .doesNotContain("ObjectMapper");
    }
}
