package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.service.search.BeneficiarySearchStrategy;
import com.waad.tba.modules.member.service.search.BeneficiarySearchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeneficiarySearchServiceTest {

    @Mock
    private BeneficiarySearchStrategy byIdStrategy;
    @Mock
    private BeneficiarySearchStrategy byNameStrategy;
    @Mock
    private BeneficiarySearchStrategy byBarcodeStrategy;
    @Mock
    private UnifiedMemberMapper unifiedMemberMapper;

    private BeneficiarySearchService service;

    @BeforeEach
    void setUp() {
        when(byIdStrategy.supportedType()).thenReturn(BeneficiarySearchType.BY_ID);
        when(byNameStrategy.supportedType()).thenReturn(BeneficiarySearchType.BY_NAME);
        when(byBarcodeStrategy.supportedType()).thenReturn(BeneficiarySearchType.BY_BARCODE);

        service = new BeneficiarySearchService(
                List.of(byIdStrategy, byNameStrategy, byBarcodeStrategy),
                unifiedMemberMapper);
    }

    @Test
    void search_byName_accepts_name_starting_with_numbers() {
        Member member = Member.builder()
                .id(11L)
                .fullName("3M Ahmed")
                .status(Member.MemberStatus.ACTIVE)
                .active(true)
                .build();

        MemberViewDto view = MemberViewDto.builder().id(11L).fullName("3M Ahmed").build();

        when(byNameStrategy.search(eq("3M Ahmed"), eq(12L), eq(Member.MemberStatus.ACTIVE), eq(20)))
                .thenReturn(List.of(member));
        when(unifiedMemberMapper.toViewDto(member)).thenReturn(view);

        List<MemberViewDto> result = service.search(BeneficiarySearchType.BY_NAME, " 3M Ahmed ", 12L, null, null);

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());

        verify(byNameStrategy).search("3M Ahmed", 12L, Member.MemberStatus.ACTIVE, 20);
        verify(unifiedMemberMapper).toViewDto(member);
    }

    @Test
    void search_byId_rejects_non_digit_value() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search(BeneficiarySearchType.BY_ID, "12A", null, null, null));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("BY_ID requires digits only", ex.getReason());
    }

    @Test
    void search_rejects_invalid_status() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search(BeneficiarySearchType.BY_NAME, "Ahmed", null, "INVALID", null));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Invalid status value: INVALID", ex.getReason());
    }

    @Test
    void search_caps_size_to_max_limit() {
        when(byBarcodeStrategy.search(any(), any(), any(), eq(50))).thenReturn(List.of());

        service.search(BeneficiarySearchType.BY_BARCODE, "WAD-2026-1001", null, "ACTIVE", 999);

        verify(byBarcodeStrategy).search("WAD-2026-1001", null, Member.MemberStatus.ACTIVE, 50);
    }
}
