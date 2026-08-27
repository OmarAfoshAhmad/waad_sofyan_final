package com.waad.tba.modules.rbac.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * S-01 / Phase 2. users.user_type initialised itself to "DATA_ENTRY" in three
 * independent places: the entity's @Builder.Default, the column default from
 * V5, and UserService.resolveUserType's final fallback. DATA_ENTRY is internal
 * staff as far as RoleService is concerned, so every one of those silently
 * handed portal-gate-bypassing standing to an account nobody had assigned a
 * role to.
 *
 * A source scan rather than a runtime check because the property under test is
 * the absence of a default -- no request can demonstrate that a default has
 * not been reintroduced, only an inspection of the places one can live.
 */
class NoImplicitUserRoleTest {

    private String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * Strips line comments before scanning. A comment explaining which default
     * was removed necessarily quotes it, and matching prose instead of code
     * would make the guard fail on its own documentation.
     */
    private String codeOnly(String source) {
        return source.lines()
                .map(line -> {
                    int comment = line.indexOf("//");
                    return comment < 0 ? line : line.substring(0, comment);
                })
                .collect(Collectors.joining(System.lineSeparator()));
    }

    @Test
    void entityDeclaresNoDefaultRole() throws Exception {
        String entity = read("src/main/java/com/waad/tba/modules/rbac/entity/User.java");
        int field = entity.indexOf("private String userType");
        assertThat(field).as("userType field must exist").isGreaterThan(-1);

        String preceding = entity.substring(Math.max(0, field - 400), field);
        assertThat(preceding)
                .as("userType must not carry @Builder.Default -- a creation path that "
                        + "forgets the role would inherit internal-staff standing")
                .doesNotContain("@Builder.Default");

        String declaration = entity.substring(field, entity.indexOf(';', field));
        assertThat(declaration)
                .as("userType must not be initialised inline")
                .doesNotContain("=");
    }

    @Test
    void newUserCannotInheritDataEntryRole() throws Exception {
        String service = read("src/main/java/com/waad/tba/modules/rbac/service/UserService.java");
        int start = service.indexOf("private String resolveUserType");
        assertThat(start).as("resolveUserType must exist").isGreaterThan(-1);
        String body = codeOnly(service.substring(start, service.indexOf("    }", start)));

        assertThat(body)
                .as("resolveUserType must never fall back to a role; with no role and "
                        + "no scope supplied the only safe answer is to refuse")
                .doesNotContain("return " + '"' + "DATA_ENTRY" + '"');
    }

    @Test
    void databaseSuppliesNoDefaultRole() throws Exception {
        String migration = read(
                "src/main/resources/db/migration/V193__drop_implicit_user_role_default.sql");
        assertThat(migration)
                .as("the column default must be dropped in the database too")
                .contains("ALTER COLUMN user_type DROP DEFAULT");
        assertThat(migration)
                .as("NOT NULL must be kept: an account with no role stays impossible")
                .doesNotContain("DROP NOT NULL");
    }

    @Test
    void registrationRequestRequiresAnExplicitRole() throws Exception {
        String dto = read("src/main/java/com/waad/tba/modules/auth/dto/RegisterRequest.java");
        assertThat(dto)
                .as("the registration payload must carry the role, not inherit it")
                .contains("private String userType");
        assertThat(dto).contains("User type is required");
    }
}
