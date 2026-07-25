package com.waad.tba.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * SafeSessionRepositoryConfig
 *
 * Configures PostgreSQL ON CONFLICT UPSERT for SPRING_SESSION_ATTRIBUTES to prevent
 * duplicate key constraint errors during concurrent page requests.
 */
@Slf4j
@Configuration
public class SafeSessionRepositoryConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof JdbcIndexedSessionRepository jdbcRepo) {
            log.info("⚡ Configuring PostgreSQL UPSERT query on JdbcIndexedSessionRepository [{}]", beanName);
            jdbcRepo.setCreateSessionAttributeQuery(
                "INSERT INTO SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (SESSION_PRIMARY_ID, ATTRIBUTE_NAME) DO UPDATE SET ATTRIBUTE_BYTES = EXCLUDED.ATTRIBUTE_BYTES"
            );
        }
        return bean;
    }
}
