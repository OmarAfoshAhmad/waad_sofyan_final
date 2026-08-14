package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The consumption ledger has exactly one write gate.
 *
 * Two services own the two life cycles, because their locks, idempotency keys
 * and ordering genuinely differ: BenefitBucketLedgerService for claims,
 * PreAuthReservationLedgerService for pre-authorization holds. Both append
 * through BenefitConsumptionEntryWriter, which is the only component that may
 * touch the repository. That gives the ledger a single place to enforce its
 * invariants instead of one more place to audit per new caller.
 *
 * Searching for the string "Status.RESERVED" is not enough on its own -- a
 * status can be built dynamically, and a service can write a row without
 * naming it. So these rules check the WRITE POINTS and the CALLERS together.
 */
class ConsumptionLedgerWriteGateArchitectureTest {

    private static final Path PRODUCTION = Path.of("src/main/java");

    private static final String GATE = "BenefitConsumptionEntryWriter.java";
    private static final String CLAIM_WRITER = "BenefitBucketLedgerService.java";
    private static final String PREAUTH_WRITER = "PreAuthReservationLedgerService.java";

    private List<Path> productionFiles() throws IOException {
        try (Stream<Path> files = Files.walk(PRODUCTION)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean isComment(String code) {
        return code.startsWith("*") || code.startsWith("//") || code.startsWith("/*");
    }

    /** 1. Only the gate may persist a consumption row. */
    @Test
    void onlyTheEntryWriterTouchesTheConsumptionRepository() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path path : productionFiles()) {
            if (path.getFileName().toString().equals(GATE)) {
                continue;
            }
            int lineNumber = 0;
            for (String line : Files.readString(path, StandardCharsets.UTF_8).lines().toList()) {
                lineNumber++;
                String code = line.strip();
                if (isComment(code)) {
                    continue;
                }
                if (code.contains("consumptionRepository.save")
                        || code.contains("consumptionRepository.saveAll")
                        || code.contains("consumptionRepository.flush")
                        || code.contains("consumptionRepository.delete")) {
                    violations.add(path + ":" + lineNumber + " -> " + code);
                }
            }
        }

        assertThat(violations)
                .as("""
                        Every consumption movement goes through \
                        BenefitConsumptionEntryWriter. A second write point means the \
                        ledger's invariants are enforced in one place and bypassed in \
                        another.""")
                .isEmpty();
    }

    /** 2. Reservation movements belong to the pre-authorization life cycle alone. */
    @Test
    void onlyThePreAuthLedgerServiceAppendsReservationsOrReleases() throws IOException {
        List<String> callers = callersOf("appendPreAuthReservation", "appendPreAuthRelease");

        assertThat(callers)
                .as("A reservation is the pre-authorization ledger service's life cycle, and no other's.")
                .isNotEmpty()
                .allSatisfy(caller -> assertThat(caller).contains(PREAUTH_WRITER));
    }

    /**
     * A hold that nothing can release removes limit from a member permanently,
     * with no claim to explain where it went. So the writer may only exist
     * alongside both of its exits -- this is the rule the old "no reservation
     * writer yet" guard stood in for, now stated directly.
     */
    @Test
    void theReservationWriterShipsWithBothOfItsExits() throws IOException {
        Path service = PRODUCTION.resolve(
                "com/waad/tba/modules/preauthorization/service/" + PREAUTH_WRITER);
        assertThat(Files.exists(service)).as(PREAUTH_WRITER + " must exist").isTrue();

        String source = Files.readString(service, StandardCharsets.UTF_8);
        assertThat(source).contains("approveAndReserve");
        assertThat(source).as("a hold with no cancellation path").contains("cancelAndRelease");
        assertThat(source).as("a hold with no expiry path").contains("expireAndRelease");
    }

    /** 3. Claim movements belong to the claim life cycle alone. */
    @Test
    void onlyTheClaimLedgerServiceAppendsClaimMovements() throws IOException {
        List<String> callers = callersOf("appendClaimCommit", "appendClaimReversal");

        assertThat(callers)
                .as("Claim consumption is the claim ledger service's life cycle, and no other's.")
                .allSatisfy(caller -> assertThat(caller).contains(CLAIM_WRITER));
    }

    /**
     * 4. No raw SQL write to the ledger anywhere in production. The append-only
     * triggers and shape constraints live in the database, but a native UPDATE
     * or DELETE issued from application code would still be an attempt to edit
     * a posted movement rather than compensate it -- and the intent matters
     * even where the database refuses.
     */
    @Test
    void noProductionCodeWritesTheLedgerThroughRawSql() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path path : productionFiles()) {
            int lineNumber = 0;
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (String line : source.lines().toList()) {
                lineNumber++;
                String code = line.strip();
                if (isComment(code)) {
                    continue;
                }
                String lowered = code.toLowerCase();
                boolean writesLedger = lowered.contains("benefit_bucket_consumptions")
                        && (lowered.contains("insert into")
                                || lowered.contains("update benefit_bucket_consumptions")
                                || lowered.contains("delete from"));
                if (writesLedger) {
                    violations.add(path + ":" + lineNumber + " -> " + code);
                }
            }
        }

        assertThat(violations)
                .as("""
                        Raw SQL writes to the consumption ledger belong to Flyway \
                        migrations and their dedicated tests, never to production \
                        services.""")
                .isEmpty();
    }

    /** Files (other than the gate itself) that call any of the given append operations. */
    private List<String> callersOf(String... operations) throws IOException {
        Set<String> exempt = Set.of(GATE);
        List<String> callers = new ArrayList<>();

        for (Path path : productionFiles()) {
            String fileName = path.getFileName().toString();
            if (exempt.contains(fileName)) {
                continue;
            }
            int lineNumber = 0;
            for (String line : Files.readString(path, StandardCharsets.UTF_8).lines().toList()) {
                lineNumber++;
                String code = line.strip();
                if (isComment(code)) {
                    continue;
                }
                for (String operation : operations) {
                    if (code.contains(operation)) {
                        callers.add(fileName + ":" + lineNumber + " -> " + code);
                    }
                }
            }
        }
        return callers;
    }

    /**
     * The two life cycles must not borrow each other's entry types. A claim
     * never holds limit, and a pre-authorization never consumes it -- V174's
     * source/entry-type matrix says so in the database, and this says so in
     * the code that builds the movements.
     */
    @Test
    void neitherLifeCycleCanProduceTheOthersEntryType() throws IOException {
        String gate = Files.readString(PRODUCTION.resolve(
                        "com/waad/tba/modules/benefitpolicy/service/" + GATE),
                StandardCharsets.UTF_8);

        int claimCommit = gate.indexOf("appendClaimCommit");
        int claimReversal = gate.indexOf("appendClaimReversal");
        int preauthReservation = gate.indexOf("appendPreAuthReservation");

        assertThat(claimCommit).isNotEqualTo(-1);
        assertThat(preauthReservation).isNotEqualTo(-1);

        // The claim commit operation must post COMMITTED, never RESERVED.
        String claimCommitBody = gate.substring(claimCommit, claimReversal);
        assertThat(claimCommitBody).contains("Status.COMMITTED");
        assertThat(claimCommitBody).doesNotContain("Status.RESERVED");

        // And the reservation operation must post RESERVED, never COMMITTED.
        String reservationBody = gate.substring(preauthReservation,
                gate.indexOf("appendPreAuthRelease"));
        assertThat(reservationBody).contains("Status.RESERVED");
        assertThat(reservationBody).doesNotContain("Status.COMMITTED");
    }

    /**
     * 5. Locks for the pre-authorization life cycle are taken only by
     * PreAuthLockCoordinator.
     *
     * The order Member -> PreAuthorization -> Buckets is global. A service
     * that takes its own locks can take them in its own order, and two
     * individually-correct orders are how a deadlock is built. Checking line
     * positions inside a service would test the wrong thing; centralising the
     * locks and forbidding direct acquisition tests the right one.
     */
    @Test
    void preAuthServicesDoNotAcquireLocksDirectly() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path path : productionFiles()) {
            String fileName = path.getFileName().toString();
            if (!path.toString().replace('\\', '/').contains("/preauthorization/")) {
                continue;
            }
            // A repository DECLARES the locking query; it does not acquire
            // a lock. The rule is about who calls it.
            if (fileName.equals("PreAuthLockCoordinator.java") || fileName.endsWith("Repository.java")) {
                continue;
            }
            int lineNumber = 0;
            for (String line : Files.readString(path, StandardCharsets.UTF_8).lines().toList()) {
                lineNumber++;
                String code = line.strip();
                if (isComment(code)) {
                    continue;
                }
                if (code.contains("findByIdForUpdate") || code.contains("findByIdWithLock")) {
                    violations.add(fileName + ":" + lineNumber + " -> " + code);
                }
            }
        }

        assertThat(violations)
                .as("""
                        Pre-authorization locks belong to PreAuthLockCoordinator, which \
                        owns the one global order. A service taking its own locks can \
                        take them in its own order.""")
                .isEmpty();
    }
}
