package com.waad.tba.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Ensures SessionCookieSanitizerFilter is registered at Tomcat Servlet level 
 * BEFORE Spring Session (SessionRepositoryFilter).
 * 
 * Order: Ordered.HIGHEST_PRECEDENCE - 100
 */
@Configuration
public class SessionCookieSanitizerFilterRegistration {

    @Bean
    public FilterRegistrationBean<SessionCookieSanitizerFilter> registerSessionCookieSanitizerFilter(
            SessionCookieSanitizerFilter filter) {
        FilterRegistrationBean<SessionCookieSanitizerFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE - 100);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
