package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

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

    // /{id}/details is not listed here any more: the route is gone. It was a
    // second read of MemberSearchDto beside the identical endpoint on the
    // controller already marked deprecated, and no screen ever called either.
    // DATA_ENTRY reaches a member through @GetMapping("/{id}"), still asserted
    // below.
    @Test
    void dataEntryCommandRoutesHaveTheReadAndSelectorDependenciesTheyNeed() {
        String member = read(MAIN.resolve("controller/UnifiedMemberController.java"));
        String employer = read(Path.of(
                "src/main/java/com/waad/tba/modules/employer/controller/EmployerController.java"));

        assertThat(annotationBefore(member, "@GetMapping(\"/search\")"))
                .contains("@permissionGuard.has('MEMBER_VIEW')");
        assertThat(annotationBefore(member, "@GetMapping(\"/count\")"))
                .contains("@permissionGuard.has('MEMBER_VIEW')");
        assertThat(annotationBefore(member, "@GetMapping(\"/{id}\")"))
                .contains("@permissionGuard.has('MEMBER_VIEW')");
        assertThat(annotationBefore(member, "@GetMapping(\"/unified-search\")"))
                .contains("@permissionGuard.has('MEMBER_VIEW')");
        assertThat(annotationBefore(employer, "@GetMapping({ \"selectors\", \"/selector\" })"))
                .contains("@permissionGuard.has('EMPLOYER_VIEW')");
    }

    @Test
    void employerEndpointsUseCapabilitiesWithoutRoleBackdoors() {
        String controller = read(Path.of(
                "src/main/java/com/waad/tba/modules/employer/controller/EmployerController.java"));
        String importer = read(Path.of(
                "src/main/java/com/waad/tba/modules/employer/controller/EmployerImportController.java"));

        assertThat(controller)
                .contains("@permissionGuard.has('EMPLOYER_VIEW')")
                .contains("@permissionGuard.has('EMPLOYER_MANAGE')")
                .doesNotContain("hasRole(")
                .doesNotContain("hasAnyRole(");
        assertThat(importer)
                .contains("@permissionGuard.has('EMPLOYER_MANAGE')")
                .doesNotContain("hasRole(")
                .doesNotContain("hasAnyRole(");
    }

    @Test
    void memberControllersUseCapabilitiesWithoutActiveRoleBackdoors() throws IOException {
        Path controllers = MAIN.resolve("controller");
        try (var files = Files.walk(controllers)) {
            var offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).lines()
                            .map(String::trim)
                            .anyMatch(line -> line.startsWith("@PreAuthorize(\"hasRole")
                                    || line.startsWith("@PreAuthorize(\"hasAnyRole")
                                    || line.startsWith("@PreAuthorize(\"isAuthenticated")))
                    .map(Path::toString)
                    .toList();
            assertThat(offenders).as("member endpoints guarded by role names").isEmpty();
        }
    }

    @Test
    void everyActiveMemberAndEmployerRouteHasAnExplicitMethodCapabilityGuard() throws IOException {
        var roots = java.util.List.of(
                MAIN.resolve("controller"),
                Path.of("src/main/java/com/waad/tba/modules/employer/controller"));
        var unguarded = new ArrayList<String>();

        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    var lines = read(path).lines().toList();
                    for (int index = 0; index < lines.size(); index++) {
                        String line = lines.get(index).trim();
                        if (!isActiveMapping(line)) {
                            continue;
                        }
                        boolean guarded = false;
                        for (int cursor = index + 1; cursor < lines.size(); cursor++) {
                            String candidate = lines.get(cursor).trim();
                            if (candidate.startsWith("@PreAuthorize(\"@permissionGuard.has('")) {
                                guarded = true;
                            }
                            if (candidate.startsWith("public ")) {
                                break;
                            }
                        }
                        if (!guarded) {
                            unguarded.add(path + ":" + (index + 1) + " " + line);
                        }
                    }
                });
            }
        }

        assertThat(unguarded).as("active routes without an explicit effective-permission guard").isEmpty();
    }

    private static boolean isActiveMapping(String line) {
        return line.startsWith("@GetMapping")
                || line.startsWith("@PostMapping")
                || line.startsWith("@PutMapping")
                || line.startsWith("@PatchMapping")
                || line.startsWith("@DeleteMapping");
    }

    private static String annotationBefore(String source, String mapping) {
        int mappingIndex = source.indexOf(mapping);
        if (mappingIndex < 0) {
            throw new IllegalStateException("Mapping not found: " + mapping);
        }
        // In these controllers PreAuthorize follows the mapping; inspect the
        // compact route block rather than relying on the order of annotations.
        return source.substring(mappingIndex, Math.min(source.length(), mappingIndex + 320));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect " + path, e);
        }
    }
}
