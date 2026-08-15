package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Prevents the parallel access paths removed by member-01 from returning. */
class MemberAccessWiringArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java/com/waad/tba/modules/member");

    @Test
    void memberProductionCodeDoesNotUseTheLegacyNullableEmployerScope() throws IOException {
        try (var files = Files.walk(MAIN)) {
            var offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> read(p).contains(".resolveEmployerScope("))
                    .map(Path::toString)
                    .toList();
            assertThat(offenders).as("legacy null-means-global access path").isEmpty();
        }
    }

    @Test
    void kinshipResetIsPostOnlyAndDelegatesToTheTransactionalService() {
        String controller = read(MAIN.resolve("controller/MemberDuplicateController.java"));
        assertThat(controller)
                .contains("@PostMapping(\"/reset-kinship\")")
                .contains("kinshipAdminService.resetVerification")
                .doesNotContain("@GetMapping(\"/reset-kinship\")")
                .doesNotContain("UPDATE members SET kinship_verified");
    }

    @Test
    void eligibilityEvaluationsAreCommandsNotGetRequests() throws IOException {
        String eligibility = read(Path.of(
                "src/main/java/com/waad/tba/modules/eligibility/controller/EligibilityController.java"));
        String unified = read(MAIN.resolve("controller/UnifiedEligibilityController.java"));
        String member = read(MAIN.resolve("controller/UnifiedMemberController.java"));

        assertThat(eligibility).contains("@PostMapping({\"/check\", \"/evaluations\"})");
        assertThat(eligibility).contains("@PostMapping(\"/family/{memberId}/evaluations\")");
        assertThat(unified).contains("@PostMapping(\"/eligibility/evaluations\")");
        assertThat(member).contains("@PostMapping(\"/eligibility/evaluations\")");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect " + path, e);
        }
    }
}
