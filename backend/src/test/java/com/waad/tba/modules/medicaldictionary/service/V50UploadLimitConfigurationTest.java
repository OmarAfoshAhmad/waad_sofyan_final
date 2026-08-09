package com.waad.tba.modules.medicaldictionary.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V50UploadLimitConfigurationTest {

    @Test
    void defaultAndDeploymentExampleAcceptTheFrozenV50Seed() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String environmentExample = Files.readString(Path.of("../.env.example"));

        assertThat(application)
                .contains("SPRING_MULTIPART_MAX_FILE_SIZE:128MB")
                .contains("SPRING_MULTIPART_MAX_REQUEST_SIZE:128MB");
        assertThat(environmentExample)
                .contains("SPRING_MULTIPART_MAX_FILE_SIZE=128MB")
                .contains("SPRING_MULTIPART_MAX_REQUEST_SIZE=128MB");
    }
}
