package com.waad.tba.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Varies the JSESSIONID cookie's Max-Age per login based on the "rememberMe"
 * session attribute set by AuthController#sessionLogin, instead of the fixed
 * 30-minute Max-Age Spring Session would otherwise always apply.
 *
 * writeCookieValue is synchronized because cookieMaxAge is mutated on this
 * shared singleton right before each write -- without synchronization two
 * concurrent logins (one remember-me, one not) could interleave and pick up
 * each other's Max-Age.
 */
public class RememberMeAwareCookieSerializer extends DefaultCookieSerializer {

    private final int defaultMaxAgeSeconds;
    private final int rememberMeMaxAgeSeconds;

    public RememberMeAwareCookieSerializer(int defaultMaxAgeSeconds, int rememberMeMaxAgeSeconds) {
        this.defaultMaxAgeSeconds = defaultMaxAgeSeconds;
        this.rememberMeMaxAgeSeconds = rememberMeMaxAgeSeconds;
    }

    @Override
    public synchronized void writeCookieValue(CookieValue cookieValue) {
        boolean rememberMe = false;
        HttpSession session = cookieValue.getRequest().getSession(false);
        if (session != null) {
            rememberMe = Boolean.TRUE.equals(session.getAttribute("rememberMe"));
        }
        setCookieMaxAge(rememberMe ? rememberMeMaxAgeSeconds : defaultMaxAgeSeconds);
        super.writeCookieValue(cookieValue);
    }
}
