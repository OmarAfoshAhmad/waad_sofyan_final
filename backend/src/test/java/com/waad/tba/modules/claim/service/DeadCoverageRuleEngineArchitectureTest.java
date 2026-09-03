package com.waad.tba.modules.claim.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fake admin surface, not merely dead code, must not come back.
 *
 * <p>claim.ruleengine was a complete vertical -- entity, repository, an admin
 * CRUD controller at /api/v1/admin/claim-coverage-rules, a frontend service, and
 * a settings tab -- that persisted rows to claim_coverage_rules and never once
 * fed CoverageEngineService or CoverageDecisionService, the two places a claim's
 * coverage is actually decided. An administrator could create, edit and enable
 * rules through a working screen that saved successfully and changed nothing.
 * That is a worse failure than an unreachable code path: it is a control that
 * looks live and is not.
 *
 * <p>These checks read the source tree rather than run the app, because the
 * defect they guard against is a package existing at all, not a behaviour a
 * runtime test could exercise. The tables the old rules lived in
 * (claim_coverage_rules, claim_rule_execution_audit) are deliberately left in
 * place here -- removing them is a separate, later migration, done only after
 * confirming no production data needs preserving first.
 */
class DeadCoverageRuleEngineArchitectureTest {

    private static final Path BACKEND_MAIN = Paths.get("src/main/java/com/waad/tba");
    private static final Path FRONTEND_SRC = Paths.get("../frontend/src");

    @Test
    void theRuleEnginePackageDoesNotExist() throws IOException {
        Path pkg = BACKEND_MAIN.resolve("modules/claim/ruleengine");

        assertThat(Files.exists(pkg))
                .as("claim.ruleengine was a fully wired admin surface with no effect on "
                        + "coverage decisions; it must be removed, not left as a parallel path")
                .isFalse();
    }

    @Test
    void noSourceFileReferencesTheAdminEndpoint() throws IOException {
        assertThat(grep(BACKEND_MAIN, ".java", "/admin/claim-coverage-rules"))
                .as("the admin CRUD endpoint for the disconnected rule engine must not be reachable")
                .isEmpty();
    }

    @Test
    void frontendDoesNotImportTheDisconnectedRuleEngineService() throws IOException {
        if (!Files.isDirectory(FRONTEND_SRC)) {
            return; // architecture test running from a context without the frontend checked out
        }
        assertThat(grep(FRONTEND_SRC, ".js", "coverageRuleEngine"))
                .as("the frontend service for the disconnected rule engine must not be reintroduced")
                .isEmpty();
        assertThat(grep(FRONTEND_SRC, ".jsx", "coverageRuleEngine"))
                .isEmpty();
    }

    @Test
    void theOrphanedSettingsTabDoesNotExist() {
        if (!Files.isDirectory(FRONTEND_SRC)) {
            return;
        }
        Path tab = FRONTEND_SRC.resolve("pages/settings/FinancialRuleEngineTab.jsx");

        assertThat(Files.exists(tab))
                .as("this tab was never wired into any route or parent page -- a genuinely live "
                        + "settings screen for coverage configuration belongs behind CoverageEngineService, "
                        + "not resurrected under its old name")
                .isFalse();
    }

    private List<Path> grep(Path root, String extension, String needle) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .filter(p -> containsSafely(p, needle))
                    .toList();
        }
    }

    private boolean containsSafely(Path file, String needle) {
        try {
            return Files.readString(file).contains(needle);
        } catch (IOException e) {
            return false;
        }
    }
}
