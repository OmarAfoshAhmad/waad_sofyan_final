package com.waad.tba.modules.preauthorization.mapper;

import com.waad.tba.modules.preauthorization.dto.PreAuthEmailAttachmentDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthEmailRequestDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthEmailAttachment;
import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PreAuthEmailMapper {

    public PreAuthEmailRequestDto toDto(PreAuthEmailRequest entity) {
        if (entity == null) return null;

        return PreAuthEmailRequestDto.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .senderEmail(entity.getSenderEmail())
                .senderName(entity.getSenderName())
                .subject(entity.getSubject())
                .bodyText(entity.getBodyText())
                .bodyHtml(entity.getBodyHtml())
                .receivedAt(entity.getReceivedAt())
                .processed(entity.getProcessed())
                .convertedToPreAuthId(entity.getConvertedToPreAuthId())
                .providerId(entity.getProviderId())
                .providerName(entity.getProviderName())
                .memberId(entity.getMemberId())
                .memberFullName(entity.getMemberFullName())
                .detectedServiceId(entity.getDetectedServiceId())
                .serviceName(entity.getServiceName())
                .attachmentsCount(entity.getAttachmentsCount())
                .attachments(entity.getAttachments() != null ? 
                        entity.getAttachments().stream().map(this::toDto).collect(Collectors.toList()) : null)
                .build();
    }

    public PreAuthEmailAttachmentDto toDto(PreAuthEmailAttachment entity) {
        if (entity == null) return null;

        return PreAuthEmailAttachmentDto.builder()
                .id(entity.getId())
                .fileName(entity.getOriginalFileName())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .fileId(entity.getStoredFileName())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
