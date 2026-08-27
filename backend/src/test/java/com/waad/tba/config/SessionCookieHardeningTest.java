package com.waad.tba.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * S-06. application-prod.yml already carried secure/same-site/http-only, but
 * the base application.yml did not -- so every other profile, and any
 * deployment that forgot to activate prod, fell back to a bare session cookie
 * travelling in clear text.
 *
 * The fix is which file holds the hardened value, not the value itself: the
 * default must be the safe one, with the relaxation stated out loud where it
 * genuinely applies.
 */
class SessionCookieHardeningTest {

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    /** The cookie block of a given config file, comments stripped. */
    private String cookieBlock(String path) throws Exception {
        String config = read(path);
        int start = config.indexOf("cookie:");
        if (start < 0) {
            return "";
        }
        int end = config.indexOf("\n\n", start);
        String block = end < 0 ? config.substring(start) : config.substring(start, end);
        return block.lines()
                .map(line -> {
                    int comment = line.indexOf('#');
                    return comment < 0 ? line : line.substring(0, comment);
                })
                .reduce("", (a, b) -> a + System.lineSeparator() + b);
    }

    @Test
    void theDefaultProfileShipsAHardenedSessionCookie() throws Exception {
        String block = cookieBlock("src/main/resources/application.yml");

        assertThat(block)
                .as("the base profile must not fall back to a bare cookie")
                .contains("http-only: true")
                .contains("secure: ")
                .contains("same-site: ");
        assertThat(block)
                .as("secure must default to true, overridable only downward and on purpose")
                .contains("SESSION_COOKIE_SECURE:true");
        assertThat(block)
                .as("SameSite must default to Strict, matching what production already runs")
                .contains("SESSION_COOKIE_SAME_SITE:Strict");
    }

    @Test
    void productionRemainsAtLeastAsStrictAsTheDefault() throws Exception {
        String prod = cookieBlock("src/main/resources/application-prod.yml");

        assertThat(prod).contains("secure: true");
        assertThat(prod).contains("http-only: true");
        assertThat(prod.toLowerCase()).contains("same-site: strict");
    }

    /**
     * Development is the only place the cookie may be insecure, and only
     * because local HTTP would otherwise never receive it at all. Anything
     * else relaxed here would be a hole wearing a developer's clothes.
     */
    @Test
    void developmentRelaxesOnlyTheSecureFlagAndSaysWhy() throws Exception {
        String dev = read("src/main/resources/application-dev.yml");

        assertThat(dev)
                .as("dev may turn Secure off for plain-HTTP localhost")
                .contains("secure: false");
        assertThat(dev)
                .as("but it must not also loosen SameSite or HttpOnly")
                .doesNotContain("same-site")
                .doesNotContain("http-only: false");
    }
}
