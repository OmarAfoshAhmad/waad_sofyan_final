package com.waad.tba.modules.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.auth.dto.LoginRequest;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.servlet.http.Cookie;

@SpringBootTest(classes = TbaWaadApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAuthenticationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String username;
    private String password;

    @BeforeEach
    void createActiveUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        username = "session-" + suffix;
        password = "Session@Test123";
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .fullName("Session Integration User")
                .email(username + "@waad.test")
                .userType("SUPER_ADMIN")
                .active(true)
                .emailVerified(true)
                .build());
    }

    @Test
    void loginRotatesExistingSessionAndReturnsNoJwt() throws Exception {
        Cookie attackerKnownSession = new Cookie("JSESSIONID", "attacker-known-session-id");

        MvcResult login = mockMvc.perform(post("/api/v1/auth/session/login")
                        .cookie(attackerKnownSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.roles[0]").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andReturn();

        Cookie authenticatedSession = login.getResponse().getCookie("JSESSIONID");
        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.getValue()).isNotEqualTo(attackerKnownSession.getValue());

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void sessionSupportsMeAndLogoutRevokesIt() throws Exception {
        Cookie session = login();

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/v1/auth/session/logout").cookie(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void deactivatedUserCannotContinueUsingExistingSession() throws Exception {
        Cookie session = login();
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void authenticatedMutationWithoutCsrfTokenIsRejected() throws Exception {
        Cookie session = login();

        mockMvc.perform(post("/api/v1/auth/session/logout").cookie(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void listsAndRevokesAllSessionsForCurrentUser() throws Exception {
        Cookie first = login();
        Cookie second = login();

        MvcResult inventory = mockMvc.perform(get("/api/v1/auth/session/sessions").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.current == true)]").isNotEmpty())
                .andReturn();

        // Spring Security may rotate the id after restoring authentication on the
        // first request; a browser automatically adopts this Set-Cookie value.
        Cookie current = inventory.getResponse().getCookie("JSESSIONID");
        if (current == null) {
            current = first;
        }

        mockMvc.perform(post("/api/v1/auth/session/logout-all").cookie(current).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/v1/auth/session/me").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void changingPasswordRevokesEveryExistingSession() throws Exception {
        Cookie first = login();
        Cookie second = login();
        String newPassword = "Changed@Test456";

        MvcResult bootstrap = mockMvc.perform(get("/api/v1/auth/session/me").cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie current = bootstrap.getResponse().getCookie("JSESSIONID");
        if (current == null) {
            current = first;
        }

        String changeBody = """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s",
                  "confirmPassword": "%s"
                }
                """.formatted(password, newPassword, newPassword);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/auth/users/me/password")
                        .cookie(current)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session/me").cookie(current))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/v1/auth/session/me").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isUnauthorized());

        password = newPassword;
        mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPasswordDoesNotCreateAuthenticatedSession() throws Exception {
        LoginRequest invalid = LoginRequest.builder()
                .identifier(username)
                .password("wrong-password")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        Cookie anonymousSession = result.getResponse().getCookie("JSESSIONID");
        if (anonymousSession != null) {
            // 200 with a null payload, not 401: "no session yet" is the expected outcome
            // of this check, not a client error — see getSessionUser's javadoc.
            mockMvc.perform(get("/api/v1/auth/session/me").cookie(anonymousSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void bearerHeaderCannotOverrideAnAuthenticatedBrowserSession() throws Exception {
        Cookie session = login();

        MvcResult bootstrap = mockMvc.perform(get("/api/v1/auth/session/me").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie current = bootstrap.getResponse().getCookie("JSESSIONID");
        if (current == null) {
            current = session;
        }

        mockMvc.perform(get("/api/v1/auth/session/me")
                        .cookie(current)
                        .header("Authorization", "Bearer deliberately-invalid-mobile-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("JSESSIONID");
        assertThat(session).isNotNull();
        return session;
    }

    private String loginJson() throws Exception {
        return objectMapper.writeValueAsString(LoginRequest.builder()
                .identifier(username)
                .password(password)
                .build());
    }
}
