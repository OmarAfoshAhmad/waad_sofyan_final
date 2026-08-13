package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.member.entity.Member.MemberStatus;

class MemberImportParserStatusTest {

    private final MemberImportParser parser = new MemberImportParser();

    @Test
    void completedBenefitLabelKeepsMembershipActive() {
        assertThat(parser.parseMemberStatus("مكتمل")).isEqualTo(MemberStatus.ACTIVE);
        assertThat(parser.parseMemberStatus("completed")).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void mapsSupportedMembershipStatuses() {
        assertThat(parser.parseMemberStatus("نشط")).isEqualTo(MemberStatus.ACTIVE);
        assertThat(parser.parseMemberStatus("موقوف")).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(parser.parseMemberStatus("منتهي")).isEqualTo(MemberStatus.TERMINATED);
        assertThat(parser.parseMemberStatus("قيد المراجعة")).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    void rejectsUnknownStatusInsteadOfSilentlyActivatingMember() {
        assertThatThrownBy(() -> parser.parseMemberStatus("غير معروفة"))
                .isInstanceOf(MemberImportRowValidationException.class)
                .hasMessageContaining("حالة عضوية غير معروفة");
    }
}
