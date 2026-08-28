package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A balance a person reads must keep its sign.
 *
 * Clamping a remaining balance at zero collapses two different facts into one
 * number: a member who has spent exactly their ceiling and a member who has
 * overspent it read identically, and the second is the case anyone looking at
 * the screen is there to find. Three separate places had done it -- the member
 * financial summary, the coverage service, and the provider portal -- and each
 * looked locally harmless.
 *
 * The rule is narrow on purpose. {@code .max(BigDecimal.ZERO)} is correct in
 * plenty of places: a refused amount cannot be negative, neither can a
 * remaining deductible. It is wrong on the files below, which exist to tell
 * someone how much of a ceiling is left.
 */
class BalanceDisplayIsNotClampedArchitectureTest {

    /**
     * The paths that answer "how much of this member's ceiling is left". Named
     * rather than pattern-matched: a list someone must edit deliberately is
     * the point, and a new balance-display path should be added here as part
     * of writing it.
     */
    private static final List<String> BALANCE_DISPLAY_PATHS = List.of(
            "modules/member/service/MemberFinancialSummaryService.java",
            "modules/member/service/MemberLimitOverviewService.java",
            "modules/benefitpolicy/service/LimitBalanceReader.java",
            "modules/benefitpolicy/service/GeneralCeilingReading.java",
            "modules/provider/service/ProviderPortalService.java",
            "modules/provider/service/ProviderClaimsService.java");

    private static final Pattern CLAMP = Pattern.compile(
            "\\.max\\(\\s*(java\\.math\\.)?BigDecimal\\.ZERO\\s*\\)");

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/waad/tba");

    @Test
    @DisplayName("no balance-display path clamps a remaining figure at zero")
    void noBalanceDisplayPathClampsAtZero() throws IOException {
        List<String> offences = new ArrayList<>();

        for (String relative : BALANCE_DISPLAY_PATHS) {
            Path file = SOURCE_ROOT.resolve(relative);
            assertThat(file)
                    .as("%s is on the balance-display list but does not exist; move or "
                            + "rename it here rather than leaving the rule pointing at nothing",
                            relative)
                    .exists();

            String source = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
            Matcher matcher = CLAMP.matcher(source);
            while (matcher.find()) {
                offences.add(relative + " -> " + contextAround(source, matcher.start()));
            }
        }

        assertThat(offences)
                .as("a remaining balance shown to a person must keep its sign; an "
                        + "overspend clamped to zero is indistinguishable from an "
                        + "exactly-spent ceiling, and that is the case the reader is "
                        + "looking for")
                .isEmpty();
    }

    /**
     * Strips comments and string literals, so this test cannot be tripped by
     * its own explanation of what it forbids -- a guard test that fails on
     * prose teaches people to soften the prose.
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

    private static String contextAround(String source, int index) {
        int from = Math.max(0, source.lastIndexOf('\n', index) + 1);
        int to = source.indexOf('\n', index);
        return source.substring(from, to < 0 ? source.length() : to).trim();
    }
}
