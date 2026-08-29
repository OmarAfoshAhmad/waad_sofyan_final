package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A metric label may only take values from a fixed, small set.
 *
 * Prometheus stores one time series per distinct combination of labels, and
 * keeps it. A label whose values come from the data -- a batch id, a member
 * id, a file name, a username -- turns one metric into an unbounded family of
 * them, and the failure is not a wrong number on a dashboard: it is the
 * scrape target growing until it takes the process down, weeks after the line
 * was written and nowhere near it.
 *
 * Nothing about the Micrometer API stops that, and the mistake reads as
 * ordinary, helpful code -- tagging a counter with the batch id looks like
 * making it more useful. So the rule is enforced here rather than remembered.
 *
 * The allowed values are named. A new one is a decision someone makes on
 * purpose by adding it, not something that arrives by accident.
 */
class MetricLabelsAreBoundedArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/waad/tba");

    /**
     * Label KEYS whose values are known to come from a fixed enum or a literal
     * in the code, not from data.
     */
    private static final Set<String> BOUNDED_LABEL_KEYS = Set.of(
            "outcome",   // MemberImportLog.ImportStatus
            "action",    // GRANTED / REVOKED
            "status",    // an enum name
            "result",    // an enum name
            "type"       // an enum name
    );

    /** Anything named like this is per-record and can never be a label. */
    private static final List<String> FORBIDDEN_IN_LABEL_VALUE = List.of(
            "getId()", "getBatchId()", "getImportBatchId()", "getMemberId()",
            "getUsername()", "getFileName()", "getEmployerId()", "getCardNumber()",
            "getNationalNumber()", "batchId", "memberId", "employerId", "username", "fileName");

    private static final Pattern TAG_CALL = Pattern.compile("\\.tag\\(\\s*([^,]+?)\\s*,\\s*([^)]+?)\\s*\\)");

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    @Test
    @DisplayName("no metric is labelled with anything that varies per record")
    void metricLabelsCannotExplode() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
                if (!code.contains("io.micrometer") && !code.contains("MeterRegistry")
                        && !code.contains("Counter.builder") && !code.contains("Timer.builder")) {
                    continue;
                }
                Matcher tags = TAG_CALL.matcher(code);
                while (tags.find()) {
                    String key = tags.group(1).trim();
                    String value = tags.group(2).trim();

                    String literalKey = key.startsWith("\"") && key.endsWith("\"")
                            ? key.substring(1, key.length() - 1)
                            : null;
                    if (literalKey != null && !BOUNDED_LABEL_KEYS.contains(literalKey)) {
                        violations.add(file.getFileName() + ": label key \"" + literalKey
                                + "\" is not in the bounded set -- add it deliberately, or use a log line");
                    }
                    for (String forbidden : FORBIDDEN_IN_LABEL_VALUE) {
                        if (value.contains(forbidden)) {
                            violations.add(file.getFileName() + ": label value " + value
                                    + " varies per record");
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("a per-record label creates one stored time series per record, forever; "
                        + "that detail belongs in the log line the trackingId already points at")
                .isEmpty();
    }

    @Test
    @DisplayName("the metrics that exist are registered at zero, so a healthy system says zero")
    void outcomeCountersExistBeforeAnythingHappens() throws IOException {
        String metrics = Files.readString(
                Path.of("src/main/java/com/waad/tba/modules/member/service/MemberImportMetrics.java"),
                StandardCharsets.UTF_8);

        // A counter never incremented is absent from the scrape, and a panel
        // for failures then reads "no data" -- which is what a broken exporter
        // looks like too.
        assertThat(codeOnly(metrics))
                .as("every outcome is registered up front")
                .contains("@PostConstruct")
                .contains("ImportStatus.values()");
    }
}
