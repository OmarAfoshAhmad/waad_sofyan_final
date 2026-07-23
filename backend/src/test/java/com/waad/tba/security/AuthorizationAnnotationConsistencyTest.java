package com.waad.tba.security;

import com.waad.tba.security.rbac.SystemRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationAnnotationConsistencyTest {

    private static final Pattern ROLE_EXPRESSION =
            Pattern.compile("has(?:Any)?Role\\(([^)]*)\\)");
    private static final Pattern QUOTED_ROLE =
            Pattern.compile("['\"]([A-Z][A-Z0-9_]*)['\"]");

    @Test
    void everyRoleReferencedByMethodSecurityIsARegisteredSystemRole() throws IOException {
        Set<String> registeredRoles = new HashSet<>(Arrays.stream(SystemRole.values())
                .map(Enum::name)
                .toList());
        List<String> invalidReferences = new ArrayList<>();
        Path sourceRoot = Path.of("src", "main", "java");

        try (var sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                Matcher expressionMatcher = ROLE_EXPRESSION.matcher(content);
                while (expressionMatcher.find()) {
                    Matcher roleMatcher = QUOTED_ROLE.matcher(expressionMatcher.group(1));
                    while (roleMatcher.find()) {
                        String role = roleMatcher.group(1);
                        if (!registeredRoles.contains(role)) {
                            invalidReferences.add(source + " -> " + role);
                        }
                    }
                }
            }
        }

        assertThat(invalidReferences)
                .as("Every hasRole/hasAnyRole reference must use SystemRole")
                .isEmpty();
    }
}
