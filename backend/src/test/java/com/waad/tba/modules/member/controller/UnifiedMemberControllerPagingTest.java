package com.waad.tba.modules.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.service.MemberExcelExportService;
import com.waad.tba.modules.member.service.MemberFinancialSummaryService;
import com.waad.tba.modules.member.service.UnifiedMemberService;
import com.waad.tba.modules.member.service.UnifiedSearchService;
import com.waad.tba.services.pdf.HtmlToPdfService;
import com.waad.tba.services.pdf.PdfTemplateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

class UnifiedMemberControllerPagingTest {

    private final UnifiedMemberService unifiedMemberService = mock(UnifiedMemberService.class);

    private final UnifiedMemberController controller = new UnifiedMemberController(
            unifiedMemberService,
            mock(UnifiedSearchService.class),
            mock(MemberFinancialSummaryService.class),
            mock(PdfTemplateService.class),
            mock(HtmlToPdfService.class),
            mock(FileStorageService.class),
            mock(MemberExcelExportService.class)
    );

    @Test
    void searchMembersCapsRequestedPageSizeToProtectLargeMemberTables() {
        when(unifiedMemberService.searchMembers(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.<MemberViewDto>of()));

        controller.searchMembers(
                null, null, null, null, null,
                null, null, null, null, null,
                false, 0, 50_000, "createdAt", "DESC");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(unifiedMemberService).searchMembers(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), anyBoolean(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void getAllMembersCapsRequestedPageSizeToProtectLargeMemberTables() {
        when(unifiedMemberService.getAllMembers(any(Pageable.class), any(), any(), any()))
                .thenReturn(Page.empty());

        controller.getAllMembers(0, 10_000, "id", "DESC", null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(unifiedMemberService).getAllMembers(pageableCaptor.capture(), any(), any(), any());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }
}
