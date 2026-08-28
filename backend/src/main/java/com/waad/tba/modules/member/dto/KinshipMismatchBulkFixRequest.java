package com.waad.tba.modules.member.dto;

import com.waad.tba.modules.member.entity.Member;
import lombok.Data;
import java.util.List;
import jakarta.validation.constraints.NotBlank;

@Data
public class KinshipMismatchBulkFixRequest {
    private List<Long> memberIds;
    private Member.Relationship newRelationship;
    private Member.Gender newGender;
    @NotBlank
    private String reason;
}
