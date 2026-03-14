package com.waad.tba.modules.preauthorization.scheduler;

import com.waad.tba.modules.preauthorization.service.EmailPreAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailPreAuthScheduler {

    private final EmailPreAuthService emailPreAuthService;

    /**
     * Run every 5 minutes to check for new emails.
     * The service itself will check if the listener is enabled in settings.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void pollEmails() {
        log.debug("Starting scheduled email polling for pre-authorization...");
        try {
            emailPreAuthService.processEmails();
        } catch (Exception e) {
            log.error("Error during scheduled email polling: {}", e.getMessage(), e);
        }
    }
}
