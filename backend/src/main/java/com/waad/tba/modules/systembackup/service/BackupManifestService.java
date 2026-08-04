package com.waad.tba.modules.systembackup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupManifest;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class BackupManifestService {
    private final ObjectMapper objectMapper;

    public BackupManifestService() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void write(Path manifestPath, BackupManifest manifest) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write backup manifest", e);
        }
    }

    public byte[] toBytes(BackupManifest manifest) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize backup manifest", e);
        }
    }
}
