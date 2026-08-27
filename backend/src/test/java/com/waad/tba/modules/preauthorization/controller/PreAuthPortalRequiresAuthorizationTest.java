package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * S-02. /api/v1/provider/preauths carried no authorization at all while its
 * /bulk endpoint persists real pre-authorizations. SecurityConfig ends in
 * .anyRequest().authenticated(), so every account that could log in could
 * write rows here -- including one holding no pre-authorization permission.
 *
 * The assertion is the row count, not the status: an endpoint that refuses
 * with 403 after already flushing the insert has refused nothing.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreAuthPortalRequiresAuthorizationTest extends PostgresIntegrationTestBase {

    private static final String BULK = "/api/v1/provider/preauths/bulk";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String powerless;

    @BeforeEach
    void createAccountWithoutPreauthPermission() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        powerless = "finance-" + suffix;
        // FINANCE_VIEWER holds no PREAUTH_* permission in the V191 templates.
        userRepository.save(User.builder()
                .username(powerless)
                .password(passwordEncoder.encode("Powerless@Test123"))
                .fullName("Finance Viewer")
                .email(powerless + "@waad.test")
                .userType("FINANCE_VIEWER")
                .active(true)
                .emailVerified(true)
                .build());
    }

    private long portalRowCount() {
        Long count = jdbc.queryForObject(
                "select count(*) from pre_authorizations where pre_auth_number like 'PA-MOCK-%'",
                Long.class);
        return count == null ? 0L : count;
    }

    private String bulkPayload() {
        return """
                {"member":{"id":42},"providerId":9,"expectedServiceDate":"2026-12-01",
                 "lines":[{"code":"SVC-1","name":"Service","contractPrice":"100.00",
                 "medicalCategoryId":7}]}
                """;
    }

    @Test
    void anonymousCannotWriteThroughThePortal() throws Exception {
        long before = portalRowCount();

        mockMvc.perform(post(BULK).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkPayload()))
                .andExpect(status().is4xxClientError());

        assertThat(portalRowCount())
                .as("an anonymous caller must not persist a pre-authorization")
                .isEqualTo(before);
    }

    @Test
    @WithMockUser(username = "unknown-principal")
    void authenticatedWithoutPreauthPermissionCannotWrite() throws Exception {
        long before = portalRowCount();

        mockMvc.perform(post(BULK).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkPayload()))
                .andExpect(status().is4xxClientError());

        assertThat(portalRowCount())
                .as("authentication alone must not be enough to write here")
                .isEqualTo(before);
    }

    @Test
    void anonymousCannotReadThroughThePortal() throws Exception {
        mockMvc.perform(get("/api/v1/provider/preauths"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/provider/preauths/1"))
                .andExpect(status().is4xxClientError());
    }

    /**
     * Guards the shape of the fix, not just its effect: every endpoint on this
     * controller must carry an explicit permission check, so a method added
     * later cannot inherit the old wide-open behaviour by omission.
     */
    @Test
    void everyPortalEndpointDeclaresAPermission() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthPortalController.java"),
                java.nio.charset.StandardCharsets.UTF_8);

        long mappings = source.lines()
                .filter(line -> line.trim().startsWith("@GetMapping")
                        || line.trim().startsWith("@PostMapping")
                        || line.trim().startsWith("@PutMapping")
                        || line.trim().startsWith("@DeleteMapping"))
                .count();
        long guards = source.lines()
                .filter(line -> line.trim().startsWith("@PreAuthorize"))
                .count();

        assertThat(mappings).as("the controller must still expose endpoints").isGreaterThan(0);
        assertThat(guards)
                .as("every endpoint must declare a permission; found " + mappings
                        + " mappings and " + guards + " guards")
                .isGreaterThanOrEqualTo(mappings);
    }
}
