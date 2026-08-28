package com.waad.tba.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Asserted on a real response rather than on the configuration source,
 * because what protects a browser is the header that actually arrives.
 *
 * Referrer-Policy is the one that belongs to the same problem as moving
 * reasons out of query strings: the URLs that remain still carry member ids
 * and search identifiers, and without this the browser attaches the whole
 * address to every request leaving for another origin.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mockMvc;

    /** A public endpoint: headers must be present before anyone authenticates. */
    private static final String PUBLIC_ENDPOINT = "/api/v1/auth/session/me";

    @Test
    void urlsAreNotLeakedToOtherOriginsViaReferer() throws Exception {
        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void contentTypeSniffingIsDisabled() throws Exception {
        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void aContentSecurityPolicyIsPresentAndDeniesForeignObjects() throws Exception {
        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'self'")));
    }

    @Test
    void hardwareAndLocationApisAreDenied() throws Exception {
        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("geolocation=()")));
    }

    @Test
    void framingIsRestrictedToTheApplicationItself() throws Exception {
        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }
}
