package com.waad.tba.modules.medicaldictionary.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class MedicalDictionaryReleaseResponse {
    Long id;
    String version;
    String sourceFilename;
    String sourceSha256;
    String status;
    int categoryCount;
    int conceptCount;
    int aliasCount;
    int exceptionCount;
    LocalDateTime activatedAt;
}
