package com.waad.tba.common.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces the system-wide rule that every persisted monetary column is
 * NUMERIC(x,2) — see {@link Money}.
 *
 * This exists because the rule was previously only a convention: the settlement
 * module carried @Column(precision = 12, scale = 3) on PaymentRecord.amount and
 * both PaymentAuditLog amounts while the actual columns were NUMERIC(15,2). The
 * mismatch is invisible at runtime (Hibernate does not fail on it) but means a
 * third decimal is silently rounded away on write — exactly the kind of silent
 * money drift that is unacceptable here.
 *
 * Implemented as a source scan rather than by adding ArchUnit: the check is a
 * single regex over entity sources, and pulling in a new dependency for it would
 * cost more than it returns.
 */
class MonetaryScaleConventionTest {

    /**
     * Fields that legitimately carry a non-2 scale because they are NOT money.
     * Anything added here must be justified — if it holds an amount of currency
     * it does not belong on this list.
     */
    private static final Set<String> NON_MONETARY_ALLOWLIST = Set.of(
            // Rule engine timing metric, measured in milliseconds — not currency.
            "execution_time_ms");

    private static final Pattern COLUMN_WITH_SCALE = Pattern.compile(
            "@Column\\s*\\(([^)]*?)\\)\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*private\\s+BigDecimal\\s+(\\w+)",
            Pattern.DOTALL);

    private static final Pattern SCALE_VALUE = Pattern.compile("scale\\s*=\\s*(\\d+)");
    private static final Pattern COLUMN_NAME = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");

    @Test
    void everyPersistedMonetaryFieldUsesScaleTwo() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        assertThat(sourceRoot).as("backend source root must exist").exists();

        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher fields = COLUMN_WITH_SCALE.matcher(source);
                while (fields.find()) {
                    String columnArgs = fields.group(1);
                    String fieldName = fields.group(2);

                    Matcher scale = SCALE_VALUE.matcher(columnArgs);
                    if (!scale.find()) {
                        continue; // no explicit scale — not in scope for this rule
                    }
                    int declaredScale = Integer.parseInt(scale.group(1));
                    if (declaredScale == Money.SCALE) {
                        continue;
                    }

                    Matcher name = COLUMN_NAME.matcher(columnArgs);
                    String columnName = name.find() ? name.group(1) : fieldName;
                    if (NON_MONETARY_ALLOWLIST.contains(columnName)) {
                        continue;
                    }

                    violations.add(String.format("%s -> %s (scale = %d)",
                            sourceRoot.relativize(file), columnName, declaredScale));
                }
            }
        }

        assertThat(violations)
                .as("Monetary BigDecimal columns must use scale = %d (see Money). "
                        + "If a field is genuinely not money, add its column name to "
                        + "NON_MONETARY_ALLOWLIST with a justification.", Money.SCALE)
                .isEmpty();
    }

    @Test
    void normalizeAppliesCanonicalScaleAndRounding() {
        assertThat(Money.normalize(new BigDecimal("10.005"))).isEqualByComparingTo("10.01"); // HALF_UP
        assertThat(Money.normalize(new BigDecimal("10.004"))).isEqualByComparingTo("10.00");
        assertThat(Money.normalize(new BigDecimal("10"))).isEqualByComparingTo("10.00");
        assertThat(Money.normalize(null)).isEqualByComparingTo("0.00");
        assertThat(Money.normalize(new BigDecimal("10.1")).scale()).isEqualTo(Money.SCALE);
    }

    @Test
    void isExactDetectsSubCentValuesInsteadOfSilentlyRoundingThem() {
        assertThat(Money.isExact(new BigDecimal("10.50"))).isTrue();
        assertThat(Money.isExact(new BigDecimal("10.5"))).isTrue();   // same value, different scale
        assertThat(Money.isExact(new BigDecimal("10.001"))).isFalse(); // sub-cent -> must be rejected
        assertThat(Money.isExact(null)).isFalse();
    }
}
