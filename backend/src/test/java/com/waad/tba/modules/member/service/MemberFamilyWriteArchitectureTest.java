package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Prevents a new unaudited family-structure writer from bypassing V184. */
class MemberFamilyWriteArchitectureTest {

    private static final Set<String> ALLOWED = Set.of(
            "MemberFamilyService.java",       // audited transfer/correction
            "UnifiedMemberService.java",      // aggregate creation only
            "MemberImportRowProcessor.java",  // atomic import creation only
            "MemberDuplicateService.java");   // phase-5 duplicate merge, separately audited

    @Test
    void familyPointersCannotGainANewWriterOutsideTheOwnedServices() throws Exception {
        Path root = Path.of("src/main/java/com/waad/tba/modules/member");
        try (var paths = Files.walk(root)) {
            var offenders = paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            return source.contains(".setParent(") || source.contains(".setRelationship(");
                        } catch (Exception e) { throw new RuntimeException(e); }
                    })
                    .filter(p -> !p.getFileName().toString().equals("UnifiedMemberMapper.java"))
                    .filter(p -> !ALLOWED.contains(p.getFileName().toString()))
                    .map(Path::toString).toList();
            assertThat(offenders).as("unaudited family structure writers").isEmpty();
        }
    }
}
