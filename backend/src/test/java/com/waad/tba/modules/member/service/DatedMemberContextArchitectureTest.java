package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Prevents dated financial/eligibility paths from returning to current pointers. */
class DatedMemberContextArchitectureTest {

    private static final List<String> DATED_DECISION_FILES = List.of(
            "modules/benefitpolicy/service/BenefitPolicyCoverageService.java",
            "modules/benefitpolicy/service/BenefitBucketLimitService.java",
            "modules/claim/service/ClaimService.java",
            "modules/claim/service/ClaimPendingServiceService.java",
            "modules/claim/service/ClaimReviewService.java",
            "modules/provider/service/ProviderClaimsService.java",
            "modules/visit/service/VisitService.java",
            "modules/eligibility/rules/MemberEnrollmentRule.java");

    @Test
    void datedDecisionsCannotReadCurrentPolicyOrEmployerPointersOrInventToday() throws Exception {
        Path root = Path.of("src/main/java/com/waad/tba");
        List<String> violations = new ArrayList<>();
        for (String relative : DATED_DECISION_FILES) {
            Path file = root.resolve(relative);
            int lineNumber = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNumber++;
                String code = line.strip();
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) continue;
                if (code.contains("member.getBenefitPolicy()")
                        || code.contains("visit.getMember().getEmployer()")
                        || code.matches(".*serviceDate.*LocalDate\\.now\\(\\).*")) {
                    violations.add(relative + ":" + lineNumber + " -> " + code);
                }
            }
        }
        assertThat(violations)
                .as("Dated decisions must use MemberContextResolver/MemberPolicyResolver with the explicit date")
                .isEmpty();
    }
}
