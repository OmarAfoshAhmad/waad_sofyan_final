package com.waad.tba.modules.member.dto;

import java.util.List;

import lombok.Data;

@Data
public class BulkTerminateRequestDto {
    private List<Long> ids;
    private String reason;
}
