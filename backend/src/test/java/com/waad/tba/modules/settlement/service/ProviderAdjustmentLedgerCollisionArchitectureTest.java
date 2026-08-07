package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the exact ambiguity a live reviewer caught in this account's own
 * design: {@code AccountTransaction.createAdjustment}/{@code ReferenceType.ADJUSTMENT}
 * is not one thing. It is used for two structurally different purposes that must
 * never be able to grow a third caller by accident:
 *
 * 1. Real, pre-existing money movement with no relation to the new
 *    {@code ProviderPayment} model ({@code handleClaimAmountAdjusted}, claim
 *    amount corrections). Legitimate, still active, out of this test's scope to
 *    change.
 * 2. The now-frozen legacy payment paths ({@code debitOnInstallmentPayment},
 *    {@code settleRemainingBalanceByProvider}), which used to raise totalPaid
 *    through the same untyped entry, invisible to reconciliation's ledgerNet.
 *
 * {@code ProviderAccountAdjustmentService} (Phase 7's reconciliation correction)
 * deliberately does NOT call this factory at all — see
 * {@code ProviderAccountReconciliationAudit}. If it ever did again, the
 * correction would recreate the exact drift it was meant to close on the very
 * next reconciliation read. This test fails loudly if any new call site appears
 * anywhere in the source tree without a deliberate update to the allowlist below.
 */
class ProviderAdjustmentLedgerCollisionArchitectureTest {

    /**
     * Every source file allowed to call {@code AccountTransaction.createAdjustment}
     * or {@code AccountTransactionService.createAdjustment}. Adding to this list
     * is a financial-architecture decision, not a mechanical fix — it means a new
     * code path will raise totalPaid in a way reconciliation's ledgerNet cannot
     * see.
     */
    private static final Set<String> ALLOWED_CREATE_ADJUSTMENT_CALLERS = Set.of(
            // Entity: hosts the factory itself; ProviderAccount.java: two frozen
            // methods below still reference it in Javadoc only, not in code.
            "AccountTransaction.java",
            // Service-level wrapper used by the callers below.
            "AccountTransactionService.java",
            // handleClaimAmountAdjusted: legitimate claim-amount correction,
            // unrelated to provider payments — out of scope for this phase.
            "ProviderAccountService.java");

    @Test
    void createAdjustmentHasNoCallersOutsideTheKnownAllowlist() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        assertThat(sourceRoot).as("backend source root must exist").exists();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fileName = file.getFileName().toString();
                if (ALLOWED_CREATE_ADJUSTMENT_CALLERS.contains(fileName)) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("createAdjustment(") || source.contains("ReferenceType.ADJUSTMENT")) {
                    violations.add(sourceRoot.relativize(file).toString());
                }
            }
        }

        assertThat(violations)
                .as("New callers of the generic ADJUSTMENT ledger entry were found. Decide explicitly: "
                        + "is this real money movement (extend ledgerNet's definition) or a non-financial "
                        + "audit trace (use ProviderAccountReconciliationAudit, never account_transactions)? "
                        + "Then add the file here only if the first is genuinely intended.")
                .isEmpty();
    }

    @Test
    void reconciliationCorrectionServiceNeverReferencesAccountTransaction() throws IOException {
        Path file = Paths.get("src", "main", "java", "com", "waad", "tba", "modules",
                "settlement", "service", "ProviderAccountAdjustmentService.java");
        assertThat(file).exists();
        String source = Files.readString(file, StandardCharsets.UTF_8);

        assertThat(source)
                .as("The reconciliation correction must never write to account_transactions — "
                        + "doing so would let the correction recreate the drift it closes on the next "
                        + "reconciliation read. It must use ProviderAccountReconciliationAudit instead.")
                .doesNotContain("AccountTransaction")
                .doesNotContain("createAdjustment");
    }
}
