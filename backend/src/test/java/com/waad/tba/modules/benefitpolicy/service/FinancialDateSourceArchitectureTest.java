package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Two rules the financial paths must keep, enforced by the build rather than
 * by memory:
 *
 * 1. A financial decision never reads members.benefit_policy_id. That pointer
 *    is a denormalized "current policy" convenience for list screens; using it
 *    to decide money means a backdated claim is priced with today's limits.
 *    MemberPolicyResolver, keyed on the service date, is the only permitted
 *    source.
 *
 * 2. A financial decision never calls LocalDate.now(). Substituting "today"
 *    for a missing service date is the same defect wearing a different hat --
 *    it silently turns "when this was entered" into "when it happened".
 *
 * Exceptions are listed explicitly below with a reason, so an addition is a
 * conscious act rather than an oversight.
 */
class FinancialDateSourceArchitectureTest {

    /** Files whose decisions move money or consume a limit. */
    private static final List<String> FINANCIAL_DECISION_FILES = List.of(
            "src/main/java/com/waad/tba/modules/claim/service/finance/ClaimFinancialAdjudicationService.java",
            "src/main/java/com/waad/tba/modules/claim/service/CostCalculationService.java",
            "src/main/java/com/waad/tba/modules/claim/service/ClaimFinancialSnapshotService.java",
            "src/main/java/com/waad/tba/modules/benefitpolicy/service/BenefitBucketLedgerService.java",
            "src/main/java/com/waad/tba/modules/benefitpolicy/service/EffectiveLimitResolver.java");

    /**
     * Files still permitted to call LocalDate.now(), each for a stated reason.
     * Anything not listed must take its date from the operation being decided.
     */
    private static final Set<String> NOW_ALLOWED = Set.of(
            // Presents "current policy" for display-only screens and keeps the
            // legacy no-date overloads working; its money-deciding methods
            // (validateAmountLimits, getRemainingCoverage, validateClaimCoverage)
            // all take an explicit date.
            "BenefitPolicyCoverageService.java");

    @Test
    void financialDecisionsNeverReadTheDenormalizedPolicyPointer() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String file : FINANCIAL_DECISION_FILES) {
            Path path = Path.of(file);
            assertThat(Files.exists(path)).as(file + " must exist").isTrue();
            String source = Files.readString(path, StandardCharsets.UTF_8);
            source.lines().forEach(line -> {
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("//")) {
                    return; // a comment explaining the old behaviour is not a call
                }
                if (code.contains(".getBenefitPolicy()")) {
                    violations.add(path.getFileName() + ": " + code);
                }
            });
        }
        assertThat(violations)
                .as("Financial decisions must resolve the policy by service date via "
                        + "MemberPolicyResolver, never via members.benefit_policy_id: " + violations)
                .isEmpty();
    }

    @Test
    void financialDecisionsNeverSubstituteTodayForAMissingServiceDate() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String file : FINANCIAL_DECISION_FILES) {
            Path path = Path.of(file);
            if (NOW_ALLOWED.contains(path.getFileName().toString())) {
                continue;
            }
            String source = Files.readString(path, StandardCharsets.UTF_8);
            source.lines().forEach(line -> {
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("//")) {
                    return;
                }
                if (code.contains("LocalDate.now()")) {
                    violations.add(path.getFileName() + ": " + code);
                }
            });
        }
        assertThat(violations)
                .as("A financial decision must take its date from the claim/visit/pre-authorization "
                        + "being decided, never from the clock: " + violations)
                .isEmpty();
    }
}
