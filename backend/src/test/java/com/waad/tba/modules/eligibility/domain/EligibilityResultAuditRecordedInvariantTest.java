package com.waad.tba.modules.eligibility.domain;

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
 * Structural guard for the auditRecorded fail-closed invariant: the field
 * defaults to false (see EligibilityResult), and the ONLY place in the
 * codebase allowed to set it true is EligibilityEngineServiceImpl, and only
 * after EligibilityAuditRecorder.record() has actually returned true. If any
 * other code path (a new factory method, a new caller, a future refactor)
 * starts constructing a result with auditRecorded(true) directly, it can
 * silently claim an audit trail exists when it was never actually written --
 * this test fails the build the moment that happens, since Java has no
 * language-level way to restrict which callers may set a Lombok-generated
 * builder field.
 */
class EligibilityResultAuditRecordedInvariantTest {

    private static final String ALLOWED_FILE = "EligibilityEngineServiceImpl.java";

    @Test
    void onlyEligibilityEngineServiceImplMaySetAuditRecordedToTrue() throws IOException {
        Path srcRoot = mainJavaRoot();
        List<Path> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(srcRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals(ALLOWED_FILE))
                    .filter(p -> !p.getFileName().toString().equals("EligibilityResult.java")) // the @Builder.Default itself
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            if (content.contains(".auditRecorded(true)")
                                    || content.matches("(?s).*\\.auditRecorded\\(\\s*true\\s*\\).*")) {
                                offenders.add(p);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertThat(offenders)
                .as("Only EligibilityEngineServiceImpl may set auditRecorded(true), and only after "
                        + "EligibilityAuditRecorder.record() returns true. Found unexpected offenders: " + offenders)
                .isEmpty();
    }

    @Test
    void defaultAuditRecordedIsFalse() {
        EligibilityResult result = EligibilityResult.eligible("req", null, 1L, 1);
        assertThat(result.isAuditRecorded())
                .as("auditRecorded must fail closed: a result built without going through the audit recorder "
                        + "must never claim to be audited")
                .isFalse();
    }

    private Path mainJavaRoot() {
        // Test runs from backend/ (Maven working dir); source root is a fixed,
        // stable relative path from there.
        Path path = Path.of("src", "main", "java");
        assertThat(Files.isDirectory(path)).as("expected src/main/java to exist at " + path.toAbsolutePath()).isTrue();
        return path;
    }
}
