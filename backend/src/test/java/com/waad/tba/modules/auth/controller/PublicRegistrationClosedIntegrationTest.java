package com.waad.tba.modules.auth.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.servlet.http.Cookie;

/**
 * S-01. /api/v1/auth/** was permitAll as a wildcard, and /register carried no
 * authorization of its own. Anyone on the internet could create an ACTIVE
 * account that inherited User.userType's DATA_ENTRY default -- a role
 * classified as internal staff, which bypasses every FeatureGuard portal
 * check and reaches endpoints guarded only by anyRequest().authenticated().
 *
 * The assertion that matters is not the status code but the absence of a row:
 * a denial that still persisted the user would be no denial at all.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRegistrationClosedIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminUsername;
    private String adminPassword;

    @BeforeEach
    void createAdministrator() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminUsername = "reg-admin-" + suffix;
        adminPassword = "Register@Test123";
        userRepository.save(User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .fullName("Registration Admin")
                .email(adminUsername + "@waad.test")
                .userType("SUPER_ADMIN")
                .active(true)
                .emailVerified(true)
                .build());
    }

    private String registrationJson(String username) {
        return """
                {"username":"%s","password":"Intruder@123","fullName":"Intruder",
                 "email":"%s@waad.test","phone":"0910000000","userType":"DATA_ENTRY"}
                """.formatted(username, username);
    }

    @Test
    void anonymousCannotRegister() throws Exception {
        String intruder = "intruder-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(intruder)))
                .andExpect(status().is4xxClientError());

        assertThat(userRepository.existsByUsernameIgnoreCase(intruder))
                .as("an anonymous caller must not be able to persist a user")
                .isFalse();
    }

    /** Even with a valid CSRF token, absence of USER_MANAGE must still deny. */
    @Test
    void anonymousWithCsrfStillCannotRegister() throws Exception {
        String intruder = "intruder-csrf-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(intruder)))
                .andExpect(status().is4xxClientError());

        assertThat(userRepository.existsByUsernameIgnoreCase(intruder)).isFalse();
    }

    @Test
    void administratorWithUserManageCanStillRegister() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}"
                                .formatted(adminUsername, adminPassword)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie session = login.getResponse().getCookie("JSESSIONID");
        assertThat(session).as("login must establish a session").isNotNull();

        // Login rotates the session id; a browser adopts the Set-Cookie from the
        // next response. Mirror that here or the follow-up request authenticates
        // against an id the server has already replaced.
        MvcResult bootstrap = mockMvc.perform(get("/api/v1/auth/session/me").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie current = bootstrap.getResponse().getCookie("JSESSIONID");
        if (current == null) {
            current = session;
        }

        String created = "created-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(current)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(created)))
                .andExpect(status().is2xxSuccessful());

        assertThat(userRepository.existsByUsernameIgnoreCase(created))
                .as("an administrator holding USER_MANAGE must still be able to create a user")
                .isTrue();
    }

    /**
     * Narrowing the wildcard must not take the login bootstrap with it.
     * /session/me answers "is there a session yet?" on every page load and is
     * public by design: it returns 200 with a null payload, not 401.
     */
    @Test
    void publicAuthEndpointsRemainReachable() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session/me"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}"
                                .formatted(adminUsername, adminPassword)))
                .andExpect(status().isOk());
    }
}
