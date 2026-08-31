package com.waad.tba.modules.benefitpolicy.service;

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
 * A MEMBER's ceiling comes from LimitBalanceReader. Nothing else may compute
 * one from BenefitPolicy.getAnnualLimit().
 *
 * This rule was written after breaking it twice in one change.
 *
 * The uplift feature added an exceptional, per-member increase and wired it
 * into LimitBalanceReader.readGeneralCeilingBulk -- the read the members list
 * uses. It was not wired into readGeneralCeiling, the single read every
 * DECISION uses: what a claim may consume, what a pre-authorization may hold,
 * whether an approval still fits. A member granted an exception therefore saw
 * a raised ceiling on every screen and was refused the moment they spent past
 * the policy figure.
 *
 * Then MemberFinancialSummaryService was found reporting annualLimit from the
 * policy while the balances beside it came from the effective ceiling, so the
 * same member was reported with a remaining balance larger than their limit --
 * and that field feeds /remaining-limit, which the provider portal reads while
 * a claim is being entered.
 *
 * Neither was carelessness about a new feature. Both are what happens when a
 * value that used to be a simple column becomes a computed thing and the
 * places that read the column are not enumerable. Putting the rule in the
 * reader made the correct path the obvious one; this makes it the only one.
 *
 * WHAT IS ALLOWED, and why each is not a member ceiling:
 *
 *   - the policy module writing, validating or displaying a policy's OWN limit
 *   - importing a policy, where the number is the input being parsed
 *   - the reader itself, which is where the rule lives
 */
class MemberCeilingComesFromOneReaderArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/waad/tba");

    /**
     * Files permitted to read a policy's annual limit directly, each because
     * what it does with the number is not "this member's ceiling".
     *
     * An entry here is a claim about a file. Adding one without being able to
     * say which of the three reasons above applies is how the rule dies.
     */
    private static final Set<String> ALLOWED = Set.of(
            // The reader. The rule lives here.
            "LimitBalanceReader.java",
            // The policy's own lifecycle: setting, validating and showing the
            // number the policy grants its members.
            "BenefitPolicyService.java",
            "BenefitBucketLimitService.java",
            "ApplicableLimitResolver.java",
            // Reads it to hand straight to LimitBalanceReader, which is the
            // correct path -- the reader adds the uplift to what it is given.
            "BenefitPolicyCoverageService.java",
            "BenefitBucketLedgerService.java",
            // Parses the column out of an uploaded policy sheet.
            "EmployerImportRowProcessor.java",
            // Reads the policy figure to HAND to the reader, which adds the
            // uplift to what it is given -- the correct path, not a bypass.
            "MemberLimitOverviewService.java",
            // Falls back to the policy figure only where the ceiling read
            // reports no figures at all (UNLIMITED / NOT_CONFIGURED), which is
            // a mode with no effective ceiling to differ from.
            "MemberFinancialSummaryService.java",
            // Both read the policy figure to answer one question -- "is there a
            // ceiling at all?" -- and take every figure from the reader after
            // that. Verified by reading them, not assumed: ProviderClaimsService
            // decides on ceiling.reservableAvailable() and ProviderPortalService
            // displays ceiling.limit(). Neither computes a member ceiling.
            "ProviderClaimsService.java",
            "ProviderPortalService.java",
            // Same category, and verified the same way rather than assumed:
            // it reads the policy figure to hand to the reader and to answer
            // "is there a ceiling at all?" (ceilingMode). Every figure it
            // reports -- annualLimit, committed, reserved, actualRemaining,
            // reservableAvailable -- is taken off the reader's result, so no
            // member ceiling is computed here.
            "ClaimEntryContextService.java");

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /**
     * Reading the limit OFF A POLICY is the thing being banned -- not reading a
     * field called annualLimit off something that already computed one.
     *
     * MemberFinancialSummaryDto.getAnnualLimit() is the effective ceiling by
     * the time anyone reads it, and BenefitPolicyResponseDto showing a
     * policy's own number is the policy screen doing its job. Flagging those
     * would make the rule noise, and a rule that cries wolf is a rule someone
     * eventually adds an exclusion to rather than obeying.
     */
    private static boolean readsTheLimitOffAPolicy(String line) {
        if (!line.contains("getAnnualLimit()") && !line.contains(".annualLimit()")) {
            return false;
        }
        // The receiver, immediately before the call.
        for (String call : new String[] { "getAnnualLimit()", "annualLimit()" }) {
            int at = line.indexOf(call);
            while (at > 0) {
                String before = line.substring(0, at);
                int dot = before.lastIndexOf('.', before.length() - 2);
                String receiver = dot < 0 ? before : before.substring(dot + 1);
                receiver = receiver.replace(".", "").trim();
                if (receiver.toLowerCase(java.util.Locale.ROOT).endsWith("policy")
                        || receiver.toLowerCase(java.util.Locale.ROOT).endsWith("policy()")) {
                    return true;
                }
                at = line.indexOf(call, at + 1);
            }
        }
        return false;
    }

    @Test
    @DisplayName("no member ceiling is computed from a policy's annual limit outside the reader")
    void theCeilingHasOneSource() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (ALLOWED.contains(name)) {
                    continue;
                }
                String code = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
                for (String line : code.lines().toList()) {
                    if (readsTheLimitOffAPolicy(line)) {
                        violations.add(file.getFileName() + ": " + line.trim());
                    }
                }
            }
        }

        assertThat(violations)
                .as("a member's ceiling is the policy limit PLUS any exceptional uplift granted to "
                        + "them; reading the policy column directly produces the wrong number for "
                        + "exactly the members an administrator went out of their way to help, and "
                        + "produces it silently")
                .isEmpty();
    }

    @Test
    @DisplayName("both reads in the reader resolve the uplift -- one place, not one per method")
    void everyEntryPointOfTheReaderAddsTheUplift() throws IOException {
        String reader = codeOnly(Files.readString(
                SOURCE_ROOT.resolve("modules/benefitpolicy/service/LimitBalanceReader.java"),
                StandardCharsets.UTF_8));

        // The first bug was not that the rule was in the wrong place. It was
        // that the right place had two doors and only one of them was fixed.
        long entryPoints = reader.lines()
                .filter(line -> line.contains("public") && line.contains("readGeneralCeiling"))
                .count();
        long upliftResolutions = reader.lines()
                .filter(line -> line.contains("upliftInForce("))
                .count();

        assertThat(entryPoints).as("the reader's public ceiling reads").isGreaterThanOrEqualTo(2);
        assertThat(upliftResolutions)
                .as("every public ceiling read resolves the uplift; a door that skips it returns a "
                        + "different member's ceiling with nothing to show it")
                .isGreaterThanOrEqualTo(entryPoints);
    }
}
