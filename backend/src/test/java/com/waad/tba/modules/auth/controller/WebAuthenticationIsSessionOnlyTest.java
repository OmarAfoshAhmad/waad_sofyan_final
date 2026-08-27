package com.waad.tba.modules.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * S-05. A JwtAuthenticationFilter used to sit in the chain beside the session
 * filter as a "legacy fallback", which made the application's security
 * whichever of the two paths was weaker.
 *
 * It also cut across the authorization model the rest of this codebase is
 * built on: PermissionAdministrationService calls
 * sessionManagementService.revokeAll() after every permission change, and a
 * bearer token is not a row anyone can delete -- its holder keeps their
 * standing until it expires on its own.
 *
 * Nothing consumed it. The browser client calls only /auth/session/*, stores
 * no token and sends no Authorization header, and the mobile audience the
 * config named has never been built.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebAuthenticationIsSessionOnlyTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String username;

    @BeforeEach
    void createUser() {
        username = "jwt-gone-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("Session@Test123"))
                .fullName("Session Only User")
                .email(username + "@waad.test")
                .userType("SUPER_ADMIN")
                .active(true)
                .emailVerified(true)
                .build());
    }

    @Test
    void jwtIsNotAcceptedForWebAuthentication() throws Exception {
        // A structurally valid-looking bearer token must buy nothing at all.
        mockMvc.perform(get("/api/v1/admin/access-control/permissions")
                        .header("Authorization", "Bearer any.token.at.all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theJwtOnlyEndpointsAreGone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/auth/refresh-token")).andExpect(status().is4xxClientError());
    }

    @Test
    void sessionLoginStillWorksAndIssuesNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Session@Test123\"}"
                                .formatted(username)))
                .andExpect(status().isOk());
    }

    /**
     * Pins the shape, not just the behaviour: a second authentication filter
     * reintroduced later would restore the very ambiguity this removed, and no
     * request-level assertion can see that coming.
     */
    @Test
    void theSecurityChainRegistersNoJwtFilter() throws Exception {
        String config = Files.readString(
                Path.of("src/main/java/com/waad/tba/security/SecurityConfig.java"),
                StandardCharsets.UTF_8);
        String code = config.lines()
                .map(line -> {
                    int comment = line.indexOf("//");
                    return comment < 0 ? line : line.substring(0, comment);
                })
                .reduce("", (a, b) -> a + System.lineSeparator() + b);

        assertThat(code)
                .as("web authentication must have exactly one way in")
                .doesNotContain("jwtAuthenticationFilter");
    }
}
