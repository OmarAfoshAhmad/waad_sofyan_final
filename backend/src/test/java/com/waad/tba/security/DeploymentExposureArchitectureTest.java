package com.waad.tba.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DeploymentExposureArchitectureTest {
    private static final Pattern INLINE_POSTGRES_PASSWORD = Pattern.compile(
            "(?:jdbc:)?" + "postgresql://postgres:" + "[^@\\s{}$%]+@",
            Pattern.CASE_INSENSITIVE);

    @Test
    void localComposeOverrideMustNotPublishPostgresOnEveryInterface() throws Exception {
        String override = Files.readString(Path.of("../docker-compose.override.yml"));

        assertTrue(override.contains("127.0.0.1:5432:5432"));
        assertFalse(override.contains("\"5432:5432\""));
        assertFalse(override.contains("- 5432:5432"));
    }

    @Test
    void repositoryMustNotContainHardcodedPostgresPasswords() throws Exception {
        List<String> ignoredPathFragments = List.of(
                ".git",
                "target",
                "node_modules",
                "dist",
                "yarn.lock",
                "package-lock.json");

        try (Stream<Path> files = Files.walk(Path.of(".."))) {
            boolean found = files
                    .filter(Files::isRegularFile)
                    .filter(path -> ignoredPathFragments.stream().noneMatch(fragment -> path.toString().contains(fragment)))
                    .filter(this::isTextFile)
                    .anyMatch(this::containsInlinePostgresPassword);

            assertFalse(found, "Do not commit PostgreSQL DSNs with inline passwords; use DATABASE_URL or DB_PASSWORD.");
        }
    }

    @Test
    void nginxRateLimitMustReturnThrottleStatusInsteadOfServerError() throws Exception {
        String nginx = Files.readString(Path.of("../frontend/nginx.conf"));

        assertTrue(nginx.contains("limit_req_zone  $binary_remote_addr zone=api:10m"));
        assertTrue(nginx.contains("limit_req_zone  $binary_remote_addr zone=auth:10m   rate=5r/m;"));
        assertTrue(nginx.contains("limit_req_status 429;"));
        assertTrue(nginx.contains("location /api/v1/auth/ {"));
        assertTrue(nginx.contains("limit_req zone=auth burst=3 nodelay;"));
        assertTrue(nginx.contains("proxy_pass http://waadapp-backend:8080/api/v1/auth/;"));
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
            return false;
        }
    }

    private boolean containsInlinePostgresPassword(Path path) {
        try {
            return INLINE_POSTGRES_PASSWORD.matcher(Files.readString(path)).find();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java")
                || fileName.endsWith(".js")
                || fileName.endsWith(".jsx")
                || fileName.endsWith(".ts")
                || fileName.endsWith(".tsx")
                || fileName.endsWith(".py")
                || fileName.endsWith(".md")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".properties")
                || fileName.endsWith(".sql")
                || fileName.endsWith(".sh")
                || fileName.endsWith(".bat")
                || fileName.endsWith(".ps1")
                || fileName.endsWith(".txt")
                || fileName.equals(".env.example");
    }
}
