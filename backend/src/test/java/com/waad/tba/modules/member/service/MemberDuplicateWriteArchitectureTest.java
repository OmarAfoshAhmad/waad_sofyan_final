package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MemberDuplicateWriteArchitectureTest {
    @Test
    void duplicateResolutionCannotMoveHistoryDeleteMembersOrRewriteFamilyIdentity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/member/service/MemberDuplicateService.java"));
        assertThat(source).doesNotContain(".setMember(", "claimRepository.save", "visitRepository.save",
                "memberRepository.delete", ".setParent(", "resequenceDependents(",
                "memberAttributeRepository.delete");
        assertThat(source).contains("member_merge_records", "DUPLICATE_MERGED");
    }

    @Test
    void newVisitBoundaryResolvesCanonicalIdentityWithoutRewritingHistoricalVisits() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/visit/service/VisitService.java"));
        assertThat(source).contains("memberIdentityResolver.resolveCanonicalOrFail(dto.getMemberId())");
        assertThat(source).doesNotContain("entity.setMember(memberIdentityResolver");
    }
}
