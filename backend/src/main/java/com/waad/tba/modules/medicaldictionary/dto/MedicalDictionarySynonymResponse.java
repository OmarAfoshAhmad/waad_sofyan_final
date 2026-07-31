package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MedicalDictionarySynonymResponse {
    private Long id;
    private Long entryId;
    private String synonym;
    private String normalizedSynonym;
    private DictionarySynonymType synonymType;
    private String language;
    private boolean active;
    private Long usageCount;
    private String lifecycleStatus;
    private String learnedFromSource;
    private String sourceReference;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long lockedBy;
    private LocalDateTime lockedAt;
    private Long disabledBy;
    private LocalDateTime disabledAt;
    private String governanceNote;
}
