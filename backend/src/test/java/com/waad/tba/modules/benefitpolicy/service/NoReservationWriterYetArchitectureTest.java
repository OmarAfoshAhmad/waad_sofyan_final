package com.waad.tba.modules.benefitpolicy.service;

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
 * V174 taught the ledger to REPRESENT a pre-authorization hold. It did not
 * authorize anyone to write one.
 *
 * The distinction matters because a reservation is money removed from a
 * member's available limit before any claim exists. Writing one without the
 * approval snapshot and the atomic approval step means a hold can be created
 * that nothing releases: the member loses limit permanently and no claim ever
 * explains where it went. Representation had to land first so the approval
 * service has somewhere to write; this test is the fence that keeps the gap
 * between the two steps from being filled by accident.
 *
 * When the approval service arrives it will be the first legitimate writer,
 * and it must be added to WRITERS_ALLOWED in the same commit that introduces
 * it -- deliberately, with its release path and expiry path present.
 */
class NoReservationWriterYetArchitectureTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");

    /**
     * Files permitted to name Status.RESERVED. Until the approval service
     * exists, only the definition itself and the readers that must recognise
     * an existing hold may do so -- reading a reservation is safe, creating
     * one is not.
     */
    private static final List<String> RESERVED_MENTION_ALLOWED = List.of(
            // Declares the enum constant.
            "BenefitBucketConsumption.java",
            // Read paths: they subtract existing holds from what may still be
            // reserved. They never construct a consumption row.
            "LimitBalanceReader.java",
            "BenefitBucketConsumptionRepository.java");

    @Test
    void noProductionCodeCreatesAReservationBeforeTheApprovalServiceExists() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(PRODUCTION_SOURCES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fileName = path.getFileName().toString();
                if (RESERVED_MENTION_ALLOWED.contains(fileName)) {
                    continue;
                }
                String source = Files.readString(path, StandardCharsets.UTF_8);
                int lineNumber = 0;
                for (String line : source.lines().toList()) {
                    lineNumber++;
                    String code = line.strip();
                    if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                        continue; // prose about reservations is not a writer
                    }
                    if (code.contains("Status.RESERVED")) {
                        violations.add(path + ":" + lineNumber + " -> " + code);
                    }
                }
            }
        }

        assertThat(violations)
                .as("""
                        A reservation may only be written by the pre-authorization approval \
                        service, which does not exist yet. Creating a hold without the \
                        approval snapshot and its release path removes limit from a member \
                        that nothing gives back. Add the new writer to \
                        RESERVED_MENTION_ALLOWED only alongside its release and expiry paths.""")
                .isEmpty();
    }

    /**
     * The general ceiling's reserved figure must come from POLICY_GENERAL rows,
     * never from summing bucket rows. One claim line can map to several
     * buckets, so adding bucket amounts together to obtain the general figure
     * counts the same money once per bucket it touches -- which understates
     * what the member may still spend, and does so invisibly.
     */
    @Test
    void theGeneralCeilingNeverDerivesItsReservedAmountFromBucketRows() throws IOException {
        Path reader = PRODUCTION_SOURCES.resolve(
                "com/waad/tba/modules/benefitpolicy/service/LimitBalanceReader.java");
        assertThat(Files.exists(reader)).as(reader + " must exist").isTrue();

        String source = Files.readString(reader, StandardCharsets.UTF_8);
        assertThat(source)
                .as("The general ceiling must read its own POLICY_GENERAL rows.")
                .contains("sumGeneralScopeReserved");
    }
}
