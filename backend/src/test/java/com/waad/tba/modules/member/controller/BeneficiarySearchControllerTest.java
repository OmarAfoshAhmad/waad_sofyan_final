package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.service.BeneficiarySearchService;
import com.waad.tba.modules.member.service.search.BeneficiarySearchType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiarySearchControllerTest {

    @Mock
    private BeneficiarySearchService beneficiarySearchService;

    @InjectMocks
    private BeneficiarySearchController controller;

    @Test
    void search_rejects_blank_value() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.search(BeneficiarySearchType.BY_NAME, "   ", null, null, null));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("type and value are required", ex.getReason());
    }

    @Test
    void search_returns_success_response() {
        List<MemberViewDto> members = List.of(
                MemberViewDto.builder().id(1L).fullName("Ahmed").build(),
                MemberViewDto.builder().id(2L).fullName("Ali").build());

        when(beneficiarySearchService.search(BeneficiarySearchType.BY_NAME, "Ah", 10L, "ACTIVE", 20))
                .thenReturn(members);

        ResponseEntity<ApiResponse<List<MemberViewDto>>> response = controller.search(BeneficiarySearchType.BY_NAME,
                "Ah", 10L, "ACTIVE", 20);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().getStatus());
        assertEquals("Found 2 beneficiaries", response.getBody().getMessage());
        assertEquals(2, response.getBody().getData().size());

        verify(beneficiarySearchService).search(BeneficiarySearchType.BY_NAME, "Ah", 10L, "ACTIVE", 20);
    }
}
