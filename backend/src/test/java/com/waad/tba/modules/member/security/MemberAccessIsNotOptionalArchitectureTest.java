package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Production code must not be able to ask for a decision and ignore it.
 *
 * forListing/forMember return a value Java is happy to discard, so a call
 * site that forgets to check it proceeds as if allowed. requireListing and
 * requireMember throw instead, and hand back the scope the query needs -- the
 * permission and the filter are the same object, so the check cannot be
 * skipped without the query having nothing to filter on.
 *
 * Those raw methods are package-private, which the compiler enforces. This
 * test guards the softer half: that nobody re-widens them, and that the
 * employer id a client sends is never read straight into a filter.
 */
class MemberAccessIsNotOptionalArchitectureTest {

    private static final Path PRODUCTION = Path.of("src/main/java");
    private static final Path POLICY_PACKAGE =
            Path.of("src/main/java/com/waad/tba/modules/member/security");

    private List<Path> productionFiles() throws IOException {
        try (Stream<Path> files = Files.walk(PRODUCTION)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean isComment(String code) {
        return code.startsWith("*") || code.startsWith("//") || code.startsWith("/*");
    }

    @Test
    void theIgnorableDecisionMethodsStayInsideThePolicyPackage() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path path : productionFiles()) {
            if (path.toAbsolutePath().startsWith(POLICY_PACKAGE.toAbsolutePath())) {
                continue;
            }
            int lineNumber = 0;
            for (String line : Files.readString(path, StandardCharsets.UTF_8).lines().toList()) {
                lineNumber++;
                String code = line.strip();
                if (isComment(code)) {
                    continue;
                }
                if (code.contains(".forListing(") || code.contains(".forMember(")) {
                    violations.add(path.getFileName() + ":" + lineNumber + " -> " + code);
                }
            }
        }

        assertThat(violations)
                .as("""
                        Call requireListing/requireMember. The raw decision can be \
                        produced and dropped, and a dropped authorisation check reads \
                        exactly like a granted one.""")
                .isEmpty();
    }

    @Test
    void theRawDecisionMethodsAreNotPublic() throws IOException {
        String source = Files.readString(
                POLICY_PACKAGE.resolve("MemberQueryAccessPolicy.java"), StandardCharsets.UTF_8);

        assertThat(source)
                .as("forListing must stay package-private so the compiler enforces this")
                .doesNotContain("public MemberAccessDecision forListing");
        assertThat(source)
                .doesNotContain("public MemberAccessDecision forMember");
        assertThat(source).contains("public AuthorizedMemberScope requireListing");
        assertThat(source).contains("public AuthorizedMemberScope requireMember");
    }
}
