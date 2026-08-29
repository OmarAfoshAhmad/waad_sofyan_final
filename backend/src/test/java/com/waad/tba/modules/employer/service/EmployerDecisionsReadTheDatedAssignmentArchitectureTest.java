package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A DECISION about a member's employer reads member_employer_assignments.
 * Only a DISPLAY may read the members.employer_id pointer.
 *
 * The pointer is not the problem and is not being banned. It is a
 * denormalised current value, MemberEmployerResolver keeps it in step, and a
 * dashboard counting heads or a response DTO showing "42 members" is entitled
 * to it -- those are cheap reads of something that is true right now, and
 * being one transaction stale costs nobody anything.
 *
 * What the pointer must not decide is anything that ends, blocks, terminates
 * or validates. Three of those were found in one afternoon:
 *
 *   EmployerService.archive     -- whether an employer may be archived
 *   EmployerService.update      -- whether a member cap may be lowered
 *   MemberExcelImportService    -- WHO GETS TERMINATED by a replacement import
 *
 * The last one is the shape of the risk. It decided a destructive write from
 * a cache of the answer, and a member whose pointer had drifted would be
 * missed by a replacement that should have ended them -- or ended by one that
 * should not have touched them -- with the file looking correctly applied
 * either way.
 *
 * None of the three was reachable as a bug today, because one writer keeps
 * the pointer and the assignments in step. That is the point: the agreement
 * was load-bearing and nothing enforced it.
 */
class EmployerDecisionsReadTheDatedAssignmentArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/waad/tba");

    /** Reads of the pointer through the member repository. */
    private static final List<String> POINTER_READS = List.of(
            "memberRepository.countByEmployerId",
            "memberRepository.findByEmployerId",
            "memberRepository.existsByEmployerId",
            "memberRepository.countByEmployerIdAndActiveTrue");

    /**
     * Files allowed to read the pointer, each because what it does with the
     * number is DISPLAY -- it is shown, not acted on.
     *
     * An entry here is a claim that nothing downstream decides anything from
     * it. Adding one without checking that is how this rule stops meaning
     * anything.
     */
    private static final Set<String> DISPLAY_ONLY = Set.of(
            // Dashboard head-counts and statistics tiles.
            "DashboardService.java",
            // membersCount on the employer response DTO.
            "EmployerMapper.java",
            // A javadoc example, not a call -- but the example teaches the
            // pattern, so it is named here rather than silently skipped.
            "DeletionGuard.java");

    // MedicalAuditLogController is deliberately NOT allow-listed. Its filter
    // asks a third question -- who was EVER with this employer -- and it now
    // asks it of findMemberIdsEverAssignedTo. Exempting it instead would have
    // left an audit trail quietly incomplete for everyone who had left, which
    // is most of what an audit gets opened to find.

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    @Test
    @DisplayName("nothing decides a member's employer from the current pointer")
    void decisionsDoNotReadThePointer() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (DISPLAY_ONLY.contains(file.getFileName().toString())) {
                    continue;
                }
                String code = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
                for (String line : code.lines().toList()) {
                    for (String read : POINTER_READS) {
                        if (line.contains(read)) {
                            violations.add(file.getFileName() + ": " + line.trim());
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("members.employer_id is a denormalised current pointer. Ask "
                        + "MemberEmployerAssignmentRepository.countActiveMembersAssignedOn or "
                        + "findMemberIdsAssignedOn -- their names say which date they answer, so the "
                        + "next person cannot reach for one and get the other's answer")
                .isEmpty();
    }

    @Test
    @DisplayName("the dated reads exist and are named for the date they answer")
    void theAuthoritativeReadsAreNamedForTheirDate() throws IOException {
        String repository = Files.readString(
                SOURCE_ROOT.resolve("modules/member/repository/MemberEmployerAssignmentRepository.java"),
                StandardCharsets.UTF_8);

        // Deliberately asserting on the NAMES. A method called countByEmployer
        // would be reached for by someone asking a different question, and
        // they would get this one's answer with nothing to warn them.
        assertThat(repository)
                .contains("countActiveMembersAssignedOn")
                .contains("findMemberIdsAssignedOn")
                .contains("hasEverHadAnAssignedMember");

        assertThat(codeOnly(repository))
                .as("\"does anyone belong now\" and \"was anyone ever here\" are different questions, "
                        + "and neither may be answered with the other's query")
                .doesNotContain("countByEmployer(")
                .doesNotContain("existsByEmployer(");
    }
}
