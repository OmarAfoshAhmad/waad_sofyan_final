package com.waad.tba.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DeploymentExposureArchitectureTest {

    @Test
    void localComposeOverrideMustNotPublishPostgresOnEveryInterface() throws Exception {
        String override = Files.readString(Path.of("../docker-compose.override.yml"));

        assertTrue(override.contains("127.0.0.1:5432:5432"));
        assertFalse(override.contains("\"5432:5432\""));
        assertFalse(override.contains("- 5432:5432"));
    }

    @Test
    void nginxRateLimitMustReturnThrottleStatusInsteadOfServerError() throws Exception {
        String nginx = Files.readString(Path.of("../frontend/nginx.conf"));

        assertTrue(nginx.contains("limit_req_zone"));
        assertTrue(nginx.contains("limit_req_status 429;"));
    }

    @Test
    void frontendMustNotCarryLegacyServiceTokenStorageKey() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("../frontend/src"))) {
            boolean found = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".js")
                            || path.toString().endsWith(".jsx")
                            || path.toString().endsWith(".ts")
                            || path.toString().endsWith(".tsx"))
                    .anyMatch(path -> contains(path, "serviceToken"));

            assertFalse(found);
        }
    }

    @Test
    void openApiMustDocumentSessionAuthNotLegacyBearerJwt() throws Exception {
        String openApi = Files.readString(Path.of(
                "src/main/java/com/waad/tba/common/config/OpenApiConfig.java"));

        assertTrue(openApi.contains("SessionCookie"));
        assertTrue(openApi.contains("SecuritySchemeIn.COOKIE"));
        assertFalse(openApi.contains("BearerAuth"));
        assertFalse(openApi.contains("bearerFormat"));
    }

    private boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read " + path, ex);
        }
    }
}
