package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Prevents future code paths from writing Member.status/active independently. */
class MemberStatusWriteArchitectureTest {

    @Test
    void onlyTransitionServiceMayCallMemberStatusAndActiveSetters() throws Exception {
        Path root = Path.of("src/main/java/com/waad/tba/modules/member");
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("MemberStatusTransitionService.java")) continue;
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains(".setStatus(") || source.contains(".setActive(")) {
                    violations.add(root.relativize(file).toString());
                }
            }
        }
        assertThat(violations)
                .as("Member status/active writes must go through MemberStatusTransitionService")
                .isEmpty();
    }
}
