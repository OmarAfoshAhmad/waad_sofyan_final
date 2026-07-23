package com.waad.tba.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.waad.tba.common.file.LocalFileStorageService;

class RawFileEndpointArchitectureTest {

    @Test
    void rawFileKeyControllerMustNotExist() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/waad/tba/common/file/FileController.java")));
    }

    @Test
    void localStorageMustNotIssueRawFileKeyUrls() {
        LocalFileStorageService storage = new LocalFileStorageService();

        assertThrows(UnsupportedOperationException.class,
                () -> storage.getPresignedUrl("claims/1/secret.pdf", 60));
    }

    @Test
    void legacyEmailRequestControllerMustNotExist() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/waad/tba/modules/preauthorization/controller/"
                        + "PreAuthEmailRequestController.java")));
    }
}
