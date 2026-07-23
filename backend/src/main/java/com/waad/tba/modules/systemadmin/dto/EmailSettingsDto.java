package com.waad.tba.modules.systemadmin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSettingsDto {
    private Long id;
    private String emailAddress;
    private String displayName;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String smtpPassword;
    private Boolean smtpPasswordConfigured;
    private String encryptionType;
    private Boolean isActive;
}
