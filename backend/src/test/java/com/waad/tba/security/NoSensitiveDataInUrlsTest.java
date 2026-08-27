package com.waad.tba.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A query string is not a private channel. It is written verbatim into the
 * servlet container's access log and every reverse proxy in front of it, kept
 * in browser history, and attached to outbound Referer headers -- none of
 * which honour the retention rules or access controls that govern the tables
 * this same data is deliberately stored in.
 *
 * The reasons matter most here. They are free text written by staff about a
 * named person -- why a membership was ended, why a pre-authorization was
 * cancelled -- and in a health-insurance system that routinely names a
 * condition or a personal circumstance.
 *
 * Multipart endpoints are exempt: there @RequestParam binds a form field in
 * the request body, not a query parameter, so the value never reaches the URL.
 */
class NoSensitiveDataInUrlsTest {

    /** Names that must never travel in a query string. */
    private static final Set<String> FORBIDDEN = Set.of(
            "reason", "password", "newPassword", "oldPassword", "currentPassword",
            "token", "resetToken", "otp", "secret", "apiKey", "iban", "bankAccount");

    private List<Path> controllers() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/waad/tba"))) {
            return files.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .toList();
        }
    }

    @Test
    void noEndpointCarriesSensitiveValuesInTheQueryString() throws Exception {
        List<String> violations = new ArrayList<>();

        for (Path controller : controllers()) {
            String source = Files.readString(controller, StandardCharsets.UTF_8);
            // A controller that consumes multipart binds @RequestParam to form
            // parts rather than the URL, so its parameters never appear there.
            boolean multipart = source.contains("multipart/form-data")
                    || source.contains("MULTIPART_FORM_DATA_VALUE");
            if (multipart) {
                continue;
            }
            for (String forbidden : FORBIDDEN) {
                boolean present = source.contains("@RequestParam(name = \"" + forbidden + "\"")
                        || source.contains("@RequestParam(\"" + forbidden + "\"")
                        || source.contains("@RequestParam(value = \"" + forbidden + "\"");
                if (present) {
                    violations.add(controller.getFileName() + " exposes '" + forbidden + "'");
                }
            }
        }

        assertThat(violations)
                .as("these values must move into the request body: " + violations)
                .isEmpty();
    }

    /**
     * The body-carried reason has a shared shape so this cannot drift back one
     * endpoint at a time.
     */
    @Test
    void theSharedReasonRequestExists() throws Exception {
        String dto = Files.readString(
                Path.of("src/main/java/com/waad/tba/common/dto/ReasonRequest.java"),
                StandardCharsets.UTF_8);
        assertThat(dto).contains("private String reason");
        assertThat(dto).contains("reasonOf");
    }
}
