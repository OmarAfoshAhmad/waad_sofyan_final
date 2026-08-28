package com.waad.tba.modules.member.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The members whose ceilings a page needs.
 *
 * A body rather than a query string: a page of ids is long, and ids in a URL
 * reach access logs, APM and browser history. The size cap is the list's own
 * maximum page size -- a page is the only legitimate caller.
 */
@Data
public class MemberLimitOverviewRequest {

    @NotEmpty(message = "قائمة المستفيدين مطلوبة")
    @Size(max = 200, message = "عدد المستفيدين في الطلب يتجاوز الحد المسموح")
    private List<Long> memberIds;
}
