package com.waad.tba.modules.systemadmin.dto;

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
    private String smtpPassword;
    private String imapHost;
    private Integer imapPort;
    private String imapUsername;
    private String imapPassword;
    private String encryptionType;
    private Boolean listenerEnabled;
    private Integer syncIntervalMins;
    private String subjectFilter;
    private Boolean onlyFromProviders;
    private Boolean isActive;
}
