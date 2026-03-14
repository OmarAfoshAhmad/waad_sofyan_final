package com.waad.tba.modules.preauthorization.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthEmailRequestDto {
    private Long id;
    private String messageId;
    private String senderEmail;
    private String senderName;
    private String subject;
    private String bodyText;
    private String bodyHtml;
    private LocalDateTime receivedAt;
    private Boolean processed;
    private Long convertedToPreAuthId;
    private Long providerId;
    private String providerName;
    private Long memberId;
    private String memberFullName;
    private Long detectedServiceId;
    private String serviceName;
    private int attachmentsCount;
    private List<PreAuthEmailAttachmentDto> attachments;
}
