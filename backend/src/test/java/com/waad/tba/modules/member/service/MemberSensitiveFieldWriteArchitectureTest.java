package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extends MemberStatusWriteArchitectureTest (status/active) to the rest of the
 * fields whose change must be a dedicated, audited operation rather than a
 * field assignment: the benefit policy, the employer, and the family
 * structure.
 *
 * A new centralized service is worth nothing while an old back door still
 * bypasses it -- this test is what makes "there is exactly one way to change
 * X" a property the build enforces instead of a convention people remember.
 *
 * Deliberately a source scan rather than ArchUnit: this repository has no
 * ArchUnit dependency, and adding one to enforce four setters would be a
 * heavier commitment than the rule warrants. The trade-off is that a rename
 * of a setter silently disarms the check -- so the allow-lists below name the
 * exact owning service for each field, and any new writer must be added here
 * consciously.
 */
class MemberSensitiveFieldWriteArchitectureTest {

    /** setter -> the only files allowed to call it, and why. */
    private static final Map<String, Set<String>> ALLOWED_WRITERS = Map.of(
            ".setBenefitPolicy(", Set.of(
                    // The policy assignment record is the source of truth; this
                    // service is the only thing that may sync the denormalized
                    // pointer alongside writing an assignment row.
                    "MemberPolicyResolver.java",
                    // Creation paths set it before the first assignment exists;
                    // both immediately record an assignment through the resolver.
                    "UnifiedMemberService.java",
                    "MemberImportRowProcessor.java",
                    // Undoes exactly what MemberImportRowProcessor (already
                    // allowed above) wrote, restoring the value captured in a
                    // MemberImportFieldSnapshot at that same write -- not a new
                    // state-change vector, the inverse half of an existing one.
                    "MemberImportRollbackService.java"),
            ".setEmployer(", Set.of(
                    // Creation only. Moving an existing member between employers
                    // is a separate operation (not yet implemented) -- see
                    // UnifiedMemberService.rejectSensitiveFieldChanges.
                    "UnifiedMemberService.java",
                    "MemberImportRowProcessor.java",
                    "MemberExcelImportService.java",
                    "MemberImportRollbackService.java"),
            ".setParent(", Set.of(
                    // Family structure. Creation and import only; moving a
                    // dependent between principals is a separate operation.
                    "UnifiedMemberService.java",
                    "MemberImportRowProcessor.java",
                    // Duplicate-merge re-parents the duplicate's dependents onto
                    // the surviving primary; a dedicated operation, not a field edit.
                    "MemberDuplicateService.java",
                    "MemberImportRollbackService.java"),
            ".setRelationship(", Set.of(
                    "UnifiedMemberService.java",
                    "MemberImportRowProcessor.java",
                    // Dedicated, purpose-built correction service for kinship /
                    // gender mismatches -- exactly the "separate operation"
                    // shape this rule asks for, not a generic back door.
                    "KinshipMismatchService.java",
                    "MemberImportRollbackService.java"));

    @org.junit.jupiter.api.Test
    void sensitiveMemberFieldsAreOnlyWrittenByTheirOwningService() throws Exception {
        Path root = Path.of("src/main/java/com/waad/tba/modules/member");
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fileName = file.getFileName().toString();
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (var entry : ALLOWED_WRITERS.entrySet()) {
                    String setter = entry.getKey();
                    if (entry.getValue().contains(fileName)) {
                        continue;
                    }
                    // Only entity-side writes matter. "dto.setRelationship(...)"
                    // populates a response object and is not a state change.
                    boolean writesEntity = source.lines()
                            .filter(line -> line.contains(setter))
                            .anyMatch(line -> !line.contains("dto.set") && !line.contains("Dto.set"));
                    if (writesEntity) {
                        violations.add(fileName + " calls " + setter);
                    }
                }
            }
        }

        assertThat(violations)
                .as("Changing a member's policy, employer or family structure must go through its "
                        + "dedicated operation, not a direct setter. Unexpected writers: " + violations)
                .isEmpty();
    }

    /**
     * The generic update path must never grow the ability to change a
     * sensitive field back. Guards the specific regression this closed: PUT
     * /{id} applied employerId/benefitPolicyId directly, and the mapper
     * silently dropped status/active.
     */
    @org.junit.jupiter.api.Test
    void theGenericUpdateMapperCopiesNoSensitiveField() throws Exception {
        String mapper = Files.readString(
                Path.of("src/main/java/com/waad/tba/modules/member/mapper/UnifiedMemberMapper.java"),
                StandardCharsets.UTF_8);
        int updateStart = mapper.indexOf("public void updateEntityFromDto");
        assertThat(updateStart).as("updateEntityFromDto must exist").isGreaterThan(-1);
        String updateBody = mapper.substring(updateStart, mapper.indexOf("\n    }", updateStart));

        for (String forbidden : List.of(".setStatus(", ".setActive(", ".setBenefitPolicy(",
                ".setEmployer(", ".setParent(", ".setRelationship(", ".setCardNumber(", ".setBarcode(")) {
            assertThat(updateBody)
                    .as("updateEntityFromDto must not copy " + forbidden)
                    .doesNotContain(forbidden);
        }
    }
}
