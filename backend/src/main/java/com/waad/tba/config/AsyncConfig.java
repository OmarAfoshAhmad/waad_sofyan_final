package com.waad.tba.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for background processing.
 * Used for split-phase approval operations (Claims & PreAuthorizations).
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Task executor for approval processing.
     * Optimized for financial calculations with PESSIMISTIC locks.
     */
    @Bean(name = "approvalTaskExecutor")
    public Executor approvalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Start with 5 threads
        executor.setMaxPoolSize(10); // Max 10 concurrent approvals
        executor.setQueueCapacity(50); // Queue up to 50 pending approvals
        executor.setThreadNamePrefix("approval-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Handles exceptions thrown by @Async methods that are not caught.
     * Without this, failures in async settlement listeners are swallowed silently.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> log.error(
                "[ASYNC-ERROR] Uncaught exception in async method '{}' — params: {} — error: {}",
                method.getName(), params, throwable.getMessage(), throwable);
    }
}
