package com.waad.tba.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EmailPreAuthRetirementArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/com/waad/tba/modules/preauthorization");

    @Test
    void inboundEmailComponentsMustNotReturn() {
        String[] retired = {
                "controller/EmailPreAuthController.java",
                "service/EmailPreAuthService.java",
                "service/PreAuthEmailNotificationService.java",
                "scheduler/EmailPreAuthScheduler.java",
                "entity/PreAuthEmailRequest.java",
                "entity/PreAuthEmailAttachment.java",
                "repository/PreAuthEmailRequestRepository.java",
                "repository/PreAuthEmailAttachmentRepository.java",
                "mapper/PreAuthEmailMapper.java"
        };
        for (String relative : retired) {
            assertFalse(Files.exists(sourceRoot.resolve(relative)),
                    () -> "Retired inbound-email component returned: " + relative);
        }
    }
}
