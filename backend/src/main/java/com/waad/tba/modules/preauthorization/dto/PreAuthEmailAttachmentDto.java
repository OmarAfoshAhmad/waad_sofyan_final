package com.waad.tba.modules.preauthorization.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthEmailAttachmentDto {
    private Long id;
    private String fileName; // originalFileName
    private String fileType;
    private Long fileSize;
    private String fileId; // storedFileName (used in download URL)
    private LocalDateTime createdAt;
}
