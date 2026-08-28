package com.waad.tba.modules.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A field kept only so an existing contract does not break must not acquire
 * new readers.
 *
 * {@code remainingCoverage} is retained as an unclamped alias of
 * {@code actualRemaining} while external consumers migrate, and the three
 * claim-axis money fields are retained as nulls. Every one of them is a name
 * that reads as trustworthy, which is exactly how the old totalPaid came to be
 * relied on. Deprecation alone does not stop that -- an annotation is a
 * suggestion, and a compiler warning in a codebase with warnings is invisible.
 *
 * So the rule is mechanical: nothing under src/main may read them. When the
 * next API version drops the fields, this test and the fields go together.
 * See docs/DEPRECATED_API_FIELDS.md for the removal plan.
 */
class DeprecatedSummaryFieldsHaveNoConsumersTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/waad/tba");

    private static final String OWNING_TYPE = "MemberFinancialSummaryDto";

    /**
     * The accessor, and why nobody may call it. The message is the point: a
     * failure has to tell whoever hit it what to use instead, or they will
     * reach for the shortest way to make it green.
     */
    private static final Map<String, String> FORBIDDEN_READS = Map.of(
            "getRemainingCoverage()",
            "ambiguous by name; say which you mean -- getActualRemaining() for the "
                    + "accounting figure, getReservableAvailable() for any decision about a "
                    + "new commitment",
            "getTotalPaid()",
            "never a disbursement; payments are recorded per (employer, provider, year, "
                    + "month) and carry no member, so there is no per-member figure to read",
            "getTotalApproved()",
            "the claim-approval axis, routinely mistaken for the ceiling axis; use "
                    + "getLimitConsumedAmount() for consumption, or ask the claims module",
            "getTotalClaimed()",
            "the claim-approval axis; ask the claims module for claim totals");

    /**
     * The class that owns the fields. Its own builder and field declarations
     * naturally mention them, and MemberFinancialSummaryService is what nulls
     * them -- neither is a consumer.
     */
    private static final List<String> DECLARATION_SITES = List.of(
            "modules/member/dto/MemberFinancialSummaryDto.java",
            "modules/member/service/MemberFinancialSummaryService.java");

    @Test
    @DisplayName("no production code reads a deprecated member-summary money field")
    void noProductionCodeReadsADeprecatedField() throws IOException {
        List<String> offences = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (DECLARATION_SITES.contains(relative)) {
                    continue;
                }
                String source = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
                // Only files that actually handle the DTO. The accessor names
                // are not unique -- ProviderAccount has a real getTotalPaid()
                // backed by an actual payment ledger, and the claim projection
                // declares its own -- and a rule that cannot tell them apart
                // would fail on correct code until someone deleted the rule.
                if (!source.contains(OWNING_TYPE)) {
                    continue;
                }
                for (var forbidden : FORBIDDEN_READS.entrySet()) {
                    if (source.contains(forbidden.getKey())) {
                        offences.add(relative + " reads " + forbidden.getKey()
                                + " -- " + forbidden.getValue());
                    }
                }
            }
        }

        assertThat(offences)
                .as("these fields exist only to keep an existing response shape valid "
                        + "until the next API version removes them; a new reader turns a "
                        + "scheduled deletion into a migration")
                .isEmpty();
    }

    /**
     * Strips comments and string literals so the rule cannot be tripped by
     * prose describing it -- including this file's own explanations, were it
     * ever to live under src/main.
     */
    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder();
        for (String line : withoutBlockComments.split("\n", -1)) {
            int comment = line.indexOf("//");
            out.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return out.toString().replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
