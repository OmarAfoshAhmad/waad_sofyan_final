package com.waad.tba.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ReviewerAssignmentFixerActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void isDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ReviewerAssignmentFixer.class));
    }

    @Test
    void requiresExplicitMaintenanceFlag() {
        contextRunner
                .withPropertyValues("waad.maintenance.reviewer-assignment-fixer.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReviewerAssignmentFixer.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ReviewerAssignmentFixer.class)
    static class TestConfiguration {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }
}
